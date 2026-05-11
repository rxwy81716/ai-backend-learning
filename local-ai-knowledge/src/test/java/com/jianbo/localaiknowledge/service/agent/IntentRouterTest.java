package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link IntentRouter} 路由规则单元测试。
 *
 * <p>关键约束：
 *
 * <ul>
 *   <li>关键词快速匹配命中时绝不调用 ChatModel（性能 + 离线可用）
 *   <li>规划类关键词优先于知识库 fallback（PlannerAgent 启动门槛）
 *   <li>LLM 兜底分类对未知 tag 应 fallback 到 KNOWLEDGE
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
        // 规划类
        "规划一下怎么完成知识库总结, PLANNER",
        "请帮我多步拆解这个问题, PLANNER",
        "step by step 分析一下, PLANNER",
        "先检索再回答, PLANNER",
        // 热榜
        "B站今天有什么热门视频, HOT_SEARCH",
        "知乎热榜, HOT_SEARCH",
        "GitHub trending 项目推荐, HOT_SEARCH",
        // 文档内搜索
        "搜索包含 RAG 的文档, DOCUMENT_SEARCH",
        "查找提到 PlannerAgent 的文档, DOCUMENT_SEARCH",
        // 文档概览
        "总结知识库, DOCUMENT_OVERVIEW",
        "知识库里有什么, DOCUMENT_OVERVIEW",
        // 通用任务（写代码/翻译/写作）
        "帮我写一段python代码, CHAT",
        "写代码实现冒泡排序, CHAT",
        "帮我翻译这句话成英文, CHAT",
        "写一篇关于春天的作文, CHAT",
        "帮我写个故事, CHAT",
        "算一下 123 * 456, CHAT",
        // 元/闲聊
        "你是谁, CHAT",
        "你能做什么, CHAT",
        "你好, CHAT",
        "hello, CHAT"
    })
    void shouldRouteByKeyword(String question, String expectedAgentType) {
      AgentType result = router.route(question);
      assertThat(result).isEqualTo(AgentType.valueOf(expectedAgentType));
      verifyNoInteractions(chatModel);  // 关键：不应调用 LLM
    }

    @Test
    @DisplayName("PLAN_KEYWORDS 应该优先于 OVERVIEW_KEYWORDS（同时命中时取 PLANNER）")
    void planKeywordTakesPrecedenceOverOverview() {
      // "规划一下" + "总结知识库" 同时命中，按代码顺序 PLAN_KEYWORDS 先匹配
      AgentType result = router.route("规划一下，帮我总结知识库");
      assertThat(result).isEqualTo(AgentType.PLANNER);
      verifyNoInteractions(chatModel);
    }
  }

  // ===== LLM 兜底分类 =====

  @Nested
  @DisplayName("LLM 兜底分类（关键词未命中时）")
  class LlmClassify {

    @ParameterizedTest(name = "[{index}] LLM 返回 \"{0}\" → {1}")
    @CsvSource({
        "KNOWLEDGE,         KNOWLEDGE",
        "DOCUMENT_OVERVIEW, DOCUMENT_OVERVIEW",
        "DOCUMENT_SEARCH,   DOCUMENT_SEARCH",
        "HOT_SEARCH,        HOT_SEARCH",
        "CHAT,              CHAT",
        // 容错：标点/换行/小写 都应被剥离
        "  knowledge.,      KNOWLEDGE",
        "'KNOWLEDGE\n',     KNOWLEDGE",
        // 未知标签 → fallback KNOWLEDGE
        "GIBBERISH,         KNOWLEDGE",
        "PLANNER,           KNOWLEDGE"  // LLM 不应返回 PLANNER（仅靠关键词触发），返回了也兜底到 KNOWLEDGE
    })
    void shouldParseLlmTag(String llmRaw, String expectedAgentType) {
      mockChatModelReturning(llmRaw);
      // 用一个不会被任何快速规则命中的问题
      AgentType result = router.route("如何配置 pgvector 的 HNSW 参数？");
      assertThat(result).isEqualTo(AgentType.valueOf(expectedAgentType));
      verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("LLM 抛异常时 fallback 到 KNOWLEDGE")
    void shouldFallbackOnLlmException() {
      when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("模型不可用"));
      AgentType result = router.route("某个不会被关键词命中的奇怪问题");
      assertThat(result).isEqualTo(AgentType.KNOWLEDGE);
    }

    @Test
    @DisplayName("LLM 返回空串时 fallback 到 KNOWLEDGE")
    void shouldFallbackOnBlankLlmResult() {
      mockChatModelReturning("   ");
      AgentType result = router.route("某个不会被关键词命中的奇怪问题");
      assertThat(result).isEqualTo(AgentType.KNOWLEDGE);
    }

    @Test
    @DisplayName("调用 LLM 时应携带 router system prompt 与 user question")
    void shouldPassQuestionToLlm() {
      mockChatModelReturning("KNOWLEDGE");
      router.route("一个独特的奇怪问题 unique-query-marker");
      ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
      verify(chatModel).call(captor.capture());
      assertThat(captor.getValue().getInstructions().toString())
          .contains("unique-query-marker");
    }
  }

  // ===== 边界 =====

  @Test
  @DisplayName("大小写无关：'STEP BY STEP' 应同样命中 PLANNER")
  void caseInsensitive() {
    AgentType result = router.route("Please STEP BY STEP analyze this");
    assertThat(result).isEqualTo(AgentType.PLANNER);
    verifyNoInteractions(chatModel);
  }

  // ===== helpers =====

  private void mockChatModelReturning(String raw) {
    AssistantMessage msg = new AssistantMessage(raw);
    Generation gen = new Generation(msg);
    ChatResponse resp = new ChatResponse(List.of(gen));
    when(chatModel.call(any(Prompt.class))).thenReturn(resp);
  }
}
