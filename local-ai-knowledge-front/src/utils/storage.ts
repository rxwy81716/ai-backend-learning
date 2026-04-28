// 本地存储工具
const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'
const REMEMBER_KEY = 'remember'

export const storage = {
  // Token
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  
  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
  },
  
  removeToken(): void {
    localStorage.removeItem(TOKEN_KEY)
  },
  
  // 用户信息
  getUserInfo(): any {
    const info = localStorage.getItem(USER_INFO_KEY)
    return info ? JSON.parse(info) : null
  },
  
  setUserInfo(userInfo: any): void {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
  },
  
  removeUserInfo(): void {
    localStorage.removeItem(USER_INFO_KEY)
  },
  
  // 记住密码
  getRemember(): any {
    const info = localStorage.getItem(REMEMBER_KEY)
    return info ? JSON.parse(info) : null
  },
  
  setRemember(data: any): void {
    localStorage.setItem(REMEMBER_KEY, JSON.stringify(data))
  },
  
  removeRemember(): void {
    localStorage.removeItem(REMEMBER_KEY)
  },
  
  // 清除所有
  clear(): void {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
  }
}

export default storage
