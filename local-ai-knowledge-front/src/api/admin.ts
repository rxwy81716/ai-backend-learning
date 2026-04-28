import request from '@/utils/request'
import type { UserListItem } from '@/types'

// 获取所有用户
export function getUsers() {
  return request.get<any, UserListItem[]>('/api/admin/users')
}

// 分配角色
export function assignRole(userId: number, roleCode: string) {
  return request.put<any, { message: string }>(`/api/admin/user/${userId}/role`, { roleCode })
}

// 启用/禁用用户
export function setUserEnabled(userId: number, enabled: boolean) {
  return request.put<any, { message: string }>(`/api/admin/user/${userId}/enabled`, { enabled })
}