package com.jianbo.springai.service.save;

import com.jianbo.springai.utils.TextCleanUtil;
import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Service 中使用两个存储
@Service
@RequiredArgsConstructor
@Slf4j
public class DualVectorStoreService {
  /** PG 存储（默认 @Primary） */
  private final VectorStore pgVectorStore;

  /** ES 存储（用 @Qualifier 指定） */
  @Qualifier("esVectorStore")
  private final VectorStore esVectorStore;

  /** 文档双写入库（PG + ES 各存一份） */
  public void importToBoth(String rawText, String source) {
    List<Document> docs = buildDocuments(rawText, source);

    pgVectorStore.add(docs);
    log.info("PG 入库完成");

    esVectorStore.add(docs);
    log.info("ES 入库完成");
  }

  private static @NonNull List<Document> buildDocuments(String rawText, String source) {
    List<String> splitText = TextSplitterUtil.splitText(TextCleanUtil.cleanText(rawText));
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
}
