package com.jianbo.localaiknowledge.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流式错误降级处理器（与 Spring AI 版一致）。
 */
@Component
@Slf4j
public class StreamErrorHandler {

    public Flux<String> handleError(Throwable error, String question) {
        log.error("[StreamError] question={}, error={}", question, error.getMessage());

        String msg = error.getMessage();
        if (msg != null && msg.contains("429")) {
            return Flux.just("⚠️ 当前请求过于频繁，请稍后再试。");
        }
        if (msg != null && (msg.contains("timeout") || msg.contains("timed out"))) {
            return Flux.just("⚠️ 请求超时，请稍后重试。");
        }
        return Flux.just("⚠️ 处理时出现错误，请重试：" + (msg != null ? msg : "未知错误"));
    }
}
