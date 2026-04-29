# Day29 Spring AI 1.0.0-M5 → 2.0.0-M4 升级 + 代码质量整改

> 本次工作覆盖 `local-ai-knowledge` 模块。包含框架升级、API 适配、4 个 P0 bug 修复、3 个 P1 性能优化，以及若干清理。

## 起因

Spring AI 从 1.0.0-M5 跳到 2.0.0-M4 时同步要求 Spring Boot 4，starter 命名 + 多个核心 API（Tool Calling / Embedding Options / VectorStore Builder）有破坏性变更。借此机会做一次完整代码审查。

---

## 一、依赖与配置升级

### `pom.xml`

```xml
<spring-ai.version>2.0.0-M4</spring-ai.version>
```

starter 全部改名：

| 旧 | 新 |
| --- | --- |
| `spring-ai-minimax-spring-boot-starter` | `spring-ai-starter-model-minimax` |
| `spring-ai-ollama-spring-boot-starter` | `spring-ai-starter-model-ollama` |
| `spring-ai-pgvector-store-spring-boot-starter` | `spring-ai-starter-vector-store-pgvector` |
| `spring-ai-elasticsearch-store-spring-boot-starter` | `spring-ai-starter-vector-store-elasticsearch` |
| `spring-ai-core` | `spring-ai-model` |

### yml 配置切换

旧：靠 `spring.autoconfigure.exclude` 排除不用的 starter 来选模型。
新：用 `spring.ai.model.{chat,embedding}` 选择器，starter 自动只创建对应的 `ChatModel` / `EmbeddingModel`。

```yaml
# application-com.yml（公司 / MiniMax）
spring:
  ai:
    model:
      chat: minimax
      embedding: none           # embedding 自定义 bean 接管
    minimax:
      api-key: ${MINIMAX_API_KEY}
      chat:
        options:
          model: MiniMax-M2.7
    ollama:
      base-url: http://10.56.60.249:11434
      embedding:
        options:
          model: bge-m3
```

```yaml
# application-local.yml（家用 / 全 Ollama）
spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
    ollama:
      base-url: http://localhost:11434
```

---

## 二、Tool Calling API 改造（Spring AI 2.x）

### 旧版（M5）

```java
public List<FunctionCallback> callbacks() {
    return List.of(
        FunctionCallback.builder()
            .function("searchKnowledgeBase", request -> { ... })
            .description("...")
            .inputType(KbRequest.class)
            .build());
}
```

工具上下文用 `ThreadLocal` 传 userId，流式场景下线程切换易丢。

### 新版（2.x）

```java
@Component
public class RagTools {
    public static final String CTX_KEY = "ragCtx";

    @Tool(name = "searchKnowledgeBase",
          description = "从企业私域知识库检索相关内容片段。优先调用。")
    public String searchKnowledgeBase(
            @ToolParam(description = "用户问题的检索关键词") String query,
            ToolContext toolCtx) {
        RagToolContext ctx = (RagToolContext) toolCtx.getContext().get(CTX_KEY);
        ctx.recordInvocation(TOOL_KB);
        ...
    }
}
```

调用方：

```java
final RagToolContext ctx = RagToolContext.create(userId);
spec = chatClient.prompt()
    .messages(messages)
    .tools(ragTools)
    .toolContext(Map.of(RagTools.CTX_KEY, ctx));
```

**好处**：
- `ToolContext` 是框架原生跨线程容器，不依赖 ThreadLocal，流式 / 并发都安全
- 删除 `FunctionCallback` 胶水代码、入参 DTO、Jackson schema 转换约 80 行

---

## 三、配置类适配

### `EmbeddingModelConfig`（关键 bug 修复）

```java
return OllamaEmbeddingModel.builder()
    .ollamaApi(api)
    // 关键：显式设置请求时使用的模型名，
    // 否则 SDK fallback 默认值（mxbai-embed-large/768维），
    // 与 ES 索引配置的 1024 维（bge-m3）不匹配会导致写入/检索失败。
    .defaultOptions(OllamaEmbeddingOptions.builder().model(embeddingModel).build())
    .build();
```

要点：
- `OllamaOptions` 在 2.x 拆成 `OllamaChatOptions` / `OllamaEmbeddingOptions`
- `OllamaApi.builder()` 不再传 `webClientBuilder`（embedding 同步路径用不上）
- `additionalModels` 预拉模型与 `pullModelStrategy=WHEN_MISSING` 重复，删除
- `RestClient + JdkClientHttpRequestFactory`：避开 Reactor Netty，防止流式 Tool Calling 回调线程触发 BlockHound

### `VectorStoreConfig`

```java
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;

@Bean
public Rest5Client elasticsearchRestClient(
        @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
    String[] hosts = uris.split(",");
    HttpHost[] httpHosts = new HttpHost[hosts.length];
    for (int i = 0; i < hosts.length; i++) {
        java.net.URI uri = java.net.URI.create(hosts[i].trim());
        httpHosts[i] = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }
    return Rest5Client.builder(httpHosts).build();
}
```

`elasticsearch-java` 9.x 改用基于 Apache HttpClient 5 的 `Rest5Client`。

### `ChatClientConfig`（简化）

```java
@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
```

之前用 `@ConditionalOnProperty(name = "app.llm.provider")` 维护两套 ChatClient，与 `spring.ai.model.chat` 选择器重复。新版只剩单 Bean，`ChatModel` 由框架自动注入。

---

## 四、4 个 P0 Bug 修复

### Bug 1：`HybridSearchService` PG VectorStore 注入错配

```java
// ❌ 之前
private final VectorStore pgVectorStore;
// 字段名 pgVectorStore ≠ Spring AI 自动 bean 名 vectorStore，
// 加上 ES 是 @Primary → 实际注入 ES，PG 降级路径形同虚设

// ✅ 现在
@Qualifier("vectorStore")
private final VectorStore pgVectorStore;
```

要让 `@Qualifier` 在 `@RequiredArgsConstructor` 生成的构造器参数上生效，新增 `lombok.config`：

```properties
config.stopBubbling = true
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value
```

### Bug 2：`ChatHistoryCacheService.@Async` 双重失效

```java
// 问题1：自调用绕过 Spring AOP 代理
public void saveMessage(ChatMessage message) {
    persistToDb(message);   // this.persistToDb，@Async 失效
}

@Async
public void persistToDb(ChatMessage message) { ... }
```

```java
// 问题2：项目根本没启用 @EnableAsync
@Configuration
@EnableScheduling           // 只启用了 @Scheduled
public class AsyncConfig {}
```

修复：移除 `@Async`，DB 写改为同步。两个调用点都已在可阻塞线程：
- 同步 `chat()` 走请求线程
- 流式 `chatStream` 的回调通过 `subscribeOn(boundedElastic())` 已在 boundedElastic 上

### Bug 3：`RagController` 越权访问

`history` / `deleteSession` / `renameSession` 三个端点没校验 sessionId 归属，**任意用户可看 / 删 / 改他人会话**。

```java
// 新增鉴权 SQL
@Select("""
    SELECT EXISTS (SELECT 1 FROM chat_conversation
                   WHERE session_id = #{sessionId} AND user_id = #{userId} LIMIT 1)
    """)
boolean existsBySessionAndUserId(@Param("sessionId") String sessionId,
                                 @Param("userId") String userId);
```

```java
private void requireSessionOwnership(String sessionId) {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (userId == null) throw new SecurityException("未登录");
    if (!conversationMapper.existsBySessionAndUserId(sessionId, userId))
        throw new SecurityException("无权访问该会话");
}
```

`GlobalExceptionHandler` 加 `SecurityException → 403` handler。

### Bug 4：`DocumentParseService` 假事务

```java
// ❌ 之前
@Transactional(rollbackFor = Exception.class)
public void parseAndImport(String taskId) {
    try {
        ... // ES HTTP 调用、PG 操作、Tika 解析
    } catch (Exception e) {
        failTask(task, e.getMessage());   // 异常被吞 → 永不回滚
    }
}
```

问题叠加：
- 异常被外层 try-catch 吞没，`@Transactional` 永远不会触发回滚
- 事务里夹杂 ES 这种非事务参与方，PG 回滚也无法回滚 ES
- Tika 解析很慢，长事务持续占用 PG 连接

修复：移除 `@Transactional`，改各阶段独立 try-catch + 任务状态机兜底，更如实反映运行行为。

---

## 五、3 个 P1 性能优化

### 优化 1：`RagController.sessions()` N+1 query

```java
// ❌ 之前：每个 session 触发 2 次额外 SQL（firstQuestion + createdAt）
List<String> sessionIds = conversationMapper.selectByUserId(userId);
sessionIds.stream().map(id -> {
    conversationMapper.selectFirstQuestion(id);  // 1
    conversationMapper.selectCreatedAt(id);      // 2
    ...
});
```

```java
// ✅ 现在：一次聚合 SQL
@Select("""
    SELECT
      c.session_id            AS sessionId,
      (SELECT content FROM chat_conversation
          WHERE session_id = c.session_id AND role = 'user'
          ORDER BY created_at ASC LIMIT 1) AS firstQuestion,
      (EXTRACT(EPOCH FROM MIN(c.created_at)) * 1000)::bigint AS createdAt
    FROM chat_conversation c
    WHERE c.user_id = #{userId}
    GROUP BY c.session_id
    ORDER BY createdAt DESC
    """)
List<ChatSession> selectSessionListByUserId(@Param("userId") String userId);
```

100 个会话从 **201 次 SQL** 降到 **1 次**。

### 优化 2：`DocumentParseService` 切片复用

之前同一份文本被切 3 次：ES 入库内部切一次、`saveChunksToPg` 切一次、`saveVectorToPg` 切一次。

```java
// ✅ 切 1 次，三处复用
String clean = TextCleanUtil.clean(rawText);
List<String> chunks = TextSplitterUtil.splitText(clean);

esVectorStoreService.importChunks(chunks, source, userId, docScope);
saveChunksToPg(taskId, chunks, source, userId, docScope);
saveVectorToPg(chunks, source, userId, docScope);
```

`EsVectorStoreService` 新增公共入口 `importChunks(List<String> chunks, ...)` 接受预切片。

### 优化 3：`WebSearchService` 避开 Reactor Netty

```java
// ❌ 之前：Reactor Netty + .block()
private static final WebClient WEB_CLIENT = WebClient.builder()...build();
Map<String, Object> response = WEB_CLIENT.post()...bodyToMono(Map.class).block();
```

如果 LLM 在流式 chat 中调用 `searchWeb` 工具，`block()` 在 Netty event loop 上会触发 BlockHound 异常。

```java
// ✅ 现在：JDK HttpClient + RestClient（同步阻塞 IO，无线程亲和限制）
private static final RestClient REST_CLIENT =
    RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).build();
Map<String, Object> response = REST_CLIENT.post()...retrieve().body(Map.class);
```

跟 `EmbeddingModelConfig` 一样的套路。

---

## 六、流式问答会话历史断裂修复

`RagAgentService.chatStream` 之前用 `doOnComplete` 持久化 assistant 消息，**流式异常时不触发**，会话历史变成"用户问完没回答"。

```java
// ✅ 改用 doFinally 覆盖 complete / error / cancel 三种情况
return spec.stream()
    .content()
    .timeout(Duration.ofSeconds(LLM_TIMEOUT_SECONDS))
    .doOnNext(fullAnswer::append)
    .concatWith(Flux.defer(() -> Flux.just("[META]" + toJson(meta) + "[/META]")))
    .onErrorResume(e -> {
        log.error("Agent 流式异常: {}", e.getMessage(), e);
        String fallback = "抱歉，AI 服务暂时不可用，请稍后重试。";
        fullAnswer.append(fallback);   // 兜底文本也要 append，doFinally 才能持久化
        return Flux.just(fallback);
    })
    .doFinally(sig -> {
        if (sid != null && fullAnswer.length() > 0) {
            chatHistoryCache.saveMessage(
                ChatMessage.of(sid, "assistant", fullAnswer.toString(), meta, userId));
        }
    })
    .subscribeOn(Schedulers.boundedElastic());
```

| 场景 | 之前 | 现在 |
| --- | --- | --- |
| 流正常完成 | 保存正文 | 保存正文 |
| 流抛异常 | assistant **丢失** | 保存兜底文本 |
| 客户端取消 | 不保存 | 保存当前已生成内容 |

---

## 七、其他清理

### 死代码

- `RagToolContext` 的 `HOLDER` ThreadLocal + `begin()` / `current()` / `clear()`（迁移到 `ToolContext` 后无人调用，30 行）
- `HotSearchService.isHotQuery()` / `containsStrongHotPattern()` / `HOT_KEYWORDS`（旧版关键词路由的痕迹，工具调用模式下完全交给 LLM，~50 行）
- `VectorStoreConfig.objectMapper()` Bean（Spring Boot 4 的 `JacksonAutoConfiguration` 已自动配置）

### Jackson API 升级

```java
// ❌ JsonNode.fields() Iterator 在 Jackson 2.18 标记 deprecated
Iterator<Map.Entry<String, JsonNode>> it = node.fields();

// ✅ 改用 .properties() 返回 Set<Entry>
for (Map.Entry<String, JsonNode> entry : node.properties()) { ... }
```

### Javadoc 失效引用清理

`RagAgentService` javadoc 里多处 `{@link RagTools#callbacks()}` 引用 2.x 改造时已删的方法，已修正。

---

## 数字汇总

- 净减约 **150 行**死代码 / 冗余配置
- **2 个真 bug 修复**（DI 错配 / 双重 @Async 失效）
- **2 个安全 bug 修复**（越权访问 / 假事务）
- **3 个性能优化**（N+1 / 切片复用 / 阻塞线程亲和）
- **lombok.config** 防止类似 `@Qualifier` 字段陷阱再现

## 验证清单

- [x] `mvn compile` 全过，零关联 warning
- [ ] 启动验证（profile `com` → MiniMax；`local` → Ollama）
- [ ] 手测：上传文档 → 检索 → 流式问答返回 source / references
- [ ] 手测：用 user A 的 token 访问 user B 的 sessionId → 返回 403
- [ ] 手测：流式问答中途超时 → DB 里 assistant 消息为兜底文本

## 经验沉淀

1. **`@RequiredArgsConstructor` + `@Qualifier` 在字段上无效**，必须配合 `lombok.config` 中的 `copyableAnnotations`，否则注解被吞 + Spring `@Primary` 静默接管
2. **`@Async` 失效的两种典型陷阱**：自调用绕代理 / 项目没 `@EnableAsync`；写之前先确认两个前提
3. **`@Transactional` 包裹外部系统调用是反模式**：HTTP / 消息队列等不参与本地事务，回滚也回不掉，反而误导读者
4. **N+1 query 在 Stream + Mapper 组合下极易出现**，`stream().map(id -> mapper.queryByX(id))` 是最常见形式，code review 要重点扫这个 pattern
5. **Reactor 流里调阻塞 IO 必须用 `subscribeOn(boundedElastic)` + 阻塞型 client（JDK HttpClient / RestClient）**，否则 BlockHound 会爆
6. **流式响应的副作用持久化用 `doFinally`，不要用 `doOnComplete`**，否则错误路径会丢数据

