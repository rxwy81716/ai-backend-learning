import request from './request'

/**
 * 同步 RAG 问答（对应后端 GET /rag/chat）
 *
 * 缺点：要等大模型完整生成才返回（3~5秒空白）
 * 仅用于测试或非用户场景
 */
export function ragChat(query: string, topK = 5, threshold = 0.7): Promise<string> {
  return request.get('/rag/chat', {
    params: { query, topK, threshold },
    // 后端返回 String，不是 JSON
    transformResponse: [(data) => data]
  })
}

/**
 * 多轮 RAG 问答（对应后端 POST /rag/multi-chat）
 *
 * 后端会自动维护会话历史，前端只要传 sessionId 就行
 */
export interface ChatSessionDTO {
  sessionId: string
  question: string
  stream?: boolean
}

export function ragMultiChat(dto: ChatSessionDTO): Promise<string> {
  return request.post('/rag/multi-chat', dto, {
    transformResponse: [(data) => data]
  })
}

/** 流式接口的 URL（不直接调，由 SSE util 调用） */
export function ragStreamUrl(query: string, topK = 5, threshold = 0.7): string {
  const base = import.meta.env.VITE_API_BASE || '/api'
  const params = new URLSearchParams({
    query,
    topK: String(topK),
    threshold: String(threshold)
  })
  return `${base}/rag/chat-stream?${params.toString()}`
}
