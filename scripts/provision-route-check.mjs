// Actual Vue component with mocked APIs; no backend data is written. Run against a Vite dev server.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const { chromium } = require('playwright')
const hash=JSON.parse(readFileSync(new URL('../frontend/node_modules/.vite/deps/_metadata.json', import.meta.url))).browserHash
const browser = await chromium.launch(process.env.LEXAID_CHECK_EXECUTABLE ? { executablePath: process.env.LEXAID_CHECK_EXECUTABLE } : {})
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => { errors.push(e.message); console.log('PAGE ERROR',e.message) })
page.on('console', msg => { if(msg.type()==='error') console.log('CONSOLE',msg.text()) })
let failNext = true
let finishFavorite
let favoriteRequest
let notifyFavorite
const favoriteStarted = new Promise(resolve => { notifyFavorite = resolve })
const html = `<!doctype html><div id="app"></div><script type="module">
import {createApp,h} from '/node_modules/.vite/deps/vue.js?v=${hash}';
import {createRouter,createWebHistory,RouterView} from '/node_modules/.vite/deps/vue-router.js?v=${hash}';
import {createPinia} from '/node_modules/.vite/deps/pinia.js?v=${hash}';
import ElementPlus from '/node_modules/.vite/deps/element-plus.js?v=${hash}';
import ProvisionDetail from '/src/views/provisions/ProvisionDetail.vue';
const router=createRouter({history:createWebHistory(),routes:[{path:'/provisions/:id',component:ProvisionDetail}]});
window.auditRouter=router;
createApp({render:()=>h(RouterView)}).use(createPinia()).use(router).use(ElementPlus).mount('#app');
</script>`
const versions = [4,5].map(id => ({id, lawName:'中华人民共和国刑法',articleNo:'第二十条',effectiveDate:id===4?'1997-10-01':'2021-03-01'}))
await page.route('**/*', async route => {
  const req=route.request(), url=new URL(req.url())
  if(req.resourceType()==='document') return route.fulfill({contentType:'text/html',body:html})
  if(!url.pathname.startsWith('/api/')) return route.continue()
  let data=[]
  if(url.pathname==='/api/favorites' && req.method()==='POST') {
    favoriteRequest=req.postDataJSON()
    notifyFavorite()
    await new Promise(resolve=>finishFavorite=resolve)
  } else if(url.pathname==='/api/provisions/5' && failNext) {
    failNext=false
    return route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({code:500,message:'测试故障'})})
  } else if(/\/provisions\/\d+$/.test(url.pathname)) {
    const id=Number(url.pathname.split('/').at(-1))
    data={...versions.find(v=>v.id===id),content:`法条正文 ${id}`,tags:[],favorite:false}
  } else if(url.pathname.endsWith('/versions')) data=versions
  else if(url.pathname.endsWith('/related')) data={provisions:[],cases:[],viewpoints:[],questions:[]}
  return route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})})
})
try {
  await page.goto((process.env.LEXAID_CHECK_SITE || 'http://127.0.0.1:5179') + '/provisions/4')
  await page.getByText('法条正文 4',{exact:true}).waitFor()
  await page.getByRole('link',{name:/生效 2021-03-01/}).click()
  await page.getByText('法条加载失败，请重试。',{exact:true}).waitFor()
  assert.equal(await page.getByText('法条正文 4',{exact:true}).count(),0)
  assert.equal(await page.getByRole('button',{name:'添加笔记',exact:true}).count(),0)
  await page.getByRole('button',{name:'重新加载',exact:true}).click()
  await page.getByText('法条正文 5',{exact:true}).waitFor()
  await page.getByRole('button',{name:'收藏',exact:true}).click()
  await Promise.race([favoriteStarted, new Promise((_, reject) => setTimeout(() => reject(Error('Favorite request did not start')), 5000))])
  await page.getByRole('link',{name:/生效 1997-10-01/}).click()
  await page.getByText('法条正文 4',{exact:true}).waitFor()
  finishFavorite()
  await page.getByText('收藏成功',{exact:true}).waitFor()
  assert.deepEqual(favoriteRequest,{targetType:'PROVISION',targetId:5})
  assert.equal(await page.getByRole('button',{name:'收藏',exact:true}).count(),1)
  assert.equal(await page.getByRole('button',{name:'已收藏',exact:true}).count(),0)
  assert.deepEqual(errors,[])
  const report = { passed:true, checks:['failed version load hides stale text and notes','retry loads requested version','late favorite response does not mark another version'], pageErrors:errors }
  const output = new URL('./shots/provision-route/', import.meta.url)
  await mkdir(output, { recursive:true })
  await writeFile(new URL('report.json', output), JSON.stringify(report,null,2))
  console.log(JSON.stringify(report))
} catch(e) { console.log(await page.locator('body').innerText()); throw e }
finally { if(finishFavorite) finishFavorite(); await browser.close() }
