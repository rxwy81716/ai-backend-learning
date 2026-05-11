package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.utils.RagFormatUtil;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库工具（对标 Spring AI 版 KnowledgeTools）。
 *
 * <h2>@Tool 注解对比</h2>
 * <pre>
 * Spring AI:
 *   @org.springframework.ai.tool.annotation.Tool(name="searchKnowledgeBase", description="...")
 *   @ToolParam(description="...")
 *
 * LangChain4j:
 *   @dev.langchain4j.agent.tool.Tool(name="searchKnowledgeBase", value={"..."})
 *   @P("描述")
 * </pre>
 *
 * <p>功能几乎相同！LangChain4j 用 {@code @P} 注解参数描述（更简洁），
 * Spring AI 用 {@code @ToolParam}。</p>
 *
 * <h2>ToolContext 差异</h2>
 * <p>Spring AI 版通过 {@code ToolContext} 隐式传 userId，
 * LangChain4j 没有等价机制。解决方案：通过 ThreadLocal 或在 RagToolContext 中持有 userId，
 * 工具方法内部从 toolContextHolder 取。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeTools {

    private final HybridSearchService hybridSearchService;

    /** ThreadLocal 传递当前请求的 RagToolContext */
    private static final ThreadLocal<RagToolContext> CONTEXT_HOLDER = new ThreadLocal<>();

    public static void setContext(RagToolContext ctx) { CONTEXT_HOLDER.set(ctx); }
    public static void clearContext() { CONTEXT_HOLDER.remove(); }

    @Tool(name = "searchKnowledgeBase", value = {
        "从企业私域知识库中检索与问题最相关的内容片段。",
        "必须调用此工具来回答用户问题。如果知识库中没有相关内容，工具会明确提示你基于通用知识回答。"
    })
    public String searchKnowledgeBase(@P("检索关键词，保留专有名词") String query) {
        RagToolContext ctx = CONTEXT_HOLDER.get();
        String userId = ctx != null ? ctx.getUserId() : null;

        log.info("[Tool] searchKnowledgeBase | query={}, userId={}", query, userId);

        if (ctx != null) {
            ctx.addInvokedTool("searchKnowledgeBase(" + query + ")");
            ctx.emitStep("正在检索知识库...");
        }

        var docs = hybridSearchService.searchWithOwnership(query, userId, 5);

        if (docs.isEmpty()) {
            return "【知识库无相关结果】请基于你的通用知识回答，并注明\"此回答未经知识库验证\"。";
        }

        if (ctx != null) {
            for (var doc : docs) {
                ctx.addRetrievedDoc(
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getContent()
                );
            }
        }

        return RagFormatUtil.formatDocs(docs);
    }
}
