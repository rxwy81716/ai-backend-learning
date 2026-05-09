package com.jianbo.localaiknowledge.utils;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG 检索结果格式化工具：把 {@link Document} 列表渲染成给 LLM 看的"【N】[来源: xxx]\n正文" 段落。
 *
 * <p>抽取自 {@code KnowledgeAgent} / {@code KnowledgeTools}，避免两处重复实现漂移。
 */
public final class RagFormatUtil {

  private RagFormatUtil() {}

  /** 把检索到的 Document 列表格式化为带编号 + 来源标注的多段文本。 */
  public static String formatDocs(List<Document> docs) {
    if (docs == null || docs.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < docs.size(); i++) {
      Document doc = docs.get(i);
      String src = String.valueOf(doc.getMetadata().getOrDefault("source", "未知"));
      sb.append("【").append(i + 1).append("】[来源: ").append(src).append("]\n")
          .append(doc.getText()).append("\n\n");
    }
    return sb.toString();
  }
}
