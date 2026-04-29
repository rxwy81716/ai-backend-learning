# local-ai-knowledge 项目推进计划

> 基于 `docs/后续规划.md`，结合当前项目现状，列出在 local-ai-knowledge 中应该一步一步做的事情。

---

## 当前已完成

- [x] ES 向量存储（EsVectorStoreService + EsVectorSearchService）
- [x] Embedding 服务（MiniMax embo-01）
- [x] 文本清洗 + 切片工具
- [x] 文件上传 + 本地保存（DocumentController）
- [x] 异步 Tika 解析 + 向量入库（DocumentParseService）
- [x] 任务状态持久化到 PostgreSQL（document_task 表）
- [x] 操作日志记录（document_task_log 表）
- [x] Redisson 队列替代 @Async（持久化异步任务）
- [x] 文件上传流式写入优化（减少内存占用）
- [x] SystemPrompt 配置管理（DB 持久化 + Caffeine 缓存）
- [x] ChatMessage 模型 + ChatConversationMapper
- [x] RagService 完整 RAG 问答（单轮/多轮/同步/SSE流式）
- [x] RagController 全部接口（问答 + 会话管理 + Prompt 管理）
- [x] 多轮对话 Redis 热缓存（ChatHistoryCacheService，DB + Redis 双写）
- [x] Caffeine 本地缓存（SystemPrompt 10 分钟过期）
- [x] 文档用户归属隔离（userId + docScope: PRIVATE/PUBLIC）
- [x] 多智能体路由（RagAgentService：知识库 → 网络搜索降级）
- [x] WebSearchService（Tavily API 集成，可配置开关）

---

## Step 1：建表 + 验证持久化 ✅

```
✅ 在 PostgreSQL (vector_db) 中执行 db/document_task.sql 建表
✅ 启动项目，用 Postman/curl 测试：
    POST /api/doc/upload   (上传一个 PDF/TXT)
    GET  /api/doc/status/{taskId}
    GET  /api/doc/tasks
    GET  /api/doc/logs/{taskId}
✅ 验证：重启应用后 GET /api/doc/tasks 仍能看到历史任务
```

## Step 2：RAG 问答接口 ✅

```
✅ 创建 RagService：接收用户问题 → ES 向量检索 → 拼 Prompt → 调 LLM → 返回答案
✅ 创建 RagController：
    POST /api/rag/chat         单轮问答
    POST /api/rag/chat/stream  SSE 流式问答
✅ Prompt 模板：把检索到的文档片段作为上下文注入
✅ 返回结构包含 answer + references（来源引用）
✅ 抗幻觉：检索为空不调 LLM + SystemPrompt 严格约束 + 置信度评分
```

## Step 3：多轮对话 + Redis 历史 ✅

```
✅ DB 持久化会话（chat_conversation 表）+ Redis 热缓存（TTL 30min）
✅ POST /api/rag/multi-chat（带 sessionId）
✅ GET  /api/rag/history/{sessionId}  获取历史消息
✅ 滑动窗口策略：ChatContextUtil Token 裁剪
✅ 多智能体路由：POST /api/rag/agent/chat（知识库 → 网络搜索降级）
✅ 用户文档隔离：上传绑定 userId，检索按 doc_scope 过滤
```

## Step 4：用户认证 JWT（对应 Day29）✅

```
✅ 添加 spring-boot-starter-security + jjwt 依赖
✅ 创建 User 表 + UserService（BCrypt 加密）
✅ AuthController：POST /auth/register, POST /auth/login, GET /auth/me
✅ JwtAuthenticationFilter：解析 Token → SecurityContext
✅ SecurityConfig：/auth/** 放行，/api/admin/** 需 ROLE_ADMIN，其他需认证
✅ 文档上传、RAG 问答等接口绑定用户身份（SecurityUtil）
✅ RateLimitFilter 限流过滤器
✅ RBAC 权限体系（AdminController：用户/角色/菜单/智能体管理）
✅ UserController：根据角色返回动态菜单树
```

## Step 5：前端对接（对应 Day28/Day30/Day31）✅

```
✅ local-ai-knowledge-front（Vue3 + TS + Element Plus + Pinia）
✅ 登录/注册页面 + axios Token 拦截器 + 401 自动跳转
✅ 路由守卫（认证 + 角色权限校验）
✅ Layout 布局（侧栏 + 头部 + 面包屑 + 移动端适配）
✅ Pinia 用户 Store + 菜单 Store（动态菜单权限）
✅ 知识库管理页：
    - 文件上传组件（el-upload + 拖拽 + 文档范围选择）
    - 任务列表（状态标签 + 切片进度条）
    - 操作日志弹窗（任务详情 + 日志时间线）
    - 文档下载 / 删除（权限校验）
✅ RAG 问答页面：
    - SSE 流式展示（fetch + ReadableStream）
    - 知识库模式 / LLM 直答模式切换
    - Markdown 简单渲染
✅ 多轮对话 UI：
    - 左侧会话列表 + 右侧消息区
    - 新建/切换/删除会话
    - 移动端侧滑面板适配
✅ 管理后台（ROLE_ADMIN）：
    - 用户管理（CRUD + 角色分配 + 启禁用）
    - 角色管理（CRUD + 菜单权限绑定）
    - 菜单管理
    - 智能体管理（SystemPrompt CRUD + 设为默认）
✅ 403 / 404 错误页

✅ 小缺口（已补齐）：
    ✅ 答案下方引用来源卡片（SSE 流式推送 [META] 元数据 + 前端折叠卡片展示）
    ✅ 会话重命名（Redis 存储自定义标题 + PUT /api/rag/session/{id}/title）
    ✅ 文档上传后自动轮询解析状态（3秒轮询，全部完成自动停止）
    ✅ Token 到期前自动刷新（JWT 解析过期时间 + 到期前2小时自动续期）
    ✅ 个人中心页面（/profile 路由 + 使用统计）
    ✅ 文本切片优化（MAX_CHUNK_SIZE 500→800, OVERLAP_SIZE 50→100, RAG_TOP_K 5→8）
```

## Step 6：Docker Compose 部署（对应 Day32）⏭️ 跳过

```
⏭️ 当前架构不适用 Docker Compose：
    - PG 在远程服务器，Redis/ES/Ollama 在本地
    - 通过 ZeroTier 组网 + frp 内网穿透
    - Docker Compose 要求所有服务同机编排，与跨机器部署冲突
    - 服务器配置不足以承载全套服务

    未来条件满足时可启用（服务器升级 / 需要云部署演示）
```

## Step 7：爬虫数据采集（对应 Day33-38，模块 local-ai-crawler）✅ 已完成

```
✅ 新建 local-ai-crawler 模块（独立于 local-ai-knowledge）
✅ GitHub Trending + 微博热搜（Jsoup 静态页面入门）
✅ 知乎热榜 + B站热门（JSON API）
✅ 小红书 / 抖音（Playwright 动态页面，进阶）
✅ 清洗管道：去重(BloomFilter) → 清洗 → 向量化入库
✅ 定时调度（@Scheduled / XXL-Job）
✅ 爬取结果通过 API 调 local-ai-knowledge 入库
```

## Step 8：AI SQL 智能助手（对应 Day42-48，新模块 demo-ai-sql）

```
□ 新建 demo-ai-sql 模块
□ Schema 元数据采集 → RAG 入库
□ NL→SQL Prompt 设计（Few-Shot + Schema RAG）
□ JSqlParser SQL 校验四层防御
□ Druid 数据源 + SQL 防火墙
□ Caffeine + Redis 二级缓存
□ Sentinel 限流熔断
□ 审计日志（Kafka 异步落库）
□ 前端可视化（Monaco Editor + ECharts）
```

## Step 9：智能化（对应 Day39-41，远期）

```
□ 趋势分析：多源热点数据 + LLM 聚合分析
□ 邮件日报推送（定时任务 + LLM 总结）
□ Agent 化：自然语言 → 自动选择爬虫源 + 总结
```

---

## 优先级建议

| 优先级 | 步骤 | 原因 |
|:------|:-----|:-----|
| P0 | Step 1 建表验证 | 当前代码的前置条件 |
| P0 | Step 2 RAG 问答 | 项目核心能力，没有这个等于没有产品 |
| P1 | Step 3 多轮对话 | 提升体验，面试加分项 |
| ~~P1~~ | ~~Step 4 JWT 认证~~ | ✅ 已完成（含 RBAC 权限体系） |
| ~~P1~~ | ~~Step 5 前端对接~~ | ✅ 已完成（含管理后台 + 移动端适配） |
| ~~P2~~ | ~~Step 6 Docker 部署~~ | ⏭️ 跳过（跨机器架构不适用，远期再议） |
| ~~P2~~ | ~~Step 7 爬虫~~ | ✅ 已完成（local-ai-crawler 模块） |
| P3 | Step 8 AI SQL | 大特性，工作量大，可后做 |
| P3 | Step 9 智能化 | 远期目标 |
