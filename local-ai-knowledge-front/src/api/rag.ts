import request from '@/utils/request'
import type { ChatRequest, ChatResponse, Session, SystemPrompt, ChatMessage } from '@/types'

// ==================== 智能问答 ====================

// 智能问答（同步）
export function agentChat(data: ChatRequest) {
  return request.post<any, ChatResponse>('/api/rag/agent/chat', data)
}

// 智能问答（SSE流式）
export function agentChatStream(data: ChatRequest) {
  return request.post<any, any>('/api/rag/agent/chat/stream', data)
}

// ==================== 单轮问答 ====================

// 单轮RAG问答（同步）
export function chat(data: { question: string; promptName?: string }) {
  return request.post<any, ChatResponse>('/api/rag/chat', data)
}

// 单轮RAG问答（SSE流式）
export function chatStream(data: { question: string; promptName?: string }) {
  return request.post<any, any>('/api/rag/chat/stream', data)
}

// ==================== 多轮对话 ====================

// 多轮对话（同步）
export function multiChat(data: ChatRequest) {
  return request.post<any, ChatResponse>('/api/rag/multi-chat', data)
}

// 多轮对话（SSE流式）
export function multiChatStream(data: ChatRequest) {
  return request.post<any, any>('/api/rag/multi-chat/stream', data)
}

// ==================== 会话管理 ====================

// 获取所有会话
export function getSessions() {
  return request.get<any, string[]>('/api/rag/sessions')
}

// 获取会话历史
export function getHistory(sessionId: string) {
  return request.get<any, ChatMessage[]>(`/api/rag/history/${sessionId}`)
}

// 删除会话
export function deleteSession(sessionId: string) {
  return request.delete<any, { message: string; sessionId: string }>(`/api/rag/session/${sessionId}`)
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