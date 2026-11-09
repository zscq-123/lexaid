const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    const folders = []
    const favorites = [
      { id: 1, targetType: 'PROVISION', targetId: 4, title: '民法条文', folderId: null },
      { id: 2, targetType: 'VIEWPOINT', targetId: 24, title: '法律研究论文', folderId: null }
    ]
    await page.addInitScript(() => localStorage.setItem('lexaid_token', 'favorites-fixture'))
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const api = new URL(route.request().url()).pathname
      const method = route.request().method()
      const body = route.request().postDataJSON()
      let data
      if (api === '/api/mock-exams/active') return route.fulfill({ json: { code: 200, data: null } })
      if (api === '/api/auth/me') data = { id: 7, username: 'reader' }
      else if (api === '/api/favorites' && method === 'GET') data = favorites
      else if (api === '/api/favorites/folders' && method === 'GET') data = folders
      else if (api === '/api/favorites/folders' && method === 'POST') {
        data = { id: 11, name: body.name }
        folders.push(data)
      } else if (api === '/api/favorites/2/folder' && method === 'PUT') {
        favorites[1].folderId = body.folderId
      } else if (api === '/api/favorites/folders/11' && method === 'PUT') {
        folders[0].name = body.name
        data = folders[0]
      } else if (api === '/api/favorites/folders/11' && method === 'DELETE') {
        folders.pop()
        favorites.forEach(item => { item.folderId = null })
      } else if (api === '/api/favorites/2' && method === 'DELETE') {
        favorites.pop()
      } else throw new Error('Unexpected request: ' + method + ' ' + api)
      await route.fulfill({ json: { code: 200, data: data ?? null } })
    })

    async function choose(name, option) {
      const box = page.getByRole('combobox', { name })
      await page.locator('.el-select').filter({ has: box }).click()
      await page.getByRole('option', { name: option, exact: true }).click()
    }
    const profile = (process.env.SMOKE_BASE || 'http://127.0.0.1:5173') + '/profile'
    await page.goto(profile)
    await page.getByRole('tab', { name: '我的收藏' }).click()
    await page.waitForURL('**/profile?tab=favorites')
    await page.getByText('共 2 条收藏').waitFor()
    const paper = page.locator('.favorite-row').filter({ hasText: '法律研究论文' })
    assert.equal(await paper.getByRole('link').getAttribute('href'), '/viewpoints/24')
    await choose('收藏类型', '学者观点 / 论文')
    await page.getByText('共 1 条收藏').waitFor()
    await paper.getByRole('button', { name: '移动' }).click()
    await page.getByRole('menuitem', { name: '新建分区' }).click()
    await page.getByRole('textbox', { name: '分区名称' }).fill('复习')
    await page.getByRole('button', { name: '保存' }).click()
    await paper.getByText('复习', { exact: true }).waitFor()
    assert.equal(favorites[1].folderId, 11)
    await page.reload()
    await choose('收藏分区', '复习')
    await page.getByText('共 1 条收藏').waitFor()
    await page.getByRole('button', { name: '重命名' }).click()
    await page.getByRole('textbox', { name: '分区名称' }).fill('考试')
    await page.getByRole('button', { name: '保存' }).click()
    await paper.getByText('考试', { exact: true }).waitFor()
    await page.getByRole('button', { name: '删除分区' }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '删除分区' }).click()
    await paper.getByText('未分区', { exact: true }).waitFor()
    await page.getByText('共 2 条收藏').waitFor()
    assert.equal(await page.locator('main').evaluate(el => el.scrollWidth <= el.clientWidth), true)
    await paper.getByRole('button', { name: '取消收藏' }).click()
    await page.getByText('共 1 条收藏').waitFor()
    assert.equal(favorites.length, 1)
    console.log('Profile favorites, type filtering, persistent folder changes and removal passed.')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
