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
✅ HotSearchService：热榜关键词检测 + 来源识别 + 结构化上下文注入
✅ RagAgentService 三路路由（知识库 → 热榜 → 网络搜索）
```

---

# 🚀 后续推荐功能（让产品不再"简陋"）

> 当前 RAG 全链路已通：上传 → 解析 → 向量 → 多智能体 → 流式 → 多轮 → 鉴权 → 前端。
> 下一阶段的核心是：**提升检索质量、丰富使用场景、强化可观测性、增加差异化亮点**。

## 🥇 P0 — 直接拉高产品质感（投入小、收益大）

### F1. 检索质量升级（RAG 真正落地）
```
✅ Iter1 混合检索：ES BM25 + 向量召回（RRF 融合，CompletableFuture 并发 + 超时降级）
   - EsKeywordSearchService（BM25 + 用户归属过滤）
   - HybridSearchService（双路并发 + RRF k=60 融合 + 元数据回写）
   - 配置开关 app.rag.hybrid.enabled
   - ik_max_word 中文分词迁移脚本 db/es_index_ik.md
□ Iter2 Query Rewriting：LLM 改写用户问题（多轮指代消解 / 关键词扩展）
□ Iter3 Rerank 重排：BGE-Reranker 或 Cohere Rerank API 二阶段精排
□ Iter4 Parent-Child 切片：召回小片段 → 返回完整段落上下文
□ Iter5 检索召回评估接口：/api/rag/eval（人工标注 + recall@k）
```
> 简历亮点详见 `docs/简历亮点-RAG检索质量升级.md`
> 这是 **RAG 项目的天花板**，比堆其他功能更值钱。

### F2. 文档增强（让"上传文档"不再是终点）
```
□ 上传后自动生成：摘要 / 关键词 / 自动打标签（LLM 一次调用）
□ 文档列表展示摘要卡片，而不只是文件名
□ 文档全文搜索（关键词 + 高亮）
□ 文档版本管理：同名文件覆盖时保留历史版本 + Diff
□ 文档分组 / 知识库（一个用户多个独立 KB）
```

### F3. 对话体验
```
□ 答案"踩/赞"反馈 → chat_feedback 表 → 后续微调数据集
□ 对话导出：Markdown / PDF
□ 引用溯源点击：点击 [1] 跳转到原文档片段并高亮
□ 推荐追问：LLM 根据上下文生成 3 个 follow-up
□ Stop / 重新生成按钮
□ 代码块语法高亮（highlight.js / shiki）+ 复制按钮
```

---

## 🥈 P1 — 差异化亮点（面试 / 演示加分）

### F4. 数据可视化大屏（结合已有爬虫数据）
```
□ 热榜趋势分析页（ECharts）
   - 多平台 Top10 对比柱状图
   - 关键词词云（jieba + wordcloud）
   - 24h / 7d 热度变化折线
□ "今日热点 AI 解读"：定时 LLM 聚合各平台热榜 → 生成日报
□ 邮件订阅日报（Spring Mail + 模板）
```

### F5. Function Calling / MCP 工具调用
```
□ 接入 Spring AI Tool Calling
   - 计算器 / 天气 / 时间 / URL 抓取
   - 调用本地 SQL（连 demo-ai-sql 模块）
□ Agent 自主选择工具 → 形成"思考-行动-观察"循环
□ 前端展示工具调用过程（类似 Cursor / Claude）
```

### F6. 多模态
```
□ 图片 OCR：上传图片 → Tesseract / PaddleOCR → 入向量库
□ 图片理解：MiniMax / GPT-4V 直接读图问答
□ 语音输入：Web Speech API → STT → 提问
□ TTS 朗读答案
```

---

## 🥉 P2 — 工程化 & 可观测性（生产级标配）

### F7. 监控 & 审计
```
□ Micrometer + Prometheus + Grafana：QPS / 延迟 / Token 消耗
□ SkyWalking 链路追踪
□ 慢查询日志 + 异常告警（钉钉 / 飞书 webhook）
□ 用户行为审计表（who-when-what）
□ Token 消耗统计 + 配额限制（按用户 / 按角色）
```

### F8. 性能 & 稳定性
```
□ Embedding 批量缓存（同一文本不重复调 API）
□ 向量入库队列限流（避免 ES 压垮）
□ 失败任务重试机制（Redisson 死信队列）
□ ES 索引别名 + 平滑重建
□ 接口幂等（防止重复上传 / 重复提问）
```

### F9. 多租户 & 协作
```
□ 团队工作区（Workspace）：多用户共享知识库
□ 文档分享链接（带 Token，有过期时间）
□ 评论 / 笔记：在文档片段上做标注
□ 邀请成员 + 角色（Owner / Editor / Viewer）
```

---

## 🛸 P3 — 远期 & 大模块（独立项目级）

### Step 8：AI SQL 智能助手（对应 Day42-48，新模块 demo-ai-sql）
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

### Step 9：开放平台 / OpenAPI
```
□ API Key 管理（用户生成 sk-xxx）
□ 兼容 OpenAI Chat Completions 协议（让第三方客户端能直接连）
□ 嵌入式 SDK：iframe / JS Widget，贴到任意网站做客服机器人
□ Webhook：解析完成 / 答案生成时回调
```

### Step 10：本地化 & 离线
```
□ Ollama 本地 LLM（qwen / deepseek）一键切换
□ 本地 Embedding（bge-m3）摆脱 MiniMax API
□ 完全离线包（Docker Compose / 一键脚本）
```

---

## 优先级建议（更新版）

| 优先级 | 模块 | 价值 | 工作量 |
|:------|:-----|:-----|:-----|
| **P0** | F1 检索质量（混合检索+Rerank） | ⭐⭐⭐⭐⭐ | 中 |
| **P0** | F2 文档增强（摘要/标签） | ⭐⭐⭐⭐ | 小 |
| **P0** | F3 对话体验（反馈/引用跳转/导出） | ⭐⭐⭐⭐ | 小 |
| P1 | F4 热点大屏 + AI 日报 | ⭐⭐⭐⭐ | 中 |
| P1 | F5 Function Calling / MCP | ⭐⭐⭐⭐⭐ | 中 |
| P1 | F6 多模态（OCR/语音） | ⭐⭐⭐ | 中 |
| P2 | F7 监控审计 | ⭐⭐⭐ | 中 |
| P2 | F8 性能稳定性 | ⭐⭐⭐ | 小 |
| P2 | F9 多租户协作 | ⭐⭐⭐ | 大 |
| P3 | Step 8 AI SQL | ⭐⭐⭐⭐ | 大 |
| P3 | Step 9 OpenAPI | ⭐⭐⭐ | 中 |
| P3 | Step 10 本地化离线 | ⭐⭐ | 中 |

---

## 🎯 我的推荐路径

如果只挑 3 件事做，按这个顺序：

1. **F1 混合检索 + Rerank** — RAG 项目的核心竞争力，没做约等于"会调 API"
2. **F5 Function Calling** — 当前最热的 Agent 方向，演示效果炸裂
3. **F4 热点大屏 + AI 日报** — 把已经做的爬虫数据"用起来"，闭环 + 视觉冲击力
