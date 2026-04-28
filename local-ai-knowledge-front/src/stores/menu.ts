import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

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

  // 根据用户角色生成菜单
  function generateMenus(isAdmin: boolean) {
    const baseMenus: MenuItem[] = [
      {
        path: '/rag',
        name: 'RagChat',
        title: '智能问答',
        icon: 'ChatDotRound',
        order: 1
      },
      {
        path: '/rag/multi-chat',
        name: 'MultiChat',
        title: '多轮对话',
        icon: 'ChatLineRound',
        order: 2
      }
    ]

    // 管理员额外菜单
    if (isAdmin) {
      baseMenus.push(
        {
          path: '/document',
          name: 'Document',
          title: '文档管理',
          icon: 'Document',
          order: 3
        },
        {
          path: '/admin',
          name: 'Admin',
          title: '系统管理',
          icon: 'Setting',
          order: 4
        }
      )
    }

    menus.value = baseMenus.sort((a, b) => (a.order || 99) - (b.order || 99))
  }

  // 清空菜单
  function clearMenus() {
    menus.value = []
  }

  return {
    menus,
    generateMenus,
    clearMenus
  }
})