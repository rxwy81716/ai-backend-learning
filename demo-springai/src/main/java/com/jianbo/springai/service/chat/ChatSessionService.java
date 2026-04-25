package com.jianbo.springai.service.chat;

import com.jianbo.springai.entity.ChatMsg;
import com.jianbo.springai.entity.ChatSessionDTO;
import com.jianbo.springai.session.ChatSessionCache;
import com.jianbo.springai.utils.ChatContextUtil;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatSessionService {
  private final ChatClient minimaxChatClient;
  private final ChatSessionCache sessionCache;
  private final ChatContextUtil chatContextUtil;
  // 系统提示信息
  private static final String SYSTEM_CONTENT =
      "你是专业情感分析师,请根据用户输入的文本，分析用户的问题，并给出相应的建议。不会的直接说不懂,不要胡说。要符合人类自然语言的表达方式，不要使用专业术语，不要使用英文。";
  // 最大保留消息数
  private static final int MAX_MESSAGES = 15;

  public String chat(ChatSessionDTO dto) {
    String sessionId = dto.getSessionId();
    String question = dto.getQuestion();
    // 1 获取历史消息
    List<ChatMsg> history = sessionCache.getHistory(sessionId);
    if (history == null) {
      history = new ArrayList<>();
      history.add(new ChatMsg(MessageType.SYSTEM, SYSTEM_CONTENT));
    }
    // 2  加入用户新问题
    history.add(new ChatMsg(MessageType.USER, question));
    // ============上下文窗口维护(重点)================
    // 限制最大消息数,超出移除最早会话,防止Token爆炸
    if (history.size() > MAX_MESSAGES) {
      // 保留系统消息+最新N条,删除最旧聊天
      List<ChatMsg> system = history.stream().filter(ChatMsg::isSystem).toList();
      // 最新N条
      List<ChatMsg> recent =
          history.stream().filter(m -> !m.isSystem()).skip(history.size() - MAX_MESSAGES).toList();
      history.clear();
      history.addAll(system);
      history.addAll(recent);
    }
    // 3. 转为SpringAI标准Message
    List<Message> messages = buildMessage(history);
    //todo  ==================第二层上下文裁剪==================================================
    chatContextUtil.trimByToken(messages);
    //=======================================================================================
    Prompt prompt = new Prompt(messages);
    // 4. 调用ChatClient获取回复
    String answer = minimaxChatClient.prompt(prompt).call().content();
    history.add(new ChatMsg(MessageType.ASSISTANT, answer));
    // 更新redis会话
    sessionCache.saveHistory(sessionId, history);
    return answer;
  }

  private List<Message> buildMessage(List<ChatMsg> msgList) {
    List<Message> list = new ArrayList<>();
    for (ChatMsg chatMsg : msgList) {
      switch (chatMsg.getType()) {
        case SYSTEM:
          list.add(new SystemMessage(chatMsg.getContent()));
          break;
        case USER:
          list.add(new UserMessage(chatMsg.getContent()));
          break;
        case ASSISTANT:
          list.add(new AssistantMessage(chatMsg.getContent()));
          break;
      }
    }
    return list;
  }

}
