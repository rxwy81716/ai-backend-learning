package com.jianbo.localaiknowledge.utils;

import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;

import java.util.List;

/**
 * 检索结果格式化工具（对标 Spring AI 版）。
 *
 * <h2>差异</h2>
 * <p>Spring AI 版操作 {@code Document} 对象，
 * LangChain4j 版操作 {@code SearchDoc}（对 TextSegment 的封装）。
 * 格式化逻辑完全一致。</p>
 */
public class RagFormatUtil {

    public static String formatDocs(List<SearchDoc> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            SearchDoc doc = docs.get(i);
            String source = doc.getMetadata().getOrDefault("source", "unknown").toString();
            sb.append("<doc_").append(i + 1).append(" source=\"").append(source).append("\">\n");
            sb.append(doc.getContent()).append("\n");
            sb.append("</doc_").append(i + 1).append(">\n\n");
        }
        return sb.toString();
    }
}
