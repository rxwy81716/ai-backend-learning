package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 通用对话 Agent（对标 Spring AI 版 ChatAgent）。
 *
 * <h2>核心差异：流式调用方式</h2>
 * <pre>
 * Spring AI:
 *   return chatClient.prompt()
 *       .system(systemPrompt)
 *       .messages(messages)
 *       .stream().content();          // 直接返回 Flux&lt;String&gt;
 *
 * LangChain4j:
 *   return TokenStreamFluxAdapter.toFlux(handler ->
 *       streamingModel.chat(chatRequest, handler)  // 回调式
 *   );
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ChatAgent implements SpecializedAgent {

    private final StreamingChatModel streamingModel;

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手。请用准确、简洁的中文回答用户问题。
            如果不确定答案，请明确告知用户"仅供参考"。
            不要编造事实，保持诚实。""";

    @Override
    public AgentType type() { return AgentType.CHAT; }

    @Override
    public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(request.messages())
                .build();

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(chatRequest, handler)
        );
    }
}
