import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi, getMe, updateMe, uploadAvatar, removeAvatar } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lexaid_token') || '',
    user: JSON.parse(localStorage.getItem('lexaid_user') || 'null'),
    profileRevision: 0,
    avatarSaving: false,
    profileSaving: false
  }),
  getters: {
    isLogin: (s) => !!s.token
  },
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.profileRevision++
      this.avatarSaving = false
      this.profileSaving = false
      this.token = data.token
      localStorage.setItem('lexaid_token', data.token)
      this.setUser(data.user)
      return data
    },
    async register(form) {
      return registerApi(form)
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('lexaid_user', JSON.stringify(user))
    },
    async fetchMe() {
      if (!this.token || this.avatarSaving || this.profileSaving) return false
      const token = this.token
      const revision = ++this.profileRevision
      const user = await getMe()
      if (this.token !== token || revision !== this.profileRevision) return false
      this.setUser(user)
      return true
    },
    async saveAvatar(file) {
      if (!this.token || this.avatarSaving || this.profileSaving) return false
      const token = this.token
      const revision = ++this.profileRevision
      this.avatarSaving = true
      try {
        const user = file ? await uploadAvatar(file) : await removeAvatar()
        if (this.token !== token || revision !== this.profileRevision) return false
        this.setUser(user)
        return true
      } finally {
        if (this.token === token && revision === this.profileRevision) this.avatarSaving = false
      }
    },
    async saveProfile(profile) {
      if (!this.token || this.profileSaving || this.avatarSaving) return false
      const token = this.token
      const revision = ++this.profileRevision
      this.profileSaving = true
      try {
        const user = await updateMe(profile)
        if (this.token !== token || revision !== this.profileRevision) return false
        this.setUser(user)
        return true
      } finally {
        if (this.token === token && revision === this.profileRevision) this.profileSaving = false
      }
    },
    logout() {
      this.profileRevision++
      this.avatarSaving = false
      this.profileSaving = false
      this.token = ''
      this.user = null
      localStorage.removeItem('lexaid_token')
      localStorage.removeItem('lexaid_user')
    }
  }
})
