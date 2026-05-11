package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.utils.RagFormatUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库 Agent（对标 Spring AI 版 KnowledgeAgent）。
 *
 * <h2>Spring AI 版实现方式</h2>
 * <pre>
 *   chatClient.prompt()
 *       .system(systemPrompt)
 *       .messages(messages)
 *       .tools(knowledgeTools)          // Spring AI 自动触发 @Tool 方法
 *       .toolContext(Map.of("userId", userId))
 *       .stream().content();
 * </pre>
 *
 * <h2>LangChain4j 版实现方式</h2>
 * <p>LangChain4j 流式模型不直接支持 tool binding + streaming 同时使用。
 * 解决方案：先用同步模型调 tool 拿到检索结果，再用流式模型生成回答。
 * 这与原版的"Spring AI 自动拦截 tool call 后继续流式"效果一致。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeAgent implements SpecializedAgent {

    private final StreamingChatModel streamingModel;
    private final HybridSearchService hybridSearchService;

    private static final String SYSTEM_PROMPT = """
            你是一个企业知识库问答助手。请严格基于【知识库检索结果】回答问题。
            规则：
            1. 只使用检索结果中的信息，禁止用训练数据脑补
            2. 如果检索结果不足以回答，明确说明"根据现有资料无法完整回答"
            3. 回答要准确、有条理，使用 Markdown 格式
            4. 禁止输出来源文件名标签""";

    @Override
    public AgentType type() { return AgentType.KNOWLEDGE; }

    @Override
    public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        RagToolContext ctx = request.toolContext();
        String query = request.question();
        String userId = request.userId();

        // 1. 先检索
        ctx.emitStep("正在检索知识库...");
        var docs = hybridSearchService.searchWithOwnership(query, userId, 5);

        // 2. 检索为空 → 去元词重试
        if (docs.isEmpty()) {
            String stripped = query.replaceAll("(?i)(请|帮我|介绍一下|什么是|如何|怎么)", "").trim();
            if (!stripped.isBlank() && !stripped.equals(query)) {
                ctx.emitStep("扩展检索中...");
                docs = hybridSearchService.searchWithOwnership(stripped, userId, 5);
            }
        }

        // 3. 注入检索结果到消息列表
        List<ChatMessage> messages = new ArrayList<>(request.messages());
        if (!docs.isEmpty()) {
            for (var doc : docs) {
                ctx.addRetrievedDoc(
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getContent());
            }
            String context = RagFormatUtil.formatDocs(docs);
            // 把检索结果作为最后一条 user message 之前的 context
            messages.add(messages.size() - 1, UserMessage.from("【知识库检索结果】\n" + context));
            ctx.addInvokedTool("searchKnowledgeBase(" + query + ")");
        } else {
            messages.add(messages.size() - 1,
                    UserMessage.from("【知识库检索结果为空】请基于通用知识回答，并注明\"此回答未经知识库验证\"。"));
        }

        // 4. 流式回答
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(chatRequest, handler)
        );
    }
}
