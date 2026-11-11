import request from './request'

const base = '/messages'

export const listConversations = (signal) =>
  request.get(base + '/conversations', { signal, silent: true }).then((response) => response.data)

export const getConversation = (peerId, params, signal) =>
  request.get(`${base}/conversations/${peerId}`, { params, signal, silent: true }).then((response) => response.data)

export const sendDirectMessage = (recipientId, content, quoteId) =>
  request.post(base, { recipientId, content, quoteId }).then((response) => response.data)

export function sendAttachmentMessage({ recipientId, content, kind, file, duration, quoteId }, onUploadProgress) {
  const form = new FormData()
  form.append('recipientId', String(recipientId))
  form.append('content', content)
  form.append('kind', kind)
  if (duration != null) form.append('duration', String(duration))
  if (quoteId != null) form.append('quoteId', String(quoteId))
  form.append('file', file)
  return request.post(base + '/attachments', form, { timeout: 0, onUploadProgress })
    .then((response) => response.data)
}

export const forwardMessage = (id, recipientId) =>
  request.post(`${base}/${id}/forward`, { recipientId }).then((response) => response.data)

export const deleteMessage = (id) =>
  request.delete(`${base}/${id}`).then((response) => response.data)

export const favoriteMessage = (id) =>
  request.post(`${base}/${id}/favorite`).then((response) => response.data)

export const translateMessage = (id) =>
  request.post(`${base}/${id}/translate`).then((response) => response.data)

export const remindMessage = (id, minutes) =>
  request.post(`${base}/${id}/remind`, { minutes }).then((response) => response.data)

export const loadMessageAttachment = (id, signal, { silent = true } = {}) =>
  request.get(`${base}/${id}/attachment`, { responseType: 'blob', signal, silent })

export const recallMessage = (id) =>
  request.put(`${base}/${id}/recall`, null, { silent: true }).then((response) => response.data)

export const updateConversationSettings = (peerId, data) =>
  request.put(`${base}/conversations/${peerId}/settings`, data).then((response) => response.data)

export const searchConversation = (peerId, keyword, signal) =>
  request.get(`${base}/conversations/${peerId}/search`, { params: { keyword }, signal, silent: true })
    .then((response) => response.data)

export const clearConversation = (peerId) =>
  request.delete(`${base}/conversations/${peerId}`).then((response) => response.data)

export const markConversationRead = (peerId) =>
  request.put(`${base}/conversations/${peerId}/read`, null, { silent: true }).then((response) => response.data)

export const getUnreadTotal = (signal) =>
  request.get(base + '/unread', { signal, silent: true }).then((response) => response.data)

export const getMessageTicket = () =>
  request.get(base + '/ws-ticket', { silent: true }).then((response) => response.data)
