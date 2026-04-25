# demo-springai-front

Spring AI RAG 系统的前端项目，对接 `demo-springai` 后端。

## 技术栈

- **Vue 3.5** + **Vite 5** + **TypeScript 5.6**
- **Element Plus 2.8** UI 组件库
- **Vue Router 4** 路由
- **Pinia** 状态管理
- **axios** HTTP 客户端
- **@microsoft/fetch-event-source** SSE 流式接收
- **markdown-it + highlight.js** Markdown 渲染 + 代码高亮

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 启动后端

确保 `demo-springai` 后端已启动（端口 12115）。

### 3. 启动前端

```bash
npm run dev
```

浏览器自动打开 http://localhost:5173

### 4. 打包构建

```bash
npm run build
```

产物在 `dist/` 目录。

## 功能

- ✅ 知识问答（流式 SSE）
- ✅ 知识库管理（上传/列表/删除）
- ⏳ 用户登录（Day29 接入）
- ⏳ 多轮对话（已有后端，前端 Day29 接入）

## 接口对接

前端通过 Vite Proxy 把 `/api/**` 代理到后端 `http://localhost:12115`，
开发环境无需后端配 CORS（已经配了 `WebMvcConfig` 双保险）。

## 项目结构

```
src/
├── api/          后端接口封装
├── components/   公共组件（消息气泡、Markdown 渲染等）
├── layouts/      布局（侧边栏 + 路由出口）
├── router/       路由配置
├── stores/       Pinia 状态管理
├── styles/       全局样式
├── utils/        工具函数（SSE 封装等）
├── views/        页面组件
├── App.vue       根组件
└── main.ts       入口
```
