const fs = require('fs/promises')
const http = require('http')
const path = require('path')
const { pipeline } = require('node:stream/promises')

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

async function readRendererFile(distRoot, requestPath) {
  const decoded = decodeURIComponent(requestPath)
  const candidate = path.resolve(distRoot, `.${decoded}`)
  if (candidate !== distRoot && !candidate.startsWith(distRoot + path.sep)) {
    const error = new Error('invalid path')
    error.statusCode = 403
    throw error
  }
  try {
    const stat = await fs.stat(candidate)
    if (stat.isFile()) return candidate
  } catch {
    // SPA 路由回退到 index.html。
  }
  return path.join(distRoot, 'index.html')
}

function startRendererServer(distDirectory) {
  const distRoot = path.resolve(distDirectory)
  return new Promise((resolve, reject) => {
    const server = http.createServer(async (req, res) => {
      try {
        const requestUrl = new URL(req.url, 'http://127.0.0.1')
        const file = await readRendererFile(distRoot, requestUrl.pathname)
        const handle = await fs.open(file, 'r')
        try {
          res.writeHead(200, {
            'Content-Type': contentTypes[path.extname(file).toLowerCase()] || 'application/octet-stream',
            'Cache-Control': file.endsWith('index.html') ? 'no-cache' : 'public, max-age=31536000, immutable',
            'Content-Security-Policy': "default-src 'self'; connect-src 'self' http://127.0.0.1:* http://localhost:* https:; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:"
          })
          if (req.method === 'HEAD') res.end()
          else await pipeline(handle.createReadStream(), res)
        } finally {
          await handle.close()
        }
      } catch (error) {
        if (res.headersSent || res.destroyed) return res.destroy()
        res.writeHead(error.statusCode || 500, { 'Content-Type': 'text/plain; charset=utf-8' })
        res.end(error.statusCode === 403 ? 'Forbidden' : 'Renderer files are unavailable. Run npm run build in frontend first.')
      }
    })
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      resolve({ server, url: `http://127.0.0.1:${address.port}` })
    })
  })
}

module.exports = { contentTypes, readRendererFile, startRendererServer }
