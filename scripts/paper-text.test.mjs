import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { gunzipSync } from 'node:zlib'
import { paperBlocks, paperSourcePages, publicationLine } from '../frontend/src/views/viewpoints/paper-text.js'

const options = { sourceDataset: 'China-Law-Journal-Archive', title: '法律研究', scholarName: '王甲；李乙', summary: '作者的摘要。' }

test('structured journal paragraphs and outline levels keep text as data', () => {
  const text = 'A. Introduction\r\n\r\n<script>source</script>\r\n\r\nI. Poland\r\n\r\nD. Public broadcasters\r\n\r\n3.2. Legal duty'
  assert.deepEqual(paperBlocks(text), [
    { kind: 'heading', text: 'A. Introduction' },
    { kind: 'paragraph', text: '<script>source</script>' },
    { kind: 'subheading', text: 'I. Poland' },
    { kind: 'heading', text: 'D. Public broadcasters' },
    { kind: 'subheading', text: '3.2. Legal duty' }
  ])
  assert.deepEqual(paperBlocks(null), [])
})

test('skips a CLJ title, byline and abstract only when all three match', () => {
  const text = '法律\n研究\n王甲李乙*\n摘要：作者的摘\n要。\n关键词：法律\n* 作者单位。'
  const kept = '关键词：法律\n* 作者单位。'
  assert.deepEqual(paperBlocks(text, options), [{ kind: 'line', text: kept }])
  for (const change of [{ title: '研究' }, { scholarName: '王甲' }, { summary: '不同版本的摘要。' }]) {
    const actual = paperBlocks(text, { ...options, ...change }).map(b => b.text).join('')
    assert.equal(actual.replace(/\s/g, ''), text.replace(/\s/g, ''))
  }
})

test('CLJ preserves TOC, enumerated items, page numbers, footnotes and tables around prose', () => {
  const wrap = '这一段讨论元宇宙背景下个人生物信息的法律保护及其原则与方法，'
  const text = [
    '目录', '一、讨论 ................... 3', '', '一、讨论', '（一）背景',
    wrap, '以及具体应用。', '（1）第一项', '（2）第二项', '38', '名称\t数值', '[1] 参见期刊，',
    '第25页。', '', '结语'
  ].join('\n')
  const blocks = paperBlocks(text, options)
  assert.deepEqual(blocks.map(b => b.kind), ['heading', 'line', 'heading', 'subheading', 'paragraph', 'line', 'heading'])
  assert.equal(blocks[4].text, wrap + '以及具体应用。')
  assert.ok(blocks[5].text.includes('（2）第二项\n38\n名称\t数值\n[1] 参见期刊，\n第25页。'))
})

test('unstructured lines retain their spacing and Yale reflows confidently indented prose', () => {
  const text = 'Column A    Column B\n21          22\n    Total   43'
  assert.deepEqual(paperBlocks(text), [{ kind: 'line', text }])
  const first = '     Conflicts between religious liberty and gender and LGBTQ equality'
  const last = 'are examined in the following discussion.'
  const blocks = paperBlocks(first + '\n' + last + '\n\n' + text, { sourceDataset: 'Yale-Law-Archive-OSF' })
  assert.deepEqual(blocks, [{ kind: 'paragraph', text: first.trim() + ' ' + last }, { kind: 'line', text }])
})

test('publication line prints the year once and handles partial metadata', () => {
  assert.equal(publicationLine({ source: '华夏法学（2024 年第1期）', publishYear: 2024 }), '华夏法学（2024 年第1期）')
  assert.equal(publicationLine({ source: 'Law Journal 12024', publishYear: 2024 }), 'Law Journal 12024 · 2024')
  assert.equal(publicationLine({ publishYear: 1998 }), '1998')
  assert.equal(publicationLine({ source: 'Law Journal' }), 'Law Journal')
  assert.equal(publicationLine({}), '')
})

test('Yale front matter requires a matching author and the complete abstract', () => {
  const record = {
    sourceDataset: 'Yale-Law-Archive-OSF', title: 'A Study of Law', scholarName: 'Jane Doe', summary: 'An abstract of this study.'
  }
  const text = 'A Study\nof Law\n\n   Jane Doe\n\nAbstract\n\nAn abstract of\nthis study.\n\nKeywords: Law.\n\nI. Introduction'
  assert.equal(paperBlocks(text, record)[0].text, 'Keywords: Law.')
  const moreText = text.replace('Keywords:', 'A longer abstract.\n\nKeywords:')
  assert.equal(paperBlocks(moreText, record)[0].text, 'A Study\nof Law')
  assert.equal(paperBlocks(text, { ...record, scholarName: 'Different Name' })[0].text, 'A Study\nof Law')
})

test('CLJ pagination verifies the printed range and only removes duplicate front matter on page one', () => {
  const first = '法律研究\n王甲李乙*\n摘要：作者的摘要。\n关键词：法律'
  const content = [first, first].join('\n\n').replaceAll('\n', '\r\n')
  const record = { ...options, source: '华夏法学（2024年第1期，第14–15页）', content }
  const pages = paperSourcePages(record)
  assert.deepEqual(pages.map(page => page.number), [14, 15])
  assert.deepEqual(pages[0].blocks, [{ kind: 'line', text: '关键词：法律' }])
  assert.equal(pages[1].blocks[0].text, '法律研究')
  assert.equal(paperSourcePages({ ...record, source: '华夏法学（第14–16页）' }), null)
  assert.equal(paperSourcePages({ ...record, source: '' }), null)
  assert.equal(paperSourcePages({ ...record, sourceDataset: 'some-other-journal' }), null)
  assert.equal(paperSourcePages({ ...record, content: null }), null)
})

test('explicit page breaks preserve blank pages and discard a final terminator', () => {
  const content = '\fOne\n\n12\f\fFour\f\n'
  const pages = paperSourcePages({ content })
  assert.deepEqual(pages.map(page => page.blocks.map(block => block.text)), [[], ['One', '12'], [], ['Four']])
  assert.deepEqual(pages.map(page => page.number), [1, 2, 3, 4])
  assert.equal(paperSourcePages({ content: 'One\n\n12\n\nFour' }), null)
  assert.equal(paperSourcePages({ content: 'One\f' }).length, 1)
})

test('Yale pages match a pinned PDF and reject changed text or a different source', () => {
  const archive = new URL('../datasets/legal-papers/yale/records.json.gz', import.meta.url)
  const record = JSON.parse(gunzipSync(readFileSync(archive))).find(row => row.source_url.endsWith('/2h73r_v1/'))
  const paper = { ...record, sourceUrl: record.source_url, sourceDataset: record.source_dataset, scholarName: record.scholar_name }
  const pages = paperSourcePages(paper)
  assert.ok(pages.length > 1)
  assert.deepEqual(pages[0].blocks, paperBlocks(paper.content.slice(0, 2613), paper))
  assert.deepEqual(pages.flatMap(page => page.blocks), paperBlocks(paper.content, paper))
  assert.equal(paperSourcePages({ ...paper, content: 'X' + paper.content.slice(1) }), null)
  assert.equal(paperSourcePages({ ...paper, content: paper.content.slice(1) }), null)
  assert.equal(paperSourcePages({ ...paper, sourceUrl: '' }), null)
})
