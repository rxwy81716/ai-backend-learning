package com.jianbo.springai.service.save;

import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向量入库服务（SpringAI VectorStore 方式 -- 推荐）
 *
 * 核心依赖：spring-ai-pgvector-store-spring-boot-starter
 *   --> 自动配置 PgVectorStore Bean（实现了 VectorStore 接口）
 *   --> 自动处理：向量化 + 入库（一步到位！）
 *
 * VectorStore.add(documents) 时 SpringAI 会自动：
 *   1. 调用 EmbeddingModel 把文本转成向量
 *   2. 把 content + embedding + metadata 一起存入 PostgreSQL
 *   你不需要手动调用 embeddingService.embed()！
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreService {
    /**
     * SpringAI 的 VectorStore 接口
     * 实际注入的是 PgVectorStore（由 pgvector starter 自动配置）
     * 内部已持有 EmbeddingModel，add() 时自动向量化
     */
    private final VectorStore vectorStore;

    /**
     * 文档入库（切片 + 向量化 + 存入pgvector 一条龙）
     *
     * @param rawText    原始文档全文
     * @param sourceName 文档来源名称（如："java_guide.pdf"）
     * @return 入库的切片数量
     */
    public int importDocument(String rawText,String sourceName){
        // ===== 第一步：文本切片（复用 Day22 工具类） =====
        List<String> chunks = TextSplitterUtil.splitText(rawText);
        log.info("文本切片完成: {} 段, 来源: {}", chunks.size(), sourceName);

        // ===== 第二步：封装成 SpringAI Document 对象 =====
        List<Document> documents = getDocuments(sourceName, chunks);
        // ===== 第三步：一键入库（自动 Embedding + INSERT） =====
        vectorStore.add(documents);
        log.info("向量入库完成: {} 条记录已存入 pgvector", documents.size());
        return documents.size();
    }

    private static @NonNull List<Document> getDocuments(String sourceName, List<String> chunks) {
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            // Document 参数1: 文本内容（会被向量化）
            // Document 参数2: 元数据Map（不会被向量化，但存入数据库）
            Document doc = new Document(
                    chunks.get(i),
                    Map.of(
                            "source", sourceName,
                            "chunk_index", String.valueOf(i),
                            "total_chunks", String.valueOf(chunks.size())
                    )
            );
            documents.add(doc);
        }
        return documents;
    }

    /**
     * 批量文档入库
     * @param documentMap  Map<文档来源名, 文档全文>
     * @return 总入库切片数量
     */
    public int importDocuments(Map<String, String> documentMap) {
        int totalCount = 0;
        for (var entry : documentMap.entrySet()) {
            int count = importDocument(entry.getValue(), entry.getKey());
            totalCount += count;
        }
        log.info("批量入库完成, 共 {} 个文档, {} 条切片", documentMap.size(), totalCount);
        return totalCount;
    }

    /**
     * 删除指定文档的所有向量（文档更新时用：先删旧的再导新的）
     */
    public void deleteDocuments(List<String> documentIds) {
        vectorStore.delete(documentIds);
        log.info("已删除 {} 条向量记录", documentIds.size());
    }

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

//    ==============================手动向量入库（原生JDBC方式）=======================================
  /**
   *
   * 手动向量入库（原生JDBC方式）
   * 手动入库：切片文本 --> 向量化 --> INSERT
   * CREATE TABLE my_documents
   * (
   *     id          BIGSERIAL PRIMARY KEY,
   *     doc_title   VARCHAR(200),
   *     chunk_index INTEGER,
   *     content     TEXT         NOT NULL,
   *     embedding   VECTOR(1536) NOT NULL,
   *     source      VARCHAR(100),
   *     created_at  TIMESTAMP DEFAULT NOW()
   * );
   * CREATE INDEX idx_my_docs_embedding
   *     ON my_documents USING hnsw (embedding vector_cosine_ops);
   *
   */
  @Transactional
  public int importChunks(String docTitle, String source, List<String> chunks) {
        // 1. 批量向量化
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        // 2. 逐条INSERT
        String sql = """
            INSERT INTO my_documents (doc_title, chunk_index, content, embedding, source)
            VALUES (?, ?, ?, ?::vector, ?)
            """;

        for (int i = 0; i < chunks.size(); i++) {
            jdbcTemplate.update(sql,
                    docTitle,                              // 文档标题
                    i,                                     // 片段序号
                    chunks.get(i),                         // 原始文本
                    floatArrayToPgVector(vectors.get(i)),  // 向量字符串
                    source                                 // 来源
            );
        }

        log.info("手动入库完成: {} 条, 文档: {}", chunks.size(), docTitle);
        return chunks.size();
    }

    /**
     * float[] 转 pgvector 格式字符串
     *
     * 输入：float[]{0.12f, -0.34f, 0.56f}
     * 输出："[0.12,-0.34,0.56]"
     *
     * SQL中用 ?::vector 做类型转换
     */
    private String floatArrayToPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

}
