#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { createWriteStream, readFileSync, readdirSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { createGzip } from 'node:zlib'
import { buildSubjectSnapshot } from './build-question-subjects.mjs'

const LAW_BENCH_TASKS = {
  '1-1': '法条背诵',
  '1-2': '法律知识',
  '2-1': '文书校对',
  '2-2': '争议焦点',
  '2-3': '婚姻家庭',
  '2-4': '法律咨询分类',
  '2-5': '案例阅读',
  '2-6': '实体识别',
  '2-7': '司法舆情摘要',
  '2-8': '论点匹配',
  '2-9': '法律事件检测',
  '2-10': '事件触发词',
  '3-1': '法条预测',
  '3-2': '法律依据',
  '3-3': '罪名预测',
  '3-4': '刑期预测',
  '3-5': '刑期预测',
  '3-6': '案例分析',
  '3-7': '涉案金额计算',
  '3-8': '法律咨询'
}

const LEX_EVAL_TASKS = {
  '1_1': '法律概念',
  '1_2': '法条内容',
  '1_3': '法律演变',
  '2_1': '法律要素',
  '2_2': '事实理解',
  '2_3': '案例阅读',
  '2_4': '关系抽取',
  '2_5': '实体识别',
  '3_1': '罪名预测',
  '3_2': '法条预测',
  '3_3': '刑期预测',
  '3_4': '法律推理',
  '3_5': '法律推理',
  '3_6': '论点匹配',
  '4_1': '类案检索',
  '4_2': '文书审查',
  '5_1': '法律摘要',
  '5_2': '裁判分析',
  '5_3': '法律翻译',
  '5_4': '法律论述',
  '6_1': '平等与歧视',
  '6_2': '司法伦理',
  '6_3': '隐私与人格权'
}

const SOURCES = {
  LawBench: {
    license: 'Apache-2.0',
    url: 'https://github.com/open-compass/LawBench'
  },
  LexEval: {
    license: 'MIT',
    url: 'https://github.com/CSHaitao/LexEval'
  },
  LeCoQA: {
    license: 'MIT',
    url: 'https://github.com/oneal2000/LeCoQA'
  }
}

function cleanText(value) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .trim()
}

function canonicalText(value) {
  return cleanText(value).replace(/\s+/g, ' ')
}

function answerLetters(rawAnswer, optionKeys) {
  const raw = cleanText(rawAnswer).toUpperCase()
  const patterns = [
    /(?:正确答案|答案)[^A-H]*([A-H]+)/,
    /^([A-H]+)[。.!！]?$/
  ]
  for (const pattern of patterns) {
    const match = raw.match(pattern)
    if (!match) continue
    const letters = [...new Set([...match[1]])]
    if (letters.length > 0 && letters.every((letter) => optionKeys.includes(letter))) {
      return letters
    }
  }
  return []
}

export function parseChoice(question, rawAnswer) {
  const text = cleanText(question)
  const markers = [...text.matchAll(/([A-H])[:：]/g)]
  let best = []
  for (let start = 0; start < markers.length; start++) {
    if (markers[start][1] !== 'A') continue
    const run = [markers[start]]
    let expected = 'B'.charCodeAt(0)
    for (let i = start + 1; i < markers.length; i++) {
      if (markers[i][1].charCodeAt(0) === expected) {
        run.push(markers[i])
        expected++
      } else if (markers[i][1] === 'A') {
        break
      }
    }
    if (run.length > best.length) best = run
  }
  if (best.length < 2) return null

  const options = best.map((marker, index) => {
    const valueStart = marker.index + marker[0].length
    const valueEnd = index + 1 < best.length ? best[index + 1].index : text.length
    return { key: marker[1], text: cleanText(text.slice(valueStart, valueEnd)) }
  })
  if (options.some((option) => !option.text)) return null

  const letters = answerLetters(rawAnswer, options.map((option) => option.key))
  if (letters.length === 0) return null
  return {
    type: letters.length === 1 ? 'SINGLE' : 'MULTIPLE',
    stem: cleanText(text.slice(0, best[0].index)).replace(/[：:]$/, '').trim(),
    options,
    answer: letters
  }
}

function toQuestion({ question, answer, instruction, category, difficulty }) {
  const choice = parseChoice(question, answer)
  if (choice) {
    return {
      ...choice,
      analysis: cleanText(instruction),
      difficulty,
      category
    }
  }
  return {
    type: 'SHORT',
    stem: cleanText(question),
    options: null,
    answer: cleanText(answer),
    analysis: cleanText(instruction),
    difficulty,
    category
  }
}

function readJsonLines(file) {
  return readFileSync(file, 'utf8')
    .split(/\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      try {
        return JSON.parse(line)
      } catch (error) {
        throw new Error(`${file}:${index + 1} 不是有效 JSON：${error.message}`)
      }
    })
}

function readLeCoQa(file) {
  const sanitized = readFileSync(file, 'utf8').replace(/:\s*NaN(?=\s*[,}\n])/g, ': null')
  return JSON.parse(sanitized)
}

function lawBenchRecords(directory) {
  const records = []
  for (const file of readdirSync(directory).filter((name) => name.endsWith('.json')).sort()) {
    const taskCode = file.replace(/\.json$/, '')
    const items = JSON.parse(readFileSync(join(directory, file), 'utf8'))
    items.forEach((item, index) => {
      records.push({
        dataset: 'LawBench',
        sourceItemId: `${taskCode}:${String(index).padStart(4, '0')}`,
        sourceTask: LAW_BENCH_TASKS[taskCode] ?? taskCode,
        question: toQuestion({
          question: item.question,
          answer: item.answer,
          instruction: item.instruction,
          category: LAW_BENCH_TASKS[taskCode] ?? '法律综合',
          difficulty: Math.min(5, Number(taskCode[0]) + 1)
        }),
        metadata: {
          taskCode,
          instruction: cleanText(item.instruction),
          originalAnswer: cleanText(item.answer)
        }
      })
    })
  }
  return records
}

function lexEvalRecords(directory) {
  const records = []
  for (const file of readdirSync(directory).filter((name) => name.endsWith('.json')).sort()) {
    const taskCode = file.replace(/\.json$/, '')
    const items = readJsonLines(join(directory, file))
    items.forEach((item, index) => {
      records.push({
        dataset: 'LexEval',
        sourceItemId: `${taskCode}:${String(index).padStart(4, '0')}`,
        sourceTask: LEX_EVAL_TASKS[taskCode] ?? taskCode,
        question: toQuestion({
          question: item.input,
          answer: item.answer,
          instruction: item.instruction,
          category: LEX_EVAL_TASKS[taskCode] ?? '法律综合',
          difficulty: Math.min(5, Number(taskCode[0]) + 1)
        }),
        metadata: {
          taskCode,
          instruction: cleanText(item.instruction),
          originalAnswer: cleanText(item.answer)
        }
      })
    })
  }
  return records
}

function leCoQaRecords(file) {
  return readLeCoQa(file).map((item, index) => {
    const statutes = item['相关法规'] && typeof item['相关法规'] === 'object'
      ? Object.entries(item['相关法规'])
          .map(([name, content]) => `${name}：${cleanText(content)}`)
          .join('\n\n')
      : ''
    return {
      dataset: 'LeCoQA',
      sourceItemId: String(item.query_id ?? index),
      sourceTask: '法律咨询',
      question: {
        type: 'SHORT',
        stem: cleanText(item['问题']),
        options: null,
        answer: cleanText(item['答案文本']),
        analysis: statutes,
        difficulty: 3,
        category: '法律咨询'
      },
      metadata: {
        keywords: item['关键词'] ?? null,
        statuteNames: Array.isArray(item.match_name) ? item.match_name : [],
        originalQueryId: item.query_id ?? index
      }
    }
  })
}

function questionKey(question) {
  return createHash('sha256')
    .update(JSON.stringify([
      question.type,
      canonicalText(question.stem),
      question.options?.map((item) => [item.key, canonicalText(item.text)]) ?? null,
      question.type === 'SHORT' ? canonicalText(question.answer) : question.answer
    ]))
    .digest('hex')
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL'
  return `'${String(value)
    .replace(/\\/g, '\\\\')
    .replace(/\0/g, '\\0')
    .replace(/\x1a/g, '\\Z')
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/'/g, "''")}'`
}

async function writeChunk(stream, value) {
  if (!stream.write(value)) {
    await new Promise((resolvePromise) => stream.once('drain', resolvePromise))
  }
}

async function writeInsertBatches(stream, table, columns, rows, batchSize = 50) {
  for (let offset = 0; offset < rows.length; offset += batchSize) {
    const batch = rows.slice(offset, offset + batchSize)
    await writeChunk(
      stream,
      `INSERT IGNORE INTO ${table} (${columns.join(', ')}) VALUES\n${batch.join(',\n')};\n`
    )
  }
}

async function writeSqlGzip(output, records) {
  await mkdir(dirname(output), { recursive: true })
  const file = createWriteStream(output)
  const gzip = createGzip({ level: 9, mtime: 0 })
  gzip.pipe(file)

  const uniqueQuestions = new Map()
  let nextId = 10_000_000
  const sourceRows = []
  const skippedRecords = []
  for (const record of records) {
    if (!record.question.stem) {
      skippedRecords.push({
        dataset: record.dataset,
        sourceItemId: record.sourceItemId,
        reason: '题干为空'
      })
      continue
    }
    const key = questionKey(record.question)
    let entry = uniqueQuestions.get(key)
    if (!entry) {
      entry = { id: nextId++, question: record.question }
      uniqueQuestions.set(key, entry)
    }
    const source = SOURCES[record.dataset]
    sourceRows.push({
      questionId: entry.id,
      ...record,
      license: source.license,
      sourceUrl: source.url
    })
  }

  await writeChunk(gzip, `-- Generated by scripts/build-public-question-dataset.mjs\n-- Public datasets: LawBench, LexEval, LeCoQA\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nALTER TABLE question\n  MODIFY COLUMN stem MEDIUMTEXT NOT NULL,\n  MODIFY COLUMN options MEDIUMTEXT NULL,\n  MODIFY COLUMN answer MEDIUMTEXT NOT NULL,\n  MODIFY COLUMN analysis MEDIUMTEXT NULL;\n\n`)

  const questionRows = [...uniqueQuestions.values()].map(({ id, question }) => `(${[
    id,
    sqlString(question.type),
    sqlString(question.stem),
    sqlString(question.options ? JSON.stringify(question.options) : null),
    sqlString(question.type === 'SINGLE' || question.type === 'MULTIPLE'
      ? JSON.stringify(question.answer)
      : question.answer),
    sqlString(question.analysis || null),
    question.difficulty,
    sqlString(question.category)
  ].join(', ')})`)
  await writeInsertBatches(
    gzip,
    'question',
    ['id', 'type', 'stem', 'options', 'answer', 'analysis', 'difficulty', 'category'],
    questionRows
  )

  const provenanceRows = sourceRows.map((record) => `(${[
    record.questionId,
    sqlString(record.dataset),
    sqlString(record.sourceItemId),
    sqlString(record.sourceTask),
    sqlString(record.license),
    sqlString(record.sourceUrl),
    sqlString(JSON.stringify(record.metadata))
  ].join(', ')})`)
  await writeInsertBatches(
    gzip,
    'question_source',
    ['question_id', 'dataset', 'source_item_id', 'source_task', 'license_name', 'source_url', 'metadata'],
    provenanceRows
  )
  await writeChunk(gzip, 'SET FOREIGN_KEY_CHECKS = 1;\n')
  gzip.end()
  await new Promise((resolvePromise, reject) => {
    file.on('finish', resolvePromise)
    file.on('error', reject)
    gzip.on('error', reject)
  })
  return {
    inputRecords: records.length,
    importedSourceRecords: sourceRows.length,
    skippedRecords,
    importedQuestions: uniqueQuestions.size,
    duplicateQuestions: sourceRows.length - uniqueQuestions.size,
    sources: Object.fromEntries(
      Object.keys(SOURCES).map((dataset) => {
        const input = records.filter((record) => record.dataset === dataset).length
        const imported = sourceRows.filter((record) => record.dataset === dataset).length
        return [dataset, { input, imported, skipped: input - imported }]
      })
    )
  }
}

function parseArgs(argv) {
  const args = {}
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i]
    const value = argv[i + 1]
    if (!key?.startsWith('--') || !value) throw new Error(`参数无效：${key ?? ''}`)
    args[key.slice(2)] = resolve(value)
  }
  for (const required of ['lawbench', 'lexeval', 'lecoqa', 'output', 'manifest']) {
    if (!args[required]) throw new Error(`缺少 --${required}`)
  }
  return args
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const records = [
    ...lawBenchRecords(args.lawbench),
    ...lexEvalRecords(args.lexeval),
    ...leCoQaRecords(args.lecoqa)
  ]
  const stats = await writeSqlGzip(args.output, records)
  const subjectOutput = args.subjects ?? join(dirname(args.output), 'question-legal-subjects.sql.gz')
  const classification = await buildSubjectSnapshot(args.output, subjectOutput)
  const manifest = {
    classification,
    generatedBy: 'scripts/build-public-question-dataset.mjs',
    ...stats,
    datasets: Object.fromEntries(Object.entries(SOURCES).map(([name, value]) => [name, value]))
  }
  await mkdir(dirname(args.manifest), { recursive: true })
  const manifestStream = createWriteStream(args.manifest)
  manifestStream.end(`${JSON.stringify(manifest, null, 2)}\n`)
  await new Promise((resolvePromise, reject) => {
    manifestStream.on('finish', resolvePromise)
    manifestStream.on('error', reject)
  })
  console.log(JSON.stringify(manifest, null, 2))
}

const invokedDirectly = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href
if (invokedDirectly) {
  main().catch((error) => {
    console.error(error.stack || error.message)
    process.exitCode = 1
  })
}
