import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
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
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
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
    const res = response.data
    
    // 处理业务错误
    if (response.status >= 200 && response.status < 300) {
      return res
    }
    
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async (error: AxiosError<{ error?: string; message?: string; status?: number }>) => {
    const response = error.response
    const status = response?.status
    
    if (status === 401) {
      // Token过期或未认证
      await ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).catch(() => {})
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

// SSE流式请求
export function requestStream<T = any>(
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
  let aborted = false
  
  fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(data)
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
      
      function read() {
        reader.read().then(({ done, value }) => {
          if (done || aborted) {
            if (buffer) {
              onMessage(buffer)
            }
            onComplete?.()
            return
          }
          
          buffer += decoder.decode(value, { stream: true })
          
          // 处理SSE数据
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          
          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const text = line.slice(6).trim()
              if (text) {
                onMessage(text)
              }
            }
          }
          
          read()
        })
      }
      
      read()
    })
    .catch(error => {
      if (!aborted) {
        onError?.(error)
      }
    })
  
  return {
    cancel: () => {
      aborted = true
    }
  }
}

export default service
