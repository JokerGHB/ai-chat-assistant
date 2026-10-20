import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '../api'

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/register', component: () => import('../views/RegisterView.vue') },
  { path: '/chat', component: () => import('../views/ChatView.vue') },
  { path: '/admin', component: () => import('../views/AdminView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫：登录态与角色控制
router.beforeEach((to) => {
  const token = getToken()
  const publicPages = ['/login', '/register']

  // 未登录访问受保护页 → 登录页
  if (!token && !publicPages.includes(to.path)) return '/login'
  // 已登录访问登录/注册页 → 聊天页
  if (token && publicPages.includes(to.path)) return '/chat'
  // 管理后台要求管理员角色
  if (to.path === '/admin' && getUser()?.role !== 'admin') return '/chat'
  return true
})

export default router
