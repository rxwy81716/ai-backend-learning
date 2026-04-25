# Day28 前端项目搭建（Vue3 + Vite + Element Plus）

> 衔接 Day26/Day27 后端，本节用 IDEA + Node 20 LTS 搭建前端项目，
> 实现知识问答 + 知识库管理两大核心界面，登录页留占位。

------

## 一、技术选型与项目结构

### 1. 为什么选 Vue 3 + Vite + Element Plus

```
对 Java 后端转全栈最友好的组合：

  Vue 3        --> 模板语法接近 HTML，比 React 易上手
  Vite         --> 启动 1 秒，热更新极快（Java 程序员体验过 Webpack 都懂）
  TypeScript   --> 类型系统类比 Java 强类型，团队协作清晰
  Element Plus --> 中文社区最成熟的企业级 UI 库（饿了么出品）
  IDEA Ultimate--> 原生支持 Vue + TS，免装插件

放弃 React 的原因（针对 Java 后端学习者）：
  - JSX 语法对 Java 开发者陌生
  - shadcn/ui 配 TailwindCSS 学习曲线陡
  - 中文文档和案例少
```

### 2. 整体目录结构

```
demo-springai-front/
├── package.json                包管理 + 脚本
├── vite.config.ts              Vite 配置（端口、代理、别名）
├── tsconfig.json               TS 主配置
├── tsconfig.node.json          TS Node 配置（vite.config 用）
├── index.html                  入口 HTML
├── .env.development            开发环境变量
├── .gitignore                  Git 忽略
├── README.md                   说明文档
└── src/
    ├── main.ts                 入口（注册 Vue + 路由 + Pinia + Element）
    ├── App.vue                 根组件（空壳，只放 RouterView）
    ├── env.d.ts                环境变量类型声明
    ├── api/                    后端接口封装
    │   ├── request.ts          axios 实例 + 拦截器
    │   ├── rag.ts              RAG 问答接口
    │   └── kb.ts               知识库管理接口
    ├── components/             公共组件
    │   └── MarkdownRenderer.vue Markdown + 代码高亮
    ├── layouts/                布局
    │   └── MainLayout.vue      侧边栏 + 顶栏 + 路由出口
    ├── router/                 路由
    │   └── index.ts
    ├── stores/                 Pinia（暂时为空，Day29 加用户状态）
    ├── styles/                 全局样式
    │   └── main.scss
    ├── utils/                  工具函数
    │   └── sse.ts              SSE 流式封装（核心）
    └── views/                  页面
        ├── ChatView.vue        知识问答（核心）
        ├── KnowledgeBaseView.vue 知识库管理
        └── LoginView.vue       登录占位
```



------

## 二、IDEA 项目创建流程（手把手）

### 1. 准备工作

```
确认环境：
  ✓ Node.js 20 LTS（你已经有）
  ✓ IDEA Ultimate（社区版也可以，但 Vue 支持差一些）
  ✓ 后端 demo-springai 已启动（12115 端口）
```

### 2. 第一步：用 IDEA 打开根目录

```
1. IDEA --> File --> Open
2. 选择 d:\work\ai-backend-learning（你的项目根目录）
3. IDEA 自动识别为多模块项目
```

### 3. 第二步：在 IDEA 终端创建前端项目

```
打开 IDEA 内置终端（View --> Tool Windows --> Terminal）
确认终端当前目录是 d:\work\ai-backend-learning

执行（注意：本节我已经把所有文件直接放在 demo-springai-front 目录，
     你只需要进入目录安装依赖即可）：
```

```bash
cd demo-springai-front
npm install
```

```
npm install 会自动读 package.json 安装所有依赖，
首次安装大约 30~60 秒（取决于网速）。

国内网络慢的话切换淘宝镜像：
  npm config set registry https://registry.npmmirror.com
```

### 4. 第三步：把 demo-springai-front 标记为 IDEA 模块

```
1. IDEA --> File --> Project Structure --> Modules
2. 点击 + --> Import Module --> 选 demo-springai-front
3. 选择 "Create module from existing sources" --> 一直下一步

完成后，IDEA 左侧会把 demo-springai-front 显示为独立模块，
TypeScript 智能提示、跳转都正常。
```

### 5. 第四步：启动前端

```bash
# 在 demo-springai-front 目录
npm run dev
```

```
看到这样的输出代表成功：

  VITE v5.4.9  ready in 1234 ms
  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose

浏览器自动打开 http://localhost:5173/
应该看到登录页（紫色渐变背景），随便输入账密点登录，
进入主界面（左侧导航 + 知识问答页）。
```

### 6. 在 IDEA 创建 npm 启动配置（可选但推荐）

```
1. IDEA 右上角 Run 配置下拉 --> Edit Configurations
2. 点 + --> npm
3. 配置：
   Name:     Front Dev
   package.json: demo-springai-front/package.json
   Command:  run
   Scripts:  dev
4. 保存

之后点 IDEA 右上角的绿色三角（或 Shift+F10）就能启动前端，
比敲命令省事。
```

### 7. 同时启动前后端的最佳实践

```
1. 后端：用 IDEA 启动 DemoSpringaiApplication（12115 端口）
2. 前端：用上面的 npm 配置启动（5173 端口）
3. 浏览器访问 http://localhost:5173

两个服务在 IDEA 不同 Tab，互不干扰。
```



------

## 三、关键配置解析

### 1. vite.config.ts 代理（解决跨域）

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:12115',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, '')
    }
  }
}
```

```
原理：
  浏览器访问 http://localhost:5173/api/rag/chat
  Vite 把 /api 替换为空，转发到 http://localhost:12115/rag/chat
  --> 浏览器看到的是同源请求，不触发 CORS
  --> 后端实际接收的还是 /rag/chat，不需要改 Controller

为什么不直接 fetch http://localhost:12115？
  --> 浏览器会拦截跨域请求
  --> 即使后端配了 CORS，开发期间也会有 OPTIONS 预检请求增加复杂度

注意：生产环境部署时，前端打包后的静态资源由 Nginx 服务，
     Nginx 配置类似的 proxy_pass 转发到后端。
```

### 2. 后端 CORS 双保险（WebMvcConfig）

```java
// demo-springai/src/main/java/com/jianbo/springai/config/WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

```
为什么 Vite 已经代理了还要配后端 CORS？
  --> 双保险：万一前端不通过 Vite Proxy 直接访问后端（如手机 H5、
     小程序、其他第三方调用），后端要能直接处理跨域。
  --> 生产环境前端可能部署到其他域名，后端必须支持 CORS。

生产环境注意：
  allowedOriginPatterns("*") 太宽松，应改为具体域名：
    .allowedOriginPatterns("https://yourapp.com")
```

### 3. TypeScript 路径别名（@/ 简写）

```typescript
// vite.config.ts
resolve: {
  alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
}

// tsconfig.json
"paths": { "@/*": ["src/*"] }
```

```
两处都要配：
  vite.config.ts --> 让运行时解析 @/api/rag
  tsconfig.json  --> 让 TS 编译器和 IDE 智能提示识别 @

效果：
  // 不用 @
  import rag from '../../../api/rag'

  // 用 @（推荐）
  import rag from '@/api/rag'

类比 Java 的 import com.jianbo.xxx --> 都是固定根，省心。
```

### 4. 环境变量 .env.development

```
VITE_API_BASE=/api
```

```
前端代码里通过 import.meta.env.VITE_API_BASE 读取。

为什么 Vite 必须加 VITE_ 前缀？
  --> 安全考虑，防止误把后端密钥之类的环境变量打包进前端
  --> 只有 VITE_ 开头的才会被打包到客户端

生产环境用 .env.production：
  VITE_API_BASE=https://api.yourapp.com
```



------

## 四、核心代码解析

### 1. main.ts —— Vue 启动入口

```typescript
const app = createApp(App)

// 注册 Element Plus 全部图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

app.use(createPinia())   // 状态管理
app.use(router)          // 路由
app.use(ElementPlus)     // UI 组件
app.mount('#app')
```

```
类比 Spring Boot 的 SpringApplication.run(...)：

  Spring Boot                    Vue
  ────────────                   ────────
  @SpringBootApplication         createApp()
  自动装配 Bean                   app.use() 注册插件
  application.yaml 配置           import.meta.env 读取
  main() 启动                     app.mount('#app') 挂载
```

### 2. SSE 流式接收（最难点）

```typescript
// src/utils/sse.ts 关键片段
import { fetchEventSource } from '@microsoft/fetch-event-source'

await fetchEventSource(url, {
  method: 'GET',
  headers: { Accept: 'text/event-stream' },
  signal,                       // 用于取消
  openWhenHidden: true,         // 切到后台标签不断开

  onmessage: (ev) => {
    if (ev.data) onMessage(ev.data)  // ev.data 就是后端推送的文本片段
  },
  onclose: () => onDone?.(),
  onerror: (err) => { onError?.(err); throw err }
})
```

```
为什么不用浏览器原生的 EventSource？

  原生 EventSource 限制：
    ✗ 只支持 GET（不支持 POST）
    ✗ 不支持自定义 Header（不能带 JWT Token）
    ✗ 断网自动重连，无法精细控制

  @microsoft/fetch-event-source 优势：
    ✓ 支持任意 HTTP method
    ✓ 支持自定义 Header（Day29 加 Token 认证用得上）
    ✓ 支持 AbortController 主动取消（用户点"停止"按钮）
    ✓ 支持 openWhenHidden（切后台不断开）

后端约定（来自 Day26 RagController）：
  @GetMapping(value = "/chat-stream",
              produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> chatStream(...)

  produces = TEXT_EVENT_STREAM_VALUE 是关键：
    Spring MVC 看到这个会自动把 Flux<String> 序列化为 SSE 协议：
      data: 第一段\n\n
      data: 第二段\n\n
      ...
```

### 3. ChatView 流式接收实现

```typescript
async function send() {
  // 1. 添加用户消息
  messages.value.push({ role: 'user', content: text })
  // 2. 添加 AI 占位消息（loading 状态）
  const aiMsg = { role: 'assistant', content: '', loading: true }
  messages.value.push(aiMsg)

  // 3. 流式接收，每收到一段就追加到 aiMsg.content
  abortController = new AbortController()
  await streamSSE({
    url: ragStreamUrl(text),
    signal: abortController.signal,
    onMessage: (chunk) => {
      aiMsg.loading = false
      aiMsg.content += chunk         // <-- Vue 响应式自动更新视图
      scrollToBottom()
    },
    onDone: () => { loading.value = false },
    onError: (err) => { /* ... */ }
  })
}
```

```
关键点：

1. Vue 3 响应式：
   aiMsg.content += chunk
   --> Vue 自动检测变化，触发 MarkdownRenderer 重新渲染
   --> 用户看到逐字打印效果

2. AbortController 实现"停止"按钮：
   stop() {
     abortController?.abort()
     --> fetchEventSource 检测到 signal.aborted=true
     --> 抛出 AbortError，关闭 SSE 连接
     --> 节省后端 Token 消耗
   }

3. 自动滚动到底部：
   每收到一段就 scrollToBottom()
   --> 跟 ChatGPT 一样的体验

4. 占位消息 loading 状态：
   收到第一个字之前显示 "思考中..."
   收到第一个字后切换为正常渲染
```

### 4. MarkdownRenderer 流式安全渲染

```typescript
// src/components/MarkdownRenderer.vue
const html = computed(() => md.render(props.content || ''))
```

```
为什么用 computed？
  --> Vue 响应式：props.content 变化，html 自动重算
  --> 配合上面 aiMsg.content += chunk，
     用户每收到一个字符，Markdown 都会重新渲染

为什么 html: false（不渲染原始 HTML）？
  --> 安全：防止大模型返回的内容里有恶意 <script>
  --> markdown-it 默认就是 false，但显式写出来更清晰

代码高亮：
  hljs.highlight(code, { language: lang })
  --> 后端返回 ```java ... ``` 时自动高亮 Java 代码
  --> 配合 main.ts 引入的 atom-one-dark 主题
```

### 5. KnowledgeBaseView 文件读取

```typescript
function handleFileChange(file: any) {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.content = String(e.target?.result || '')
    if (!form.value.source) form.value.source = file.name
  }
  reader.readAsText(file.raw)
  return false  // 阻止 el-upload 自动上传
}
```

```
为什么不直接 multipart/form-data 上传文件？
  --> 后端 KnowledgeBaseController.upload 接收的是 JSON（UploadDTO）
  --> 内容是文本，没必要走 multipart

实现思路：
  1. 用户选文件 --> el-upload 触发 on-change
  2. FileReader 在浏览器端读取文件文本
  3. 填充到 form.content
  4. 用户点"确定上传" --> POST JSON 给后端

支持的格式：accept=".txt,.md,.json"
  二进制文件（PDF/Word）需要后端 Tika 解析，留到 Day29 升级
```



------

## 五、端到端联调测试

### 1. 启动顺序

```
1. PostgreSQL（5432）  --> 已启动
2. Redis（6379）        --> 已启动
3. demo-springai 后端   --> IDEA 运行 DemoSpringaiApplication
4. demo-springai-front  --> npm run dev
```

### 2. 测试场景一：知识库上传

```
1. 浏览器访问 http://localhost:5173
2. 登录页随便输入账密 --> 登录
3. 左侧导航选 "知识库管理"
4. 点 "上传文档"，填写：
   source:  java-intro.md
   title:   Java 入门
   content: Java是一门面向对象的编程语言，由Sun公司在1995年推出...
5. 点 "确定上传"
6. 等几秒（后端要做切片+向量化），看到 "上传成功！共生成 X 个片段"
7. 列表刷新，新文档出现
```

### 3. 测试场景二：RAG 流式问答

```
1. 左侧导航选 "知识问答"
2. 输入框输入：Java是什么语言？
3. 按 Enter 发送
4. 观察：
   ✓ 立刻显示用户消息气泡
   ✓ AI 气泡先显示 "思考中..."
   ✓ 大约 1~2 秒后开始逐字打印答案
   ✓ 答案带 [1] [2] 来源标注（System Prompt 约束生效）
   ✓ 代码块自动高亮（如果答案有代码）

5. 测试 "停止" 按钮：
   再发一个问题，在生成中点 "停止"
   --> 立即停止，AbortController 生效
```

### 4. 测试场景三：边界情况

```
□ 空召回：问一个知识库没有的问题（如"今天天气"）
  --> 应返回兜底文案，不调用大模型
  --> 后端日志：召回为空, 直接返回兜底文案

□ 超长问题：输入几千字
  --> Token 预算管理生效，Context 自动裁剪
  --> 后端日志：Context裁剪完成 | 原始 N 条 --> 保留 M 条

□ 并发：开两个浏览器 Tab，同时问问题
  --> 都能正常流式返回
  --> 后端日志看到两个独立请求

□ 网络中断：后端 stop 后再发问题
  --> 应显示 "请求失败" 错误提示
```



------

## 六、常见排错

### 1. npm install 报错

```
症状：npm install 卡住或报 ECONNRESET

解决：
  1. 切淘宝镜像：
     npm config set registry https://registry.npmmirror.com
  2. 清缓存重试：
     npm cache clean --force
     rm -rf node_modules package-lock.json
     npm install
  3. 检查 Node 版本是否 >= 18：
     node -v
```

### 2. 启动后页面空白

```
症状：浏览器打开 http://localhost:5173 一片空白

排查：
  1. F12 看 Console 报错
     如 "Cannot find module" --> 依赖没装好，重新 npm install
     如 "createApp is not a function" --> Vue 版本不对
  2. 看终端 Vite 输出有没有错误
  3. 清浏览器缓存（Ctrl+Shift+R）
```

### 3. 流式接口不工作（一次性返回）

```
症状：知识问答时，等几秒后一次性显示完整答案，不是逐字

排查：
  1. F12 --> Network 选项卡，找 chat-stream 请求
     看 Response Headers 中 Content-Type 是否为 text/event-stream
     如果是 application/json --> 后端 Controller produces 没设对

  2. 看 Network 的 EventStream 选项卡
     应该看到一个个 data: 推送
     如果是空的 --> 后端没真正流式返回（可能是 buffer 了）

  3. 检查 Vite Proxy 是否禁用 buffering：
     Vite 默认透传 SSE，不需要额外配置
     如果用 Nginx，需要：
       proxy_buffering off;
       proxy_cache off;
```

### 4. CORS 跨域错误

```
症状：F12 Console 显示
  "Access to fetch ... has been blocked by CORS policy"

解决：
  1. 检查 Vite Proxy 配置（vite.config.ts）
  2. 前端代码是否用了 /api 前缀（不能直接 http://localhost:12115）
  3. 后端 WebMvcConfig 是否生效（重启后端）
  4. 浏览器硬刷新（Ctrl+Shift+R）
```

### 5. TypeScript 报错"找不到模块 vue"

```
症状：IDE 大量红色波浪线，提示找不到模块

原因：
  npm install 还没跑，node_modules 不存在

解决：
  1. cd demo-springai-front
  2. npm install
  3. IDEA 重启（File --> Invalidate Caches and Restart）
```



------

## 七、面试要点总结

### 1. 前后端通信方式（背 3 种）

```
1. 普通 JSON：axios.get/post --> 适用绝大部分接口
2. SSE（Server-Sent Events）--> 流式 AI 回答（本节核心）
3. WebSocket --> 双向实时（如多人协作，本项目暂不需要）

SSE vs WebSocket：
  SSE 单向（服务端 --> 客户端），HTTP 协议，浏览器兼容好
  WebSocket 双向，自定义协议，需服务端支持
  RAG 场景只需要服务端推答案 --> SSE 完全够用
```

### 2. SSE 前端实现核心（背 3 点）

```
1. 用 @microsoft/fetch-event-source 而不是原生 EventSource
   原因：支持 POST + 自定义 Header + AbortController

2. AbortController 实现停止：
   const ctrl = new AbortController()
   fetch({ signal: ctrl.signal })
   ctrl.abort()  // 用户点停止

3. Vue 响应式自动渲染：
   aiMsg.content += chunk
   --> 视图自动逐字更新（无需手动 DOM 操作）
```

### 3. Vite 代理 vs 后端 CORS（背区别）

```
Vite Proxy（开发期）：
  浏览器 -> Vite(5173) -> 转发 -> 后端(12115)
  浏览器看到同源，不触发 CORS
  仅开发环境有效

后端 CORS（生产期）：
  浏览器 -> 后端 (跨域)
  后端响应头带 Access-Control-Allow-Origin
  生产环境必须配置

最佳实践：双保险都配（开发用 Proxy 简单，生产用 CORS 兜底）
```

### 4. 项目结构最佳实践（背 5 个目录）

```
api/        接口集中管理（修改后端 URL 时不用改业务代码）
components/ 公共组件（高内聚低耦合）
views/      页面（路由对应的页面级组件）
stores/     Pinia 状态（跨组件共享数据）
utils/      工具函数（纯函数，可独立测试）
```



------

## 八、Day29 待办（明天做）

```
功能：
  □ 真实登录（JWT Token）
     - 后端：Spring Security + jjwt
     - 前端：Pinia 用户状态 + 路由守卫 + axios 拦截器加 Token
     - SSE：通过 fetchEventSource 的 headers 携带 Token
  □ 多轮对话页面
     - sessionId 用 nanoid 生成，存 localStorage
     - 调 POST /rag/multi-chat 接口
     - 历史消息持久化到 Redis（已有后端实现）
  □ 引用来源展示
     - 后端 RagService 返回结构改造，把 docs 一并返回
     - 前端在答案下方展示来源卡片，点击跳转原文
  □ PDF/Word 上传
     - 后端用 Spring AI Tika DocumentReader 解析
     - 前端 multipart/form-data 上传

部署：
  □ Vue 打包：npm run build
  □ Nginx 静态资源 + proxy_pass 后端
  □ HTTPS 证书（Let's Encrypt 免费）
  □ Docker Compose 一键部署（PG + Redis + ES + Backend + Nginx）
```


------

## 九、附：完整文件清单（已生成）

```
后端（新增 1 个文件）：
  demo-springai/src/main/java/com/jianbo/springai/config/WebMvcConfig.java

前端（17 个文件）：
  demo-springai-front/
  ├── package.json
  ├── vite.config.ts
  ├── tsconfig.json
  ├── tsconfig.node.json
  ├── index.html
  ├── .env.development
  ├── .gitignore
  ├── README.md
  └── src/
      ├── main.ts
      ├── App.vue
      ├── env.d.ts
      ├── api/
      │   ├── request.ts
      │   ├── rag.ts
      │   └── kb.ts
      ├── components/
      │   └── MarkdownRenderer.vue
      ├── layouts/
      │   └── MainLayout.vue
      ├── router/
      │   └── index.ts
      ├── styles/
      │   └── main.scss
      ├── utils/
      │   └── sse.ts
      └── views/
          ├── ChatView.vue
          ├── KnowledgeBaseView.vue
          └── LoginView.vue

总计：18 个文件，可直接 npm install && npm run dev 跑起来
```
