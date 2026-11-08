// LexAid UI 规范检查：布局尺寸与详情页要素
const { createRequire } = require('module')
const path = require('path')
const frontendRequire = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')
const assert = require('node:assert/strict')

const BASE = process.env.SMOKE_BASE || 'http://localhost:5173'

async function main() {
  const customExecutable = process.env.SMOKE_EXECUTABLE
  const browser = await chromium.launch(customExecutable ? { executablePath: customExecutable } : {})
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.locator('input[placeholder="用户名"]').fill('demo')
  await page.locator('input[placeholder="密码"]').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL('**/dashboard')
  await page.waitForTimeout(1000)

  const header = await page.locator('.header').first().boundingBox()
  const aside = await page.locator('.aside').first().boundingBox()
  const menuItems = await page.locator('.el-menu-item').count()
  const statCards = await page.locator('.stat-card').count()
  console.log('headerHeight=' + header.height + ' asideWidth=' + aside.width + ' menuItems=' + menuItems + ' statCards=' + statCards)
  assert.equal(header.height, 56, '顶部导航高度应为 56px')
  assert.ok(aside.width >= 180, '侧边栏宽度异常')
  assert.ok(menuItems >= 9, '侧边栏菜单项缺失')
  assert.equal(statCards, 4, '工作台统计卡片应为 4 个')

  await page.goto(BASE + '/provisions/4', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  const title = await page.locator('.title').first().textContent()
  const favBtn = await page.locator('.actions .el-button').first().count()
  const notePanel = await page.locator('.notes').count()
  const related = await page.locator('.related').count()
  const askAi = await page.getByRole('button', { name: '针对本内容提问' }).count()
  console.log('detail: title=' + title + ' favBtn=' + favBtn + ' notePanel=' + notePanel + ' related=' + related + ' askAi=' + askAi)
  assert.ok(title.trim().length > 0, '详情标题为空')
  assert.equal(favBtn, 1, '收藏按钮缺失')
  assert.equal(notePanel, 1, '笔记面板缺失')
  assert.equal(related, 1, '关联内容区域缺失')
  assert.equal(askAi, 1, 'AI 提问入口缺失')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(BASE + '/ai-chat')
  const editor = page.getByRole('textbox', { name: '提问' })
  await editor.focus()
  await page.setViewportSize({ width: 390, height: 495 })
  const keyboardLayout = await editor.evaluate(async (textarea) => {
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    return {
      visible: textarea.getBoundingClientRect().bottom <= window.innerHeight,
      resizing: textarea.closest('.chat-card').getAnimations().some(animation => animation.transitionProperty === 'height')
    }
  })
  assert.ok(keyboardLayout.visible && !keyboardLayout.resizing, '键盘缩短视口后，输入框应立即可见')
  await browser.close()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
