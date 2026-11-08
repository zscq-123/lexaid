import request from './request'

export const adminOverview = (days = 14) =>
  request.get('/admin/overview', { params: { days } }).then((r) => r.data)

export const adminUsers = (params) =>
  request.get('/admin/users', { params }).then((r) => r.data)

export const adminUser = (id) =>
  request.get(`/admin/users/${encodeURIComponent(id)}`).then((r) => r.data)
