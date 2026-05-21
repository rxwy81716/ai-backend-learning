# RAG 处理全链路详解

## 一、完整链路概览

```
用户上传文档
    ↓
文档解析（Tika / txt 直读）
    ↓
文本清洗
    ↓
切片（固定长度 + 重叠窗口）
    ↓
向量化（智谱 Embedding-3）
    ↓
入库（ES 向量 + PG 原文存档 [+ PG 向量降级]）
    ↓
用户提问
    ↓
意图路由（LLM 分类）
    ↓
Query Rewrite（多轮对话指代消解）
    ↓
混合检索（向量 + BM25 并发）
    ↓
RRF 融合
    ↓
Rerank 精排（Cross-Encoder）
    ↓
Agent 构建 Prompt（召回内容 + System Prompt）
    ↓
LLM 生成答案（流式）
    ↓
后处理（过滤  块 + 清理）
    ↓
SSE 返回前端
```

---

## 二、文档入库链路

### 2.1 文件上传（`DocumentController`）

**接口**：`POST /api/document/upload`

**流程**：

```java
// 1. 校验文件大小（最大 50MB）
if (file.getSize() > 50 * 1024 * 1024) {
    throw new IllegalArgumentException("文件大小超过 50MB");
}

// 2. 生成唯一 taskId
String taskId = UUID.randomUUID().toString().replace("-", "");

// 3. 保存到本地（./uploads 目录）
Path filePath = Paths.get(uploadDir, taskId + "_" + originalFilename);
Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

// 4. 注册任务到 DB
DocumentTask task = documentParseService.registerTask(taskId, originalFilename, filePath.toString(), file.getSize(), userId, docScope);

// 5. 投递到 Redisson 队列
documentParseService.submitToQueue(taskId);

// 6. 返回 taskId 给前端（前端轮询任务状态）
return Map.of("taskId", taskId);
```

**设计权衡**：

- **本地存储**：避免直接上传到对象存储的复杂性，适合个人项目
- **异步队列**：大文件解析可能耗时数分钟，同步上传会阻塞用户
- **taskId 前置返回**：用户可以立即离开页面，后续通过 taskId 查询进度

### 2.2 队列消费（`DocParseQueueConsumer`）

**实现**：`@Component` + `@PostConstruct` 启动消费者线程

```java
@PostConstruct
public void startConsumer() {
    RBlockingQueue<String> queue = redissonClient.getBlockingQueue(DocumentParseService.QUEUE_NAME);
    Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "doc-parse-consumer");
        t.setDaemon(true);
        return t;
    }).submit(() -> {
        while (true) {
            try {
                String taskId = queue.take();
                documentParseService.parseAndImport(taskId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("文档解析异常: {}", e.getMessage(), e);
            }
        }
    });
}
```

**设计权衡**：

- **单线程消费**：避免并发解析导致 ES 429 风险
- **Daemon 线程**：JVM 退出时自动终止，无需显式关闭
- **异常捕获**：单个任务失败不影响队列消费

### 2.3 文档解析（`DocumentParseService#parseAndImport`）

#### 2.3.1 TXT 文件特殊处理

**问题**：Tika 对中文 txt 编码检测不可靠（GBK/GB18030 经常乱码）

**方案**：绕过 Tika，手动检测编码

```java
private String readPlainTextFile(Path filePath) throws IOException {
    byte[] bytes = Files.readAllBytes(filePath);

    // UTF-8 BOM (EF BB BF) → 直接 UTF-8
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).trim();
    }

    // 尝试严格 UTF-8 解码（REPORT 模式：遇到非法字节立即抛异常）
    try {
        CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text = utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString().trim();
        if (!text.isEmpty()) {
            return text;
        }
    } catch (CharacterCodingException e) {
        // 回退到 GB18030
    }

    // GB18030（GBK / GB2312 超集，几乎覆盖所有中文 txt 编码）
    Charset gb18030 = Charset.forName("GB18030");
    try {
        CharsetDecoder gbDecoder = gb18030.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return gbDecoder.decode(ByteBuffer.wrap(bytes)).toString().trim();
    } catch (CharacterCodingException e) {
        // 最终兜底：GB18030 宽松模式（替换非法字符而非拒绝）
        return new String(bytes, gb18030).trim();
    }
}
```

**设计权衡**：

- **REPORT 模式**：严格检测，避免"部分解码但乱码"的情况
- **GB18030 超集**：覆盖 GBK/GB2312，中文 txt 几乎全覆盖
- **宽松兜底**：宁可替换字符也不报错，保证解析成功

#### 2.3.2 非 TXT 文件（PDF/Word/HTML）

```java
FileSystemResource resource = new FileSystemResource(task.getFilePath());
TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> tikaDocuments = reader.get();

StringBuilder fullText = new StringBuilder();
for (Document doc : tikaDocuments) {
    fullText.append(doc.getText()).append("\n");
}
rawText = fullText.toString().trim();
```

**设计权衡**：

- **Tika 一站式**：支持 1000+ 文件格式，无需单独处理每种格式
- **多页合并**：PDF 多页合并为单一文本流，后续统一切片

### 2.4 文本清洗（`TextCleanUtil`）

**清洗规则**：

```java
public static String clean(String text) {
    if (text == null || text.isEmpty()) return "";

    // 1. 去除 HTML 标签
    text = text.replaceAll("<[^>]+>", " ");

    // 2. 去除多余空白（换行/Tab/多个空格 → 单个空格）
    text = text.replaceAll("\\s+", " ");

    // 3. 去除特殊字符（保留中文、英文、数字、常用标点）
    text = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9，。！？、；：“”‘’（）【】《》]", " ");

    // 4. 去除首尾空白
    text = text.trim();

    return text;
}
```

**设计权衡**：

- **不过度清洗**：保留常用标点，避免语义丢失
- **正则性能**：单次正则替换，避免多次遍历

### 2.5 文本切片（`TextSplitterUtil`）

**固定长度切分 + 重叠窗口**：

```java
public static List<String> splitText(String text) {
    int chunkSize = 500;  // 每块 500 字符
    int overlap = 50;    // 重叠 50 字符

    List<String> chunks = new ArrayList<>();
    int start = 0;

    while (start < text.length()) {
        int end = Math.min(start + chunkSize, text.length());
        String chunk = text.substring(start, end);
        chunks.add(chunk);
        start = end - overlap;  // 重叠窗口
    }

    return chunks;
}
```

**设计权衡**：

- **固定长度**：实现简单，适合通用场景
- **重叠窗口**：避免句子被切断，保证上下文连贯
- **500 字符**：约 300-400 token，适合 Embedding 模型输入

**未来优化方向**：

- **语义分块**：基于句子边界 + 语义相似度
- **父子文档**：父文档用于检索，子文档用于生成

### 2.6 向量化（`EmbeddingService`）

**单条向量化**：

```java
public float[] embed(String text) {
    EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
    EmbeddingResponse response = embeddingModel.call(request);
    return response.getResult().getOutput();
}
```

**批量向量化（推荐）**：

```java
public List<float[]> embedBatch(List<String> texts) {
    EmbeddingRequest request = new EmbeddingRequest(texts, null);
    EmbeddingResponse response = embeddingModel.call(request);

    List<Embedding> results = response.getResults();
    return results.stream()
        .map(Embedding::getOutput)
        .toList();
}
```

**大批量分批（防限流）**：

```java
public List<float[]> embedBatchWithChunking(List<String> texts, int batchSize) {
    List<float[]> allVectors = new ArrayList<>();

    for (int i = 0; i < texts.size(); i += batchSize) {
        int end = Math.min(i + batchSize, texts.size());
        List<String> batch = texts.subList(i, end);
        List<float[]> batchVectors = embedBatch(batch);
        allVectors.addAll(batchVectors);
    }

    return allVectors;
}
```

**设计权衡**：

- **批量优先**：N 条文本打包成一次 HTTP 请求，减少网络开销
- **分批处理**：避免单次超过 API 限流（通常 96-256 条）
- **缓存装饰**：`CachedEmbeddingModel` 装饰单条调用，避免重复 embedding

### 2.7 ES 向量入库（`EsVectorStoreService#importChunks`）

#### 2.7.1 自适应限速（类似 TCP 拥塞控制）

```java
public int importChunks(List<String> chunks, String source, String userId, String docScope, IntConsumer progressCallback) {
    int total = chunks.size();
    int batchSize = 50;  // 每批 50 chunks

    long pauseMs = 0;  // 初始 0（全速消耗桶容量）

    for (int i = 0; i < total; i += batchSize) {
        int end = Math.min(i + batchSize, total);
        List<Document> batch = documents.subList(i, end);

        // 自适应限速暂停
        if (pauseMs > 0) {
            Thread.sleep(pauseMs);
        }

        // 429 兜底：指数退避重试
        boolean hit429 = false;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                vectorStore.add(batch);
                break;
            } catch (Exception ex) {
                if (attempt < MAX_RETRY && is429(ex)) {
                    hit429 = true;
                    long wait = RETRY_BASE_MS * (1L << attempt);  // 3s, 6s, 12s, 24s, 48s
                    Thread.sleep(wait);
                } else {
                    throw ex;
                }
            }
        }

        // 自适应调整暂停时间
        if (hit429) {
            pauseMs = Math.min(pauseMs + PAUSE_INCREASE_MS, PAUSE_MAX_MS);  // +2000ms
        } else {
            pauseMs = Math.max(pauseMs - PAUSE_DECREASE_MS, 0);  // -500ms
        }

        if (progressCallback != null) {
            progressCallback.accept(end);
        }
    }

    return total;
}
```

**参数**：

- `EMBED_BATCH_SIZE = 50`：每批 50 chunks（约 52,000 tokens）
- `MAX_RETRY = 5`：最多重试 5 次
- `RETRY_BASE_MS = 3000`：首次重试等待 3s
- `PAUSE_INCREASE_MS = 2000`：遇到 429 暂停递增 2s
- `PAUSE_DECREASE_MS = 500`：成功后暂停递减 500ms
- `PAUSE_MAX_MS = 8000`：暂停上限 8s

**设计权衡**：

- **自适应限速**：自动收敛到最优吞吐速率，无需人工调参
- **指数退避**：429 时指数级增加等待时间，避免持续打爆限流
- **进度回调**：每 500 chunks 更新一次 DB，避免频繁写库

### 2.8 PG 原文存档（`DocumentParseService#saveChunksToPg`）

```java
private void saveChunksToPg(String taskId, List<String> chunks, String source, String userId, String docScope) {
    List<DocumentChunk> rows = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setTaskId(taskId);
        chunk.setChunkIndex(i);
        chunk.setContent(chunks.get(i));
        chunk.setSource(source);
        chunk.setUserId(userId);
        chunk.setDocScope(docScope != null ? docScope : "PUBLIC");
        rows.add(chunk);
    }
    chunkMapper.batchInsert(rows);
}
```

**设计权衡**：

- **原文存档**：ES 只存向量 + metadata，原文存 PG 节省存储
- **不涉及 embedding**：纯文本入库，速度快

### 2.9 缓存失效

```java
// 新文档入库后清空 RAG 检索缓存
ragSearchCache.invalidateAll();
```

**设计权衡**：

- **主动失效**：避免"刚上传的文档检索不到"的错误
- **代价**：后续 10min 内原本能命中缓存的重复问题会重走 embedding（~2.7s）

---

## 三、RAG 问答链路

### 3.1 接口入口（`RagController#chatStream`）

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatStream(@RequestBody Map<String, String> body) {
    String question = sanitizeQuestion(body.get("question"));
    String userId = SecurityUtil.getCurrentUserIdStr();
    String sessionId = body.get("sessionId");
    if (sessionId == null || sessionId.isBlank()) {
        sessionId = UUID.randomUUID().toString().replace("-", "");
    } else {
        assertSessionOwnedByCurrentUser(sessionId, userId);  // 防止越权
    }

    String promptName = body.get("promptName");
    String chatMode = normalizeChatMode(body.get("chatMode"));  // KNOWLEDGE / LLM
    boolean thinking = parseThinking(body);
    String model = body.get("model");

    return multiAgentOrchestrator.chatStream(sessionId, question, userId, promptName, chatMode, thinking, model);
}
```

**校验逻辑**：

- `sanitizeQuestion`：非空检查 + 长度检查（上限 2000 字符）+ trim
- `normalizeChatMode`：仅允许 KNOWLEDGE / LLM，其他显式拒绝
- `assertSessionOwnedByCurrentUser`：防止越权读他人对话

### 3.2 多 Agent 编排（`MultiAgentOrchestrator#chatStream`）

#### 3.2.1 模式判断

```java
String mode = normalizeMode(chatMode);  // KNOWLEDGE / LLM
boolean forceLlm = MODE_LLM.equals(mode);
```

**模式**：

- `KNOWLEDGE`：启用意图路由 + 多 Agent 路由
- `LLM`：强制走 ChatAgent（用户主动选择的逃生口）

#### 3.2.2 意图路由

```java
AgentType intent = forceLlm ? AgentType.CHAT : intentRouter.route(question);
SpecializedAgent agent = agentRegistry.get(intent);
if (agent == null) {
    agent = agentRegistry.get(AgentType.CHAT);  // fallback
}
```

**IntentRouter 实现**：

```java
public AgentType route(String question) {
    String systemPrompt = """
        你是一个意图分类助手。请将用户问题分类为以下类型之一：
        - KNOWLEDGE: 知识库问答（询问文档内容、技术问题等）
        - WEB_SEARCH: 网络搜索（需要实时信息、新闻等）
        - DOCUMENT_SEARCH: 文档搜索（查找特定文档）
        - DOCUMENT_OVERVIEW: 文档概览（了解文档整体内容）
        - HOT_SEARCH: 热点搜索（查询热门话题）
        - CHAT: 纯聊天（闲聊、问候等）

        只输出类型名称，不要解释。
        """;

    Prompt prompt = new Prompt(List.of(
        new SystemMessage(systemPrompt),
        new UserMessage(question)
    ));

    String result = chatModel.call(prompt).getResult().getOutput().getText().trim();
    return AgentType.valueOf(result);
}
```

**设计权衡**：

- **LLM 分类**：比规则更灵活，能处理复杂语义
- **Fallback**：分类失败默认走 ChatAgent，不阻断主流程

#### 3.2.3 构建 System Prompt

```java
String basePrompt = forceLlm ? agent.systemPrompt() : messageBuilder.resolveAgentSystemPrompt(promptName, agent);
String sysPrompt = thinking ? basePrompt : (NO_THINK_PREFIX + basePrompt);  // /no_think 前缀禁用思考块
```

**设计权衡**：

- **多 SystemPrompt**：支持用户自定义 Prompt（`promptName`）
- **思考模式**：`/no_think` 前缀禁用 LLM 的  块，减少 token 消耗

#### 3.2.4 构建消息列表

```java
List<Message> messages = messageBuilder.build(sysPrompt, sessionId, question, mode);
```

**ChatMessageBuilder 实现**：

```java
public List<Message> build(String systemPrompt, String sessionId, String question, String mode) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));

    if (sessionId != null) {
        List<ChatMessage> history = chatHistoryCache.getHistory(sessionId, mode);
        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
    }

    messages.add(new UserMessage(question));
    return messages;
}
```

**设计权衡**：

- **按 mode 隔离历史**：KNOWLEDGE 模式和 LLM 模式的对话历史分开，避免污染
- **最近 N 轮**：默认取最近 10 轮，避免 token 溢出

#### 3.2.5 保存用户消息

```java
if (sessionId != null) {
    String userMeta = metaBuilder.toJson(Map.of(FollowUpDetector.META_KEY_MODE, mode));
    chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question, userMeta, userId));
}
```

#### 3.2.6 Query Rewrite（多轮对话指代消解）

```java
if (!forceLlm && intent == AgentType.KNOWLEDGE) {
    List<Message> historyOnly = messages.stream().filter(m -> !(m instanceof SystemMessage)).toList();
    if (historyOnly.size() > 1) {
        List<Message> prevHistory = historyOnly.subList(0, historyOnly.size() - 1);
        QueryRewriteService.RewriteResult rewriteResult = queryRewriteService.rewriteWithTrace(prevHistory, question);
        ctx.recordRewrite(rewriteResult);
    }
}
```

**QueryRewriteService 实现**：

```java
public RewriteResult rewriteWithTrace(List<Message> history, String question) {
    if (!enabled) return new RewriteResult(question, false, false, 0L, "disabled");
    if (history == null || history.size() < minHistory) return new RewriteResult(question, false, false, 0L, "history_insufficient");

    try {
        long t0 = System.currentTimeMillis();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(REWRITE_SYSTEM_PROMPT));

        // 只取最近 6 条历史（约 3 轮问答）
        int start = Math.max(0, history.size() - historyWindow);
        for (int i = start; i < history.size(); i++) {
            messages.add(history.get(i));
        }
        messages.add(new UserMessage("请将以下追问改写为独立查询：\n" + question));

        Prompt prompt = new Prompt(messages);
        String rewritten = CompletableFuture.supplyAsync(() -> chatModel.call(prompt).getResult().getOutput().getText(), rewriteExecutor)
            .get(timeoutMs, TimeUnit.MILLISECONDS);

        long cost = System.currentTimeMillis() - t0;

        if (rewritten == null || rewritten.isBlank()) {
            return new RewriteResult(question, true, false, cost, "empty_result");
        }

        rewritten = normalizeQuery(rewritten);
        if (rewritten.equals(question)) {
            return new RewriteResult(question, true, false, cost, "unchanged");
        } else {
            return new RewriteResult(rewritten, true, true, cost, "rewritten");
        }
    } catch (TimeoutException e) {
        return new RewriteResult(question, true, false, timeoutMs, "timeout");
    } catch (Exception e) {
        return new RewriteResult(question, true, false, 0L, "error");
    }
}
```

**System Prompt**：

```
你是一个查询改写助手。你的唯一任务是将用户的最新追问改写为一个独立、完整、适合检索的查询语句。

规则：
1. 结合对话历史，将指代词（它、这个、那个、上面提到的）替换为具体实体
2. 补全省略的主语或宾语
3. 只输出改写后的查询，不要解释、不要加引号、不要加前缀
4. 如果追问本身已经是完整独立的查询，直接原样输出
5. 保留原始问题的语言（中文/英文）
6. 改写后的查询应简洁，适合搜索引擎或向量检索，不超过 100 字
```

**设计权衡**：

- **历史窗口限制**：只取最近 6 条（约 3 轮），避免 token 浪费
- **超时降级**：1200ms 超时即回退原始 query，不拖慢首 token
- **静默失败**：改写失败不抛异常，直接用原始 query

#### 3.2.7 追问检测与上文注入

```java
String followUpContext = null;
if (sessionId != null && followUpDetector.isFollowUp(question)) {
    followUpContext = followUpDetector.loadLastAssistantContent(sessionId, mode);
    if (followUpContext != null) {
        log.info("[追问检测] 检测到追问，注入上文 {} 字符", followUpContext.length());
    }
}

if (followUpContext != null) {
    messages.add(messages.size() - 1, new SystemMessage(
        "【对话上文（用户正在追问此内容）】\n" + followUpContext +
        "\n\n用户正在基于以上内容进行追问，请结合上下文理解用户的指代（如'第一个文档''刚才那个'等）。"));
}
```

**FollowUpDetector 实现**：

```java
public boolean isFollowUp(String question) {
    // 指代词检测
    String[] pronouns = {"它", "这个", "那个", "上面提到的", "刚才", "第一个", "第二个"};
    for (String p : pronouns) {
        if (question.contains(p)) {
            return true;
        }
    }
    return false;
}
```

**设计权衡**：

- **简单规则**：基于关键词检测，无需 LLM
- **上文注入**：在 SystemMessage 中注入，优先级高

#### 3.2.8 构建 AgentRequest

```java
AgentRequest request = new AgentRequest(sessionId, question, userId, messages, ctx, thinking);
```

#### 3.2.9 Agent 执行

```java
Flux<String> tokenFlux = agent.execute(request)
    .doOnSubscribe(s -> ctx.emitStep("generate", Map.of("intent", finalIntent.name())))
    .timeout(Mono.delay(Duration.ofSeconds(15)), ignored -> Mono.delay(Duration.ofSeconds(25)))
    .map(stripper::process)  // ThinkBlockStripper
    .concatWith(Flux.defer(() -> buildMetaFlux(ctx, finalIntent, forceLlm, errorCodeHolder[0])))
    .onErrorResume(e -> {
        String code = streamErrorHandler.classify(e, firstByteReceived);
        String fallback = streamErrorHandler.render(code, firstByteReceived);
        return Flux.just(fallback, "[META]" + metaBuilder.toJson(meta) + "[/META]");
    })
    .doFinally(sig -> {
        // 持久化 assistant 消息
        chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", content, metaBuilder.toJson(metaMap), userId));
    });
```

### 3.3 混合检索（`HybridSearchService#searchWithOwnership`）

#### 3.3.1 缓存检查

```java
String cacheKey = buildCacheKey(query, userId, topK);
List<Document> cached = ragSearchCache.getIfPresent(cacheKey);
if (cached != null) {
    log.info("⚡ Hybrid检索缓存命中 | key={}, hit={}条", cacheKey, cached.size());
    return cached;
}
```

**CacheKey 构造**：

```java
private String buildCacheKey(String query, String userId, int topK) {
    String q = query == null ? "" : query.trim().toLowerCase().replaceAll("\\s+", " ");
    String u = (userId == null || userId.isBlank()) ? "_anon_" : userId;
    String rerankSig = rerankService.isEnabled() ? ("rerank:on:" + rerankCandidates + ":" + topK) : "rerank:off";
    return u + "|" + topK + "|" + rerankSig + "|" + q;
}
```

**设计权衡**：

- **Query 规范化**：trim + lower + 多空白合并，让变体命中同一缓存
- **Rerank 签名**：Rerank 开关/参数变化时缓存失效
- **userId 隔离**：不同用户的检索结果隔离

#### 3.3.2 熔断检查

```java
List<Document> result = searchBreaker.callOrFallback(
    () -> doSearchWithOwnership(query, userId, topK),
    List::of);
```

**SimpleCircuitBreaker 实现**：

```java
public T callOrFallback(Supplier<T> supplier, Supplier<T> fallback) {
    if (state == State.OPEN) {
        rejectedCalls.incrementAndGet();
        return fallback.get();
    }

    totalCalls.incrementAndGet();
    try {
        T result = supplier.get();
        onSuccess();
        return result;
    } catch (Exception e) {
        failedCalls.incrementAndGet();
        onFailure();
        return fallback.get();
    }
}
```

**状态机**：

```
CLOSED → 连续失败 5 次 → OPEN（熔断 30s）→ HALF_OPEN → 成功 → CLOSED
                                      ↓
                                    失败 → OPEN
```

#### 3.3.3 并发检索

```java
CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
    () -> vectorSearchService.searchWithOwnership(query, userId, vectorTopK, similarityThreshold),
    ragSearchExecutor);

CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(
    () -> keywordSearchService.searchWithOwnership(query, userId, keywordTopK),
    ragSearchExecutor);

CompletableFuture<Void> all = CompletableFuture.allOf(vectorFuture, keywordFuture);
all.get(parallelTimeoutMs, TimeUnit.MILLISECONDS);
```

**线程池**（`CacheConfig#ragSearchExecutor`）：

```java
int corePoolSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
int maxPoolSize = Math.max(32, corePoolSize * 3 / 2);
return new ThreadPoolExecutor(
    corePoolSize,
    maxPoolSize,
    60L,
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(200),
    r -> {
        Thread t = new Thread(r, "rag-search-" + seq.incrementAndGet());
        t.setDaemon(true);
        return t;
    },
    new ThreadPoolExecutor.CallerRunsPolicy());
```

**设计权衡**：

- **专用线程池**：避免阻塞 IO 占满 `ForkJoinPool.commonPool()`
- **CallerRunsPolicy**：队列满时让调用线程自己跑，自动反压

#### 3.3.4 向量检索（`EsVectorSearchService`）

```java
public List<Document> searchWithOwnership(String query, String userId, int topK, double similarityThreshold) {
    var b = new FilterExpressionBuilder();

    Filter.Expression filter;
    if (userId != null && !userId.isBlank()) {
        // 公共文档 OR 该用户的私有文档
        filter = b.or(
                b.eq("doc_scope", "PUBLIC"),
                b.and(b.eq("doc_scope", "PRIVATE"), b.eq("user_id", userId)))
            .build();
    } else {
        filter = b.eq("doc_scope", "PUBLIC").build();
    }

    SearchRequest request = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .similarityThreshold(similarityThreshold)
        .filterExpression(filter)
        .build();

    return vectorStore.similaritySearch(request);
}
```

**设计权衡**：

- **FilterExpressionBuilder**：类型安全的 Filter 构建，编译期校验
- **用户隔离**：PUBLIC OR (PRIVATE AND user_id)，防止越权

#### 3.3.5 BM25 关键词检索（`EsKeywordSearchService`）

```java
public List<Document> searchWithOwnership(String query, String userId, int topK) {
    Query matchContent = Query.of(q -> q.matchPhrase(m -> m.field("content").query(query)));
    Query ownership = buildOwnershipQuery(userId);

    SourceFilter sourceFilter = SourceFilter.of(s -> s.includes("content", "metadata"));

    SearchResponse<Map> response = esClient.search(
        s -> s.index(indexName)
            .size(topK)
            .source(src -> src.filter(sourceFilter))
            .query(q -> q.bool(b -> b.must(matchContent).filter(ownership))),
        Map.class);

    List<Document> results = new ArrayList<>();
    for (Hit<Map> hit : response.hits().hits()) {
        Map source = hit.source();
        String content = String.valueOf(source.getOrDefault("content", ""));
        Map<String, Object> metadata = normalizeMetadata((Map<?, ?>) source.get("metadata"));
        if (hit.score() != null) metadata.put("bm25_score", hit.score());
        results.add(new Document(hit.id(), content, metadata));
    }
    return results;
}
```

**设计权衡**：

- **match_phrase**：保持短语完整性，避免"蒜薹噩梦"被拆分
- **_source 过滤**：只取 content + metadata，减小网络开销
- **BM25 score**：写入 metadata，用于调试

#### 3.3.6 RRF 融合

```java
private List<Document> rrfFuse(List<Document> vectorHits, List<Document> keywordHits, int topK) {
    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, Document> docMap = new LinkedHashMap<>();
    Map<String, Integer> vectorRank = new HashMap<>();
    Map<String, Integer> bm25Rank = new HashMap<>();

    accumulate(vectorHits, rrfScores, docMap, vectorRank);
    accumulate(keywordHits, rrfScores, docMap, bm25Rank);

    // 堆排序 O(n log k)
    PriorityQueue<Map.Entry<String, Double>> pq = new PriorityQueue<>(
        (a, b) -> Double.compare(a.getValue(), b.getValue()));

    for (Map.Entry<String, Double> entry : rrfScores.entrySet()) {
        if (pq.size() < topK) {
            pq.offer(entry);
        } else if (entry.getValue() > pq.peek().getValue()) {
            pq.poll();
            pq.offer(entry);
        }
    }

    // 逆序输出
    List<Document> result = new ArrayList<>(pq.size());
    List<Map.Entry<String, Double>> sorted = new ArrayList<>(pq);
    sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

    for (Map.Entry<String, Double> entry : sorted) {
        String id = entry.getKey();
        Document origin = docMap.get(id);
        Map<String, Object> meta = new HashMap<>(origin.getMetadata());
        meta.put("hybrid_score", entry.getValue());
        if (vectorRank.containsKey(id)) meta.put("vector_rank", vectorRank.get(id));
        if (bm25Rank.containsKey(id)) meta.put("bm25_rank", bm25Rank.get(id));
        result.add(new Document(origin.getId(), origin.getText(), meta));
    }
    return result;
}

private void accumulate(List<Document> hits, Map<String, Double> rrfScores, Map<String, Document> docMap, Map<String, Integer> rankMap) {
    for (int i = 0; i < hits.size(); i++) {
        Document doc = hits.get(i);
        String id = doc.getId();
        if (id == null || id.isBlank()) continue;

        int rank = i + 1;
        double contribution = 1.0 / (rrfK + rank);
        rrfScores.merge(id, contribution, Double::sum);
        docMap.putIfAbsent(id, doc);
        rankMap.putIfAbsent(id, rank);
    }
}
```

**RRF 公式**：

```
score(d) = Σ 1 / (k + rank_i(d))
```

**设计权衡**：

- **堆排序**：O(n log k) 优于全量排序 O(n log n)
- **元数据标注**：vector_rank / bm25_rank 用于调试

#### 3.3.7 Rerank 精排

```java
if (rerankService.isEnabled() && fused.size() > 1) {
    finalResult = rerankService.rerank(query, fused, topK);
} else {
    finalResult = fused;
}
```

**RerankService 实现**：

```java
public List<Document> rerank(String query, List<Document> candidates, int topN) {
    List<String> docTexts = new ArrayList<>(candidates.size());
    for (Document doc : candidates) {
        docTexts.add(truncateDoc(doc.getText()));
    }

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", model);
    requestBody.put("query", query);
    requestBody.put("documents", docTexts);
    requestBody.put("top_n", Math.min(topN, candidates.size()));
    requestBody.put("return_documents", false);

    String responseStr = createRestClient().post()
        .uri(apiUrl)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(objectMapper.writeValueAsString(requestBody))
        .retrieve()
        .body(String.class);

    JsonNode root = objectMapper.readTree(responseStr);
    JsonNode results = root.get("results");

    List<Document> reranked = new ArrayList<>();
    for (JsonNode item : results) {
        int index = item.get("index").asInt();
        double score = item.get("relevance_score").asDouble();

        if (score < scoreThreshold) continue;

        Document origin = candidates.get(index);
        Map<String, Object> meta = new HashMap<>(origin.getMetadata());
        meta.put("rerank_score", score);
        reranked.add(new Document(origin.getId(), origin.getText(), meta));
    }

    return reranked.isEmpty() ? candidates.subList(0, Math.min(topN, candidates.size())) : reranked;
}
```

**设计权衡**：

- **阈值过滤**：score < 0.7 直接丢弃，提升 Precision
- **降级策略**：API 超时/失败 → 返回 RRF 结果

#### 3.3.8 缓存写入

```java
if (!result.isEmpty()) {
    ragSearchCache.put(cacheKey, result);
}
```

**设计权衡**：

- **仅缓存非空结果**：避免把瞬时空结果（如 ES 抖动）冻结 TTL

### 3.4 Agent 生成答案（`KnowledgeAgent`）

```java
@Override
public Flux<String> executeStream(AgentRequest request) {
    String query = request.getQuestion();
    String userId = request.getUserId();
    RagToolContext ctx = request.getContext();

    // 检索
    List<Document> docs = hybridSearchService.searchWithOwnership(query, userId, 5);
    ctx.setRetrievedDocs(docs);

    // 构建 Prompt
    String context = buildContext(docs);
    String prompt = buildPrompt(request.getSystemPrompt(), request.getQuestion(), context);

    // 调用 ChatModel
    ChatModel chatModel = chatClientResolver.resolve(request.getModelKey());
    return chatModel.stream(new Prompt(prompt))
        .map(ChatResponse::getResult)
        .map(Generation::getOutput)
        .map(AssistantMessage::getContent);
}
```

### 3.5 后处理（`MultiAgentOrchestrator`）

#### 3.5.1 ThinkBlockStripper

```java
static class ThinkBlockStripper {
    private final StringBuilder buffer = new StringBuilder();
    private boolean inThinkBlock = false;
    private int thinkDepth = 0;

    public String process(String chunk) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);

            if (!inThinkBlock && i + 7 <= chunk.length() && chunk.substring(i, i + 7).equals("")) {
                inThinkBlock = true;
                thinkDepth = 1;
                i += 6;
                continue;
            }

            if (inThinkBlock && i + 8 <= chunk.length() && chunk.substring(i, i + 8).equals("")) {
                thinkDepth--;
                if (thinkDepth == 0) {
                    inThinkBlock = false;
                    i += 7;
                    continue;
                }
            }

            if (inThinkBlock) {
                buffer.append(c);
            } else {
                output.append(c);
            }
        }

        return output.toString();
    }

    public String flush() {
        String remaining = buffer.toString();
        buffer.setLength(0);
        return remaining;
    }
}
```

**设计权衡**：

- **流式过滤**：逐字符处理，避免 buffer 溢出
- **嵌套支持**：支持嵌套  块（虽然少见）

#### 3.5.2 cleanAnswer

```java
static String cleanAnswer(String raw) {
    if (raw == null || raw.isEmpty()) return "";

    // 去除  块
    String cleaned = THINK_BLOCK.matcher(raw).replaceAll("");

    // 去除 [STEP]...[/STEP]
    cleaned = STEP_BLOCK.matcher(cleaned).replaceAll("");

    // 去除 META 块行
    cleaned = META_BLOCK_LINE.matcher(cleaned).replaceAll("");

    // 去除内联来源标签
    cleaned = INLINE_SOURCE_TAG.matcher(cleaned).replaceAll("");

    // 去除参考来源段落
    cleaned = REFERENCE_FOOTER.matcher(cleaned).replaceAll("");

    // 去除工具调用前导语
    cleaned = LEADING_TOOL_PREAMBLE.matcher(cleaned).replaceAll("");

    // 压缩多空行
    cleaned = MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n");

    return cleaned.trim();
}
```

### 3.6 META 构建（`MetaBuilder`）

```java
public Map<String, Object> build(RagToolContext ctx, AgentType intent, boolean forceLlm, String errorCode) {
    Map<String, Object> meta = new HashMap<>();

    // 意图
    meta.put("intent", intent.name());

    // 来源
    String source = ctx.getInvokedTools().isEmpty() ? "llm" : "rag";
    meta.put("source", source);

    // 工具调用
    meta.put("tools", ctx.getInvokedTools());

    // 检索文档数
    meta.put("hitCount", ctx.getRetrievedDocs().size());

    // Query Rewrite
    if (ctx.isRewriteAttempted()) {
        meta.put("rewriteAttempted", true);
        meta.put("rewriteChanged", ctx.isRewriteChanged());
    }

    // 错误码
    if (errorCode != null) {
        meta.put("errorCode", errorCode);
    }

    return meta;
}
```

**输出格式**：

```
[META]{"intent":"KNOWLEDGE","source":"rag","tools":["vector_search"],"hitCount":5,"rewriteAttempted":true,"rewriteChanged":false}[/META]
```

---

## 四、性能优化点

### 4.1 Embedding 缓存

**命中场景**：

- 用户重复提问
- PlannerAgent 多轮检索时同义子 query

**实现**：`CachedEmbeddingModel` 装饰器

```java
@Override
public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> instructions = request.getInstructions();

    if (instructions == null || instructions.size() != 1) {
        return delegate.call(request);  // 批量调用透传
    }

    String text = instructions.get(0);
    float[] cached = cache.getIfPresent(text);
    if (cached != null) {
        return new EmbeddingResponse(List.of(new Embedding(cached.clone(), 0)), new EmbeddingResponseMetadata());
    }

    EmbeddingResponse response = delegate.call(request);
    cache.put(text, response.getResult().getOutput().clone());
    return response;
}
```

**参数**：

- maxSize = 2000（约 8MB）
- expireAfterWrite = 10 min

### 4.2 检索结果缓存

**Key**：`userId|topK|rerankSig|query`

**TTL**：10 min

**失效策略**：

- 文档入库/删除时主动 `invalidateAll()`

### 4.3 自适应限速

**原理**：类似 TCP 拥塞控制

**效果**：自动收敛到最优吞吐速率，无需人工调参

### 4.4 专用线程池

**避免**：阻塞 IO 占满 `ForkJoinPool.commonPool()`

**配置**：

- corePoolSize = max(8, CPU * 2)
- maxPoolSize = max(32, core * 1.5)
- queue = 200（bounded）
- policy = CallerRunsPolicy（反压）

### 4.5 熔断降级

**效果**：连续失败 5 次 → 熔断 30s → 半开探测 → 恢复

**行为**：熔断时返回空列表，不抛异常打断 chat 流

---

## 五、错误处理

### 5.1 StreamErrorHandler

**错误分类**：

```java
public String classify(Throwable e, boolean firstByteReceived) {
    if (e instanceof TimeoutException) {
        return "TIMEOUT";
    }
    if (e instanceof org.springframework.web.client.ResourceAccessException) {
        return "NETWORK_ERROR";
    }
    if (e instanceof org.springframework.ai.retry.RetryExhaustedException) {
        return "LLM_RETRY_EXHAUSTED";
    }
    if (firstByteReceived) {
        return "STREAM_INTERRUPTED";
    }
    return "UNKNOWN_ERROR";
}
```

**错误渲染**：

```java
public String render(String code, boolean firstByteReceived) {
    if (firstByteReceived) {
        return "\n\n_[生成中断，请重试]_";
    }
    return switch (code) {
        case "TIMEOUT" -> "请求超时，请稍后重试";
        case "NETWORK_ERROR" -> "网络异常，请检查连接";
        case "LLM_RETRY_EXHAUSTED" -> "大模型服务暂时不可用，请稍后重试";
        default -> "服务异常，请稍后重试";
    };
}
```

### 5.2 指标记录

```java
ragMetrics.incrementErrorCode(code);
```

**Prometheus 指标**：

```
rag.chat.error.total{code="TIMEOUT"}
rag.chat.error.total{code="NETWORK_ERROR"}
```

---

## 六、可观测性

### 6.1 自定义指标（`RagMetrics`）

```
rag.embed.cache.requests      # Embedding 缓存请求次数
rag.embed.cache.hits         # Embedding 缓存命中次数
rag.embed.cache.hit_rate     # Embedding 缓存命中率
rag.search.breaker.total     # 熔断器总调用次数
rag.search.breaker.state     # 熔断器状态（0=CLOSED, 1=HALF_OPEN, 2=OPEN）
rag.chat.error.total{code}   # Chat 错误计数
rag.chat.duration{model}     # Chat 单次耗时（按模型拆分）
```

### 6.2 Actuator Endpoint

```
/actuator/health      # 健康检查
/actuator/metrics     # Micrometer 指标
/actuator/prometheus  # Prometheus 格式
```

---

## 七、安全与权限

### 7.1 用户隔离检索

**Filter 表达式**：

```
(doc_scope == PUBLIC) OR (doc_scope == PRIVATE AND user_id == userId)
```

### 7.2 会话归属校验

```java
private void assertSessionOwnedByCurrentUser(String sessionId, String userId) {
    ChatSession session = chatHistoryCache.getSession(sessionId);
    if (session != null && !session.getUserId().equals(userId)) {
        throw new ForbiddenException("会话不属于当前用户");
    }
}
```

### 7.3 限流

**匿名用户**：

- 每窗口最大请求数：20
- 窗口大小：60 min

**已认证用户**：

- 单用户 QPS：2
- 单用户每日上限：200
- 单 IP QPS：5

---

## 八、未来优化方向

### 8.1 检索效果提升

- **评测体系**：构造评测集，跑 Recall@K / MRR / nDCG
- **语义分块**：基于语义相似度的动态分块
- **HyDE**：用 LLM 生成"假设性答案"再 embedding 检索
- **多查询扩展**：用 LLM 生成多个子查询并行检索

### 8.2 成本优化

- **语义缓存**：基于 embedding 相似度的历史答案复用
- **Token 计量**：按用户/模型统计 token 消耗

### 8.3 安全增强

- **Prompt 注入防御**：关键词/正则黑名单
- **PII 脱敏**：入库前/LLM 调用前对敏感信息脱敏
