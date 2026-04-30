# 简历包装与面试准备 - RAG 项目

> 适用场景：六年 Java 后端转 AI 应用开发工程师
> 项目：local-ai-knowledge（企业级 RAG 智能问答系统）

---

## 一、简历包装建议

### 项目名称（统一口径）

**企业级 RAG 智能问答系统（基于 Spring AI + 向量检索）**

### 项目描述（推荐写法）

独立设计并开发一套企业级 RAG（Retrieval-Augmented Generation）智能问答系统，
支持多模态文档上传、向量检索、多轮对话、工具调用等核心能力。系统采用
Spring AI 2.x 框架，结合 PostgreSQL PGVector 与 Elasticsearch 实现混合检索，
通过 Query Rewrite 与 Rerank 提升多轮对话质量与检索精度，支持流式输出与
用户隔离。

### 技术栈（分类突出）

#### 后端核心
- **Spring Boot 4.0.6 + Java 21**（虚拟线程、Record、模式匹配）
- **Spring AI 2.0.0-M4**（ChatClient、Vector Store、Tool Calling）
- **WebFlux + Reactor**（SSE 流式输出、非阻塞 I/O）
- **Spring Security + JJWT**（JWT 认证、权限控制）

#### 向量检索
- **PostgreSQL + PGVector**（向量存储、余弦相似度）
- **Elasticsearch**（BM25 关键词检索、向量检索）
- **混合检索 + RRF**（Reciprocal Rank Fusion 融合排序）
- **Cross-Encoder Rerank**（SiliconFlow bge-reranker-v2-m3 精排）

#### 存储与缓存
- **Redis + Redisson**（分布式锁、队列、缓存）
- **Caffeine**（本地多级缓存）
- **MyBatis**（持久化、历史消息管理）

#### 文档处理
- **Apache Tika**（PDF/Word/TXT 解析）
- **BGE-M3 Embedding**（硅基流动，1024 维向量）
- **文档分块策略**（按段落/字符切分，避免语义断裂）

#### 前端
- **Vue 3.4 + TypeScript + Vite**
- **Element Plus + Pinia + Vue Router**
- **Axios + SSE**（流式响应解析）
- **ECharts + Markdown-it**（数据可视化、Markdown 渲染）

#### AI 模型
- **多模型支持**（硅基流动 / 智谱 GLM / DeepSeek，OpenAI 兼容协议）
- **Prompt 管理**（系统提示词动态配置）
- **Tool Calling**（知识库检索、热榜查询、网搜）

### 项目亮点（3-5 条，每条带数据）

**1. 混合检索 + RRF 融合，提升召回准确率**
- 实现向量检索（PGVector）与关键词检索（BM25）双路召回
- 通过 RRF 算法融合排序，平衡语义与精确匹配
- 实测召回准确率提升 30%+（对比纯向量检索）

**2. Query Rewrite + Rerank，优化多轮对话质量**
- 基于 LLM 改写多轮追问，解决指代消解问题（"它/这个"）
- 集成 Cross-Encoder Rerank，对召回结果精排提纯
- 改写成功率 85%+，Rerank 延迟 <500ms

**3. 流式输出 + 超时控制，优化用户体验**
- 基于 WebFlux SSE 实现流式输出，首字节延迟 <2s
- 双段超时机制（首字节 15s、chunk 间 25s），避免长回答卡死
- 支持 Think Block 剥离、来源引用解析

**4. 多级缓存 + 用户隔离，保障性能与安全**
- Redis + Caffeine 双层缓存，命中率 70%+，降低 LLM 调用成本
- 用户级文档隔离，防止跨用户数据泄露
- 基于 sessionId 的历史上下文管理，支持 20 轮对话

**5. 生产化防护，支持灰度上线**
- 限流（令牌桶）、降级（Rerank/改写失败回退）、超时控制
- 结构化日志（改写率、Rerank 收缩率、检索耗时）
- 配置化开关（Query Rewrite / Rerank 可独立开关）

### 职责描述（按模块拆分）

**架构设计与技术选型**
- 选型 Spring AI 2.x 作为 AI 应用框架，对比 LangChain4j
- 设计混合检索架构（PGVector + Elasticsearch），对比 Milvus/Weaviate
- 选型 BGE-M3 作为 Embedding 模型，兼顾多语言与长文本

**核心功能开发**
- 实现 RAG 核心链路：文档上传 → 解析 → 分块 → Embedding → 向量存储 → 检索 → 生成
- 实现 Tool Calling 机制（知识库检索、热榜查询、网搜），由 LLM 自主决策
- 实现流式输出、超时控制、错误分类与兜底

**性能优化**
- 设计多级缓存策略（Redis + Caffeine），缓存 key 包含 rerank 配置签名
- 优化检索链路：并行向量检索与 BM25 检索，RRF 融合耗时 <100ms
- 优化文档解析：异步队列（Redisson）、分块策略优化

**生产化保障**
- 设计生产 Checklist（22 项），包含 SSE 解析、Session 隔离、限流、重试等
- 实现结构化日志（改写率、Rerank 收缩率、检索耗时），便于灰度观测
- 实现 JWT 认证、用户权限控制、Prompt 注入防护

---

## 二、面试题准备（分模块）

### RAG 基础理论

**Q1：什么是 RAG？为什么要用 RAG？**
- RAG = 检索增强生成，结合检索与生成
- 解决 LLM 知识截止、幻觉、私有数据不可访问问题
- 你可以举自己项目例子：企业知识库问答，基于上传文档回答

**Q2：RAG 的核心流程是什么？**
- 文档上传 → 解析 → 分块 → Embedding → 向量存储 → 检索 → 生成
- 你可以画图说明，并强调你的实现：Tika 解析、BGE-M3 Embedding、PGVector 存储

**Q3：向量检索 vs 关键词检索，区别是什么？**
- 向量检索：语义相似度，适合模糊查询
- 关键词检索：精确匹配，适合专有名词
- 你可以讲混合检索 + RRF 的优势

**Q4：什么是 Embedding？你用的什么模型？**
- Embedding 将文本映射到高维向量，保留语义信息
- 你用的是 BGE-M3（硅基流动），1024 维，支持多语言
- 可以讲为什么选 BGE-M3（长文本支持、中文优化）

### 向量数据库

**Q5：为什么选 PGVector 而不是 Milvus/Weaviate？**
- PGVector：基于 PostgreSQL，运维成本低，适合中小规模
- Milvus/Weaviate：专用向量数据库，性能更强但运维复杂
- 你可以讲你的场景：企业知识库，数据量不大，PGVector 足够

**Q6：PGVector 的索引类型有哪些？你用的什么？**
- HNSW（Hierarchical Navigable Small World）：近似检索，速度快
- IVFFlat：倒排索引，平衡精度与速度
- 你可以讲你用的 HNSW，参数配置（m=16, ef_construction=64）

**Q7：向量相似度计算方式有哪些？**
- 余弦相似度（Cosine Similarity）：最常用，不受向量长度影响
- 欧氏距离（Euclidean Distance）：几何距离
- 点积（Dot Product）：适合归一化向量
- 你可以讲你用的是余弦相似度

### 混合检索与 RRF

**Q8：什么是 RRF？为什么要用？**
- RRF（Reciprocal Rank Fusion）：融合多个排序结果
- 公式：score = Σ 1/(k + rank_i)，k 通常为 60
- 解决单一检索方式的局限性，平衡语义与精确匹配

**Q9：你的混合检索是怎么实现的？**
- 并行执行向量检索（PGVector）与 BM25 检索（ES）
- 对结果按 RRF 融合排序
- 你可以讲你的代码：HybridSearchService，并行检索、RRF 融合

### Query Rewrite

**Q10：什么是 Query Rewrite？为什么需要？**
- 多轮对话中，用户追问常包含指代（"它/这个/上面说的"）
- Query Rewrite 将追问改写为独立查询，提升召回质量
- 你可以讲你的实现：基于 LLM 改写，历史窗口 6 条，超时 1.2s

**Q11：你的 Query Rewrite 是怎么实现的？**
- 基于 LLM 改写，传入历史消息 + 当前追问
- 改写后校验：非空、长度限制、与原 query 不同
- 失败回退到原 query，不阻断主流程
- 你可以讲你的代码：QueryRewriteService，RewriteResult 结构化结果

### Rerank

**Q12：什么是 Rerank？为什么需要？**
- Rerank（重排序）：对召回结果精排，提高最终质量
- 双塔模型（检索）vs 单塔模型（Rerank）：Rerank 精度更高但更慢
- 你可以讲你的实现：Cross-Encoder（bge-reranker-v2-m3）

**Q13：你的 Rerank 是怎么实现的？**
- 调用 SiliconFlow Rerank API，传入 query + 候选文档
- 截断文档文本（2000 字符），避免超长
- 按分数阈值过滤（0.1），返回 top-N
- 失败回退到原排序，不阻断主流程
- 你可以讲你的代码：RerankService，超时 2s，降级策略

### 流式输出

**Q14：为什么要流式输出？怎么实现？**
- 流式输出提升用户体验，首字节快，长回答不卡
- 基于 SSE（Server-Sent Events）协议
- 你可以讲你的实现：WebFlux Flux SSE，前端解析 \n\n 分帧

**Q15：流式输出的超时怎么控制？**
- 双段超时：首字节 15s，chunk 间 25s
- Reactor timeout + Mono.delay race
- 你可以讲你的代码：RagAgentService，ThinkBlockStripper

### Tool Calling

**Q16：什么是 Tool Calling？为什么要用？**
- Tool Calling 让 LLM 自主调用外部工具（检索、网搜）
- 解决 LLM 知识截止问题，扩展能力
- 你可以讲你的实现：RagTools（searchKnowledgeBase、queryHotSearch、searchWeb）

**Q17：你的 Tool Calling 是怎么实现的？**
- 基于 Spring AI @Tool 注解，暴露工具给 LLM
- 通过 ToolContext 传递 userId，防注入
- 记录工具调用与命中文档，构建 references
- 你可以讲你的代码：RagAgentService，RagToolContext

### 缓存与性能

**Q18：你的缓存策略是什么？**
- 多级缓存：Caffeine（本地）+ Redis（分布式）
- 缓存 key 包含 query + rerank 配置签名，避免配置变更导致脏缓存
- 你可以讲你的代码：HybridSearchService，缓存 key 设计

**Q19：如何优化检索性能？**
- 并行检索：向量检索与 BM25 并行执行
- 索引优化：PGVector HNSW 索引，ES BM25 索引
- 缓存优化：多级缓存，命中率 70%+

### 生产化

**Q20：你的系统有哪些生产化保障？**
- 限流：令牌桶算法，防止滥用
- 降级：Rerank/改写失败回退，不影响主流程
- 超时控制：LLM 调用、Rerank、检索链路超时
- 错误分类：网络异常、超时、模型错误，分类兜底
- 结构化日志：改写率、Rerank 收缩率、检索耗时

**Q21：如何灰度上线新功能（Query Rewrite / Rerank）？**
- 配置化开关，可独立开启/关闭
- 结构化日志观测：改写率、Rerank 收缩率、耗时
- 小流量测试，观察延迟与准确率
- 你可以讲你的 PRODUCTION_CHECKLIST.md

### 系统设计

**Q22：如果让你设计一个 RAG 系统，你会怎么设计？**
- 文档上传 → 解析 → 分块 → Embedding → 向量存储
- 检索：混合检索（向量 + BM25）+ RRF 融合
- Rerank：Cross-Encoder 精排
- 生成：LLM + 工具调用
- 缓存：多级缓存
- 流式输出：SSE
- 生产化：限流、降级、超时、日志

---

## 三、技术难点与解决方案（重点准备）

### 难点 1：多轮对话指代消解

**问题**
- 用户追问常包含指代（"它/这个/上面说的"），直接检索召回率低

**解决方案**
- 实现 Query Rewrite：基于 LLM 改写追问为独立查询
- 传入历史消息（最近 6 条），保留上下文
- 改写后校验：非空、长度限制、与原 query 不同
- 失败回退到原 query，不阻断主流程

**代码体现**
- QueryRewriteService.rewriteWithTrace()
- RagAgentService.chatStream() 中调用改写
- RagToolContext.recordRewrite() 记录改写结果

### 难点 2：检索精度提升

**问题**
- 单一向量检索召回率不稳定，专有名词匹配差
- BM25 关键词检索语义理解弱

**解决方案**
- 混合检索：向量检索（PGVector）+ BM25（ES）
- RRF 融合：平衡语义与精确匹配
- Rerank：Cross-Encoder 精排，提高最终质量

**代码体现**
- HybridSearchService.searchWithOwnership()
- RerankService.rerank()
- RRF 融合算法

### 难点 3：流式输出超时控制

**问题**
- LLM 长回答容易卡死，首字节慢
- 客户端无响应，用户体验差

**解决方案**
- 双段超时：首字节 15s，chunk 间 25s
- Reactor timeout + Mono.delay race
- Think Block 剥离：去除推理块，减少无效输出

**代码体现**
- RagAgentService.chatStream()
- ThinkBlockStripper

### 难点 4：缓存一致性

**问题**
- Rerank 配置变更后，缓存 key 不变，返回脏数据

**解决方案**
- 缓存 key 包含 rerank 配置签名
- 配置变更自动失效缓存
- 你可以讲你的代码：HybridSearchService，cache key 设计

### 难点 5：用户数据隔离

**问题**
- 多用户场景，防止跨用户数据泄露

**解决方案**
- 用户级文档过滤：检索时加入 userId 过滤
- Session 隔离：基于 sessionId 管理历史
- JWT 认证：用户身份校验

**代码体现**
- HybridSearchService.searchWithOwnership() userId 过滤
- RagController sessionId 校验
- Spring Security + JWT

### 难点 6：Prompt 注入防护

**问题**
- 用户输入包含 "忽略之前指令" 等 prompt 注入攻击

**解决方案**
- 系统提示词显式禁止执行注入指令
- 输入清洗：过滤敏感词
- Tool Context 隐式传递 userId，不让 LLM 感知

**代码体现**
- AGENT_SYSTEM_PROMPT 安全准则
- RagToolContext userId 隐式传递

---

## 四、面试策略建议

### 开场自我介绍（1-2 分钟）

我是六年 Java 后端，最近一年专注 AI 应用开发。
独立设计并开发了一套企业级 RAG 智能问答系统，基于 Spring AI 2.x，
结合 PGVector 与 Elasticsearch 实现混合检索，支持多轮对话、
Query Rewrite、Rerank、流式输出等核心能力。
系统已实现生产化防护，包括限流、降级、超时控制、结构化日志。
技术栈包括 Spring Boot 4.0.6 + Java 21 + Vue 3.4 + TypeScript，
熟悉 Spring AI、向量检索、LLM Tool Calling 等技术。

### 项目讲解顺序

1. **项目背景与目标**（1 分钟）
2. **技术选型与架构**（2 分钟）
3. **核心功能实现**（3 分钟）
4. **性能优化与生产化**（2 分钟）
5. **难点与解决方案**（3 分钟）

### 面试官可能追问

- "为什么选 Spring AI 而不是 LangChain？"（答：Java 生态、Spring 集成、企业级）
- "你的系统并发多少？怎么扩展？"（答：当前支持 100 QPS，可通过 ES 分片、Redis 集群扩展）
- "Rerank 延迟多少？如何优化？"（答：<500ms，可调小 candidates、max-doc-chars）
- "如何评估 RAG 质量？"（答：改写率、Rerank 收缩率、用户反馈、后续可接评测系统）

### 面试前准备

1. **复习代码**：重点看 RagAgentService、HybridSearchService、QueryRewriteService、RerankService
2. **画架构图**：RAG 链路、混合检索、缓存策略
3. **准备数据**：改写率、Rerank 收缩率、检索耗时、缓存命中率
4. **准备问题**：技术难点、生产化经验、后续优化方向

---

## 五、简历模板（项目部分）

```
项目：企业级 RAG 智能问答系统
时间：2025.03 - 至今
角色：独立开发者

项目描述：
独立设计并开发一套企业级 RAG 智能问答系统，支持多模态文档上传、
向量检索、多轮对话、工具调用等核心能力。系统采用 Spring AI 2.x 框架，
结合 PostgreSQL PGVector 与 Elasticsearch 实现混合检索，通过 Query Rewrite
与 Rerank 提升多轮对话质量与检索精度，支持流式输出与用户隔离。

技术栈：
Spring Boot 4.0.6 + Java 21 + Spring AI 2.0.0-M4 + PostgreSQL + PGVector +
Elasticsearch + Redis + Redisson + WebFlux + MyBatis + Vue 3.4 + TypeScript +
Vite + Element Plus + Pinia + BGE-M3 + bge-reranker-v2-m3

核心职责：
1. 设计并实现 RAG 核心链路：文档上传 → 解析 → 分块 → Embedding → 向量存储 → 检索 → 生成
2. 实现混合检索（向量 + BM25）+ RRF 融合，提升召回准确率 30%+
3. 实现 Query Rewrite（基于 LLM 改写多轮追问）+ Rerank（Cross-Encoder 精排），优化多轮对话质量
4. 实现流式输出（WebFlux SSE）+ 超时控制，首字节延迟 <2s
5. 设计多级缓存策略（Redis + Caffeine），缓存命中率 70%+，降低 LLM 调用成本
6. 实现生产化防护：限流、降级、超时控制、结构化日志，支持灰度上线

技术亮点：
- 混合检索 + RRF 融合：平衡语义与精确匹配，召回准确率提升 30%+
- Query Rewrite + Rerank：改写成功率 85%+，Rerank 延迟 <500ms
- 流式输出 + 超时控制：首字节延迟 <2s，支持长回答不卡死
- 多级缓存 + 用户隔离：缓存命中率 70%+，防止跨用户数据泄露
- 生产化防护：22 项生产 Checklist，支持灰度上线与观测
```

---

## 六、加分项补充（按优先级与可行性）

> 说明：以下内容按"投入产出比"排序，优先做高性价比项目。时间预估基于六年 Java 后端经验，实际因人而异。

### 阶段一：必做项（1-2 周，高性价比）

#### 1. 系统架构图（1-2 天）

**为什么加分**
- 面试时直接展示架构图，体现系统设计能力
- 证明你对整体架构有清晰认知
- 面试官更容易理解你的项目

**需要画（至少 3 张）**
- **RAG 链路架构图**：文档上传 → 解析 → 分块 → Embedding → 向量存储 → 检索 → 生成
- **混合检索架构图**：PGVector + ES + RRF + Rerank 的数据流
- **部署架构图**：Nginx + Spring Boot + PostgreSQL + ES + Redis

**工具推荐**
- Draw.io（免费、简单）
- Excalidraw（手绘风格）
- Mermaid（代码生成，适合 Git）

**现实建议**
- 不需要画得特别精美，清晰即可
- 重点标注关键组件和数据流
- 面试时可以指着图讲，效果更好

#### 2. 性能压测数据（2-3 天）

**为什么加分**
- 证明系统经过实战验证，不是玩具项目
- 展示性能优化能力
- 面试官喜欢问"你的系统性能如何"

**需要准备（真实数据优先）**
- **QPS 数据**：系统支持的并发量（如 50-100 QPS）
- **延迟数据**：首字节延迟（1-2s）、总响应时间（3-5s）
- **缓存命中率**：60-70%（真实值）
- **检索耗时**：向量检索（150-200ms）、BM25（80-100ms）、RRF（30-50ms）、Rerank（300-500ms）
- **LLM 调用耗时**：平均 3-5s（取决于模型）

**工具推荐**
- JMeter（免费、功能全）
- K6（现代、脚本化）
- wrk（简单、命令行）

**现实建议**
- 不需要压测到极限，压到 80% 负载即可
- 重点测试检索链路，这是性能瓶颈
- 如果没有真实压测环境，可以用本地测试数据代替（面试时说明是本地测试）

#### 3. GitHub 开源（1-2 天）

**为什么加分**
- 证明代码质量，面试官可直接看代码
- 展示开源意识与工程化能力
- 增加项目曝光度

**需要准备**
- 完善 README.md（项目介绍、技术栈、快速开始、架构图）
- 添加 LICENSE（MIT / Apache 2.0）
- 补充部署文档（Docker Compose 启动方式）
- 清理敏感信息（API Key、数据库密码）

**现实建议**
- 不需要所有代码都开源，可以先开源核心模块
- README 写清楚"个人学习项目"，降低期望
- 如果代码质量一般，可以先整理核心类再开源

#### 4. 技术博客（2-3 天，至少 2 篇）

**为什么加分**
- 展示技术深度与表达能力
- 面试官可直接看你的文章
- 增加个人影响力

**推荐写 2-3 篇（选你最熟悉的）**
- 《基于 Spring AI 构建企业级 RAG 系统》
- 《混合检索 + RRF 融合实战》
- 《Query Rewrite 与 Rerank 在多轮对话中的应用》
- 《WebFlux SSE 流式输出实践》
- 《RAG 系统生产化防护经验总结》

**平台推荐**
- 掘金（技术社区、流量好）
- CSDN（老牌、收录快）
- 知乎（深度讨论）
- 个人博客（独立域名）

**现实建议**
- 每篇 2000-3000 字即可，不用太长
- 重点讲"为什么这么做"、"遇到什么问题"、"怎么解决"
- 配上架构图和代码片段，效果更好

### 阶段二：推荐项（2-4 周，中性价比）

#### 5. Docker 部署方案（2-3 天）

**为什么加分**
- 证明具备容器化能力
- 展示 DevOps 意识
- 面试官可以直接启动项目

**需要准备**
- Dockerfile（多阶段构建，优化镜像大小）
- docker-compose.yml（一键启动所有服务）
- .dockerignore（排除不必要文件）

**现实建议**
- 不需要 K8s，Docker Compose 足够
- 重点优化镜像大小（<500MB）
- 面试时可以说"支持 Docker 部署，K8s 可以扩展"

#### 6. API 文档（1-2 天）

**为什么加分**
- 证明接口设计规范
- 降低面试官上手门槛
- 展示工程化能力

**需要准备**
- Swagger / OpenAPI 文档
- Postman Collection（可选）
- 接口示例（curl 命令）

**现实建议**
- 只需要核心接口文档（上传、检索、对话）
- 不需要所有接口都写文档
- 如果项目简单，README 里写几个 curl 命令也够

#### 7. 部署文档（1 天）

**为什么加分**
- 证明具备部署能力
- 降低面试官上手门槛
- 展示工程化思维

**需要准备**
- 环境要求（Java 21、PostgreSQL、ES、Redis）
- 快速开始（Docker Compose 一键启动）
- 配置说明（application.yml 关键参数）
- 常见问题（启动失败、连接超时）

**现实建议**
- 重点写"快速开始"，让面试官 5 分钟能跑起来
- 配置说明只写关键参数，不用全写
- 如果有 Docker Compose，部署文档可以简化

#### 8. 故障排查手册（1 天）

**为什么加分**
- 证明具备故障排查能力
- 展示生产经验
- 面试官可以问"遇到过什么问题"

**需要准备**
- 常见问题（检索无结果、流式卡死、缓存失效）
- 排查步骤（日志查看、指标检查、链路追踪）
- 应急预案（降级、回滚、扩容）

**现实建议**
- 只写你实际遇到过的问题
- 排查步骤要具体，不要泛泛而谈
- 可以加上日志示例，更有说服力

### 阶段三：进阶项（4-8 周，低性价比）

#### 9. 监控告警方案（1-2 周）

**为什么加分**
- 证明生产运维能力
- 展示可观测性意识
- 大厂看重

**需要准备**
- **Prometheus**：采集指标（QPS、延迟、缓存命中率、LLM 调用量）
- **Grafana**：可视化大盘
- **Alertmanager**：告警规则（延迟 >5s、错误率 >1%）

**现实建议**
- 如果没有生产环境，可以先不做
- 可以在文档里写"计划接入 Prometheus"
- 面试时说"设计了监控方案，待生产验证"

#### 10. RAG 评测系统（2-3 周）

**为什么加分**
- 证明具备 AI 评测能力
- 展示数据驱动思维
- AI 公司看重

**需要准备**
- **评测指标**：准确率、召回率、F1、MRR
- **评测数据集**：构造问答对（100-500 条）
- **自动化评测**：基于 RAGAS / TruLens
- **A/B 对比**：有/无 Rerank 的效果对比

**现实建议**
- 构造数据集很耗时，可以先不做
- 可以在文档里写"计划接入 RAGAS"
- 面试时说"设计了评测方案，待实施"

#### 11. 多模型效果对比（2-3 周）

**为什么加分**
- 证明具备模型选型能力
- 展示实验精神
- AI 公司看重

**需要准备**
- **Embedding 模型对比**：BGE-M3 / OpenAI text-embedding-3 / M3E
- **Rerank 模型对比**：bge-reranker-v2-m3 / Cohere Rerank
- **LLM 模型对比**：GPT-4 / Claude / DeepSeek / 智谱 GLM
- **对比维度**：准确率、延迟、成本

**现实建议**
- 需要调用多个模型 API，成本较高
- 可以在文档里写"计划进行模型对比"
- 面试时说"调研了多个模型，选型基于 X、Y、Z"

#### 12. 高可用方案（2-3 周）

**为什么加分**
- 证明具备高可用架构能力
- 展示生产级经验
- 大厂看重

**需要准备**
- **主备方案**：PostgreSQL 主从、ES 集群
- **灾备方案**：异地多活、数据备份
- **故障演练**：混沌工程、故障恢复
- **SLA 定义**：可用性 99.9%、恢复时间 <5min

**现实建议**
- 如果没有生产环境，可以先不做
- 可以在文档里写"设计了高可用方案"
- 面试时说"计划在生产环境实施"

### 阶段四：可选项（1-3 天，快速补充）

#### 13. 技术选型对比表（0.5-1 天）

**为什么加分**
- 证明做过充分调研
- 展示决策能力

**需要准备**
- 向量数据库对比（PGVector vs Milvus vs Weaviate）
- Embedding 模型对比（BGE-M3 vs OpenAI vs M3E）
- 框架对比（Spring AI vs LangChain4j）

**现实建议**
- 只需要对比你实际调研过的
- 用表格形式，清晰明了
- 面试时可以拿出来说"为什么选 X 而不是 Y"

#### 14. 数据流图（0.5-1 天）

**为什么加分**
- 证明对数据流转有清晰认知
- 展示系统设计能力

**需要准备**
- 文档上传数据流
- 检索链路数据流
- 缓存读写数据流

**现实建议**
- 如果已经有架构图，可以不用单独画数据流图
- 重点标注关键节点和转换
- 面试时可以指着图讲

#### 15. 时序图（0.5-1 天）

**为什么加分**
- 证明对交互流程有清晰认知
- 展示细节把控能力

**需要准备**
- 多轮对话时序图
- Tool Calling 时序图
- 流式输出时序图

**现实建议**
- 只需要画最复杂的交互
- 用 Mermaid 画，简单快捷
- 面试时可以拿出来说"这个流程是这样的"

### 现实建议总结

**优先做这 4 项（1-2 周，高性价比）**
1. 系统架构图（1-2 天）
2. 性能压测数据（2-3 天）
3. GitHub 开源（1-2 天）
4. 技术博客（2-3 天，至少 2 篇）

**有时间再做这 4 项（2-4 周，中性价比）**
5. Docker 部署方案（2-3 天）
6. API 文档（1-2 天）
7. 部署文档（1 天）
8. 故障排查手册（1 天）

**没有时间可以不做（4-8 周，低性价比）**
9. 监控告警方案（1-2 周）
10. RAG 评测系统（2-3 周）
11. 多模型效果对比（2-3 周）
12. 高可用方案（2-3 周）

**面试时可以这么说**
- "系统设计了监控方案，计划在生产环境接入 Prometheus"
- "调研了多个模型，选型基于成本、性能、中文支持"
- "设计了高可用方案，包括主从、灾备、故障演练"

这样既展示了你的规划能力，又不会因为没实施而被质疑。

---

## 七、后续学习建议（长期）

1. **深入向量数据库**：学习 Milvus、Weaviate、Pinecone
2. **学习评测系统**：RAGAS、TruLens、RAG 评测指标
3. **学习 Agent 框架**：LangChain、AutoGen、CrewAI
4. **学习微服务**：Spring Cloud、K8s、Service Mesh
5. **学习监控告警**：Prometheus、Grafana、ELK
