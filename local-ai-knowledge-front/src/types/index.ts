// 用户相关类型
export interface SysUser {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  enabled: boolean
  roles: SysRole[]
  createdAt?: string
  updatedAt?: string
}

export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  description?: string
}

// 登录注册
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  nickname?: string
  roles: string[]
  expiresIn: number
}

// 文档相关
export type TaskStatus = 'UPLOADED' | 'PARSING' | 'IMPORTING' | 'DONE' | 'FAILED'
export type DocScope = 'PUBLIC' | 'PRIVATE'

export interface DocumentTask {
  taskId: string
  fileName: string
  filePath: string
  fileSize: number
  status: TaskStatus
  docScope: DocScope
  userId?: string
  totalChunks: number
  importedChunks: number
  errorMsg?: string
  createdAt: string
  finishedAt?: string
}

export interface DocumentUploadResponse {
  taskId: string
  fileName: string
  fileSize: number
  status: string
}

export interface DocumentTaskLog {
  task: DocumentTask
  logs: string[]
}

// RAG问答相关
export type ChatMode = 'KNOWLEDGE' | 'LLM'

export interface ChatMessage {
  /** chat_conversation.id，仅历史接口返回时存在；流式生成中的消息无 id */
  id?: number
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: string
}

export interface ChatRequest {
  question: string
  sessionId?: string
  promptName?: string
  chatMode?: ChatMode
  /** 思考模式开关：true=深度推理（慢，回答更好） / false 或省略=快速模式（默认） */
  thinking?: boolean
}

export interface ChatResponse {
  answer: string
  sources?: SourceDocument[]
  sessionId?: string
  metadata?: Record<string, any>
}

export interface SourceDocument {
  content: string
  score: number
  source?: string
}

export interface Session {
  sessionId: string
  title?: string
  firstQuestion?: string
  createdAt?: number
  updatedAt?: string
}

export interface SystemPrompt {
  id?: number
  name: string
  content: string
  isDefault?: boolean
  description?: string
}

// 管理员相关
export interface UserListItem {
  id: number
  username: string
  nickname?: string
  email?: string
  enabled: boolean
  roles: SysRole[]
  createdAt: string
}

// 通用响应
export interface ApiResponse<T = any> {
  data?: T
  error?: string
  message?: string
  status?: number
}

// 路由元信息
export interface RouteMeta {
  title: string
  icon?: string
  requiresAuth?: boolean
  requiredRoles?: string[]
  hidden?: boolean
  order?: number
}
