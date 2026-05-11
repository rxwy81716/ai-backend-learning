package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import com.jianbo.localaiknowledge.service.HotSearchService;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class HotSearchAgent implements SpecializedAgent {

    private final StreamingChatModel streamingModel;
    private final HotSearchService hotSearchService;

    private static final String SYSTEM_PROMPT = """
            你是一个热点资讯助手，擅长整理和分析各平台热搜热榜信息。
            请基于提供的热搜数据，以清晰的格式呈现，并适当添加分析。""";

    @Override public AgentType type() { return AgentType.HOT_SEARCH; }
    @Override public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        request.toolContext().emitStep("正在获取热搜数据...");
        String hotData = hotSearchService.queryAndFormat(request.question());

        List<ChatMessage> messages = new ArrayList<>(request.messages());
        if (hotData != null && !hotData.isBlank()) {
            messages.add(messages.size() - 1, UserMessage.from("【热搜数据】\n" + hotData));
        }

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(ChatRequest.builder().messages(messages).build(), handler));
    }
}
