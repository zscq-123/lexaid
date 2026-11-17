import {createRequire} from 'node:module'
import fs from 'node:fs'
import {pathToFileURL} from 'node:url'
const root='/Users/yangyongze/Desktop/lexaid/lexaid/'
const require=createRequire(root+'frontend/package.json'),{chromium}=require('playwright')
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true})
try {
 const page=await browser.newPage({viewport:{width:1280,height:900}}),errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.goto(pathToFileURL(root+'docs/audits/2026-11-12/review.html').href)
 await page.waitForSelector('article')
 const count=()=>page.locator('#count').textContent()
 if(!(await count()).includes('26286'))throw Error('total')
 await page.locator('#source').selectOption('lecoqa');if(!(await count()).includes('1543 / 26286'))throw Error('Le total')
 await page.locator('#decision').selectOption('confirmed');if(!(await count()).includes('216 / 26286'))throw Error('confirmed')
 await page.locator('#decision').selectOption('pending');if(!(await count()).includes('715 / 26286'))throw Error('pending')
 await page.locator('#decision').selectOption('');await page.locator('#search').fill('10025071')
 if(await page.locator('article').count()!==1)throw Error('search')
 const links=await page.locator('article a').evaluateAll(a=>a.map(x=>x.href))
 if(!links.includes('https://ecro.mee.gov.cn/fgbz/fl/202604/t20260416_1148993.html'))throw Error('additional official URL missing')
 await page.locator('#search').fill('');await page.locator('#source').selectOption('');await page.locator('#decision').selectOption('revised')
 if(!(await count()).includes('92 / 26286'))throw Error('revisions')
 await page.locator('#next').click();if(!(await count()).includes('第 2'))throw Error('pagination')
 await page.setViewportSize({width:390,height:844})
 if(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth))throw Error('overflow')
 if(errors.length)throw Error(errors.join(';'))
 const result={passed:true,total:26286,lecoqa:1543,confirmed:216,pending:715,revised:92,sourceAndDecisionFilters:true,search:true,pagination:true,additionalOfficialLinks:true,mobile390:true,pageErrors:errors}
 fs.writeFileSync(root+'docs/audits/2026-11-12/evidence/viewer-check.json',JSON.stringify(result,null,2)+'\n')
 console.log(JSON.stringify(result))
} finally {await browser.close()}
