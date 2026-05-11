package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.QueryRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多 Agent 编排器（对标 Spring AI 版 MultiAgentOrchestrator）。
 *
 * <h2>与 Spring AI 版的区别</h2>
 * <p>编排逻辑完全一致（路由 → 消息构建 → Agent 执行 → 后处理 → 持久化），
 * 差异仅在：</p>
 * <ul>
 *   <li>消息类型从 Spring AI Message → LangChain4j ChatMessage</li>
 *   <li>流式返回从 ChatClient.stream() → TokenStreamFluxAdapter</li>
 *   <li>Tool 上下文从 ToolContext → ThreadLocal + RagToolContext</li>
 * </ul>
 */
@Service
@Slf4j
public class MultiAgentOrchestrator {

    private final IntentRouter intentRouter;
    private final ChatMessageBuilder messageBuilder;
    private final MetaBuilder metaBuilder;
    private final QueryRewriteService queryRewriteService;
    private final ChatHistoryCacheService historyService;
    private final Map<AgentType, SpecializedAgent> agentRegistry;

    public MultiAgentOrchestrator(
            IntentRouter intentRouter,
            ChatMessageBuilder messageBuilder,
            MetaBuilder metaBuilder,
            QueryRewriteService queryRewriteService,
            ChatHistoryCacheService historyService,
            List<SpecializedAgent> agents) {
        this.intentRouter = intentRouter;
        this.messageBuilder = messageBuilder;
        this.metaBuilder = metaBuilder;
        this.queryRewriteService = queryRewriteService;
        this.historyService = historyService;
        this.agentRegistry = agents.stream()
                .collect(Collectors.toMap(SpecializedAgent::type, Function.identity()));
        log.info("Agent 注册表初始化完成: {}", agentRegistry.keySet());
    }

    /**
     * 核心流式对话入口（8 步流程与 Spring AI 版一致）。
     */
    public Flux<String> chatStream(String sessionId, String question, String userId,
                                   String chatMode, String promptName, String modelKey,
                                   boolean thinking) {
        return Flux.defer(() -> {
            // 1. 意图路由
            AgentType intent = "KNOWLEDGE".equals(chatMode) ?
                    intentRouter.route(question) : AgentType.CHAT;
            log.info("[Orchestrator] sessionId={}, intent={}, question={}", sessionId, intent, question);

            // 2. 查询改写（多轮对话场景）
            RagToolContext toolContext = new RagToolContext(userId);
            String rewritten = question;
            if (intent == AgentType.KNOWLEDGE || intent == AgentType.PLANNER) {
                rewritten = queryRewriteService.rewrite(question, sessionId);
                if (!rewritten.equals(question)) {
                    toolContext.setRewrittenQuery(rewritten);
                    toolContext.emitStep("查询已改写: " + rewritten);
                }
            }

            // 3. 选择 Agent
            SpecializedAgent agent = agentRegistry.getOrDefault(intent,
                    agentRegistry.get(AgentType.KNOWLEDGE));

            // 4. 构建消息列表
            var messages = messageBuilder.build(
                    sessionId, userId, rewritten, agent, promptName, chatMode);

            // 5. 构建请求
            AgentRequest request = new AgentRequest(
                    sessionId, rewritten, userId, messages, toolContext, thinking);

            // 6. 执行 Agent
            StringBuilder fullResponse = new StringBuilder();
            Flux<String> agentFlux = agent.execute(request)
                    .doOnNext(fullResponse::append);

            // 7. 步骤事件流
            Flux<String> stepFlux = toolContext.getStepSink().asFlux();

            // 8. 合并流 + 追加 META + 持久化
            String metaTag = metaBuilder.build(intent, toolContext, chatMode);

            return Flux.merge(stepFlux, agentFlux)
                    .concatWith(Flux.just(metaTag))
                    .doOnComplete(() -> {
                        String response = fullResponse.toString();
                        if (!response.isBlank()) {
                            historyService.appendMessage(sessionId, userId, "user", question, null);
                            historyService.appendMessage(sessionId, userId, "assistant", response, null);
                        }
                        // 关闭 stepSink
                        toolContext.getStepSink().tryEmitComplete();
                    })
                    .doOnError(e -> {
                        log.error("[Orchestrator] 流式错误: {}", e.getMessage());
                        toolContext.getStepSink().tryEmitComplete();
                    });
        });
    }
}
