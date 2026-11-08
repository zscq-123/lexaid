const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')
const { apiBase, createAppHandler } = require('./app-protocol')

test('serves only the application origin and submits API requests to the configured HTTPS service', async () => {
  assert.throws(() => apiBase('http://example.org/api'))
  assert.throws(() => apiBase('https://user:pass@example.org/api'))
  let received
  const handler = createAppHandler('/unused','https://site.example:18443/api/',async (url, init) => {
    received = { url, init }
    return Response.json({code:200})
  })
  const req = new Request('lexaid://app/api/test?scope=mine',{
    method:'POST',headers:{authorization:'Bearer test','content-type':'text/plain',origin:'lexaid://app',cookie:'a=b'},body:'example'
  })
  assert.equal((await handler(req)).status,200)
  assert.equal(received.url,'https://site.example:18443/api/test?scope=mine')
  assert.equal(received.init.headers.get('authorization'),'Bearer test')
  assert.equal(received.init.headers.get('cookie'),null)
  assert.equal(received.init.headers.get('origin'),null)
  assert.equal(received.init.body,req.body)
  assert.equal(received.init.duplex,'half')
  assert.equal(await new Response(received.init.body).text(),'example')
  assert.equal(received.init.redirect,'error')
  assert.equal((await handler(new Request('lexaid://other/api/test'))).status,403)
})

test('bundled UI assets and SPA navigation use a stable origin and same-origin CSP',async t => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(),'lexaid-protocol-'))
  t.after(() => fs.rm(dir,{recursive:true,force:true}))
  await fs.writeFile(path.join(dir,'index.html'),'<main>LexAid</main>')
  let fileResponse
  const handler = createAppHandler(dir,'https://example.com/api',async (url, init) => {
    if (!url.startsWith('file:')) throw new Error('offline')
    const file = new URL(url)
    assert.equal(path.basename(file.pathname),'index.html')
    fileResponse = new Response(init.method === 'HEAD' ? null : await fs.readFile(file))
    return fileResponse
  })
  const spa = await handler(new Request('lexaid://app/profile'))
  assert.equal(spa,fileResponse)
  assert.equal(spa.bodyUsed,false)
  assert.equal(await spa.text(),'<main>LexAid</main>')
  assert.match(spa.headers.get('content-security-policy'),/connect-src 'self'/)
  assert.equal((await handler(new Request('lexaid://app/profile',{method:'HEAD'}))).body,null)
  assert.equal((await handler(new Request('lexaid://app/api/auth/me'))).status,502)
})
