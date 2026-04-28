import request from '@/utils/request'
import type { DocumentTask, DocumentUploadResponse, DocumentTaskLog, DocScope } from '@/types'

// 上传文档
export function uploadDocument(file: File, docScope: DocScope = 'PRIVATE') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('docScope', docScope)
  
  return request.post<any, DocumentUploadResponse>('/api/doc/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 查询任务状态
export function getTaskStatus(taskId: string) {
  return request.get<any, DocumentTask>(`/api/doc/status/${taskId}`)
}

// 查询所有任务
export function getAllTasks() {
  return request.get<any, DocumentTask[]>('/api/doc/tasks')
}

// 查询任务日志
export function getTaskLogs(taskId: string) {
  return request.get<any, DocumentTaskLog>(`/api/doc/logs/${taskId}`)
}

// 删除文档
export function deleteDocument(taskId: string) {
  return request.delete<any, { message: string }>(`/api/doc/${taskId}`)
}

// 获取文档下载URL
export function getDownloadUrl(taskId: string) {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:12116'
  return `${baseURL}/api/doc/download/${taskId}`
}
