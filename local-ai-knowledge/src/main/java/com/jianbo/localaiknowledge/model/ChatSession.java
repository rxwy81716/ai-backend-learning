package com.jianbo.localaiknowledge.model;

import lombok.Data;

/**
 * 会话信息 DTO
 */
@Data
public class ChatSession {
    private String sessionId;
    private String title;
    private String firstQuestion;
    private long createdAt;
}
