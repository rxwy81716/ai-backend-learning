package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jianbo.localaiknowledge.utils.TextCleanUtil;
import com.jianbo.localaiknowledge.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 向量入库服务
 *
 * <p>关键：VectorStore 接口是统一的！ PG 注入的是 PgVectorStore，ES 注入的是 ElasticsearchVectorStore
 * 业务代码一模一样，只是底层存储不同 --> 面向接口编程的威力
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorStoreService {
  /**
   * 注入 ElasticsearchVectorStore（由 ES starter 自动配置）
   *
   * <p>注意：如果项目同时有 PG 和 ES 两个 VectorStore Bean， 需要用 @Qualifier 区分（见下方"双存储共存"章节）
   */
  private final VectorStore vectorStore;

  /**
   * 文档入库 ES（和 PG 版本的代码几乎一模一样！）
   *
   * <p>vectorStore.add() 内部自动： 1. 调 EmbeddingModel 把 content 转向量 2. PUT
   * /vector_store_index/_doc/{id} 存入 ES
   */
  public int importDocuments(String rawText, String source) {

    // 文本清洗切片
    String clean = TextCleanUtil.clean(rawText);
    List<String> splitText = TextSplitterUtil.splitText(clean);
    log.debug("文本清洗切片完成:{}段,来源:{}", splitText.size(), source);

    // 封装成SpringAI document
    List<Document> documents = getDocuments(source, splitText);
    // 一件入库
    vectorStore.add(documents);
    log.debug("文档入库完成:{}段,来源:{}", documents.size(), source);
    return documents.size();
  }

  /**
   * 批量文档入库
   *
   * @param documentMap Map<文档来源名, 文档全文>
   * @return 总入库切片数量
   */
  public int importDocuments(Map<String, String> documentMap) {
    int totalCount = 0;
    for (var entry : documentMap.entrySet()) {
      int count = importDocuments(entry.getValue(), entry.getKey());
      totalCount += count;
    }
    log.info("批量入库完成, 共 {} 个文档, {} 条切片", documentMap.size(), totalCount);
    return totalCount;
  }

  /** 删除文档 */
  public void deleteDocuments(List<String> documentIds) {
    vectorStore.delete(documentIds);
    log.info("ES 已删除 {} 条", documentIds.size());
  }

  private static @NonNull List<Document> getDocuments(String source, List<String> splitText) {
    List<Document> documents = new ArrayList<>();
    for (int i = 0; i < splitText.size(); i++) {
      Document document =
          new Document(
              splitText.get(i),
              Map.of(
                  "source",
                  source,
                  "chunk_index",
                  String.valueOf(i),
                  "total_chunks",
                  String.valueOf(splitText.size())));
      documents.add(document);
    }
    return documents;
  }

  //  ===============入库方式2 手动RestClient================
  private final RestClient restClient; // ES 低级客户端
  private final EmbeddingService embeddingService;
  private final ObjectMapper objectMapper;
  private static final String INDEX_NAME = "knowledge_vector_store";

  /** 手动创建索引(类似PG建表 只需执行一次) */
  public void createIndex() throws IOException {
    Request request = new Request("PUT", "/" + INDEX_NAME);
    String mapping =
        """
      {
        "mappings": {
          "properties": {
            "doc_title": { "type": "keyword" },
            "chunk_index": { "type": "integer" },
            "content": { "type": "text", "analyzer": "ik_max_word" },
            "embedding": {
              "type": "dense_vector",
              "dims": 1536,
              "index": true,
              "similarity": "cosine"
            },
            "source": { "type": "keyword" },
            "created_at": { "type": "date" }
          }
        }
      }
      """;
    request.setJsonEntity(mapping);
    restClient.performRequest(request);
    log.info("ES 索引 {} 创建成功", INDEX_NAME);
  }

  /** 手动入库（清洗 + 切片 + 向量化 + 写入ES） */
  public int importChunks(String docTitle, String source, List<String> chunks) throws IOException {
    // 批量向量化
    List<float[]> vectors = embeddingService.embedBatch(chunks);
    // 逐条写入ES
    for (int i = 0; i < vectors.size(); i++) {
      Map<String, Object> doc = new HashMap<>();
      doc.put("doc_title", docTitle);
      doc.put("chunk_index", i);
      doc.put("content", chunks.get(i));
      doc.put("embedding", vectors.get(i));
      doc.put("source", source);
      doc.put("created_at", System.currentTimeMillis());
      Request request = new Request("POST", "/" + INDEX_NAME + "/_doc");
      request.setJsonEntity(objectMapper.writeValueAsString(doc));
      restClient.performRequest(request);
    }
    log.info("ES 入库完成 {} 条", chunks.size());
    return chunks.size();
  }

  /** 批量入库 (_bulk API，性能更高) */
  public int bulkImportDocuments(String docTitle, String source, List<String> chunks)
      throws IOException {
    List<float[]> vectors = embeddingService.embedBatch(chunks);
    StringBuilder bulkBody = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      //action行
      bulkBody.append("{\"index\":{\"_index\": \"").append(INDEX_NAME).append("\"}}\n");
      //document行
      HashMap<String, Object> doc = new HashMap<>();
      doc.put("doc_title", docTitle);
      doc.put("chunk_index", i);
      doc.put("content", chunks.get(i));
      doc.put("embedding", vectors.get(i));
      doc.put("source", source);
      doc.put("created_at", System.currentTimeMillis());
      bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
    }
    Request request = new Request("POST", "/" + INDEX_NAME + "/_bulk");
    request.setJsonEntity(bulkBody.toString());
    restClient.performRequest(request);
    log.info("ES 批量入库完成 {} 条", chunks.size());
    return chunks.size();
  }
}
