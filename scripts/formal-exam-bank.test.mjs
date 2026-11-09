import test from 'node:test'
import assert from 'node:assert/strict'
import { simulation, validate } from './build-formal-exam-bank.mjs'

const facts = '甲公司将货车抵押给乙银行，仍由甲占有。甲随后把车辆交付丙并通知乙银行；乙银行要求清偿借款，丙以已付清价款为由拒绝交还车辆。'
const options = '\nA: 乙的担保债权消灭\nB: 乙可依法主张抵押权\nC: 丙当然取得优先地位\nD: 乙只能追究合同责任'
const prompt = facts + '下列哪一项正确？' + options

test('a simulated case keeps all four options, the source row and a valid key', () => {
  const row = simulation('3_4', 7, {input:prompt,answer:'B'})
  validate(row)
  assert.equal(row.kind,'SINGLE')
  assert.equal(row.answer,'B')
  assert.deepEqual(row.options.map(o => o.key),['A','B','C','D'])
  assert.match(row.sourceUrl,/3_4\.json#L7$/)
})
test('skip empty or visible keys, incomplete cases, low-demand tasks and section conflicts', () => {
  for (const answer of [null,'AA','E','参考答案为C','BC']) assert.equal(simulation('3_4',3,{input:prompt,answer}),null)
  assert.equal(simulation('3_4',3,{input:prompt.replace('下列哪一项','下列哪些选项'),answer:'B'}),null)
  assert.equal(simulation('3_4',3,{input:'正确答案：A\n'+prompt,answer:'A'}),null)
  assert.equal(simulation('3_4',3,{input:'根据上述案情，选出正确选项。'+options,answer:'B'}),null)
  assert.equal(simulation('6_2',3,{input:'下列属于文明上网的是？'+options,answer:'B'}),null)
  assert.equal(simulation('3_5',394,{input:prompt,answer:'B'}),null)
})
test('indefinite selection is independent of how many choices are correct', () => {
  let found = false
  for (let i=0;i<100;i++) {
    const input = facts + '对于编号'+i+'的车辆，下列说法正确的是？' + options
    const single=simulation('3_4',i+1,{input,answer:'B'})
    const multiple=simulation('3_4',i+1,{input,answer:'BCD'})
    if(single.kind==='INDEFINITE') {
      assert.equal(multiple.kind,'INDEFINITE')
      validate(single); validate(multiple)
      found = true
      break
    }
  }
  assert.ok(found)
})
