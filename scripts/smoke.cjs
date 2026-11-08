// LexAid 前端冒烟测试：登录后遍历核心页面，收集控制台错误并截图
// 运行：NODE_PATH=<bundled node_modules> node scripts/smoke.cjs
const { createRequire } = require('module')
const frontendRequire = createRequire(require('path').join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')
const fs = require('fs')
const path = require('path')

const BASE = process.env.SMOKE_BASE || 'http://localhost:5173'
const SHOT_DIR = path.join(__dirname, 'shots')

async function main() {
  fs.mkdirSync(SHOT_DIR, { recursive: true })
  const customExecutable = process.env.SMOKE_EXECUTABLE
  const browser = await chromium.launch(customExecutable ? { executablePath: customExecutable } : {})
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push('[console.error] ' + msg.text())
  })
  page.on('pageerror', (err) => errors.push('[pageerror] ' + err.message))
  page.on('response', (r) => {
    if (r.status() >= 400) errors.push('[http ' + r.status() + '] ' + r.url())
  })

  // 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.screenshot({ path: path.join(SHOT_DIR, '01-login.png') })
  await page.locator('input[placeholder="用户名"]').fill('demo')
  await page.locator('input[placeholder="密码"]').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL('**/dashboard', { timeout: 15000 })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: path.join(SHOT_DIR, '02-dashboard.png') })

  // 法条列表与详情
  await page.goto(BASE + '/provisions', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.screenshot({ path: path.join(SHOT_DIR, '03-provisions.png') })
  await page.goto(BASE + '/provisions/4', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.screenshot({ path: path.join(SHOT_DIR, '04-provision-detail.png') })

  // 案例 / 观点
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.goto(BASE + '/cases/1', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.goto(BASE + '/viewpoints', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.goto(BASE + '/viewpoints/1', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.join(SHOT_DIR, '05-viewpoint-detail.png') })

  // 组卷 → 答题 → 交卷
  await page.goto(BASE + '/exam/setup', { waitUntil: 'networkidle' })
  await page.screenshot({ path: path.join(SHOT_DIR, '06-exam-setup.png') })
  await page.getByRole('button', { name: '开始答题' }).click()
  await page.waitForURL('**/exam/run/**', { timeout: 15000 })
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(SHOT_DIR, '07-exam-run.png') })
  const questionCount = await page.locator('.cards .el-button').count()
  if (questionCount === 0) throw new Error('组卷成功但没有返回题目')
  // 逐题作答（选择题选第一项，判断选正确，简答填字）
  for (let i = 0; i < questionCount; i++) {
    const typeTag = await page.locator('.stem .el-tag').first().textContent().catch(() => '')
    const btn = page.getByRole('button', { name: '下一题' })
    if (typeTag === '单选题' || typeTag === '判断题') {
      await page.locator('.options .el-radio').first().click()
    } else if (typeTag === '多选题') {
      await page.locator('.options .el-checkbox').first().click()
    } else {
      await page.locator('textarea').fill('测试作答内容')
    }
    if (i < questionCount - 1) {
      if (await btn.isVisible().catch(() => false)) await btn.click()
      await page.waitForTimeout(200)
    }
  }
  await page.getByRole('button', { name: '交 卷' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await page.waitForURL('**/exam/result/**', { timeout: 15000 })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(SHOT_DIR, '08-exam-result.png') })

  // 错题本 / AI / 笔记 / 统计
  await page.goto(BASE + '/wrong-book', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.goto(BASE + '/ai-chat', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.locator('textarea').first().fill('正当防卫的成立条件是什么？')
  await page.getByRole('button', { name: '发送' }).click()
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(SHOT_DIR, '09-ai-chat.png') })
  await page.goto(BASE + '/notes', { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await page.goto(BASE + '/stats', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(SHOT_DIR, '10-stats.png') })

  console.log('PAGES_OK')
  if (errors.length) {
    console.log('ERRORS:')
    errors.forEach((e) => console.log(e))
    throw new Error(`检测到 ${errors.length} 个浏览器或 HTTP 错误`)
  } else {
    console.log('NO_CONSOLE_ERRORS')
  }
  await browser.close()
}

main().catch((e) => {
  console.error('SMOKE_FAILED:', e.message)
  process.exit(1)
})
