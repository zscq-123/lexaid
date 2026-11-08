import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/login', component: () => import('../views/login/Login.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/dashboard/Dashboard.vue') },
      { path: 'provisions', component: () => import('../views/provisions/ProvisionList.vue') },
      { path: 'provisions/:id', component: () => import('../views/provisions/ProvisionDetail.vue') },
      { path: 'cases', component: () => import('../views/cases/CaseList.vue') },
      { path: 'cases/:id', component: () => import('../views/cases/CaseDetail.vue') },
      { path: 'viewpoints', component: () => import('../views/viewpoints/ViewpointList.vue') },
      { path: 'viewpoints/:id', component: () => import('../views/viewpoints/ViewpointDetail.vue') },
      { path: 'community', component: () => import('../views/community/Community.vue') },
      { path: 'community/posts/:id', component: () => import('../views/community/PostDetail.vue') },
      { path: 'exam/setup', component: () => import('../views/exam/ExamSetup.vue') },
      { path: 'exam/run/:sessionId', component: () => import('../views/exam/ExamRun.vue') },
      { path: 'exam/result/:sessionId', component: () => import('../views/exam/ExamResult.vue') },
      { path: 'wrong-book', component: () => import('../views/wrong/WrongBook.vue') },
      { path: 'ai-chat', component: () => import('../views/ai/AiChat.vue') },
      { path: 'notes', component: () => import('../views/notes/NoteList.vue') },
      { path: 'stats', component: () => import('../views/stats/Stats.vue') },
      { path: 'profile', component: () => import('../views/profile/Profile.vue') },
      {
        path: 'admin',
        component: () => import('../views/admin/AdminDashboard.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳登录页
router.beforeEach((to) => {
  const store = useUserStore()
  if (!store.token && to.path !== '/login') {
    return { path: '/login' }
  }
  if (store.token && to.path === '/login') {
    return { path: '/dashboard' }
  }
  if (to.meta.requiresAdmin && store.user?.role !== 'ADMIN') {
    return { path: '/dashboard' }
  }
  return true
})

export default router
