import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  children?: MenuItem[]
  order?: number
}

export const useMenuStore = defineStore('menu', () => {
  // 菜单列表
  const menus = ref<MenuItem[]>([])
  // 加载状态
  const loading = ref(false)
  // 是否已加载过
  const hasLoaded = ref(false)

  // 获取当前用户的菜单权限（只调用一次）
  async function fetchUserMenus(force = false) {
    // 如果已加载过，且不是强制刷新，则跳过
    if (hasLoaded.value && !force) {
      return
    }

    loading.value = true
    try {
      const userMenus = await request.get<any, any[]>('/api/user/menus')
      console.log('用户菜单数据:', userMenus)

      menus.value = buildSidebarMenus(userMenus)
      hasLoaded.value = true
      console.log('构建后的菜单:', menus.value)

    } catch (error) {
      console.error('获取菜单权限失败:', error)
      menus.value = getDefaultMenus()
    } finally {
      loading.value = false
    }
  }

  // 构建侧边栏菜单
  function buildSidebarMenus(backendMenus: any[]): MenuItem[] {
    if (!backendMenus || backendMenus.length === 0) {
      return []
    }

    function convertMenu(m: any): MenuItem {
      return {
        path: m.path,
        name: m.name || m.path.split('/').pop() || '',
        title: m.name,
        icon: m.icon || 'Document',
        order: m.sortOrder ?? 99,
        children: m.children && m.children.length > 0
          ? m.children.map(convertMenu)
          : undefined
      }
    }

    const result = backendMenus.map(convertMenu)

    const sortMenus = (items: MenuItem[]) => {
      items.sort((a, b) => (a.order ?? 99) - (b.order ?? 99))
      items.forEach(item => {
        if (item.children?.length) sortMenus(item.children)
      })
    }
    sortMenus(result)

    return result
  }

  // 默认菜单
  function getDefaultMenus(): MenuItem[] {
    return [
      {
        path: '/guide',
        name: 'UserGuide',
        title: '使用指南',
        icon: 'Notebook',
        order: 0
      },
      {
        path: '/rag',
        name: 'RagChat',
        title: '智能问答',
        icon: 'ChatDotRound',
        order: 1
      }
    ]
  }

  // 清空菜单（退出登录时调用）
  function clearMenus() {
    menus.value = []
    hasLoaded.value = false
  }

  return {
    menus,
    loading,
    hasLoaded,
    fetchUserMenus,
    clearMenus
  }
})
