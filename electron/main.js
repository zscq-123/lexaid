// LexAid 桌面壳：通过本机随机端口提供前端资源，避免 file:// 路由、资源路径与 CORS 问题。
const { app, BrowserWindow, shell, protocol, net } = require('electron')
const path = require('path')
const { startRendererServer } = require('./renderer-server')
const { isPaperLink, isExamLink } = require('./paper-link')
const { createAppHandler, rendererURL } = require('./app-protocol')

const distRoot = path.resolve(__dirname, '..', 'frontend', 'dist')
let rendererServer
if (app.isPackaged) {
  protocol.registerSchemesAsPrivileged([{ scheme: 'lexaid', privileges: {
    standard: true, secure: true, supportFetchAPI: true, corsEnabled: true
  } }])
}

function isRendererPage(raw, rendererUrl) {
  const page = new URL(raw)
  const root = new URL(rendererUrl)
  return page.protocol === root.protocol && page.host === root.host && !page.username && !page.password
}

function createWindow(rendererUrl) {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    title: 'LexAid 法律学习系统',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js')
    }
  })
  win.webContents.setWindowOpenHandler(({ url }) => {
    const page = new URL(win.webContents.getURL() || 'about:blank')
    const exam = /^\/mock-exams\/(run|result)\/\d+\/?$/.exec(page.pathname)
    const allowed = /^\/viewpoints\/\d+\/?$/.test(page.pathname) && isPaperLink(url)
      || exam && isExamLink(url, exam[1] === 'result')
    if (isRendererPage(page.href, rendererUrl) && allowed) {
      shell.openExternal(url).catch(error => console.error('无法打开来源', error.message))
    }
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', event => {
    if (!isRendererPage(event.url, rendererUrl)) event.preventDefault()
  })
  win.loadURL(rendererUrl)
}

let rendererUrl
function showAppWindow() {
  const win = BrowserWindow.getAllWindows()[0]
  if (win) {
    if (win.isMinimized()) win.restore()
    win.show()
    win.focus()
  } else if (rendererUrl) {
    createWindow(rendererUrl)
  }
}

async function start() {
  await app.whenReady()
  if (app.isPackaged) {
    const { apiUrl } = require('./release-config.json')
    protocol.handle('lexaid', createAppHandler(path.join(__dirname, 'dist'), apiUrl, (url, init) => net.fetch(url, init)))
    rendererUrl = rendererURL
  } else {
    const renderer = await startRendererServer(distRoot)
    rendererServer = renderer.server
    rendererUrl = renderer.url
  }
  showAppWindow()
  app.on('activate', showAppWindow)
}

if (app.requestSingleInstanceLock()) {
  app.on('second-instance', showAppWindow)
  start().catch((error) => {
    console.error('Failed to start LexAid desktop:', error)
    app.quit()
  })
} else {
  app.quit()
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => rendererServer?.close())
