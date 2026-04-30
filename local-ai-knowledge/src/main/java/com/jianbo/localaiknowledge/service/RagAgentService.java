package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.service.agent.RagToolContext;
import com.jianbo.localaiknowledge.service.agent.RagTools;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * RAG Agent（基于 Spring AI Tool Calling）
 *
 * <p><b>架构演进</b>：
 *
 * <pre>
 *   旧版：Java 关键词匹配（isHotQuery）硬编码路由 → KB / 热榜 / 网搜 / LLM 直答
 *   新版：LLM 自主调用 Tool（searchKnowledgeBase / queryHotSearch / searchWeb）
 * </pre>
 *
 * <p>路由决策权从 Java 移交给 LLM，工具由 {@link RagTools} 通过 {@code @Tool} 注解暴露。上下文 {@link RagToolContext} 用于：
 *
 * <ol>
 *   <li>把 userId 隐式传给工具（防注入 + 不让 LLM 感知）
 *   <li>回收"实际调用了哪些工具 + 命中了哪些 docs"，构建响应里的 source / references
 * </ol>
 *
 * <p><b>chatMode</b>：
 *
 * <ul>
 *   <li>{@code KNOWLEDGE / AGENT / 缺省}：启用 Tool Calling，由 LLM 自主决策（推荐）
 *   <li>{@code LLM}：禁用所有工具，强制 LLM 直答（用户主动选择，作为逃生口）
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagAgentService {

  private final ChatHistoryCacheService chatHistoryCache;
  private final SystemPromptService systemPromptService;
  private final ChatContextUtil chatContextUtil;
  private final ObjectMapper objectMapper;
  private final RagTools ragTools;

  private final ChatClient chatClient;

  private static final int MAX_HISTORY_MESSAGES = 20;
  private static final int LLM_TIMEOUT_SECONDS = 60;
  private static final int LLM_MAX_RETRIES = 2;

  /** chatMode 取值：知识库（默认，启用 Tool Calling） / LLM（直答，禁用所有工具） */
  private static final String MODE_KNOWLEDGE = "KNOWLEDGE";

  private static final String MODE_LLM = "LLM";

  /** 消息 metadata 中存储 chatMode 的 key（用于历史隔离） */
  private static final String META_KEY_MODE = "chatMode";

  /** 去除 qwen3 等推理型模型输出中的 <think>...</think> 块 */
  private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

  /** 合并 3+ 连续换行为 2 个，保留段落间隔但避免过多空行 */
  private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

  /** 快速模式开头指令：告知 qwen3 等模型跳过 thinking 阶段 */
  private static final String NO_THINK_PREFIX = "/no_think\n";

  /**
   * Agent 模式系统提示：告诉 LLM 它有哪些工具、决策原则。工具描述由 {@link RagTools} 上的 {@code @Tool} 注解自动注入到 LLM 的
   * function schema，这里只补充"何时该用工具 / 如何引用来源"等高层指令。
   */
  private static final String AGENT_SYSTEM_PROMPT =
      """
            你是一个企业知识库问答助手。你可以调用以下工具来获取回答所需信息：
              - searchKnowledgeBase：检索企业私域知识库（最优先）
              - queryHotSearch：查询主流平台实时热榜数据
              - searchWeb：检索公开互联网（成本最高，谨慎使用）

            决策原则：
              1. 一般性问题先调用 searchKnowledgeBase；只有当返回'知识库暂无相关内容'时，再考虑其它工具
              2. 涉及'热搜/热榜/今日/排行'等时效性话题，调用 queryHotSearch
              3. 知识库无结果且非热榜话题，可调用 searchWeb（如已启用）
              4. 工具返回的每个文档片段开头都有形如【序号】[来源: 具体文件名]的标记。
                 回答末尾必须用'参考来源：'格式逐条列出实际用到的【来源】完整文件名（多个用逗号分隔），
                 严禁写成'相关文档''背景介绍'等笼统措辞
              5. 若所有工具都无相关结果，再基于自身常识作答，并明确告知'以下回答基于通用知识，仅供参考'
              6. 不要编造工具未返回的链接、数据、人名

            输出规范（极重要）：
              - 调用工具前后均不要输出任何"我将检索/调用xxx/让我查询"之类的过渡说明
              - 直接给出最终回答；如需调用工具就静默调用，调用完毕直接基于结果作答
              - 禁止输出 <think>、<tool_call> 等任何标签或 JSON 形式的内部状态
            """;

  /** LLM 直答 Prompt（chatMode=LLM 时使用，跳过所有工具） */
  private static final String LLM_DIRECT_PROMPT =
      """
            你是一个智能问答助手。请基于你自身的知识尽可能准确地回答用户问题。
            注意：
              - 如果你不确定，请明确告知用户"以下回答基于通用知识，仅供参考"
              - 不要编造具体数据、链接或不存在的来源
              - 直接给出最终回答，不要输出"让我想想/我将分析"之类的过渡说明
            """;

  // ========================================================================
  // 同步问答
  // ========================================================================

  public Map<String, Object> chat(
      String sessionId,
      String question,
      String userId,
      String promptName,
      String chatMode,
      boolean thinking) {
    long t0 = System.currentTimeMillis();
    String mode = normalizeMode(chatMode);
    boolean toolsDisabled = MODE_LLM.equals(mode);

    // 准备 system prompt：快速模式加 /no_think，思考模式不加
    String basePrompt = toolsDisabled ? LLM_DIRECT_PROMPT : resolveAgentSystemPrompt(promptName);
    String sysPrompt = thinking ? basePrompt : (NO_THINK_PREFIX + basePrompt);

    List<Message> messages = buildMessages(sysPrompt, sessionId, question, mode);

    // 调用 LLM：RagToolContext 通过 ToolContext map 显式传入，跨线程安全
    final RagToolContext ctx = RagToolContext.create(userId);
    String answer = callLlmWithProtection(messages, !toolsDisabled, ctx);
    Set<String> invoked = new LinkedHashSet<>(ctx.getInvokedTools());
    List<Document> retrievedDocs = new ArrayList<>(ctx.getRetrievedDocs());

    answer = cleanAnswer(answer);
    long total = System.currentTimeMillis() - t0;
    String source = decideSource(invoked, toolsDisabled);
    log.info(
        "⏱ Agent chat 总耗时 {}ms | source={}, invokedTools={}, hitDocs={}",
        total,
        source,
        invoked,
        retrievedDocs.size());

    // 持久化
    if (sessionId != null) {
      String userMeta = toJson(Map.of(META_KEY_MODE, mode));
      chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question, userMeta, userId));
      Map<String, Object> metaMap = new LinkedHashMap<>();
      metaMap.put(META_KEY_MODE, mode);
      metaMap.put("source", source);
      metaMap.put("invokedTools", invoked);
      metaMap.put("hitCount", retrievedDocs.size());
      if (!retrievedDocs.isEmpty()) {
        metaMap.put("references", buildReferences(retrievedDocs));
      }
      String meta = toJson(metaMap);
      chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", answer, meta, userId));
    }

    // 构建响应
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("answer", answer);
    result.put("source", source);
    result.put("invokedTools", invoked);
    result.put("hitCount", retrievedDocs.size());
    if (sessionId != null) {
      result.put("sessionId", sessionId);
    }
    if ("llm_direct".equals(source)) {
      result.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
    }
    if (!retrievedDocs.isEmpty()) {
      result.put("references", buildReferences(retrievedDocs));
    }
    result.put("costMs", total);
    return result;
  }

  // ========================================================================
  // 流式问答
  // ========================================================================

  public Flux<String> chatStream(
      String sessionId,
      String question,
      String userId,
      String promptName,
      String chatMode,
      boolean thinking) {
    String mode = normalizeMode(chatMode);
    boolean toolsDisabled = MODE_LLM.equals(mode);
    String basePrompt = toolsDisabled ? LLM_DIRECT_PROMPT : resolveAgentSystemPrompt(promptName);
    String sysPrompt = thinking ? basePrompt : (NO_THINK_PREFIX + basePrompt);
    List<Message> messages = buildMessages(sysPrompt, sessionId, question, mode);

    if (sessionId != null) {
      String userMeta = toJson(Map.of(META_KEY_MODE, mode));
      chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question, userMeta, userId));
    }

    StringBuilder fullAnswer = new StringBuilder();

    // Spring AI 2.x 原生 ToolContext：跨线程安全传递，不依赖 ThreadLocal
    final RagToolContext ctx = RagToolContext.create(userId);

    ChatClient.ChatClientRequestSpec spec = chatClient.prompt().messages(messages);
    if (!toolsDisabled) {
      spec = spec.tools(ragTools).toolContext(Map.of(RagTools.CTX_KEY, ctx));
    }

    final String sid = sessionId;
    final String finalMode = mode;
    // 状态机：跨 chunk 跟踪 <think>...</think> 边界，实时丢弃思考块与首部空白
    final ThinkBlockStripper stripper = new ThinkBlockStripper();
    return spec.stream()
        .content()
        .timeout(Duration.ofSeconds(LLM_TIMEOUT_SECONDS))
        .map(stripper::process)
        .concatWith(Flux.defer(() -> {
          String tail = stripper.flush();
          return tail.isEmpty() ? Flux.empty() : Flux.just(tail);
        }))
        .filter(s -> !s.isEmpty())
        .doOnNext(fullAnswer::append)
        .concatWith(
            Flux.defer(
                () -> {
                  // 流末尾追加 [META] 段，前端可解析出工具调用与引用
                  Set<String> invoked = new LinkedHashSet<>(ctx.getInvokedTools());
                  List<Document> docs = new ArrayList<>(ctx.getRetrievedDocs());
                  String source = decideSource(invoked, toolsDisabled);

                  Map<String, Object> meta = new LinkedHashMap<>();
                  meta.put("source", source);
                  meta.put("invokedTools", invoked);
                  meta.put("hitCount", docs.size());
                  if (!docs.isEmpty()) meta.put("references", buildReferences(docs));
                  if ("llm_direct".equals(source)) {
                    meta.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
                  }
                  return Flux.just("[META]" + toJson(meta) + "[/META]");
                }))
        .onErrorResume(
            e -> {
              log.error("Agent 流式异常: {}", e.getMessage(), e);
              String fallback = "抱歉，AI 服务暂时不可用，请稍后重试。";
              fullAnswer.append(fallback);
              return Flux.just(fallback);
            })
        // 无论流式正常完成还是被 onErrorResume 替换为兜底文本，
        // 这里都会拿到 fullAnswer（含正文 / 兜底文本），统一持久化，避免会话历史"用户问完没回答"的断裂。
        .doFinally(
            sig -> {
              if (sid != null && fullAnswer.length() > 0) {
                Set<String> invoked = ctx.getInvokedTools();
                List<Document> docs = ctx.getRetrievedDocs();
                String source = decideSource(invoked, toolsDisabled);
                Map<String, Object> metaMap = new LinkedHashMap<>();
                metaMap.put(META_KEY_MODE, finalMode);
                metaMap.put("source", source);
                metaMap.put("invokedTools", invoked);
                metaMap.put("hitCount", docs.size());
                if (!docs.isEmpty()) {
                  metaMap.put("references", buildReferences(docs));
                }
                String meta = toJson(metaMap);
                try {
                  chatHistoryCache.saveMessage(
                      ChatMessage.of(
                          sid, "assistant", cleanAnswer(fullAnswer.toString()), meta, userId));
                } catch (Exception persistErr) {
                  log.warn("流式 assistant 消息持久化失败: {}", persistErr.getMessage());
                }
              }
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  // ========================================================================
  // 内部工具方法
  // ========================================================================

  private List<Message> buildMessages(
      String sysPrompt, String sessionId, String question, String currentMode) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(sysPrompt));
    if (sessionId != null) {
      List<ChatMessage> history =
          chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
      for (ChatMessage msg : history) {
        // 仅加载与当前 chatMode 相同的历史，避免 LLM 直答与知识库模式互相污染上下文。
        // 历史遗留消息（meta 缺失 chatMode）一律跳过，确保切换模式后行为干净可预期。
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

  private static String normalizeMode(String chatMode) {
    return MODE_LLM.equalsIgnoreCase(chatMode) ? MODE_LLM : MODE_KNOWLEDGE;
  }

  /**
   * 清洗 LLM 输出：
   *
   * <ul>
   *   <li>去除 {@code <think>...</think>} 推理块（qwen3 等模型即使 /no_think 也可能残留空块）
   *   <li>将 3+ 连续换行压缩为 2，保留段落感但避免刷屏
   *   <li>trim 首尾空白
   * </ul>
   */
  private static String cleanAnswer(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    String s = THINK_BLOCK.matcher(raw).replaceAll("");
    s = MULTI_NEWLINE.matcher(s).replaceAll("\n\n");
    return s.trim();
  }

  /**
   * 流式 <think>...</think> 过滤器（状态机）。
   *
   * <p>跨 chunk 边界保留部分标签前缀，避免“{@code <thi}”这样的不完整标签被当作普通文本带出。
   * 同时在首个非空字符出现前 trim掉开头的空白/换行，防止前端 Markdown 馈送上头的乱换行。
   */
  private static final class ThinkBlockStripper {
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

    /** 流结束时冲出残余 buffer（仅当不在 think 块内）。 */
    String flush() {
      if (inThink) {
        buffer.setLength(0);
        return "";
      }
      String s = buffer.toString();
      buffer.setLength(0);
      return trimLeadingIfNeeded(s);
    }

    /** 返回 buf 中不会与 tag 前缀冲突的安全长度（即可以放心输出的前缀长）。 */
    private static int trailingSafeLen(StringBuilder buf, String tag) {
      int max = Math.min(tag.length() - 1, buf.length());
      for (int i = max; i > 0; i--) {
        if (regionMatches(buf, buf.length() - i, tag, 0, i)) {
          return buf.length() - i;
        }
      }
      return buf.length();
    }

    /** 返回 buf 末尾与 tag 前缀匹配的最长长度（需保留）。 */
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

  private boolean isSameMode(ChatMessage msg, String currentMode) {
    String meta = msg.getMetadata();
    if (meta == null || meta.isBlank()) return false;
    try {
      Map<?, ?> map = objectMapper.readValue(meta, Map.class);
      Object stored = map.get(META_KEY_MODE);
      return stored != null && currentMode.equalsIgnoreCase(stored.toString());
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 同步 LLM 调用 + 工具注入 + 超时 + 重试 + 友好兑底。
   *
   * <p>{@code ctx} 通过 {@link org.springframework.ai.chat.model.ToolContext} 显式传入工具方法。
   */
  private String callLlmWithProtection(
      List<Message> messages, boolean enableTools, RagToolContext ctx) {
    Exception lastException = null;

    for (int attempt = 1; attempt <= LLM_MAX_RETRIES; attempt++) {
      try {
        CompletableFuture<String> future =
            CompletableFuture.supplyAsync(() -> invokeChatClient(messages, enableTools, ctx));
        return future.get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        log.warn(
            "LLM 调用超时 | attempt={}/{}, timeout={}s", attempt, LLM_MAX_RETRIES, LLM_TIMEOUT_SECONDS);
        lastException = e;
      } catch (Exception e) {
        log.warn("LLM 调用异常 | attempt={}/{}, error={}", attempt, LLM_MAX_RETRIES, e.getMessage());
        lastException = e;
      }
    }
    log.error(
        "LLM 调用全部失败 | retries={}, lastError={}",
        LLM_MAX_RETRIES,
        lastException != null ? lastException.getMessage() : "unknown");
    return "抱歉，AI 服务暂时不可用，请稍后重试。";
  }

  private String invokeChatClient(List<Message> messages, boolean enableTools, RagToolContext ctx) {
    ChatClient.ChatClientRequestSpec spec = chatClient.prompt().messages(messages);
    if (enableTools) {
      spec = spec.tools(ragTools).toolContext(Map.of(RagTools.CTX_KEY, ctx));
    }
    return spec.call().content();
  }

  /** 根据工具调用记录推断响应来源标识。 优先级：knowledge_base > hot_search > web_search > llm_direct */
  private String decideSource(Set<String> invokedTools, boolean toolsDisabled) {
    if (toolsDisabled || invokedTools == null || invokedTools.isEmpty()) {
      return "llm_direct";
    }
    if (invokedTools.contains(RagTools.TOOL_KB)) return "knowledge_base";
    if (invokedTools.contains(RagTools.TOOL_HOT)) return "hot_search";
    if (invokedTools.contains(RagTools.TOOL_WEB)) return "web_search";
    return "llm_direct";
  }

  /**
   * 构建参考来源列表：去重后只返回文档名（剩文件名，不返回全路径/全文）。
   *
   * <p>同一文档下多个 chunk 合并为一项，避免重复刷屏。
   */
  private List<String> buildReferences(List<Document> docs) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (Document doc : docs) {
      Object src = doc.getMetadata().getOrDefault("source", "未知");
      String name = String.valueOf(src);
      // 剩文件名（处理 / 和 \ 两种分隔符）
      int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
      if (slash >= 0 && slash < name.length() - 1) {
        name = name.substring(slash + 1);
      }
      names.add(name);
    }
    return new ArrayList<>(names);
  }

  private String resolveAgentSystemPrompt(String promptName) {
    // 用户自定义 SystemPrompt（如有）拼接到 Agent 协议之后，避免覆盖工具调用规则
    if (promptName == null || promptName.isBlank()) {
      return AGENT_SYSTEM_PROMPT;
    }
    var prompt = systemPromptService.getByName(promptName);
    if (prompt == null || prompt.getContent() == null || prompt.getContent().isBlank()) {
      return AGENT_SYSTEM_PROMPT;
    }
    // 用户 Prompt 里若包含 {context} 占位符，去掉（工具模式下不再外部注入 context）
    String userPart = prompt.getContent().replace("{context}", "").trim();
    return AGENT_SYSTEM_PROMPT + "\n\n附加风格指令：\n" + userPart;
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return null;
    }
  }
}
