package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.HotSearchService;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG Agent 可调用的工具集合（Spring AI 2.x {@code @Tool} 注解风格）。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>每个 {@code @Tool} 方法的 {@code description} 描述"何时调用"，由 LLM 自主决策
 *   <li>用户身份 / 调用记录通过 {@link ToolContext} 显式传入（key = {@code ragCtx}）， 不暴露给 LLM 也不依赖
 *       ThreadLocal，跨线程安全
 *   <li>所有工具返回 {@code String}，同时把命中文档/调用记录写回 {@link RagToolContext}， 供外层构建响应元数据 source / references
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RagTools {

  private final HybridSearchService hybridSearchService;
  private final HotSearchService hotSearchService;
  private final WebSearchService webSearchService;

  public static final String TOOL_KB = "searchKnowledgeBase";
  public static final String TOOL_HOT = "queryHotSearch";
  public static final String TOOL_WEB = "searchWeb";

  /** ToolContext map 中存放 {@link RagToolContext} 的 key */
  public static final String CTX_KEY = "ragCtx";

  private static final int DEFAULT_KB_TOP_K = 8;

  // ==================== Tool 方法（@Tool 自动注册到 LLM 的 function schema） ====================

  @Tool(
      name = TOOL_KB,
      description =
          """
            从企业私域知识库（用户上传的文档、PDF、Word 等）中检索与问题最相关的内容片段。
            优先调用此工具；只有当本工具返回'知识库暂无相关内容'时，才考虑调用其它工具或基于自身知识回答。
            适用场景：用户询问产品文档、内部资料、合同条款、专业领域知识等。""")
  public String searchKnowledgeBase(
      @ToolParam(description = "用户问题的检索关键词，建议保留专有名词、人名、产品名等关键信息") String query,
      ToolContext toolCtx) {
    long t0 = System.currentTimeMillis();
    RagToolContext ctx = resolveCtx(toolCtx);
    ctx.recordInvocation(TOOL_KB);

    // 优先使用 Query Rewrite 改写后的检索 query（多轮对话指代消解）
    String searchQuery = ctx.getSearchQuery(query);
    String userId = ctx.getUserId();
    List<Document> docs = hybridSearchService.searchWithOwnership(searchQuery, userId, DEFAULT_KB_TOP_K);
    ctx.addDocs(docs);

    log.info(
        "[Tool] {} | llmQuery={}, searchQuery={}, rewritten={}, userId={}, hit={}, cost={}ms",
        TOOL_KB,
        query,
        searchQuery,
        !searchQuery.equals(query),
        userId,
        docs.size(),
        System.currentTimeMillis() - t0);

    if (docs.isEmpty()) {
      return "知识库暂无相关内容。";
    }
    return formatDocs(docs);
  }

  @Tool(
      name = TOOL_HOT,
      description =
          """
            查询主流平台的实时热榜数据（微博、知乎、B站、GitHub Trending、抖音、小红书等）。
            适用场景：用户询问'今日热搜''xxx 平台热门''最近流行什么'等时效性强的话题。
            注意：数据来自定时爬虫采集，可能有几小时延迟。""")
  public String queryHotSearch(
      @ToolParam(description = "原始用户问题，用于自动识别想查询哪个平台（如包含'微博/知乎/B站'）") String question,
      ToolContext toolCtx) {
    RagToolContext ctx = resolveCtx(toolCtx);
    ctx.recordInvocation(TOOL_HOT);
    log.info("[Tool] {} | question={}", TOOL_HOT, question);
    return hotSearchService.queryAndFormat(question);
  }

  @Tool(
      name = TOOL_WEB,
      description =
          """
            通过外部搜索引擎（Tavily）检索公开互联网上的最新信息。
            适用场景：用户询问最新新闻、实时数据、知识库未涵盖的公开知识等，
            且 queryHotSearch 不适用（不是热榜性质）。
            注意：调用成本较高且可能耗时较长，请仅在前两个工具都不适用时使用。""")
  public String searchWeb(
      @ToolParam(description = "搜索关键词，应简洁包含核心实体与动作") String query, ToolContext toolCtx) {
    RagToolContext ctx = resolveCtx(toolCtx);
    ctx.recordInvocation(TOOL_WEB);

    if (!webSearchService.isEnabled()) {
      log.info("[Tool] {} | 未启用，跳过", TOOL_WEB);
      return "网络搜索未启用。";
    }
    long t0 = System.currentTimeMillis();
    List<Map<String, String>> results = webSearchService.search(query);
    log.info(
        "[Tool] {} | query={}, results={}, cost={}ms",
        TOOL_WEB,
        query,
        results.size(),
        System.currentTimeMillis() - t0);
    if (results.isEmpty()) {
      return "网络搜索无结果。";
    }
    return webSearchService.formatAsContext(results);
  }

  /** 从 {@link ToolContext} 取出 {@link RagToolContext}； 缺失时降级返回临时空 ctx 以防 NPE，但元数据回传会失效。 */
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
      sb.append("【")
          .append(i + 1)
          .append("】[来源: ")
          .append(src)
          .append("]\n")
          .append(doc.getText())
          .append("\n\n");
    }
    return sb.toString();
  }
}
