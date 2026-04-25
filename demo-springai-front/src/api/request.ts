import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 60000,  // RAG 同步接口可能较慢，给 60s
  headers: {
    'Content-Type': 'application/json'
  }
})

// 响应拦截：统一错误提示
request.interceptors.response.use(
  (res: AxiosResponse) => res.data,
  (err) => {
    const msg = err.response?.data?.message || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default request
