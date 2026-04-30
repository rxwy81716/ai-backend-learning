package com.jianbo.localaiknowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  public record RewriteResult(
      String query, boolean attempted, boolean changed, long costMs, String reason) {}

  private final ChatModel chatModel;

  @Value("${app.rag.query-rewrite.enabled:true}")
  private boolean enabled;

  /** 历史消息条数低于此值时跳过改写（首轮无指代，改写反而引入噪声） */
  @Value("${app.rag.query-rewrite.min-history:2}")
  private int minHistory;

  @Value("${app.rag.query-rewrite.history-window:6}")
  private int historyWindow;

  @Value("${app.rag.query-rewrite.timeout-ms:1200}")
  private long timeoutMs;

  @Value("${app.rag.query-rewrite.max-query-length:100}")
  private int maxQueryLength;

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
    return rewriteWithTrace(history, question).query();
  }

  public RewriteResult rewriteWithTrace(List<Message> history, String question) {
    if (!enabled) {
      return new RewriteResult(question, false, false, 0L, "disabled");
    }
    if (question == null || question.isBlank()) {
      return new RewriteResult(question, false, false, 0L, "blank_question");
    }
    if (history == null || history.size() < minHistory) {
      return new RewriteResult(question, false, false, 0L, "history_insufficient");
    }

    try {
      long t0 = System.currentTimeMillis();

      // 构建紧凑的改写 prompt：system + 最近几轮历史 + 当前追问
      List<Message> messages = new ArrayList<>();
      messages.add(new SystemMessage(REWRITE_SYSTEM_PROMPT));

      // 只取最近 6 条历史（3 轮问答），避免 token 浪费
      int start = Math.max(0, history.size() - historyWindow);
      for (int i = start; i < history.size(); i++) {
        messages.add(history.get(i));
      }
      messages.add(new UserMessage("请将以下追问改写为独立查询：\n" + question));

      Prompt prompt = new Prompt(messages);
      String rewritten =
          CompletableFuture.supplyAsync(() -> chatModel.call(prompt).getResult().getOutput().getText())
              .get(timeoutMs, TimeUnit.MILLISECONDS);
      long cost = System.currentTimeMillis() - t0;

      if (rewritten == null || rewritten.isBlank()) {
        log.warn("Query 改写返回空，回退原始 query");
        return new RewriteResult(question, true, false, cost, "empty_result");
      }

      rewritten = normalizeQuery(rewritten);
      if (rewritten.isBlank() || rewritten.length() > maxQueryLength) {
        log.warn("Query 改写结果异常，回退原始 query | rewritten={}", rewritten);
        return new RewriteResult(question, true, false, cost, "invalid_result");
      }

      if (rewritten.equals(question)) {
        log.debug("Query 无需改写 | cost={}ms | query=[{}]", cost, question);
        return new RewriteResult(question, true, false, cost, "unchanged");
      } else {
        log.info("🔄 Query 改写 | cost={}ms | original=[{}] → rewritten=[{}]",
            cost, question, rewritten);
        return new RewriteResult(rewritten, true, true, cost, "rewritten");
      }
    } catch (TimeoutException e) {
      log.warn("Query 改写超时 {}ms，回退原始 query", timeoutMs);
      return new RewriteResult(question, true, false, timeoutMs, "timeout");
    } catch (Exception e) {
      log.warn("Query 改写失败，回退原始 query | err={}", e.getMessage());
      return new RewriteResult(question, true, false, 0L, "error");
    }
  }

  private String normalizeQuery(String query) {
    String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ");
    if ((normalized.startsWith("\"") && normalized.endsWith("\""))
        || (normalized.startsWith("「") && normalized.endsWith("」"))) {
      normalized = normalized.substring(1, normalized.length() - 1).trim();
    }
    return normalized;
  }
}
