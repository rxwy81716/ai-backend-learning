package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.QueryRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
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
  private final QueryRewriteService queryRewriteService;
  private final IntentRouter intentRouter;
  private final StreamErrorHandler streamErrorHandler;
  private final ChatMessageBuilder messageBuilder;
  private final FollowUpDetector followUpDetector;
  private final MetaBuilder metaBuilder;

  /** Agent 注册表：type → agent 实例 */
  private final Map<AgentType, SpecializedAgent> agentRegistry;

  private static final int STREAM_FIRST_BYTE_TIMEOUT_SECONDS = 15;
  private static final int STREAM_IDLE_TIMEOUT_SECONDS = 25;
  private static final String MODE_KNOWLEDGE = "KNOWLEDGE";
  private static final String MODE_LLM = "LLM";
  private static final String NO_THINK_PREFIX = "/no_think\n";

  // ========== 后处理正则 ==========
  private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
  private static final Pattern STEP_BLOCK = Pattern.compile("\\[STEP].*?\\[/STEP]", Pattern.DOTALL);
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

  // 追问检测 / 模式隔离逻辑已下沉到 FollowUpDetector

  /** Round 4：可选 metrics，启动时用 @Autowired(required=false) 注入，缺失也能跑 */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.jianbo.localaiknowledge.config.RagMetrics ragMetrics;

  public MultiAgentOrchestrator(
      ChatHistoryCacheService chatHistoryCache,
      QueryRewriteService queryRewriteService,
      IntentRouter intentRouter,
      StreamErrorHandler streamErrorHandler,
      ChatMessageBuilder messageBuilder,
      FollowUpDetector followUpDetector,
      MetaBuilder metaBuilder,
      List<SpecializedAgent> agents) {
    this.chatHistoryCache = chatHistoryCache;
    this.queryRewriteService = queryRewriteService;
    this.intentRouter = intentRouter;
    this.streamErrorHandler = streamErrorHandler;
    this.messageBuilder = messageBuilder;
    this.followUpDetector = followUpDetector;
    this.metaBuilder = metaBuilder;

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
    return chatStream(sessionId, question, userId, promptName, chatMode, thinking, null);
  }

  /**
   * 流式问答（带运行时模型选择）。
   *
   * @param modelKey 指定 ChatModel key（与 {@link ChatModelRegistry} 对齐；null/blank = 走默认）
   */
  public Flux<String> chatStream(
      String sessionId,
      String question,
      String userId,
      String promptName,
      String chatMode,
      boolean thinking,
      String modelKey) {
    final long chatStartNanos = System.nanoTime();
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
                String userMeta = metaBuilder.toJson(Map.of(FollowUpDetector.META_KEY_MODE, mode));
                chatHistoryCache.saveMessage(
                    ChatMessage.of(sessionId, "user", question, userMeta, userId));
              }

              // 6. 构建工具调用上下文 + Query Rewrite
              StringBuilder fullAnswer = new StringBuilder();
              final RagToolContext ctx = RagToolContext.create(userId);
              ctx.setModelKey(modelKey);
              // 6.0 推送路由事件
              ctx.emitStep("route", Map.of(
                  "intent", intent.name(),
                  "mode", mode,
                  "model", modelKey == null ? "default" : modelKey));

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
                  if (rewriteResult != null && rewriteResult.attempted()) {
                    ctx.emitStep("rewrite", Map.of(
                        "changed", rewriteResult.changed(),
                        "costMs", rewriteResult.costMs(),
                        "reason", String.valueOf(rewriteResult.reason())));
                  }
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

              // Agent token 流 + step 事件流 merge：
              //  - step 事件带 [STEP]...[/STEP] 标签，前端可单独解析为执行状态 UI
              //  - step 流在 agent 完成/异常时被 complete，避免挂住 SSE 连接
              Flux<String> tokenFlux = agent.execute(request)
                  .doOnSubscribe(s -> ctx.emitStep("generate", Map.of("intent", finalIntent.name())))
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
                  .doFinally(sig -> ctx.completeSteps());

              return Flux.merge(ctx.stepsFlux(), tokenFlux)
                  .concatWith(Flux.defer(() -> buildMetaFlux(ctx, finalIntent, forceLlm, errorCodeHolder[0])))
                  .onErrorResume(e -> {
                    boolean firstByteReceived = !fullAnswer.isEmpty();
                    String code = classifyStreamError(e, firstByteReceived);
                    errorCodeHolder[0] = code;
                    log.error("Agent 流式异常 | session={}, agent={}, code={}, firstByte={}, err={}",
                        sid, finalIntent, code, firstByteReceived, e.toString());
                    String fallback = renderStreamErrorMessage(code, firstByteReceived);
                    fullAnswer.append(fallback);
                    Map<String, Object> meta = metaBuilder.build(ctx, finalIntent, forceLlm, code);
                    return Flux.just(fallback, "[META]" + metaBuilder.toJson(meta) + "[/META]");
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
                    Map<String, Object> metaMap = metaBuilder.build(ctx, finalIntent, forceLlm, errorCodeHolder[0]);
                    metaMap.put(FollowUpDetector.META_KEY_MODE, finalMode);
                    if (cancelled) metaMap.put("cancelled", true);
                    if (cancelled && !content.isEmpty()) {
                      content = content + "\n\n_[已中断]_";
                    }
                    try {
                      chatHistoryCache.saveMessage(
                          ChatMessage.of(sid, "assistant", content, metaBuilder.toJson(metaMap), userId));
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
        .subscribeOn(Schedulers.boundedElastic())
        // Round 4：无论成功/失败/取消都记录 chat 总耗时到 Micrometer Timer
        .doFinally(sig -> {
          if (ragMetrics != null) {
            long costMs = (System.nanoTime() - chatStartNanos) / 1_000_000L;
            ragMetrics.recordChatDuration(modelKey, costMs);
          }
        });
  }

  // ========================================================================
  // 内部工具方法
  // ========================================================================

  /** META 流：交给 MetaBuilder 构造 map，再包成 [META]…[/META] */
  private Flux<String> buildMetaFlux(RagToolContext ctx, AgentType intent, boolean forceLlm, String errorCode) {
    Map<String, Object> meta = metaBuilder.build(ctx, intent, forceLlm, errorCode);
    return Flux.just("[META]" + metaBuilder.toJson(meta) + "[/META]");
  }

  /** 兼容旧调用：消息构建委托给 ChatMessageBuilder */
  private List<Message> buildMessages(
      String sysPrompt, String sessionId, String question, String currentMode) {
    return messageBuilder.build(sysPrompt, sessionId, question, currentMode);
  }

  /** 兼容旧调用：system prompt 解析委托给 ChatMessageBuilder */
  private String resolveAgentSystemPrompt(String promptName, SpecializedAgent agent) {
    return messageBuilder.resolveAgentSystemPrompt(promptName, agent);
  }

  private static String normalizeMode(String chatMode) {
    return MODE_LLM.equalsIgnoreCase(chatMode) ? MODE_LLM : MODE_KNOWLEDGE;
  }

  /** 兼容旧调用：追问检测委托给 FollowUpDetector */
  private boolean isFollowUp(String question) {
    return followUpDetector.isFollowUp(question);
  }

  /** 兼容旧调用：上文加载委托给 FollowUpDetector */
  private String loadLastAssistantContent(String sessionId, String mode) {
    return followUpDetector.loadLastAssistantContent(sessionId, mode);
  }

  // ========== 后处理 ==========

  private static String cleanAnswer(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    String s = THINK_BLOCK.matcher(raw).replaceAll("");
    s = STEP_BLOCK.matcher(s).replaceAll("");
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

  // ========== 错误处理（逻辑已下沉到 StreamErrorHandler；此处仅保留瘦代理方法以减小 diff） ==========

  private String classifyStreamError(Throwable e, boolean firstByteReceived) {
    return streamErrorHandler.classify(e, firstByteReceived);
  }

  private String renderStreamErrorMessage(String code, boolean firstByteReceived) {
    return streamErrorHandler.render(code, firstByteReceived);
  }

  // 引用构建 / JSON 序列化已下沉到 MetaBuilder
}
