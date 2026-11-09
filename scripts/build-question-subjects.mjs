#!/usr/bin/env node
import { createHash } from 'node:crypto'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createReadStream } from 'node:fs'
import { createGunzip, gzipSync } from 'node:zlib'
import { createInterface } from 'node:readline'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { buildCodeIndex, classify } from './legal-subjects.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const seeds = join(root, 'backend/src/main/resources/data.sql')
const lines = (file) =>
  createInterface({ input: createReadStream(file).pipe(createGunzip()), crlfDelay: Infinity })
const escapes = { 0: '\0', n: '\n', r: '\r', Z: '\x1a' }
function readCell(raw) {
  if (raw === 'NULL') return null
  if (raw[0] !== "'") return Number(raw)
  return raw.slice(1, -1).replace(/''|\\(.)/gs, (all, c) => (all === "''" ? "'" : (escapes[c] ?? c)))
}
// Only reads the explicit VALUES tuples emitted by our MySQL fixture builders.
function* tuples(body) {
  const cells = /\s*('(?:''|\\.|[^'\\])*'|NULL|[0-9]+)\s*([,)])/gsy
  const start = /\s*,?\s*\(/gy
  let offset = 0
  while (offset < body.length) {
    start.lastIndex = offset
    if (!start.exec(body)) {
      if (/^\s*[,;]?\s*$/.test(body.slice(offset))) return
      throw Error('Unexpected data after SQL tuple')
    }
    cells.lastIndex = start.lastIndex
    const row = []
    let cell
    do {
      cell = cells.exec(body)
      if (!cell) throw Error('Unrecognized SQL value')
      row.push(readCell(cell[1]))
    } while (cell[2] === ',')
    yield row
    offset = cells.lastIndex
  }
}
function question(row) {
  const [id, type, stem, options, answer, , , category] = row
  return {
    id,
    stem,
    category,
    options: options ? JSON.parse(options) : null,
    answer: ['SINGLE', 'MULTIPLE'].includes(type) ? JSON.parse(answer) : answer,
    sources: [],
    citations: []
  }
}
function seedRows(source, table) {
  const section = new RegExp(
    'INSERT IGNORE INTO ' + table + ' \\([^)]*\\) VALUES\\s*([\\s\\S]*?);(?:\\r?\\n|$)'
  ).exec(source)
  if (!section) throw Error('Missing seed table ' + table)
  return [...tuples(section[1])]
}
const quote = (value) => "'" + value.replace(/'/g, "''") + "'"
function insert(table, rows) {
  let text = ''
  for (let i = 0; i < rows.length; i += 250)
    text += 'INSERT INTO ' + table + ' VALUES\n' + rows.slice(i, i + 250).join(',\n') + ';\n'
  return text
}

export async function buildSubjectSnapshot(bank, output) {
  const seed = await readFile(seeds, 'utf8')
  const questions = new Map(seedRows(seed, 'question').map((row) => [row[0], question(row)]))
  const seedLaws = new Map(
    seedRows(seed, 'legal_provision').map(([id, law, , , article]) => [id, law + article])
  )
  for (const m of seed.matchAll(/\('QUESTION',\s*(\d+),\s*'PROVISION',\s*(\d+),\s*'EXAMINES'\)/g)) {
    questions.get(+m[1])?.citations.push(seedLaws.get(+m[2]))
  }
  let table
  for await (const line of lines(bank)) {
    const insertLine = /^INSERT IGNORE INTO (question_source|question) \(/.exec(line)
    if (insertLine) {
      table = insertLine[1]
      continue
    }
    if (!table || !line.startsWith('(')) continue
    for (const row of tuples(line)) {
      if (table === 'question') questions.set(row[0], question(row))
      else {
        const [id, dataset, , , , , meta] = row
        if (dataset === 'LeCoQA') {
          const metadata = JSON.parse(meta)
          questions
            .get(id)
            .sources.push({ statuteNames: metadata?.statuteNames ?? [], keywords: metadata?.keywords })
        }
      }
    }
    if (line.endsWith(';')) table = null
  }
  const prefixes = []
  for await (const line of lines(join(root, 'datasets/legal-codes/lexaid-laws-cn.sql.gz'))) {
    if (line.startsWith("('中华人民共和国") && line.slice(0, 210).includes('编'))
      prefixes.push(line.slice(0, 300))
  }
  const codeIndex = buildCodeIndex(prefixes),
    rows = [],
    bookRows = [],
    counts = new Map()
  for (const q of questions.values()) {
    const { discipline, subject, books } = classify(q, q.sources, codeIndex, q.citations)
    if (!discipline) throw Error('Unregistered subject: ' + subject)
    const hash = createHash('sha256').update(q.stem, 'utf8').digest('hex')
    rows.push(`(${q.id},'${hash}',${quote(discipline)},${quote(subject)})`)
    counts.set(discipline + ' / ' + subject, (counts.get(discipline + ' / ' + subject) ?? 0) + 1)
    for (const [code, book] of books) bookRows.push(`(${q.id},${quote(code)},${quote(book)})`)
  }
  const sql = `-- Generated from the shipped question bank, teaching seeds and legal code snapshots.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS question_legal_subject (
 question_id BIGINT PRIMARY KEY, discipline VARCHAR(64) NOT NULL, subject VARCHAR(64) NOT NULL,
 KEY idx_question_subject(discipline,subject,question_id),
 FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS question_code_book (
 question_id BIGINT NOT NULL, code_name VARCHAR(64) NOT NULL, book_name VARCHAR(128) NOT NULL,
 PRIMARY KEY(question_id,code_name,book_name), KEY idx_code_book(code_name,book_name,question_id),
 FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DROP TEMPORARY TABLE IF EXISTS tmp_subject_candidates,tmp_subject_verified,tmp_book_candidates;
CREATE TEMPORARY TABLE tmp_subject_candidates (id BIGINT PRIMARY KEY, digest CHAR(64), discipline VARCHAR(64), subject VARCHAR(64)) DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE tmp_book_candidates (id BIGINT, code_name VARCHAR(64), book_name VARCHAR(128)) DEFAULT CHARSET=utf8mb4;
${insert('tmp_subject_candidates', rows)}${insert('tmp_book_candidates', bookRows)}
START TRANSACTION;
CREATE TEMPORARY TABLE tmp_subject_verified AS
 SELECT s.id,s.discipline,s.subject FROM tmp_subject_candidates s JOIN question q ON q.id=s.id AND SHA2(q.stem,256)=s.digest;
INSERT INTO question_legal_subject (question_id,discipline,subject)
 SELECT s.id,s.discipline,s.subject FROM tmp_subject_verified s
 ON DUPLICATE KEY UPDATE discipline=s.discipline, subject=s.subject;
DELETE b FROM question_code_book b JOIN tmp_subject_verified s ON s.id=b.question_id;
INSERT INTO question_code_book (question_id,code_name,book_name)
 SELECT b.id,b.code_name,b.book_name FROM tmp_book_candidates b JOIN tmp_subject_verified s ON s.id=b.id;
COMMIT;
DROP TEMPORARY TABLE tmp_subject_candidates,tmp_subject_verified,tmp_book_candidates;
`
  await mkdir(dirname(output), { recursive: true })
  await writeFile(output, gzipSync(sql, { level: 9 }))
  return {
    questions: rows.length,
    bookLinks: bookRows.length,
    counts: Object.fromEntries([...counts].sort())
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const bank = resolve(process.argv[2] ?? join(root, 'datasets/lexaid-public-questions.sql.gz'))
  const output = resolve(process.argv[3] ?? join(root, 'datasets/question-legal-subjects.sql.gz'))
  console.log(JSON.stringify(await buildSubjectSnapshot(bank, output), null, 2))
}
