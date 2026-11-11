import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import { activeExamFor, roomPath } from '../views/mock-exams/active'

const routes = [
  { path: '/mock-exams/run/:id(\\d+)', component: () => import('../views/mock-exams/MockRoom.vue'), meta: { mockRoom: true } },
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
      { path: 'resources', component: () => import('../views/resources/ResourceLibrary.vue') },
      { path: 'messages', component: () => import('../views/messages/MessageCenter.vue') },
      { path: 'community', component: () => import('../views/community/Community.vue') },
      { path: 'community/posts/:id', component: () => import('../views/community/PostDetail.vue') },
      { path: 'exam/setup', component: () => import('../views/exam/ExamSetup.vue') },
      { path: 'exam/run/:sessionId', component: () => import('../views/exam/ExamRun.vue') },
      { path: 'exam/result/:sessionId', component: () => import('../views/exam/ExamResult.vue') },
      { path: 'mock-exams', component: () => import('../views/mock-exams/MockLobby.vue') },
      { path: 'mock-exams/result/:id(\\d+)', component: () => import('../views/mock-exams/MockResult.vue') },
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
router.beforeEach(async (to) => {
  const store = useUserStore()
  if (!store.token && to.path !== '/login') {
    return { path: '/login' }
  }
  if (store.token) {
    const id = await activeExamFor(store)
    if (id && to.path !== roomPath(id)) return { path: roomPath(id), replace: true }
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
