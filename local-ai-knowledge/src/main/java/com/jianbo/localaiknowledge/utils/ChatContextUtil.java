package com.jianbo.localaiknowledge.utils;

import lombok.AllArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@AllArgsConstructor
public class ChatContextUtil extends JTokkitTokenCountEstimator {

  // 32K 模型上限
  public static final int MAX_CONTEXT_TOKEN = 32768;
  /**
   * 安全阈值：~60% MAX_CONTEXT_TOKEN。
   *
   * <p>之前是 80%（26214），但 JTokkit 用的是 GPT cl100k BPE，对中文 token 数估算偏低 30%~50%，
   * 真实 token（DeepSeek/GLM 各自 BPE）会更高；同时 system prompt 本身已 ~600 token，
   * 还要给 LLM 留输出空间（max-tokens=2048），整体压到 ~60% 更安全。
   */
  public static final int SAFE_TOKEN_LIMIT = 19660;
  // 消息条数兜底：单会话最多保留20轮问答
  public static final int MAX_MSG_SIZE = 20;

  /** 计算消息列表总Token */
  public int countTotalToken(List<Message> messageList) {
    int total = 0;
    for (Message msg : messageList) {
      total += estimate(msg.getText());
    }
    return total;
  }

  /**
   * 按 Token 精准截断（增量 O(n)，不重复遍历整个列表）。
   *
   * <p>规则：
   * <ol>
   *   <li>保留 System 系统提示不删
   *   <li>从最早 User+Assistant 成对删除
   *   <li>每次只减去刚删的那两条 token，避免每轮 O(n²) 全量重算
   * </ol>
   */
  public void trimByToken(List<Message> messageList) {
    int total = countTotalToken(messageList);
    while (total > SAFE_TOKEN_LIMIT) {
      // 找到第一条非 System 消息
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
      // 删除最早 user，并从 total 中扣除其 token
      Message removed = messageList.remove(firstUserIdx);
      total -= estimate(removed.getText());
      // 删除紧随其后的 assistant（如果还在），同样扣除
      if (firstUserIdx < messageList.size()) {
        Message removedAssistant = messageList.remove(firstUserIdx);
        total -= estimate(removedAssistant.getText());
      }
    }
  }
}
