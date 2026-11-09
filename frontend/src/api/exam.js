import request from './request'

export const listQuestionSubjects = () => request.get('/exam/subjects').then((r) => r.data)
export const listCodeBooks = () => request.get('/exam/code-books').then((r) => r.data)
export const getExamProgress = (params, signal) => request.get('/exam/progress', { params, signal }).then(r => r.data)
export const createSession = (data) => request.post('/exam/sessions', data).then((r) => r.data)
export const getSession = (id) => request.get(`/exam/sessions/${id}`).then((r) => r.data)
export const getSessionResult = (id) => request.get(`/exam/sessions/${id}/result`).then((r) => r.data)
export const submitSession = (id, data) => request.post(`/exam/sessions/${id}/submit`, data).then((r) => r.data)
export const retryGrading = (id) => request.post(`/exam/sessions/${id}/retry-grading`).then(r => r.data)
export const examHistory = (params) => request.get('/exam/sessions', { params }).then((r) => r.data)
export const wrongQuestions = (params) => request.get('/wrong-questions', { params }).then((r) => r.data)
export const openScenario = (questionId, after, signal) =>
  request.post(`/wrong-questions/${questionId}/scenarios`, null,
    { params: after ? { after } : {}, signal, timeout: 75000 }).then(r => r.data)
export const answerScenario = (id, choice, signal) =>
  request.post(`/wrong-questions/scenarios/${id}/answer`, { choice }, { signal }).then(r => r.data)
