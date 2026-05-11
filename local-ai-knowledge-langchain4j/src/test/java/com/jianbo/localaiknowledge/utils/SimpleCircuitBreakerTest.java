package com.jianbo.localaiknowledge.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleCircuitBreakerTest {

    @Test
    @DisplayName("正常调用：应直接返回结果")
    void normalCall() throws Exception {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, 1000);
        String result = cb.call(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("连续失败达阈值后 → 熔断（不再执行实际调用）")
    void openAfterThreshold() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 5000);
        AtomicInteger callCount = new AtomicInteger();

        // 连续失败 2 次
        for (int i = 0; i < 2; i++) {
            try {
                cb.call(() -> { callCount.incrementAndGet(); throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }

        // 第 3 次应被熔断，不执行 supplier
        int countBefore = callCount.get();
        assertThatThrownBy(() -> cb.call(() -> { callCount.incrementAndGet(); return "x"; }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OPEN");
        assertThat(callCount.get()).isEqualTo(countBefore); // supplier 未被调用
    }

    @Test
    @DisplayName("成功调用应重置失败计数")
    void successResetsCount() throws Exception {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, 1000);

        // 失败 2 次
        for (int i = 0; i < 2; i++) {
            try { cb.call(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        }

        // 成功 1 次 → 重置
        cb.call(() -> "ok");

        // 再失败 2 次不应熔断（因为已重置）
        for (int i = 0; i < 2; i++) {
            try { cb.call(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        }

        // 第 3 次才熔断
        assertThatThrownBy(() -> cb.call(() -> "x"))
                .hasMessageContaining("OPEN");
    }
}
