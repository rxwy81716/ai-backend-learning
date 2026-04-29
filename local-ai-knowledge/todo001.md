## 一、现状盘点

当前
`@/d:/work/ai-backend-learning/local-ai-knowledge/src/main/java/com/jianbo/localaiknowledge/service/EsVectorSearchService.java`
大致是：

```
用户 question
   → Embedding 一次
   → ES knn_search（单路向量）
   → top_k 直接拼 Prompt
```

**痛点：**

- 关键词命中差（向量擅长语义，不擅长精确词）
- 用户问题口语化、有指代（多轮中的"它"），向量召回偏
- top_k 内顺序未必最相关，LLM 容易被噪声干扰
- 切片太小答不全，太大又稀释相关性

------

## 二、目标架构

```
question
   │
   ├─[A] Query Rewriting (LLM 改写 / 多路 query)
   │
   ├─[B] 并发多路召回
   │       ├── 向量召回 (kNN, top 30)
   │       └── BM25 关键词召回 (top 30)
   │
   ├─[C] RRF 融合 (Reciprocal Rank Fusion → top 20)
   │
   ├─[D] Rerank (BGE-Reranker / Cohere → top 5)
   │
   ├─[E] Parent-Child 扩展 (小片段命中 → 返回完整段落)
   │
   └─→ 拼 Prompt → LLM
```

------

## 三、分步骤开发流程（建议 5 个迭代）

### Iteration 1 — BM25 关键词召回 + RRF 融合 ⭐ 最重要

**目标**：先做"双路召回"，立刻可见效果。

```
1. ES 索引 schema 升级
   - 给 content 字段加 ik_max_word 分词器（如已是则跳过）
   - 确认 mapping：dense_vector + text(ik) 共存
 
2. 新增 EsKeywordSearchService
   - match query + bool filter (userId/scope)
   - 返回 List<DocChunk> + bm25_score
 
3. 新增 HybridSearchService
   - 并发调用 vector + keyword（CompletableFuture）
   - 实现 RRF 融合：score = Σ 1/(k + rank_i)，k=60
   - 输出 top_k 融合结果
 
4. RagService 切换为 HybridSearchService
   - 加配置开关 app.rag.hybrid.enabled，便于 A/B
 
5. 数据验证
   - 准备 20 条人工标注 query → 期望文档 ID
   - 写单测对比 recall@5：纯向量 vs 混合
```

**预期产出**：`HybridSearchService` + 配置开关 + 评测脚本

------

### Iteration 2 — Query Rewriting（多轮指代消解）

**目标**：解决多轮对话里"它/这个/上面那个"召回崩溃的问题。

```
1. 新增 QueryRewriteService
   - 输入：当前 question + 最近 3 轮 history
   - LLM Prompt：将问题重写成"独立可检索的完整问题"
   - 输出：rewritten_query (+ 可选 sub_queries[])
 
2. 性能优化
   - 单轮对话跳过（无 history）
   - Caffeine 缓存（短期内同问题不重复改写）
   - 用更便宜/更快的模型（minimax-text-01 / 本地小模型）
 
3. 多 Query 召回（可选进阶）
   - LLM 拆分成 2-3 个子问题 → 并发召回 → 合并去重
 
4. 接入 RagService
   - 改写后的 query 喂给 HybridSearchService
   - 返回结构里附带 rewritten_query，前端可展示"理解为：xxx"
```

**预期产出**：`QueryRewriteService` + 多轮场景效果提升

------

### Iteration 3 — Rerank 重排

**目标**：把 top_20 → 精排 top_5，质量飞跃。

```
1. 选型（任选其一）
   方案 A：本地 BGE-Reranker（推荐，免费）
       - 部署 bge-reranker-v2-m3（HuggingFace + Python FastAPI 暴露 /rerank）
       - 或用 sentence-transformers ONNX 跑在 Java 端（djl）
   方案 B：Cohere Rerank API（最简单，按调用收费）
   方案 C：让 LLM 自己打分（成本高，不推荐生产）
 
2. 新增 RerankService
   - 输入：query + List<DocChunk>(20)
   - 输出：重排后 top_n（默认 5）+ rerank_score
 
3. 链路接入
   HybridSearch(top 20) → Rerank → top 5 → Prompt
 
4. 性能保护
   - 设置超时（500ms）
   - 失败降级：直接用融合结果，不阻断主流程
   - 加配置开关 app.rag.rerank.enabled
```

**预期产出**：`RerankService` + 主链路接入 + 降级保护

------

### Iteration 4 — Parent-Child 切片

**目标**：解决"小片段精准召回 vs 大片段答得全"的矛盾。

```
1. 切片策略改造（TextSplitterUtil）
   - 双层切片：
     * Parent: 800-1200 字（段落级）
     * Child: 200-300 字（句群级，向量化用）
   - Child 元数据带 parent_id
 
2. ES schema 调整
   - 新增 parent_id, parent_text 字段（或单独 parent 索引）
 
3. EsVectorStoreService 改造
   - 一次存：1 个 parent + N 个 child
   - 只对 child 做向量化（节省 embedding 成本）
 
4. 检索阶段
   - 检索 child → 拿到 parent_id → 去重 → 取 parent_text 进 Prompt
   - LLM 看到的上下文 = 完整段落，不再被切碎
 
5. 兼容老数据
   - 提供"重建索引"接口，扫旧 chunk 重新 parent-child 化
```

**预期产出**：双层切片 + 索引升级 + 重建工具

------

### Iteration 5 — 评测体系（贯穿始终，强烈建议第一步就搭）

**目标**：每次改进都能量化，不靠"感觉"。

```
1. 评测数据集
   - 新表 rag_eval_dataset(id, question, expected_doc_ids[], gold_answer)
   - 提供前端"标注页"或导入接口
   - 目标量：50-100 条覆盖各场景
 
2. 评测接口 POST /api/rag/eval/run
   - 跑一遍数据集
   - 输出指标：
     * Recall@5 / Recall@10（召回命中率）
     * MRR（平均倒数排名）
     * Answer 相似度（embedding cosine vs gold）
     * 平均延迟、Token 消耗
 
3. 报告页（前端）
   - 历史评测对比表格
   - 配置开关 ON/OFF 后跑一次，直观看到提升
 
4. CI 兜底（可选）
   - GitHub Action 每晚跑评测
   - Recall 跌破阈值自动告警
```

**预期产出**：评测数据表 + 评测接口 + 对比报告

------

## 四、配置项规划（一次性加好）

`application.yml`：

yaml

```
app:
  rag:
    top-k: 8                    # 最终喂给 LLM 的片段数
    hybrid:
      enabled: true
      vector-top-k: 30          # 向量召回数
      keyword-top-k: 30         # BM25 召回数
      rrf-k: 60                 # RRF 平滑常数
    rewrite:
      enabled: true
      model: minimax-text-01    # 改写用的小模型
      cache-ttl: 600
    rerank:
      enabled: true
      provider: bge             # bge | cohere
      endpoint: http://localhost:8080/rerank
      timeout-ms: 500
      top-n: 5
    parent-child:
      enabled: false            # 改造大，最后再开
      parent-size: 1000
      child-size: 250
```

------

## 五、依赖 / 中间件清单

| 项                      | 何时引入   | 备注                             |
|:-----------------------|:-------|:-------------------------------|
| ES `ik_max_word` 分词器   | Iter 1 | `elasticsearch-analysis-ik` 插件 |
| `CompletableFuture` 并发 | Iter 1 | JDK 自带                         |
| BGE-Reranker 服务        | Iter 3 | Python 微服务 / DJL ONNX          |
| 小型 Embedding（评测用）      | Iter 5 | 复用现有 EmbeddingService          |

------

## 六、推荐推进顺序（按 ROI）

```
Week 1: Iter 5 评测体系（先有标尺）
        + Iter 1 BM25 + RRF（立竿见影）
 
Week 2: Iter 2 Query Rewriting（多轮提升）
        + Iter 3 Rerank（质量天花板）
 
Week 3: Iter 4 Parent-Child（锦上添花）
        + 整体回归 + 文档输出
```

------

## 七、面试 / 简历价值点（写出来怎么吹）

- **混合检索**：双路召回 + RRF 融合，Recall@5 从 X% → Y%
- **多轮鲁棒**：基于上下文的 Query 改写，多轮场景准确率 +30%
- **二阶段精排**：BGE-Reranker 重排，关键问题 NDCG +25%
- **层级上下文**：Parent-Child 切片，答案完整度显著提升
- **可量化**：自建评测集 + 自动化报告，每次改动有据可查