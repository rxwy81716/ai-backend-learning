package com.jianbo.localaiknowledge.service.agent;

import reactor.core.publisher.Flux;

/**
 * 专职 Agent 统一接口。
 *
 * <p>每个实现只关注自己领域的 system prompt + 工具绑定 + 流式执行，
 * 通用逻辑（历史构建、后处理、META 构建、持久化）由 {@link MultiAgentOrchestrator} 统一处理。
 */
public interface SpecializedAgent {

  /** Agent 类型标识 */
  AgentType type();

  /** 该 Agent 使用的 system prompt */
  String systemPrompt();

  /**
   * 执行流式问答，返回 token 流。
   *
   * <p>实现者只需要：
   * <ol>
   *   <li>用 {@link AgentRequest#messages()} 构建 ChatClient 请求
   *   <li>绑定自己的工具（如有）
   *   <li>返回 {@code spec.stream().content()}
   * </ol>
   */
  Flux<String> execute(AgentRequest request);
}
