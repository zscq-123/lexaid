const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontendRequire = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontendRequire('playwright')

const tree = [
  { value: 'CN', label: '中国', children: [{
    value: 'zj', label: '浙江省', children: [{
      value: 'hz', label: '杭州市', children: [{
        value: 'xh', label: '西湖区', children: [{ value: 'court-xh', label: '杭州市西湖区人民法院' }]
      }]
    }]
  }] },
  { value: 'US', label: '美国', children: [{ value: 'court-us', label: 'Supreme Court of the United States' }] }
]

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'court-fixture'))
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const { pathname } = new URL(route.request().url())
      let data
      if (pathname.endsWith('/auth/me')) data = { id: 1, username: 'reader' }
      if (pathname.endsWith('/cases/courts')) data = tree
      if (pathname.endsWith('/cases')) data = { records: [], total: 0 }
      return route.fulfill({ json: { code: 200, data } })
    })
    await page.goto((process.env.SMOKE_BASE || 'http://localhost:5173') + '/cases')
    const court = page.locator('.el-cascader input').first()
    const scope = page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '法域' }) })

    async function query(button = '查询') {
      const [res] = await Promise.all([
        page.waitForResponse(response => new URL(response.url()).pathname === '/api/cases'),
        page.getByRole('button', { name: button, exact: true }).click()
      ])
      return new URL(res.url()).searchParams
    }
    async function choose(labels) {
      await court.click()
      for (const [index, label] of labels.entries()) {
        const node = page.locator('.el-cascader-node:visible').filter({ has: page.getByText(label, { exact: true }) })
        if (index === labels.length - 1) await node.locator('.el-radio').click()
        else await node.locator('.el-cascader-node__label').click()
      }
    }

    await scope.click()
    await page.getByRole('option', { name: '中国', exact: true }).click()
    await choose(['浙江省'])
    assert.equal((await query()).get('courtNode'), 'zj')
    await choose(['浙江省', '杭州市'])
    assert.equal((await query()).get('courtNode'), 'hz')
    await choose(['浙江省', '杭州市', '西湖区'])
    assert.equal((await query()).get('courtNode'), 'xh')
    await choose(['浙江省', '杭州市', '西湖区', '杭州市西湖区人民法院'])
    await page.getByPlaceholder('如：故意伤害').fill('行政处罚')
    const selected = await query()
    assert.equal(selected.get('courtNode'), 'court-xh')
    assert.equal(selected.get('jurisdiction'), 'CN')
    assert.equal(selected.get('caseType'), '行政处罚')

    await scope.click()
    await page.getByRole('option', { name: '美国', exact: true }).click()
    assert.equal((await query()).get('courtNode'), null)
    const reset = await query('重置')
    assert.ok(!reset.get('courtNode') && !reset.get('jurisdiction') && !reset.get('caseType'))
    await court.fill('supreme united')
    await page.locator('.el-cascader__suggestion-item:visible').filter({ hasText: 'Supreme Court' }).click()
    const foreign = await query()
    assert.equal(foreign.get('courtNode'), 'court-us')
    assert.ok(!foreign.get('jurisdiction'))
    await scope.click()
    await page.getByRole('option', { name: '美国', exact: true }).click()
    const compatible = await query()
    assert.equal(compatible.get('courtNode'), 'court-us')
    assert.equal(compatible.get('jurisdiction'), 'US')
    console.log('Court hierarchy selection, combined filters, jurisdiction change, reset and court search passed.')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
