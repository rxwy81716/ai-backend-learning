# Local AI Knowledge — LangChain4j 版

> 对标 `local-ai-knowledge`（Spring AI 版），使用 LangChain4j 重新实现全部 AI 能力层。

## 技术栈

| 层 | Spring AI 版 | LangChain4j 版 |
|----|-------------|----------------|
| LLM 调用 | `ChatModel` / `ChatClient` | `ChatLanguageModel` / `StreamingChatLanguageModel` |
| 流式输出 | `Flux<String>` 原生 | `TokenStreamFluxAdapter`（回调→Flux 桥接） |
| Embedding | `EmbeddingModel.embed()` → `float[]` | `EmbeddingModel.embed()` → `Embedding.vector()` |
| 向量存储 | `VectorStore.similaritySearch(SearchRequest)` | `EmbeddingStore.search(EmbeddingSearchRequest)` |
| 过滤语法 | `FilterExpression("a == 'b'")` | `new IsEqualTo("a", "b")` |
| 工具调用 | `@org.springframework.ai.tool.annotation.Tool` | `@dev.langchain4j.agent.tool.Tool` |
| 工具参数 | `@ToolParam` | `@P` |
| 工具上下文 | `ToolContext` | ThreadLocal + `RagToolContext` |
| 消息类型 | `AssistantMessage` | `AiMessage` |
| Token 计数 | `JTokkitTokenCountEstimator` | `OpenAiTokenizer` |
| 文档解析 | `TikaDocumentReader.read()` | `ApacheTikaDocumentParser.parse()` |
| MCP Server | `spring-ai-starter-mcp-server-webmvc` | ❌ 需自研（LangChain4j 暂无） |

## 项目结构

```
src/main/java/com/jianbo/localaiknowledge/
├── adapter/
│   └── TokenStreamFluxAdapter.java    ← 核心：回调式→Flux 桥接器
├── config/
│   ├── ChatModelRegistry.java         ← 多模型注册表
│   └── VectorStoreConfig.java         ← EmbeddingModel + ES Store
├── controller/
│   └── RagController.java             ← 对外 SSE 接口（签名不变）
├── mapper/                            ← MyBatis mapper（原样复用）
├── model/                             ← 数据模型（原样复用）
├── service/
│   ├── EmbeddingService.java          ← embed() + embedBatch()
│   ├── EsVectorSearchService.java     ← 向量检索（核心 API 差异）
│   ├── HybridSearchService.java       ← RRF 混合检索
│   ├── DocumentParseService.java      ← 文档解析（手动 embed+存入）
│   └── agent/
│       ├── SpecializedAgent.java      ← Agent 接口
│       ├── ChatAgent.java             ← 最简 Agent（展示适配模式）
│       ├── KnowledgeAgent.java        ← RAG Agent
│       ├── PlannerAgent.java          ← ReAct Agent
│       ├── KnowledgeTools.java        ← @Tool（ThreadLocal 传 userId）
│       ├── ChatMessageBuilder.java    ← 消息构建
│       └── MultiAgentOrchestrator.java ← 编排器
└── utils/
    ├── ChatContextUtil.java           ← Token 裁剪
    └── RagFormatUtil.java             ← 检索结果格式化
```

## 与前端对接

**前端代码完全不需要改动**。后端 SSE 接口签名、URL、参数格式与 Spring AI 版完全一致：

```
POST /api/rag/chat/stream  →  text/event-stream
```

## 启动

```bash
# 1. 确保 PostgreSQL、Redis、Elasticsearch 已启动
# 2. 设置环境变量（或修改 application.yml）
export OPENAI_API_KEY=sk-xxx
export OPENAI_BASE_URL=https://api.siliconflow.cn/v1

# 3. 启动
mvn spring-boot:run
```

## 已完成

- [x] `EsKeywordSearchService` — ES Java Client BM25 match_phrase 查询
- [x] `SystemPromptService` — DB + Caffeine 缓存
- [x] `HotSearchService` — CrawlerHotItemMapper 多平台热搜
- [x] `WebSearchService` — Tavily API 完整实现
- [x] Security 配置 — JWT + CORS + 路由鉴权
- [x] `EsVectorStoreService` — 分批 embed + 自适应限速入库
- [x] `DocumentController` — 文件上传/状态/删除
- [x] 可观测性 — RagMetrics (Prometheus)

## 待完成

- [ ] MCP Server 自研方案（LangChain4j 暂无等价）
- [ ] Admin Controller（用户/角色管理）
- [ ] 完整集成测试
- [ ] `DocumentController.upload` 文件落盘 + 异步解析串联
