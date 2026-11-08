const path = require('node:path')
const { pathToFileURL } = require('node:url')
const { contentTypes, readRendererFile } = require('./renderer-server')

const rendererURL = 'lexaid://app'
const policy = "default-src 'self'; connect-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"

function apiBase(raw) {
  if (!raw) throw new Error('Set LEXAID_RELEASE_API_URL to the deployed HTTPS API endpoint.')
  const url = new URL(raw)
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
    throw new Error('Production API must be an HTTPS URL without credentials, query or fragment.')
  }
  return url.href.replace(/\/+$/, '')
}

function createAppHandler(distRoot, backend, fetchURL) {
  const base = apiBase(backend)
  return async request => {
    const url = new URL(request.url)
    if (url.protocol !== 'lexaid:' || url.host !== 'app' || url.username || url.password) {
      return new Response(null, { status: 403 })
    }
    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) {
      const headers = new Headers()
      for (const key of ['authorization', 'content-type', 'accept', 'if-none-match']) {
        if (request.headers.has(key)) headers.set(key, request.headers.get(key))
      }
      const init = { method: request.method, headers, redirect: 'error', credentials: 'omit', signal: request.signal }
      if (request.body) {
        init.body = request.body
        init.duplex = 'half'
      }
      try {
        return await fetchURL(base + url.pathname.slice(4) + url.search, init)
      } catch {
        return Response.json({ code: 502, message: '暂时无法连接 LexAid 服务器' }, { status: 502 })
      }
    }
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response(null, { status: 405 })
    try {
      const file = await readRendererFile(distRoot, url.pathname)
      const response = await fetchURL(pathToFileURL(file).href, { method: request.method, signal: request.signal })
      response.headers.set('Content-Type', contentTypes[path.extname(file).toLowerCase()] || 'application/octet-stream')
      response.headers.set('Content-Security-Policy', policy)
      response.headers.set('Cache-Control', path.basename(file) === 'index.html' ? 'no-cache' : 'public, max-age=31536000, immutable')
      return response
    } catch (error) {
      return new Response(null, { status: error.statusCode || 404 })
    }
  }
}

module.exports = { rendererURL, apiBase, createAppHandler }
