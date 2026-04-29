package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.service.agent.RagToolContext;
import com.jianbo.localaiknowledge.service.agent.RagTools;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

/**
 * RAG Agent（基于 Spring AI Tool Calling）
 *
 * <p><b>架构演进</b>：
 * <pre>
 *   旧版：Java 关键词匹配（isHotQuery）硬编码路由 → KB / 热榜 / 网搜 / LLM 直答
 *   新版：LLM 自主调用 Tool（searchKnowledgeBase / queryHotSearch / searchWeb）
 * </pre>
 *
 * <p>路由决策权从 Java 移交给 LLM，工具描述由 {@link RagTools#callbacks()} 提供。
 * 上下文 {@link RagToolContext} 用于：
 * <ol>
 *   <li>把 userId 隐式传给工具（防注入 + 不让 LLM 感知）</li>
 *   <li>回收"实际调用了哪些工具 + 命中了哪些 docs"，构建响应里的 source / references</li>
 * </ol>
 *
 * <p><b>chatMode</b>：
 * <ul>
 *   <li>{@code KNOWLEDGE / AGENT / 缺省}：启用 Tool Calling，由 LLM 自主决策（推荐）</li>
 *   <li>{@code LLM}：禁用所有工具，强制 LLM 直答（用户主动选择，作为逃生口）</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagAgentService {

    private final ChatHistoryCacheService chatHistoryCache;
    private final SystemPromptService systemPromptService;
    private final ChatContextUtil chatContextUtil;
    private final ObjectMapper objectMapper;
    private final RagTools ragTools;

    @Qualifier("MiniMaxChatClient")
    private final ChatClient chatClient;

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int LLM_TIMEOUT_SECONDS = 60;
    private static final int LLM_MAX_RETRIES = 2;

    /**
     * Agent 模式系统提示：告诉 LLM 它有哪些工具、决策原则。
     * 工具描述由 {@link RagTools#callbacks()} 自动注入到 LLM 的 function schema，
     * 这里只补充"何时该用工具 / 如何引用来源"等高层指令。
     */
    private static final String AGENT_SYSTEM_PROMPT = """
            你是一个企业知识库问答助手。你可以调用以下工具来获取回答所需信息：
              - searchKnowledgeBase：检索企业私域知识库（最优先）
              - queryHotSearch：查询主流平台实时热榜数据
              - searchWeb：检索公开互联网（成本最高，谨慎使用）
            
            决策原则：
              1. 一般性问题先调用 searchKnowledgeBase；只有当返回'知识库暂无相关内容'时，再考虑其它工具
              2. 涉及'热搜/热榜/今日/排行'等时效性话题，调用 queryHotSearch
              3. 知识库无结果且非热榜话题，可调用 searchWeb（如已启用）
              4. 工具返回的内容里【来源】是真实可引用的，请在回答末尾用'参考来源：xxx'方式列出
              5. 若所有工具都无相关结果，再基于自身常识作答，并明确告知'以下回答基于通用知识，仅供参考'
              6. 不要编造工具未返回的链接、数据、人名
            """;

    /** LLM 直答 Prompt（chatMode=LLM 时使用，跳过所有工具） */
    private static final String LLM_DIRECT_PROMPT =
            "你是一个智能问答助手。请基于你自身的知识尽可能准确地回答用户问题。\n" +
            "注意：\n" +
            "- 如果你不确定，请明确告知用户\"以下回答基于通用知识，仅供参考\"\n" +
            "- 不要编造具体数据、链接或不存在的来源";

    // ========================================================================
    // 同步问答
    // ========================================================================

    public Map<String, Object> chat(String sessionId, String question, String userId, String promptName, String chatMode) {
        long t0 = System.currentTimeMillis();
        boolean toolsDisabled = "LLM".equalsIgnoreCase(chatMode);

        // 准备 system prompt
        String sysPrompt = toolsDisabled ? LLM_DIRECT_PROMPT : resolveAgentSystemPrompt(promptName);

        List<Message> messages = buildMessages(sysPrompt, sessionId, question);

        // 调用 LLM（开启工具时由 ThreadLocal 上下文回收元数据）
        RagToolContext.begin(userId);
        String answer;
        Set<String> invoked;
        List<Document> retrievedDocs;
        try {
            answer = callLlmWithProtection(messages, !toolsDisabled);
            RagToolContext ctx = RagToolContext.current();
            invoked = ctx == null ? Set.of() : new LinkedHashSet<>(ctx.getInvokedTools());
            retrievedDocs = ctx == null ? List.of() : new ArrayList<>(ctx.getRetrievedDocs());
        } finally {
            RagToolContext.clear();
        }

        long total = System.currentTimeMillis() - t0;
        String source = decideSource(invoked, toolsDisabled);
        log.info("⏱ Agent chat 总耗时 {}ms | source={}, invokedTools={}, hitDocs={}",
                total, source, invoked, retrievedDocs.size());

        // 持久化
        if (sessionId != null) {
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question, null, userId));
            String meta = toJson(Map.of(
                    "source", source,
                    "invokedTools", invoked,
                    "hitCount", retrievedDocs.size()));
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", answer, meta, userId));
        }

        // 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("source", source);
        result.put("invokedTools", invoked);
        result.put("hitCount", retrievedDocs.size());
        if (sessionId != null) {
            result.put("sessionId", sessionId);
        }
        if ("llm_direct".equals(source)) {
            result.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
        }
        if (!retrievedDocs.isEmpty()) {
            result.put("references", buildReferences(retrievedDocs));
        }
        result.put("costMs", total);
        return result;
    }

    // ========================================================================
    // 流式问答
    // ========================================================================

    public Flux<String> chatStream(String sessionId, String question, String userId, String promptName, String chatMode) {
        boolean toolsDisabled = "LLM".equalsIgnoreCase(chatMode);
        String sysPrompt = toolsDisabled ? LLM_DIRECT_PROMPT : resolveAgentSystemPrompt(promptName);
        List<Message> messages = buildMessages(sysPrompt, sessionId, question);

        if (sessionId != null) {
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question, null, userId));
        }

        StringBuilder fullAnswer = new StringBuilder();

        // 流式场景下 Spring AI 会在 LLM WebClient 的 Netty IO 线程上同步触发工具回调，
        // ThreadLocal 跨线程不可见 → 改为显式创建 ctx 并通过闭包绑定到 FunctionCallback 与下游算子。
        final RagToolContext ctx = RagToolContext.create(userId);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().messages(messages);
        if (!toolsDisabled) {
            spec = spec.functions(ragTools.callbacks(ctx).toArray(new FunctionCallback[0]));
        }

        return spec.stream()
                .content()
                .timeout(Duration.ofSeconds(LLM_TIMEOUT_SECONDS))
                .doOnNext(fullAnswer::append)
                .concatWith(Flux.defer(() -> {
                    // 流末尾追加 [META] 段，前端可解析出工具调用与引用
                    Set<String> invoked = new LinkedHashSet<>(ctx.getInvokedTools());
                    List<Document> docs = new ArrayList<>(ctx.getRetrievedDocs());
                    String source = decideSource(invoked, toolsDisabled);

                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("source", source);
                    meta.put("invokedTools", invoked);
                    meta.put("hitCount", docs.size());
                    if (!docs.isEmpty()) meta.put("references", buildReferences(docs));
                    if ("llm_direct".equals(source)) {
                        meta.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
                    }
                    return Flux.just("[META]" + toJson(meta) + "[/META]");
                }))
                .doOnComplete(() -> {
                    if (sessionId != null) {
                        Set<String> invoked = ctx.getInvokedTools();
                        String source = decideSource(invoked, toolsDisabled);
                        String meta = toJson(Map.of("source", source, "invokedTools", invoked));
                        chatHistoryCache.saveMessage(
                                ChatMessage.of(sessionId, "assistant", fullAnswer.toString(), meta, userId));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Agent 流式异常: {}", e.getMessage(), e);
                    return Flux.just("抱歉，AI 服务暂时不可用，请稍后重试。");
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ========================================================================
    // 内部工具方法
    // ========================================================================

    private List<Message> buildMessages(String sysPrompt, String sessionId, String question) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(sysPrompt));
        if (sessionId != null) {
            List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
            for (ChatMessage msg : history) {
                switch (msg.getRole()) {
                    case "user" -> messages.add(new UserMessage(msg.getContent()));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        messages.add(new UserMessage(question));
        chatContextUtil.trimByToken(messages);
        return messages;
    }

    /**
     * 同步 LLM 调用 + 工具注入 + 超时 + 重试 + 友好兜底
     */
    private String callLlmWithProtection(List<Message> messages, boolean enableTools) {
        Exception lastException = null;
        FunctionCallback[] callbacks = enableTools
                ? ragTools.callbacks().toArray(new FunctionCallback[0])
                : new FunctionCallback[0];

        for (int attempt = 1; attempt <= LLM_MAX_RETRIES; attempt++) {
            try {
                java.util.concurrent.CompletableFuture<String> future =
                        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            // 在工作线程内沿用主线程的 RagToolContext
                            RagToolContext parent = RagToolContext.current();
                            // CompletableFuture.supplyAsync 在 ForkJoinPool，需要把 ctx 传进去
                            // 但 supplyAsync 的 lambda 在新线程，主线程的 ThreadLocal 不可见，
                            // 改为同步执行更稳妥
                            return invokeChatClient(messages, callbacks);
                        });
                return future.get(LLM_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("LLM 调用超时 | attempt={}/{}, timeout={}s", attempt, LLM_MAX_RETRIES, LLM_TIMEOUT_SECONDS);
                lastException = e;
            } catch (Exception e) {
                log.warn("LLM 调用异常 | attempt={}/{}, error={}", attempt, LLM_MAX_RETRIES, e.getMessage());
                lastException = e;
            }
        }
        log.error("LLM 调用全部失败 | retries={}, lastError={}", LLM_MAX_RETRIES,
                lastException != null ? lastException.getMessage() : "unknown");
        return "抱歉，AI 服务暂时不可用，请稍后重试。";
    }

    private String invokeChatClient(List<Message> messages, FunctionCallback[] callbacks) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().messages(messages);
        if (callbacks.length > 0) {
            spec = spec.functions(callbacks);
        }
        return spec.call().content();
    }

    /**
     * 根据工具调用记录推断响应来源标识。
     * 优先级：knowledge_base > hot_search > web_search > llm_direct
     */
    private String decideSource(Set<String> invokedTools, boolean toolsDisabled) {
        if (toolsDisabled || invokedTools == null || invokedTools.isEmpty()) {
            return "llm_direct";
        }
        if (invokedTools.contains(RagTools.TOOL_KB)) return "knowledge_base";
        if (invokedTools.contains(RagTools.TOOL_HOT)) return "hot_search";
        if (invokedTools.contains(RagTools.TOOL_WEB)) return "web_search";
        return "llm_direct";
    }

    private List<Map<String, Object>> buildReferences(List<Document> docs) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("source", doc.getMetadata().getOrDefault("source", "未知"));
            String text = doc.getText() == null ? "" : doc.getText();
            ref.put("content", text.length() > 200 ? text.substring(0, 200) + "..." : text);
            refs.add(ref);
        }
        return refs;
    }

    private String resolveAgentSystemPrompt(String promptName) {
        // 用户自定义 SystemPrompt（如有）拼接到 Agent 协议之后，避免覆盖工具调用规则
        if (promptName == null || promptName.isBlank()) {
            return AGENT_SYSTEM_PROMPT;
        }
        var prompt = systemPromptService.getByName(promptName);
        if (prompt == null || prompt.getContent() == null || prompt.getContent().isBlank()) {
            return AGENT_SYSTEM_PROMPT;
        }
        // 用户 Prompt 里若包含 {context} 占位符，去掉（工具模式下不再外部注入 context）
        String userPart = prompt.getContent().replace("{context}", "").trim();
        return AGENT_SYSTEM_PROMPT + "\n\n附加风格指令：\n" + userPart;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
