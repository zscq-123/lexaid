import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi, getMe } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lexaid_token') || '',
    user: JSON.parse(localStorage.getItem('lexaid_user') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token
  },
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('lexaid_token', data.token)
      localStorage.setItem('lexaid_user', JSON.stringify(data.user))
      return data
    },
    async register(form) {
      return registerApi(form)
    },
    async fetchMe() {
      const user = await getMe()
      this.user = user
      localStorage.setItem('lexaid_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('lexaid_token')
      localStorage.removeItem('lexaid_user')
    }
  }
})
