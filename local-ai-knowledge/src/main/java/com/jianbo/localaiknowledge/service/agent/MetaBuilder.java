package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SSE 流末尾 {@code [META]...[/META]} 段的构造器。
 *
 * <p>从 {@code MultiAgentOrchestrator} 抽出（review #2 P1 架构）。负责：
 *
 * <ul>
 *   <li>{@link #build(RagToolContext, AgentType, boolean, String)} 构造主 meta map（source / agent / hitCount / queryRewrite / references / error）
 *   <li>{@link #buildReferences(List)} 把检索到的 Document 折叠为去重 + 截断后的引用卡片
 *   <li>{@link #toJson(Object)} JSON 序列化兜底（异常即返回 "{}"）
 * </ul>
 *
 * <p>无副作用，纯函数风格；可独立单测。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetaBuilder {

  /** 单条引用卡片正文截断长度 */
  public static final int REFERENCE_CONTENT_MAX = 200;

  private final ObjectMapper objectMapper;

  /**
   * 构造主 meta map。
   *
   * @param ctx 当前请求的工具上下文（含工具调用记录、检索文档、Query Rewrite 结果）
   * @param intent 路由命中的 Agent 类型
   * @param forceLlm 是否强制 LLM 直答模式
   * @param errorCode 错误码（无错误传 null）
   */
  public Map<String, Object> build(RagToolContext ctx, AgentType intent, boolean forceLlm, String errorCode) {
    Set<String> invoked = new LinkedHashSet<>(ctx.getInvokedTools());
    List<Document> docs = new ArrayList<>(ctx.getRetrievedDocs());

    // source 标识逻辑：
    //  - 强制 LLM 模式 / 无工具调用：llm_direct
    //  - KNOWLEDGE / DOCUMENT_OVERVIEW / DOCUMENT_SEARCH：需要 docs 命中才算 knowledge_base，否则 llm_direct
    //  - 其他 Agent（如 HOT_SEARCH / PLANNER）：只要调用了工具即用 agent sourceTag
    String source;
    if (forceLlm || invoked.isEmpty()) {
      source = "llm_direct";
    } else if ((intent == AgentType.KNOWLEDGE
            || intent == AgentType.DOCUMENT_OVERVIEW
            || intent == AgentType.DOCUMENT_SEARCH)
        && docs.isEmpty()) {
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

  /** 把 Document 列表折叠为去重 + 截断后的引用卡片（按 source 去重，保留首篇） */
  public List<Map<String, Object>> buildReferences(List<Document> docs) {
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

  /** JSON 序列化（失败兜底空对象，永不抛异常打断 SSE 流） */
  public String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.warn("META JSON 序列化失败 | err={}", e.getMessage());
      return "{}";
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    String t = s.replaceAll("\\s+", " ").trim();
    if (t.length() <= max) return t;
    return t.substring(0, max) + "…";
  }
}
