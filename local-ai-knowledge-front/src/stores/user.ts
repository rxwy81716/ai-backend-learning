import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getCurrentUser, refreshToken as apiRefreshToken } from '@/api/auth'
import { storage } from '@/utils/storage'
import router from '@/router'
import { useMenuStore } from './menu'
import type { LoginRequest, RegisterRequest, SysUser } from '@/types'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(storage.getToken() || '')
  const userInfo = ref<SysUser | null>(storage.getUserInfo())
  const loading = ref(false)
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  // 解析 JWT 过期时间（秒级时间戳）
  function getTokenExpiry(t: string): number | null {
    try {
      const payload = JSON.parse(atob(t.split('.')[1]))
      return payload.exp || null
    } catch {
      return null
    }
  }

  // 启动 Token 自动续期定时器
  function scheduleTokenRefresh() {
    if (refreshTimer) clearTimeout(refreshTimer)
    if (!token.value) return

    const exp = getTokenExpiry(token.value)
    if (!exp) return

    // 在到期前 2 小时刷新（如果剩余不足 2 小时，则 10 秒后立即刷新）
    const now = Math.floor(Date.now() / 1000)
    const remaining = exp - now
    const refreshIn = Math.max((remaining - 7200) * 1000, 10000)

    refreshTimer = setTimeout(async () => {
      try {
        const res = await apiRefreshToken()
        token.value = res.token
        storage.setToken(res.token)
        scheduleTokenRefresh()
      } catch {
        // 刷新失败不强制退出，等下次请求 401 再处理
      }
    }, refreshIn)
  }

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  // roles 是字符串数组 ["ROLE_USER", "ROLE_ADMIN"]
  const isAdmin = computed(() => userInfo.value?.roles?.includes('ROLE_ADMIN') ?? false)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  // 返回角色编码数组
  const roleCodes = computed(() => userInfo.value?.roles || [])

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

      // 登录后重新获取菜单
      const menuStore = useMenuStore()
      menuStore.clearMenus()
      menuStore.fetchUserMenus()

      // 启动 Token 自动续期
      scheduleTokenRefresh()

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
    if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
    token.value = ''
    userInfo.value = null
    storage.clear()

    // 清空菜单
    const menuStore = useMenuStore()
    menuStore.clearMenus()

    router.push('/login')
  }

  // 刷新用户信息
  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await getCurrentUser()
      userInfo.value = res
      storage.setUserInfo(res)
      scheduleTokenRefresh()
    } catch {
      logout()
    }
  }

  // 检查权限
  function hasRole(roleCode: string): boolean {
    return roleCodes.value.includes(roleCode)
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
    roles: roleCodes,
    login,
    register,
    logout,
    fetchUserInfo,
    hasRole,
    hasAnyRole
  }
})