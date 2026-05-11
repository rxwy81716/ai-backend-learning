package com.jianbo.localaiknowledge.service.agent;

import reactor.core.publisher.Flux;

/**
 * 专职 Agent 接口（对标 Spring AI 版）。
 *
 * <h2>与 Spring AI 版的区别</h2>
 * <p>接口签名完全一致，返回 {@code Flux<String>}。
 * 差异在实现层：Spring AI 版直接用 ChatClient.stream()，
 * LangChain4j 版通过 {@code TokenStreamFluxAdapter} 桥接。</p>
 */
public interface SpecializedAgent {
    AgentType type();
    String systemPrompt();
    Flux<String> execute(AgentRequest request);
}
