package com.jianbo.localaiknowledge.utils;

import lombok.AllArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@AllArgsConstructor
public class ChatContextUtil extends JTokkitTokenCountEstimator{

    // 32K模型上限
    public static final int MAX_CONTEXT_TOKEN = 32768;
    // 安全阈值：80%，到达就裁剪
    public static final int SAFE_TOKEN_LIMIT = 26214;
    // 消息条数兜底：单会话最多保留20轮问答
    public static final int MAX_MSG_SIZE = 20;

    /**
     * 计算消息列表总Token
     */
    public int countTotalToken(List<Message> messageList) {
        int total = 0;
        for (Message msg : messageList) {
            total += estimate(msg.getText());
        }
        return total;
    }

    /**
     * 按Token精准截断
     * 规则：
     * 1. 保留 System 系统提示不删
     * 2. 从最早 User+Assistant 成对删除
     * 3. 循环裁剪至安全阈值内
     */
    public void trimByToken(List<Message> messageList) {
        while (countTotalToken(messageList) > SAFE_TOKEN_LIMIT) {
            // 找到第一条非System消息
            int firstUserIdx = -1;
            for (int i = 0; i < messageList.size(); i++) {
                if (!(messageList.get(i) instanceof SystemMessage)) {
                    firstUserIdx = i;
                    break;
                }
            }
            if (firstUserIdx == -1) {
                break;
            }
            // 删除最早user
            messageList.remove(firstUserIdx);
            // 删除对应assistant
            if (firstUserIdx < messageList.size()) {
                messageList.remove(firstUserIdx);
            }
        }
    }
}