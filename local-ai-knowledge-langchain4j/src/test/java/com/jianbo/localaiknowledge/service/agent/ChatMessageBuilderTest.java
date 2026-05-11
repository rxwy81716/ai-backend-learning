package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ChatMessageBuilder 单元测试 — LangChain4j 版。
 *
 * <h3>验证要点</h3>
 * <ul>
 *   <li>消息序列：[SystemMessage, ...history, UserMessage]</li>
 *   <li>history 中 role=user → UserMessage, role=assistant → AiMessage</li>
 *   <li>promptName 优先从 DB 加载，fallback 到 agent 内置</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageBuilderTest {

    @Mock ChatHistoryCacheService historyService;
    @Mock SystemPromptService systemPromptService;

    private ChatMessageBuilder builder;

    private final SpecializedAgent mockAgent = new SpecializedAgent() {
        @Override public AgentType type() { return AgentType.CHAT; }
        @Override public String systemPrompt() { return "你是一个通用助手"; }
        @Override public Flux<String> execute(AgentRequest request) { return Flux.empty(); }
    };

    @BeforeEach
    void setUp() {
        builder = new ChatMessageBuilder(historyService, systemPromptService, new ChatContextUtil());
    }

    @Test
    @DisplayName("基本构建：SystemMessage + 历史 + 当前问题")
    void basicBuild() {
        ChatMessage h1 = ChatMessage.of("s1", "u1", "user", "之前的问题", null);
        ChatMessage h2 = ChatMessage.of("s1", "u1", "assistant", "之前的回答", null);
        when(historyService.loadHistory("s1")).thenReturn(List.of(h1, h2));
        when(systemPromptService.getByName(anyString())).thenReturn(null);
        when(systemPromptService.getDefault()).thenReturn(null);

        var messages = builder.build("s1", "u1", "新问题", mockAgent, "", "CHAT");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);

        // 最后一条是当前问题
        assertThat(((UserMessage) messages.get(3)).singleText()).isEqualTo("新问题");
    }

    @Test
    @DisplayName("无历史：只有 SystemMessage + UserMessage")
    void noHistory() {
        when(historyService.loadHistory("s1")).thenReturn(List.of());
        when(systemPromptService.getDefault()).thenReturn(null);

        var messages = builder.build("s1", "u1", "问题", mockAgent, null, "CHAT");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("自定义 promptName → 追加到 agent systemPrompt 后")
    void customPrompt() {
        when(historyService.loadHistory("s1")).thenReturn(List.of());
        SystemPrompt sp = new SystemPrompt();
        sp.setContent("请用简洁风格回答");
        when(systemPromptService.getByName("concise")).thenReturn(sp);

        var messages = builder.build("s1", "u1", "问题", mockAgent, "concise", "CHAT");

        String sysText = ((SystemMessage) messages.get(0)).text();
        assertThat(sysText).contains("你是一个通用助手");
        assertThat(sysText).contains("请用简洁风格回答");
    }
}
