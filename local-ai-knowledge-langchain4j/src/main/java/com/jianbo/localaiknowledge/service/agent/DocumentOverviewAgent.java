package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.adapter.TokenStreamFluxAdapter;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentTask;
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
public class DocumentOverviewAgent implements SpecializedAgent {

    private final StreamingChatModel streamingModel;
    private final DocumentTaskMapper taskMapper;
    private final HybridSearchService hybridSearchService;

    private static final String SYSTEM_PROMPT = """
            你是一个知识库管理助手。请基于提供的文档列表和内容采样，
            给用户一个清晰的知识库概览，包括每个文档的主题和关键内容。""";

    @Override public AgentType type() { return AgentType.DOCUMENT_OVERVIEW; }
    @Override public String systemPrompt() { return SYSTEM_PROMPT; }

    @Override
    public Flux<String> execute(AgentRequest request) {
        request.toolContext().emitStep("正在加载文档列表...");
        List<DocumentTask> tasks = taskMapper.selectAccessibleTasks(request.userId());

        if (tasks.isEmpty()) {
            return Flux.just("您的知识库中暂无文档。请先上传文档后再查看概览。");
        }

        StringBuilder context = new StringBuilder("【知识库文档列表】\n");
        for (int i = 0; i < tasks.size(); i++) {
            DocumentTask t = tasks.get(i);
            context.append(String.format("%d. %s（%s, %d 个分片）\n",
                    i + 1, t.getFileName(), t.getDocScope(), t.getTotalChunks()));
        }

        // 每个文档取一个代表性片段
        request.toolContext().emitStep("正在采样文档内容...");
        for (DocumentTask t : tasks) {
            var sample = hybridSearchService.searchWithOwnership(t.getFileName(), request.userId(), 1);
            if (!sample.isEmpty()) {
                context.append("\n📄 ").append(t.getFileName()).append(" 内容采样：\n");
                context.append(sample.get(0).getContent(), 0, Math.min(300, sample.get(0).getContent().length()));
                context.append("...\n");
            }
        }

        List<ChatMessage> messages = new ArrayList<>(request.messages());
        messages.add(messages.size() - 1, UserMessage.from(context.toString()));

        return TokenStreamFluxAdapter.toFlux(handler ->
                streamingModel.chat(ChatRequest.builder().messages(messages).build(), handler));
    }
}
