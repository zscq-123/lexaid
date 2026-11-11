// Run against a disposable LexAid database. This check creates two users and uploads a 21 MB file
// to confirm the resource library has no application-level upload ceiling.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const base = process.env.RESOURCE_TEST_API
if (!base) throw new Error('Set RESOURCE_TEST_API to a test backend, e.g. http://127.0.0.1:18081/api')
const run = randomUUID().slice(0, 8)
const resources = '/resources'

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
  const username = 'resource_' + label + '_' + run
  const password = randomUUID()
  await request(null, 'POST', '/auth/register', { username, password, nickname: label + '_' + run })
  return request(null, 'POST', '/auth/login', { username, password })
}

function draft(fileName, bytes, fields = {}) {
  const form = new FormData()
  for (const [key, value] of Object.entries(fields)) form.set(key, value)
  form.set('file', new Blob([bytes], { type: 'application/octet-stream' }), fileName)
  return form
}

async function download(user, id, expected = 200) {
  const res = await fetch(base + resources + '/' + id + '/download', {
    headers: user ? { Authorization: 'Bearer ' + user.token } : {}
  })
  assert.equal(res.status, expected)
  if (expected !== 200) return null
  assert.match(res.headers.get('cache-control') || '', /no-store/)
  assert.match(res.headers.get('content-disposition') || '', /attachment/)
  assert.match(res.headers.get('content-disposition') || '', /filename\*=UTF-8''/)
  return Buffer.from(await res.arrayBuffer())
}

const [owner, reader] = await Promise.all(['owner', 'reader'].map(account))
await request(null, 'GET', resources, undefined, 401)

const note = Buffer.from('# 正当防卫\n\n防卫限度的判断要点。', 'utf8')
const privateResource = await request(owner, 'POST', resources,
  draft('正当防卫笔记.md', note, {
    title: '正当防卫复习笔记',
    description: '课堂整理，包含起因、时机和限度条件。',
    category: '复习笔记',
    visibility: 'PRIVATE'
  }))
assert.equal(privateResource.visibility, 'PRIVATE')
assert.equal(privateResource.owned, true)
assert.equal(privateResource.fileExtension, 'md')
assert.equal(privateResource.fileName, '正当防卫笔记.md')

assert.equal((await request(reader, 'GET', resources + '?scope=community')).total, 0)
assert.equal((await request(owner, 'GET', resources + '?scope=mine')).total, 1)
await download(reader, privateResource.id, 404)
assert.deepEqual(await download(owner, privateResource.id), note)

const published = await request(owner, 'PUT', resources + '/' + privateResource.id, {
  title: '正当防卫复习笔记（公开版）',
  description: '可下载的课堂整理。',
  category: '复习笔记',
  visibility: 'PUBLIC'
})
assert.equal(published.visibility, 'PUBLIC')
const community = await request(reader, 'GET', resources + '?scope=community&keyword=正当防卫')
assert.equal(community.total, 1)
assert.equal(community.records[0].ownerName, owner.user.nickname)
assert.equal(community.records[0].owned, false)
assert.deepEqual(await download(reader, privateResource.id), note)
assert.equal((await request(reader, 'GET', resources + '?scope=community&format=md')).total, 1)
assert.equal((await request(reader, 'GET', resources + '?scope=community&category=复习笔记')).total, 1)
assert.equal((await request(reader, 'GET', resources + '?scope=community&sort=popular')).total, 1)
const refreshed = (await request(owner, 'GET', resources + '?scope=mine')).records[0]
assert.equal(refreshed.downloadCount, 2)

await request(reader, 'PUT', resources + '/' + privateResource.id,
  { title: '越权', description: '', category: '其他', visibility: 'PRIVATE' }, 404)
await request(reader, 'DELETE', resources + '/' + privateResource.id, undefined, 404)
await request(owner, 'POST', resources,
  draft('伪造.pdf', Buffer.from('<svg onload="alert(1)"/>'), { title: '伪造 PDF' }), 400)

const large = Buffer.alloc(21 * 1024 * 1024, 'a')
const largeResource = await request(owner, 'POST', resources,
  draft('大文件.txt', large, { title: '21 MB 文本', category: '其他', visibility: 'PRIVATE' }))
assert.equal(largeResource.fileSize, large.length)
assert.deepEqual(await download(owner, largeResource.id), large)

await request(owner, 'DELETE', resources + '/' + largeResource.id)
await request(owner, 'DELETE', resources + '/' + privateResource.id)
assert.equal((await request(owner, 'GET', resources + '?scope=mine')).total, 0)
console.log('RESOURCE_OK')
