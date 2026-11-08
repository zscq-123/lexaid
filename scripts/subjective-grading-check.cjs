const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const frontend = createRequire(path.join(__dirname, '../frontend/package.json'))
const { chromium } = frontend('playwright')

const question = { questionId: 1, type: 'SHORT', stem: '简述题目', userAnswer: '测试作答', correctAnswer: '评分要点' }
const noReference = { ...question, questionId: 2, stem: '缺少参考题', correctAnswer: '', gradingStatus: 'NO_REFERENCE', score: null, maxScore: 10 }
const objective = { questionId: 3, stem: '判断题', type: 'JUDGE', userAnswer: '', correctAnswer: '正确', gradingStatus: 'OBJECTIVE', isCorrect: false, score: 0, maxScore: 1 }
const graded = { ...question, gradingStatus: 'AI_GRADED', isCorrect: false, score: 7, maxScore: 10, feedback: '答到了第一个评分要点，需要补充第二个要点。' }
const pending = { ...question, gradingStatus: 'PENDING', isCorrect: null, score: null, maxScore: 10 }
const unavailable = { ...pending, gradingStatus: 'UNAVAILABLE', feedback: '评分服务暂时无法完成批改。' }
function result(detail) {
  return { total: 3, gradedTotal: detail.score == null ? 1 : 2, score: detail.score ?? 0,
    maxScore: detail.score == null ? 1 : 11, details: [detail, noReference, objective] }
}

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SMOKE_EXECUTABLE })
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    await page.addInitScript(() => {
      localStorage.setItem('lexaid_token', 'grade-fixture')
      sessionStorage.setItem('lexaid_result_7', JSON.stringify({ score: 999, details: [] }))
    })
    let pollCount = 0
    let retried = false
    let submitted = 0
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const api = new URL(route.request().url()).pathname
      let data
      if (api === '/api/auth/me') data = { id: 2, username: 'learner' }
      else if (api === '/api/exam/categories') data = []
      else if (api === '/api/exam/sessions') data = { total: 1, records: [
        { id: 8, total: 1, category: '民法', startTime: '2026-01-02T12:04:00', endTime: '2026-01-02T12:06:00' }
      ] }
      else if (api === '/api/exam/sessions/7') data = [{ ...question, id: 1, options: [], category: '民法' }]
      else if (api === '/api/exam/sessions/7/submit') {
        submitted++
        assert.equal(route.request().postDataJSON().answers[0].answer, '测试作答')
        data = result(pending)
      } else if (api === '/api/exam/sessions/7/retry-grading') {
        retried = true
        data = result(pending)
      } else if (api === '/api/exam/sessions/7/result') {
        data = result(retried ? graded : ++pollCount === 1 ? pending : unavailable)
      } else if (api === '/api/exam/sessions/8/result') {
        data = { total: 1, gradedTotal: 0, score: 0, details: [{ ...question, isCorrect: null }] }
      } else if (api === '/api/wrong-questions') {
        data = { records: [graded], total: 1 }
      } else throw Error('Unexpected route: ' + api)
      await route.fulfill({ json: { code: 200, data } })
    })
    const base = process.env.SMOKE_BASE || 'http://localhost:5173'
    await page.goto(base + '/exam/run/7')
    await page.getByPlaceholder('请输入你的作答').fill('测试作答')
    await page.getByRole('button', { name: '交 卷' }).click()
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await page.getByText('等待 DeepSeek 评分', {exact:true}).waitFor()
    assert.equal(submitted, 1)
    await page.getByText('缺少参考答案', {exact:true}).waitFor()
    await page.getByText('你的答案：（未作答）', {exact:true}).waitFor()
    await page.getByText('评分暂不可用', {exact:true}).waitFor()
    await page.getByRole('button', { name:'重新评分' }).click()
    await page.getByText('部分得分', {exact:true}).waitFor()
    await page.getByText('7 / 11 分', {exact:true}).waitFor()
    await page.getByText(graded.feedback, {exact:true}).waitFor()
    await page.reload()
    await page.getByText('DeepSeek 评分 7 / 10 分', {exact:true}).waitFor()
    await page.getByRole('button', { name:'查看错题本' }).click()
    await page.getByText('部分得分', {exact:true}).waitFor()
    await page.getByText(graded.feedback, {exact:true}).waitFor()
    await page.goto(base + '/exam/setup')
    await page.getByText('答题记录', {exact:true}).click()
    await page.getByRole('link', {name:'查看成绩'}).click()
    await page.getByText('历史作答未评分', {exact:true}).waitFor()
    await page.getByText('暂无已评分题目', {exact:true}).waitFor()
    console.log('Submit, asynchronous grades, retry, partial points, wrong book and historical results passed.')
  } finally { await browser.close() }
}

main().catch(err => { console.error(err); process.exitCode = 1 })
