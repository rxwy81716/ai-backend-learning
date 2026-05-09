# Day30 · Agent 能力增强：Planner + 多模型注册表 + SSE 步骤事件 + 真流式 Finalize + 前端时间线

> 目标：把 `local-ai-knowledge` 模块对齐 JChatMind 五大面试亮点
> （自主决策 / 工具框架 / RAG+向量 / 多模型注册表 / SSE 实时推送），
> 把改造前只能对标 2 条（工具 + RAG）+ 半条（SSE）升级为 5 条全部命中。
>
> 本文档分三轮：
> - **Round 1**：基础 Planner + ChatModelRegistry + SSE 步骤事件
> - **Round 2**：PlannerAgent 真流式 finalize + 抽出 StreamErrorHandler + 前端 `[STEP]` 时间线 UI
> - **Round 3**：Embedding 缓存装饰器 + 离线 RAG 评估（hit@k / MRR）+ 关键模块单测三件套
> - **Round 4**：Orchestrator 拆分（ChatMessageBuilder / MetaBuilder / FollowUpDetector）+ SimpleCircuitBreaker 检索熔断 + Micrometer/Prometheus 指标 + Planner web_search/get_hot_list 两个 action

---

## 一、改造前 vs 改造后

| 能力 | 改造前 | Round 1 后 | Round 2 后 | Round 3 后 | Round 4 后 |
|---|---|---|---|---|---|
| Think-Execute 循环 | ❌ 一次意图分类 | ✅ ReAct | ✅✅ 真流式 finalize | ✅✅ | ✅✅ + **3 种 action（kb/web/hot）** |
| 工具调用框架 | ✅ Tool 注解 | ✅ + step 事件 | ✅ | ✅ | ✅ + Planner 直接编排多工具 |
| RAG + pgvector | ✅✅ Hybrid + Rerank | ✅✅ | ✅✅ | ✅✅ + 离线评估 | ✅✅ + **检索熔断（SimpleCircuitBreaker）** |
| 多模型切换 | ❌ profile 启动级 | ✅ 注册表运行时切换 | ✅ | ✅ | ✅ + **per-model QPS/p95 Timer** |
| SSE 实时推送 | ⚠️ 只有 token + META | ✅ `[STEP]` | ✅✅ 时间线 UI | ✅✅ 契约单测 | ✅✅ |
| 错误处理职责 | ⚠️ 都堆在 Orchestrator | ⚠️ | ✅ 抽出 `StreamErrorHandler` | ✅ 单测覆盖 | ✅ + **错误码 Counter 埋点** |
| Embedding 性能 | ⚠️ ~2.7s/次 | ⚠️ | ⚠️ | ✅ Caffeine 装饰器 | ✅ + **命中率 gauge** |
| Orchestrator 体积 | ⚠️ 600+ 行 God Service | ⚠️ | ⚠️ | ⚠️ | ✅ **4 个 Component 各 100 行** |
| 可观测性 | ❌ 只有日志 | ❌ | ❌ | ❌ | ✅ **`/actuator/prometheus` + 自定义指标** |
| 单测覆盖 | ❌ 0 个 | ❌ | ❌ | ✅ 3 套 | ✅ 3 套 |

---

## 二、改动清单

### Round 1（基础接入）

**新增文件**

| 文件 | 作用 |
|---|---|
| `config/ChatModelProperties.java` | `@ConfigurationProperties("app.chat-models")`，声明多 provider |
| `service/agent/ChatModelRegistry.java` | 运行时多模型注册表（key → ChatClient） |
| `service/agent/PlannerAgent.java` | ReAct 规划器，自主 Think-Act-Observe 循环 |

**修改文件**

| 文件 | 改动 |
|---|---|
| `service/agent/RagToolContext.java` | 追加 `modelKey` + `Sinks.Many<String> stepSink` + `emitStep / stepsFlux / completeSteps` |
| `service/agent/AgentType.java` | 新增 `PLANNER` 枚举 |
| `service/agent/IntentRouter.java` | 新增 `PLAN_KEYWORDS` 快速匹配规则，命中即返 `PLANNER` |
| `service/agent/KnowledgeTools.java` | 工具前后推送 `tool(start/end)` 事件 |
| `service/agent/MultiAgentOrchestrator.java` | 重载 `chatStream(..., modelKey)`；`Flux.merge(stepsFlux, tokenFlux)`；推送 `route/rewrite/generate` 事件；`cleanAnswer` 兼容剔除 `[STEP]` |
| `controller/RagController.java` | `/chat/stream` 支持 `model` 字段；新增 `GET /api/rag/models` |
| `resources/application.yml` | 新增 `app.chat-models.*` 配置段 |

### Round 2（体验 + 工程化打磨）

**新增文件**

| 文件 | 作用 |
|---|---|
| `service/agent/StreamErrorHandler.java` | 把 SSE 流错误分类 + 文案渲染从 Orchestrator 抽出 |

**修改文件**

| 文件 | 改动 | 修改原因 |
|---|---|---|
| `service/agent/PlannerAgent.java` | `finish` 分支不再 `emitAsChunks(answer)`，改为 `streamFinalize(...)` 调一次 `stream().content()` | **修改原因**：原来 ReAct 规划器最后把 JSON 里的 `answer` 字段切片下发，本质是一次性回放，首字节延迟 ≈ Planner 完整规划时长（1~2s），用户体感不像流式。改造后丢掉 JSON 壳，让 LLM 边生成边吐 token，首字节回到 ~300ms 量级 |
| `service/agent/MultiAgentOrchestrator.java` | 移除 `classifyStreamError` / `renderStreamErrorMessage` 实现，改为持有 `StreamErrorHandler` 并瘦代理；删掉 `HttpClientErrorException` 等已下沉的 import | **修改原因**：见 #4 review，Orchestrator 已 600+ 行 God Service，错误分类与"消息编排"无关，下沉后单测无须 Spring 上下文，多个 Agent 后续可直接复用同套错误映射 |
| `local-ai-knowledge-front/src/views/rag/RagChat.vue` | 加 `StepEvent` 接口、`extractMarkers` 跨 chunk 解析器、`formatStage / formatStepDetail`；模板新增执行步骤时间线 + CSS | **修改原因**：Round 1 后端已经在吐 `[STEP]{json}[/STEP]`，但前端原逻辑只 strip `[META]`，会把 STEP 当普通 token 拼到答案里，用户既看不见也很丑。Round 2 把它解析成时间线，对应面试亮点"SSE 实时推送 → 执行状态可视化" |

### Round 3（性能 + 评估 + 测试）

**新增文件**

| 文件 | 作用 |
|---|---|
| `config/CachedEmbeddingModel.java` | `EmbeddingModel` 装饰器，对单条 query embedding 加 Caffeine 缓存 |
| `src/test/resources/rag-golden-queries.jsonl` | RAG 检索离线评估 golden 集（jsonl，含 query + expectedSourceKeywords） |
| `src/test/java/.../eval/RetrievalEvaluatorTest.java` | 跑 golden 集计算 hit@1/5/10 + MRR + 平均延迟，输出 Markdown 报告（默认 disabled，靠 `RAG_EVAL_ENABLED=true` 开） |
| `src/test/java/.../service/agent/IntentRouterTest.java` | `IntentRouter` 关键词路由 + LLM 兜底分类单测（mock ChatModel，纯 JUnit） |
| `src/test/java/.../service/agent/ThinkBlockStripperTest.java` | `ThinkBlockStripper` 跨 chunk / 嵌套 / 未闭合 / 字符级流式 等 10 个 case |
| `src/test/java/.../service/agent/StreamErrorHandlerTest.java` | 错误码分类 + 文案渲染分支全覆盖 |

**修改文件**

| 文件 | 改动 | 修改原因 |
|---|---|---|
| `config/EmbeddingModelConfig.java` | `customEmbeddingModel()` 出口前包一层 `CachedEmbeddingModel`；新增 `app.embedding.cache.{enabled,max-size,ttl-minutes}` 三个开关 | **修改原因**：bge-m3 单次远程 embedding ≈ 2.7s，是首字节延迟最大的固定成本。`HybridSearchService` 现有的 `ragSearchCache` 缓存的是最终 docs，受 query+userId+topK 三元组组合爆炸影响 miss 率高；缓存 query → vector 这一层粒度更细，命中率显著更高，且对所有走 `VectorStore.similaritySearch` 的路径（包括 PlannerAgent 多轮检索的同义子 query）都生效 |

### Round 4（架构瘦身 + 稳定性 + 可观测）

**新增文件**

| 文件 | 作用 |
|---|---|
| `service/agent/FollowUpDetector.java` | 追问检测（短问题 + 序数指代正则）+ 跨模式历史隔离 `isSameMode` 下沉 |
| `service/agent/ChatMessageBuilder.java` | system prompt 合并 + 历史过滤 + token 裁剪 → 独立 Component |
| `service/agent/MetaBuilder.java` | `[META]` map 构造 + references 去重截断 + JSON 兜底序列化 |
| `utils/SimpleCircuitBreaker.java` | 轻量三态机（CLOSED/OPEN/HALF_OPEN），无外部依赖 ~80 行 |
| `config/RagMetrics.java` | 注册 gauge（embed-cache 命中率 / search-breaker 状态）+ Counter（错误码）+ Timer（per-model chat 耗时） |

**修改文件**

| 文件 | 改动 | 修改原因 |
|---|---|---|
| `service/agent/MultiAgentOrchestrator.java` | 拆掉 `buildMessages` / `resolveAgentSystemPrompt` / `isFollowUp` / `loadLastAssistantContent` / `isSameMode` / `buildMeta` / `buildReferences` / `toJson` 共 8 个私有方法实现（仅保留瘦代理），构造器签名精简为 7 个 Bean；新增可选 `RagMetrics` 注入 + `chatStartNanos` 计时 + `doFinally` 写 Timer | **修改原因**：review #2 要求拆 600+ 行 God Service。瘦代理保留方法签名避免扩散式改名，未来可按需内联；新增 `RagMetrics` 是为了覆盖 review #8 的可观测诉求 |
| `service/HybridSearchService.java` | 字段级 `SimpleCircuitBreaker searchBreaker`；`searchWithOwnership` 用 `breaker.callOrFallback(...)` 包住 `doSearchWithOwnership`，熔断时返回空列表由调用方走"基于通用知识回答"分支兜底；暴露 `getSearchBreaker()` 供 metrics 读取 | **修改原因**：ES / PG 任一故障时，若让异常冒泡会打断整条 chat SSE；旧代码只有"单次超时"保护（`parallelTimeoutMs=3s`），但连续超时仍会拖垮 chat 首字节。引入连续失败计数 + 30s 冷却 + 半开探测，配合 fallback 空列表 → 知识库 Agent 自动降级为 LLM 直答，用户体验从"报错断流"变"基于通用知识回答 + META.source=llm_direct" |
| `service/agent/StreamErrorHandler.java` | 新增 `@Autowired(required=false) RagMetrics`；`classify` 拆为 public 埋点入口 + `classifyInternal` 原逻辑；非 null 时 `ragMetrics.incrementErrorCode(code)` | **修改原因**：review #4 / #8 的"错误码 counter"。单测不接 Spring 上下文时 `new StreamErrorHandler()` 直接 `ragMetrics=null`，`classify` 不触发埋点，保证 StreamErrorHandlerTest 零改动仍绿 |
| `service/agent/PlannerAgent.java` | 新增 `WebSearchService` / `HotSearchService` 依赖；system prompt 的 action 枚举扩展为 `search_kb \| web_search \| get_hot_list \| finish`；action 分发 switch 改成三分支 + 统一上限 3 次；新增 `formatWebSearchResults(query, results)` 折叠 URL / title / content | **修改原因**：review #8 的"Planner 编排感更强"。旧版本仅有 `search_kb` 一种 tool，不能体现多工具调度；补齐 web_search（时效性问题）+ get_hot_list（热榜），让 Planner 自主决定用哪个工具 |
| `pom.xml` | 新增 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`；移除原计划的 `resilience4j-*`（本机 maven 代理 SSL 拦截无法下载，改用 SimpleCircuitBreaker 替代） | **修改原因**：引入 actuator + Prometheus 标准指标面是 review #8 可观测性的基础；资源受限情况下自写 ~80 行熔断器比强依赖缺失工件更可控 |
| `config/SecurityConfig.java` | `permitAll` 放行 `/actuator/**`（生产环境应改 IP 白名单或独立 basic auth） | **修改原因**：默认 Spring Security 会拦 actuator，Prometheus 抓取时直接 401 |
| `resources/application.yml` | 新增 `management.endpoints.web.exposure.include: health,info,metrics,prometheus` + `management.metrics.tags.application` | — |

---

## 三、技术要点详解

### 3.1 ChatModelRegistry（运行时多模型切换）

**设计思路**：

- 启动时按 `app.chat-models.providers.*` 为每个 provider 构造 `OpenAiChatModel`（复用 `EmbeddingModelConfig` 同款 `OpenAiApi.builder() + RestClient` 套路）。
- 同时把 Spring AI 自动配置的 `@Primary ChatModel` 注册为 `default` 兜底。
- 若 `app.chat-models.default-key` 指向某个配置型 provider，把它别名到 `default`。
- 对外只暴露 `getClient(modelKey) / getModel(modelKey) / availableKeys()`，未知 key 自动 fallback。

**与 profile 的区别**：

| 维度 | profile 切换 | 注册表 |
|---|---|---|
| 生效时机 | 启动时 | 每次请求 |
| 并行持有多模型 | ❌ | ✅ |
| 前端动态选择 | ❌ | ✅（`GET /api/rag/models`） |
| 适用场景 | 部署级切换 | A/B、灰度、容灾、按问题类型分派 |

### 3.2 PlannerAgent（ReAct Think-Execute 循环）

**循环主干**（`MAX_ITERATIONS = 4`）：

```text
 ┌─────────────────────────────────────────────────────────┐
 │ for (iter = 1 .. MAX_ITERATIONS):                        │
 │   emitStep("think", {iter})                              │
 │   raw  = llm.call(messages)            ← 单行 JSON       │
 │   step = parseStep(raw)                                  │
 │   emitStep("act", {action, thought})                     │
 │                                                          │
 │   switch (step.action):                                  │
 │     case "finish":                                       │
 │       emitStep("generate")                               │
 │       streamFinalize(...)             ← Round 2 真流式   │
 │       emitStep("done")     → return                      │
 │     case "search_kb":                                    │
 │       emitStep("tool", {name, query})                    │
 │       obs = knowledgeTools.searchKnowledgeBase(query)    │
 │       emitStep("observe", {hits, chars})                 │
 │       messages.add(UserMessage("【工具结果】" + obs))    │
 │       continue                                           │
 └─────────────────────────────────────────────────────────┘
```

**关键工程细节**：

- 规划阶段用 `call()` 同步返回完整 JSON（流式无法保证 JSON 可解析）；
- **finalize 阶段**改用 `stream().content()` 真流式（Round 2 改进）。
- 最多调用 3 次 `search_kb`，`MAX_ITERATIONS=4` 防死循环；超限时插入 system 提示强制 `finish`。
- JSON 解析失败时降级输出原文，而不是中断对话。
- 所有阶段都经 `ctx.emitStep(...)` 推送 SSE 可视化事件。

**触发方式**：

`IntentRouter.PLAN_KEYWORDS` 快速匹配——用户问题包含 "规划一下 / 多步 / step by step / 逐步分析 / 先查再答 / 先检索…" 等关键词自动路由。

### 3.3 真流式 Finalize（Round 2 新增）

**原方案问题**：

```java
// 旧：把 JSON 里的 answer 切片回放，首字节 = 整个规划耗时
emitAsChunks(step.answer(), sink);
```

**新方案**：

```java
private void streamFinalize(...) {
    List<Message> finalMsgs = new ArrayList<>(baseMsgs);
    finalMsgs.set(0, new SystemMessage(FINALIZE_SYSTEM_PROMPT));
    finalMsgs.add(new UserMessage("基于以上工具结果，直接以自然语言回答用户问题：" + userQuestion));

    CountDownLatch done = new CountDownLatch(1);
    final boolean[] gotAny = {false};
    llm.prompt().messages(finalMsgs).stream().content()
        .subscribe(
            chunk -> { gotAny[0] = true; sink.next(chunk); },
            err   -> { /* fallback 到 planner.answer */ done.countDown(); },
            done::countDown);
    done.await();    // 已在 boundedElastic 线程上，阻塞 OK
}
```

**收益**：

| 维度 | 旧方案 | 新方案 |
|---|---|---|
| 首字节延迟 | 规划耗时全打满（1~2s） | LLM 流式起始（~300ms） |
| token 颗粒度 | ~40 字符切片，伪流式 | 真 LLM token 流 |
| 答案质量 | 受 JSON 字段长度上限约束 | 不受 JSON 限制，markdown 自由 |
| 失败兜底 | 无 | 三层（流式异常 → planner.answer → 错误文案） |

**为何用 `CountDownLatch` 而不是 `block()`**：`.block()` 会拒绝 reactive thread；`subscribe + latch` 在 boundedElastic 上同步等待，符合 Spring AI 的线程模型。

### 3.4 SSE 执行状态事件协议

**协议约定**：

- 在原 token 流里插入 `[STEP]{"stage":"...","ts":...,...}[/STEP]` 文本行。
- 前端按正则 `\[STEP\](.*?)\[/STEP\]` 抽取，JSON 解析后渲染时间线。
- 末尾仍保留 `[META]...[/META]`，向后兼容。

**事件阶段枚举**：

| stage | 触发点 | payload |
|---|---|---|
| `route` | `IntentRouter` 命中意图 | `intent, mode, model` |
| `rewrite` | `QueryRewrite` 完成 | `changed, costMs, reason` |
| `generate` | agent 订阅开始 / Planner finish 进入 finalize | `intent, iter` |
| `tool` | `KnowledgeTools.searchKnowledgeBase` 前/后 | `name, phase(start/end), query, hits, costMs` |
| `think` / `act` / `observe` | `PlannerAgent` ReAct 三段 | `iter, action, thought, hits, ...` |
| `done` | `PlannerAgent` 完成 | `searchCalls, iter, truncated?` |

**实现细节**：

```java
// RagToolContext
private final Sinks.Many<String> stepSink = Sinks.many().multicast().onBackpressureBuffer();
public Flux<String> stepsFlux() { return stepSink.asFlux(); }
public void emitStep(String stage, Map<String, Object> payload) {
    stepSink.tryEmitNext("[STEP]" + json + "[/STEP]");
}
public void completeSteps() { stepSink.tryEmitComplete(); }

// MultiAgentOrchestrator#chatStream
Flux<String> tokenFlux = agent.execute(request)
    .doOnSubscribe(s -> ctx.emitStep("generate", ...))
    ...
    .doFinally(sig -> ctx.completeSteps());   // 关键：关 sink，不挂住 SSE 连接

return Flux.merge(ctx.stepsFlux(), tokenFlux)  // 步骤事件与 token 交织
    .concatWith(buildMetaFlux(...))            // 末尾拼 META
    .onErrorResume(...);
```

**持久化兼容**：`cleanAnswer` 增加 `STEP_BLOCK = \[STEP].*?\[/STEP]` 正则，避免步骤 JSON 进 `chat:history`。

### 3.5 StreamErrorHandler（Round 2 新增）

**抽出动机**（对应 review #4）：

- Orchestrator 已 600+ 行，错误分类与"消息编排 / 路由 / Rewrite / 持久化"职责完全无关。
- 单测时不希望被强制装载整个 Spring 上下文。
- 后续 ChatAgent / PlannerAgent / WebSearchAgent 都需要同一套错误码 → 用户文案的映射，避免每个 Agent 自己抄一遍。

**API**：

```java
@Component
public class StreamErrorHandler {
    public String classify(Throwable e, boolean firstByteReceived);  // → 稳定错误码
    public String render(String code, boolean firstByteReceived);    // → 中文兜底文案
    // 错误码常量：CODE_TIMEOUT_FIRST_BYTE / CODE_RATE_LIMIT / CODE_AUTH / ...
}
```

Orchestrator 改为瘦代理：

```java
private String classifyStreamError(Throwable e, boolean firstByteReceived) {
    return streamErrorHandler.classify(e, firstByteReceived);
}
```

### 3.6 前端 `[STEP]` 时间线 UI（Round 2 新增）

**核心问题**：SSE 文本帧可能把 `[STEP]{json}[/STEP]` 切成两段（如 `[STEP]{"stag` + `e":"think"}[/STEP]`）。

**解法**：跨 chunk 缓冲 + 一次性贪婪抽取：

```ts
function extractMarkers(buffer: string) {
  const fullRe = /\[(STEP|META)\]([\s\S]*?)\[\/\1\]/g
  let out = '', lastIndex = 0
  while ((m = fullRe.exec(buffer))) {
    out += buffer.slice(lastIndex, m.index)
    if (m[1] === 'STEP') steps.push(JSON.parse(m[2]))
    else meta = JSON.parse(m[2])
    lastIndex = m.index + m[0].length
  }
  // 末尾出现 '[' 则保留至下次（可能是未闭合的 marker 起始）
  let rest = buffer.slice(lastIndex)
  const i = rest.indexOf('[')
  if (i >= 0) { out += rest.slice(0, i); rest = rest.slice(i) }
  else        { out += rest; rest = '' }
  return { steps, meta, text: out, rest }
}
```

**UI**：

- 答案下方加可折叠的"执行步骤 · N 步"面板。
- 每条事件一颗彩色圆点 + 阶段中文名（🔀 路由 / 🤔 思考 / ⚡ 决策 / 🔧 工具调用 / 👁 观察 / ✅ 完成）。
- 一行简洁描述：`tool=searchKnowledgeBase/end · hits=5 · 320ms`。
- CSS 用左侧竖线 + 圆点，类似 GitHub Actions 时间线。

### 3.7 CachedEmbeddingModel（Round 3 新增）

**装饰器模式**：

```java
@Bean @Primary
public EmbeddingModel customEmbeddingModel() {
    EmbeddingModel raw = new OpenAiEmbeddingModel(...);
    if (!cacheEnabled) return raw;
    return new CachedEmbeddingModel(raw, cacheMaxSize, cacheTtlMinutes);
}
```

`CachedEmbeddingModel implements EmbeddingModel`，重写 `call(EmbeddingRequest)`：

```java
@Override
public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> instructions = request.getInstructions();
    if (instructions.size() != 1) {
        return delegate.call(request);  // 批量 ingestion 透传
    }
    String text = instructions.get(0);
    float[] cached = cache.getIfPresent(text);
    if (cached != null) {
        return new EmbeddingResponse(
            List.of(new Embedding(cached.clone(), 0)),
            new EmbeddingResponseMetadata());
    }
    EmbeddingResponse resp = delegate.call(request);
    cache.put(text, resp.getResult().getOutput().clone());
    return resp;
}
```

**关键点**：

- **只缓存单条 query**：批量 ingestion（chunk 文本几乎不重复）透传 delegate，不污染缓存
- **EmbeddingModel 接口的 `embed(String) / embed(List<String>) / dimensions()` 都是 default 方法**，最终汇聚到 `call(...)`，因此重写一处即可全路径受益
- **`embed(Document)` 是 abstract**，必须显式实现 → 直接透传 delegate
- **clone vector**：Spring AI 部分实现会 in-place 归一化向量，不 clone 会污染缓存条目
- 容量 maxSize=2000（约 8MB）、TTL=10min，可由 `app.embedding.cache.{max-size, ttl-minutes}` 调节

**收益**（典型场景）：

| 场景 | 优化前 | 优化后 |
|---|---|---|
| 用户重复问同一问题 | 2.7s | ~0ms（命中） |
| PlannerAgent 多轮检索同义子 query | N×2.7s | 一次 2.7s + (N-1)×0ms |
| 历史会话拉起后再次提问 | 2.7s | TTL 内命中 |
| 文档批量入库 | N×batch_call | 不变（批量透传，不缓存） |

**对 #5 review 的对照**：原 `ragSearchCache` 缓存的是 `query+userId+topK → docs`，三元组任一变动都 miss；本 cache 把 key 收窄到 query 本身，命中率显著更高，且适用所有 VectorStore.similaritySearch 路径（不止 RAG，HotSearch / DocumentSearch 等也受益）。

### 3.8 离线 RAG 评估（Round 3 新增）

**Why**：之前调 `similarity-threshold` / `score-threshold` / `rrf-k` / `top-n` 全靠人工 demo。任何 PR 都没量化指标，质量回归无感。

**Golden Set 设计**（`rag-golden-queries.jsonl`）：

```jsonl
{"query":"RAG 是什么","expectedSourceKeywords":["rag","retrieval","检索增强"],"note":"基础概念题"}
{"query":"hybrid 检索为什么用 RRF 融合","expectedSourceKeywords":["rrf","hybrid","融合"]}
{"query":"bge-m3 的向量维度是多少","expectedSourceKeywords":["bge-m3","1024","embedding"]}
```

- **judge 函数**：返回的某个 doc 的 `metadata.source` 或 `text` 包含任一 expected keyword 即视为命中
- 这样判断比"维护精确 doc_id 列表"成本低得多，且文档重导入后仍稳定

**指标**（`RetrievalEvaluatorTest.evaluate()`）：

| 指标 | 含义 |
|---|---|
| **hit@K** | top-K 内是否至少命中一条 |
| **MRR** | 第一条命中文档排名倒数的平均，对排名敏感 |
| **avg latency** | 单 query 检索耗时（含 embedding + 双路召回 + RRF + Rerank） |

**输出**直接打成 Markdown 表（贴 PR 即用）：

```text
## 📊 RAG 检索评估报告
样本数：5，topK：10，平均延迟：380ms

| metric | value |
|---|---|
| hit@1  | 60.00% |
| hit@5  | 100.00% |
| MRR    | 0.7833 |

### Per-query 详情
| query                | first-hit rank | returned | latency |
|----------------------|----------------|----------|---------|
| RAG 是什么            | 1              | 5        | 320ms   |
...
```

**默认关闭**：`@EnabledIfEnvironmentVariable(named="RAG_EVAL_ENABLED", matches="true")`，
避免普通 CI 跑测试时拉起 PG / ES / 远程 embedding 服务。

**运行**：

```powershell
# Windows PowerShell
$env:RAG_EVAL_ENABLED="true"
mvn -pl local-ai-knowledge -Dtest=RetrievalEvaluatorTest test
```

### 3.9 关键模块单测三件套（Round 3 新增）

**为什么先做这三个**（review #7 要求）：

| 模块 | 改一行就可能崩 | 协议变更影响范围 |
|---|---|---|
| `IntentRouter.fastMatch` | 关键词集合调一下顺序就影响路由 | 全部 chat 流量 |
| `ThinkBlockStripper.process` | 跨 chunk 状态机错一步就泄漏 `<think>` 给前端 | 所有 reasoning 模型回答 |
| `StreamErrorHandler.classify` | 错误码字符串改一个，前端展示错乱 | 所有错误兜底文案 |

**`IntentRouterTest`**：纯 JUnit + Mockito，无 Spring 上下文。

- `@ParameterizedTest + @CsvSource` 一次性验证 14 条快速匹配规则
- 关键断言：快速命中时 `verifyNoInteractions(chatModel)` —— 保证关键词路径绝不烧 LLM token
- LLM 兜底分支：mock `ChatModel.call()` 返回各种格式（标点、换行、未知 tag）验证 `tag` 解析容错
- 异常分支：`ChatModel` 抛出时 fallback 到 `KNOWLEDGE`
- ArgumentCaptor 验证 prompt 实际携带了用户问题

**`ThinkBlockStripperTest`**（同包访问 package-private 内部类）：

- 单 chunk 完整 think 块剥离
- **跨 chunk 切断 `<think>` 起始标签**（`"<thi" + "nk>...</think>real"`）
- **跨 chunk 切断 `</think>` 结束标签**（`"<think>...</thin" + "k>visible"`）
- **字符级 streaming**：把整段输入按字符拆分逐个 feed，断言最终输出仍正确
- 多个连续 think 块、未闭合 tail、纯 think 块、null/空安全
- 起始空白裁剪 + 首个非空白 token 后保留所有空白（保护段落分隔）

**`StreamErrorHandlerTest`**：

- 全部 9 个错误码各自有用例（timeout / 429 / 401 / 403 / content_filter / 4xx / 5xx / 网络 / 未知）
- `firstByteReceived` 布尔在不同分支的影响（in-flight 用"中断"短文案，未起步用"不可用"全文案）
- 每个错误码都断言 `render(...)` 非空非 blank（兜底文案不能漏）

**运行命令**（任选其一）：

```powershell
# IntelliJ：右键 test 类 → Run（最稳，使用项目 SDK）
# Maven CLI（需正确 JDK 21）：
mvn -pl local-ai-knowledge -Dtest='IntentRouterTest,ThinkBlockStripperTest,StreamErrorHandlerTest' test
```

### 3.10 Orchestrator 拆分为 4 个 Component（Round 4 新增）

**前情**：`MultiAgentOrchestrator` 在 Round 1~3 累计已 600+ 行，承担消息构建 / 路由 / 改写 / 流处理 / 错误兜底 / META 构造 / 持久化 / 后处理 8 类职责。**review #2** 直接打成 P1。

**拆法**（按"语义职责"切，不按行数硬切）：

| 抽出物 | 职责 | 进入条件（why 必须独立） |
|---|---|---|
| `FollowUpDetector` | 短追问识别 + 跨模式历史隔离 | 正则规则集中维护；`isSameMode` 同时被 `ChatMessageBuilder` 使用，循环引用消除 |
| `ChatMessageBuilder` | system prompt 合并 / 历史过滤 / token 裁剪 | 纯组装类无副作用，独立单测 |
| `MetaBuilder` | `[META]` map + references + JSON 序列化 | references 去重逻辑（按 source 名）独立后可被任意 Agent 复用 |
| `StreamErrorHandler`（Round 2 已抽） | 错误码分类 + 文案 | — |

**Orchestrator 保留瘦代理**：

```java
private List<Message> buildMessages(...) { return messageBuilder.build(...); }
private boolean isFollowUp(String q) { return followUpDetector.isFollowUp(q); }
```

不直接删旧调用点，避免一次性扩散改动；后续 PR 可逐步内联。**结果**：Orchestrator 主流程下降到 ~280 行，4 个 Component 各 80~130 行，职责单一可独立单测。

### 3.11 SimpleCircuitBreaker：80 行极简熔断（Round 4 新增）

**为什么不用 Resilience4j**：本项目 maven central 当前被代理 SSL 拦截（cert 返回 outlook 域），`io.github.resilience4j:*` 工件无法解析。Roadmap 上原本是 P1，必须就位。

**取舍**：用 `AtomicInteger` + `AtomicLong` + `AtomicReference<State>` 实现 CLOSED → OPEN → HALF_OPEN 三态机：

```
CLOSED  ──连续失败 ≥ 5 次──▶ OPEN
OPEN    ──冷却 30s 已过────▶ HALF_OPEN（CAS 抢占，仅一个线程探测）
HALF_OPEN ──探测成功──▶ CLOSED
          ──探测失败──▶ OPEN（再冷却 30s）
```

**接入位置**：`HybridSearchService.searchWithOwnership()` 中包一层：

```java
List<Document> result = searchBreaker.callOrFallback(
    () -> doSearchWithOwnership(query, userId, topK),
    List::of);   // ← 熔断时返回空列表，KnowledgeAgent 据此降级 LLM 直答
```

**对比 Resilience4j 的削减项**：滑动窗口失败率 / 慢调用计数 / Bulkhead；这些场景本项目暂不需要（ES + PG 单一上游，不涉及多租户隔离）。**等网络恢复后**只需把 `searchBreaker.callOrFallback(...)` 替成 `Decorators.ofSupplier(...).withCircuitBreaker(cb).withFallback(...)`，调用点改动 1 处即可平滑切换。

### 3.12 Micrometer + Prometheus（Round 4 新增）

**接入步骤**：

1. 添加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` 两个依赖
2. yml 暴露 `health,info,metrics,prometheus` 4 个 endpoint
3. `SecurityConfig` 放行 `/actuator/**`（生产环境改 IP 白名单）
4. 写 `RagMetrics` 注册自定义指标

**自定义指标清单**（全部走标准 Meter API，Grafana 可直接抓）：

| 指标 | 类型 | tags | 用途 |
|---|---|---|---|
| `rag.embed.cache.hit_rate` | Gauge | — | bge-m3 远程 embedding 缓存命中率 |
| `rag.embed.cache.{requests,hits,misses,evictions}` | Gauge | — | 详细计数，对应 `CacheStats` |
| `rag.search.breaker.state` | Gauge | — | 0=CLOSED / 1=HALF_OPEN / 2=OPEN，Grafana 阶梯图 |
| `rag.search.breaker.{total,rejected,failed}` | Gauge | — | 检索请求 / 熔断拒绝 / 实际失败计数 |
| `rag.chat.error.total` | Counter | `code` | StreamErrorHandler 错误码分布（timeout_first_byte / rate_limit / network ...） |
| `rag.chat.duration` | Timer（含 p50/p90/p99） | `model` | per-model chat 耗时；切换 GLM ↔ DeepSeek 直接对比 |

**埋点位置**：

- `MultiAgentOrchestrator.chatStream(...).doFinally(sig -> ragMetrics.recordChatDuration(...))` —— 不论成功/失败/取消都记
- `StreamErrorHandler.classify(...)` —— 拆 public + private，`classify` 调完私有逻辑后追加 `ragMetrics.incrementErrorCode(code)`
- gauge 全部用 lambda 引用源对象（`registry.gauge(name, src, fn)`），不需要主动 push

**spring-mvc 自带指标**也免费拿到了：`http.server.requests{uri,method,status}` —— 可直接画各 controller 的 QPS / 错误率。

### 3.13 Planner 多工具编排（Round 4 新增）

**before**：Planner 只能调 `search_kb` 一个工具，多次循环也都在同一来源里打转，"编排感"较弱。

**after**：system prompt 里 action 枚举扩到 `search_kb | web_search | get_hot_list | finish`，由 LLM 自主决定：

| 用户问题 | LLM 期望选择 |
|---|---|
| "RAG 是什么？" | `search_kb`（知识库有专题） |
| "今年 GPT-5 发布了吗？" | `web_search`（时效性） |
| "今天微博热搜前三" | `get_hot_list`（实时榜单） |
| "我们公司的 RAG 系统跟今年 OpenAI 新功能比怎样" | 先 `search_kb` 再 `web_search`（需要混合编排） |

**统一上限 3 次**：`search_kb + web_search + get_hot_list` 累计计数，避免 LLM 把工具当万金油打到 token 爆掉。

**结果折叠**：`formatWebSearchResults(query, results)` 只取前 5 条，每条 `title + content（截 400 字）+ url`，避免长 URL 撑爆 LLM 上下文窗口。

---

## 四、配置示例

`application.yml`：

```yaml
app:
  chat-models:
    default-key: glm
    providers:
      glm:
        base-url: https://open.bigmodel.cn/api/paas
        api-key: ${ZHIPU_API_KEY:}
        completions-path: /v4/chat/completions
        model: glm-4-flash
        temperature: 0.3
        max-tokens: 2048
      deepseek:
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-chat
        temperature: 0.3
        max-tokens: 2048
  # ===== Round 3 新增：query embedding 缓存 =====
  embedding:
    cache:
      enabled: true       # false 即关闭装饰器，回退到原始 OpenAiEmbeddingModel
      max-size: 2000      # 约 8MB（bge-m3 1024 维 × 4B × 2000）
      ttl-minutes: 10     # 与 ragSearchCache 对齐
```

> provider 的 `api-key` 留空会被 `ChatModelRegistry` 跳过，日志输出 `[ChatModelRegistry] 跳过 provider [xxx]`。
> ⚠️ **本仓库 Round 1 提交时 yml 默认值里写了真实 key（已暴露）**，按 review 建议必须吊销轮换并改为空 fallback。

---

## 五、API 变化

### 1. `POST /api/rag/chat/stream`（向后兼容）

新增可选字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `model` | string | `glm` / `deepseek` / `default` / ...；缺省即用 `default-key` |

示例请求：

```json
{
  "question": "规划一下：先检索知识库里关于 RAG 的内容，再总结核心差异",
  "sessionId": "xxx",
  "chatMode": "KNOWLEDGE",
  "model": "deepseek",
  "thinking": false
}
```

触发关键词 "规划一下 / 先检索" → 路由到 `PlannerAgent`，SSE 流前端能解析出：

```
[STEP]{"stage":"route","intent":"PLANNER","mode":"KNOWLEDGE","model":"deepseek"}[/STEP]
[STEP]{"stage":"generate","intent":"PLANNER"}[/STEP]
[STEP]{"stage":"think","iter":1}[/STEP]
[STEP]{"stage":"act","iter":1,"action":"search_kb","thought":"先查 RAG 定义"}[/STEP]
[STEP]{"stage":"tool","iter":1,"name":"searchKnowledgeBase","query":"RAG 定义","phase":"start"}[/STEP]
[STEP]{"stage":"tool","iter":1,"name":"searchKnowledgeBase","phase":"end","hits":5,"costMs":340}[/STEP]
[STEP]{"stage":"observe","iter":1,"hits":5,"chars":1234}[/STEP]
[STEP]{"stage":"think","iter":2}[/STEP]
[STEP]{"stage":"act","iter":2,"action":"finish","thought":"信息够了"}[/STEP]
[STEP]{"stage":"generate","iter":2}[/STEP]
RAG 是 Retrieval-Augmented Generation ...（真流式 token）
[STEP]{"stage":"done","iter":2,"searchCalls":1}[/STEP]
[META]{"source":"planner","agent":"PLANNER",...}[/META]
```

### 2. `GET /api/rag/models`（新增）

```json
{ "models": ["default", "glm", "deepseek"] }
```

供前端模型下拉使用。

---

## 六、面试话术对齐

| 面试亮点 | 本项目对应实现 | 关键代码 |
|---|---|---|
| **Think-Execute 循环（自主决策）** | `PlannerAgent` ReAct 4 轮 Plan-Act-Observe；finish 后真流式生成自然语言答案 | `PlannerAgent#runReactLoop` + `streamFinalize` |
| **工具调用框架（可扩展）** | `SpecializedAgent` 接口 + `agentRegistry: Map<AgentType, SpecializedAgent>`；`@Tool` 注解扩展工具 | `MultiAgentOrchestrator` 构造器自动注册 |
| **RAG + 向量检索（pgvector）** | pgvector HNSW + ES IK 双 VectorStore；Hybrid（向量+BM25+RRF）+ Rerank(bge-reranker-v2-m3) + Query Rewrite | `VectorStoreConfig` / `HybridSearchService` |
| **多模型切换架构（注册表模式）** | `ChatModelRegistry` 启动时批量构建多 provider；运行时前端传 `model=xxx` 即时切换；未命中 fallback `default` | `ChatModelRegistry` / `ChatModelProperties` |
| **SSE 实时推送（执行状态可视化）** | token 流外独立 `Sinks.Many<String> stepSink`，经 `Flux.merge` 交织下发 `[STEP]` 全阶段事件；前端 Vue 跨 chunk 解析为时间线 UI | `RagToolContext.stepSink` / `MultiAgentOrchestrator#tokenFlux` / `RagChat.vue#extractMarkers` |
| **工程化**（彩蛋） | `StreamErrorHandler` 把错误分类下沉成独立 Component，便于复用与单测 | `StreamErrorHandler.java` |

---

## 七、验证

### 编译

```text
local-ai-knowledge ... BUILD SUCCESS（仅历史 deprecation 警告）
local-ai-knowledge-front ... 0 lint error
```

### 冒烟测试（需本地启动后执行）

```bash
# 1. 模型列表
curl -H "Authorization: Bearer $TOKEN" http://localhost:12116/api/rag/models
# => { "code":0, "data":{ "models":["default","glm","deepseek"] } }

# 2. 指定模型直答
curl -N -X POST http://localhost:12116/api/rag/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"介绍一下你","model":"deepseek","chatMode":"LLM"}'
# 应看到 [STEP]{"stage":"route",...,"model":"deepseek"}[/STEP] 先行

# 3. PlannerAgent 触发（真流式 finalize）
curl -N -X POST http://localhost:12116/api/rag/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"规划一下，帮我先检索再总结知识库里有关 RAG 的内容"}'
# 期望：think/act/tool/observe 多阶段事件 → generate(iter=N) →
#       真流式 token 逐字下发 → done

# 4. 历史持久化干净
curl http://localhost:12116/api/rag/history/{sessionId}
# assistant content 里不应包含 [STEP]...[/STEP] 残留
```

### 前端冒烟

启动前端 `pnpm dev` 后：
- 任意 PlannerAgent 触发的对话 → 答案下方应看到"执行步骤 · N 步"折叠面板
- 点击展开 → 彩色圆点时间线 + 中文阶段名 + 一行细节
- 普通 KnowledgeAgent 对话也应能看到 route / rewrite / tool / done 4~5 步

### 已知边界

- 前端跨 chunk 缓冲基于 `[` 字符切割，理论上若答案正文里出现 `[` 会被保留到下次 chunk 才显示（延迟 ≤ 1 chunk），不影响最终显示。
- `PlannerAgent.streamFinalize` 在 `boundedElastic` 上 `latch.await()`，OK；不要在主线程或 reactor parallel 调度上调它。
- `ChatModelRegistry` 构造 `OpenAiChatModel` 失败时仅 WARN + skip，不阻塞启动。

---

## 八、后续 Roadmap

按 review 优先级，本文档三轮覆盖到 #3 / #4 / #5 / #6 / #7 / #8。仍待做：

1. **🔴 P0 安全**：吊销 + 轮换 yml 里的明文 key；DB 密码 / JWT secret / CORS 全部走环境变量
2. ~~**🟠 P1 架构**：继续拆 `MultiAgentOrchestrator`（`ChatMessageBuilder` / `MetaBuilder` / `FollowUpDetector`）~~ ✅ Round 4 已完成
3. ~~**🟠 P1 稳定**：`HybridSearchService` 接 Resilience4j 超时熔断~~ ✅ Round 4 已完成（用 `SimpleCircuitBreaker` 替代，原因见下）
4. ~~**🟠 P1 可观测**：引入 Micrometer + actuator/prometheus，per-model QPS/p95；`CachedEmbeddingModel#stats()` 已经 ready，缺一个 metrics endpoint~~ ✅ Round 4 已完成（`RagMetrics` + `/actuator/prometheus`）
5. ~~**🟡 P2 性能**：query embedding 加 Caffeine 缓存~~ ✅ Round 3 已完成（`CachedEmbeddingModel`）
6. ~~**🟢 P3 RAG**：写离线评估脚本（hit@k / MRR）~~ ✅ Round 3 已完成（`RetrievalEvaluatorTest` + golden jsonl）
7. ~~**🔵 P4 测试**：补 `IntentRouter` / `ThinkBlockStripper` / SSE 协议契约测试~~ ✅ Round 3 已完成（前两个），SSE 端到端契约测试仍可后补
8. ~~**🟣 P5 包装**：Planner 加 `web_search` / `get_hot_list` 两个 action，编排感更强；`StreamErrorHandler` 命中率 / 各错误码计数加到 metrics~~ ✅ Round 4 已完成

**Round 4 遗留 / 后续可选项**：
- 网络恢复后可把 `SimpleCircuitBreaker` 平滑切 Resilience4j（保留滑动窗口失败率 + 慢调用统计 + bulkhead 隔离）
- Grafana Dashboard JSON 模板（embed-cache 命中率 / breaker state 阶梯图 / per-model p95 折线 / 错误码 top-N）
- SSE 端到端契约测试（用 `WebTestClient` 断言 `[STEP]` / `[META]` 顺序）
- 扩 golden 集 → 30+ query 跑回归，卡 hit@5 ≥ 80% / MRR ≥ 0.7 作为 CI 阈值
