# local-ai-knowledge

基于 Spring Boot + Spring AI 的本地知识库 RAG 问答系统，支持文档上传解析、向量检索、多轮对话、多智能体路由和流式响应。

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0 + Spring AI |
| LLM | MiniMax（ChatClient + Embedding） |
| 向量存储 | Elasticsearch / PgVector |
| 关系数据库 | PostgreSQL |
| 缓存 | Redis（会话热缓存）+ Caffeine（本地缓存） |
| 异步队列 | Redisson（阻塞队列，持久化任务） |
| 文档解析 | Apache Tika |
| 网络搜索 | Tavily API（可选降级） |
| Java | 21 |

## 项目结构

```
src/main/java/com/jianbo/localaiknowledge/
├── config/                          # 配置类
│   ├── CacheConfig.java             # Caffeine 缓存配置
│   ├── ChatClientConfig.java        # MiniMax ChatClient Bean
│   ├── RedisConfig.java             # RedisTemplate 配置
│   ├── RedissonConfig.java          # Redisson 客户端配置
│   ├── VectorStoreConfig.java       # ES VectorStore Bean
│   ├── WebMvcConfig.java            # CORS 配置
│   └── AsyncConfig.java             # 调度配置
├── controller/
│   ├── DocumentController.java      # 文档上传 & 任务管理 API
│   └── RagController.java           # RAG 问答 & 会话 & Prompt API
├── consumer/
│   └── DocParseQueueConsumer.java   # Redisson 队列消费者
├── mapper/
│   ├── DocumentTaskMapper.java      # 解析任务 CRUD
│   ├── DocumentTaskLogMapper.java   # 任务操作日志
│   ├── SystemPromptMapper.java      # SystemPrompt CRUD
│   └── ChatConversationMapper.java  # 会话消息 CRUD
├── model/
│   ├── DocumentTask.java            # 文档解析任务模型
│   ├── SystemPrompt.java            # 系统提示词配置
│   └── ChatMessage.java             # 聊天消息模型
├── service/
│   ├── EsVectorStoreService.java    # ES 向量入库（支持用户归属）
│   ├── EsVectorSearchService.java   # ES 向量检索（支持用户隔离）
│   ├── EmbeddingService.java        # 文本向量化
│   ├── DocumentParseService.java    # 文档解析 + 入库
│   ├── RagService.java              # 基础 RAG 问答
│   ├── RagAgentService.java         # 多智能体路由（知识库 → 网络搜索）
│   ├── WebSearchService.java        # 网络搜索降级（Tavily）
│   ├── SystemPromptService.java     # Prompt 管理（带缓存）
│   └── ChatHistoryCacheService.java # 会话缓存（Redis + DB 双写）
└── utils/
    ├── TextCleanUtil.java           # 文本清洗
    ├── TextSplitterUtil.java        # 文本分段
    ├── ChatContextUtil.java         # Token 计数 & 裁剪
    └── RedisUtil.java               # Redis 对象转换
```

## 核心架构

```
用户请求 → RagController
              │
              ▼
        RagAgentService (路由决策)
              │
      ┌───────┴────────┐
      ▼                ▼
  Agent 1           Agent 2
  知识库检索         网络搜索
  (ES 向量)        (Tavily API)
      │                │
      ▼                ▼
  拼装 SystemPrompt + Context + History
              │
              ▼
        ChatClient (MiniMax LLM)
              │
        ┌─────┴─────┐
        ▼           ▼
     同步返回    SSE 流式
              │
              ▼
     持久化 DB + Redis 缓存
```

### 文档隔离

- 用户上传文档标记为 `PRIVATE`，绑定 `userId`
- 爬虫/公共数据标记为 `PUBLIC`
- 检索时过滤：`(scope=PUBLIC) OR (scope=PRIVATE AND user_id=当前用户)`

### 抗幻觉三道防线

1. **SystemPrompt 约束** — 严格要求只引用参考资料
2. **检索为空拦截** — 无相关文档直接返回，不调 LLM
3. **置信度 + 来源引用** — 返回 `confidence` 分数和 `references`

## API 接口

### 智能问答（推荐，多智能体路由）

```bash
# 同步
POST /api/rag/agent/chat
{"question": "什么是RAG?", "userId": "user1", "sessionId": "xxx"}

# SSE 流式
POST /api/rag/agent/chat/stream
{"question": "什么是RAG?", "userId": "user1", "sessionId": "xxx"}
```

### 基础 RAG

```bash
POST /api/rag/chat                 # 单轮同步
POST /api/rag/chat/stream          # 单轮 SSE
POST /api/rag/multi-chat           # 多轮同步
POST /api/rag/multi-chat/stream    # 多轮 SSE
```

### 文档管理

```bash
POST /api/doc/upload               # 上传文档（?userId=xxx 绑定用户）
GET  /api/doc/status/{taskId}      # 查询解析进度
GET  /api/doc/tasks                # 所有任务列表
GET  /api/doc/logs/{taskId}        # 任务操作日志
```

### 会话管理

```bash
GET    /api/rag/sessions            # 所有会话 ID
GET    /api/rag/history/{sessionId} # 会话历史消息
DELETE /api/rag/session/{sessionId} # 删除会话
```

### Prompt 管理

```bash
GET    /api/rag/prompts             # 所有 SystemPrompt
POST   /api/rag/prompt              # 创建/更新 Prompt
PUT    /api/rag/prompt/default/{name} # 设置默认 Prompt
```

## 快速开始

### 环境依赖

- Java 21+
- PostgreSQL 16+（需安装 pgvector 扩展）
- Elasticsearch 8.x
- Redis 7+

### 初始化数据库

```sql
-- 1. 执行建表
\i src/main/resources/db/document_task.sql
\i src/main/resources/db/rag_tables.sql
```

### 配置

修改 `application-local.yml` 或 `application-com.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vector_db
  ai:
    minimax:
      api-key: your-api-key
  data:
    redis:
      host: localhost

# 可选：启用网络搜索降级
app:
  web-search:
    enabled: true
    api-key: your-tavily-api-key
```

### 启动

```bash
mvn spring-boot:run
```

## 缓存策略

| 缓存项 | 存储 | TTL | 用途 |
|--------|------|-----|------|
| SystemPrompt | Caffeine | 10 min | 避免每次请求查 DB |
| 会话历史 | Redis | 30 min | 热会话加速，DB 兜底 |
| 任务队列 | Redisson | 持久化 | JVM 重启不丢任务 |

## 后续规划

详见 [TODO.md](./TODO.md)

- [ ] Step 4：用户认证 JWT
- [ ] Step 5：前端对接
- [ ] Step 6：Docker Compose 部署
- [ ] Step 7：爬虫数据采集
- [ ] Step 8：AI SQL 智能助手
