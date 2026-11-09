import request from './request'

// 法条
export const listProvisionCodes = (jurisdiction, signal) =>
  request.get('/provisions/outline/codes', { params: { jurisdiction: jurisdiction || undefined }, signal }).then(r => r.data)
export const getProvisionOutline = (jurisdiction, code, signal) =>
  request.get('/provisions/outline', { params: { jurisdiction, code }, signal }).then(r => r.data)
export const listProvisions = (params) => request.get('/provisions', { params }).then((r) => r.data)
export const listProvisionCategories = () => request.get('/provisions/categories').then((r) => r.data)
export const getProvision = (id) => request.get(`/provisions/${id}`).then((r) => r.data)
export const getProvisionRelated = (id) => request.get(`/provisions/${id}/related`).then((r) => r.data)

// 案例
export const listCases = (params) => request.get('/cases', { params }).then((r) => r.data)
export const listCaseCourts = () => request.get('/cases/courts').then((r) => r.data)
export const getCase = (id) => request.get(`/cases/${id}`).then((r) => r.data)
export const getCaseRelated = (id) => request.get(`/cases/${id}/related`).then((r) => r.data)

// 观点
export const listViewpoints = (params) => request.get('/viewpoints', { params }).then((r) => r.data)
export const getViewpoint = (id, signal) => request.get(`/viewpoints/${id}`, { signal }).then((r) => r.data)
export const getViewpointRelated = (id, signal) => request.get(`/viewpoints/${id}/related`, { signal }).then((r) => r.data)

// 全局搜索
export const globalSearch = (keyword) => request.get('/search', { params: { keyword } }).then((r) => r.data)
