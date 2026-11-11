import request from './request'

const path = '/resources'

export const listResources = (params, signal) =>
  request.get(path, { params, signal }).then((response) => response.data)

export function uploadResource({ title, description, category, visibility, file }, onUploadProgress) {
  const form = new FormData()
  form.append('title', title)
  form.append('description', description)
  form.append('category', category)
  form.append('visibility', visibility)
  form.append('file', file)
  // Large files may take longer than the default request timeout.
  return request.post(path, form, { timeout: 0, onUploadProgress }).then((response) => response.data)
}

export const updateResource = (id, data) => request.put(`${path}/${id}`, data).then((response) => response.data)
export const deleteResource = (id) => request.delete(`${path}/${id}`)
export const downloadResource = (id, signal) =>
  request.get(`${path}/${id}/download`, { responseType: 'blob', timeout: 0, signal })
