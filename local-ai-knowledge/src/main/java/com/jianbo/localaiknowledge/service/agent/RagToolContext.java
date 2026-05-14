package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.service.QueryRewriteService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RAG Tool 调用上下文（请求级实例，跨线程通过 Spring AI {@code ToolContext} 显式传递）。
 *
 * <p>承担两个职责：
 *
 * <ul>
 *   <li>把 {@code userId} 隐式传给工具方法（不让 LLM 感知，防 prompt 注入篡改）
 *   <li>回收"实际调用了哪些工具 + 检索到哪些文档"，供外层构建响应的 {@code source} / {@code references} 字段
 * </ul>
 *
 * <p>线程安全说明：Spring AI 2.x 默认串行调用工具，{@code synchronizedSet} / {@code CopyOnWriteArrayList}
 * 是为了防御未来可能出现的并行 Tool Calling 场景。
 */
@Getter
@Slf4j
public class RagToolContext {

  private static final ObjectMapper STEP_MAPPER = new ObjectMapper();

  /** 当前请求的用户 ID（null = 未登录，仅看公共文档） */
  private final String userId;

  /** 可选：指定使用的 ChatModel key（null = 走默认 @Primary ChatClient） */
  @Setter
  private volatile String modelKey;

  /**
   * 执行状态事件流：Agent / Tool 在处理过程中往这里 push 可视化事件，
   * Orchestrator 会 merge 到 SSE 主流中，以 {@code [STEP]{json}[/STEP]} 形式下发给前端。
   *
   * <p>multicast + onBackpressureBuffer：允许多订阅者 + 避免丢事件。
   */
  private final Sinks.Many<String> stepSink =
      Sinks.many().multicast().onBackpressureBuffer();

  /** Query Rewrite 改写后的检索 query（null = 未改写，工具应使用 LLM 传入的原始 query） */
  @Setter
  private volatile String rewrittenQuery;

  private volatile boolean rewriteAttempted;

  private volatile boolean rewriteChanged;

  private volatile long rewriteCostMs;

  private volatile String rewriteReason;

  /** 实际被 LLM 调用过的工具名称（按调用顺序、去重） */
  private final Set<String> invokedTools = Collections.synchronizedSet(new LinkedHashSet<>());

  /** 知识库工具命中的文档（用于构建 references） */
  private final List<Document> retrievedDocs = new CopyOnWriteArrayList<>();

  /** 首轮检索结果（追问检索为空时，用于扩展上下文） */
  private volatile List<Document> firstRetrievalDocs;

  private RagToolContext(String userId) {
    this.userId = userId;
  }

  /** 创建一次请求的上下文实例。 */
  public static RagToolContext create(String userId) {
    return new RagToolContext(userId);
  }

  /** 获取检索用 query：优先改写版，无则返回 fallback */
  public String getSearchQuery(String fallback) {
    String rq = rewrittenQuery;
    return (rq != null && !rq.isBlank()) ? rq : fallback;
  }

  public void recordRewrite(QueryRewriteService.RewriteResult result) {
    if (result == null) {
      return;
    }
    rewriteAttempted = result.attempted();
    rewriteChanged = result.changed();
    rewriteCostMs = result.costMs();
    rewriteReason = result.reason();
    if (result.changed() && result.query() != null && !result.query().isBlank()) {
      rewrittenQuery = result.query();
    }
  }

  public void recordInvocation(String toolName) {
    invokedTools.add(toolName);
  }

  public void addDocs(List<Document> docs) {
    if (docs != null && !docs.isEmpty()) {
      retrievedDocs.addAll(docs);
    }
  }

  /**
   * 记录首轮检索结果（仅首次记录有效）。
   * 用于追问检索为空时扩展上下文。
   */
  public void recordFirstRetrieval(List<Document> docs) {
    if (this.firstRetrievalDocs == null && docs != null && !docs.isEmpty()) {
      this.firstRetrievalDocs = new ArrayList<>(docs);
    }
  }

  public List<Document> getFirstRetrievalDocs() {
    return firstRetrievalDocs;
  }

  public boolean hasFirstRetrieval() {
    return firstRetrievalDocs != null && !firstRetrievalDocs.isEmpty();
  }

  // ==================== 执行状态事件流 ====================

  /** 订阅 step 事件流（Orchestrator merge 到 SSE 主流） */
  public Flux<String> stepsFlux() {
    return stepSink.asFlux();
  }

  /** 标记 step 流结束（Orchestrator 在 agent 流完成/异常时调用） */
  public void completeSteps() {
    stepSink.tryEmitComplete();
  }

  /**
   * 推送一个执行状态事件。
   *
   * @param stage   阶段名（route / rewrite / tool / think / act / observe / generate / done）
   * @param payload 附加字段（如 intent / query / tool / hits / costMs ...）
   */
  public void emitStep(String stage, Map<String, Object> payload) {
    try {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("stage", stage);
      if (payload != null) data.putAll(payload);
      data.put("ts", System.currentTimeMillis());
      String json = STEP_MAPPER.writeValueAsString(data);
      Sinks.EmitResult r = stepSink.tryEmitNext("[STEP]" + json + "[/STEP]");
      if (r.isFailure() && log.isDebugEnabled()) {
        log.debug("[RagToolContext] 推送 step 失败: stage={}, reason={}", stage, r);
      }
    } catch (Exception e) {
      log.warn("[RagToolContext] 构建 step 事件异常: stage={}, err={}", stage, e.getMessage());
    }
  }

  public void emitStep(String stage) {
    emitStep(stage, Map.of());
  }
}
