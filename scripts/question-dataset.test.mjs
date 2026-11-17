import assert from 'node:assert/strict'
import test from 'node:test'
import { parseChoice, toQuestion } from './build-public-question-dataset.mjs'

test('parses compact single-choice questions', () => {
  assert.deepEqual(parseChoice('问题?A:甲;B:乙;C:丙;D:丁', '正确答案：B。'), {
    type: 'SINGLE',
    stem: '问题?',
    options: [
      { key: 'A', text: '甲;' },
      { key: 'B', text: '乙;' },
      { key: 'C', text: '丙;' },
      { key: 'D', text: '丁' }
    ],
    answer: ['B']
  })
})

test('parses multiline multiple-choice questions', () => {
  const parsed = parseChoice('问题\nA: 甲\nB: 乙\nC: 丙\nD: 丁', 'ACD')
  assert.equal(parsed.type, 'MULTIPLE')
  assert.deepEqual(parsed.answer, ['A', 'C', 'D'])
  assert.equal(parsed.options.length, 4)
})

test('does not invent answers for non-choice tasks', () => {
  assert.equal(parseChoice('请简述正当防卫的条件。', '应当存在正在进行的不法侵害'), null)
})

test('benchmark instructions cannot be imported as teaching explanations', () => {
  const question = toQuestion({ question: '问题?A:甲;B:乙', answer: 'A', instruction: '请只给出答案的序号', category: '法律知识', difficulty: 2 })
  assert.equal(question.analysis, null)
  assert.equal(question.type, 'SINGLE')
})

test('keeps an explicitly multiple-choice question even when only one option is correct', () => {
  const question = toQuestion({ question: '问题?A:甲;B:乙', answer: 'A', instruction: '不定项选择题，请选择所有正确选项', category: '法律知识', difficulty: 2 })
  assert.equal(question.type, 'MULTIPLE')
  assert.deepEqual(question.answer, ['A'])
  assert.equal(question.analysis, null)
  assert.equal(parseChoice('多项选择题：问题?A:甲;B:乙', 'A').type, 'MULTIPLE')
  assert.equal(parseChoice('问题?A:甲;B:乙', 'AB', '单项选择题'), null)
  assert.equal(parseChoice('问题?A:甲;B:乙', 'A', '单项选择题或多项选择题'), null)
})

test('accepts unambiguous source option labels and separators', () => {
  const question = '问题?A:甲;B:乙;C:丙;D:丁'
  assert.deepEqual(parseChoice(question, '选项ABC').answer, ['A', 'B', 'C'])
  assert.deepEqual(parseChoice(question, 'B D').answer, ['B', 'D'])
  assert.deepEqual(parseChoice(question, '参考答案：A、C。').answer, ['A', 'C'])
  assert.deepEqual(parseChoice(question, 'bd').answer, ['B', 'D'])
})

test('preserves LawBench native paired answer markers without accepting trailing prose', () => {
  const question = '问题?A:甲;B:乙;C:丙;D:丁'
  assert.deepEqual(parseChoice(question, '[正确答案]A<eoa>').answer, ['A'])
  assert.deepEqual(parseChoice(question, '[正确答案]BD<eoa>').answer, ['B', 'D'])
  for (const answer of ['[正确答案]A', 'A<eoa>', '[正确答案]A<eoa>B', '[正确答案]Because<eoa>', '[正确答案]A<eoa>解释', '[正确答案]AA<eoa>']) {
    assert.equal(parseChoice(question, answer), null, answer)
  }
})

test('preserves prose, contradictory or malformed answers for review instead of truncating them', () => {
  const question = '问题?A:甲;B:乙;C:丙;D:丁;E:戊;F:己'
  for (const answer of ['答案：Because of the facts', '正确答案：BACK', '答案是A或B', '答案A，另一答案是B', 'A（可能另有例外）', 'AA', 'ABZ']) {
    assert.equal(parseChoice(question, answer), null, answer)
    const imported = toQuestion({ question, answer, instruction: '请选择', category: '法律综合', difficulty: 2 })
    assert.equal(imported.type, 'SHORT')
    assert.equal(imported.answer, answer)
  }
})
