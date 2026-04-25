import request from './request'

export interface DocumentInfo {
  source: string
  title: string
  totalChunks: number
  uploadTime: number
}

export interface UploadDTO {
  source: string
  title: string
  content: string
}

export interface UploadResult {
  source: string
  chunks: number
}

/** 上传文档到知识库 */
export function uploadDoc(dto: UploadDTO): Promise<UploadResult> {
  return request.post('/kb/upload', dto)
}

/** 列出所有文档 */
export function listDocs(maxScan = 1000): Promise<DocumentInfo[]> {
  return request.get('/kb/list', { params: { maxScan } })
}

/** 更新文档 */
export function updateDoc(source: string, dto: Omit<UploadDTO, 'source'>): Promise<UploadResult> {
  return request.put(`/kb/${encodeURIComponent(source)}`, dto)
}

/** 删除单个文档 */
export function deleteDoc(source: string): Promise<void> {
  return request.delete(`/kb/${encodeURIComponent(source)}`)
}

/** 批量删除 */
export function batchDelete(sources: string[]): Promise<void> {
  return request.post('/kb/batch-delete', sources)
}
