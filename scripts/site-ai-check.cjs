const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const { chromium } = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))('playwright')
const base = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'site-search-test'))
    const requests = []
    const lookups = []
    const source = { type: 'POST', id: 32, title: '侵权案例的学习讨论', summary: '站内已核实的讨论记录' }
    const paper = { type: 'VIEWPOINT', id: 24, title: '侵权案例研究', summary: '论文正文节选' }
    const history = []
    const errors = []
    let denied = false
    let failHistory = true
    page.on('pageerror', error => errors.push(error.message))
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const url = new URL(route.request().url())
      const send = (data, status = 200) => route.fulfill({ status, json: { code: status, message: '来源不可访问', data } })
      if (url.pathname === '/api/auth/me') return send({ id: 6, nickname: '读者' })
      if (url.pathname === '/api/ai/history') return send(history, failHistory ? 503 : 200)
      if (url.pathname === '/api/ai/chat') {
        const body = route.request().postDataJSON()
        requests.push(body)
        const refs = [body.contextType === 'PAPER' ? paper : { ...source, summary: '旧摘录' }]
        const answer = '请参阅站内来源【1】。'
        history.push({ id: history.length + 1, role: 'USER', content: body.message })
        history.push({ id: history.length + 1, role: 'ASSISTANT', content: answer, refs })
        return send({ answer, refs })
      }
      if (url.pathname === '/api/ai/sources/VIEWPOINT/24') return send(paper)
      if (url.pathname === '/api/viewpoints/24') return send({ id: 24, title: paper.title, scholarName: '测试作者',
        summaryKind: 'author_abstract', summary: paper.summary, sourceDataset: '测试期刊', tags: [] })
      if (url.pathname === '/api/viewpoints/24/related') return send({})
      if (url.pathname === '/api/notes') return send([])
      if (url.pathname === '/api/ai/sources/POST/32') {
        lookups.push(url.searchParams.get('q'))
        return denied ? send(null, 404) : send(source)
      }
      if (url.pathname === '/api/community/posts/32') {
        if (denied) return send(null, 404)
        return send({ id: 32, authorId: 6, authorName: '读者', createdAt: '2026-10-01', visibility: 'PUBLIC',
          caption: '完整帖子正文', imageIds: [], liked: false, likeCount: 0, commentCount: 0 })
      }
      throw new Error('Unexpected request ' + url.pathname)
    })

    await page.goto(base + '/ai-chat')
    await page.getByRole('alert').filter({ hasText: '问答记录加载失败' }).waitFor()
    failHistory = false
    await page.getByRole('button', { name: '重试', exact: true }).click()
    const ask = async text => {
      await page.getByRole('textbox', { name: '提问' }).fill(text)
      await page.getByRole('button', { name: '发送', exact: true }).click()
      await page.locator('.loading').waitFor({ state: 'hidden' })
      await page.locator('.assistant .refs').last().waitFor()
    }
    await ask('站内有哪些关于侵权的讨论？')
    assert.equal(requests[0].contextType, undefined)
    assert.equal(requests[0].contextId, undefined)
    const citation = page.getByRole('button', { name: /侵权案例的学习讨论/ }).first()
    await citation.click()
    const dialog = page.getByRole('dialog', { name: '站内来源' })
    await dialog.getByText(source.summary).waitFor()
    assert.equal(lookups.at(-1), requests[0].message)
    await dialog.getByRole('button', { name: '针对这条内容提问' }).click()
    await page.waitForURL('**/ai-chat?**contextId=32')
    await ask('这篇帖子是怎样讨论的？')
    assert.equal(requests[1].contextId, 32)
    assert.equal(requests[1].contextType, 'POST')
    assert.equal(requests[1].history, undefined)
    await page.getByRole('button', { name: '解除限定' }).click()
    await page.locator('.scope-picker .el-select__wrapper').click()
    await page.getByRole('option', { name: '题库', exact: true }).click()
    await ask('找一下练习题')
    assert.equal(requests[2].contextType, 'QUESTION')
    assert.equal(requests[2].contextId, undefined)
    assert.ok(await page.locator('.main').evaluate(el => el.scrollWidth <= el.clientWidth))
    denied = true
    await citation.click()
    await dialog.getByRole('alert').getByText(/已删除或当前不可查看/).waitFor()
    assert.equal(await dialog.getByText(source.summary).count(), 0)
    assert.equal(await dialog.getByRole('button', { name: '打开原文' }).count(), 0)
    denied = false
    await dialog.getByRole('button', { name: '重试' }).click()
    await dialog.getByRole('button', { name: '打开原文' }).click()
    await page.waitForURL('**/community/posts/32')
    await page.getByText('完整帖子正文', { exact: true }).waitFor()
    await page.getByRole('button', { name: '针对帖子提问' }).click()
    await page.waitForURL('**/ai-chat?contextType=POST&contextId=32')
    await page.locator('.context-bar').getByText('当前来源：' + source.title).waitFor()
    await page.goBack()
    await page.waitForURL('**/community/posts/32')
    await page.goBack()
    await page.waitForURL('**/ai-chat?contextType=QUESTION')
    await citation.click()
    await dialog.getByText(source.summary).waitFor()
    await page.keyboard.press('Escape')
    await page.locator('.scope-picker .el-select__wrapper').click()
    await page.getByRole('option', { name: '仅论文', exact: true }).click()
    await ask('只查询站内论文')
    assert.equal(requests[3].contextType, 'PAPER')
    const paperCitation = page.getByRole('button', { name: /侵权案例研究/ })
    await paperCitation.click()
    await dialog.getByRole('button', { name: '打开原文' }).click()
    await page.waitForURL('**/viewpoints/24')
    await page.getByRole('heading', { name: paper.title }).waitFor()
    await page.goBack()
    await page.waitForURL('**/ai-chat?contextType=PAPER')
    await paperCitation.click()
    await dialog.getByText(paper.summary).waitFor()
    denied = true
    await page.goto(base + '/community/posts/32')
    await page.getByRole('alert').filter({ hasText: '帖子已删除' }).waitFor()
    assert.equal(await page.getByRole('button', { name: '针对帖子提问' }).count(), 0)
    assert.deepEqual(errors, [])
    console.log('Site-wide and paper-only chat, focused follow-up, authorized previews, original post/paper, return navigation and mobile layout passed.')
  } finally {
    await browser.close()
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
