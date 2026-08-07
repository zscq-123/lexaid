import request from './request'

export const login = (data) => request.post('/auth/login', data).then((r) => r.data)
export const register = (data) => request.post('/auth/register', data).then((r) => r.data)
export const getMe = () => request.get('/auth/me').then((r) => r.data)
