package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息（对应 chat_conversation 表）
 */
@Data
public class ChatMessage {

    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID（可选，后续接入认证） */
    private String userId;

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 元数据（JSON，如来源引用、置信度等） */
    private String metadata;

    private LocalDateTime createdAt;

    public static ChatMessage of(String sessionId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    public static ChatMessage of(String sessionId, String role, String content, String metadata) {
        ChatMessage msg = of(sessionId, role, content);
        msg.setMetadata(metadata);
        return msg;
    }

    //没有userId
    public static ChatMessage of(String sessionId, String role, String content, String metadata, String userId) {
        ChatMessage msg = of(sessionId, role, content, metadata);
        msg.setUserId(userId);
        return msg;
    }
}
