import request from '@/utils/request'

export interface ComparisonItem {
  dimension: string
  rag: string
  finetune: string
}

export interface ComparisonInfo {
  title: string
  currentApproach: string
  comparison: ComparisonItem[]
  recommendation: string
  trainingDataFormat: { format: string; example: string }
}

export interface EstimateResult {
  taskId: string
  fileName: string
  totalChunks: number
  validChunks: number
  estimatedQAPairs: number
  estimatedCost: string
  error?: string
}

/** RAG vs 微调对比说明 */
export function getComparison() {
  return request.get<any, ComparisonInfo>('/api/finetune/comparison')
}

/** 估算训练数据量 */
export function estimateTrainingData(taskId: string) {
  return request.get<any, EstimateResult>(`/api/finetune/${taskId}/estimate`)
}

/** 生成训练数据（POST 下载 JSONL） */
export async function downloadTrainingData(taskId: string, userId?: string): Promise<Blob> {
  const params = userId ? `?userId=${userId}` : ''
  const response = await request.post<any, Blob>(`/api/finetune/${taskId}/generate${params}`, {}, {
    responseType: 'blob'
  } as any)
  return response
}
