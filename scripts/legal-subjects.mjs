// Product study groups; question.category remains the original source task.
const GROUPS = {
  理论与法律史: ['法理学', '法律史', '法律职业伦理'],
  宪法与行政法: ['宪法学', '行政法学'],
  民商法: [
    '民法学',
    '合同法',
    '物权法',
    '侵权责任法',
    '人格权法',
    '婚姻家庭与继承法',
    '商法学',
    '知识产权法学'
  ],
  刑法: ['刑法学'],
  诉讼与争议解决: ['刑事诉讼法学', '民事诉讼法学', '行政诉讼法学', '仲裁法学'],
  经济与社会法: ['经济法学', '劳动与社会保障法学', '环境与资源保护法学'],
  国际法: ['国际公法学', '国际私法学', '国际经济法学'],
  综合与待判定: ['跨学科综合', '待判定']
}
const disciplineOf = new Map(
  Object.entries(GROUPS).flatMap(([group, subjects]) => subjects.map((s) => [s, group]))
)
const rules = {
  法理学:
    /法理学|法的(?:起源|历史发展|继承|移植|本质|价值|作用|渊源|效力|实施)|法律(?:渊源|实证主义|体系|规范的结构)|法律与道德|自然法学派|习近平法治思想|全面依法治国|法治体系/g,
  法律史:
    /法律史|中国法制史|外国法制史|唐律(?!师)|大清律|罗马法|中华法系|魏晋|古代刑罚|清末修律|中华民国临时约法|中华民国约法|明大诰|大明律|宋刑统|晋律|北齐律|法经|春秋决狱|八议入律/g,
  法律职业伦理:
    /法律职业(?:道德|伦理)|(?:法官|律师|检察官|司法人员).{0,8}职业(?:道德|伦理)|司法(?:伦理|廉洁)|法官(?:职业|惩戒)|律师(?:的)?(?:职业|执业纪律|保密义务)|检察官职业/g,
  宪法学: /宪法|宪政|全国人民代表大会|全国人大常委会|违宪审查|基本权利|民族区域自治|国家机构|选举法/g,
  行政法学:
    /行政(?:处罚|许可|复议|强制|拘留|机关.{0,3}职权|征收|法学)|治安管理处罚|国家赔偿法|行政行为|政府信息公开|城管执法/g,
  民法学:
    /民法(?:学|总则|通则)|民事(?:行为能力|权利能力|法律行为|主体)|监护人|法定代理人|民事权利|无权代理|表见代理|宣告失踪/g,
  合同法:
    /(?<!劳动|聘用)合同|要约|违约|定金|借款|欠款|债权人|债务人|代位权|租金|租赁|买卖价款|劳务报酬|承揽|承包款/g,
  物权法:
    /物权|抵押|质押|质权|留置权|所有权(?!证)|占有返还|用益物权|建设用地使用权|不动产登记|善意取得|相邻权/g,
  侵权责任法:
    /侵权责任|侵权行为|人身损害|精神损害赔偿|伤残等级|护理费|误工费|交强险|赔偿医疗费|过错责任|过错推定|安全保障义务|医疗损害|交通事故责任|损害赔偿责任/g,
  人格权法: /人格权|名誉权|隐私权|肖像权|姓名权|人格尊严|个人信息保护/g,
  婚姻家庭与继承法:
    /婚姻|离婚|结婚|婚前财产|彩礼|家庭暴力|抚养|赡养|继承人|遗嘱|遗产|遗赠|夫妻(?:共同|财产|债务)|收养|探望权/g,
  商法学:
    /公司法|商法学|合伙企业|股东|出资义务|公司章程|公司登记|农民专业合作社|证券投资基金|董事会|独立董事|票据法|破产法|破产债权|证券法|保险法|保险合同|海商法|海上货物运输/g,
  知识产权法学: /知识产权|著作权|版权|专利|商标|商业秘密|集成电路布图/g,
  刑法学:
    /(?<!诉讼)刑法|罪刑法定|犯罪(?:构成|中止|预备|未遂|既遂|故意|过失)|刑事责任(?:年龄|能力)|正当防卫|防卫过当|紧急避险|有期徒刑|量刑|缓刑|数罪并罚|共同犯罪|故意伤害|故意杀人|扰乱无线电(?:通讯|通信)管理秩序|盗窃|抢劫|贩卖.{0,8}毒品|吸食甲基苯丙胺|贩毒|窃得|盗走|盗割|扒窃|受贿|行贿|贪污|寻衅滋事|自首|累犯|犯罪(?:行为|金额|所得)|挪用.{0,5}罪|非法吸收公众存款|骗取.{0,6}财物|诈骗罪|犯.{1,8}罪/g,
  刑事诉讼法学:
    /刑事诉讼|取保候审|刑事强制措施|监视居住|非法证据排除|审查起诉|不起诉决定|补充侦查|刑事拘留|刑事附带民事|附带民事诉讼|死刑复核|提请批准逮捕|提请.{0,7}审查批准|辩护人/g,
  民事诉讼法学:
    /(?<!附带)民事诉讼|民事诉状|民事案件管辖|诉讼保全|财产保全|督促程序|先予执行|民事证据|第三人撤销之诉|执行异议之诉/g,
  行政诉讼法学: /行政诉讼|行政起诉|行政案件管辖|行政公益诉讼|行政诉讼原告/g,
  仲裁法学: /仲裁(?:法|委员会|协议|裁决|庭|员)/g,
  经济法学:
    /(?<!国际)经济法|反垄断|反不正当竞争|消费者权益|反商业贿赂|税收征收|个人所得税|企业所得税|增值税|产品质量法|政府采购|预算法|财政法|银行业监督|宏观调控/g,
  劳动与社会保障法学:
    /劳动(?:人事争议|法|合同|关系|争议|仲裁|报酬|保障|者)|社会保险|养老保险|工伤|最低工资|用人单位|加班费|(?:就业|招聘)歧视|社会救助|退役军人保障|劳动能力鉴定|失业保险/g,
  环境与资源保护法学:
    /环境(?:保护|法|污染|影响评价)|生态(?:环境|保护|补偿)|排污|环境资源|水污染|大气污染|固体废物|自然保护区|矿产资源法/g,
  国际公法学:
    /国际公法|国家主权|国际法院|外交豁免|外交特权|国际条约法|联合国宪章|国际海洋法|国际人道法|国家豁免|领海|专属经济区/g,
  国际私法学:
    /国际私法|准据法|冲突规范|法律冲突|涉外民事关系|外国判决.{0,6}承认|涉外民事.{0,6}法律适用|反致|识别与定性/g,
  国际经济法学:
    /世界贸易组织|关税与贸易总协定|国际经济法|反倾销|反补贴|国际贸易|国际投资争端|外资企业法|国际货物买卖|国际商事仲裁|信用证|贸易术语|WTO/g
}
const BOOK_SUBJECT = new Map(
  Object.entries({
    总则: '民法学',
    物权: '物权法',
    合同: '合同法',
    人格权: '人格权法',
    婚姻家庭: '婚姻家庭与继承法',
    继承: '婚姻家庭与继承法',
    侵权责任: '侵权责任法'
  })
)
const sourceCategories = {
  罪名预测: '刑法学',
  刑期预测: '刑法学',
  婚姻家庭: '婚姻家庭与继承法'
}
const consultation = {
  婚姻家庭: '婚姻家庭与继承法',
  抵押担保: '物权法',
  消费权益: '经济法学',
  海事海商: '商法学',
  人身损害: '侵权责任法',
  侵权: '侵权责任法',
  知识产权: '知识产权法学',
  行政诉讼: '行政诉讼法学',
  公司法: '商法学',
  合同纠纷: '合同法',
  债权债务: '合同法',
  刑事辩护: '刑事诉讼法学',
  劳动纠纷: '劳动与社会保障法学'
}
function votes(text) {
  const scores = new Map()
  for (const [subject, re] of Object.entries(rules)) {
    const hits = new Set(text.match(re) ?? [])
    if (hits.size) scores.set(subject, hits.size)
  }
  return scores
}
function choose(scores) {
  const sorted = [...scores].sort((a, b) => b[1] - a[1])
  if (!sorted.length) return null
  const top = sorted.filter((s) => s[1] === sorted[0][1]).map((s) => s[0])
  if (top.length === 1) return top[0]
  return top.every((s) => disciplineOf.get(s) === '民商法' && !['商法学', '知识产权法学'].includes(s))
    ? '民法学'
    : '跨学科综合'
}
function correctText(q) {
  if (!q.options?.length) return typeof q.answer === 'string' ? q.answer : ''
  return q.options
    .filter((o) => q.answer.includes(o.key))
    .map((o) => o.text)
    .join('。')
}
// Read the chart of articles and books from the bundled laws snapshot, not from inferred topics.
export function buildCodeIndex(lines) {
  const index = new Map()
  for (const line of lines) {
    const m = /^\('(中华人民共和国[^']+)', '[^']*', '([^']*)', '([^']*)',/.exec(line)
    if (!m) continue
    const book = /^(第[一二三四五六七八九十]+编)\s*([^·]+?)(?:\s*·|$)/.exec(m[2])
    if (!book) continue
    const parts = index.get(m[1]) ?? new Map()
    index.set(m[1], parts)
    const name = book[1] + ' ' + book[2].trim()
    // Conflicting historical/version mappings are not evidence for any one book.
    parts.set(m[3], parts.has(m[3]) && parts.get(m[3]) !== name ? null : name)
  }
  return index
}
function chineseNumber(n) {
  if (!/^\d+$/.test(n)) return n.replaceAll('〇', '零').replaceAll('两', '二')
  const num = Number(n)
  if (!num || num > 9999) return ''
  const digits = '零一二三四五六七八九',
    units = ['', '十', '百', '千']
  let value = '',
    zero = false
  for (let i = 3; i >= 0; i--) {
    const d = Math.floor(num / 10 ** i) % 10
    if (d) {
      if (zero) value += '零'
      if (!(i === 1 && d === 1 && !value)) value += digits[d]
      value += units[i]
      zero = false
    } else if (value) zero = true
  }
  return value
}
// Only an adjacent citation or an explicit book heading links a question to a book.
function citedBooks(text, index) {
  const found = new Map()
  const numerals = '[0-9一二两三四五六七八九十百千零〇]+'
  const historical = /修订前|修改前|修正前|旧法|(?:19\d\d|二十世纪)年.{0,8}刑法/
  if (historical.test(text)) return []
  for (const [code, articles] of index) {
    const short = code.replace(/^中华人民共和国/, '')
    const statute = new RegExp('(?:中华人民共和国|我国|中国)?' + short + '(?:》)?(?:的)?\\s*', 'g')
    for (const citation of text.matchAll(statute)) {
      const before = text.slice(Math.max(0, citation.index - 26), citation.index)
      if (
        /(?:德国|法国|日本|美国|韩国|英国|俄罗斯|瑞士|意大利|荷兰|越南|新加坡|外国|台湾(?:地区)?|澳门(?:地区)?|香港(?:地区)?|[0-9]{4}年)(?:的|《|现行|旧|联邦)*$/.test(
          before
        )
      )
        continue
      // A title ending in a short Chinese code name may belong to a different jurisdiction.
      if (/《[^》]{1,24}$/.test(before) && !/《$/.test(before)) continue
      const after = text.slice(citation.index + citation[0].length, citation.index + citation[0].length + 100)
      const article = new RegExp(
        '^第(' + numerals + '(?:[、，,]' + numerals + ')*)条(?:之(' + numerals + '))?'
      ).exec(after)
      if (article) {
        const numbers = article[1].split(/[、，,]/)
        const trailing = new RegExp('^[、，,及和]\\s*第(' + numerals + ')条(?:之(' + numerals + '))?')
        const listed = numbers.map((n, i) => [n, i === numbers.length - 1 ? article[2] : null])
        let remainder = after.slice(article[0].length)
        let ref
        while ((ref = trailing.exec(remainder))) {
          listed.push([ref[1], ref[2]])
          remainder = remainder.slice(ref[0].length)
        }
        for (const [n, extra] of listed) {
          const key = '第' + chineseNumber(n) + '条' + (extra ? '之' + chineseNumber(extra) : '')
          const book = articles.get(key)
          if (book) found.set(code + book, [code, book])
        }
      } else if (/^(?:第[一二三四五六七八九十]+编|[\u3400-\u9fff]{1,12}编)/.test(after)) {
        for (const book of new Set(articles.values())) {
          if (!book) continue
          const [number, ...name] = book.split(' ')
          if (after.startsWith(number) || after.startsWith(name.join(' ') + '编'))
            found.set(code + book, [code, book])
        }
      }
    }
  }
  return [...found.values()]
}
function implied(code, book) {
  return code === '中华人民共和国民法典'
    ? BOOK_SUBJECT.get(book.split(' ')[1])
    : {
        中华人民共和国刑法: '刑法学',
        中华人民共和国民事诉讼法: '民事诉讼法学',
        中华人民共和国刑事诉讼法: '刑事诉讼法学',
        中华人民共和国生态环境法典: '环境与资源保护法学'
      }[code]
}
function assignment(subject, books) {
  return { discipline: disciplineOf.get(subject), subject, books }
}
function bestBookSubject(books, stem) {
  const scores = new Map()
  for (const [code, book] of books) {
    const subject = implied(code, book)
    if (subject) scores.set(subject, (scores.get(subject) ?? 0) + 1)
  }
  const picked = choose(scores)
  if (picked === '民法学' && scores.size > 1) {
    const specific = choose(votes(stem))
    if (scores.has(specific)) return specific
  }
  return picked
}
export function classify(q, sources, index, linkedCitations = []) {
  let stem = q.stem.split(/(?:选项如下|(?:^|\s)A[.．:：、])/u)[0].trim()
  // LexEval's potential entity/relation types describe every prompt in the task.
  stem = stem
    .replace(/^.{0,300}?实体类型列表.{0,200}?输入文本[：:]/s, '')
    .replace(/^.{0,180}?关系列表有[^。]*。/s, '')
  const correct = correctText(q)
  const citations = [...linkedCitations, ...sources.flatMap((s) => s.statuteNames ?? [])]
  const authoritative = citations.flatMap((text) => citedBooks(text, index))
  const answerBooks = citedBooks(correct, index)
  const stemBooks = citedBooks(stem, index)
  const books = [
    ...new Map([...authoritative, ...stemBooks, ...answerBooks].map((x) => [x.join(':'), x])).values()
  ]
  if (sourceCategories[q.category]) return assignment(sourceCategories[q.category], books)
  if (/劳动(?:人事)?争议(?:调解)?仲裁|劳动仲裁/.test(stem)) return assignment('劳动与社会保障法学', books)
  if (q.category === '涉案金额计算' && correct.includes('犯罪金额')) return assignment('刑法学', books)

  const stemScores = votes(stem)
  const fromStem = choose(stemScores)
  const answerScores = votes(correct.slice(0, 3000))
  const fromAnswer = choose(answerScores)
  // Some consultation labels are broad or noisy. Prefer an explicit issue in the question
  // (e.g. 劳动合同 and 工资 despite a generic 合同纠纷 source label).
  const consultationLabel = q.category === '法律咨询分类' ? consultation[correct.trim()] : null
  let subject =
    bestBookSubject(authoritative, stem) ??
    choose(votes(citations.join('。'))) ??
    choose(votes(sources.flatMap((s) => s.keywords ?? []).join('。')))
  if (!subject && ['法条预测', '法律依据'].includes(q.category))
    subject = bestBookSubject(answerBooks, stem) ?? fromAnswer
  if (!subject && ['法条背诵', '法条内容'].includes(q.category)) subject = bestBookSubject(stemBooks, stem)
  if (!subject) {
    // Bias-detection tests describe hypothetical stereotypes in answer choices.
    const answerEvidence = q.category === '平等与歧视' ? null : fromAnswer
    subject = fromStem ?? consultationLabel ?? answerEvidence ?? bestBookSubject(books, stem)
    if (subject === '跨学科综合') {
      const tieBreaker = consultationLabel ?? answerEvidence
      if (tieBreaker && tieBreaker !== '跨学科综合' && stemScores.has(tieBreaker)) subject = tieBreaker
    }
  }
  if (!subject && /民法典|中华人民共和国民法第/.test(stem)) subject = '民法学'
  if (!subject && q.category === '刑法') subject = '刑法学'
  if (!subject && q.category === '民法') subject = '民法学'
  if (!subject && q.category === '行政法') subject = '行政法学'
  return assignment(subject ?? '待判定', books)
}
