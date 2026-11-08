import request from './request'

export const listQuestionCategories = () => request.get('/exam/categories').then((r) => r.data)
export const createSession = (data) => request.post('/exam/sessions', data).then((r) => r.data)
export const getSession = (id) => request.get(`/exam/sessions/${id}`).then((r) => r.data)
export const getSessionResult = (id) => request.get(`/exam/sessions/${id}/result`).then((r) => r.data)
export const submitSession = (id, data) => request.post(`/exam/sessions/${id}/submit`, data).then((r) => r.data)
export const retryGrading = (id) => request.post(`/exam/sessions/${id}/retry-grading`).then(r => r.data)
export const examHistory = (params) => request.get('/exam/sessions', { params }).then((r) => r.data)
export const wrongQuestions = (params) => request.get('/wrong-questions', { params }).then((r) => r.data)
