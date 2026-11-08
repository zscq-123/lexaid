import yalePageBoundaries from './yale-page-boundaries.js'

const clj = 'China-Law-Journal-Archive'
const yale = 'Yale-Law-Archive-OSF'
const abstractLabel = /^摘\s*要\s*[:：]/
const keywordsLabel = /^关\s*键\s*词\s*[:：]/
const leaders = /\.{3,}|…{2,}|⋯{2,}/
const fixedLayout = /\t|\S {2,}\S|[│┃|]/
const noteStart = /^\s*(?:\[\d+\]|【\d+】|[*＊†‡]|[①-⑳])/

export function publicationLine({ source, publishYear } = {}) {
  const publication = String(source ?? '').trim()
  const year = String(publishYear ?? '').trim()
  if (year && publication.match(/\d+/g)?.includes(year)) return publication
  return [publication, year].filter(Boolean).join(' · ')
}

function headingKind(text, letteredSections) {
  if (text.length > 150 || leaders.test(text) || /[。！？；;.!?]$/.test(text)) return null
  if (/^(?:目录|目次|引言|绪论|前言|结语|结论|参考文献|致谢|摘要|introduction|background|discussion|conclusions?|references|abstract|acknowledg(?:e)?ments?)$/i.test(text)) return 'heading'
  if (/^[一二三四五六七八九十百]+、/.test(text)) return 'heading'
  if (/^[（(][一二三四五六七八九十百]+[)）]/.test(text)) return 'subheading'
  if (/^\d+(?:\.\d+)+\.?\s+\p{L}/u.test(text)) return 'subheading'
  if (/^\d+\.\s+\p{L}/u.test(text)) return 'heading'
  if (/^[IVX]+\.\s+\p{L}/u.test(text)) return letteredSections ? 'subheading' : 'heading'
  if (/^[A-Z]\.\s+\p{L}/u.test(text)) return letteredSections ? 'heading' : 'subheading'
  return null
}

const compact = text => text.replace(/\s+/g, '')

// Only skip a front matter block when the reader already displays all three fields.
function withoutDuplicateFrontMatter(text, { title, scholarName, summary, sourceDataset }) {
  if (!title || !scholarName || !summary) return text
  const lines = text.split('\n')
  if (sourceDataset === yale) {
    const label = lines.findIndex(line => /^\s*abstract\s*:?\s*$/i.test(line))
    if (label < 2 || label > 16) return text
    const header = lines.slice(0, label).filter(line => line.trim())
    if (compact(header.pop() || '') !== compact(scholarName) || compact(header.join('')) !== compact(title)) return text
    const expected = compact(summary)
    let extracted = ''
    for (let i = label + 1; i < lines.length; i++) {
      extracted += compact(lines[i])
      if (extracted === expected) {
        const remainder = lines.slice(i + 1)
        const next = remainder.find(line => line.trim())?.trim()
        if (!next || /^key\s*words?\s*:/i.test(next) || headingKind(next, false)) return remainder.join('\n')
        break
      }
      if (!expected.startsWith(extracted)) break
    }
    return text
  }
  const abstract = lines.findIndex(line => abstractLabel.test(line.trimStart()))
  if (abstract < 2 || abstract > 8) return text
  const author = lines[abstract - 1].trim().replace(/[*＊]$/, '')
  const name = value => compact(value).replace(/[；;]/g, '')
  if (compact(lines.slice(0, abstract - 1).join('')) !== compact(title) || name(author) !== name(scholarName)) return text
  const keywords = lines.findIndex((line, i) => i > abstract && keywordsLabel.test(line.trimStart()))
  if (keywords < 0) return text
  const extracted = lines.slice(abstract, keywords).join('').trimStart().replace(abstractLabel, '')
  return compact(extracted) === compact(summary) ? lines.slice(keywords).join('\n') : text
}

function canJoinWrap(previous, next) {
  // CLJ body lines are typically 38–40 CJK characters wide. A short line or
  // sentence ending can mark a paragraph break, even without a blank line.
  return previous.length >= 30 && previous.length <= 100 && /\p{Script=Han}/u.test(previous)
    && !/^\s/.test(next) && !/[。！？；：.!?][”’」』）)\]]*$/.test(previous.trimEnd())
    && !/\[\d+\]$/.test(previous.trimEnd())
}

function joinWrap(previous, next) {
  const space = /[a-z\d]$/i.test(previous) && /^[a-z\d]/i.test(next) ? ' ' : ''
  return previous + space + next
}

function cljParagraphs(chunk) {
  const blocks = []
  let previous = ''
  let inNotes = false
  for (const raw of chunk.split('\n')) {
    const text = raw.trim()
    if (!text) continue
    const kind = headingKind(text, false)
    // A note may wrap across several lines; keep its continuations together.
    if (noteStart.test(raw)) inNotes = true
    if (inNotes || leaders.test(raw) || fixedLayout.test(raw) || keywordsLabel.test(text)
      || /^\d+$|^\(?\d+\)|^（\d+）/.test(text)) {
      const last = blocks.at(-1)
      if (last?.kind === 'line') last.text += '\n' + raw
      else blocks.push({ kind: 'line', text: raw })
    } else if (kind) {
      blocks.push({ kind, text })
    } else if (blocks.at(-1)?.kind === 'paragraph' && canJoinWrap(previous, raw)) {
      const last = blocks.at(-1)
      last.text = joinWrap(last.text, raw)
    } else {
      blocks.push({ kind: 'paragraph', text: raw })
    }
    previous = raw
  }
  return blocks
}

function layoutBlock(text, letteredSections) {
  if (text.includes('\n') || fixedLayout.test(text) || leaders.test(text)) return { kind: 'line', text }
  return { kind: headingKind(text.trim(), letteredSections) || 'paragraph', text }
}

function yaleProse(text) {
  // In the OSF PDF extracts an indented first line followed by full-width,
  // flush-left lines can identify prose. Leave ambiguous breaks and hyphens intact.
  const lines = text.split('\n')
  const isProse = lines.length > 1 && /^ {3,7}[A-Z][a-z]/.test(lines[0])
    && lines.slice(1).every(line => /^[\p{L}“‘]/u.test(line))
    && lines.slice(0, -1).every(line => line.length >= 50 && !line.endsWith('-'))
    && lines.every(line => !/\t|\S {3,}\S/.test(line) && !leaders.test(line))
    && /[.!?][”’)"']?\d*$/.test(lines.at(-1).trimEnd())
  return isProse ? lines.map(line => line.trim()).join(' ') : null
}

function yaleBlocks(text, letteredSections) {
  const blocks = []
  let retained = []
  for (const part of text.split(/\n(?= {3,7}[A-Z][a-z])/)) {
    const prose = yaleProse(part)
    if (prose === null) {
      retained.push(part)
      continue
    }
    if (retained.length) blocks.push(layoutBlock(retained.join('\n'), letteredSections))
    retained = []
    blocks.push({ kind: 'paragraph', text: prose })
  }
  if (retained.length) blocks.push(layoutBlock(retained.join('\n'), letteredSections))
  return blocks
}

function bodyChunks(text, frontMatter) {
  const extractedPdf = frontMatter?.sourceDataset === clj || frontMatter?.sourceDataset === yale
  const body = extractedPdf ? withoutDuplicateFrontMatter(text, frontMatter) : text
  return body.split(/\n(?:[^\S\n]*\n)+/).filter(chunk => chunk.trim())
}

function firstOutline(chunks) {
  return chunks.find(chunk => /^[A-Z]+\.\s+\p{L}/u.test(chunk.trim()) && !chunk.trim().includes('\n'))
}

function layoutChunks(chunks, sourceDataset, letteredSections) {
  if (sourceDataset === clj) return chunks.flatMap(cljParagraphs)
  return chunks.flatMap(chunk => {
    const text = chunk.replace(/^\n+|\n+$/g, '')
    if (sourceDataset === yale) return yaleBlocks(text, letteredSections)
    return layoutBlock(text, letteredSections)
  })
}

export function paperBlocks(text, options = {}) {
  if (typeof text !== 'string' || !text.trim()) return []
  const chunks = bodyChunks(text.replace(/\r\n?/g, '\n'), options)
  const lettered = options.sourceDataset !== clj && /^A\.\s/.test(firstOutline(chunks)?.trim() || '')
  return layoutChunks(chunks, options.sourceDataset, lettered)
}

function verifiedYalePages({ content, sourceUrl }) {
  const entry = yalePageBoundaries[sourceUrl]
  if (!entry?.ends?.length || entry.length !== content.length || entry.ends.at(-1) !== content.length) return null

  let signature = 2166136261
  for (let i = 0; i < content.length; i++) {
    signature = Math.imul(signature ^ content.charCodeAt(i), 16777619)
  }
  if ((signature >>> 0).toString(16).padStart(8, '0') !== entry.checksum) return null

  const pages = []
  let start = 0
  for (const end of entry.ends) {
    if (!Number.isSafeInteger(end) || end < start || end > content.length) return null
    if (end < content.length && !content.startsWith('\n\n', end)) return null
    pages.push(content.slice(start, end))
    start = end + 2
  }
  return pages
}

export function paperSourcePages(paper) {
  if (typeof paper?.content !== 'string') return null
  const text = paper.content.replace(/\r\n?/g, '\n')
  const isClj = paper.sourceDataset === clj
  const printedRange = isClj && /第\s*(\d+)\s*(?:[–—-]\s*(\d+)\s*)?页/.exec(paper.source || '')
  let pages

  if (text.includes('\f')) {
    pages = text.split('\f')
    // PDF text exports often terminate the final physical page with a form feed.
    if (!pages.at(-1).trim()) pages.pop()
  } else if (printedRange && text.trim()) {
    // The CLJ importer strips blank lines within each page before joining pages with \n\n.
    pages = text.split('\n\n')
    if (pages.some(page => !page.trim())) return null
  } else if (paper.sourceDataset === yale) {
    pages = verifiedYalePages(paper)
    if (!pages) return null
  } else {
    return null
  }

  let first = 1
  if (printedRange) {
    first = Number(printedRange[1])
    const last = Number(printedRange[2] || printedRange[1])
    if (!Number.isSafeInteger(last) || first < 1 || last - first + 1 !== pages.length) return null
  }
  // Heading levels use the first outline in the document, including on subsequent pages.
  let letteredSections = false
  if (!isClj) {
    for (const [i, page] of pages.entries()) {
      const outline = firstOutline(bodyChunks(page, i === 0 ? paper : null))
      if (outline) {
        letteredSections = /^A\.\s/.test(outline.trim())
        break
      }
    }
  }
  // The Vue reader keeps the visible page's blocks in a computed property.
  // Keep only source text here so visiting many pages does not accumulate formatted paragraphs.
  return pages.map((page, index) => ({
    number: first + index,
    get blocks() {
      return layoutChunks(bodyChunks(page, index === 0 ? paper : null), paper.sourceDataset, letteredSections)
    }
  }))
}
