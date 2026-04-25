import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/ChatView.vue'),
          meta: { title: '知识问答' }
        },
        {
          path: 'kb',
          name: 'kb',
          component: () => import('@/views/KnowledgeBaseView.vue'),
          meta: { title: '知识库管理' }
        }
      ]
    }
  ]
})

export default router
