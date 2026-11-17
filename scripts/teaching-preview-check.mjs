import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = require('playwright')
const directory = path.join(root, 'docs/teaching/2026-11-12/batch-001')
const browser = await chromium.launch({
  executablePath: process.env.CHROME_BINARY || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
})
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(pathToFileURL(path.join(directory, 'preview.html')).href)
  if (await page.locator('article').count() !== 216) throw Error('Missing question cards')
  if ((await page.locator('#count').textContent()) !== '显示 216 / 216 道题') throw Error('Wrong initial count')
  await page.locator('#search').fill('10024085')
  if (await page.locator('article:visible').count() !== 1) throw Error('ID search failed')
  if (!(await page.locator('article:visible').textContent()).includes('【推理过程】')) throw Error('Missing reasoning')
  await page.locator('article:visible summary').click()
  if (await page.locator('article:visible details').getAttribute('open') === null) throw Error('Original content not expandable')
  await page.locator('#search').fill('不可能命中这个搜索字符串XYZ')
  if (await page.locator('article:visible').count() !== 0) throw Error('Empty result failed')
  await page.locator('#search').fill('')
  const links = await page.locator('article a').evaluateAll(nodes => nodes.map(node => ({ href: node.href, rel: node.rel })))
  if (!links.length || links.some(link => !link.href.startsWith('https://') || !link.rel.includes('noopener'))) throw Error('Unsafe evidence link')
  await page.setViewportSize({ width: 390, height: 844 })
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw Error('Mobile horizontal overflow')
  await page.locator('#search').fill('10025079')
  if (await page.locator('article:visible').count() !== 1) throw Error('Mobile search failed')
  await page.screenshot({ path: path.join(directory, 'evidence/preview-mobile.png'), fullPage: true })
  if (errors.length) throw Error(errors.join('; '))
  const result = { cards: 216, search: true, emptyResults: true, originalsExpandable: true, officialLinks: links.length, mobile390: true, pageErrors: errors }
  fs.writeFileSync(path.join(directory, 'evidence/preview-check.json'), JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
} finally {
  await browser.close()
}
