import request from '@/utils/request'
import type { DocumentTask, DocumentUploadResponse, DocumentTaskLog } from '@/types'

// 上传文档
export function uploadDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  
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