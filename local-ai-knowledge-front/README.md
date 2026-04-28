# local-ai-knowledge-front

本地AI知识库问答系统前端项目

## 技术栈

- Vue 3
- Vite
- TypeScript
- Pinia (状态管理)
- Element Plus (UI组件库)
- Axios (HTTP请求)
- Vue Router (路由管理)

## 功能特性

- 用户登录/注册
- 智能问答（RAG）
- 多轮对话
- 文档上传与管理
- 用户权限管理
- 响应式布局

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式启动

```bash
npm run dev
```

### 构建生产版本

```bash
npm run build
```

## 项目结构

```
src/
├── api/            # API接口定义
├── assets/         # 静态资源
├── components/     # 公共组件
├── layout/         # 布局组件
├── router/         # 路由配置
├── stores/         # Pinia状态管理
├── types/          # TypeScript类型定义
├── utils/          # 工具函数
└── views/          # 页面组件
    ├── admin/      # 管理员页面
    ├── auth/       # 认证页面
    ├── document/   # 文档管理页面
    ├── error/      # 错误页面
    └── rag/        # RAG问答页面
```

## 环境变量

创建 `.env.development` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080
```

## 后端对接说明

本前端项目对接的后端接口来自 `local-ai-knowledge` 项目，主要包括：

### 认证接口
- POST /auth/login - 用户登录
- POST /auth/register - 用户注册
- GET /auth/me - 获取当前用户信息

### 文档接口
- POST /api/doc/upload - 上传文档
- GET /api/doc/tasks - 获取文档列表
- GET /api/doc/status/{taskId} - 获取任务状态
- GET /api/doc/logs/{taskId} - 获取任务日志

### RAG问答接口
- POST /api/rag/agent/chat - 智能问答
- POST /api/rag/agent/chat/stream - 智能问答（SSE流式）
- POST /api/rag/multi-chat - 多轮对话
- POST /api/rag/multi-chat/stream - 多轮对话（SSE流式）
- GET /api/rag/sessions - 获取会话列表
- GET /api/rag/history/{sessionId} - 获取会话历史
- DELETE /api/rag/session/{sessionId} - 删除会话

### 管理员接口
- GET /api/admin/users - 获取用户列表
- PUT /api/admin/user/{id}/role - 分配角色
- PUT /api/admin/user/{id}/enabled - 启用/禁用用户

## 角色说明

- **普通用户**：可使用智能问答、多轮对话功能
- **管理员**：可额外使用文档上传管理、用户管理功能

## License

MIT
