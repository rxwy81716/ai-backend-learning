# local-ai-knowledge

基于 **Spring Boot 4 + Spring AI 2** 的企业级本地知识库 RAG 问答系统。
核心定位：**多 Agent 智能路由 + 混合检索（向量 + BM25 + RRF + Rerank）+ 流式 SSE + 全链路韧性**。

> 本仓库刻意覆盖 RAG 落地里几乎所有真实场景的工程难点：意图路由、查询改写、混合召回、跨编码器精排、SSE 协议设计、首字节超时熔断、Redis Lua 限流、自适应限速等。

---

## 技术栈

| 类别       | 选型                                                                                  |
|----------|-------------------------------------------------------------------------------------|
| 框架       | Spring Boot 4.0 + Spring AI 2.0.0-M4                                                 |
| Java     | 21（Virtual Threads / Records / Pattern Matching）                                    |
| LLM      | OpenAI 兼容协议 — 默认 SiliconFlow，支持 DeepSeek / GLM / 任意 OpenAI-Compatible 端，运行时按 `modelKey` 切换 |
| Embedding| bge-m3（1024 维）                                                                      |
| Reranker | bge-reranker（Cross-Encoder 精排，可选）                                                    |
| 向量存储     | Elasticsearch 9.x（dense_vector + cosine）+ PgVector（双写 / 降级）                          |
| 关系库      | PostgreSQL 16（用户 / 角色 / 文档任务 / 会话）                                                  |
| 缓存       | Redis（会话热缓存）+ Caffeine（embedding / 检索结果 / 文档概览）                                       |
| 队列       | Redisson 阻塞队列（文档解析任务持久化）                                                            |
| 鉴权       | Spring Security 6 + JWT + RBAC                                                       |
| 可观测性     | Spring Boot Actuator + Micrometer Prometheus                                          |
| 文档解析     | Apache Tika                                                                          |

---

## 架构总览

```
                                ┌─────────────────────────────┐
   /api/rag/chat/stream  ──▶   │  RagController (SSE Endpoint) │
                                └──────────────┬──────────────┘
                                               ▼
                                ┌─────────────────────────────┐
                                │   MultiAgentOrchestrator    │   ◀── ChatHistoryCacheService（Redis+DB）
                                │  - SSE 协议组装              │
                                │  - 首字节/静默超时熔断        │   ◀── RateLimitFilter（Lua 原子限流）
                                │  - 错误分类 + 兜底文案        │
                                └──────────────┬──────────────┘
                                               ▼
                              ┌─────────────────────────────────┐
                              │   IntentRouter（关键词→LLM 兜底）│
                              └──────────────┬──────────────────┘
                                             │
        ┌────────────┬───────────┬───────────┼───────────────┬───────────────┐
        ▼            ▼           ▼           ▼               ▼               ▼
 KnowledgeAgent  Planner   DocumentOverview DocumentSearch  HotSearchAgent  ChatAgent
   (混合检索)     (ReAct)     (按文档采样)      (BM25 全文)      (热榜)        (纯 LLM)
        │
        ▼
┌───────────────────────────────────────┐
│   HybridSearchService                  │
│  ┌──────────┐    ┌──────────┐          │
│  │ ES Vector │    │ ES BM25  │  并发 ──▶  RRF 融合 ──▶ Caffeine 缓存
│  └──────────┘    └──────────┘          │                │
│        ▲                ▲              │                ▼
│        │                │              │       Cross-Encoder Rerank（可选）
│   bge-m3 embed     ik_max_word         │                │
└────────│───────────────────────────────┘                ▼
         │                                          融合 + 精排后的 Top-K
         ▼
   query embed cache（Caffeine, 命中即跳过推理）
```

---

## 核心亮点

### 1. 多 Agent 智能路由
- **`IntentRouter`**：先用关键词集合 + 正则做 O(1) 命中，命中失败再用 LLM 兜底分类，避免每条请求都吃一次 LLM 延迟。
- 6 个专职 Agent：`KnowledgeAgent`（标准 RAG）、`PlannerAgent`（ReAct，工具循环 + 真流式 finalize）、`DocumentOverviewAgent`（按文档采样总结）、`DocumentSearchAgent`（关键词全文定位）、`ChatAgent`（纯 LLM）、`HotSearchAgent`（热榜资讯）。
- 路由结果通过 SSE `[STEP]` 协议推送给前端，用户能看到「检索 → 工具调用 → 生成」全过程。

### 2. 混合检索（向量 + BM25 + RRF + Rerank）
- `HybridSearchService` 在**专用线程池**上并发跑两路召回（避开 `ForkJoinPool.commonPool` 阻塞 IO 引发的连带堵塞 — `CacheConfig#ragSearchExecutor`）。
- **RRF（Reciprocal Rank Fusion）** 融合两路 ranking，对长查询、稀疏命中更稳；可选 Cross-Encoder rerank 精排。
- **`ragSearchCache`**（Caffeine, 10min TTL）规避 bge-m3 单次 ~2.7s 的推理；新文档入库 / 删除时主动 `invalidateAll()`。
- 用户私有文档 + 公开文档**所有权过滤**直接在 ES query 里完成，不放行后过滤。

### 3. 流式 SSE 协议工程化
- 自定义协议把执行轨迹和 token 流合并到一条 SSE：
  - `[STEP]{"type":"route", ...}[/STEP]` — 执行进度事件（路由 / 工具调用 / 生成）
  - `... token token token ...`              — 模型 token 流
  - `[META]{...}[/META]`                       — 末尾结构化元数据（耗时 / 命中数 / 引用 / 错误码）
- **首字节超时 + 静默超时**双重熔断：见 `MultiAgentOrchestrator` 中的 `firstByteTimeout` / `idleTimeout`。
- **9 类错误码**精细化兜底（`StreamErrorHandler`）：429 / 401 / 403 / 上游 5xx / 网络断 / 超时 / 用户取消 等都有对应文案。
- **PlannerAgent 真流式 finalize**：纯 reactive 桥接（`subscribe → FluxSink`），不再用 `CountDownLatch` 阻塞，保留 Reactor 背压。

### 4. 韧性 & 可观测性
- **`SimpleCircuitBreaker`**（CLOSED → OPEN → HALF_OPEN 三态机）：保护 embedding / 检索两条调用链。
- **Redis Lua 原子限流**（`RateLimitFilter`）：脚本内一气做 `INCR + PEXPIRE`，规避「INCR 后服务挂掉 → key 永不过期」的固定窗口竞态；分匿名 IP / 已认证用户 QPS / 已认证用户日配额三层。
- **自适应限速**（`EsVectorStoreService` 入库）：类似 TCP 拥塞控制，遇到 429 增大暂停步长 + 指数退避，成功逐步降低，自动收敛到最优吞吐。
- **指标暴露**：`/actuator/prometheus` — embedding 缓存命中率、检索熔断器状态、chat 耗时 P50/P90/P99、各错误码计数。

### 5. 多模型运行时切换
- `ChatModelRegistry`：启动时为每个配置的 provider 构建 `OpenAiChatModel`（仅当 api-key 非空），`@PostConstruct` 注册到 `Map<String, ChatClient>`。
- `ChatClientConfig` 把 `DEFAULT_KEY` 对应的实例暴露成 `@Primary ChatClient`，业务代码统一一份注入；切换模型走 `chatModelRegistry.getClient(modelKey)`。
- 前端调用 `/api/rag/chat/stream?model=glm` 即时切到 GLM；同进程同时持有 SiliconFlow / DeepSeek / GLM，做 A/B / 灰度 / 容灾兜底。

---

## 项目结构（核心模块）

```
src/main/java/com/jianbo/localaiknowledge/
├── config/
│   ├── CacheConfig.java              # Caffeine 缓存 + ragSearchExecutor 专用线程池
│   ├── ChatClientConfig.java         # @Primary ChatClient ← ChatModelRegistry
│   ├── ChatModelProperties.java      # app.chat-models.providers 多模型配置
│   ├── RateLimitFilter.java          # Lua 原子 INCR+EXPIRE 限流（StringRedisTemplate）
│   ├── RedisConfig.java / RedissonConfig.java
│   ├── SecurityConfig.java / JwtAuthenticationFilter.java
│   └── VectorStoreConfig.java
├── controller/
│   ├── RagController.java            # /api/rag/* 问答 / 会话 / 反馈 / Prompt
│   ├── DocumentController.java       # /api/doc/* 上传 / 状态 / 重解析 / 下载
│   ├── AdminController.java          # /api/admin/* 用户 / 角色 / 菜单 / Agent
│   └── AuthController.java           # /auth/login + JWT 签发
├── service/
│   ├── HybridSearchService.java      # 混合检索（向量+BM25 RRF）+ Caffeine 缓存
│   ├── EsVectorSearchService.java    # ES 向量召回 + 用户归属过滤
│   ├── EsKeywordSearchService.java   # ES BM25 召回 + ik_max_word 分词
│   ├── EsVectorStoreService.java     # 入库（自适应限速 + 429 指数退避）
│   ├── RerankService.java            # Cross-Encoder 精排
│   ├── EmbeddingService.java         # 带缓存装饰的 EmbeddingModel
│   ├── DocumentParseService.java     # Tika 解析 + 切片 + 双写 ES/PG
│   ├── ChatHistoryCacheService.java  # 多轮对话 Redis 热缓存 + DB 兜底
│   └── agent/
│       ├── MultiAgentOrchestrator.java  # SSE 主流程
│       ├── IntentRouter.java            # 关键词 + LLM 兜底分类
│       ├── ChatModelRegistry.java       # 运行时多模型切换
│       ├── KnowledgeAgent.java / PlannerAgent.java / DocumentOverviewAgent.java …
│       ├── StreamErrorHandler.java      # 9 类错误码 + 兜底文案
│       ├── MetaBuilder.java             # [META] 元数据构造
│       ├── ChatMessageBuilder.java      # 系统 prompt + 历史拼装
│       └── FollowUpDetector.java        # 追问识别 + 上文注入
└── utils/
    ├── RagFormatUtil.java            # 检索结果统一格式化
    └── SecurityUtil.java             # 当前 userId / 角色获取
```

---

## 快速启动

### 前置依赖
- JDK 21
- Maven 3.9+
- PostgreSQL 16 + 启用 `pgvector` 插件
- Elasticsearch 9.x + IK 分词器
- Redis 7.x

### 配置（`application.yml`）
关键配置（节选）：

```yaml
spring:
  ai:
    openai:
      api-key: ${SILICONFLOW_API_KEY}
      base-url: https://api.siliconflow.cn
      chat:
        options:
          model: deepseek-ai/DeepSeek-V3
      embedding:
        options:
          model: BAAI/bge-m3

app:
  chat-models:
    default-key: default      # 与 ChatModelRegistry.DEFAULT_KEY 对齐
    providers:
      glm:
        base-url: https://open.bigmodel.cn/api/paas/v4
        api-key: ${GLM_API_KEY:}
        model: glm-4-flash
      deepseek:
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-chat

  rate-limit:
    anonymous-max: 20         # 匿名 IP 每窗口最多请求数
    window-minutes: 60
    chat:
      user-qps: 2             # 已认证用户 chat QPS
      user-daily: 200         # 已认证用户日配额

  rag:
    hybrid:
      enabled: true
      vector-top-k: 10
      keyword-top-k: 10
      rrf-k: 60
      similarity-threshold: 0.5
      timeout-ms: 5000
    rerank:
      enabled: false          # 视 reranker 服务可用性开启
```

### 运行

```bash
mvn clean package -DskipTests
java -jar target/local-ai-knowledge-*.jar
```

---

## 主要 API

| Method | Path                                  | 说明                                              |
|--------|---------------------------------------|------------------------------------------------|
| POST   | `/auth/login`                         | 登录，返回 JWT                                      |
| POST   | `/api/doc/upload`                     | 上传文档（PRIVATE 默认，立即返回 taskId）                    |
| POST   | `/api/doc/crawler-upload`             | 爬虫专用上传（X-Crawler-Key 鉴权 + 固定 PUBLIC）             |
| GET    | `/api/doc/status/{taskId}`            | 查询解析进度                                          |
| GET    | `/api/doc/tasks`                      | 当前用户文档列表（管理员看全量）                                |
| POST   | `/api/doc/reparse/{taskId}`           | 重新解析（清旧 + 重切 + 重写库）                              |
| DELETE | `/api/doc/{taskId}`                   | 删除文档（含 ES + PG + 上传文件）                          |
| GET    | `/api/rag/chat/stream`                | **SSE 流式问答**（`?question=&sessionId=&chatMode=&model=&thinking=`） |
| GET    | `/api/rag/sessions`                   | 当前用户全部会话（含首条问题 + 自定义标题）                          |
| GET    | `/api/rag/history/{sessionId}`        | 会话全部消息                                          |
| DELETE | `/api/rag/session/{sessionId}`        | 删除会话                                            |
| GET    | `/api/rag/models`                     | 当前可用 ChatModel keys                              |
| GET    | `/api/rag/prompts`                    | SystemPrompt 列表                                  |
| GET    | `/actuator/prometheus`                | Prometheus 指标                                    |

> SSE 协议示例（一条 stream 内的混合内容）：
> `[STEP]{"type":"route","intent":"KNOWLEDGE",...}[/STEP] [STEP]{"type":"tool","name":"searchKnowledgeBase","phase":"start"}[/STEP] ... [STEP]{...,"phase":"end","hits":7,"costMs":2840}[/STEP] 文档显示 ... 等正文 token ... [META]{"intent":"KNOWLEDGE","tools":["searchKnowledgeBase"],"docCount":7,"refs":[...],"errorCode":null}[/META]`

---

## 性能指标（实测）

| 指标                          | 数值                                       |
|-----------------------------|------------------------------------------|
| 意图路由 P95                    | < 50 ms（关键词命中路径，零 LLM 调用）                |
| Embedding 缓存命中率              | ~ 60%+ （bge-m3 单次 ~2.7s 直接跳过）             |
| 混合检索整体超时                    | 5 s（任一路超时即取已完成的一路）                       |
| chat 流式首字节                   | 通常 < 1.5 s（SiliconFlow / DeepSeek-V3）     |
| 入库吞吐                        | 50 chunk / batch，自适应限速收敛后 ~ 200 chunk/s |

---

## 测试

```bash
mvn test                                           # 全部单测
mvn test -Dtest=StreamErrorHandlerTest             # 单个
```

---

## License

仅作学习交流用。LLM 服务密钥与商用许可由各 provider 自行约束。
