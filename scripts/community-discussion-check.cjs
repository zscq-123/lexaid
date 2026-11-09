// Browser regression for cited discussions. All API calls use isolated fixtures.
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

async function main() {
  const base = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'discussion-fixture'))
    await page.clock.install()
    const post = { id: 21, authorId: 1, authorName: '甲', caption: '如何评价本案中的权利行使？',
      visibility: 'PUBLIC', commentCount: 33, likeCount: 1 }
    const privatePost = { ...post, id: 22, visibility: 'FRIENDS' }
    const lowPost = { ...post, id: 23, commentCount: 0 }
    let statusReads = 0
    let privateReads = 0
    let retryReads = 0
    const commentPages = []
    const keywords = []
    await page.route(base + '/api/**', async route => {
      const url = new URL(route.request().url())
      let data = []
      if (url.pathname === '/api/auth/me') data = { id: 3, username: 'test-user' }
      if (url.pathname === '/api/community/posts') data = { total: 2, records: [post, lowPost] }
      if (url.pathname === '/api/community/posts/21') data = post
      if (url.pathname === '/api/community/posts/22') data = privatePost
      if (url.pathname === '/api/community/posts/23') data = lowPost
      if (/\/api\/community\/posts\/(24|25|26)$/.test(url.pathname)) {
        data = { ...post, id: Number(url.pathname.split('/').at(-1)) }
      }
      if (url.pathname.endsWith('/discussion-summaries')) {
        const keyword = url.searchParams.get('keyword')
        keywords.push(keyword)
        data = keyword === '无匹配' ? { total: 0, records: [] } : {
          total: 1, records: [{ postId: 21, topic: '权利行使的边界', commentCount: 33, sampledComments: 24 }]
        }
      }
      if (url.pathname === '/api/community/posts/21/discussion-summary') {
        statusReads++
        data = { postId: 21, status: statusReads === 1 ? 'PENDING' : 'READY', generatedAt: '2026-11-09T15:00:00',
          topic: '权利行使的边界', sampledComments: 24, points: [{ kind: 'MIXED', text: '需要区分不同情形。', sourceIds: [13, 0] }],
          sources: [{ id: 13, authorName: '乙', text: '来源评论13', page: 1 }, { id: 0, authorName: '甲', text: post.caption, page: 0 }] }
      }
      if (url.pathname === '/api/community/posts/22/discussion-summary') privateReads++
      if (url.pathname === '/api/community/posts/23/discussion-summary') data = { status: 'INELIGIBLE' }
      if (url.pathname === '/api/community/posts/24/discussion-summary') data = { status: 'READY', points: [], sources: [] }
      if (url.pathname === '/api/community/posts/25/discussion-summary') data = { status: 'UNAVAILABLE' }
      if (url.pathname === '/api/community/posts/26/discussion-summary') {
        retryReads++
        if (retryReads === 1) return route.fulfill({ status: 503, json: { message: '测试暂时不可用' } })
        data = { status: 'READY', points: [], sources: [] }
      }
      if (url.pathname === '/api/community/posts/21/comments') {
        const current = Number(url.searchParams.get('page'))
        commentPages.push(current)
        const records = Array.from({length: 33}, (_, i) => ({
          id: 33 - i, authorId: 2, authorName: '乙', content: '来源评论' + (33 - i)
        })).slice((current - 1) * 10, current * 10)
        data = { records, total: 33 }
      }
      await route.fulfill({ json: { code: 200, data } })
    })

    await page.goto(base + '/community')
    await page.getByRole('link', { name: 'AI 观点整理' }).waitFor()
    assert.equal(statusReads, 0, 'feed must not request summaries for every post')
    await page.getByRole('button', { name: '发布帖子' }).click()
    await page.getByText(/公开帖收到至少 2 位用户/).waitFor()
    await page.getByRole('dialog').locator('.visibility .el-select').click()
    await page.getByRole('option', { name: '仅好友' }).click()
    await page.getByText(/仅好友可见的帖子不参与 AI 观点整理/).waitFor()
    await page.getByRole('button', { name: '取消' }).click()
    await page.getByRole('button', { name: '讨论知识库' }).click()
    await page.getByRole('link', { name: /权利行使的边界/ }).waitFor()
    const filter = page.getByRole('search', { name: '搜索讨论知识库' })
    await filter.getByRole('textbox').fill('无匹配')
    await filter.getByRole('button', { name: '搜索' }).click()
    await page.getByText('没有找到匹配的讨论').waitFor()
    await filter.getByRole('textbox').fill('权利')
    await filter.getByRole('textbox').press('Enter')
    await page.getByRole('link', { name: /权利行使的边界/ }).click()
    await page.getByText('正在整理最新讨论，完成后这里会自动更新。').waitFor()
    await page.clock.fastForward(16000)
    await page.getByRole('heading', { name: '折中观点' }).waitFor()
    assert.equal(await page.getByRole('heading', { name: '反对观点' }).count(), 0)
    await page.getByRole('button', { name: '乙的评论 #13' }).click()
    await page.waitForFunction(() => document.activeElement.dataset.commentId === '13')
    assert.deepEqual(commentPages, [1, 3])
    assert.equal(await page.locator('[data-comment-id="13"] p').innerText(), '来源评论13')
    await page.getByRole('button', { name: '查看原帖' }).click()
    await page.waitForFunction(() => document.activeElement.matches('article.post'))
    await page.goBack()
    await page.getByRole('link', { name: /权利行使的边界/ }).waitFor()
    assert.ok(keywords.includes('无匹配') && keywords.includes('权利'))
    await page.goto(base + '/community/posts/22')
    await page.getByText('仅公开讨论会进入观点整理。').waitFor()
    assert.equal(privateReads, 0)
    await page.goto(base + '/community/posts/23')
    await page.getByText(/公开帖有至少 3 条评论/).waitFor()
    await page.goto(base + '/community/posts/24')
    await page.getByText('近期讨论尚无可归纳的法律观点。').waitFor()
    await page.goto(base + '/community/posts/25')
    await page.getByText(/AI 整理暂时不可用/).waitFor()
    await page.goto(base + '/community/posts/26')
    await page.getByRole('alert').getByRole('button', { name: '重试' }).click()
    await page.getByText('近期讨论尚无可归纳的法律观点。').waitFor()
    await page.setViewportSize({ width: 320, height: 680 })
    assert.ok(await page.locator('.post-detail').evaluate(e => e.scrollWidth <= e.clientWidth + 1))
    console.log('讨论知识库检索、自动刷新、引文跨页定位、原帖定位、无结论、错误重试、低热度与好友范围均通过')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
