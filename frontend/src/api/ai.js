import request from './request'

export const chat = (data) => request.post('/ai/chat', data, { timeout: 90000 }).then((r) => r.data)
export const chatHistory = () => request.get('/ai/history').then((r) => r.data)
export const getChatSource = (type, id, question) =>
  request.get(`/ai/sources/${encodeURIComponent(type)}/${encodeURIComponent(id)}`, {
    // Keep even percent-encoded Chinese questions within normal HTTP request-line limits.
    params: question ? { q: Array.from(question).slice(0, 600).join('') } : {}
  }).then(r => r.data)
