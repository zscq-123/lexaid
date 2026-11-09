// UI regression with isolated API fixtures; verify no reference is shown before answering.
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')
const base = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    let submitted = false
    let nextRequests = 0
    const item = {
      id: 41, round: 1, topic: '防卫限度', origin: '内置练习', stem: '仓库交货纠纷中的新案情',
      options: [{ key: 'A', text: '选项一' }, { key: 'B', text: '选项二' }, { key: 'C', text: '选项三' }, { key: 'D', text: '选项四' }]
    }
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'scenario-fixture'))
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const req = route.request()
      const url = new URL(req.url())
      let data
      if (url.pathname === '/api/mock-exams/active') return route.fulfill({ json: { code: 200, data: null } })
      if (url.pathname === '/api/auth/me') data = { id: 1, username: 'learner' }
      else if (url.pathname === '/api/wrong-questions') data = { total: 2, records: [
        { questionId: 1, type: 'SINGLE', stem: '原题', correctAnswer: 'A', userAnswer: 'B', isCorrect: false },
        { questionId: 2, type: 'JUDGE', stem: '已答对的题', isCorrect: true }
      ] }
      else if (url.pathname === '/api/wrong-questions/1/scenarios') {
        if (url.searchParams.has('after')) {
          assert.equal(url.searchParams.get('after'), '41')
          nextRequests++
          data = { ...item, id: 42, round: 2, stem: '果园纠纷的新案情' }
        } else data = { ...item, ...(submitted ? { selected: 'A', correctAnswer: 'B', explanation: '新案解释', comparison: '事实对照', basis: '限度依据' } : {}) }
      } else if (url.pathname === '/api/wrong-questions/scenarios/41/answer') {
        assert.deepEqual(req.postDataJSON(), { choice: 'A' })
        submitted = true
        data = { ...item, selected: 'A', correctAnswer: 'B', explanation: '新案解释', comparison: '事实对照', basis: '限度依据' }
      } else throw Error('Unexpected route: '+url.pathname)
      await route.fulfill({ json: { code: 200, data } })
    })
    await page.goto(base + '/wrong-book')
    const review = page.getByRole('button', { name: '情景迁移练习' })
    await review.waitFor()
    assert.equal(await review.count(), 1)
    await review.click()
    const dialog = page.getByRole('dialog')
    await dialog.getByText(item.stem).waitFor()
    assert.equal(await dialog.getByText('原题参考依据').count(), 0)
    assert.ok(await dialog.getByRole('button', { name: '提交答案' }).isDisabled())
    await dialog.getByRole('radio').first().check()
    await dialog.getByRole('button', { name: '提交答案' }).click()
    await dialog.getByText('事实对照').waitFor()
    assert.ok(submitted)
    await dialog.getByRole('button', { name: '关闭', exact: true }).last().click()
    await dialog.waitFor({ state: 'hidden' })
    await review.click()
    await dialog.getByText('限度依据').waitFor()
    await dialog.getByRole('button', { name: '换个情景' }).click()
    await dialog.getByText('果园纠纷的新案情').waitFor()
    assert.equal(nextRequests, 1)
    assert.ok(await dialog.getByRole('button', { name: '提交答案' }).isDisabled())
    console.log('Scenario entry, wrong-only eligibility, delayed feedback, resume and alternate scenario passed.')
  } finally { await browser.close() }
}
main().catch(e=>{console.error(e);process.exitCode=1})
