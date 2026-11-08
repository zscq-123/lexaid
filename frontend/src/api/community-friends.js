import request from './request'

const base = '/community/friends'
export const listFriends = () => request.get(base).then(r => r.data)
export const listInvitations = () => request.get(base + '/requests').then(r => r.data)
export const findPeople = q => request.get(base + '/search', { params: { q } }).then(r => r.data)
export const getPerson = id => request.get(base + '/users/' + id).then(r => r.data)
export const sendInvitation = (userId, message) => request.post(base + '/requests', { userId, message }).then(r => r.data)
export const answerInvitation = (requestId, accept) => request.put(base + '/requests/' + requestId, { accept })
export const deleteFriend = id => request.delete(base + '/' + id)
