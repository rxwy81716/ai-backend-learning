package com.jianbo.localaiknowledge.service.agent;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 传递给各专职 Agent 的请求上下文（不可变）。
 *
 * @param sessionId 会话 ID（可 null = 无状态单轮）
 * @param question  用户原始问题
 * @param userId    当前用户 ID（可 null = 未登录）
 * @param messages  已构建好的消息列表（含 system prompt + 历史 + 当前问题）
 * @param toolCtx   工具调用上下文（userId 隐式传递 + 回收调用记录）
 * @param thinking  是否启用思考模式（true = 允许 <think> 块）
 */
public record AgentRequest(
    String sessionId,
    String question,
    String userId,
    List<Message> messages,
    RagToolContext toolCtx,
    boolean thinking
) {}
