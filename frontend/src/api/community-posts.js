import request from './request'

const posts = '/community/posts'

export const listPosts = (params) => request.get(posts, { params }).then((response) => response.data)
export const getPost = id => request.get(`${posts}/${encodeURIComponent(id)}`).then(r => r.data)

export function publishPost({ caption, visibility, files }) {
  const form = new FormData()
  form.append('caption', caption)
  form.append('visibility', visibility)
  for (const file of files) form.append('images', file)
  return request.post(posts, form).then((response) => response.data)
}

export const deletePost = (id) => request.delete(`${posts}/${id}`)
export const loadPostImage = (id, imageId, signal) =>
  request.get(`${posts}/${id}/images/${imageId}`, { responseType: 'blob', signal })
export const setPostLike = (id, liked) =>
  liked ? request.put(`${posts}/${id}/like`) : request.delete(`${posts}/${id}/like`)
export const listPostComments = (id, params) =>
  request.get(`${posts}/${id}/comments`, { params }).then((response) => response.data)
export const addPostComment = (id, content) =>
  request.post(`${posts}/${id}/comments`, { content }).then((response) => response.data)
