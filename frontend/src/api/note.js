import request from './request'

export const listNotes = (params) => request.get('/notes', { params }).then((r) => r.data)
export const createNote = (data) => request.post('/notes', data).then((r) => r.data)
export const updateNote = (id, data) => request.put(`/notes/${id}`, data).then((r) => r.data)
export const deleteNote = (id) => request.delete(`/notes/${id}`).then((r) => r.data)
