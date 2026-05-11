package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import com.jianbo.localaiknowledge.service.HotSearchService;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.service.WebSearchService;
import com.jianbo.localaiknowledge.utils.RagFormatUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Planner Agent — ReAct 循环（对标 Spring AI 版 PlannerAgent）。
 *
 * <h2>实现差异</h2>
 * <p>Spring AI 版在 ReAct 的 Think 阶段用同步 chatModel.call()，
 * Finalize 阶段用 chatClient.stream()。
 * LangChain4j 版同理：Think 用 ChatLanguageModel.chat()，
 * Finalize 用 StreamingChatLanguageModel + TokenStreamFluxAdapter。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlannerAgent implements SpecializedAgent {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;
    private final HybridSearchService hybridSearchService;
    private final WebSearchService webSearchService;
    private final HotSearchService hotSearchService;
    private final ObjectMapper objectMapper;

    private static final int MAX_ITERATIONS = 5;
    private static final Pattern JSON_OBJ = Pattern.compile("\\{[^{}]*}");

    private static final String SYSTEM_PROMPT = """
            你是一个智能规划助手，使用 Think → Act → Observe 循环解决复杂问题。
            每轮你必须输出单行 JSON（不能有 markdown 围栏），schema：
            {"thought": "<本轮思考>", "action": "<search_kb|web_search|get_hot_list|finish>", "query": "<关键词>", "answer": "<若 action=finish 则填最终答案>"}
            """;

    @Override public AgentType type() { return AgentType.PLANNER; }
    @Override public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        return Flux.defer(() -> {
            List<ChatMessage> messages = new ArrayList<>(request.messages());
            RagToolContext ctx = request.toolContext();
            StringBuilder collectedContext = new StringBuilder();

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                ctx.emitStep("思考中（第 " + (i + 1) + " 轮）...");

                // Think: 同步调用拿 JSON
                var chatResp = chatModel.chat(ChatRequest.builder().messages(messages).build());
                String raw = chatResp.aiMessage().text();
                messages.add(AiMessage.from(raw));

                PlanStep step = parseStep(raw);
                if (step == null) {
                    log.warn("[Planner] 无法解析步骤: {}", raw);
                    break;
                }

                log.info("[Planner] Round {} | thought={}, action={}", i + 1, step.thought, step.action);

                // Finish → 流式输出最终答案
                if ("finish".equals(step.action)) {
                    if (step.answer != null && !step.answer.isBlank()) {
                        return streamFinalize(messages, collectedContext.toString(), ctx);
                    }
                    break;
                }

                // Act → 执行工具
                String observation = dispatch(step, request.userId(), ctx);
                collectedContext.append(observation).append("\n\n");
                messages.add(UserMessage.from("【工具结果 " + step.action + "(\"" + step.query + "\")】\n" + observation));
            }

            // 兜底：直接流式
            return streamFinalize(messages, collectedContext.toString(), ctx);
        });
    }

    private Flux<String> streamFinalize(List<ChatMessage> messages, String context, RagToolContext ctx) {
        ctx.emitStep("正在生成最终回答...");

        if (!context.isBlank()) {
            messages.add(UserMessage.from("请基于以上所有工具结果，用 Markdown 给出完整、有条理的最终回答。"));
        }

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(ChatRequest.builder().messages(messages).build(), handler));
    }

    private String dispatch(PlanStep step, String userId, RagToolContext ctx) {
        return switch (step.action) {
            case "search_kb" -> {
                ctx.emitStep("正在检索知识库: " + step.query);
                ctx.addInvokedTool("search_kb(" + step.query + ")");
                var docs = hybridSearchService.searchWithOwnership(step.query, userId, 5);
                if (docs.isEmpty()) yield "知识库中未找到相关内容。";
                docs.forEach(d -> ctx.addRetrievedDoc(
                        d.getMetadata().getOrDefault("source", "unknown").toString(),
                        d.getContent()));
                yield RagFormatUtil.formatDocs(docs);
            }
            case "web_search" -> {
                ctx.emitStep("正在搜索网络: " + step.query);
                ctx.addInvokedTool("web_search(" + step.query + ")");
                if (!webSearchService.isEnabled()) yield "网络搜索未启用。";
                var results = webSearchService.search(step.query);
                yield results.isEmpty() ? "未找到相关网络结果。" : webSearchService.formatAsContext(results);
            }
            case "get_hot_list" -> {
                ctx.emitStep("正在获取热榜: " + step.query);
                ctx.addInvokedTool("get_hot_list(" + step.query + ")");
                yield hotSearchService.queryAndFormat(step.query);
            }
            default -> "未知动作: " + step.action;
        };
    }

    private PlanStep parseStep(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = JSON_OBJ.matcher(raw);
        if (!m.find()) return null;
        try {
            JsonNode n = objectMapper.readTree(m.group());
            return new PlanStep(
                    text(n, "thought"), text(n, "action"),
                    text(n, "query"), text(n, "answer"));
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode n, String field) {
        return n.has(field) ? n.get(field).asText("") : "";
    }

    record PlanStep(String thought, String action, String query, String answer) {}
}
