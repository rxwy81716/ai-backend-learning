package com.jianbo.localaiknowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多轮对话 Query 改写服务。
 *
 * <p>痛点：多轮对话中用户的追问通常包含指代（"它""这个""上面提到的"）或省略主语，
 * 直接用原始 query 做检索会导致召回质量骤降。
 *
 * <p>方案：利用 LLM 将"聊天历史 + 当前追问"改写为一个<b>独立、完整、适合检索</b>的查询，
 * 再用改写后的 query 送给知识库/搜索工具。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅当存在历史消息时才改写（首轮直接用原始 query）
 *   <li>使用同一 {@link ChatModel}，单次 prompt 控制在 ~500 token，延迟 <1s
 *   <li>失败时静默回退到原始 query，不阻断主流程
 *   <li>可通过 {@code app.rag.query-rewrite.enabled} 全局开关
 * </ul>
 */
@Slf4j
@Service
public class QueryRewriteService {

  private final ChatModel chatModel;

  @Value("${app.rag.query-rewrite.enabled:true}")
  private boolean enabled;

  /** 历史消息条数低于此值时跳过改写（首轮无指代，改写反而引入噪声） */
  @Value("${app.rag.query-rewrite.min-history:2}")
  private int minHistory;

  public QueryRewriteService(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  private static final String REWRITE_SYSTEM_PROMPT =
      """
          你是一个查询改写助手。你的唯一任务是将用户的最新追问改写为一个独立、完整、适合检索的查询语句。

          规则：
          1. 结合对话历史，将指代词（它、这个、那个、上面提到的）替换为具体实体
          2. 补全省略的主语或宾语
          3. 只输出改写后的查询，不要解释、不要加引号、不要加前缀
          4. 如果追问本身已经是完整独立的查询，直接原样输出
          5. 保留原始问题的语言（中文/英文）
          6. 改写后的查询应简洁，适合搜索引擎或向量检索，不超过 100 字
          """;

  /**
   * 根据聊天历史改写当前问题。
   *
   * @param history   近期历史消息（不含当前 question，不含 system message）
   * @param question  用户当前追问
   * @return 改写后的查询；失败或无需改写时返回原始 question
   */
  public String rewrite(List<Message> history, String question) {
    if (!enabled) {
      return question;
    }
    // 历史不足，无需改写
    if (history == null || history.size() < minHistory) {
      return question;
    }

    try {
      long t0 = System.currentTimeMillis();

      // 构建紧凑的改写 prompt：system + 最近几轮历史 + 当前追问
      List<Message> messages = new java.util.ArrayList<>();
      messages.add(new SystemMessage(REWRITE_SYSTEM_PROMPT));

      // 只取最近 6 条历史（3 轮问答），避免 token 浪费
      int start = Math.max(0, history.size() - 6);
      for (int i = start; i < history.size(); i++) {
        messages.add(history.get(i));
      }
      messages.add(new UserMessage("请将以下追问改写为独立查询：\n" + question));

      Prompt prompt = new Prompt(messages);
      String rewritten = chatModel.call(prompt).getResult().getOutput().getText();

      if (rewritten == null || rewritten.isBlank()) {
        log.warn("Query 改写返回空，回退原始 query");
        return question;
      }

      rewritten = rewritten.trim();
      // 去掉 LLM 可能加的引号
      if ((rewritten.startsWith("\"") && rewritten.endsWith("\""))
          || (rewritten.startsWith("「") && rewritten.endsWith("」"))) {
        rewritten = rewritten.substring(1, rewritten.length() - 1).trim();
      }

      long cost = System.currentTimeMillis() - t0;
      log.info("🔄 Query 改写 | cost={}ms | original=[{}] → rewritten=[{}]",
          cost, question, rewritten);

      return rewritten;
    } catch (Exception e) {
      log.warn("Query 改写失败，回退原始 query | err={}", e.getMessage());
      return question;
    }
  }
}
