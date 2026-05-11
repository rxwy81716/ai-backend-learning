package com.jianbo.localaiknowledge.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * RAG 可观测性指标（与 Spring AI 版完全一致，无 AI 框架依赖）。
 */
@Component
public class RagMetrics {

    private final Counter queryCounter;
    private final Counter errorCounter;
    private final Timer queryTimer;

    public RagMetrics(MeterRegistry registry) {
        this.queryCounter = Counter.builder("rag.query.total")
                .description("Total RAG queries").register(registry);
        this.errorCounter = Counter.builder("rag.query.errors")
                .description("RAG query errors").register(registry);
        this.queryTimer = Timer.builder("rag.query.duration")
                .description("RAG query duration").register(registry);
    }

    public void recordQuery() { queryCounter.increment(); }
    public void recordError() { errorCounter.increment(); }
    public Timer.Sample startTimer() { return Timer.start(); }
    public void stopTimer(Timer.Sample sample) { sample.stop(queryTimer); }
}
