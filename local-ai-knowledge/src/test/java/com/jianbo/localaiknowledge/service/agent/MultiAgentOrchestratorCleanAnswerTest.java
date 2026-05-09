package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MultiAgentOrchestrator#cleanAnswer(String)} 后处理回归测试。
 *
 * <p>cleanAnswer 用 6 个串联正则擦掉 LLM 输出里不希望持久化到对话历史的内容：
 * <ul>
 *   <li>{@code <think>...</think>} 推理块（思考型模型如 GLM-Z1 / DeepSeek-R1 输出）
 *   <li>{@code [STEP]...[/STEP]} SSE 协议的执行进度事件
 *   <li>{@code 【运行时上下文】/【执行工具】/【工具结果】/【工具调用】} 元描述行
 *   <li>{@code [来源: xxx]} 内联标注
 *   <li>"参考来源:" / "参考文档:" / "来源:" 末尾段落
 *   <li>开头"为了回答这个问题，我将检索..."类的工具前导白话
 * </ul>
 *
 * <p>这套正则脆弱，prompt 调整或新模型输出格式变更很容易破坏。本测试覆盖每个分支的代表性
 * 输入，作为修改 prompt / 升级模型时的回归护航。
 */
@DisplayName("MultiAgentOrchestrator.cleanAnswer 后处理")
class MultiAgentOrchestratorCleanAnswerTest {

  @Nested
  @DisplayName("空值与边界")
  class EmptyOrNull {

    @Test
    void nullInputReturnsEmpty() {
      assertThat(MultiAgentOrchestrator.cleanAnswer(null)).isEmpty();
    }

    @Test
    void emptyInputReturnsEmpty() {
      assertThat(MultiAgentOrchestrator.cleanAnswer("")).isEmpty();
    }

    @Test
    void blankInputReturnsEmptyAfterTrim() {
      assertThat(MultiAgentOrchestrator.cleanAnswer("   \n\n  ")).isEmpty();
    }

    @Test
    void plainAnswerKeptAsIs() {
      String raw = "Spring AI 是 Spring 生态下的 AI 应用开发框架。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo(raw);
    }
  }

  @Nested
  @DisplayName("<think>...</think> 推理块")
  class ThinkBlock {

    @Test
    void singleLineThinkBlockStripped() {
      String raw = "<think>这里是推理</think>正式回答";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("正式回答");
    }

    @Test
    void multiLineThinkBlockStripped() {
      String raw = "<think>\n用户问 X\n所以我要查 Y\n</think>\n答：Y 是 Z。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答：Y 是 Z。");
    }

    @Test
    void multipleThinkBlocksStripped() {
      String raw = "<think>第一段思考</think>开头<think>第二段思考</think>结尾";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("开头结尾");
    }
  }

  @Nested
  @DisplayName("[STEP]...[/STEP] SSE 进度事件")
  class StepBlock {

    @Test
    void stepEventStripped() {
      String raw = "[STEP]{\"type\":\"route\",\"intent\":\"KNOWLEDGE\"}[/STEP]文档显示...";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("文档显示...");
    }

    @Test
    void multipleStepEventsStripped() {
      String raw = "[STEP]{\"type\":\"route\"}[/STEP]" +
          "[STEP]{\"type\":\"tool\",\"phase\":\"start\"}[/STEP]答案。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答案。");
    }
  }

  @Nested
  @DisplayName("元描述行 与 [来源: xxx] 内联标注")
  class MetaAndSourceTag {

    @Test
    void metaLinesStripped() {
      String raw = "【执行工具】searchKnowledgeBase\n答案正文。\n【工具结果】共找到 7 个片段";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答案正文。");
    }

    @Test
    void inlineSourceTagStripped() {
      String raw = "Spring AI 默认走 OpenAI 兼容协议[来源: spring-ai-doc.pdf]，可以接 GLM/DeepSeek。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw))
          .isEqualTo("Spring AI 默认走 OpenAI 兼容协议，可以接 GLM/DeepSeek。");
    }

    @Test
    void multipleInlineSourceTagsStripped() {
      String raw = "ES 走向量检索[来源: a.md]，PG 做兜底[来源: b.md]。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("ES 走向量检索，PG 做兜底。");
    }
  }

  @Nested
  @DisplayName("末尾的 参考来源 / 参考文档 段落")
  class ReferenceFooter {

    @Test
    void referenceFooterStripped() {
      String raw = "答案正文。\n\n参考来源: doc1.pdf, doc2.md";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答案正文。");
    }

    @Test
    void multilineReferenceFooterStripped() {
      String raw = """
          答案正文段落。

          参考文档:
            - 文档A.pdf
            - 文档B.md
          """;
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答案正文段落。");
    }
  }

  @Nested
  @DisplayName("开头的工具调用前导白话")
  class LeadingToolPreamble {

    @Test
    void preambleWithRetrievalIntentStripped() {
      String raw = "为了回答这个问题，我需要先检索一下知识库。Spring AI 的核心是 ChatClient。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw))
          .isEqualTo("Spring AI 的核心是 ChatClient。");
    }

    @Test
    void preambleWithLeadingWoStripped() {
      // 正则要求 "我(需要|将|要|来|得)|让我|我先" 后跟工具动词，本例 "我先 查询一下" 触发匹配
      String raw = "我先查询一下相关资料。答案是 42。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("答案是 42。");
    }

    @Test
    void preambleWithLetMeStripped() {
      String raw = "让我搜索一下相关文档。结果显示 Spring 6.x 已经发布。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("结果显示 Spring 6.x 已经发布。");
    }

    @Test
    void onlyFirstPreambleStripped() {
      // LEADING_TOOL_PREAMBLE 用的是 replaceFirst，正文中类似"我来查询"不应被误删
      String raw = "我先检索一下文档。第一步我来查询 ES 配置，第二步... 答案出来了。";
      String cleaned = MultiAgentOrchestrator.cleanAnswer(raw);
      // 开头的"我先检索一下文档。"应被去掉
      assertThat(cleaned).doesNotStartWith("我先检索");
      // 但正文里第二个"我来查询"应保留
      assertThat(cleaned).contains("第一步我来查询");
    }
  }

  @Nested
  @DisplayName("多种污染共存")
  class Combined {

    @Test
    void thinkPlusStepPlusSourceTagAllStripped() {
      String raw =
          "<think>用户问 RAG，我去查 KB</think>"
              + "[STEP]{\"type\":\"tool\"}[/STEP]"
              + "RAG 全称 Retrieval-Augmented Generation[来源: rag-101.pdf]，核心流程是检索+生成。"
              + "\n\n参考来源: rag-101.pdf";
      String cleaned = MultiAgentOrchestrator.cleanAnswer(raw);
      assertThat(cleaned).isEqualTo("RAG 全称 Retrieval-Augmented Generation，核心流程是检索+生成。");
    }

    @Test
    void multipleNewlinesCollapsed() {
      String raw = "段落一。\n\n\n\n\n段落二。";
      assertThat(MultiAgentOrchestrator.cleanAnswer(raw)).isEqualTo("段落一。\n\n段落二。");
    }
  }
}
