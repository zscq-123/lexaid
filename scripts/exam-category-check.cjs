const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

// Ensure a category added to the question bank is selectable without changing the UI.
const rows = [
  { category: '民法', difficulty: 2, count: 2 },
  { category: '类案检索', difficulty: 3, count: 3 },
  { category: '类案检索', difficulty: 4, count: 4 },
  { category: '文书审查', difficulty: 4, count: 11 }
]

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'category-fixture'))
    const sessions = []
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const pathname = new URL(route.request().url()).pathname
      let data
      if (pathname === '/api/auth/me') data = { id: 1, username: 'test' }
      if (pathname === '/api/exam/categories') data = rows
      if (pathname === '/api/exam/sessions') {
        sessions.push(route.request().postDataJSON())
        data = { sessionId: sessions.length }
      }
      if (/^\/api\/exam\/sessions\/\d+$/.test(pathname)) {
        data = [{ id: 1, stem: '测试题', type: 'SHORT', options: [], category: sessions.at(-1).category }]
      }
      return route.fulfill({ json: { code: 200, data } })
    })

    const setup = (process.env.SMOKE_BASE || 'http://localhost:5173') + '/exam/setup'
    const category = page.getByRole('combobox', { name: '题目类别' })
    const level = page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '难度' }) })
    const categoryField = page.locator('.el-select').filter({ has: category })
    async function start(expected) {
      await page.getByRole('button', { name: '开始答题' }).click()
      await page.locator('.stem-text').waitFor()
      assert.deepEqual(sessions.at(-1), expected)
    }

    await page.goto(setup)
    await category.fill('类案')
    const group = page.getByRole('option', { name: /类案检索/ })
    await group.waitFor()
    assert.match(await group.innerText(), /7\s*题/)
    assert.equal(await page.getByRole('option').count(), 1)
    await group.click()
    await level.click()
    assert.equal(await page.getByRole('option', { name: '1 星' }).getAttribute('aria-disabled'), 'true')
    await page.getByRole('option', { name: '3 星' }).click()
    await page.getByText('当前条件共有 3 道题', { exact: false }).waitFor()
    await start({ category: '类案检索', difficulty: 3, count: 10 })

    await page.goto(setup)
    await level.click()
    await page.getByRole('option', { name: '4 星' }).click()
    await categoryField.click()
    const filtered = page.getByRole('option', { name: /类案检索/ })
    await filtered.waitFor()
    assert.match(await filtered.innerText(), /4\s*题/)
    assert.equal(await page.getByRole('option', { name: /民法/ }).count(), 0)
    await page.getByRole('option', { name: /文书审查/ }).click()
    await page.getByText('按实体组卷', { exact: true }).click()
    await start({ category: '文书审查', difficulty: 4, count: 10, sourceType: 'PROVISION', sourceId: 4 })

    await page.goto(setup)
    await categoryField.click()
    await page.getByRole('option', { name: /文书审查/ }).click()
    await categoryField.hover()
    await categoryField.locator('.el-select__clear').click()
    await start({ count: 10 })
    console.log('Category search, counts, difficulty, random/entity sessions and clearing passed.')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
