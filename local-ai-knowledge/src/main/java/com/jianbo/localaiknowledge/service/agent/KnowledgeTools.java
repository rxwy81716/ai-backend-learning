package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库 Agent 专用工具：从企业私域知识库检索文档片段。
 *
 * <p>从原 {@link RagTools} 拆出，仅保留 searchKnowledgeBase 一个工具，
 * 让 LLM 不会在"只有 1 个工具"时出现选择错误。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeTools {

  private final HybridSearchService hybridSearchService;

  public static final String TOOL_NAME = "searchKnowledgeBase";
  public static final String CTX_KEY = "ragCtx";

  private static final int DEFAULT_KB_TOP_K = 8;

  @Tool(
      name = "searchKnowledgeBase",
      description = """
          从企业私域知识库（用户上传的文档、PDF、Word 等）中检索与问题最相关的内容片段。
          必须调用此工具来回答用户问题。如果知识库中没有相关内容，工具会明确提示你基于通用知识回答。
          适用场景：用户询问产品文档、内部资料、合同条款、专业领域知识等。""")
  public String searchKnowledgeBase(
      @ToolParam(description = "用户问题的检索关键词，建议保留专有名词、人名、产品名等关键信息") String query,
      ToolContext toolCtx) {
    long t0 = System.currentTimeMillis();
    RagToolContext ctx = resolveCtx(toolCtx);
    ctx.recordInvocation(TOOL_NAME);

    String searchQuery = ctx.getSearchQuery(query);
    String userId = ctx.getUserId();
    List<Document> docs = hybridSearchService.searchWithOwnership(searchQuery, userId, DEFAULT_KB_TOP_K);
    ctx.addDocs(docs);

    log.info(
        "[KnowledgeAgent Tool] {} | llmQuery={}, searchQuery={}, rewritten={}, userId={}, hit={}, cost={}ms",
        TOOL_NAME, query, searchQuery, !searchQuery.equals(query), userId, docs.size(),
        System.currentTimeMillis() - t0);

    if (docs.isEmpty()) {
      return "知识库暂无相关内容。请基于你的通用知识回答用户问题，并在回答末尾明确告知'以下回答基于通用知识，仅供参考'。";
    }
    return formatDocs(docs);
  }

  private RagToolContext resolveCtx(ToolContext toolCtx) {
    if (toolCtx != null) {
      Object obj = toolCtx.getContext().get(CTX_KEY);
      if (obj instanceof RagToolContext rc) return rc;
    }
    log.warn("ToolContext 未携带 {}，工具调用将无法回传元数据", CTX_KEY);
    return RagToolContext.create(null);
  }

  private String formatDocs(List<Document> docs) {
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
