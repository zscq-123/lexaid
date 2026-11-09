const assert = require('node:assert/strict')
const test = require('node:test')
const { isPaperLink, isExamLink } = require('./paper-link')

test('permits only HTTPS links to known paper sources', () => {
  assert.equal(isPaperLink('https://doi.org/10.1234/example'), true)
  assert.equal(isPaperLink('https://clj.ac/archive/article/'), true)
  for (const url of ['https://openalex.org/W1', 'https://doaj.org/article/1', 'https://osf.io/preprints/lawarchive/abcde']) {
    assert.equal(isPaperLink(url), true)
  }
  for (const url of ['file:///tmp/foo', 'http://clj.ac', 'https://clj.ac.invalid', 'https://someone@doi.org', 'invalid']) {
    assert.equal(isPaperLink(url), false, url)
  }
})

test('exam windows open the published paper, exposing answer links only from results', () => {
  const paper = 'https://edu.sina.com.cn/official/2017-09-18/doc-ifykymue6828659.shtml'
  const answers = 'https://edu.sina.com.cn/official/2017-09-25/doc-ifymfcih4558711.shtml'
  assert.ok(isExamLink(paper + '#:~:text=86'))
  assert.ok(!isExamLink(answers))
  assert.ok(isExamLink(answers, true))
  assert.ok(!isExamLink(paper.replace('edu.sina.com.cn', 'edu.sina.com.cn.example')))
  assert.ok(!isExamLink(paper + '?redirect=elsewhere'))
})
