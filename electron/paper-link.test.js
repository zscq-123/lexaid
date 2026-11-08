const assert = require('node:assert/strict')
const test = require('node:test')
const { isPaperLink } = require('./paper-link')

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
