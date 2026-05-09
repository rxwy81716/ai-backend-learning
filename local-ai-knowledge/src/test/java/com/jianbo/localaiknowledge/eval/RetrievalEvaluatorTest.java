package com.jianbo.localaiknowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索离线评估（hit@k / MRR）。
 *
 * <p>用法：
 *
 * <pre>
 *   # Windows PowerShell
 *   $env:RAG_EVAL_ENABLED="true"; mvn -pl local-ai-knowledge -Dtest=RetrievalEvaluatorTest test
 *   # Linux / macOS
 *   RAG_EVAL_ENABLED=true mvn -pl local-ai-knowledge -Dtest=RetrievalEvaluatorTest test
 * </pre>
 *
 * <p>默认通过 {@link EnabledIfEnvironmentVariable} 关闭，避免普通 CI 跑测试时拉起 PG/ES/远程
 * embedding 服务。仅在评估检索质量时手动开。
 *
 * <p>判定方法：每条 golden query 提供一组 {@code expectedSourceKeywords}；只要返回的文档的
 * {@code metadata.source} 或 {@code text} 中（大小写不敏感）包含其中任一关键词，即视为命中。这避免了
 * 维护精确 doc_id 列表的成本，也允许文档在重导入后保持稳定。
 *
 * <p>输出指标：
 *
 * <ul>
 *   <li><b>hit@1 / hit@5 / hit@10</b> — top-K 内是否至少命中一条
 *   <li><b>MRR</b> — 第一条命中文档排名的倒数的平均
 *   <li><b>avg latency</b> — 单 query 检索耗时（含 embedding + 双路召回 + RRF + Rerank）
 * </ul>
 *
 * <p>结果会以 Markdown 表格形式打到 INFO 日志，便于直接贴到 PR 描述里。
 */
@Slf4j
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAG_EVAL_ENABLED", matches = "true")
public class RetrievalEvaluatorTest {

  private static final String GOLDEN_FILE = "rag-golden-queries.jsonl";
  private static final String DEFAULT_USER = null; // 评估走 PUBLIC 文档，免登录
  private static final int TOP_K = 10;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private HybridSearchService hybridSearchService;

  @Test
  public void evaluate() throws Exception {
    List<GoldenItem> goldens = loadGoldens();
    if (goldens.isEmpty()) {
      log.warn("[RAG-EVAL] golden set 为空，跳过");
      return;
    }
    log.info("[RAG-EVAL] 加载 golden 集 {} 条，开始评估...", goldens.size());

    int hit1 = 0, hit5 = 0, hit10 = 0;
    double mrrSum = 0.0;
    long latencySum = 0L;
    List<String> rows = new ArrayList<>();

    for (GoldenItem g : goldens) {
      long t0 = System.currentTimeMillis();
      List<Document> docs =
          hybridSearchService.searchWithOwnership(g.query, DEFAULT_USER, TOP_K);
      long cost = System.currentTimeMillis() - t0;
      latencySum += cost;

      int firstHitRank = firstHitRank(docs, g.expectedSourceKeywords);
      if (firstHitRank > 0 && firstHitRank <= 1) hit1++;
      if (firstHitRank > 0 && firstHitRank <= 5) hit5++;
      if (firstHitRank > 0 && firstHitRank <= 10) hit10++;
      if (firstHitRank > 0) mrrSum += 1.0 / firstHitRank;

      rows.add(String.format(
          "| %s | %d | %d | %dms |",
          truncate(g.query, 28),
          firstHitRank,
          docs.size(),
          cost));
    }

    int n = goldens.size();
    String report = """

        ## 📊 RAG 检索评估报告
        **样本数**：%d，**topK**：%d，**平均延迟**：%dms

        | metric | value |
        |---|---|
        | hit@1  | %.2f%% |
        | hit@5  | %.2f%% |
        | hit@10 | %.2f%% |
        | MRR    | %.4f |

        ### Per-query 详情
        | query | first-hit rank | returned | latency |
        |---|---|---|---|
        %s
        """.formatted(
            n, TOP_K, latencySum / Math.max(n, 1),
            100.0 * hit1 / n,
            100.0 * hit5 / n,
            100.0 * hit10 / n,
            mrrSum / n,
            String.join("\n", rows));

    log.info(report);
  }

  /** 在返回结果中找第一条命中的排名（从 1 起；0 表示未命中） */
  private int firstHitRank(List<Document> docs, List<String> keywords) {
    if (docs == null || keywords == null || keywords.isEmpty()) return 0;
    for (int i = 0; i < docs.size(); i++) {
      Document d = docs.get(i);
      String src = String.valueOf(d.getMetadata().getOrDefault("source", "")).toLowerCase();
      String text = d.getText() == null ? "" : d.getText().toLowerCase();
      for (String kw : keywords) {
        if (kw == null || kw.isBlank()) continue;
        String k = kw.toLowerCase();
        if (src.contains(k) || text.contains(k)) {
          return i + 1;
        }
      }
    }
    return 0;
  }

  private List<GoldenItem> loadGoldens() throws Exception {
    ClassPathResource res = new ClassPathResource(GOLDEN_FILE);
    if (!res.exists()) return List.of();
    List<GoldenItem> out = new ArrayList<>();
    try (BufferedReader r =
        new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        JsonNode n = objectMapper.readTree(line);
        GoldenItem g = new GoldenItem();
        g.query = n.get("query").asText();
        g.expectedSourceKeywords = new ArrayList<>();
        if (n.has("expectedSourceKeywords") && n.get("expectedSourceKeywords").isArray()) {
          n.get("expectedSourceKeywords").forEach(k -> g.expectedSourceKeywords.add(k.asText()));
        }
        out.add(g);
      }
    }
    return out;
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private static class GoldenItem {
    String query;
    List<String> expectedSourceKeywords;
  }
}
