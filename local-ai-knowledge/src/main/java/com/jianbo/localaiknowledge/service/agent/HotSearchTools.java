package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.HotSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 热榜 Agent 专用工具：查询各平台实时热搜/热榜数据。
 *
 * @deprecated {@link HotSearchAgent} 已改为程序主动调用 {@link HotSearchService}，
 *             不再通过 LLM tool calling。本类保留供未来可能的 tool calling 场景使用。
 */
@Deprecated(forRemoval = true)
@Slf4j
@RequiredArgsConstructor
public class HotSearchTools {

  private final HotSearchService hotSearchService;

  public static final String TOOL_NAME = "queryHotSearch";
  public static final String CTX_KEY = "ragCtx";

  @Tool(
      name = "queryHotSearch",
      description = """
          查询主流平台的实时热榜数据（微博、知乎、B站、GitHub Trending、抖音、小红书等）。
          必须调用此工具来获取热榜数据。
          数据来自定时爬虫采集，可能有几小时延迟。""")
  public String queryHotSearch(
      @ToolParam(description = "原始用户问题，用于自动识别想查询哪个平台（如包含'微博/知乎/B站'）") String question,
      ToolContext toolCtx) {
    RagToolContext ctx = resolveCtx(toolCtx);
    ctx.recordInvocation(TOOL_NAME);
    log.info("[HotSearchAgent Tool] {} | question={}", TOOL_NAME, question);
    return hotSearchService.queryAndFormat(question);
  }

  private RagToolContext resolveCtx(ToolContext toolCtx) {
    if (toolCtx != null) {
      Object obj = toolCtx.getContext().get(CTX_KEY);
      if (obj instanceof RagToolContext rc) return rc;
    }
    log.warn("ToolContext 未携带 {}，工具调用将无法回传元数据", CTX_KEY);
    return RagToolContext.create(null);
  }
}
