const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontendRequire = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontendRequire('playwright')

const base = process.env.SMOKE_BASE || 'http://localhost:5173'
const original = {
  id: 1, title: '分页测试', scholarName: '测试作者', languageCode: 'zh', summaryKind: 'metadata_only',
  sourceDataset: 'China-Law-Journal-Archive', source: '测试期刊（第14–16页）',
  content: '原文首页。\n\n原文续页。\n\n原文末页。'
}
const reflow = {
  ...original, id: 2, title: 'A Study of Legal Interpretation', languageCode: 'en',
  sourceDataset: 'journal', source: 'Test Journal',
  content: Array.from({ length: 40 }, (_, i) => 'Paragraph ' + (i + 1) + '. ' +
    'This paragraph discusses the interpretation of judicial decisions and their consequences for the application of the law. '.repeat(5)
  ).join('\n\n')
}

function firstVisibleCharacter() {
  const viewport = document.querySelector('.paper-viewport').getBoundingClientRect()
  const walker = document.createTreeWalker(document.querySelector('.paper-flow'), NodeFilter.SHOW_TEXT)
  const range = document.createRange()
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    range.selectNodeContents(node)
    if (range.getBoundingClientRect().right <= viewport.left + 1) continue
    for (let i = 0; i < node.length; i++) {
      if (!node.textContent[i].trim()) continue
      range.setStart(node, i)
      range.setEnd(node, i + 1)
      const rect = range.getBoundingClientRect()
      if (rect.right > viewport.left + 1 && rect.left < viewport.right) return range
    }
  }
  throw new Error('当前页缺少可见文字')
}

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'pagination-fixture'))
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const pathname = new URL(route.request().url()).pathname
      let data = []
      if (pathname.endsWith('/auth/me')) data = { id: 1, username: 'reader' }
      if (pathname.endsWith('/related')) data = {}
      if (pathname.endsWith('/viewpoints/1')) data = original
      if (pathname.endsWith('/viewpoints/2')) data = reflow
      return route.fulfill({ json: { code: 200, data } })
    })
    await page.goto(base + '/viewpoints/1')
    const prev = page.getByRole('button', { name: '上一页' })
    const next = page.getByRole('button', { name: '下一页' })
    await next.waitFor()
    assert.ok(await prev.isDisabled())
    for (const [i, paragraph] of original.content.split('\n\n').entries()) {
      await page.getByText(paragraph, { exact: true }).waitFor()
      assert.equal(await page.locator('.folio').innerText(), '原文第 ' + (14 + i) + ' 页')
      if (i < 2) await next.click()
    }
    assert.ok(await next.isDisabled())
    await prev.click()
    await page.getByText('原文续页。', { exact: true }).waitFor()

    await page.goto(base + '/viewpoints/2')
    const picker = page.getByRole('combobox', { name: '跳转到阅读页' })
    await picker.waitFor()
    await page.evaluate(() => document.fonts.ready)
    const desktopCount = await picker.locator('option').count()
    assert.ok(desktopCount > 4)
    await picker.selectOption(String(desktopCount))
    assert.ok(await next.isDisabled())
    await picker.selectOption('4')
    const anchor = await page.evaluateHandle(firstVisibleCharacter)
    // Deliver the scroll from the preceding jump while the reader still has its old column measurements.
    await page.evaluate(() => window.addEventListener('resize', () => {
      document.querySelector('.paper-viewport').dispatchEvent(new Event('scroll'))
    }, { once: true }))
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForFunction(count => document.querySelector('.page-picker select').length > count, desktopCount)
    assert.ok(await anchor.evaluate(range => {
      const viewport = document.querySelector('.paper-viewport').getBoundingClientRect()
      const rect = range.getBoundingClientRect()
      return rect.right > viewport.left && rect.left < viewport.right
    }), '重新分页后，之前的首字符应仍在当前页内')

    // Simulate leaving before a long paper and its related records have downloaded.
    const slowPaths = ['/api/viewpoints/3', '/api/viewpoints/3/related']
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.route(url => slowPaths.includes(url.pathname), () => {})
    const requests = slowPaths.map(path => page.waitForRequest(req => new URL(req.url()).pathname === path))
    await page.goto(base + '/viewpoints/3', { waitUntil: 'domcontentloaded' })
    await Promise.all(requests)
    const cancellations = slowPaths.map(path =>
      page.waitForEvent('requestfailed', req => new URL(req.url()).pathname === path)
    )
    await page.getByRole('menuitem', { name: '工作台' }).click()
    await Promise.all(cancellations)
    await page.waitForURL('**/dashboard')
    assert.equal(await page.getByText('网络异常，请稍后重试', { exact: true }).count(), 0)
    console.log('Original pages, last-page controls, resize position and cancelled requests passed.')

  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
