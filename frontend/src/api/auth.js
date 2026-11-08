import request from './request'

export const login = (data) => request.post('/auth/login', data).then((r) => r.data)
export const register = (data) => request.post('/auth/register', data).then((r) => r.data)
export const getMe = () => request.get('/auth/me').then((r) => r.data)

export function uploadAvatar(file) {
  const form = new FormData()
  form.append('avatar', file)
  return request.put('/auth/me/avatar', form).then(r => r.data)
}

export const removeAvatar = () => request.delete('/auth/me/avatar').then(r => r.data)

export function getAvatarBlob(userId, version, signal) {
  return request.get('/users/' + encodeURIComponent(userId) + '/avatar', {
    params: { v: version },
    signal,
    responseType: 'blob',
    validateStatus: status => status === 200 || status === 404
  }).then(blob => blob instanceof Blob && blob.type.startsWith('image/') ? blob : null)
}
