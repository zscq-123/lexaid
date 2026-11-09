import request from './request'

const root = '/mock-exams'
const data = res => res.data
export const getMockCatalog = () => request.get(root + '/catalog').then(data)
export const getActiveMock = () => request.get(root + '/active').then(data)
export const getMockHistory = () => request.get(root + '/history').then(data)
export const startMock = setup => request.post(root + '/sessions', setup).then(data)
export const getMock = id => request.get(root + '/sessions/' + id).then(data)
export const saveMockAnswer = (id, position, draft) =>
  request.put(root + '/sessions/' + id + '/answers/' + position, draft).then(data)
export const submitMock = id => request.post(root + '/sessions/' + id + '/submit').then(data)
export const retryMockGrading = id => request.post(root + '/sessions/' + id + '/retry-grading').then(data)
