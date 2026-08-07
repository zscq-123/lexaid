import request from './request'

export const addFavorite = (data) => request.post('/favorites', data).then((r) => r.data)
export const listFavorites = (params) => request.get('/favorites', { params }).then((r) => r.data)
export const removeFavorite = (id) => request.delete(`/favorites/${id}`).then((r) => r.data)
