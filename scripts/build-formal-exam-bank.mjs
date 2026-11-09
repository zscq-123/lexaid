import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { gzipSync, gunzipSync } from 'node:zlib'
import { parseChoice } from './build-public-question-dataset.mjs'
import { classify } from './legal-subjects.mjs'

const directory = fileURLToPath(new URL('../datasets/formal-exams/', import.meta.url))
const revision = '044c695f62894deef41ad9a30797e1da0404945c'
const github = 'https://github.com/CSHaitao/LexEval/blob/' + revision
const raw = 'https://raw.githubusercontent.com/CSHaitao/LexEval/' + revision
// Exams retain the source's questions and answer key. The section, difficulty and subject
// are local teaching metadata, not difficulty statistics from examination authorities.
const tasks = {
  '3_4': '法考材料改编·多步推理',
  '6_2': '职业行为与刑事责任',
  '6_3': '权益保护'
}
const section26 = '227d2f007205c8c37aff2319a6c7f599e731e676b9d95413a91765bbfd6ae9d9'
// Source defects verified from the full question, independent of its published key.
const excluded = new Set([
  1340092, 1340129, 1340366, // inconsistent parties or historical limitation issue
  1340372, 1340450, 1340110, // more than one reading of the published answer
  1620549, 1630162             // singular instructions conflict with the section
])
const singular = /哪一|哪个|哪项|哪种|哪条|那一[项个种]|唯一|单项|单选|择一|一项|一种|一条/
const plural = /哪些|哪几|多项|多选|几项|多于一个/
const missingMaterial = /^(?:根据|结合|接续|承接|阅读)?(?:上述|上文|上题|前题|前述|上例|前案|图表|附图|所给材料)|见下图|见附表|接上题/
const weakTopic = /文明上网|网络文明|安全上网|安全使用|公共场所上网|账号安全|绿色上网|避免沉迷|医学临床实验|以下决策|最为合适|您认为|你认为|你觉得|道德困境|以上都[是对]|都不对|以上均有可能/
const actor = /甲|乙|丙|丁|戊|[一-龥]某|[一-龥][A-Z]{2}|公司|法院|公安机关|检察院|银行|债权人|债务人|保证人|被告|原告|申请人|当事人|[甲乙丙丁]国/
const evidence = /合同|协议|交付|登记|借款|担保|处分|追认|债权|债务|继承|诉讼|法院|起诉|立案|判决|仲裁|侦查|赔偿|犯罪|盗窃|故意|过失|刑罚|重伤|死亡|抢劫|受贿|管辖|申请|许可|处分|扣押|票据|执行|赔偿|签订|清偿|抵押|查封|出资|变更|违约|处罚|转让|物权|保险|代理|宪法|专利|商标|条约|承诺|劳动|授权|权利|法律效力|撤销|无效|侵权|权属|采纳|证明|质证/g

function subjectOf(stem) {
  const procedural = [
    [/国家赔偿|赔偿委员会|行政赔偿|行政复议|行政处罚/, '宪法与行政法'],
    [/刑事拘留|刑事诉讼|辩护人|审查起诉|刑事案件.*证据/, '刑事诉讼法学'],
    [/简易程序|支付令|民事诉讼|管辖异议|管辖权异议|送达.*判决书/, '民事诉讼法学'],
    [/准据法|冲突规范|适用下列哪国|如何适用法律/, '国际私法学'],
    [/国家继承|领土主权|国际条约|国际常设仲裁法院/, '国际公法学']
  ]
  for (const [pattern, subject] of procedural) if (pattern.test(stem)) return subject
  const value = classify({stem, options: [], answer: '', category: ''}, [], new Map()).subject
  return value === '待判定' ? '综合案例' : value
}

function assess(task, choice, id) {
  const { stem, options, answer } = choice
  const optionTexts = options.map(o => o.text)
  const text = [stem, ...optionTexts].join(' ')
  if (excluded.has(id) || missingMaterial.test(stem) || weakTopic.test(text)) return null
  if (stem.length < 80 && /在本案中|上述事实|上例中|回答第.{0,8}题/.test(stem)) return null
  if (/�|彳|程序绝|W此|冋|卬的|医疗费川|^月\d+日/.test(text)) return null
  const prompt = stem.split(/[。；;？！?!]/).filter(v => v.trim()).at(-1) || stem
  // Match the instruction, not in-story references to holding a single right or several objects.
  const asksOne = singular.test(prompt)
  const asksMany = plural.test(prompt)
  if (asksOne && answer.length !== 1 || asksMany && answer.length === 1) return null
  const acts = new Set(text.match(evidence) ?? []).size
  const factsInStem = actor.test(stem) && stem.length >= 55 &&
    (stem.match(/[，,。；;：:]/g) ?? []).length >= 2
  const factsInOptions = optionTexts.filter(t => actor.test(t) && t.length >= 26).length >= 3
  if (!factsInStem && !factsInOptions || acts < 2) return null
  // Non-exam tasks only survive when they include substantive connected case facts,
  // rather than a generic instruction with a few plausible-sounding answers.
  if (task !== '3_4' && (!factsInStem || acts < 4 || stem.length < 95)) return null
  if (factsInStem && acts >= 5 && stem.length >= 155 ||
      factsInOptions && acts >= 5 && optionTexts.every(t => t.length >= 32)) return 5
  return 4
}
const hash = value => createHash('sha256').update(value).digest('hex')
const normalize = text => text.normalize('NFKC').replace(/\s+/g, '')

export function simulation(task, line, rawRow) {
  if (!/^[A-D]{1,4}$/.test(rawRow.answer ?? '')) return null
  const choice = parseChoice(rawRow.input, rawRow.answer)
  if (!choice?.stem || choice.options.map(o => o.key).join('') !== 'ABCD') return null
  const optionsText = choice.options.map(o => normalize(o.text))
  if (new Set(optionsText).size !== optionsText.length || new Set(rawRow.answer).size !== rawRow.answer.length) return null
  // Withhold malformed prompts and ones which would give away a reference answer while testing.
  if (/参考答案\s*[:：]|正确答案\s*[:：]|答案解析\s*[:：]/.test(rawRow.input)) return null
  const id = 1000000 + Number(task.replace('_', '')) * 10000 + line
  if (!Object.hasOwn(tasks, task)) return null
  const difficulty = assess(task, choice, id)
  if (!difficulty) return null
  const taskName = tasks[task]
  const subject = subjectOf(choice.stem)
  const fingerprint = normalize([choice.stem, ...optionsText].join('|'))
  // Adapt neutral prompts as "one or more correct". Deterministic selection does not
  // depend on the number of correct options. Keep questions asking for one specific choice single.
  const neutral = !singular.test(choice.stem) && !plural.test(choice.stem)
  const indefinite = neutral && Number.parseInt(hash(fingerprint).slice(0, 4), 16) % 5 === 0
  return {
    id, kind: indefinite ? 'INDEFINITE' : choice.type,
    stem: choice.stem, options: choice.options, answer: choice.answer.join(''),
    difficulty, subject,
    sourceLabel: 'LexEval · ' + taskName + ' · ' + task + ' 第' + line + '题（模拟）',
    sourceUrl: github + '/data/' + task + '.json#L' + line, fingerprint
  }
}

export function validate(row) {
  if (!Number.isSafeInteger(row.id) || row.id <= 0 || !row.stem?.trim() || !row.answer?.trim()
      || !row.sourceLabel?.trim() || !row.subject?.trim() || !Number.isInteger(row.difficulty)
      || row.difficulty < 1 || row.difficulty > 5) throw Error('Incomplete exam question: ' + row.id)
  for (const url of [row.sourceUrl, row.readingUrl].filter(Boolean)) {
    if (new URL(url).protocol !== 'https:') throw Error('Unexpected URL for ' + row.id)
  }
  if (row.kind === 'SHORT') {
    if (row.options?.length) throw Error('Written question has options: ' + row.id)
    return
  }
  if (!['SINGLE','MULTIPLE','INDEFINITE'].includes(row.kind)) throw Error('Unknown section: ' + row.kind)
  const keys = row.options.map(o => o.key)
  if (keys.join('') !== 'ABCD' || row.options.some(o => !o.text.trim()) ||
      !/^[A-D]{1,4}$/.test(row.answer) || new Set(row.answer).size !== row.answer.length ||
      (row.kind === 'SINGLE' && row.answer.length !== 1) ||
      (row.kind === 'MULTIPLE' && row.answer.length < 2)) throw Error('Invalid answer: ' + row.id)
}

// UTF-8 hex expressions round-trip case text including quotes, backslashes and paragraph breaks
// independently of the SQL mode on an existing deployment.
const literal = v => v == null ? 'NULL' : typeof v === 'number' ? String(v)
  : "CONVERT(X'" + Buffer.from(v).toString('hex') + "' USING utf8mb4)"
const columns = 'id,source_type,paper_type,kind,subject,difficulty,stem,options_json,reference_answer,explanation,source_label,source_url,reading_url'
function tuple(row) {
  return '(' + [row.id, row.sourceType, row.kind === 'SHORT' ? 'SUBJECTIVE' : 'OBJECTIVE', row.kind,
    row.subject, row.difficulty, row.stem, row.options?.length ? JSON.stringify(row.options) : null,
    row.kind === 'SHORT' ? row.answer : JSON.stringify([...row.answer]), row.explanation ?? null,
    row.sourceLabel, row.sourceUrl, row.readingUrl ?? null].map(literal).join(',') + ')'
}
async function getFile(name, local) {
  if (local) return readFile(join(local, name))
  const url = raw + '/' + (name === 'LICENSE' ? name : 'data/' + name)
  // urllib also honours macOS system proxy settings on machines rebuilding the distributable.
  const downloader = `import sys, time, urllib.request
for attempt in range(3):
    try:
        with urllib.request.urlopen(sys.argv[1], timeout=20) as response:
            data = response.read()
        sys.stdout.buffer.write(data)
        break
    except OSError:
        if attempt == 2:
            raise
        time.sleep(1 + attempt)`
  return execFileSync('python3', ['-c', downloader, url], {maxBuffer: 16 * 1024 * 1024})
}

async function build(local) {
  const rows = [], sources = [], seen = new Set(), rejected = {}
  const lock = JSON.parse(await readFile(join(directory, 'lexeval-sources.json'), 'utf8'))
  for (const [task] of Object.entries(tasks)) {
    const file = task + '.json'
    const bytes = await getFile(file, local)
    if (hash(bytes) !== lock[file]) throw Error('Upstream checksum mismatch for ' + file)
    const lines = bytes.toString().trimEnd().split('\n')
    let accepted = 0
    const stemUses = new Map()
    for (const [index, line] of lines.entries()) {
      const row = simulation(task, index + 1, JSON.parse(line))
      if (!row || seen.has(row.fingerprint)) continue
      // Cap repeated generic stems with rotated options; keep distinct case-based questions.
      const stem = normalize(row.stem), uses = stemUses.get(stem) ?? 0
      if (uses >= 2) continue
      stemUses.set(stem, uses + 1)
      seen.add(row.fingerprint)
      rows.push({ ...row, sourceType: 'MOCK' })
      accepted++
    }
    sources.push({ file, source: github + '/data/' + file, rawRows:lines.length, accepted })
    rejected[file] = lines.length - accepted
  }
  const license = await getFile('LICENSE', local)
  if (hash(license) !== lock.LICENSE) throw Error('License checksum mismatch')
  await writeFile(join(directory, 'LexEval-LICENSE.txt'), license)
  for (const [file, sourceType] of [['original-subjective.json','MOCK'], ['original-objective.json','MOCK'], ['linked-real.json','REAL']]) {
    const entries = JSON.parse(await readFile(join(directory,file), 'utf8'))
    for (const row of entries) rows.push({kind:'SHORT', ...row, sourceType})
  }
  const ids = new Set(), counts = {}
  for (const row of rows) {
    validate(row)
    if (ids.has(row.id)) throw Error('Duplicate id: ' + row.id)
    ids.add(row.id)
    const group = counts[row.sourceType] ??= {}
    group[row.kind] = (group[row.kind] ?? 0) + 1
  }
  // Migration 26 is immutable and also fixes the exact identities owned by this importer.
  const original = await readFile(join(directory,'formal-exam-bank.sql.gz'))
  if (hash(original) !== section26) throw Error('Expected original migration 26')
  const previousIds = [...gunzipSync(original).toString().matchAll(/^\((\d+),/gm)].map(m => Number(m[1]))
  if (previousIds.length !== 3602) throw Error('Unexpected ID count in migration 26')
  const removed = previousIds.filter(id => !ids.has(id))
  const sql = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
  for (let i=0;i<removed.length;i+=300) {
    sql.push('DELETE FROM mock_exam_bank WHERE source_type=\'MOCK\' AND paper_type=\'OBJECTIVE\' AND source_url LIKE '
      + literal(github + '/data/%') + ' AND id IN (' + removed.slice(i,i+300).join(',') + ');')
  }
  for (let i = 0; i < rows.length; i += 30) {
    sql.push('INSERT INTO mock_exam_bank (' + columns + ') VALUES\n' + rows.slice(i,i+30).map(tuple).join(',\n')
      + '\nON DUPLICATE KEY UPDATE ' + columns.split(',').slice(1).map(c => c + '=VALUES(' + c + ')').join(',') + ';')
  }
  sql.push('COMMIT;\n')
  const gz = gzipSync(sql.join('\n'),{level:9})
  const manifest = {revision, sources, filteredRows:rejected, counts, questions:rows.length, originalSha256:section26, removedFromOriginal:removed.length, sha256:hash(gz)}
  await writeFile(join(directory,'formal-exam-bank-reviewed.sql.gz'),gz)
  await writeFile(join(directory,'manifest.json'), JSON.stringify(manifest,null,2) + '\n')
  console.log(JSON.stringify({questions: rows.length,counts,sha256:manifest.sha256}))
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await build(process.argv[2])
}
