const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')
const { startRendererServer } = require('./renderer-server')

test('serves assets and falls back to index for SPA routes', async (t) => {
  const dist = await fs.mkdtemp(path.join(os.tmpdir(), 'lexaid-renderer-'))
  await fs.mkdir(path.join(dist, 'assets'))
  await fs.writeFile(path.join(dist, 'index.html'), '<h1>LexAid</h1>')
  await fs.writeFile(path.join(dist, 'assets', 'app.js'), 'console.log("ok")')
  const { server, url } = await startRendererServer(dist)
  t.after(() => server.close())
  t.after(() => fs.rm(dist, { recursive: true, force: true }))

  const asset = await fetch(`${url}/assets/app.js`)
  assert.equal(asset.status, 200)
  assert.match(asset.headers.get('content-type'), /text\/javascript/)

  const route = await fetch(`${url}/provisions/4`)
  assert.equal(route.status, 200)
  assert.equal(await route.text(), '<h1>LexAid</h1>')
})
