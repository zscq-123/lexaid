// Run against a disposable LexAid database. This check creates three users, makes two friends,
// and verifies REST history/permissions, live WebSocket delivery, read receipts, image/file
// attachments, download isolation and message recall.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const base = process.env.MESSAGES_TEST_API
if (!base) throw new Error('Set MESSAGES_TEST_API to a test backend, e.g. http://127.0.0.1:18081/api')
if (typeof WebSocket === 'undefined') throw new Error('This check needs a Node.js runtime with global WebSocket (Node.js 22+)')
const run = randomUUID().slice(0, 8)
const wsBase = base.replace(/^http/, 'ws').replace(/\/api\/?$/, '') + '/ws/messages'

async function request(user, method, path, body, expected = 200) {
  const headers = user ? { Authorization: 'Bearer ' + user.token } : {}
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }
  const res = await fetch(base + path, { method, headers, body })
  assert.equal(res.status, expected, method + ' ' + path + ' ' + res.status)
  const result = await res.json()
  assert.equal(result.code, expected, result.message)
  return result.data
}

async function account(label) {
  const username = 'message_' + label + '_' + run
  const password = randomUUID()
  await request(null, 'POST', '/auth/register', { username, password, nickname: label + '_' + run })
  return request(null, 'POST', '/auth/login', { username, password })
}

function attachmentForm(recipientId, kind, fileName, mimeType, bytes, content = '', duration = null) {
  const form = new FormData()
  form.set('recipientId', String(recipientId))
  form.set('kind', kind)
  form.set('content', content)
  if (duration != null) form.set('duration', String(duration))
  form.set('file', new Blob([bytes], { type: mimeType }), fileName)
  return form
}

function wav(seconds = 1, rate = 8000) {
  const dataSize = rate * seconds
  const buffer = Buffer.alloc(44 + dataSize)
  buffer.write('RIFF', 0)
  buffer.writeUInt32LE(36 + dataSize, 4)
  buffer.write('WAVE', 8)
  buffer.write('fmt ', 12)
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)
  buffer.writeUInt16LE(1, 22)
  buffer.writeUInt32LE(rate, 24)
  buffer.writeUInt32LE(rate, 28)
  buffer.writeUInt16LE(1, 32)
  buffer.writeUInt16LE(8, 34)
  buffer.write('data', 36)
  buffer.writeUInt32LE(dataSize, 40)
  buffer.fill(128, 44)
  return buffer
}

async function downloadAttachment(user, id, expected = 200) {
  const res = await fetch(base + '/messages/' + id + '/attachment', {
    headers: user ? { Authorization: 'Bearer ' + user.token } : {}
  })
  assert.equal(res.status, expected)
  if (expected !== 200) return null
  return { headers: res.headers, bytes: Buffer.from(await res.arrayBuffer()) }
}

async function live(user) {
  const { ticket } = await request(user, 'GET', '/messages/ws-ticket')
  const socket = new WebSocket(wsBase + '?ticket=' + encodeURIComponent(ticket))
  const events = []
  const waiters = []
  socket.onmessage = (event) => {
    const payload = JSON.parse(event.data)
    if (payload.type === 'READY' || payload.type === 'PONG') return
    events.push(payload)
    for (const waiter of [...waiters]) {
      if (waiter.type === payload.type && (!waiter.match || waiter.match(payload))) {
        clearTimeout(waiter.timer)
        waiters.splice(waiters.indexOf(waiter), 1)
        waiter.resolve(payload)
      }
    }
  }
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket 未在 5 秒内连接')), 5000)
    socket.onopen = () => { clearTimeout(timer); resolve() }
    socket.onerror = () => { clearTimeout(timer); reject(new Error('WebSocket 连接失败')) }
  })
  return {
    socket,
    events,
    next(type, match) {
      const existing = events.find(event => event.type === type && (!match || match(event)))
      if (existing) {
        events.splice(events.indexOf(existing), 1)
        return Promise.resolve(existing)
      }
      return new Promise((resolve, reject) => {
        const waiter = { type, match, resolve, timer: setTimeout(() => {
          waiters.splice(waiters.indexOf(waiter), 1)
          reject(new Error('等待 WebSocket 事件超时: ' + type))
        }, 5000) }
        waiters.push(waiter)
      })
    }
  }
}

const [a, b, c] = await Promise.all(['alice', 'bob', 'carol'].map(account))
await request(null, 'GET', '/messages/conversations', undefined, 401)
await request(a, 'POST', '/messages', { recipientId: b.user.id, content: '还不是好友' }, 403)

const requestId = await request(a, 'POST', '/community/friends/requests', {
  userId: b.user.id, message: '一起讨论案例'
})
await request(b, 'PUT', '/community/friends/requests/' + requestId, { accept: true })

const aliceLive = await live(a)
const bobLive = await live(b)
const first = await request(a, 'POST', '/messages', { recipientId: b.user.id, content: '你好，Bob' })
assert.equal(first.senderId, a.user.id)
assert.equal(first.recipientId, b.user.id)
const delivered = await bobLive.next('MESSAGE', event => event.message.id === first.id)
assert.equal(delivered.message.content, '你好，Bob')
assert.equal(delivered.unreadTotal, 1)

let conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations.length, 1)
assert.equal(conversations[0].peerId, a.user.id)
assert.equal(conversations[0].unreadCount, 1)
assert.equal(conversations[0].lastMessageMine, false)
let history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records.length, 1)
assert.equal(history.records[0].read, false)
assert.equal(history.friend, true)

const read = await request(b, 'PUT', '/messages/conversations/' + a.user.id + '/read')
assert.equal(read.updated, 1)
assert.equal(read.unreadTotal, 0)
const receipt = await aliceLive.next('READ', event => event.readerId === b.user.id)
assert.equal(receipt.peerId, a.user.id)
history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records[0].read, true)

const reply = await request(b, 'POST', '/messages', { recipientId: a.user.id, content: '你好，Alice' })
const received = await aliceLive.next('MESSAGE', event => event.message.id === reply.id)
assert.equal(received.message.content, '你好，Alice')
assert.equal((await request(a, 'GET', '/messages/unread')).total, 1)
await request(a, 'PUT', '/messages/conversations/' + b.user.id + '/read')
assert.equal((await request(a, 'GET', '/messages/unread')).total, 0)
history = await request(a, 'GET', '/messages/conversations/' + b.user.id)
assert.deepEqual(history.records.map(item => item.content), ['你好，Bob', '你好，Alice'])

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAE0lEQVR4nGM0Ci1mgAEmOAsvBwAxlwECmY+lpgAAAABJRU5ErkJggg==', 'base64')
const image = await request(a, 'POST', '/messages/attachments',
  attachmentForm(b.user.id, 'IMAGE', '法条截图.png', 'image/png', png, '这张图是重点'), 200)
assert.equal(image.kind, 'IMAGE')
assert.equal(image.fileName, '图片.png')
assert.equal(image.mediaType, 'image/png')
assert.equal(image.content, '这张图是重点')
await bobLive.next('MESSAGE', event => event.message.id === image.id)

const pdf = Buffer.from('%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n')
const file = await request(a, 'POST', '/messages/attachments',
  attachmentForm(b.user.id, 'FILE', '案卷材料.pdf', 'application/pdf', pdf), 200)
assert.equal(file.kind, 'FILE')
assert.equal(file.fileName, '案卷材料.pdf')
assert.equal(file.mediaType, 'application/pdf')
await bobLive.next('MESSAGE', event => event.message.id === file.id)

const imageDownload = await downloadAttachment(b, image.id)
assert.match(imageDownload.headers.get('content-type') || '', /image\/png/)
assert.ok(imageDownload.bytes.length > 0)
const fileDownload = await downloadAttachment(b, file.id)
assert.match(fileDownload.headers.get('content-disposition') || '', /attachment/)
assert.deepEqual(fileDownload.bytes, pdf)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 2)
assert.equal(await downloadAttachment(c, image.id, 404), null)

await request(b, 'PUT', '/messages/' + image.id + '/recall', undefined, 403)
const recalledImage = await request(a, 'PUT', '/messages/' + image.id + '/recall')
assert.equal(recalledImage.recalled, true)
assert.equal(recalledImage.content, '')
await bobLive.next('RECALL', event => event.message.id === image.id)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 1)
assert.equal(await downloadAttachment(b, image.id, 404), null)

const recalledFile = await request(a, 'PUT', '/messages/' + file.id + '/recall')
assert.equal(recalledFile.recalled, true)
await bobLive.next('RECALL', event => event.message.id === file.id)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 0)
conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations[0].lastMessage, '消息已撤回')
history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records.length, 4)
assert.equal(history.records[2].recalled, true)
assert.equal(history.records[3].recalled, true)

const voiceBytes = wav(1)
const voice = await request(a, 'POST', '/messages/attachments',
  attachmentForm(b.user.id, 'VOICE', '语音.wav', 'audio/wav', voiceBytes, '', 1), 200)
assert.equal(voice.kind, 'VOICE')
assert.equal(voice.durationSeconds, 1)
assert.equal(voice.fileName, '语音.wav')
await bobLive.next('MESSAGE', event => event.message.id === voice.id)
const voiceDownload = await downloadAttachment(b, voice.id)
assert.match(voiceDownload.headers.get('content-type') || '', /audio\/wav/)
assert.deepEqual(voiceDownload.bytes, voiceBytes)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 1)
conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations[0].lastMessage, '[语音]')
await request(a, 'PUT', '/messages/' + voice.id + '/recall')
assert.equal((await request(b, 'GET', '/messages/unread')).total, 0)
await bobLive.next('RECALL', event => event.message.id === voice.id)
conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations[0].lastMessage, '消息已撤回')
history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records.length, 5)
assert.equal(history.records[4].recalled, true)

const settings = await request(b, 'PUT', '/messages/conversations/' + a.user.id + '/settings',
  { muted: true, pinned: true })
assert.equal(settings.muted, true)
assert.equal(settings.pinned, true)
conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations[0].muted, true)
assert.equal(conversations[0].pinned, true)
const found = await request(b, 'GET', '/messages/conversations/' + a.user.id + '/search?keyword=' + encodeURIComponent('你好'))
assert.ok(found.length >= 1)
assert.ok(found.some(item => item.content.includes('你好')))

const cleared = await request(b, 'DELETE', '/messages/conversations/' + a.user.id)
assert.equal(cleared.peerId, a.user.id)
assert.equal(cleared.clearedBeforeId, voice.id)
await bobLive.next('CLEAR', event => event.peerId === a.user.id)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 0)
assert.equal((await request(b, 'GET', '/messages/conversations')).length, 0)
history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records.length, 0)
assert.equal(history.muted, true)
assert.equal(history.pinned, true)
assert.equal((await request(b, 'GET', '/messages/conversations/' + a.user.id + '/search?keyword=' + encodeURIComponent('你好'))).length, 0)
assert.equal((await request(a, 'GET', '/messages/conversations/' + b.user.id)).records.length, 5)

const afterClear = await request(a, 'POST', '/messages', { recipientId: b.user.id, content: '清空后的新消息' })
await bobLive.next('MESSAGE', event => event.message.id === afterClear.id)
assert.equal((await request(b, 'GET', '/messages/unread')).total, 1)
conversations = await request(b, 'GET', '/messages/conversations')
assert.equal(conversations.length, 1)
assert.equal(conversations[0].lastMessage, '清空后的新消息')
assert.equal(conversations[0].muted, true)
assert.equal(conversations[0].pinned, true)
history = await request(b, 'GET', '/messages/conversations/' + a.user.id)
assert.equal(history.records.length, 1)
assert.equal(history.records[0].content, '清空后的新消息')
await request(b, 'PUT', '/messages/conversations/' + a.user.id + '/read')

const quoted = await request(a, 'POST', '/messages',
  { recipientId: b.user.id, content: '引用清空后的消息', quoteId: afterClear.id })
assert.equal(quoted.quoted.id, afterClear.id)
assert.match(quoted.quoted.preview, /清空后的新消息/)
await bobLive.next('MESSAGE', event => event.message.id === quoted.id)
const forwarded = await request(a, 'POST', '/messages/' + afterClear.id + '/forward', { recipientId: b.user.id })
assert.equal(forwarded.content, '清空后的新消息')
await bobLive.next('MESSAGE', event => event.message.id === forwarded.id)
await request(b, 'POST', '/messages/' + afterClear.id + '/favorite')
const favorites = await request(b, 'GET', '/favorites?type=MESSAGE')
assert.ok(favorites.some(item => item.targetId === afterClear.id))
const deleted = await request(a, 'DELETE', '/messages/' + quoted.id)
assert.equal(deleted.messageId, quoted.id)
assert.equal((await request(a, 'GET', '/messages/conversations/' + b.user.id)).records.some(item => item.id === quoted.id), false)
assert.equal((await request(b, 'GET', '/messages/conversations/' + a.user.id)).records.some(item => item.id === quoted.id), true)
const reminder = await request(a, 'POST', '/messages/' + afterClear.id + '/remind', { minutes: 1 })
assert.ok(reminder.reminderId > 0)

await request(c, 'GET', '/messages/conversations/' + a.user.id, undefined, 403)
await request(c, 'POST', '/messages', { recipientId: a.user.id, content: '陌生人' }, 403)
await request(c, 'PUT', '/messages/conversations/' + a.user.id + '/read', undefined, 403)

await request(b, 'DELETE', '/community/friends/' + a.user.id)
await request(a, 'POST', '/messages', { recipientId: b.user.id, content: '好友已删除' }, 403)
history = await request(a, 'GET', '/messages/conversations/' + b.user.id)
assert.equal(history.friend, false)
assert.equal(history.records.length, 7)

aliceLive.socket.close()
bobLive.socket.close()
console.log('MESSAGES_OK')
