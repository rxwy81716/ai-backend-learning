package com.jianbo.springai.entity;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionDTO {

    // 聊天会话ID
    private String sessionId;

    //新提问
    private String question;

    //是否流式
    private boolean stream;
}
