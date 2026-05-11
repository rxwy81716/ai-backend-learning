package com.jianbo.localaiknowledge.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatContextUtil 单元测试 — LangChain4j 版。
 *
 * <h3>与 Spring AI 版差异</h3>
 * <ul>
 *   <li>消息类型：SystemMessage / UserMessage / AiMessage (不是 AssistantMessage)</li>
 *   <li>底层 tokenizer 相同（JTokkit cl100k_base），结果一致</li>
 * </ul>
 */
class ChatContextUtilTest {

    private final ChatContextUtil util = new ChatContextUtil();

    @Test
    @DisplayName("estimate：非空字符串应返回正数")
    void estimatePositive() {
        assertThat(util.estimate("Hello World")).isGreaterThan(0);
    }

    @Test
    @DisplayName("estimate：空/null 应返回 0")
    void estimateEmpty() {
        assertThat(util.estimate(null)).isEqualTo(0);
        assertThat(util.estimate("")).isEqualTo(0);
    }

    @Test
    @DisplayName("中文 token 计数合理（中文约 1-2 token/字）")
    void estimateChinese() {
        int count = util.estimate("今天天气不错");
        assertThat(count).isBetween(3, 12);
    }

    @Test
    @DisplayName("countTotalToken：多条消息之和")
    void countTotal() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("你是一个助手"),
                UserMessage.from("你好"),
                AiMessage.from("你好！有什么可以帮你的？")
        );
        int total = util.countTotalToken(messages);
        assertThat(total).isGreaterThan(5);
    }

    @Test
    @DisplayName("trimByToken：超限时删除最早的非系统消息对")
    void trimShouldRemoveOldestPairs() {
        // 构造超长上下文
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("System prompt"));
        for (int i = 0; i < 100; i++) {
            messages.add(UserMessage.from("User message " + i + " with some padding text to increase token count"));
            messages.add(AiMessage.from("AI response " + i + " with some padding text to increase token count"));
        }

        int before = messages.size();
        util.trimByToken(messages);
        int after = messages.size();

        // 应删除了一些对，但保留了 SystemMessage
        assertThat(after).isLessThan(before);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(util.countTotalToken(messages)).isLessThanOrEqualTo(ChatContextUtil.SAFE_TOKEN_LIMIT);
    }

    @Test
    @DisplayName("trimByToken：未超限时不删除")
    void trimShouldNotRemoveWhenUnderLimit() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("sys"));
        messages.add(UserMessage.from("你好"));
        messages.add(AiMessage.from("你好！"));

        int before = messages.size();
        util.trimByToken(messages);
        assertThat(messages).hasSize(before);
    }

    @Test
    @DisplayName("getContent：各消息类型都能提取文本")
    void getContent() {
        assertThat(ChatContextUtil.getContent(SystemMessage.from("sys"))).isEqualTo("sys");
        assertThat(ChatContextUtil.getContent(UserMessage.from("user"))).isEqualTo("user");
        assertThat(ChatContextUtil.getContent(AiMessage.from("ai"))).isEqualTo("ai");
    }
}
