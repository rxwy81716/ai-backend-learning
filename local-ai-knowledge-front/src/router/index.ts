import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import type { RouteRecordNormalized } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMenuStore } from '@/stores/menu'
import NProgress from 'nprogress'

// 路由懒加载
const Login = () => import('@/views/auth/Login.vue')
const Register = () => import('@/views/auth/Register.vue')
const Layout = () => import('@/layout/index.vue')

// RAG相关页面
const RagChat = () => import('@/views/rag/RagChat.vue')
const MultiChat = () => import('@/views/rag/MultiChat.vue')

// 文档管理
const DocumentManage = () => import('@/views/document/DocumentManage.vue')

// 管理员页面
const UserManage = () => import('@/views/admin/UserManage.vue')

// 错误页面
const NotFound = () => import('@/views/error/404.vue')
const Forbidden = () => import('@/views/error/403.vue')

// 公共路由
const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { title: '注册', hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: NotFound,
    meta: { title: '页面不存在', hidden: true }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: Forbidden,
    meta: { title: '无权限', hidden: true }
  }
]

// 私有路由（需要认证）
const privateRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/rag',
    children: [
      {
        path: 'rag',
        name: 'RagChat',
        component: RagChat,
        meta: { title: '智能问答', icon: 'ChatDotRound' }
      },
      {
        path: 'rag/multi-chat',
        name: 'MultiChat',
        component: MultiChat,
        meta: { title: '多轮对话', icon: 'ChatLineRound' }
      },
      {
        path: 'document',
        name: 'DocumentManage',
        component: DocumentManage,
        meta: { title: '文档管理', icon: 'Document', requiredRoles: ['ROLE_ADMIN'] }
      },
      {
        path: 'admin',
        name: 'Admin',
        redirect: '/admin/users',
        meta: { title: '系统管理', icon: 'Setting', requiredRoles: ['ROLE_ADMIN'] },
        children: [
          {
            path: 'users',
            name: 'UserManage',
            component: UserManage,
            meta: { title: '用户管理', requiredRoles: ['ROLE_ADMIN'] }
          }
        ]
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: [...publicRoutes, ...privateRoutes]
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const menuStore = useMenuStore()

  // 开始进度条
  NProgress.start()

  // 设置页面标题
  const title = to.meta.title as string
  document.title = title ? `${title} - Local AI Knowledge` : 'Local AI Knowledge'

  // 公共路由放行
  if (to.meta.hidden) {
    next()
    return
  }

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    const token = localStorage.getItem('token')
    if (token) {
      try {
        await userStore.fetchUserInfo()
      } catch {
        next('/login')
        return
      }
    } else {
      next('/login')
      return
    }
  }

  // 检查角色权限
  const requiredRoles = to.meta.requiredRoles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    const hasPermission = userStore.hasAnyRole(requiredRoles)
    if (!hasPermission) {
      next('/403')
      return
    }
  }

  // 生成菜单
  menuStore.generateMenus(userStore.isAdmin)

  next()
})

router.afterEach(() => {
  NProgress.done()
})

// 导出路由实例供其他地方使用
export default router