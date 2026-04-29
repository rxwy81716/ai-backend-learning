package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.jianbo.localaiknowledge.service.HotSearchService;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG Agent 可调用的工具集合（基于 Spring AI 1.0.0-M5 FunctionCallback API）
 *
 * <p>设计原则：
 * <ul>
 *   <li>每个工具职责单一，{@code description} 描述清楚"什么场景下应该调用我"，
 *       由 LLM 自主决策是否调用以及调用顺序</li>
 *   <li>工具参数仅暴露 LLM 需要决策的字段（如 query），用户身份通过
 *       {@link RagToolContext} 隐式传递，防止 LLM 越权或被 prompt 注入篡改 userId</li>
 *   <li>所有工具返回 {@code String}（LLM 可直接消费的上下文文本），
 *       同时把命中文档/调用记录写入 {@link RagToolContext}，供外层构建响应元数据</li>
 * </ul>
 *
 * <p>升级到 Spring AI 1.0.0-M6+ 后，可改用 {@code @Tool}/{@code @ToolParam} 注解大幅简化。
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

    private static final int DEFAULT_KB_TOP_K = 8;

    // ==================== Tool 入参 DTO（schema 由 Jackson 注解驱动） ====================

    @JsonClassDescription("知识库检索请求参数")
    public record KbInput(
            @JsonProperty(required = true, value = "query")
            @JsonPropertyDescription("用户问题的检索关键词，建议保留专有名词、人名、产品名等关键信息")
            String query
    ) {}

    @JsonClassDescription("热榜查询请求参数")
    public record HotInput(
            @JsonProperty(required = true, value = "question")
            @JsonPropertyDescription("原始用户问题，用于自动识别想查询哪个平台（如包含'微博/知乎/B站'）")
            String question
    ) {}

    @JsonClassDescription("网络搜索请求参数")
    public record WebInput(
            @JsonProperty(required = true, value = "query")
            @JsonPropertyDescription("搜索关键词，应简洁包含核心实体与动作")
            String query
    ) {}

    // ==================== 对外暴露：批量获取所有 FunctionCallback ====================

    public List<FunctionCallback> callbacks() {
        return callbacks(null);
    }

    /**
     * 构建工具回调列表，并把 {@link RagToolContext} 通过闭包绑死到 lambda 上。
     * <p>这样无论 LLM 在哪个线程触发回调（流式场景下可能是 Reactor Netty IO 线程），
     * 都能拿到正确的 ctx，绕开 ThreadLocal 跨线程不可见问题。
     *
     * @param ctx 显式上下文；为 null 时回退到 {@link RagToolContext#current()} 读 ThreadLocal（同步路径用）
     */
    public List<FunctionCallback> callbacks(RagToolContext ctx) {
        return List.of(
                FunctionCallback.builder()
                        .function(TOOL_KB, (KbInput in) -> doSearchKb(in.query(), resolveCtx(ctx)))
                        .description("""
                                从企业私域知识库（用户上传的文档、PDF、Word 等）中检索与问题最相关的内容片段。
                                优先调用此工具；只有当本工具返回'知识库暂无相关内容'时，才考虑调用其它工具或基于自身知识回答。
                                适用场景：用户询问产品文档、内部资料、合同条款、专业领域知识等。""")
                        .inputType(KbInput.class)
                        .build(),
                FunctionCallback.builder()
                        .function(TOOL_HOT, (HotInput in) -> doQueryHot(in.question(), resolveCtx(ctx)))
                        .description("""
                                查询主流平台的实时热榜数据（微博、知乎、B站、GitHub Trending、抖音、小红书等）。
                                适用场景：用户询问'今日热搜''xxx 平台热门''最近流行什么'等时效性强的话题。
                                注意：数据来自定时爬虫采集，可能有几小时延迟。""")
                        .inputType(HotInput.class)
                        .build(),
                FunctionCallback.builder()
                        .function(TOOL_WEB, (WebInput in) -> doSearchWeb(in.query(), resolveCtx(ctx)))
                        .description("""
                                通过外部搜索引擎（Tavily）检索公开互联网上的最新信息。
                                适用场景：用户询问最新新闻、实时数据、知识库未涵盖的公开知识等，
                                且 queryHotSearch 不适用（不是热榜性质）。
                                注意：调用成本较高且可能耗时较长，请仅在前两个工具都不适用时使用。""")
                        .inputType(WebInput.class)
                        .build()
        );
    }

    // ==================== 工具实际逻辑 ====================

    private String doSearchKb(String query, RagToolContext ctx) {
        long t0 = System.currentTimeMillis();
        ctx.recordInvocation(TOOL_KB);

        String userId = ctx.getUserId();
        List<Document> docs = hybridSearchService.searchWithOwnership(query, userId, DEFAULT_KB_TOP_K);
        ctx.addDocs(docs);

        log.info("[Tool] {} | query={}, userId={}, hit={}, cost={}ms",
                TOOL_KB, query, userId, docs.size(), System.currentTimeMillis() - t0);

        if (docs.isEmpty()) {
            return "知识库暂无相关内容。";
        }
        return formatDocs(docs);
    }

    private String doQueryHot(String question, RagToolContext ctx) {
        ctx.recordInvocation(TOOL_HOT);
        log.info("[Tool] {} | question={}", TOOL_HOT, question);
        return hotSearchService.queryAndFormat(question);
    }

    private String doSearchWeb(String query, RagToolContext ctx) {
        ctx.recordInvocation(TOOL_WEB);

        if (!webSearchService.isEnabled()) {
            log.info("[Tool] {} | 未启用，跳过", TOOL_WEB);
            return "网络搜索未启用。";
        }
        long t0 = System.currentTimeMillis();
        List<Map<String, String>> results = webSearchService.search(query);
        log.info("[Tool] {} | query={}, results={}, cost={}ms",
                TOOL_WEB, query, results.size(), System.currentTimeMillis() - t0);
        if (results.isEmpty()) {
            return "网络搜索无结果。";
        }
        return webSearchService.formatAsContext(results);
    }

    /**
     * 解析实际使用的 ctx：
     * <ol>
     *   <li>外层显式传入（流式场景必须） → 直接用</li>
     *   <li>否则回退读 ThreadLocal（同步 chat() 走这条）</li>
     *   <li>都没有则兜底创建空 ctx 防止 NPE</li>
     * </ol>
     */
    private RagToolContext resolveCtx(RagToolContext explicit) {
        if (explicit != null) return explicit;
        RagToolContext fromTL = RagToolContext.current();
        if (fromTL != null) return fromTL;
        log.warn("RagToolContext 未初始化，工具调用将无法回传元数据");
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
