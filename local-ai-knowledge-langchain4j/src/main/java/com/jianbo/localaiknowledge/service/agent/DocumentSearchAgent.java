package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import com.jianbo.localaiknowledge.service.EsKeywordSearchService;
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
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentSearchAgent implements SpecializedAgent {

    private final StreamingChatModel streamingModel;
    private final ChatModel chatModel;
    private final EsKeywordSearchService keywordSearchService;

    private static final String SYSTEM_PROMPT = """
            你是一个文档搜索助手，擅长从知识库中检索和整理信息。
            请基于搜索结果，按文档分组展示，标注来源。""";

    @Override public AgentType type() { return AgentType.DOCUMENT_SEARCH; }
    @Override public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        request.toolContext().emitStep("正在提取搜索关键词...");

        // 用同步模型提取关键词
        String extractPrompt = "从以下问题中提取1-3个核心搜索关键词，只输出关键词，用空格分隔：\n" + request.question();
        String keywords = chatModel.chat(extractPrompt);

        if (keywords == null || keywords.isBlank()) {
            keywords = request.question();
        }

        request.toolContext().emitStep("正在搜索文档: " + keywords);
        var docs = keywordSearchService.search(keywords.trim(), request.userId(), 10);

        List<ChatMessage> messages = new ArrayList<>(request.messages());
        if (!docs.isEmpty()) {
            StringBuilder sb = new StringBuilder("【文档搜索结果】\n");
            // 按来源分组
            var grouped = docs.stream().collect(Collectors.groupingBy(
                    d -> d.getMetadata().getOrDefault("source", "未知").toString()));
            for (var entry : grouped.entrySet()) {
                sb.append("\n📄 ").append(entry.getKey()).append(":\n");
                for (var doc : entry.getValue()) {
                    sb.append("  - ").append(doc.getContent(), 0, Math.min(200, doc.getContent().length())).append("...\n");
                }
            }
            messages.add(messages.size() - 1, UserMessage.from(sb.toString()));
        } else {
            messages.add(messages.size() - 1, UserMessage.from("【搜索结果为空】未找到匹配的文档内容。"));
        }

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(ChatRequest.builder().messages(messages).build(), handler));
    }
}
