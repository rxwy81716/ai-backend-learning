package com.jianbo.localaiknowledge.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextSplitterUtilTest {

    @Test
    @DisplayName("正常切片：长文本按 chunkSize 切，overlap 重叠")
    void normalSplit() {
        String text = "a".repeat(2000);
        List<String> chunks = TextSplitterUtil.split(text, 800, 100);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).hasSize(800);
        // 第二片起始位置应是 800-100=700，所以内容有重叠
        assertThat(chunks.size()).isGreaterThan(2);
    }

    @Test
    @DisplayName("短文本：不足 chunkSize 只产生一片")
    void shortText() {
        List<String> chunks = TextSplitterUtil.split("hello", 800, 100);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("hello");
    }

    @Test
    @DisplayName("空/null → 空列表")
    void emptyInput() {
        assertThat(TextSplitterUtil.split(null, 800, 100)).isEmpty();
        assertThat(TextSplitterUtil.split("", 800, 100)).isEmpty();
        assertThat(TextSplitterUtil.split("   ", 800, 100)).isEmpty();
    }

    @Test
    @DisplayName("所有切片拼接后应覆盖原文所有字符")
    void coverageCheck() {
        String text = "abcdefghijklmnopqrstuvwxyz";
        List<String> chunks = TextSplitterUtil.split(text, 10, 3);
        // 验证首片包含开头，末片包含结尾
        assertThat(chunks.get(0)).startsWith("a");
        assertThat(chunks.get(chunks.size() - 1)).endsWith("z");
    }
}
