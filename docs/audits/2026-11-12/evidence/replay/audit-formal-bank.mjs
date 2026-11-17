import fs from 'node:fs'
import {createHash} from 'node:crypto'
import {simulation,validate} from '/Users/yangyongze/Desktop/lexaid/lexaid/scripts/build-formal-exam-bank.mjs'
const base='/tmp/lexaid-full-review/', repo='/Users/yangyongze/Desktop/lexaid/lexaid/'
const read=(file)=>JSON.parse(fs.readFileSync(file,'utf8'))
const lines=(file)=>fs.readFileSync(base+file,'utf8').trim().split('\n').map(JSON.parse)
const stable=v=>v&&typeof v==='object'?Array.isArray(v)?v.map(stable):Object.fromEntries(Object.keys(v).sort().map(k=>[k,stable(v[k])])):v
const same=(a,b)=>JSON.stringify(stable(a))===JSON.stringify(stable(b))
const hash=v=>createHash('sha256').update(JSON.stringify(stable(v))).digest('hex')
const sources=new Map(), manualSources=new Map(), after=new Map(lines('formal-source-after.jsonl').map(r=>[r.id,r]))
for(const task of ['3_4','6_2','6_3'])sources.set(task,fs.readFileSync(base+'lexeval-upstream/'+task+'.json','utf8').trim().split('\n').map(JSON.parse))
for(const name of ['original-objective','original-subjective','linked-real'])for(const r of read(repo+'datasets/formal-exams/'+name+'.json'))manualSources.set(r.id,r)
const corrections=new Map(read(base+'formal-corrections.json').map(r=>[r.id,r]))
const historical=new Map(lines('formal-real-source-check.jsonl').map(r=>[r.id,r]))
const manualReasons={
8000001:'按两次交易分别判断职务代理、善意信赖和事后履行追认，题面已给撤权通知时间。',
8000002:'区分委托占有物与遗失物；返还、费用支付及追偿条件分别对应两件物。',
8000003:'有效定金20、超额给付10，返还50及另补损失6，与扩大损失4分开计算。',
8000004:'原题误把保证人逃匿写成先诉抗辩例外，现明确排除法定例外。',
8000005:'危险是否结束决定两阶段防卫前提，不能用最终伤情代替事发时判断。',
8000006:'言词与实物非法证据的排除路径区分清楚；修正“违法定程序”错字。',
8000007:'先确定时间效力再处理同一行为罚款竞合，初犯不自动免罚。',
8000008:'决定前告知听证、复核新证据和不得因申辩加重均有评分点；补明五个工作日。',
8000009:'诉前保全覆盖管辖、紧急性、担保、限额、48小时和30日启动诉讼条件。',
8000010:'区分优先购买的同等条件、股东名册权利行使与两类出资转让责任。',
8000011:'第三人直接侵权与组织者补充责任分开，受害人过错须有关联；变式无第三人。',
8000012:'有效遗嘱冲突之前先审查形式要求及必留份，逐页签名要求有题面对应。',
8100001:'定金16双倍32加超额付款8合计40，选B与题设不另求损害赔偿一致。',
8100002:'第一块为委托占有物，丙善意取得后有权赠与；遗失物两年规则无从套用。',
8100003:'未届期转让后受让人到期欠缴，转让人在欠缴范围内负补充责任。',
8100004:'采取诉前保全后30日未启动诉讼仲裁，事后申请不使已届满期限自动延长。',
8100005:'先选每个规范适用版本再按高额规定罚一次，初犯尚需轻微后果等条件。',
8100006:'不能排除刑讯须排除争议供述，其他独立证据可另行审查。',
8100007:'第一笔内部限制不得对抗善意相对人，第二笔明知履行可构成追认。',
8100008:'方式不明为一般保证；只催保证人不保全一般保证请求，第七月起诉已超六个月。',
8100009:'严重侵害进行中与危险消除后的报复分阶段判断。',
8100010:'依法主持听证并复核证据；不得因申辩加重或转嫁听证组织费。',
8100011:'紧急性担保、范围和48小时裁定要求适用于题设诉前保全。',
8100012:'第三人侵害及未尽安全保障义务导致相应补充责任，承担后可追偿。',
8100013:'遗失物可在两年内追回，正规经营者购入者的费用须支付后再追偿。',
8100014:'违法实物证据可补正解释，与暴力威胁证言法定排除不同。',
8100015:'已逾期出资的转让且受让人知情，在出资不足范围承担连带责任。',
8100016:'定金与违约金择一，定金不能填补的可赔损失可另请求，扩大损失除外。',
8100017:'未依法告知并听取意见不能径作决定，沉默不等于放弃未被告知的权利。',
8100018:'打印遗嘱须两名以上见证人，形式缺陷不当然使原自书遗嘱失效，另审必留份。'}
let rows=[]
for(const r of lines('formal-source.jsonl')){
 const current=after.get(r.id), correction=corrections.get(r.id)
 if(!same(current,correction?.after??r))throw Error('Unexpected changed content '+r.id)
 const logical={...r,kind:r.kind,answer:r.kind==='SHORT'?r.answer:JSON.parse(r.answer).join('')}
 validate(logical)
 let sourceMatch,method,decision,action,issues=[]
 const matched=r.sourceUrl.match(/\/data\/(\d_\d)\.json#L(\d+)$/)
 if(matched){
  const task=matched[1],line=Number(matched[2]),u=sources.get(task)[line-1],converted=simulation(task,line,u)
  sourceMatch=!!converted&&['stem','kind','subject','difficulty','sourceLabel','sourceUrl'].every(k=>r[k]===converted[k])&&same(r.options,converted.options)&&logical.answer===converted.answer
  method='全字段结构及固定上游来源对照；未逐题法律推理核验'
  decision='SOURCE_CONSISTENT_REQUIRES_LEGAL_REVIEW';action='当前模拟卷可抽取，但325道来源题没有教学解析；应补逐项理由、适用日期与法律依据。'
  if(!r.explanation)issues.push('NO_TEACHING_EXPLANATION')
 }else{
  const s=manualSources.get(r.id),compare=correction?current:r
  sourceMatch=['stem','subject','difficulty','sourceLabel','sourceUrl'].every(k=>compare[k]===s[k])&&compare.answer===(r.kind==='SHORT'?s.answer:JSON.stringify([...s.answer]))&&same(compare.options,s.options?.length?s.options:null)&&compare.explanation===(s.explanation??null)&&compare.readingUrl===(s.readingUrl??null)
  if(r.sourceType==='REAL'){
   const checked=historical.get(r.id)
   if(checked&&(!checked.answerMatches||!Object.values(checked.optionsMatch).every(Boolean)))throw Error('Historical mismatch '+r.id)
   method=checked?'原卷四个选项全文与答案页标记逐字段比对；未按现行法重判':'已阅读本地评分要点；原卷整体法律推理未重新核验'
   decision='HISTORICAL_REFERENCE_CONSISTENCY_ONLY';action='保持2017年作答语境；原卷年度参考答案一致不等于现行法律结论。'
  }else{
   method='AI 完整阅读题干、所有选项（如有）、参考答案和解析；重点问题对照官方法条'
   decision=correction?'FOCUSED_CORRECTION_APPLIED':'READ_NO_SPECIFIC_DEFECT_IDENTIFIED'
   action=manualReasons[r.id];if(!action)throw Error('Missing manual note '+r.id)
   if(correction)issues.push('ORIGINAL_WORDING_CORRECTED')
  }
 }
 if(!sourceMatch)throw Error('Source mismatch '+r.id)
 rows.push({id:r.id,dataset:'formal',stem:r.stem,contentHash:hash(r),currentContentHash:hash(current),contentHashAlgorithm:'sha256 of recursively sorted JSON, UTF-8',status:decision,issues,action,reviewMethod:method,fieldsRead:manualReasons[r.id]?['stem','options','answer','explanation']:[],sourceFieldMatch:sourceMatch,sourceUrls:[r.sourceUrl,r.readingUrl].filter(Boolean),legalReasoningVerified:false,humanDualApproval:false,appliedRevision:correction??null})
}
const summary={total:rows.length,sourceFieldMatches:rows.filter(r=>r.sourceFieldMatch).length,lexeval:325,originalCases:30,historicalObjective:298,historicalSubjective:5,originalFullContentRead:30,historicalLocalRubricRead:5,originalCorrectionsApplied:3,historicalObjectiveOptionAndKeyMatches:298,legalAnswersIndividuallyFullyVerified:0,scope:'Source/structural audit of all 658. Full semantic reading of 30 original cases plus local 5 historical rubrics; no claim that 658 legal arguments are all verified.'}
fs.writeFileSync(base+'formal.jsonl',rows.map(r=>JSON.stringify(r)).join('\n')+'\n');fs.writeFileSync(base+'formal-summary.json',JSON.stringify(summary,null,2)+'\n');console.log(JSON.stringify(summary,null,2))
