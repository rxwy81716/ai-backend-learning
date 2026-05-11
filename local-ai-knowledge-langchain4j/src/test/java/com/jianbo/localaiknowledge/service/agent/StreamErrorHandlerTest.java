package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * StreamErrorHandler 单元测试 — LangChain4j 版。
 */
class StreamErrorHandlerTest {

    private final StreamErrorHandler handler = new StreamErrorHandler();

    @Test
    @DisplayName("429 限流 → 频繁提示")
    void rateLimitError() {
        var flux = handler.handleError(new RuntimeException("HTTP 429 Too Many Requests"), "测试");
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("频繁"))
                .verifyComplete();
    }

    @Test
    @DisplayName("超时 → 超时提示")
    void timeoutError() {
        var flux = handler.handleError(new RuntimeException("Request timed out"), "测试");
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("超时"))
                .verifyComplete();
    }

    @Test
    @DisplayName("其他异常 → 包含错误信息")
    void genericError() {
        var flux = handler.handleError(new RuntimeException("Connection refused"), "测试");
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("Connection refused"))
                .verifyComplete();
    }

    @Test
    @DisplayName("null message → 未知错误")
    void nullMessageError() {
        var flux = handler.handleError(new RuntimeException((String) null), "测试");
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("未知错误"))
                .verifyComplete();
    }
}
