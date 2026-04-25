package com.jianbo.springai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.ai.chat.messages.MessageType;

/**
 * 聊天消息
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMsg {
    // 消息类型 SYSTEM/USER/ASSISTANT
    private MessageType type;
    // 消息内容
    private String content;

    public boolean isSystem() {
        return type == MessageType.SYSTEM;
    }
}
