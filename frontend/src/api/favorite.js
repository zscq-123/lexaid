import request from './request'

export const addFavorite = (data) => request.post('/favorites', data).then((r) => r.data)
export const listFavorites = (params) => request.get('/favorites', { params }).then((r) => r.data)
export const removeFavorite = (id) => request.delete(`/favorites/${id}`).then((r) => r.data)
export const listFavoriteFolders = () => request.get('/favorites/folders').then(r => r.data)
export const createFavoriteFolder = (name) => request.post('/favorites/folders', { name }).then(r => r.data)
export const renameFavoriteFolder = (id, name) => request.put(`/favorites/folders/${id}`, { name }).then(r => r.data)
export const deleteFavoriteFolder = (id) => request.delete(`/favorites/folders/${id}`).then(r => r.data)
export const moveFavorite = (id, folderId) => request.put(`/favorites/${id}/folder`, { folderId }).then(r => r.data)
