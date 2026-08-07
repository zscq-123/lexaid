import request from './request'

export const chat = (data) => request.post('/ai/chat', data).then((r) => r.data)
export const chatHistory = () => request.get('/ai/history').then((r) => r.data)
