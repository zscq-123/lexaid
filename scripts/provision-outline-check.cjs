const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

const civil = '中华人民共和国民法典'
const foreign = 'Civil Code'
const labels = ['第二编 物权', '第四分编 担保物权', '第十七章 抵押权', '第一节 一般抵押权']
const paths = labels.map((_, i) => labels.slice(0, i + 1).join(' · '))
const tree = labels.reduceRight((child, label, i) => [{ label, path: paths[i], children: child }], [])
const usTree = [{ label: 'Title I', path: 'Title I', children: [] }]

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true })
    page.setDefaultTimeout(9000)
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'outline-fixture'))
    let failOutline = false
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const url = new URL(route.request().url())
      const p = url.pathname
      const get = name => url.searchParams.get(name)
      let data
      if (p === '/api/mock-exams/active') return route.fulfill({ json: { code: 200, data: null } })
      if (p.endsWith('/auth/me')) data = { id: 1, username: 'tester' }
      else if (p.endsWith('/provisions/outline/codes')) {
        data = [{ jurisdiction: 'CN', lawName: civil }, { jurisdiction: 'US', lawName: foreign }]
          .filter(row => !get('jurisdiction') || row.jurisdiction === get('jurisdiction'))
      } else if (p.endsWith('/provisions/outline')) {
        if (failOutline) return route.fulfill({ status: 503, json: { message: '试用：目录读取失败' } })
        assert.ok(get('code') === civil && get('jurisdiction') === 'CN' || get('code') === foreign && get('jurisdiction') === 'US')
        data = get('code') === civil ? tree : usTree
      } else if (p.endsWith('/provisions/categories')) {
        data = [{ jurisdiction: 'CN', category: '民法' }, { jurisdiction: 'US', category: '私法' }]
      } else if (p.endsWith('/provisions')) {
        data = { total: 1, records: [{
          id: 1, jurisdiction: get('jurisdiction') || 'CN', category: '民法', lawName: get('code') || '法律',
          chapter: get('code') === civil ? paths.at(-1) : '', articleNo: '测试条文', content: '测试条文内容', tags: []
        }] }
      } else return route.continue()
      return route.fulfill({ json: { code: 200, data } })
    })
    const base = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'
    await page.goto(base + '/provisions')
    const code = page.getByRole('combobox', { name: '法典目录', exact: true })
    const outline = page.getByRole('textbox', { name: '编章范围', exact: true })
    const menu = page.locator('.provision-outline-menu:visible')
    const listChange = async (action, expected) => {
      const req = page.waitForRequest(r => new URL(r.url()).pathname === '/api/provisions')
      await action()
      const q = new URL((await req).url()).searchParams
      for (const [key,value] of Object.entries(expected)) assert.equal(q.get(key) || '', value, key)
    }
    const expand = label => menu.getByText(label, { exact: true }).click()
    const choose = label => menu.locator('.el-cascader-node:visible').filter({ hasText: label }).first().locator('.el-radio').click()

    await code.click()
    await listChange(() => page.getByRole('option', { name: civil }).click(), { code: civil, path: '', jurisdiction: 'CN' })
    await page.getByText(paths.at(-1), { exact: true }).waitFor()
    await outline.click()
    await expand(labels[0])
    await listChange(() => choose(labels[1]), { code: civil, path: paths[1], jurisdiction: 'CN' })

    await outline.click()
    if (!await menu.getByText(labels[2], { exact: true }).isVisible()) await expand(labels[1])
    await expand(labels[2])
    await menu.getByText(labels[3], { exact: true }).waitFor()
    const panel = menu.locator('.el-cascader-panel')
    await page.waitForFunction(() => document.querySelector('.provision-outline-menu .el-cascader-panel')?.scrollLeft > 0)
    assert.ok((await panel.boundingBox()).width <= 390)
    await listChange(() => choose(labels[3]), { code: civil, path: paths[3], jurisdiction: 'CN' })

    await outline.click()
    await listChange(() => menu.getByRole('button', { name: '全部编章' }).click(), { code: civil, path: '', jurisdiction: 'CN' })
    await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '法域', exact: true }) }).click()
    await listChange(() => page.getByRole('option', { name: '美国', exact: true }).click(), { code: '', path: '', jurisdiction: 'US' })
    assert.equal(await outline.count(), 0)
    await code.click()
    assert.equal(await page.getByRole('option', { name: civil }).count(), 0)
    failOutline = true
    const failed = page.waitForResponse(r => new URL(r.url()).pathname === '/api/provisions/outline' && r.status() === 503)
    await listChange(() => page.getByRole('option', { name: foreign }).click(), { code: foreign, path: '', jurisdiction: 'US' })
    await failed
    failOutline = false
    await outline.click()
    await listChange(() => choose('Title I'), { code: foreign, path: 'Title I', jurisdiction: 'US' })
    await page.setViewportSize({ width: 320, height: 700 })
    assert.ok(await page.locator('main').first().evaluate(el => el.scrollWidth <= el.clientWidth + 1))
    const codePicker = page.locator('.el-select').filter({ has: code })
    await codePicker.hover()
    await listChange(() => codePicker.locator('.el-select__clear').click(), { jurisdiction: 'US', path: '', code: '' })
    assert.equal(await outline.count(), 0)
    await listChange(() => page.getByRole('button', { name: '重置', exact: true }).click(), { jurisdiction: '', path: '', code: '' })
    assert.equal(await outline.count(), 0)
    console.log('Law outline: code, parent, four-level branch, clear, jurisdiction, retry and phone width passed.')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
