// Browser fixture for timed exams. Advance the server's clock without changing the computer clock.
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')
const base = process.env.SMOKE_BASE || 'http://127.0.0.1:18244'
const root = '/api/mock-exams'
const options = [{ key: 'A', text: '选项甲' }, { key: 'B', text: '选项乙' }]

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 320, height: 720 } })
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    page.on('dialog', dialog => dialog.accept())
    const user = { id: 42, username: 'candidate' }
    await page.addInitScript(u => {
      localStorage.setItem('lexaid_token','mock-token')
      localStorage.setItem('lexaid_user',JSON.stringify(u))
    }, user)
    let current, minutes = 0, shortClock = false, offline = false, saves = 0, submits = 0
    const clock = () => {
      current.serverTime = shortClock ? Date.now() : current.startedAt + minutes * 60000
      if (current.serverTime >= current.deadline) current.status = 'SUBMITTED'
      return current
    }
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const req = route.request(), p = new URL(req.url()).pathname
      const send = data => route.fulfill({ json: { code: 200, data } })
      if (p.endsWith('/auth/me')) return send(user)
      if (p === root + '/active') return send(current && clock().status === 'ACTIVE' ? current : null)
      if (p === root + '/history') return send(current ? [current] : [])
      if (p === root + '/catalog') return send(['MOCK','REAL'].flatMap(sourceType => ['OBJECTIVE','SUBJECTIVE'].map(paperType => ({
        sourceType, paperType, available: sourceType === 'MOCK' || paperType === 'OBJECTIVE', durationMinutes: 180,
        sections: [{ kind:'SINGLE', count:50, points:1 }, { kind:'MULTIPLE', count:50, points:2 }], totalPoints:150
      }))))
      if (p === root + '/sessions') {
        const setup = req.postDataJSON()
        assert.equal(setup.durationMinutes,70)
        const now = Date.now()
        current = { id: 7, ...setup, status:'ACTIVE', startedAt:now, serverTime:now, earlySubmitAt:now+1800000, deadline:now+(shortClock ? 1900 : 4200000),
          totalPoints:150, earnedPoints:0, score:null, items: ['SINGLE','MULTIPLE','INDEFINITE','SHORT'].map((kind,i) => ({
            kind, position:i+1, stem:'案例 '+(i+1), maxPoints:2, options, answer:'', revision:0, gradeStatus:'OPEN',
            sourceUrl:i===0?'https://example.org/paper':null,sourceLabel:i===0?'原卷答题卡':null
          })) }
        return send(current)
      }
      if (p === root + '/sessions/7') return send(clock())
      if (p.includes('/answers/')) {
        if (offline) return route.abort('internetdisconnected')
        const {revision,answer} = req.postDataJSON(), q = current.items[+p.split('/').pop()-1]
        const accepted = revision === q.revision+1
        if (accepted) { q.revision=revision; q.answer=answer; saves++ }
        return send({accepted,revision:q.revision,answer:q.answer,status:clock().status})
      }
      if (p.endsWith('/submit')) {
        assert.ok(minutes>=30);submits++
        current.status='SUBMITTED';current.items[3].gradeStatus='UNAVAILABLE'
        return send(current)
      }
      if (p.endsWith('/retry-grading')) {
        current.items[3].gradeStatus='GRADED'; current.items[3].points=2;current.earnedPoints=4;current.score=4;return send(current)
      }
      return send([])
    })
    const start = async () => {
      await page.goto(base+'/mock-exams')
      await page.getByLabel('考试时长').fill('0')
      assert.ok(await page.getByRole('button',{ name:'开始考试' }).isDisabled())
      await page.getByLabel('考试时长').fill('70')
      await page.getByRole('button',{ name:'开始考试' }).click()
      await page.waitForURL('**/mock-exams/run/7')
      await page.getByText('案例 1',{ exact:true }).waitFor()
    }
    await start()
    assert.ok(await page.getByRole('button',{ name:'提前交卷' }).isDisabled())
    const link = page.getByRole('link',{ name:'阅读原卷' })
    assert.equal(await link.getAttribute('target'),'_blank')
    await page.getByRole('radio',{ name:/选项甲/ }).check()
    await page.getByText('作答已同步',{ exact:true }).waitFor()
    assert.equal(current.items[0].answer,'["A"]')
    await page.getByRole('button',{ name:'下一题' }).click()
    await page.getByRole('checkbox',{ name:/选项甲/ }).check()
    await page.getByRole('checkbox',{ name:/选项乙/ }).check()
    await page.getByText('作答已同步',{ exact:true }).waitFor()
    await page.getByRole('button',{ name:'第 4 题',exact:true }).click()
    offline = true
    await page.getByRole('textbox',{name:'你的作答'}).fill('有参考依据的主观作答')
    await page.getByRole('button',{name:'重试保存'}).waitFor()
    await page.reload()
    await page.getByRole('button',{ name:'第 4 题',exact:true }).click()
    assert.equal(await page.getByRole('textbox').inputValue(),'有参考依据的主观作答')
    await page.getByRole('button',{name:'重试保存'}).waitFor()
    offline = false
    await page.getByRole('button',{name:'重试保存'}).click()
    await page.getByText('作答已同步',{ exact:true }).waitFor()
    assert.ok(saves>=3)
    current.items[3].answer = '来自另一窗口的作答'
    current.items[3].revision++
    await page.getByRole('textbox').fill('保留这份答案')
    await page.getByRole('button',{name:'保留本机作答'}).click()
    await page.getByText('作答已同步',{exact:true}).waitFor()
    assert.equal(current.items[3].answer,'保留这份答案')
    await page.goBack()
    await page.waitForURL('**/mock-exams/run/7')
    // A new page or navigation to another module must restore the active exam.
    await page.goto(base+'/wrong-book')
    await page.waitForURL('**/mock-exams/run/7')
    await page.getByText('案例 1',{ exact:true }).waitFor()
    assert.ok(!await page.getByRole('menuitem').count())
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    minutes=30
    await page.evaluate(() => window.dispatchEvent(new Event('online')))
    const early = page.getByRole('button',{name:'提前交卷'})
    await early.click()
    await page.getByRole('button',{name:'确认交卷'}).click()
    await page.waitForURL('**/mock-exams/result/7')
    assert.equal(submits,1)
    await page.getByRole('button',{name:'重试主观题评分'}).click()
    await page.getByText('4',{exact:true}).waitFor()
    minutes=0;shortClock=true
    await start()
    await page.waitForURL('**/mock-exams/result/7')
    assert.equal(submits,1) // timeout settles server-side, including when the page is asleep.
    assert.deepEqual(errors,[])
    console.log('Timed mock exam: custom duration, room lock, draft recovery, server cutoff, grading retry and mobile layout passed.')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode=1 })
