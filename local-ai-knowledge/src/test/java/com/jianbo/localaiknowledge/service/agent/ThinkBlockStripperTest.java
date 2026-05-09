package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MultiAgentOrchestrator.ThinkBlockStripper} 单测。
 *
 * <p>该 Stripper 负责从 SSE token 流中剥离 {@code <think>...</think>} 段（推理模式 reasoning），
 * 是流式后处理最脆弱的一环：跨 chunk 切断、嵌套标签、未闭合 tail 任何一处出错都会导致前端把
 * 内部思考过程渲染给用户。本测试覆盖四类典型场景。
 *
 * <p>测试在同一包下，可访问包级可见的 inner class。
 */
class ThinkBlockStripperTest {

  /** 把一段输入按字符级 chunked feed 给 stripper，返回拼接后的最终输出 */
  private static String runChunked(MultiAgentOrchestrator.ThinkBlockStripper s, List<String> chunks) {
    StringBuilder out = new StringBuilder();
    for (String c : chunks) out.append(s.process(c));
    out.append(s.flush());
    return out.toString();
  }

  @Test
  @DisplayName("一次性 chunk 包含完整 <think>…</think>：应被完全剥离")
  void singleChunkRemovesThinkBlock() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String result = runChunked(s, List.of("<think>internal reasoning</think>Hello world"));
    assertThat(result).isEqualTo("Hello world");
  }

  @Test
  @DisplayName("多 chunk 切断 <think> 起始标签：仍能正确识别并剥离")
  void crossChunkOpenTag() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    // "<thi" + "nk>foo</think>real answer"
    String result = runChunked(s, List.of("<thi", "nk>foo</think>real answer"));
    assertThat(result).isEqualTo("real answer");
  }

  @Test
  @DisplayName("多 chunk 切断 </think> 结束标签：仍能正确识别并剥离")
  void crossChunkCloseTag() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    // "<think>secret</thin" + "k>visible"
    String result = runChunked(s, List.of("<think>secret</thin", "k>visible"));
    assertThat(result).isEqualTo("visible");
  }

  @Test
  @DisplayName("Token 级别一字一吐：跨任意位置切都不漏")
  void tokenLevelStreaming() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String input = "<think>aaa</think>BBB<think>ccc</think>DDD";
    List<String> chunks = new java.util.ArrayList<>();
    for (int i = 0; i < input.length(); i++) {
      chunks.add(String.valueOf(input.charAt(i)));
    }
    String result = runChunked(s, chunks);
    assertThat(result).isEqualTo("BBBDDD");
  }

  @Test
  @DisplayName("两个连续 think 块之间夹着正文：正文应保留")
  void multipleThinkBlocks() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String result = runChunked(s,
        List.of("prefix<think>r1</think>middle<think>r2</think>suffix"));
    assertThat(result).isEqualTo("prefixmiddlesuffix");
  }

  @Test
  @DisplayName("流尾出现未闭合 <think>：buffer 中残留应被 flush 丢弃")
  void unclosedThinkAtTail() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String result = runChunked(s, List.of("answer text<think>incomplete"));
    assertThat(result).isEqualTo("answer text");
  }

  @Test
  @DisplayName("纯 think 块（整段都是思考）：输出应为空字符串")
  void pureThinkBlock() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String result = runChunked(s, List.of("<think>only reasoning here</think>"));
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("起始空白：think 闭合后正文前导空白应被裁掉（防止首 token 是空格/换行）")
  void leadingWhitespaceTrimmedAfterThink() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String result = runChunked(s, List.of("<think>r</think>\n\n  Hello"));
    assertThat(result).isEqualTo("Hello");
  }

  @Test
  @DisplayName("一旦发出非空白内容，后续空白不再裁剪（保留段落 / 列表分隔）")
  void preserveWhitespaceAfterFirstNonBlank() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    String r1 = s.process("<think>r</think>Hello\n");
    String r2 = s.process("\nWorld");
    String r3 = s.flush();
    assertThat(r1 + r2 + r3).isEqualTo("Hello\n\nWorld");
  }

  @Test
  @DisplayName("空输入安全：null 与空串不应抛异常")
  void nullSafe() {
    var s = new MultiAgentOrchestrator.ThinkBlockStripper();
    assertThat(s.process(null)).isEmpty();
    assertThat(s.process("")).isEmpty();
    assertThat(s.flush()).isEmpty();
  }
}
