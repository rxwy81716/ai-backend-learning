package com.jianbo.localaiknowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String sessionId;
    private String userId;
    private String role;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;

    public static ChatMessage of(String sessionId, String userId, String role, String content, String metadata) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMetadata(metadata);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }
}
