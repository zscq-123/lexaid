const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')
const base = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'
const photo = fs.readFileSync(path.join(__dirname, '../frontend/public/mobile-icon-512.png'))
const avatar = fs.readFileSync(path.join(__dirname, '../frontend/public/mobile-icon-180.png'))

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => {
      localStorage.setItem('lexaid_token', 'media-test-one')
      const create = URL.createObjectURL.bind(URL)
      const revoke = URL.revokeObjectURL.bind(URL)
      window.activeBlobs = new Map()
      URL.createObjectURL = blob => {
        const url = create(blob)
        window.activeBlobs.set(url, blob.size)
        return url
      }
      URL.revokeObjectURL = url => { window.activeBlobs.delete(url); revoke(url) }
    })
    const pictures = []
    const avatarSessions = []
    let releaseSecondImage
    const slowImage = new Promise(resolve => { releaseSecondImage = resolve })
    const user = { id: 7, nickname: '测试' }
    const posts = Array.from({ length: 10 }, (_, i) => ({
      id: i + 1, authorId: 99, authorName: '作者', authorAvatarVersion: 'v1',
      imageIds: [i * 2 + 1, i * 2 + 2], caption: '图片帖', visibility: 'PUBLIC'
    }))
    await page.route(base + '/api/**', async route => {
      const pathname = new URL(route.request().url()).pathname
      if (pathname.endsWith('/avatar')) {
        avatarSessions.push(route.request().headers().authorization)
        return route.fulfill({ contentType: 'image/png', body: avatar })
      }
      if (pathname.includes('/images/')) {
        pictures.push(pathname)
        if (pathname.endsWith('/images/2')) await slowImage
        return route.fulfill({ contentType: 'image/png', body: photo })
      }
      const responses = {
        '/api/auth/me': user,
        '/api/auth/login': { token: 'media-test-two', user: { ...user, id: 8 } },
        '/api/community/posts': { records: posts, total: posts.length }
      }
      return route.fulfill({ json: { code: 200, data: responses[pathname] || [] } })
    })
    await page.goto(base + '/community')
    const feed = page.locator('.post')
    await feed.last().waitFor()
    await page.waitForFunction(() => document.querySelectorAll('.photo img').length >= 2 &&
      document.querySelectorAll('.post .avatar img').length >= 2)
    const active = await page.evaluate(() => [...window.activeBlobs.values()])
    assert.equal(active.filter(size => size === avatar.length).length, 1)
    assert.equal(pictures.length, active.filter(size => size === photo.length).length)
    assert.ok(pictures.length < posts.length, 'initially distant posts must not fetch their images')
    assert.equal(avatarSessions.length, 1)
    console.log('First screen', pictures.length, 'original photos, 1 shared avatar for 10 posts')

    const second = page.waitForRequest(request => request.url().endsWith('/images/2'))
    await feed.first().getByRole('button', { name: '下一张图片' }).click()
    await second
    const canceled = page.waitForEvent('requestfailed', request => request.url().endsWith('/images/2'))
    await feed.first().getByRole('button', { name: '上一张图片' }).click()
    await canceled
    releaseSecondImage()
    await feed.first().getByAltText('第 1 张帖子图片').waitFor()
    await feed.last().scrollIntoViewIfNeeded()
    await feed.last().getByAltText('第 1 张帖子图片').waitFor()
    await page.waitForFunction(() => !document.querySelector('.post').querySelector('.photo img'))
    assert.ok(await page.evaluate(() => window.activeBlobs.size) < posts.length)

    await page.getByRole('button', { name: '测试，账户菜单' }).click()
    await page.getByText('退出登录', { exact: true }).click()
    await page.waitForURL('**/login')
    assert.equal(await page.evaluate(() => window.activeBlobs.size), 0)
    await page.locator('#login-username').fill('test-two')
    await page.locator('#login-password').fill('fixture')
    await page.locator('.auth-form .submit').click()
    await page.waitForURL('**/dashboard')
    await page.getByRole('navigation', { name: '快捷导航' }).getByRole('link', { name: '交流' }).click()
    await feed.first().locator('.avatar img').waitFor()
    assert.equal(avatarSessions.at(-1), 'Bearer media-test-two')
    console.log('Interrupted request canceled, offscreen resources released, second account fetched its own media')
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
