import request from '@/utils/request'
import type { ChatRequest, SystemPrompt, ChatMessage, Session } from '@/types'

// ==================== 智能问答（自动路由 知识库 → 网络搜索 → LLM直答） ====================

// 智能问答（SSE 流式）—— 后端唯一对外入口
export function chatStream(data: ChatRequest) {
  return request.post<any, any>('/api/rag/chat/stream', data)
}

// ==================== 会话管理 ====================

// 获取所有会话
export function getSessions() {
  return request.get<any, Session[]>('/api/rag/sessions')
}

// 获取会话历史
export function getHistory(sessionId: string) {
  return request.get<any, ChatMessage[]>(`/api/rag/history/${sessionId}`)
}

// 删除会话
export const deleteSession = (sessionId: string) => {
  return request.delete<any, { message: string; sessionId: string }>(`/api/rag/session/${sessionId}`)
}

// 重命名会话
export const renameSession = (sessionId: string, title: string) => {
  return request.put<any, { message: string; sessionId: string }>(`/api/rag/session/${sessionId}/title`, { title })
}

// 提交消息反馈（👍/👎），rating: 1=赞 / -1=踩
export function submitFeedback(payload: {
  messageId: number
  rating: 1 | -1
  sessionId: string
  comment?: string
}) {
  return request.post<any, { ok: boolean; messageId: number; rating: number }>(
    '/api/rag/feedback',
    payload
  )
}

// ==================== Prompt管理 ====================

// 获取所有Prompt
export function getPrompts() {
  return request.get<any, SystemPrompt[]>('/api/rag/prompts')
}

// 创建/更新Prompt
export function savePrompt(prompt: SystemPrompt) {
  return request.post<any, { message: string; name: string }>('/api/rag/prompt', prompt)
}

// 设置默认Prompt
export function setDefaultPrompt(name: string) {
  return request.put<any, { message: string; name: string }>(`/api/rag/prompt/default/${name}`)
}