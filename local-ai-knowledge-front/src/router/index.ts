import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMenuStore } from '@/stores/menu'
import NProgress from 'nprogress'

// 路由懒加载
const Login = () => import('@/views/auth/Login.vue')
const Register = () => import('@/views/auth/Register.vue')
const Layout = () => import('@/layout/index.vue')

// RAG 智能问答
const RagChat = () => import('@/views/rag/RagChat.vue')

// 文档管理
const DocumentManage = () => import('@/views/document/DocumentManage.vue')

// 管理员页面
const UserManage = () => import('@/views/admin/UserManage.vue')
const RoleManage = () => import('@/views/admin/RoleManage.vue')
const MenuManage = () => import('@/views/admin/MenuManage.vue')
const AgentManage = () => import('@/views/admin/AgentManage.vue')
const CrawlerManage = () => import('@/views/admin/CrawlerManage.vue')

// 个人中心
const UserProfile = () => import('@/views/profile/UserProfile.vue')

// 使用指南
const UserGuide = () => import('@/views/guide/UserGuide.vue')

// 每日热榜
const HotDashboard = () => import('@/views/hot/HotDashboard.vue')
const HotHistory = () => import('@/views/hot/HotHistory.vue')

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
    redirect: '/guide',
    children: [
      {
        path: 'guide',
        name: 'UserGuide',
        component: UserGuide,
        meta: { title: '使用指南', icon: 'Notebook' }
      },
      {
        path: 'rag',
        name: 'RagChat',
        component: RagChat,
        meta: { title: '智能问答', icon: 'ChatDotRound' }
      },
      {
        path: 'documents',
        name: 'DocumentManage',
        component: DocumentManage,
        meta: { title: '文档管理', icon: 'Document', requiredRoles: ['ROLE_USER', 'ROLE_ADMIN'] }
      },
      {
        path: 'hot',
        name: 'Hot',
        redirect: '/hot/dashboard',
        meta: { title: '每日热榜', icon: 'TrendCharts' },
        children: [
          {
            path: 'dashboard',
            name: 'HotDashboard',
            component: HotDashboard,
            meta: { title: '每日热榜', icon: 'TrendCharts' }
          },
          {
            path: 'history',
            name: 'HotHistory',
            component: HotHistory,
            meta: { title: '历史热榜', icon: 'Clock' }
          }
        ]
      },
      {
        path: 'profile',
        name: 'UserProfile',
        component: UserProfile,
        meta: { title: '个人中心', icon: 'User' }
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
          },
          {
            path: 'roles',
            name: 'RoleManage',
            component: RoleManage,
            meta: { title: '角色管理', requiredRoles: ['ROLE_ADMIN'] }
          },
          {
            path: 'menus',
            name: 'MenuManage',
            component: MenuManage,
            meta: { title: '菜单管理', requiredRoles: ['ROLE_ADMIN'] }
          },
          {
            path: 'agents',
            name: 'AgentManage',
            component: AgentManage,
            meta: { title: '智能体管理', requiredRoles: ['ROLE_ADMIN'] }
          },
          {
            path: 'crawler',
            name: 'CrawlerManage',
            component: CrawlerManage,
            meta: { title: '爬虫管理', requiredRoles: ['ROLE_ADMIN'] }
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
router.beforeEach(async (to, _from, next) => {
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
        // token 无效，清除并跳转登录
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
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

  // 获取用户菜单（根据用户角色权限动态获取，避免重复请求）
  if (!menuStore.hasLoaded) {
    menuStore.fetchUserMenus()
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

// 导出路由实例供其他地方使用
export default router