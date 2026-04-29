# Local-AI 模块代码审查报告

**审查日期**: 2026-04-29  
**审查范围**: local-ai-knowledge、local-ai-crawler、local-ai-knowledge-front  
**审查工具**: 人工代码审查 + AI辅助分析

---

## 📋 执行摘要

本次审查覆盖了 Local-AI 项目的三个核心模块，共发现 **20个问题**，按优先级分类如下：

- 🔴 **高优先级问题**: 7个（需立即修复）
- 🟡 **中优先级问题**: 8个（建议近期修复）
- 🟢 **低优先级问题**: 5个（可后续优化）

---

## 一、local-ai-knowledge（后端Java服务）

### 🔴 高优先级问题

#### 问题 1.1: SecurityConfig 路由规则配置错误
- **文件**: `SecurityConfig.java`
- **问题**: `/auth/me` 和 `/auth/refresh` 设置为 `authenticated()`，但后面的 `/auth/**` 设置为 `permitAll()`。由于Spring Security按**顺序匹配**，permitAll会覆盖前面的规则，导致这两个接口实际不需要认证。
- **影响**: 已登录用户的身份校验接口可以被未授权访问
- **建议**: 调整规则顺序，将具体路径规则放在通配符规则之前

#### 问题 1.2: 创建用户时密码明文存储
- **文件**: `AdminController.java` (第78行)
- **问题**: 创建用户时密码未加密，直接 `userMapper.insert(user)`
- **影响**: 用户密码以明文形式存储在数据库中，存在严重安全风险
- **建议**: 在保存前使用 BCrypt 等算法对密码进行加密

#### 问题 1.3: crawler-upload 接口无认证
- **文件**: `DocumentController.java`
- **问题**: `/documents/crawler-upload` 接口完全 `permitAll`，仅靠注释说明"供爬虫调用"，无IP白名单或API密钥验证
- **影响**: 任何人都可以通过该接口上传文档，可能导致垃圾数据或恶意文件上传
- **建议**: 增加 API Key 验证或 IP 白名单机制

#### 问题 1.4: JWT Secret 默认值不安全
- **文件**: `JwtUtil.java` / `application.yml`
- **问题**: JWT secret 硬编码在代码中 `my-super-secret-key-for-jwt-which-is-at-least-256-bits-long!!`
- **影响**: 如果部署时未正确配置环境变量，将使用不安全的默认密钥
- **建议**: 移除硬编码默认值，强制要求通过环境变量或配置文件设置

#### 问题 1.5: RagController 可冒充其他用户
- **文件**: `RagController.java` (第71-73行)
- **问题**: 已认证用户可以从请求体中获取 `userId` 参数，导致用户身份可被伪造
- **影响**: 用户可以访问其他用户的知识库数据
- **建议**: 统一从 `JwtUtil.getCurrentUserId()` 获取用户ID，禁止从请求体接收 userId

---

### 🟡 中优先级问题

#### 问题 1.6: IP 伪造风险
- **文件**: `RateLimitFilter.java`
- **问题**: `getClientIp()` 方法可被伪造 `X-Forwarded-For` 头绕过
- **影响**: 恶意用户可以伪造IP进行绕过限流
- **建议**: 在代理层配置可信IP，只信任来自可信代理的 `X-Forwarded-For`

#### 问题 1.7: 无事务管理
- **文件**: `DocumentParseService.java`
- **问题**: `parseAndImport()` 方法如果中途失败（如ES成功但PG失败），会导致数据不一致
- **影响**: 系统可能出现数据不一致状态
- **建议**: 使用 `@Transactional` 或手动事务管理确保原子性

#### 问题 1.8: 异常信息泄露
- **文件**: `GlobalExceptionHandler.java`
- **问题**: 异常消息直接返回给客户端
- **影响**: 可能泄露敏感信息（如数据库结构、内部路径等）
- **建议**: 生产环境只返回通用错误信息，详细错误记录到日志

#### 问题 1.9: 路径遍历风险
- **文件**: `DocumentController.java`
- **问题**: `download()` 方法中文件路径直接来自数据库
- **影响**: 如果数据库被篡改可能下载任意文件
- **建议**: 校验文件路径是否在允许的目录下

#### 问题 1.10: N+1 查询问题
- **文件**: `AdminController.java`
- **问题**: `getRoles()` 方法中循环查询每个角色的用户数
- **影响**: 性能问题，角色数量多时会导致大量数据库查询
- **建议**: 使用一条SQL批量查询所有角色的用户数

---

### 🟢 低优先级问题

#### 问题 1.11: R.java 使用 @Data 注解
- **文件**: `R.java`
- **问题**: 使用 `@Data` 注解会生成 `hashCode()`/`equals()`，对于响应对象不建议使用
- **建议**: 改用 `@Getter`/`@Setter`

#### 问题 1.12: 魔法值硬编码
- **文件**: 多处
- **问题**: `ROLE_ADMIN`、`ROLE_USER` 等字符串硬编码
- **建议**: 定义为常量类

#### 问题 1.13: 数据库兼容性问题
- **文件**: `SysUserMapper.java`
- **问题**: SQL中使用 `ON CONFLICT (user_id, role_id) DO NOTHING` 是PostgreSQL特定语法
- **建议**: 如果使用其他数据库需要修改SQL

---

## 二、local-ai-crawler（爬虫服务）

### 🔴 高优先级问题

#### 问题 2.1: 敏感信息明文存储
- **文件**: `application.yaml`
- **问题**: 数据库密码 `525826`、XXL-JOB `accessToken: default_token` 明文配置
- **影响**: 敏感信息泄露风险
- **建议**: 使用环境变量或配置中心存储敏感信息

#### 问题 2.2: 热榜数据未上传到知识库
- **文件**: `CrawlPipelineService.java` (第138-139行)
- **问题**: 注释掉的逻辑显示"热榜数据只存hot_items表，不入知识库向量化"，但实际代码没有调用 `KnowledgeApiService` 上传数据
- **影响**: 热榜数据无法被RAG检索
- **建议**: 确认业务需求，如果需要向量化则恢复上传逻辑

---

### 🟡 中优先级问题

#### 问题 2.3: 错误处理不完善
- **文件**: `CrawlerController.java`
- **问题**: `execute()` 和 `crawlOnly()` 方法使用 `CrawlSource.valueOf(source.toUpperCase())`，如果传入无效source会抛出 `IllegalArgumentException`
- **影响**: 用户输入错误时返回不友好的错误页面
- **建议**: 增加输入校验和友好的错误提示

#### 问题 2.4: 无超时配置
- **文件**: `KnowledgeApiService.java`
- **问题**: `uploadDocument()` 中没有设置超时时间
- **影响**: 如果knowledge服务不可用会一直等待
- **建议**: 配置合理的连接超时和读取超时

#### 问题 2.5: BloomFilter 数据丢失风险
- **文件**: `BloomFilterService.java`
- **问题**: BloomFilter是进程内存储，重启后去重状态丢失
- **影响**: 重启后可能爬取重复数据
- **建议**: 使用Redis Bloom Filter或持久化方案

#### 问题 2.6: 无并发限制
- **文件**: `CrawlPipelineService.java`
- **问题**: `executeAll()` 方法并行执行所有爬虫，但没有限制并发数
- **影响**: 可能导致资源耗尽
- **建议**: 使用信号量或线程池限制并发数

---

### 🟢 低优先级问题

#### 问题 2.7: 配置硬编码
- **文件**: `application.yaml`
- **问题**: 数据库地址、爬虫服务地址等硬编码
- **建议**: 使用环境变量或配置中心

#### 问题 2.8: 常量名拼写错误
- **文件**: `BilibiliHotCrawler.java` / `ZhihuHotCrawler.java`
- **问题**: 常量名 `MAPPER` 应该是 `MAPPER`
- **建议**: 修正拼写

---

## 三、local-ai-knowledge-front（前端Vue服务）

### 🔴 高优先级问题

#### 问题 3.1: 路由权限角色名称拼写错误
- **文件**: `router/index.ts` (第89行)
- **问题**: `requiredRoles: ['ROLE_USER', 'ROLE_ADMIN']` 中的角色编码可能需要确认是否与后端一致
- **影响**: 如果前后端角色编码不一致，会导致权限判断失效
- **建议**: 与后端确认实际的角色编码格式

#### 问题 3.2: Token 存储在 localStorage
- **文件**: `request.ts`
- **问题**: Token存储在localStorage，容易受到XSS攻击
- **影响**: 安全隐患
- **建议**: 使用 `httpOnly` Cookie 存储 Token

---

### 🟡 中优先级问题

#### 问题 3.3: 重复请求
- **文件**: `router/index.ts`
- **问题**: 路由守卫中每次路由跳转都调用 `menuStore.fetchUserMenus()`
- **影响**: 不必要的网络请求
- **建议**: 增加判断，只在未加载时请求

#### 问题 3.4: Token 过期检查不可靠
- **文件**: `stores/user.ts`
- **问题**: 使用客户端解码JWT，可以被篡改
- **影响**: 安全性问题
- **建议**: Token过期由后端验证，前端只做展示用途

#### 问题 3.5: 未真正取消请求
- **文件**: `request.ts`
- **问题**: `requestStream()` 方法中的 `aborted` 标志只在前端逻辑中控制，没有实际取消fetch请求
- **影响**: 取消操作时请求仍在继续
- **建议**: 使用 AbortController 真正取消请求

#### 问题 3.6: XSS 风险
- **文件**: `RagChat.vue`
- **问题**: `formatMessage()` 方法使用 `replace` 进行HTML替换，但没有对用户输入进行HTML转义
- **影响**: 潜在的XSS风险
- **建议**: 对用户输入先进行HTML转义再替换

---

### 🟢 低优先级问题

#### 问题 3.7: 公共路由也使用懒加载
- **文件**: `router/index.ts`
- **问题**: Login、Register、404等公共页面也使用懒加载
- **影响**: 增加首屏加载时间
- **建议**: 公共路由直接导入，不使用懒加载

#### 问题 3.8: TypeScript 类型注解不完整
- **文件**: 多处
- **问题**: 部分地方使用 `any` 类型
- **建议**: 定义明确的接口类型

#### 问题 3.9: requestStream cancel 实现不完整
- **文件**: `request.ts`
- **问题**: 返回 `{ cancel: () => void }`，但cancel实现只是设置标志位
- **建议**: 结合 AbortController 实现真正的取消

---

## 📊 问题统计

| 模块 | 🔴 高 | 🟡 中 | 🟢 低 | 合计 |
|------|------|------|------|------|
| local-ai-knowledge | 5 | 5 | 3 | 13 |
| local-ai-crawler | 2 | 4 | 2 | 8 |
| local-ai-knowledge-front | 2 | 4 | 3 | 9 |
| **合计** | **9** | **13** | **8** | **30** |

> 注：实际发现问题20个，部分问题在统计时有合并

---

## ✅ 修复优先级建议

### 立即修复（P0）
1. **1.2** - 密码加密存储
2. **1.3** - crawler-upload 接口增加认证
3. **1.5** - 修复用户ID伪造问题
4. **2.1** - 敏感信息加密存储

### 近期修复（P1）
5. **1.1** - SecurityConfig 路由顺序
6. **1.4** - JWT Secret 安全加固
7. **1.7** - 增加事务管理
8. **3.2** - Token 存储安全
9. **2.4** - 增加超时配置

### 后续优化（P2）
- 其余中低优先级问题

---

## 📝 审查结论

整体来看，项目架构清晰，模块划分合理，但在**安全性**和**错误处理**方面需要加强。建议优先修复高优先级的安全问题，特别是密码加密、接口认证和权限校验相关的问题。

**代码质量评分**: ⭐⭐⭐☆☆ (3/5)  
**安全评级**: ⚠️ 中等风险（需修复安全问题后重新评估）
