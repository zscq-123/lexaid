// Run against a disposable LexAid database. This check creates three users.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const base = process.env.COMMUNITY_TEST_API
if (!base) throw new Error('Set COMMUNITY_TEST_API to a test backend, e.g. http://127.0.0.1:18081/api')
const run = randomUUID().slice(0, 8)
const friends = '/community/friends'
const posts = '/community/posts'

async function request(user, method, path, body, expected = 200) {
  const headers = user ? { Authorization: 'Bearer ' + user.token } : {}
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }
  const res = await fetch(base + path, { method, headers, body })
  assert.equal(res.status, expected, method + ' ' + path + ' ' + res.status)
  if (res.headers.get('content-type')?.startsWith('image/')) {
    assert.match(res.headers.get('cache-control') || '', /no-store/)
    return Buffer.from(await res.arrayBuffer())
  }
  const result = await res.json()
  assert.equal(result.code, expected, result.message)
  return result.data
}

async function account(label) {
  const username = 'community_' + label + '_' + run
  const password = randomUUID()
  await request(null, 'POST', '/auth/register', { username, password, nickname: label + '_' + run })
  return request(null, 'POST', '/auth/login', { username, password })
}

function draft(visibility, caption, pictures = []) {
  const form = new FormData()
  form.set('visibility', visibility)
  form.set('caption', caption)
  for (const bytes of pictures) form.append('images', new Blob([bytes], { type: 'image/png' }), 'photo.png')
  return form
}

const [a, b, c] = await Promise.all(['owner', 'friend', 'stranger'].map(account))
await request(null, 'GET', posts, undefined, 401)
const photos = [
  'iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAE0lEQVR4nGM0Ci1mgAEmOAsvBwAxlwECmY+lpgAAAABJRU5ErkJggg==',
  'iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAE0lEQVR4nGPcUxDMAANMcBZeDgBMOwGHh8NzLAAAAABJRU5ErkJggg=='
].map(encoded => Buffer.from(encoded, 'base64'))
const myAvatar = '/auth/me/avatar'
const avatarPath = '/users/' + a.user.id + '/avatar'
function avatar(bytes) {
  const form = new FormData()
  form.set('avatar', new Blob([bytes], { type: 'image/png' }), 'avatar.png')
  return form
}
assert.equal(a.user.avatarVersion, null)
await request(b, 'GET', avatarPath, undefined, 404)
await request(null, 'PUT', myAvatar, avatar(photos[0]), 401)
const firstVersion = (await request(a, 'PUT', myAvatar, avatar(photos[0]))).avatarVersion
assert.ok(firstVersion)
const firstImage = await request(b, 'GET', avatarPath)
await request(a, 'PUT', myAvatar, avatar(Buffer.from('<svg/>')), 400)
assert.equal((await request(a, 'GET', '/auth/me')).avatarVersion, firstVersion)
assert.deepEqual(await request(b, 'GET', avatarPath), firstImage)
const nextVersion = (await request(a, 'PUT', myAvatar, avatar(photos[1]))).avatarVersion
assert.ok(nextVersion && nextVersion !== firstVersion)
assert.notDeepEqual(await request(b, 'GET', avatarPath), firstImage)
assert.equal((await request(b, 'GET', '/auth/me')).avatarVersion, null)
await request(null, 'GET', avatarPath, undefined, 401)
await request(a, 'POST', posts, draft('PUBLIC', ' '), 400)
await request(a, 'POST', posts, draft('PUBLIC', '无效图片', [Buffer.from('<svg/>')]), 400)
await request(a, 'POST', posts, draft('PUBLIC', '', [Buffer.alloc(4 * 1024 * 1024 + 1)]), 400)
const pub = await request(a, 'POST', posts, draft('PUBLIC', '公开讨论'))
const entry = await request(a, 'POST', posts, draft('FRIENDS', '读书笔记', photos))
assert.equal(entry.authorId, a.user.id)
assert.equal(entry.authorAvatarVersion, nextVersion)
assert.equal(entry.imageIds.length, 2)
const path = posts + '/' + entry.id
const imagePath = id => path + '/images/' + id
const visible = async (actor, scope) => (await request(actor, 'GET', posts + '?scope=' + scope)).records.map(p => p.id)
assert.ok((await visible(c, 'all')).includes(pub.id))
assert.ok(!(await visible(c, 'all')).includes(entry.id))
assert.ok((await visible(a, 'mine')).includes(entry.id))
assert.notDeepEqual(await request(a, 'GET', imagePath(entry.imageIds[0])), await request(a, 'GET', imagePath(entry.imageIds[1])))
await request(c, 'GET', imagePath(entry.imageIds[0]), undefined, 404)
await request(c, 'PUT', path + '/like', undefined, 404)
await request(c, 'GET', path + '/comments', undefined, 404)
await request(c, 'POST', path + '/comments', { content: '看不到的帖子' }, 404)

const invitation = { userId: b.user.id, message: '一起讨论案例' }
assert.ok((await request(a, 'GET', friends + '/search?q=' + b.user.username)).some(p => p.id === b.user.id))
await request(a, 'POST', friends + '/requests', { userId: a.user.id }, 400)
const ids = await Promise.all(Array.from({ length: 3 }, () => request(a, 'POST', friends + '/requests', invitation)))
assert.equal(new Set(ids).size, 1)
const pending = (await request(b, 'GET', friends + '/requests')).find(r => r.requestId === ids[0])
assert.equal(pending.message, invitation.message)
assert.equal(pending.direction, 'INCOMING')
assert.equal(pending.person.avatarVersion, nextVersion)
await request(b, 'POST', friends + '/requests', { userId: a.user.id }, 409)
await request(b, 'GET', imagePath(entry.imageIds[0]), undefined, 404)
const decide = (actor, id, accept, status) => request(actor, 'PUT', friends + '/requests/' + id, { accept }, status)
await decide(a, ids[0], true, 404)
await decide(c, ids[0], true, 404)
await decide(b, ids[0], false)
const next = await request(a, 'POST', friends + '/requests', invitation)
assert.notEqual(next, ids[0])
await decide(b, ids[0], true, 404)
await decide(b, next, true)
assert.ok((await request(a, 'GET', friends)).some(p => p.id === b.user.id))
assert.ok((await request(b, 'GET', friends)).some(p => p.id === a.user.id))
assert.ok((await visible(b, 'friends')).includes(entry.id))
assert.equal((await request(b, 'GET', friends)).find(person => person.id === a.user.id).avatarVersion, nextVersion)
await request(b, 'GET', imagePath(entry.imageIds[1]))
await request(b, 'DELETE', path, undefined, 404)
await request(b, 'GET', posts + '/' + pub.id + '/images/' + entry.imageIds[0], undefined, 404)
await Promise.all(Array.from({ length: 3 }, () => request(b, 'PUT', path + '/like')))
await request(b, 'POST', path + '/comments', { content: '可以看原案例吗？' })
let record = (await request(b, 'GET', posts + '?scope=friends')).records.find(p => p.id === entry.id)
assert.equal(record.liked, true)
assert.equal(record.likeCount, 1)
assert.equal(record.commentCount, 1)
assert.equal((await request(b, 'GET', path + '/comments')).records[0].authorId, b.user.id)
await request(b, 'DELETE', path + '/like')
assert.equal((await request(a, 'GET', posts + '?scope=mine')).records.find(p => p.id === entry.id).likeCount, 0)
await request(b, 'DELETE', friends + '/' + a.user.id)
assert.ok(!(await visible(b, 'all')).includes(entry.id))
await request(b, 'GET', imagePath(entry.imageIds[0]), undefined, 404)
await request(b, 'GET', path + '/comments', undefined, 404)
await request(a, 'DELETE', path)
await request(a, 'GET', imagePath(entry.imageIds[0]), undefined, 404)
await request(a, 'DELETE', posts + '/' + pub.id)
assert.equal((await request(a, 'DELETE', myAvatar)).avatarVersion, null)
assert.equal((await request(a, 'GET', '/auth/me')).avatarVersion, null)
await request(b, 'GET', avatarPath, undefined, 404)
console.log('Community API checks passed for avatars, posting, media, comments, likes, requests and friendship visibility.')
