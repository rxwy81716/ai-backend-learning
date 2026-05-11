package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.DocumentChunkMapper;
import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentChunk;
import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.utils.TextSplitterUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.List;

/**
 * 文档解析服务（对标 Spring AI 版 DocumentParseService）。
 *
 * <h2>核心差异</h2>
 * <pre>
 * Spring AI:
 *   TikaDocumentReader reader = new TikaDocumentReader(resource);
 *   List&lt;Document&gt; docs = reader.read();
 *   String text = docs.get(0).getText();
 *
 * LangChain4j:
 *   ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
 *   Document doc = parser.parse(inputStream);
 *   String text = doc.text();
 * </pre>
 *
 * <h2>向量入库差异</h2>
 * <pre>
 * Spring AI:
 *   vectorStore.add(documents);  // 自动调 embedding + 存入
 *
 * LangChain4j:
 *   Embedding emb = embeddingModel.embed(segment).content();
 *   embeddingStore.add(emb, segment);  // 需要手动两步
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentParseService {

    private final DocumentTaskMapper taskMapper;
    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchEmbeddingStore embeddingStore;

    public DocumentTask getTask(String taskId) {
        return taskMapper.selectByTaskId(taskId);
    }

    /**
     * 解析并入库（异步调用）。
     */
    public void parseAndImport(DocumentTask task) {
        try {
            // 1. 解析文档
            task.setStatus("PARSING");
            taskMapper.update(task);

            ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
            Document doc = parser.parse(new FileInputStream(task.getFilePath()));
            String fullText = doc.text();

            // 2. 切片
            task.setStatus("IMPORTING");
            taskMapper.update(task);

            List<String> chunks = TextSplitterUtil.split(fullText, 800, 100);
            task.setTotalChunks(chunks.size());

            // 3. Embedding + 入库
            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i);

                // 存 PG
                DocumentChunk chunk = new DocumentChunk();
                chunk.setTaskId(task.getTaskId());
                chunk.setChunkIndex(i);
                chunk.setContent(content);
                chunk.setSource(task.getFileName());
                chunk.setUserId(task.getUserId());
                chunk.setDocScope(task.getDocScope());
                chunkMapper.insert(chunk);

                // 存 ES（手动 embed + 存入，Spring AI 版是一步到位的 vectorStore.add()）
                TextSegment segment = TextSegment.from(content,
                        dev.langchain4j.data.document.Metadata.from("source", task.getFileName())
                                .put("user_id", task.getUserId())
                                .put("doc_scope", task.getDocScope())
                                .put("task_id", task.getTaskId()));
                var embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);

                task.setImportedChunks(i + 1);
                taskMapper.update(task);
            }

            task.setStatus("DONE");
            taskMapper.update(task);
            log.info("[Parse] 完成 | taskId={}, chunks={}", task.getTaskId(), chunks.size());

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            taskMapper.update(task);
            log.error("[Parse] 失败 | taskId={}, error={}", task.getTaskId(), e.getMessage());
        }
    }
}
