# Spring AI vs LangChain4j 核心区别

> 版本基准：Spring AI 2.0-M4 / LangChain4j 1.14.1，均运行于 Spring Boot 4 + JDK 21。

---

## 一、设计哲学

| | Spring AI | LangChain4j |
|--|-----------|-------------|
| **定位** | Spring 生态的 AI 能力扩展（"AI 就是一种 Spring Bean"） | 独立的 Java AI 应用开发框架（"不绑定任何容器"） |
| **集成思路** | Starter + 自动配置 + BOM 统一版本，约定大于配置 | 纯库形式引入，手动构建一切，可脱离 Spring 使用 |
| **抽象层级** | 高层封装：`ChatClient` 一行搞定 prompt → stream → tool call | 中层抽象：给你接口和 Builder，组装逻辑你自己写 |
| **版本节奏** | 跟随 Spring Boot 大版本，发布周期较慢 | 社区驱动，迭代极快（1~2 周一个小版本） |

---

## 二、模型接口设计

### 2.1 同步 vs 流式

**Spring AI**：一个 `ChatModel` 接口统管同步和流式，通过 `ChatClient` 暴露两种调用方式：

```java
// 同步
String result = chatClient.prompt().messages(msgs).call().content();

// 流式
Flux<String> stream = chatClient.prompt().messages(msgs).stream().content();
```

**LangChain4j**：同步和流式是**两个独立接口**，必须分别创建实例：

```java
// 同步 —— ChatModel
ChatModel chatModel = OpenAiChatModel.builder().apiKey(key)...build();
ChatResponse response = chatModel.chat(request);

// 流式 —— StreamingChatModel（独立接口！）
StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder().apiKey(key)...build();
streamingModel.chat(request, handler);  // 回调式
```

> 实际影响：如果你要支持运行时切模型，Spring AI 只需维护一份 `Map<String, ChatClient>`，LangChain4j 需要维护**两份** Map（sync + streaming）。

### 2.2 模型构建

```java
// Spring AI —— 走 OpenAiApi 中间对象，可精细控制 HTTP 层
OpenAiApi api = OpenAiApi.builder()
    .baseUrl(url).apiKey(key)
    .restClientBuilder(customRestClient)  // 可替换底层 HTTP 实现
    .completionsPath("/custom/path")      // 支持非标路径
    .build();
ChatModel model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).build();

// LangChain4j —— 扁平化 Builder，更简洁直白
ChatModel model = OpenAiChatModel.builder()
    .apiKey(key).baseUrl(url).modelName(name)
    .temperature(0.7).maxTokens(2048)
    .build();
```

---

## 三、流式输出机制（最大工程差异）

### Spring AI：原生 Reactive

```java
chatClient.prompt().messages(msgs).stream().content()  // → Flux<String>
```

- 返回值是标准的 `Flux<String>`，可直接对接 WebFlux SSE、`@GetMapping(produces = TEXT_EVENT_STREAM_VALUE)`
- 内部基于 Reactor 实现，支持 backpressure、timeout、onErrorResume 等全套操作符

### LangChain4j：回调式 Handler

```java
streamingModel.chat(chatRequest, new StreamingChatResponseHandler() {
    @Override public void onPartialResponse(String token) { /* 每个 token 回调 */ }
    @Override public void onCompleteResponse(ChatResponse resp) { /* 结束 */ }
    @Override public void onError(Throwable error) { /* 异常 */ }
});
```

- 返回值是 `void`，不是响应式类型
- 要对接 WebFlux/SSE，必须自己写 **Callback → Flux 桥接器**：

```java
public static Flux<String> toFlux(Consumer<StreamingChatResponseHandler> trigger) {
    return Flux.create(sink -> {
        trigger.accept(new StreamingChatResponseHandler() {
            public void onPartialResponse(String t) { sink.next(t); }
            public void onCompleteResponse(ChatResponse r) { sink.complete(); }
            public void onError(Throwable e) { sink.error(e); }
        });
    });
}
```

> **结论**：在 Spring WebFlux 体系下，Spring AI 零适配；LangChain4j 需要额外 ~80 行桥接代码。

---

## 四、消息类型命名

| 语义 | Spring AI 类名 | LangChain4j 类名 | 包路径 |
|------|---------------|-----------------|--------|
| 系统消息 | `SystemMessage` | `SystemMessage` | `dev.langchain4j.data.message` |
| 用户消息 | `UserMessage` | `UserMessage` | 同上 |
| **AI 回复** | **`AssistantMessage`** | **`AiMessage`** | 同上 |
| 工具消息 | `ToolResponseMessage` | `ToolExecutionResultMessage` | 同上 |
| 父接口 | `Message` | `ChatMessage` | 同上 |

**最易混淆点**：Spring AI 叫 `AssistantMessage`，LangChain4j 叫 `AiMessage`。构建历史记录时写错就会编译报错。

构造方式也不同：
```java
// Spring AI
new SystemMessage("你是助手");
new UserMessage("你好");
new AssistantMessage("你好！");

// LangChain4j
SystemMessage.from("你是助手");
UserMessage.from("你好");
AiMessage.from("你好！");
```

---

## 五、Tool Calling（核心设计分歧）

### 5.1 工具声明

```java
// ===== Spring AI =====
@Tool(name = "search", description = "检索知识库")
public String search(
    @ToolParam(description = "关键词") String query,
    ToolContext toolCtx) { ... }                       // 框架注入上下文

// ===== LangChain4j =====
@Tool(name = "search", value = {"检索知识库"})          // description 换成 value（String[]）
public String search(
    @P("关键词") String query) { ... }                  // @P 替代 @ToolParam，无法注入上下文
```

### 5.2 上下文传递

| | Spring AI | LangChain4j |
|--|-----------|-------------|
| **机制** | `ToolContext` — 框架在调用工具时自动注入的 Map 容器 | 无原生支持，需自行用 **ThreadLocal** |
| **注入方式** | 工具方法参数自动注入 | `ThreadLocal.get()` 手动取 |
| **生命周期** | 框架管理，请求结束自动释放 | 手动 `set()` / `remove()`，忘了就泄露 |
| **并发安全** | 天然安全（per-request） | ThreadLocal 在线程池场景下容易串请求 |

### 5.3 工具执行流程

**Spring AI** —— **框架自动完成 Tool Call 循环**：
```java
chatClient.prompt()
    .messages(msgs)
    .tools(myTool)                         // 1. 声明工具
    .toolContext(Map.of("ctx", myCtx))     // 2. 注入上下文
    .stream().content();                   // 3. 框架自动：检测 tool_call → 执行 → 注入结果 → 继续生成
```
LLM 如果决定调工具，Spring AI 的 `ChatClient` 会自动拦截 tool_call 信号、执行对应方法、把结果塞回消息列表、继续流式生成——**整个循环开发者无感**。

**LangChain4j** —— **流式模式下无法自动执行工具**：
```java
// 1. 同步模式下可以自动 tool call（通过 AiServices）
interface Assistant {
    @SystemMessage("你是助手")
    String chat(String msg);
}
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .tools(myTool)
    .build();
// 但这是同步阻塞式，不返回流

// 2. 流式模式下必须手动拆分步骤
String toolResult = myTool.search(query);
messages.add(UserMessage.from("参考资料:\n" + toolResult));
streamingModel.chat(request, handler);    // 手动拼好再生成
```

> **核心结论**：Spring AI 做到了 "流式 + 工具自动执行" 一体化；LangChain4j 的流式模式不支持自动 tool call 循环，需要开发者手动编排。

---

## 六、Embedding 接口

```java
// ===== Spring AI =====
EmbeddingRequest req = new EmbeddingRequest(List.of("文本"), null);
float[] vec = embeddingModel.call(req).getResult().getOutput();

// 批量：直接传 List<String>
EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(texts, null));
List<float[]> vecs = resp.getResults().stream().map(Embedding::getOutput).toList();


// ===== LangChain4j =====
float[] vec = embeddingModel.embed("文本").content().vector();

// 批量：必须包装成 TextSegment
List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
List<Embedding> embs = embeddingModel.embedAll(segments).content();
List<float[]> vecs = embs.stream().map(Embedding::vector).toList();
```

| | Spring AI | LangChain4j |
|--|-----------|-------------|
| 单文本返回 | `float[]`（一步到位） | `Response<Embedding>`（需 `.content().vector()` 解包） |
| 批量入参 | `List<String>` | `List<TextSegment>`（需要包装） |
| 批量策略 | 框架内置 `TokenCountBatchingStrategy` 自动分批 | 需自行实现分批逻辑 |

---

## 七、向量存储

### 设计理念差异

**Spring AI `VectorStore`** —— **高层抽象，embed + store 一体化**：
```java
vectorStore.add(List<Document> docs);                    // 内部自动调 EmbeddingModel 向量化
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.query("关键词").withTopK(5));           // 内部自动 embed query
```
- `VectorStore` = EmbeddingModel + 存储引擎 的组合体
- 开发者不需要手动管理 embedding 过程

**LangChain4j `EmbeddingStore`** —— **纯存储层，关注点分离**：
```java
Embedding emb = embeddingModel.embed(segment).content();  // 1. 先手动 embed
embeddingStore.add(emb, segment);                          // 2. 再手动存

Embedding queryEmb = embeddingModel.embed("关键词").content();  // 3. 手动 embed query
List<EmbeddingMatch<TextSegment>> matches =
    embeddingStore.search(EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmb).maxResults(5).build());      // 4. 手动搜索
```
- `EmbeddingStore` 只管存和搜
- embedding 过程完全由外部控制

| | Spring AI | LangChain4j |
|--|-----------|-------------|
| **add()** | 自动 embed → 存入 | 需先 embed，再传入向量 |
| **search()** | 自动 embed query → 搜索 | 需先 embed query，再搜索 |
| **自动建索引** | `initializeSchema(true)` | 需手动或依赖存储默认 |
| **适合场景** | 快速集成，不关心底层 | 需要控制 embed 细节（缓存、批处理、异步） |

---

## 八、配置方式

**Spring AI** — 走 Spring Boot 约定命名空间：
```yaml
spring:
  ai:
    openai:
      api-key: ${KEY}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-4o
          temperature: 0.7
    vectorstore:
      elasticsearch:
        index-name: my_index
        dimensions: 1024
```
- 一个 `spring-ai-starter-model-openai` 就把 `ChatModel` + `EmbeddingModel` 全自动配置好
- 切换 provider 改 starter 依赖即可（或用 profile）

**LangChain4j** — 自定义配置 + 手动构建 Bean：
```yaml
app:                          # 自定义前缀，框架不管你叫什么
  llm:
    api-key: ${KEY}
    base-url: https://api.openai.com
    model-name: gpt-4o
  embedding:
    api-key: ${KEY}
    model-name: text-embedding-3-small
```
```java
@Bean
public ChatModel chatModel(@Value("${app.llm.api-key}") String key, ...) {
    return OpenAiChatModel.builder().apiKey(key)...build();  // 手动 new
}
```
- 虽然有 `langchain4j-spring-boot-starter`，但功能远不如 Spring AI 的自动配置全面
- 大部分场景仍需手动注册 Bean

---

## 九、Spring 生态集成深度

| 能力 | Spring AI | LangChain4j |
|------|-----------|-------------|
| **MCP Server** | `spring-ai-starter-mcp-server-webmvc` 一键暴露工具为 MCP 协议 | 无原生支持 |
| **Actuator 指标** | 自动暴露 token 用量、延迟等 metrics | 需手动埋点 Micrometer |
| **Spring Security** | `ToolContext` 可直接拿 SecurityContext 中的 userId | 需 ThreadLocal / 手动传参 |
| **Testcontainers** | 官方提供向量存储的 Testcontainers 集成 | 需自行配置 |
| **Observability** | 原生支持 OpenTelemetry tracing | 需自行接入 |

---

## 十、总结

| 维度 | Spring AI 优势 | LangChain4j 优势 |
|------|---------------|-----------------|
| **流式 + 工具自动执行** | ✅ 一体化，零适配 | ❌ 需手动桥接 + 手动编排 |
| **开箱即用** | ✅ Starter 自动配置 | ❌ 手动构建 |
| **上下文传递** | ✅ ToolContext 框架管理 | ❌ ThreadLocal 手动管理 |
| **向量存储** | ✅ embed + store 一体化 | ❌ 两步手动操作 |
| **与 Spring 生态集成** | ✅ 深度融合 | ❌ 仅作为普通 Bean |
| **版本自由度** | ❌ 受 Spring AI BOM 锁定 | ✅ 任意版本组合 |
| **Provider 更新速度** | ❌ 等官方发布 | ✅ 社区响应快 |
| **脱离 Spring 使用** | ❌ 强依赖 Spring Boot | ✅ 纯 Java 即可运行 |
| **底层可控性** | ❌ 被抽象层遮挡 | ✅ 直接操作原始 API |
| **学习成本** | 需先理解 Spring AI 抽象 | API 直白，看一眼就会用 |

### 一句话选型

- **Spring AI**：你已在 Spring 生态，追求"写最少代码做最多事"，接受框架替你决定细节
- **LangChain4j**：你需要完全控制 AI 调用的每个环节，或者项目不在 Spring 体系内
