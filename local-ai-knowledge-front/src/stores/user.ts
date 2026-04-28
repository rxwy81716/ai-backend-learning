import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getCurrentUser } from '@/api/auth'
import { storage } from '@/utils/storage'
import router from '@/router'
import type { LoginRequest, RegisterRequest, LoginResponse, SysUser } from '@/types'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(storage.getToken() || '')
  const userInfo = ref<SysUser | null>(storage.getUserInfo())
  const loading = ref(false)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.roles?.some(r => r.roleCode === 'ROLE_ADMIN'))
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const roles = computed(() => userInfo.value?.roles || [])

  // 登录
  async function login(data: LoginRequest) {
    loading.value = true
    try {
      const res = await apiLogin(data)
      token.value = res.token
      storage.setToken(res.token)
      
      // 获取用户完整信息
      const userRes = await getCurrentUser()
      userInfo.value = userRes
      storage.setUserInfo(userRes)
      
      return res
    } finally {
      loading.value = false
    }
  }

  // 注册
  async function register(data: RegisterRequest) {
    loading.value = true
    try {
      const res = await apiRegister(data)
      return res
    } finally {
      loading.value = false
    }
  }

  // 退出登录
  function logout() {
    token.value = ''
    userInfo.value = null
    storage.clear()
    router.push('/login')
  }

  // 刷新用户信息
  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await getCurrentUser()
      userInfo.value = res
      storage.setUserInfo(res)
    } catch {
      logout()
    }
  }

  // 检查权限
  function hasRole(roleCode: string): boolean {
    return roles.value.some(r => r.roleCode === roleCode)
  }

  function hasAnyRole(roleCodes: string[]): boolean {
    return roleCodes.some(code => hasRole(code))
  }

  return {
    token,
    userInfo,
    loading,
    isLoggedIn,
    isAdmin,
    username,
    nickname,
    roles,
    login,
    register,
    logout,
    fetchUserInfo,
    hasRole,
    hasAnyRole
  }
})