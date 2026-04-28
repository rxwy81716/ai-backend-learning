import request from '@/utils/request'

// ==================== 用户管理 ====================

export function getUsers() {
  return request.get<any, any[]>('/api/admin/users')
}

export function assignRole(userId: number, roleCode: string) {
  return request.post<any, any>(`/api/admin/users/${userId}/role?roleCode=${roleCode}`, {})
}

export function setUserEnabled(userId: number, enabled: boolean) {
  return request.put<any, any>(`/api/admin/users/${userId}/enabled?enabled=${enabled}`, {})
}

export interface CreateUserData {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
}

export interface UpdateUserData {
  nickname?: string
  email?: string
  phone?: string
}

export function createUser(data: CreateUserData) {
  return request.post<any, any>('/api/admin/users', data)
}

export function updateUser(id: number, data: UpdateUserData) {
  return request.put<any, any>(`/api/admin/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<any, any>(`/api/admin/users/${id}`)
}

// ==================== 角色管理 ====================

export function getRoles() {
  return request.get<any, any[]>('/api/admin/roles')
}

export function createRole(data: { name: string; code: string; description?: string }) {
  return request.post<any, any>('/api/admin/roles', data)
}

export function updateRole(id: number, data: { name: string; description?: string }) {
  return request.put<any, any>(`/api/admin/roles/${id}`, data)
}

export function deleteRole(id: number) {
  return request.delete<any, any>(`/api/admin/roles/${id}`)
}

// ==================== 菜单管理 ====================

export function getMenus() {
  return request.get<any, any[]>('/api/admin/menus')
}

export function updateMenu(id: number, data: any) {
  return request.put<any, any>(`/api/admin/menus/${id}`, data)
}

// ==================== 智能体管理（System Prompt） ====================

export interface SystemPrompt {
  id: number
  name: string
  content: string
  description: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export function getAgents() {
  return request.get<any, SystemPrompt[]>('/api/admin/agents')
}

export function getAgent(id: number) {
  return request.get<any, SystemPrompt>(`/api/admin/agents/${id}`)
}

export function getDefaultAgent() {
  return request.get<any, SystemPrompt>('/api/admin/agents/default')
}

export function createAgent(data: { name: string; content: string; description?: string; isDefault?: boolean }) {
  return request.post<any, any>('/api/admin/agents', data)
}

export function updateAgent(id: number, data: { name: string; content: string; description?: string; isDefault?: boolean }) {
  return request.put<any, any>(`/api/admin/agents/${id}`, data)
}

export function setDefaultAgent(id: number) {
  return request.put<any, any>(`/api/admin/agents/${id}/default`, {})
}

export function deleteAgent(id: number) {
  return request.delete<any, any>(`/api/admin/agents/${id}`)
}
