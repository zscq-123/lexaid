// LexAid UI 规范检查：布局尺寸与详情页要素
const { chromium } = require('playwright')

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle' })
  await page.locator('input[placeholder="用户名"]').fill('demo')
  await page.locator('input[placeholder="密码"]').fill('123456')
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL('**/dashboard')
  await page.waitForTimeout(1000)

  const header = await page.locator('.header').first().boundingBox()
  const aside = await page.locator('.aside').first().boundingBox()
  const menuItems = await page.locator('.el-menu-item').count()
  const statCards = await page.locator('.stat-card').count()
  console.log('headerHeight=' + header.height + ' asideWidth=' + aside.width + ' menuItems=' + menuItems + ' statCards=' + statCards)

  await page.goto('http://localhost:5173/provisions/4', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  const title = await page.locator('.title').first().textContent()
  const favBtn = await page.locator('.actions .el-button').first().count()
  const notePanel = await page.locator('.notes').count()
  const related = await page.locator('.related').count()
  const askAi = await page.getByRole('button', { name: '针对本内容提问' }).count()
  console.log('detail: title=' + title + ' favBtn=' + favBtn + ' notePanel=' + notePanel + ' related=' + related + ' askAi=' + askAi)
  await browser.close()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
