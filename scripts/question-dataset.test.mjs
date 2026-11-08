import assert from 'node:assert/strict'
import test from 'node:test'
import { parseChoice } from './build-public-question-dataset.mjs'

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
