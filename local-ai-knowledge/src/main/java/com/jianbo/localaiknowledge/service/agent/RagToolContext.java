package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.QueryRewriteService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
public class RagToolContext {

  /** 当前请求的用户 ID（null = 未登录，仅看公共文档） */
  private final String userId;

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
}
