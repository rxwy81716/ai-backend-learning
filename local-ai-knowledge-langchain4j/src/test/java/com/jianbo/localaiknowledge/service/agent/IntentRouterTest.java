package com.jianbo.localaiknowledge.service.agent;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * IntentRouter 单元测试 — LangChain4j 版。
 *
 * <h3>与 Spring AI 版的差异</h3>
 * <ul>
 *   <li>mock 对象从 {@code ChatModel} → {@code ChatModel}</li>
 *   <li>LLM 调用从 {@code chatModel.call(Prompt)} → {@code chatModel.chat(String)}</li>
 *   <li>返回值从 {@code ChatResponse} → 直接返回 {@code String}</li>
 * </ul>
 */
class IntentRouterTest {

    private ChatModel chatModel;
    private IntentRouter router;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        router = new IntentRouter(chatModel);
    }

    // ===== 快速关键词匹配 =====

    @Nested
    @DisplayName("快速关键词匹配（不应触发 LLM）")
    class FastMatch {

        @ParameterizedTest(name = "[{index}] {0} → {1}")
        @CsvSource({
            "文档概览一下, DOCUMENT_OVERVIEW",
            "有哪些文档, DOCUMENT_OVERVIEW",
            "搜索文档中的RAG, DOCUMENT_SEARCH",
            "查找文档里的方案, DOCUMENT_SEARCH",
            "微博热搜有什么, HOT_SEARCH",
            "知乎热榜今天, HOT_SEARCH",
            "GitHub trending, HOT_SEARCH",
            "你是谁, CHAT",
            "你好啊, CHAT",
            "闲聊一下, CHAT"
        })
        void shouldRouteByKeyword(String question, String expectedType) {
            AgentType result = router.route(question);
            assertThat(result).isEqualTo(AgentType.valueOf(expectedType));
            verifyNoInteractions(chatModel);
        }
    }

    // ===== LLM 兜底分类 =====

    @Nested
    @DisplayName("LLM 兜底分类（关键词未命中时）")
    class LlmClassify {

        @ParameterizedTest(name = "[{index}] LLM 返回 \"{0}\" → {1}")
        @CsvSource({
            "KNOWLEDGE, KNOWLEDGE",
            "DOCUMENT_OVERVIEW, DOCUMENT_OVERVIEW",
            "DOCUMENT_SEARCH, DOCUMENT_SEARCH",
            "HOT_SEARCH, HOT_SEARCH",
            "CHAT, CHAT",
            "PLANNER, PLANNER",
            // 未知标签 → fallback KNOWLEDGE
            "GIBBERISH, KNOWLEDGE",
            "UNKNOWN_TYPE, KNOWLEDGE"
        })
        void shouldParseLlmTag(String llmRaw, String expectedType) {
            when(chatModel.chat(anyString())).thenReturn(llmRaw);
            // 用一个不会被关键词命中的问题
            AgentType result = router.route("如何配置 pgvector 的 HNSW 参数？");
            assertThat(result).isEqualTo(AgentType.valueOf(expectedType));
            verify(chatModel, times(1)).chat(anyString());
        }

        @Test
        @DisplayName("LLM 抛异常时 fallback 到 KNOWLEDGE")
        void shouldFallbackOnLlmException() {
            when(chatModel.chat(anyString())).thenThrow(new RuntimeException("模型不可用"));
            AgentType result = router.route("某个不会被关键词命中的奇怪问题");
            assertThat(result).isEqualTo(AgentType.KNOWLEDGE);
        }

        @Test
        @DisplayName("LLM 调用时应携带用户问题")
        void shouldPassQuestionToLlm() {
            when(chatModel.chat(anyString())).thenReturn("KNOWLEDGE");
            router.route("unique-query-marker");
            verify(chatModel).chat(contains("unique-query-marker"));
        }
    }

    // ===== 边界 =====

    @Test
    @DisplayName("大小写无关：'trending' 命中 HOT_SEARCH")
    void caseInsensitive() {
        AgentType result = router.route("GitHub Trending 推荐");
        assertThat(result).isEqualTo(AgentType.HOT_SEARCH);
        verifyNoInteractions(chatModel);
    }
}
