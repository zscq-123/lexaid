import axios from 'axios'
import { useUserStore } from '../stores/user'
import router from '../router'

const apiBaseURL = window.lexaidConfig?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || '/api'

const service = axios.create({
  baseURL: apiBaseURL,
  timeout: 30000
})

// 自动携带 token
service.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

// 统一错误提示，401 跳登录
service.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code !== undefined && body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    if (axios.isCancel(err)) return Promise.reject(err)
    const resp = err.response
    if (resp && resp.status === 401) {
      const store = useUserStore()
      const sentAuthorization = err.config?.headers?.Authorization
      if (store.token && sentAuthorization === 'Bearer ' + store.token) {
        store.logout()
        if (router.currentRoute.value.path !== '/login') {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
        }
      } else if (!sentAuthorization && !store.token) {
        ElMessage.error(resp.data?.message || '请先登录')
      }
    } else if (resp && resp.data && resp.data.message) {
      ElMessage.error(resp.data.message)
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(err)
  }
)

export default service
