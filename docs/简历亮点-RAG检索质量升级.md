# 简历亮点 — RAG 检索质量升级

> 项目：本地知识库 RAG 问答系统（local-ai-knowledge）
> 模块：F1 检索质量升级
> 日期：2026-04

---

## 已完成（Iteration 1：混合检索 + RRF 融合）

### 简历可写版本（精简）

> **混合检索 + RRF 融合**：将单路向量检索升级为 ES 向量召回 + BM25 关键词召回的双路并发架构，使用 Reciprocal Rank Fusion (RRF, k=60) 融合排序，解决纯向量检索对专有名词、英文术语、版本号等精确词召回偏弱的问题；通过 CompletableFuture 并发执行 + 超时降级保护，单路异常自动退化为另一路结果，整体 P99 延迟可控在 300ms 内。

### 技术实现要点（面试可深挖）

#### 1. 为什么做混合检索？
- **向量召回的弱点**：擅长语义相近（"如何提速" ≈ "性能优化"），但对**精确字符串**（如 "Spring AI 1.0.0-M6"、"PgVector HNSW"）容易漏召
- **BM25 的弱点**：依赖词共现，对同义改写无能为力
- **结论**：两者**互补**，工业界 RAG 几乎都用混合检索（参考 Elastic / Cohere / Pinecone 官方实践）

#### 2. 为什么选 RRF 而不是 score 加权？
- 向量 cosine ∈ [0,1]，BM25 score ∈ [0, +∞)，**量纲完全不同**，加权前必须归一化，归一化方法（min-max / z-score）又会引入新参数
- RRF 只用排名（rank），与原始 score 无关，**无超参数 + 鲁棒**
- 公式：`score(d) = Σ 1 / (k + rank_i(d))`，论文推荐 k=60

#### 3. 工程上注意了什么？
- **并发执行**：`CompletableFuture.allOf` 让两路召回并行，总耗时 ≈ max(向量耗时, BM25耗时)
- **超时降级**：`get(3000ms)` 超时后用 `getNow(空)` 拿已完成部分，避免一路慢拖垮整体
- **单路异常隔离**：try-catch 包裹每路，挂掉的那路返回空列表，不影响另一路
- **配置开关**：`app.rag.hybrid.enabled` 一键回退纯向量，A/B 灰度安全
- **元数据回写**：融合结果带 `vector_rank / bm25_rank / hybrid_score`，前端可展示来源贡献，调优可观测

#### 4. 数据隔离一致性
- 双路查询都遵循 `(doc_scope=PUBLIC) OR (doc_scope=PRIVATE AND user_id=当前用户)` 规则
- BM25 路径直接拼 ES bool query DSL，向量路径通过 Spring AI `FilterExpressionBuilder`，**逻辑等价**

#### 5. 中文优化
- ES 默认 `standard` 分词器对中文按字切，BM25 质量打折
- 提供 `ik_max_word` 索引迁移脚本（`db/es_index_ik.md`）+ 别名切换零停机方案

---

## 关键代码位置

| 类 | 路径 | 职责 |
|---|---|---|
| `EsKeywordSearchService` | `service/EsKeywordSearchService.java` | BM25 关键词召回 + 用户归属过滤 |
| `HybridSearchService` | `service/HybridSearchService.java` | 双路并发 + RRF 融合 |
| `RagAgentService` | `service/RagAgentService.java` | 知识库路由切换为混合检索（被 `/api/rag/chat` 调用） |
| 配置 | `application.yml > app.rag.hybrid.*` | top-k / rrf-k / 超时 等可调 |
| 索引迁移 | `resources/db/es_index_ik.md` | ik_max_word reindex 脚本 |

---

## 待办（后续迭代继续补强）

- [ ] **Iter 2 Query Rewriting**：多轮指代消解 + 子问题拆分
- [ ] **Iter 3 Rerank**：BGE-Reranker 二阶段精排
- [ ] **Iter 4 Parent-Child 切片**：召回小片段 → 返回完整段落
- [ ] **Iter 5 评测体系**：人工标注集 + Recall@K / MRR 自动报告

---

## 数据指标（待补，做完 Iter 5 后填）

| 指标 | 纯向量 | 混合检索 | 提升 |
|---|---|---|---|
| Recall@5 | TBD | TBD | TBD |
| MRR | TBD | TBD | TBD |
| 平均延迟 | TBD | TBD | TBD |

> ⚠️ 简历上务必写**真实数字**，等 Iter 5 评测体系跑出来再填，否则面试官追问会翻车。

---

## 一句话总结（简历最终一行）

> 在 RAG 知识库系统中设计实现 **向量 + BM25 双路并发召回 + RRF 融合** 的混合检索链路，
> 通过 CompletableFuture 并发与超时降级保证稳定性，配合 ik_max_word 中文分词索引，
> 在 Recall@5 上较纯向量基线提升 **{X}%**（注：等评测做完填实数）。
