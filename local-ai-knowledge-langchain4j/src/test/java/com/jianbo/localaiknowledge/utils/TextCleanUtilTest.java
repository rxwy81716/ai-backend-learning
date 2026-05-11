package com.jianbo.localaiknowledge.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleanUtilTest {

    @Test
    @DisplayName("clean：合并连续空白 + 去控制字符")
    void clean() {
        assertThat(TextCleanUtil.clean("hello   world\t\n")).isEqualTo("hello world");
        assertThat(TextCleanUtil.clean(null)).isEmpty();
    }

    @Test
    @DisplayName("truncate：超长截断加省略号")
    void truncate() {
        String long_ = "a".repeat(100);
        assertThat(TextCleanUtil.truncate(long_, 10)).hasSize(13); // 10 + "..."
        assertThat(TextCleanUtil.truncate(long_, 10)).endsWith("...");
        assertThat(TextCleanUtil.truncate("short", 10)).isEqualTo("short");
        assertThat(TextCleanUtil.truncate(null, 10)).isEmpty();
    }
}
