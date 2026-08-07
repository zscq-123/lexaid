import request from './request'

export const statsOverview = () => request.get('/stats/overview').then((r) => r.data)
export const statsDaily = (days = 30) => request.get('/stats/daily', { params: { days } }).then((r) => r.data)
export const statsCategory = () => request.get('/stats/category-accuracy').then((r) => r.data)
export const statsRecords = (params) => request.get('/stats/records', { params }).then((r) => r.data)
