package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.QueryRewriteService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 多 Agent 调度器。
 *
 * <p>职责划分：
 * <ul>
 *   <li><b>Orchestrator（本类）</b>：消息构建、意图路由、Query Rewrite、
 *       流式后处理（ThinkBlockStripper / cleanAnswer）、META 构建、持久化
 *   <li><b>SpecializedAgent</b>：各自领域的 system prompt + 工具绑定 + 流式执行
 * </ul>
 *
 * <p>支持两种 chatMode：
 * <ul>
 *   <li>{@code KNOWLEDGE}（默认）：启用 IntentRouter + 多 Agent 路由
 *   <li>{@code LLM}：强制走 ChatAgent（用户主动选择的逃生口）
 * </ul>
 */
@Service
@Slf4j
public class MultiAgentOrchestrator {

  private final ChatHistoryCacheService chatHistoryCache;
  private final SystemPromptService systemPromptService;
  private final ChatContextUtil chatContextUtil;
  private final ObjectMapper objectMapper;
  private final QueryRewriteService queryRewriteService;
  private final IntentRouter intentRouter;

  /** Agent 注册表：type → agent 实例 */
  private final Map<AgentType, SpecializedAgent> agentRegistry;

  private static final int MAX_HISTORY_MESSAGES = 20;
  private static final int STREAM_FIRST_BYTE_TIMEOUT_SECONDS = 15;
  private static final int STREAM_IDLE_TIMEOUT_SECONDS = 25;
  private static final String MODE_KNOWLEDGE = "KNOWLEDGE";
  private static final String MODE_LLM = "LLM";
  private static final String META_KEY_MODE = "chatMode";
  private static final String NO_THINK_PREFIX = "/no_think\n";

  // ========== 后处理正则 ==========
  private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
  private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");
  private static final Pattern META_BLOCK_LINE =
      Pattern.compile("(?m)^\\s*【(?:运行时上下文|执行工具|工具结果|工具调用)】.*$");
  private static final Pattern INLINE_SOURCE_TAG =
      Pattern.compile("\\s*\\[\\s*来源\\s*[:：][^\\]]*?\\]");
  private static final Pattern REFERENCE_FOOTER =
      Pattern.compile("(?m)^\\s*(?:参考来源|参考文档|来源)\\s*[:：].*(?:\\n[ \\t]+.*)*", Pattern.UNICODE_CASE);
  private static final Pattern LEADING_TOOL_PREAMBLE =
      Pattern.compile(
          "^\\s*(?:这个问题[^\\n。！？]{0,40}?[，,]\\s*)?"
              + "(?:为了(?:更好地)?回答[^\\n。！？]{0,40}?[，,]\\s*)?"
              + "(?:我(?:需要|将|要|来|得)|让我|我先)\\s*"
              + "(?:先\\s*)?"
              + "(?:调用|检索|查询|查阅|查找|搜索|查一下|查询一下|搜索一下)"
              + "[^\\n。！？]{0,80}?"
              + "[。！？\\n]\\s*",
          Pattern.UNICODE_CASE);

  // ========== 追问检测 ==========

  /** 追问关键词：序数引用 + 追问动作 */
  private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(
      "第[一二三四五六七八九十\\d]+[个篇份条段章]|"
          + "刚才|刚刚|前面|上面|之前|上次|上一个|"
          + "详细说说|展开讲讲|具体说说|再详细|多说点|"
          + "继续说|接着说|然后呢|还有呢|"
          + "这个文档|那个文档|这篇|那篇|"
          + "总结一下|概括一下|归纳一下",
      Pattern.UNICODE_CASE);

  /** 追问问题最大长度（超过此长度视为独立新问题） */
  private static final int FOLLOW_UP_MAX_LENGTH = 30;

  public MultiAgentOrchestrator(
      ChatHistoryCacheService chatHistoryCache,
      SystemPromptService systemPromptService,
      ChatContextUtil chatContextUtil,
      ObjectMapper objectMapper,
      QueryRewriteService queryRewriteService,
      IntentRouter intentRouter,
      List<SpecializedAgent> agents) {
    this.chatHistoryCache = chatHistoryCache;
    this.systemPromptService = systemPromptService;
    this.chatContextUtil = chatContextUtil;
    this.objectMapper = objectMapper;
    this.queryRewriteService = queryRewriteService;
    this.intentRouter = intentRouter;

    // 自动注册所有 SpecializedAgent 实现
    this.agentRegistry = new EnumMap<>(AgentType.class);
    for (SpecializedAgent agent : agents) {
      agentRegistry.put(agent.type(), agent);
      log.info("✅ 注册 Agent: {} → {}", agent.type(), agent.getClass().getSimpleName());
    }
  }

  // ========================================================================
  // 流式问答（前端唯一入口）
  // ========================================================================

  public Flux<String> chatStream(
      String sessionId,
      String question,
      String userId,
      String promptName,
      String chatMode,
      boolean thinking) {
    return Flux.defer(
            () -> {
              // 1. 模式判断
              String mode = normalizeMode(chatMode);
              boolean forceLlm = MODE_LLM.equals(mode);

              // 2. 意图路由（LLM 直答模式跳过路由，强制 CHAT）
              AgentType intent = forceLlm ? AgentType.CHAT : intentRouter.route(question);
              SpecializedAgent agent = agentRegistry.get(intent);
              if (agent == null) {
                log.error("未找到 Agent: {}，fallback CHAT", intent);
                agent = agentRegistry.get(AgentType.CHAT);
              }

              // 3. 构建 system prompt
              String basePrompt = forceLlm
                  ? agent.systemPrompt()
                  : resolveAgentSystemPrompt(promptName, agent);
              String sysPrompt = thinking ? basePrompt : (NO_THINK_PREFIX + basePrompt);

              // 4. 构建消息列表
              List<Message> messages = buildMessages(sysPrompt, sessionId, question, mode);

              // 5. 保存用户消息
              if (sessionId != null) {
                String userMeta = toJson(Map.of(META_KEY_MODE, mode));
                chatHistoryCache.saveMessage(
                    ChatMessage.of(sessionId, "user", question, userMeta, userId));
              }

              // 6. 构建工具调用上下文 + Query Rewrite
              StringBuilder fullAnswer = new StringBuilder();
              final RagToolContext ctx = RagToolContext.create(userId);

              // 6a. 追问检测：注入上文 context
              String followUpContext = null;
              if (sessionId != null && isFollowUp(question)) {
                followUpContext = loadLastAssistantContent(sessionId, mode);
                if (followUpContext != null) {
                  log.info("[追问检测] 检测到追问，注入上文 {} 字符 | session={}, question={}",
                      followUpContext.length(), sessionId, question);
                }
              }

              if (!forceLlm && intent == AgentType.KNOWLEDGE) {
                List<Message> historyOnly = messages.stream()
                    .filter(m -> !(m instanceof SystemMessage))
                    .toList();
                if (historyOnly.size() > 1) {
                  List<Message> prevHistory = historyOnly.subList(0, historyOnly.size() - 1);
                  QueryRewriteService.RewriteResult rewriteResult =
                      queryRewriteService.rewriteWithTrace(prevHistory, question);
                  ctx.recordRewrite(rewriteResult);
                }
              }

              // 7. 构建 AgentRequest 并执行
              // 7a. 追问上下文注入：在上一条用户消息前插入上文
              if (followUpContext != null) {
                messages.add(messages.size() - 1,
                    new SystemMessage("【对话上文（用户正在追问此内容）】\n" + followUpContext
                        + "\n\n用户正在基于以上内容进行追问，请结合上下文理解用户的指代（如'第一个文档''刚才那个'等）。"));
              }
              AgentRequest request = new AgentRequest(
                  sessionId, question, userId, messages, ctx, thinking);

              final String sid = sessionId;
              final String finalMode = mode;
              final AgentType finalIntent = intent;
              final String[] errorCodeHolder = new String[1];
              final ThinkBlockStripper stripper = new ThinkBlockStripper();

              return agent.execute(request)
                  .timeout(
                      reactor.core.publisher.Mono.delay(
                          Duration.ofSeconds(STREAM_FIRST_BYTE_TIMEOUT_SECONDS)),
                      ignored -> reactor.core.publisher.Mono.delay(
                          Duration.ofSeconds(STREAM_IDLE_TIMEOUT_SECONDS)))
                  .map(stripper::process)
                  .concatWith(Flux.defer(() -> {
                    String tail = stripper.flush();
                    return tail.isEmpty() ? Flux.empty() : Flux.just(tail);
                  }))
                  .filter(s -> !s.isEmpty())
                  .doOnNext(fullAnswer::append)
                  .concatWith(Flux.defer(() -> buildMetaFlux(ctx, finalIntent, forceLlm, errorCodeHolder[0])))
                  .onErrorResume(e -> {
                    boolean firstByteReceived = !fullAnswer.isEmpty();
                    String code = classifyStreamError(e, firstByteReceived);
                    errorCodeHolder[0] = code;
                    log.error("Agent 流式异常 | session={}, agent={}, code={}, firstByte={}, err={}",
                        sid, finalIntent, code, firstByteReceived, e.toString());
                    String fallback = renderStreamErrorMessage(code, firstByteReceived);
                    fullAnswer.append(fallback);
                    Map<String, Object> meta = buildMeta(ctx, finalIntent, forceLlm, code);
                    return Flux.just(fallback, "[META]" + toJson(meta) + "[/META]");
                  })
                  .doOnCancel(() -> log.info("流式被客户端取消 | session={}", sid))
                  .doFinally(sig -> {
                    if (sid == null) return;
                    String content = cleanAnswer(fullAnswer.toString());
                    boolean cancelled = sig == reactor.core.publisher.SignalType.CANCEL;
                    boolean failed = errorCodeHolder[0] != null;
                    if (content.isBlank() && !cancelled && !failed) {
                      log.info("流式产出为空，跳过持久化 | session={}", sid);
                      return;
                    }
                    Map<String, Object> metaMap = buildMeta(ctx, finalIntent, forceLlm, errorCodeHolder[0]);
                    metaMap.put(META_KEY_MODE, finalMode);
                    if (cancelled) metaMap.put("cancelled", true);
                    if (cancelled && !content.isEmpty()) {
                      content = content + "\n\n_[已中断]_";
                    }
                    try {
                      chatHistoryCache.saveMessage(
                          ChatMessage.of(sid, "assistant", content, toJson(metaMap), userId));
                    } catch (Exception persistErr) {
                      log.warn("流式 assistant 消息持久化失败: {}", persistErr.getMessage());
                    }
                    log.info(
                        "MultiAgent 请求完成 | session={}, mode={}, agent={}, source={}, tools={}, hitCount={}, rewriteAttempted={}, cancelled={}, failed={}",
                        sid, finalMode, finalIntent,
                        metaMap.get("source"), ctx.getInvokedTools(), ctx.getRetrievedDocs().size(),
                        ctx.isRewriteAttempted(), cancelled, failed);
                  });
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  // ========================================================================
  // 内部工具方法
  // ========================================================================

  private Flux<String> buildMetaFlux(RagToolContext ctx, AgentType intent, boolean forceLlm, String errorCode) {
    Map<String, Object> meta = buildMeta(ctx, intent, forceLlm, errorCode);
    return Flux.just("[META]" + toJson(meta) + "[/META]");
  }

  private Map<String, Object> buildMeta(RagToolContext ctx, AgentType intent, boolean forceLlm, String errorCode) {
    Set<String> invoked = new LinkedHashSet<>(ctx.getInvokedTools());
    List<Document> docs = new ArrayList<>(ctx.getRetrievedDocs());

    // source 标识逻辑：
    //  - 强制 LLM 模式 / 无工具调用：llm_direct
    //  - KNOWLEDGE / DOCUMENT_OVERVIEW：需要有 docs 命中才算 knowledge_base，否则 llm_direct
    //  - 其他 Agent（如 HOT_SEARCH）：只要调用了工具即用 agent sourceTag
    String source;
    if (forceLlm || invoked.isEmpty()) {
      source = "llm_direct";
    } else if ((intent == AgentType.KNOWLEDGE || intent == AgentType.DOCUMENT_OVERVIEW || intent == AgentType.DOCUMENT_SEARCH) && docs.isEmpty()) {
      source = "llm_direct";
    } else {
      source = intent.sourceTag();
    }

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("source", source);
    meta.put("agent", intent.name());
    meta.put("invokedTools", invoked);
    meta.put("hitCount", docs.size());

    if (ctx.isRewriteAttempted()) {
      meta.put("queryRewrite", Map.of(
          "changed", ctx.isRewriteChanged(),
          "costMs", ctx.getRewriteCostMs(),
          "reason", String.valueOf(ctx.getRewriteReason())));
    }
    if (!docs.isEmpty()) {
      meta.put("references", buildReferences(docs));
    }
    if ("llm_direct".equals(source) && intent != AgentType.CHAT) {
      meta.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
    }
    if (errorCode != null) {
      meta.put("error", true);
      meta.put("errorCode", errorCode);
    }
    return meta;
  }

  private List<Message> buildMessages(
      String sysPrompt, String sessionId, String question, String currentMode) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(sysPrompt));
    if (sessionId != null) {
      List<ChatMessage> history =
          chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
      for (ChatMessage msg : history) {
        if (!isSameMode(msg, currentMode)) continue;
        switch (msg.getRole()) {
          case "user" -> messages.add(new UserMessage(msg.getContent()));
          case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }
    messages.add(new UserMessage(question));
    chatContextUtil.trimByToken(messages);
    return messages;
  }

  private String resolveAgentSystemPrompt(String promptName, SpecializedAgent agent) {
    SystemPrompt prompt = null;
    if (promptName != null && !promptName.isBlank()) {
      prompt = systemPromptService.getByName(promptName);
    }
    if (prompt == null) {
      try {
        prompt = systemPromptService.getDefault();
      } catch (Exception e) {
        log.debug("加载默认 SystemPrompt 失败，使用 Agent 内置提示: {}", e.getMessage());
      }
    }
    if (prompt == null || prompt.getContent() == null || prompt.getContent().isBlank()) {
      return agent.systemPrompt();
    }
    String userPart = prompt.getContent().replace("{context}", "").trim();
    return agent.systemPrompt() + "\n\n附加风格指令：\n" + userPart;
  }

  private static String normalizeMode(String chatMode) {
    return MODE_LLM.equalsIgnoreCase(chatMode) ? MODE_LLM : MODE_KNOWLEDGE;
  }

  /** 判断是否为追问：短问题 + 包含序数引用或追问动作词 */
  private boolean isFollowUp(String question) {
    if (question == null || question.isBlank()) return false;
    if (question.length() > FOLLOW_UP_MAX_LENGTH) return false;
    return FOLLOW_UP_PATTERN.matcher(question).find();
  }

  /** 加载最近一条 assistant 消息内容，用于追问上下文注入 */
  private String loadLastAssistantContent(String sessionId, String mode) {
    List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, 5);
    for (int i = history.size() - 1; i >= 0; i--) {
      ChatMessage msg = history.get(i);
      if ("assistant".equals(msg.getRole()) && isSameMode(msg, mode)) {
        String content = msg.getContent();
        if (content != null && !content.isBlank()) {
          // 截取前 2000 字符，避免撑爆上下文
          return content.length() > 2000 ? content.substring(0, 2000) : content;
        }
      }
    }
    return null;
  }

  private boolean isSameMode(ChatMessage msg, String currentMode) {
    String stored = MODE_KNOWLEDGE;
    String meta = msg.getMetadata();
    if (meta != null && !meta.isBlank()) {
      try {
        Map<?, ?> map = objectMapper.readValue(meta, Map.class);
        Object v = map.get(META_KEY_MODE);
        if (v != null) stored = v.toString();
      } catch (Exception ignore) {
      }
    }
    return currentMode.equalsIgnoreCase(stored);
  }

  // ========== 后处理 ==========

  private static String cleanAnswer(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    String s = THINK_BLOCK.matcher(raw).replaceAll("");
    s = META_BLOCK_LINE.matcher(s).replaceAll("");
    s = INLINE_SOURCE_TAG.matcher(s).replaceAll("");
    s = REFERENCE_FOOTER.matcher(s).replaceAll("");
    s = LEADING_TOOL_PREAMBLE.matcher(s).replaceFirst("");
    s = MULTI_NEWLINE.matcher(s).replaceAll("\n\n");
    return s.trim();
  }

  // ========== ThinkBlockStripper ==========

  static final class ThinkBlockStripper {
    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";
    private boolean inThink = false;
    private boolean emittedNonBlank = false;
    private final StringBuilder buffer = new StringBuilder();

    String process(String chunk) {
      if (chunk == null || chunk.isEmpty()) return "";
      buffer.append(chunk);
      StringBuilder out = new StringBuilder();
      while (true) {
        if (!inThink) {
          int idx = buffer.indexOf(OPEN);
          if (idx >= 0) {
            out.append(buffer, 0, idx);
            buffer.delete(0, idx + OPEN.length());
            inThink = true;
            continue;
          }
          int safe = trailingSafeLen(buffer, OPEN);
          out.append(buffer, 0, safe);
          buffer.delete(0, safe);
          break;
        } else {
          int idx = buffer.indexOf(CLOSE);
          if (idx >= 0) {
            buffer.delete(0, idx + CLOSE.length());
            inThink = false;
            continue;
          }
          int keepFrom = buffer.length() - partialSuffixLen(buffer, CLOSE);
          buffer.delete(0, keepFrom);
          break;
        }
      }
      return trimLeadingIfNeeded(out.toString());
    }

    String flush() {
      if (inThink) { buffer.setLength(0); return ""; }
      String s = buffer.toString();
      buffer.setLength(0);
      return trimLeadingIfNeeded(s);
    }

    private static int trailingSafeLen(StringBuilder buf, String tag) {
      int max = Math.min(tag.length() - 1, buf.length());
      for (int i = max; i > 0; i--) {
        if (regionMatches(buf, buf.length() - i, tag, 0, i)) return buf.length() - i;
      }
      return buf.length();
    }

    private static int partialSuffixLen(StringBuilder buf, String tag) {
      int max = Math.min(tag.length() - 1, buf.length());
      for (int i = max; i > 0; i--) {
        if (regionMatches(buf, buf.length() - i, tag, 0, i)) return i;
      }
      return 0;
    }

    private static boolean regionMatches(StringBuilder buf, int off, String s, int sOff, int len) {
      for (int i = 0; i < len; i++) {
        if (buf.charAt(off + i) != s.charAt(sOff + i)) return false;
      }
      return true;
    }

    private String trimLeadingIfNeeded(String s) {
      if (emittedNonBlank || s.isEmpty()) {
        if (!s.isEmpty()) emittedNonBlank = true;
        return s;
      }
      int i = 0;
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
      String r = s.substring(i);
      if (!r.isEmpty()) emittedNonBlank = true;
      return r;
    }
  }

  // ========== 错误处理 ==========

  private static String classifyStreamError(Throwable e, boolean firstByteReceived) {
    if (e instanceof TimeoutException) {
      return firstByteReceived ? "timeout_idle" : "timeout_first_byte";
    }
    if (e instanceof HttpClientErrorException ce) {
      int code = ce.getStatusCode().value();
      if (code == 429) return "rate_limit";
      if (code == 401 || code == 403) return "auth";
      String body = ce.getResponseBodyAsString().toLowerCase();
      if (body.contains("content_filter") || body.contains("safety") || body.contains("moderation")) {
        return "content_policy";
      }
      return "client_error";
    }
    if (e instanceof HttpServerErrorException) return "server_error";
    if (e instanceof ResourceAccessException || e instanceof java.io.IOException) return "network";
    return "unknown";
  }

  private static String renderStreamErrorMessage(String code, boolean firstByteReceived) {
    return switch (code) {
      case "timeout_first_byte" ->
          "抱歉，AI 服务响应超时（首字节 " + STREAM_FIRST_BYTE_TIMEOUT_SECONDS + "s 未到），请稍后重试。";
      case "timeout_idle" ->
          "\n\n_[流式中断：连续 " + STREAM_IDLE_TIMEOUT_SECONDS + "s 未收到新内容]_";
      case "rate_limit" -> "请求过于频繁，请稍后再试。";
      case "auth" -> "AI 服务认证失败，请联系管理员检查 API Key 配置。";
      case "content_policy" -> "抱歉，您的问题或上下文触发了内容安全策略，无法回答。";
      case "client_error" -> "抱歉，请求被服务端拒绝（参数或配额问题），请稍后重试。";
      case "server_error", "network", "unknown" ->
          firstByteReceived
              ? "\n\n_[AI 服务连接异常，回答已中断]_"
              : "抱歉，AI 服务暂时不可用，请稍后重试。";
      default -> "抱歉，AI 服务暂时不可用，请稍后重试。";
    };
  }

  // ========== 引用构建 ==========

  private static final int REFERENCE_CONTENT_MAX = 200;

  private List<Map<String, Object>> buildReferences(List<Document> docs) {
    LinkedHashMap<String, Map<String, Object>> bySource = new LinkedHashMap<>();
    for (Document doc : docs) {
      Map<String, Object> meta = doc.getMetadata();
      Object src = meta.getOrDefault("source", "未知");
      String name = String.valueOf(src);
      int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
      if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
      if (bySource.containsKey(name)) continue;

      String content = truncate(doc.getText(), REFERENCE_CONTENT_MAX);
      Object score = meta.get("hybrid_score");
      if (score == null) score = meta.get("_score");
      if (score == null) score = meta.get("distance");

      Map<String, Object> ref = new LinkedHashMap<>();
      ref.put("source", name);
      ref.put("content", content);
      if (score != null) ref.put("score", score);
      bySource.put(name, ref);
    }
    return new ArrayList<>(bySource.values());
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    String t = s.replaceAll("\\s+", " ").trim();
    if (t.length() <= max) return t;
    return t.substring(0, max) + "…";
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }
}
