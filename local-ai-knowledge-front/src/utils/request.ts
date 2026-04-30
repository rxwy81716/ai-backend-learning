import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:12116',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 添加token
    const token = localStorage.getItem('token')
    console.log('请求拦截器 - URL:', config.url, 'Token:', token ? '存在' : '不存在')
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  },
  (error: AxiosError) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    // SSE / 流式响应直接返回，不做统一包装解析
    const contentType = String(response.headers['content-type'] || '')
    if (contentType.includes('text/event-stream') || contentType.includes('application/octet-stream')) {
      return response.data
    }

    const data = response.data
    // 统一响应格式处理
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0) {
        return data.data
      } else {
        // 业务错误，抛出给调用方处理
        const error = new Error(data.message || '请求失败') as any
        error.response = response
        return Promise.reject(error)
      }
    }
    return data
  },
  async (error: AxiosError<{ error?: string; message?: string; status?: number }>) => {
    const response = error.response
    const status = response?.status
    
    if (status === 401) {
      // Token过期或未认证，清除并跳转登录
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
      return Promise.reject(error)
    }
    
    if (status === 403) {
      ElMessage.error('权限不足，无法访问该资源')
      router.push('/403')
      return Promise.reject(error)
    }
    
    if (status === 500) {
      ElMessage.error('服务器内部错误')
      return Promise.reject(error)
    }
    
    // 处理业务层面的错误
    const errorMsg = response?.data?.error || response?.data?.message || error.message
    if (errorMsg && status !== 401) {
      ElMessage.error(errorMsg)
    }
    
    return Promise.reject(error)
  }
)

// 添加常用方法扩展
export const get = <T = any>(url: string, config?: InternalAxiosRequestConfig) => {
  return service.get<T>(url, config)
}

export const post = <T = any>(url: string, data?: any, config?: InternalAxiosRequestConfig) => {
  return service.post<T>(url, data, config)
}

export const put = <T = any>(url: string, data?: any, config?: InternalAxiosRequestConfig) => {
  return service.put<T>(url, data, config)
}

export const del = <T = any>(url: string, config?: InternalAxiosRequestConfig) => {
  return service.delete<T>(url, config)
}

// SSE流式请求
export function requestStream(
  url: string,
  data: Record<string, any>,
  onMessage: (text: string) => void,
  onError?: (error: Error) => void,
  onComplete?: () => void
): { cancel: () => void } {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  
  let buffer = ''
  const abortController = new AbortController()
  
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:12116'
  const fullUrl = url.startsWith('http') ? url : baseURL + url
  
  fetch(fullUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal: abortController.signal
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('无法读取响应流')
      }
      
      const decoder = new TextDecoder()
      const readerRef = reader

      // 解析单个 SSE 事件块：抽取所有 data: 行，按规范用 \n 连接；忽略注释与其它字段
      const parseEvent = (raw: string): string | null => {
        const lines = raw.split(/\r?\n/)
        const dataLines: string[] = []
        for (const line of lines) {
          if (!line || line.startsWith(':')) continue
          if (line.startsWith('data:')) {
            // 规范允许 "data: xxx" 或 "data:xxx"，去掉前缀和单个前导空格
            const v = line.slice(5)
            dataLines.push(v.startsWith(' ') ? v.slice(1) : v)
          }
        }
        if (dataLines.length === 0) return null
        return dataLines.join('\n')
      }

      function read() {
        readerRef.read().then(({ done, value }) => {
          if (done) {
            // 处理收尾残留
            const tail = buffer + decoder.decode()
            if (tail.trim()) {
              const data = parseEvent(tail)
              if (data && data !== '[DONE]') onMessage(data)
            }
            onComplete?.()
            return
          }

          buffer += decoder.decode(value, { stream: true })

          // 按 SSE 帧分隔（空行）切分；最后一段可能不完整，留到下次
          const parts = buffer.split(/\r?\n\r?\n/)
          buffer = parts.pop() ?? ''

          for (const part of parts) {
            const data = parseEvent(part)
            if (data === null) continue
            if (data === '[DONE]') continue
            onMessage(data)
          }

          read()
        })
      }
      
      read()
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        onError?.(error)
      }
    })
  
  return {
    cancel: () => {
      abortController.abort()
    }
  }
}

export default service
