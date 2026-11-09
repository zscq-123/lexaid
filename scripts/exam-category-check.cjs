const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

const subjects = [
  { discipline: '民商法学', subject: '民法', difficulty: 2, count: 3 },
  { discipline: '民商法学', subject: '民法', difficulty: 4, count: 9 },
  { discipline: '民商法学', subject: '商法', difficulty: 4, count: 5 },
  { discipline: '刑事法学', subject: '刑法', difficulty: 3, count: 6 }
]
const civilCode = { discipline: '民商法学', subject: '民法', codeName: '中华人民共和国民法典' }
const books = [
  { ...civilCode, bookName: null, difficulty: 2, count: 3 },
  { ...civilCode, bookName: '第一编 总则', difficulty: 2, count: 2 },
  { ...civilCode, bookName: '第三编 合同', difficulty: 2, count: 2 },
  { ...civilCode, bookName: null, difficulty: 4, count: 4 },
  { ...civilCode, bookName: '第三编 合同', difficulty: 4, count: 4 },
  { ...civilCode, subject: '商法', bookName: null, difficulty: 4, count: 1 },
  { ...civilCode, subject: '商法', bookName: '第三编 合同', difficulty: 4, count: 1 }
]

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'taxonomy-fixture'))
    const sessions = []
    let categoryRows = subjects
    let subjectFailed = false
    let booksFailed = false
    let progressFailed = false
    let answered = 2
    let delayProgress
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const { pathname: p, searchParams } = new URL(route.request().url())
      let data
      if (p === '/api/auth/me') data = { id: 1, username: 'test' }
      if (p === '/api/exam/subjects') {
        if (subjectFailed) return route.fulfill({ status: 503, json: { message: '测试：加载失败' } })
        data = categoryRows
      }
      if (p === '/api/exam/code-books') {
        if (booksFailed) return route.fulfill({ status: 503, json: { message: '测试：加载失败' } })
        data = books
      }
      if (p === '/api/exam/progress') {
        const query = Object.fromEntries(searchParams)
        if (delayProgress && query.subject === '民法') await delayProgress
        if (progressFailed) return route.fulfill({ status: 503, json: { message: '测试：进度加载失败' } })
        const rows = query.codeName ? books.filter(r => r.bookName === (query.bookName || null)) : categoryRows
        const total = rows.filter(row => Object.entries(query).every(([key,value]) => String(row[key]) === value))
          .reduce((sum,row) => sum + row.count, 0)
        data = { total, answered: Math.min(answered, total) }
      }
      if (p === '/api/exam/sessions') {
        if (route.request().method() === 'POST') {
          sessions.push(route.request().postDataJSON())
          data = { sessionId: sessions.length }
        } else {
          data = { records: [
            { id: 12, total: 3, ...civilCode, bookName: '第三编 合同', category: '民法' },
            { id: 1, total: 5, category: '类案检索' }
          ], total: 2 }
        }
      }
      if (/^\/api\/exam\/sessions\/\d+$/.test(p)) {
        data = [{ id: 1, stem: '测试题', type: 'SHORT', options: [], discipline: '民商法学', subject: '民法',
          codeBooks: ['第一编 总则', '第三编 合同'].map(bookName => ({ codeName: civilCode.codeName, bookName })) }]
      }
      return route.fulfill({ json: { code: 200, data } })
    })
    const setup = (process.env.SMOKE_BASE || 'http://localhost:5173') + '/exam/setup'
    const field = name => page.locator('.el-select').filter({ has: page.getByRole('combobox', { name, exact: true }) })
    const option = name => page.getByRole('option', { name })
    async function pick(label, match) {
      await field(label).click()
      await option(match).click()
    }
    async function start(expected) {
      const startButton = page.getByRole('button', { name: '开始答题' })
      await startButton.evaluate(button => button.scrollIntoView({ block: 'center' }))
      await startButton.click()
      await page.locator('.stem-text').waitFor()
      assert.deepEqual(sessions.at(-1), expected)
      assert.match(await page.locator('.run-header').innerText(), /民商法学 \/ 民法/)
      await page.getByText('关联法典与编（2 项）').click()
      await page.getByText('中华人民共和国民法典 / 第三编 合同', { exact: true }).waitFor()
    }
    const bar = page.getByRole('progressbar', { name: '做题进度' })
    async function count(n) {
      const done = Math.min(answered, n)
      await page.getByText(`已做 ${done} / ${n} 题`, { exact: true }).waitFor()
      assert.equal(await bar.getAttribute('max'), String(n))
      assert.equal(await bar.getAttribute('value'), String(done))
      await page.getByText(`剩余 ${n-done} 题`, { exact: true }).waitFor()
    }

    await page.goto(setup)
    await count(23)
    assert.equal(await page.getByRole('combobox', { name: '具体科目' }).isDisabled(), true)
    await start({ count: 10 })
    answered = 3
    await page.goBack()
    await count(23)
    await page.goto(setup)
    await pick('法典', /民法典/)
    await count(8)
    await pick('编', /第三编 合同/)
    await count(7)
    await start({ codeName: civilCode.codeName, bookName: '第三编 合同', count: 10 })

    await page.goto(setup)
    await field('法学大类').click()
    assert.match(await option(/民商法学/).innerText(), /17\s*题/)
    await option(/民商法学/).click()
    await count(17)
    await start({ discipline: '民商法学', count: 10 })

    await page.goto(setup)
    await pick('法学大类', /民商法学/)
    await pick('具体科目', /民法/)
    await count(12)
    await start({ discipline: '民商法学', subject: '民法', count: 10 })

    await page.goto(setup)
    await pick('法学大类', /民商法学/)
    await pick('具体科目', /民法/)
    await pick('法典', /民法典/)
    await count(7)
    await start({ ...civilCode, count: 10 })

    await page.goto(setup)
    await pick('难度', '2 星')
    await field('法学大类').click()
    assert.equal(await option(/刑事法学/).getAttribute('aria-disabled'), 'true')
    assert.match(await option(/民商法学/).innerText(), /3\s*题/)
    await option(/民商法学/).click()
    await field('具体科目').click()
    assert.equal(await option(/^商法/).getAttribute('aria-disabled'), 'true')
    await option(/民法/).click()
    await pick('法典', /中华人民共和国民法典/)
    await count(3) // Book counts overlap; the code aggregate counts unique questions.
    await pick('编', /第三编 合同/)
    await count(2)
    await page.getByText('按实体组卷', { exact: true }).click()
    await start({ ...civilCode, bookName: '第三编 合同', difficulty: 2, count: 10, sourceType: 'PROVISION', sourceId: 4 })

    await page.goto(setup)
    await pick('法学大类', /民商法学/)
    await pick('具体科目', /民法/)
    await pick('法典', /民法典/)
    await pick('编', /第一编 总则/)
    await field('难度').click()
    assert.equal(await option('4 星').getAttribute('aria-disabled'), 'true')
    await page.keyboard.press('Escape')
    await pick('编', '全部编')
    await count(7)
    await pick('法典', '不限法典')
    await count(12)
    await pick('具体科目', /^商法/)
    await count(5)
    await pick('法学大类', /刑事法学/)
    await count(6)
    await pick('法学大类', '全部大类')
    await count(23)
    await page.locator('summary').filter({ hasText: '答题记录' }).click()
    await page.getByText('民商法学 / 民法 / 中华人民共和国民法典 / 第三编 合同 · 3 题').waitFor()
    await page.getByText('类案检索 · 5 题').waitFor()
    await page.setViewportSize({ width: 320, height: 700 })
    assert.ok(await page.locator('main').first().evaluate(el => el.scrollWidth <= el.clientWidth + 1))

    categoryRows = []
    await page.goto(setup)
    await page.getByText('当前范围暂无题目。').waitFor()
    assert.equal(await bar.count(), 0)
    assert.equal(await page.getByRole('button', { name: '开始答题' }).isDisabled(), true)
    categoryRows = subjects
    subjectFailed = true
    await page.goto(setup)
    await page.getByText('法学科目加载失败。', { exact: false }).waitFor()
    subjectFailed = false
    await page.getByRole('button', { name: '重新加载' }).click()
    await count(23)
    booksFailed = true
    await page.goto(setup)
    await pick('法学大类', /民商法学/)
    await pick('具体科目', /民法/)
    await page.getByText('法典目录加载失败。', { exact: false }).waitFor()
    booksFailed = false
    await page.getByRole('button', { name: '重新加载' }).click()
    await pick('法典', /民法典/)
    await count(7)
    await page.goto(setup)
    await pick('法学大类', /民商法学/)
    let release
    delayProgress = new Promise(resolve => { release = resolve })
    const slowRequest = page.waitForRequest(req => new URL(req.url()).searchParams.get('subject') === '民法')
    await pick('具体科目', /民法/)
    await slowRequest
    await page.getByText('正在读取进度…').waitFor()
    assert.equal(await bar.count(), 0)
    await pick('具体科目', /^商法/)
    await count(5)
    release()
    delayProgress = undefined
    await count(5)
    progressFailed = true
    await pick('法学大类', /刑事法学/)
    await page.getByText('进度加载失败。', { exact: false }).waitFor()
    assert.equal(await bar.count(), 0)
    assert.equal(await page.getByRole('button', { name: '开始答题' }).isEnabled(), true)
    progressFailed = false
    answered = 0
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await count(6)
    assert.ok(await page.locator('main').first().evaluate(el => el.scrollWidth <= el.clientWidth + 1))
    console.log('Subject hierarchy, exam progress, scope/difficulty, loading/retry, route return and narrow layouts passed.')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
