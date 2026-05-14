import request from '@/utils/request'

// 调用后端「用户自备 OpenAI 兼容 Chat」REST；智能问答 SSE 传 model=user:{alias} 时由后端 ChatClientResolver 解析。

export interface UserChatModelVo {
  id: number
  alias: string
  label?: string
  baseUrl: string
  completionsPath?: string
  model: string
  temperature?: number
  maxTokens?: number
  apiKeyConfigured: boolean
  apiKeyHint?: string
}

export interface UserChatModelSavePayload {
  id?: number
  alias?: string
  label?: string
  baseUrl: string
  apiKey?: string
  completionsPath?: string
  model: string
  temperature?: number
  maxTokens?: number
}

export interface UserChatModelTryPayload {
  baseUrl: string
  apiKey: string
  completionsPath?: string
  model: string
  temperature?: number
  maxTokens?: number
}

export function listUserChatModels(): Promise<UserChatModelVo[]> {
  return request.get('/api/user/chat-models')
}

export function saveUserChatModel(data: UserChatModelSavePayload): Promise<UserChatModelVo> {
  return request.post('/api/user/chat-models', data)
}

export function deleteUserChatModel(id: number): Promise<{ ok: boolean }> {
  return request.delete(`/api/user/chat-models/${id}`)
}

export function tryUserChatModelInline(data: UserChatModelTryPayload): Promise<{ preview: string }> {
  return request.post('/api/user/chat-models/try', data)
}

export function tryUserChatModelSaved(id: number): Promise<{ preview: string }> {
  return request.post(`/api/user/chat-models/${id}/try`)
}
