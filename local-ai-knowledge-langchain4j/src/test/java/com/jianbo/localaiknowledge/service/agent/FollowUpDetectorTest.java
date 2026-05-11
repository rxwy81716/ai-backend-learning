package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FollowUpDetector 追问检测单元测试。
 */
class FollowUpDetectorTest {

    private final FollowUpDetector detector = new FollowUpDetector();

    @ParameterizedTest
    @DisplayName("应识别为追问")
    @ValueSource(strings = {
        "那还有呢",
        "那么具体怎么做",
        "它怎么工作的",
        "这个是什么意思",
        "上面说的不对",
        "为什么会这样",
        "继续说",
        "接着讲",
        "是不是",
        "对吗",
        "好的"  // 短于8字且无问号
    })
    void shouldDetectFollowUp(String question) {
        assertThat(detector.isFollowUp(question)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("不应识别为追问")
    @ValueSource(strings = {
        "如何配置 pgvector 的 HNSW 参数？",
        "Spring AI 和 LangChain4j 有什么区别？",
        "帮我写一段 Python 代码",
        "请介绍一下向量数据库的原理"
    })
    void shouldNotDetectFollowUp(String question) {
        assertThat(detector.isFollowUp(question)).isFalse();
    }
}
