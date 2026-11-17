// Run only against an isolated database: this check creates a user, practice sessions and notes.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const base = process.env.LEXAID_CHECK_API || 'http://127.0.0.1:8089/api'
const site = process.env.LEXAID_CHECK_SITE || 'http://127.0.0.1:5179'
if (process.env.LEXAID_CHECK_ISOLATED !== 'yes') throw Error('Set LEXAID_CHECK_ISOLATED=yes after selecting an isolated test database.')
const user = { username: `upgrade_${Date.now()}`, password: 'Regression-check-2026', nickname: '学习升级回归' }
let token
async function api(path, body, expected = 200) {
  const response = await fetch(base + path, { method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined })
  const result = await response.json()
  assert.equal(result.code, expected, `${path}: ${result.message}`)
  return result.data
}
await api('/auth/register', user)
const login = await api('/auth/login', user)
token = login.token
async function answer(question, value) {
  const { sessionId } = await api('/exam/sessions', { sourceType: 'QUESTION', sourceId: question, count: 1 })
  return api(`/exam/sessions/${sessionId}/submit`, { answers: value === undefined ? [] : [{ questionId: question, answer: value }] })
}
const quality = await api('/exam/quality')
assert.ok(quality.find(r => r.reason === 'ELIGIBLE')?.count > 0)
assert.ok(quality.filter(r => r.reason !== 'ELIGIBLE').reduce((n,r) => n+r.count,0) > 0)
await api('/admin/question-quality', undefined, 403)
await api('/exam/sessions', { sourceType: 'QUESTION', sourceId: 10000000, count: 1 }, 400)
await api('/exam/sessions', { sourceType: 'QUESTION', sourceId: 10024497, count: 1 }, 400)
const skipped = await answer(1)
assert.equal(skipped.details[0].gradingStatus, 'SKIPPED')
assert.equal(skipped.maxScore, 0)
assert.equal((await api('/wrong-questions')).total, 0)
assert.equal((await api('/exam/progress')).answered, 0)
await answer(1, 'B')
await answer(1)
assert.equal((await api('/wrong-questions')).records[0].userAnswer, 'B')
await answer(1, 'A')
assert.equal((await api('/wrong-questions')).total, 0)
await answer(2, 'A')
await answer(15, '[ ]')
await answer(8, 'A')
const stats = await api('/stats/overview')
for (const [key, value] of Object.entries({ totalAnswer: 4, distinctQuestions: 3, repeatedAnswer: 1,
  skippedAnswer: 3, totalCorrect: 2, totalIncorrect: 2, gradedAnswer: 4, accuracy: 50 })) assert.equal(stats[key], value, key)
assert.equal((await api('/exam/progress')).answered, 3)
assert.equal((await api('/wrong-questions')).total, 1)
assert.equal((await api('/wrong-questions?all=true&questionId=8')).records[0].questionId, 8)
await api('/notes', { targetType: 'QUESTION', targetId: 8, content: '区分责任形式并核对依据。 #合同法 #易错点' })
await api('/notes', { targetType: 'PROVISION', targetId: 4, content: '核对版本与适用条件。 #刑法' })
assert.equal((await api('/notes?targetType=QUESTION')).length, 1)
assert.equal((await api('/notes?targetType=PROVISION')).length, 1)
const retrieval = []
for (const [query, law, article] of [
  ['查询《刑法》第20条', '刑法', '第二十条'], ['刑法第287条之二', '刑法', '第二百八十七条之二'],
  ['查一下刑法第20条', '刑法', '第二十条'], ['帮我查一下现行刑法第20条', '刑法', '第二十条'],
  ['《民法典》第577条', '民法典', '第五百七十七条'], ['《民法典》第1198条', '民法典', '第一千一百九十八条'],
  ['《行政处罚法》第33条', '行政处罚法', '第三十三条'], ['《行政诉讼法》第2条', '行政诉讼法', '第二条']
]) {
  const chat = await api('/ai/chat', { message: query })
  assert.ok(chat.refs.length > 0, query)
  assert.ok(chat.refs.every(r => r.type === 'PROVISION' && r.title === `中华人民共和国${law} ${article}`), query)
  retrieval.push({ query, returned: chat.refs.length, relevantInFirstFive: Math.min(chat.refs.length,5), precisionAtReturnedFive: 1 })
}
assert.equal((await api('/ai/chat', { message: '《不存在法》第9999条' })).refs.length, 0)
const provisions = await api('/provisions?keyword=' + encodeURIComponent('《刑法》第20条'))
assert.ok(provisions.records.length > 0 && provisions.records.every(p => p.lawName === '中华人民共和国刑法' && p.articleNo === '第二十条'))
const versions = await api('/provisions/4/versions')
assert.ok(versions.length > 1)

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const { chromium } = require('playwright')
const browser = await chromium.launch(process.env.LEXAID_CHECK_EXECUTABLE ? { executablePath: process.env.LEXAID_CHECK_EXECUTABLE } : {})
const output = resolve('scripts/shots/learning-upgrade')
await mkdir(output, { recursive: true })
const errors = []
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } })
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('lexaid_token',token); localStorage.setItem('lexaid_user',JSON.stringify(user))
  }, login)
  page.on('pageerror', e => errors.push(e.message))
  page.on('response', r => { if (r.url().includes('/api/') && r.status() >= 400) errors.push(`${r.status()} ${r.url()}`) })
  await page.goto(site + '/exam/setup')
  await page.getByText('围绕学习内容组卷', { exact: true }).click()
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/api/provisions?') && new URL(r.url()).searchParams.get('keyword') === '刑法第20条'),
    page.getByRole('combobox', { name: '选择学习内容', exact: true }).fill('刑法第20条')
  ])
  await page.getByRole('option').filter({ hasText: '中华人民共和国刑法 第二十条' }).first().click()
  assert.equal(await page.getByText('实体 ID', { exact: true }).count(), 0)
  await page.getByText('组卷设置', { exact: true }).click()
  await page.screenshot({ path: resolve(output,'setup-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '开始答题', exact: true }).click()
  await page.waitForURL('**/exam/run/*')
  await page.goto(site + '/stats')
  await page.getByText('实际作答 4 次 = 首次作答 3 道 + 重复作答 1 次。', { exact: true }).waitFor()
  await page.screenshot({ path: resolve(output,'stats-desktop.png'), fullPage: true })
  await page.goto(site + '/notes')
  await page.getByText('区分责任形式并核对依据。 #合同法 #易错点', { exact: true }).waitFor()
  await page.goto(site + '/wrong-book?question=8')
  await page.getByText('记录错因与关联依据', { exact: true }).click()
  await page.getByText('区分责任形式并核对依据。 #合同法 #易错点', { exact: true }).waitFor()
  await page.goto(site + '/provisions/4')
  await page.getByLabel('来源与核验信息').waitFor()
  await page.getByRole('link').filter({ hasText: '生效 2021-03-01' }).click()
  await page.getByText('当前查看 · 生效 2021-03-01', { exact: false }).waitFor()
  await page.screenshot({ path: resolve(output,'provision-versions.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  for (const route of ['/dashboard','/exam/setup','/stats','/wrong-book','/notes']) {
    await page.goto(site + route)
    const ready = {
      '/dashboard': page.getByLabel('学习专题'),
      '/exam/setup': page.getByText(/道可练，/),
      '/stats': page.getByText('实际作答 4 次 = 首次作答 3 道 + 重复作答 1 次。', { exact: true }),
      '/wrong-book': page.getByText('记录错因与关联依据', { exact: true }),
      '/notes': page.getByText('区分责任形式并核对依据。 #合同法 #易错点', { exact: true })
    }[route]
    await ready.waitFor()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), route + ' mobile overflow')
    if (route === '/dashboard') {
      assert.equal(await page.getByLabel('学习专题').locator('option').count(),6)
      await page.getByLabel('学习专题').selectOption('5')
    }
    await page.screenshot({ path: resolve(output,route.replaceAll('/','') + '-mobile.png'), fullPage: true })
  }
  assert.deepEqual(errors, [])
} finally { await browser.close() }
await writeFile(resolve(output,'report.json'),JSON.stringify({ quality, stats, retrieval, versions: versions.length, browserErrors: errors },null,2))
console.log(JSON.stringify({ status: 'passed', quality, stats, retrieval, versions: versions.length, output },null,2))
