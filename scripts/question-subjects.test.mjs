import assert from 'node:assert/strict'
import test from 'node:test'
import { buildCodeIndex, classify } from './legal-subjects.mjs'

const civil = '中华人民共和国民法典'
const criminal = '中华人民共和国刑法'
const codeIndex = buildCodeIndex(
  [
    [civil, '第一编总则 · 第一章基本规定', '第一条'],
    [civil, '第三编合同 · 第一章一般规定', '第五百七十七条'],
    [civil, '第七编侵权责任 · 第一章一般规定', '第一千一百八十八条'],
    [criminal, '第一编总则 · 第一章刑法的任务', '第一条'],
    [criminal, '第二编分则 · 第一章危害国家安全罪', '第二百六十六条'],
    [criminal, '第二编分则 · 第一章危害国家安全罪', '第二百八十七条之二']
  ].map(([law, chapter, article]) => `('${law}', '法律', '${chapter}', '${article}', ''),`)
)
const question = (stem, extra = {}, sources = [], linked = []) =>
  classify({ stem, category: '法律知识', answer: '', ...extra }, sources, codeIndex, linked)

test('procedure requires its own evidence; a judgment alone does not identify a subject', () => {
  assert.equal(question('法院审理被告的案件，并作出判决。').subject, '待判定')
  assert.equal(question('承租人违约，债权人向法院起诉。').subject, '合同法')
  assert.equal(question('侦查中可否对犯罪嫌疑人取保候审？').subject, '刑事诉讼法学')
  assert.equal(question('申请财产保全的条件是？').subject, '民事诉讼法学')
  assert.equal(question('行政案件管辖权如何确定？').subject, '行政诉讼法学')
  assert.equal(question('行政复议机关的职权是什么？').subject, '行政法学')
})

test('distinguishes specialist subjects and cross-disciplinary questions', () => {
  const examples = [
    ['法的渊源分为哪些？', '法理学'],
    ['《唐律》适用于化外人吗？', '法律史'],
    ['律师的执业纪律有哪些？', '法律职业伦理'],
    ['谁有权解释宪法？', '宪法学'],
    ['申请商标能享有哪些权利？', '知识产权法学'],
    ['招聘歧视影响就业平等', '劳动与社会保障法学'],
    ['公司反垄断审查的标准', '经济法学'],
    ['排污应遵循哪些规范？', '环境与资源保护法学'],
    ['外国依法享有国家豁免吗？', '国际公法学'],
    ['如何确定准据法？', '国际私法学'],
    ['反倾销调查程序', '国际经济法学'],
    ['正当防卫和专利的规范依据分别是什么？', '跨学科综合']
  ]
  for (const [stem, expected] of examples) assert.equal(question(stem).subject, expected, stem)
})

test('source task labels and wrong answer choices do not become subject evidence', () => {
  const noisy = question(
    '请根据从给定的输入中抽取所有的实体并确定实体类型。实体类型列表有受害人，盗窃获利。输入文本：某机关在某日举行会议。选项如下',
    {
      category: '实体识别',
      options: [
        { key: 'A', text: '某机关（组织机构）' },
        { key: 'B', text: '盗窃罪' }
      ],
      answer: ['A']
    }
  )
  assert.deepEqual(noisy, { discipline: '综合与待判定', subject: '待判定', books: [] })
  assert.equal(question('法官判决应当遵循哪些原则？', { category: '刑期预测' }).subject, '刑法学')
  assert.equal(question('某人收到了传票。', { category: '司法伦理' }).subject, '待判定')
  assert.equal(
    question('员工要求解除劳动合同并补发工资。', { category: '法律咨询分类', answer: '合同纠纷' }).subject,
    '劳动与社会保障法学'
  )
  assert.equal(question('劳动人事争议仲裁委员会作出裁决。').subject, '劳动与社会保障法学')
})

test('code books require explicit local citations; use all referenced books, including Arabic numerals', () => {
  const found = question('合同解释', {}, [{ statuteNames: [civil + '第577条、第1188条'] }])
  assert.deepEqual(found.books, [
    [civil, '第三编 合同'],
    [civil, '第七编 侵权责任']
  ])
  assert.equal(question('我国刑法第1、266条', { category: '法条背诵' }).books.length, 2)
  assert.deepEqual(question('刑法第287条之二').books, [[criminal, '第二编 分则']])
  assert.deepEqual(question('民法典合同编').books, [[civil, '第三编 合同']])
  assert.deepEqual(question('刑法第二编').books, [[criminal, '第二编 分则']])
  for (const stem of [
    '合同纠纷如何处理？',
    '依据合同法第五百七十七条',
    '德国《民法典》第577条',
    '《外国刑法》第266条',
    '旧法中的刑法第266条'
  ])
    assert.deepEqual(question(stem).books, [], stem)
  assert.deepEqual(
    question('有关合同违约的题目，选出正确的日期。', {
      options: [
        { key: 'A', text: '5月1日' },
        { key: 'B', text: '民法典第577条' }
      ],
      answer: ['A']
    }).books,
    []
  )
  assert.deepEqual(question('行为人身份如何认定？', {}, [], [criminal + '第二百六十六条']).books, [
    [criminal, '第二编 分则']
  ])
})
