import request from '@/utils/request'
import type { LoginRequest, RegisterRequest, LoginResponse, SysUser } from '@/types'

// 登录
export function login(data: LoginRequest) {
  return request.post<any, LoginResponse>('/auth/login', data)
}

// 注册
export function register(data: RegisterRequest) {
  return request.post<any, { message: string }>('/auth/register', data)
}

// 获取当前用户信息
export function getCurrentUser() {
  return request.get<any, any>('/auth/me')
}