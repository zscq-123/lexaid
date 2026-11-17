from pathlib import Path
import json,re,collections,hashlib,datetime
p=Path('/tmp/lexaid-full-review');raw={r['id']:r for r in map(json.loads,(p/'lecoqa-source.jsonl').read_text().splitlines())};rows=list(map(json.loads,(p/'lecoqa.jsonl').read_text().splitlines()));reading=json.loads((p/'reading-log.json').read_text());findings=json.loads((p/'manual-findings.json').read_text());fm=collections.defaultdict(list)
for f in findings:fm[f['id']].append(f)
header=re.compile(r'(?:^|\n)([^\n：:]{2,150}?)(第[零〇一二三四五六七八九十百千万\d]+条(?:之[一二三四五六七八九十\d]+)?)\s*[：:]')
article=re.compile(r'第[零〇一二三四五六七八九十百千万\d]+条(?:之[一二三四五六七八九十\d]+)?')
missing=collections.defaultdict(lambda:{'referenceOccurrences':0,'questionIds':[],'articles':collections.Counter(),'officialUrlAttempted':None,'status':'OFFICIAL_TEXT_NOT_IN_CORPUS'})
for r in rows:
 q=raw[r['id']];a=q['analysis']or'';hs=[(m.start(),m.end())for m in header.finditer(a)];nested=[{'article':m[0],'offset':m.start()}for m in article.finditer(a)if not any(s<=m.start()<e for s,e in hs)];inline=[{'article':m[0],'offset':m.start()}for m in article.finditer(q['answer']or'')]
 r['citationParsingCoverage']={'analysisHeaderCount':len(r['citationChecks']),'analysisNonHeaderArticleMentions':nested,'answerArticleMentions':inline,'unresolvedMentionPolicy':'These may be nested statutory cross-references, repeated articles, or answer citations; not individually resolved as independent legal authorities. They are not counted as matched.'}
 r['semanticRead']=reading.get(str(r['id']),{'stem':False,'answer':False,'analysis':'NOT_READ','completeRecord':False,'evidenceFile':None});r.pop('modelReading',None)
 r['reasoningVerified']=False;r['reasoningVerificationScope']='No full question has received complete independent application, exception, jurisdiction and temporal-law verification. Confirmed narrow findings are separate.'
 r['manualFindings']=fm[r['id']]
 for f in r['manualFindings']:
  assert f['contentHash']==r['contentHash']
  r['flags'].append({'code':f['code'],'level':f['level'],'domain':f['domain'],'evidence':f['evidence'],'officialUrls':f['officialUrls']})
 r['status']='CONFIRMED_DEFECT'if any(f['level']=='confirmed'for f in r['flags'])else 'RISK_REQUIRES_REVIEW'if any(f['level']=='risk'for f in r['flags'])else 'NOT_CLEARED_NO_DEFINITE_DEFECT_BY_CHECKS'
 r['disposition']='KEEP_ORIGINAL_AND_WITHHOLD_FROM_PRACTICE_PENDING_TEACHING_REVIEW';r['auditDate']='2026-11-12';r['sourceSnapshotFile']='lecoqa-source.jsonl'
 for c in r['citationChecks']:
  if c['status']=='NOT_CHECKED_OFFICIAL_TEXT_UNAVAILABLE':
   x=missing[c['law']];x['referenceOccurrences']+=1;x['questionIds'].append(r['id']);x['articles'][c['article']]+=1;x['officialUrlAttempted']=c.get('officialUrl')
for law,x in missing.items():x['law']=law;x['questionIds']=sorted(set(x['questionIds']));x['questionCount']=len(x['questionIds']);x['articles']=dict(x['articles'])
missingList=sorted(missing.values(),key=lambda x:(-x['referenceOccurrences'],x['law']))
(p/'missing-official-sources.json').write_text(json.dumps(missingList,ensure_ascii=False,indent=2)+'\n')
(p/'lecoqa.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in rows))
s=json.loads((p/'lecoqa-summary.json').read_text());s.update({'auditDate':'2026-11-12','statuses':dict(collections.Counter(r['status']for r in rows)),'reading':{'stemAndAnswerReadCount':sum(r['semanticRead']['stem']and r['semanticRead']['answer']for r in rows),'stemAndAnswerReadIds':[r['id']for r in rows if r['semanticRead']['stem']and r['semanticRead']['answer']],'completeStemAnswerAnalysisReadCount':sum(r['semanticRead']['completeRecord']for r in rows),'completeStemAnswerAnalysisReadIds':[r['id']for r in rows if r['semanticRead']['completeRecord']],'completeLegalReasoningVerifiedCount':0,'note':'Actual model reading as of this snapshot; parallel semantic-review shards will supersede the reading coverage when completed. Text matching never establishes legal correctness.'},'confirmedFindingQuestionsByDomain':{d:sorted({f['id']for f in findings if f['domain']==d and f['level']=='confirmed'})for d in sorted({f['domain']for f in findings})},'confirmedFindingCountsByDomain':{d:{'findings':sum(f['domain']==d and f['level']=='confirmed'for f in findings),'questions':len({f['id']for f in findings if f['domain']==d and f['level']=='confirmed'})}for d in sorted({f['domain']for f in findings})},'structuralDefects':{'unclassifiedQuestions':44,'emptyAnswerQuestions':1,'emptyAnswerIds':[10025129],'note':'Classification gaps are not legal error findings.'},'parsingCoverage':{'totalAnalysisRecords':len(rows),'analysisRecordsWithParsedHeader':sum(bool(r['citationChecks'])for r in rows),'parsedHeaderReferences':sum(len(r['citationChecks'])for r in rows),'analysisRecordsWithNonHeaderArticleMentions':sum(bool(r['citationParsingCoverage']['analysisNonHeaderArticleMentions'])for r in rows),'analysisNonHeaderArticleMentions':sum(len(r['citationParsingCoverage']['analysisNonHeaderArticleMentions'])for r in rows),'answerRecordsWithArticleMentions':sum(bool(r['citationParsingCoverage']['answerArticleMentions'])for r in rows),'answerArticleMentions':sum(len(r['citationParsingCoverage']['answerArticleMentions'])for r in rows),'note':'Nonheader and inline citations remain unverified as standalone authorities; counts include nested legislative references and repeats.'},'missingOfficialSources':{'instruments':len(missingList),'referenceOccurrences':sum(x['referenceOccurrences']for x in missingList),'questionIds':sorted({i for x in missingList for i in x['questionIds']}),'file':'missing-official-sources.json'},'sourceSnapshotSha256':hashlib.sha256((p/'lecoqa-source.jsonl').read_bytes()).hexdigest(),'notApproval':True,'humanDualApprovalCount':0,'allSourceRecordsRetained':True})
(p/'lecoqa-summary.json').write_text(json.dumps(s,ensure_ascii=False,indent=2)+'\n')
manifest=[]
for f in sorted((p/'official').glob('*.json')):
 c=json.loads(f.read_text());manifest.append({'law':c['law'],'url':c['url'],'promulgated':c.get('promulgated'),'effective':c.get('effective'),'retrievedAt':c.get('retrievedAt'),'retrievalTimePrecision':'date (original collection did not capture exact timestamp)','file':str(f.relative_to(p)),'fileSha256':hashlib.sha256(f.read_bytes()).hexdigest(),'articleMapSha256':hashlib.sha256(json.dumps(c.get('articles',{}),ensure_ascii=False,sort_keys=True).encode()).hexdigest(),'textSha256':c.get('textSha256')or c.get('textSHA256'),'articleCount':len(c.get('articles',{})),'status':c.get('status'),'coverage':c.get('coverage'),'rawSegments':c.get('rawSegments'),'futureVersion':c.get('effective','')>'2026-11-12','scope':'Official source quotation corpus; does not prove every later amendment, interpretation, transitional provision or legal application has been exhausted.'})
(p/'official-corpus-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
evidence=[]
for f in sorted((p/'manual-evidence').glob('*.txt')):evidence.append({'file':str(f.relative_to(p)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'retrievedAt':'2026-11-12','urls':sorted(set(re.findall(r'https?://[^\s)\]"<>]+',f.read_text()))),'note':'May contain search excerpts or access failure; inspect file, not a complete corpus by default.'})
(p/'manual-evidence-manifest.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
report=f'''# LeCoQA 全量流程审计与内容核验范围（2026-11-12）

本报告处理全部 **1543** 条 LeCoQA 原始题，每题有 contentHash、结构检查、解析引用检查、教学文本信号、实际阅读范围和处置。**不是1543题已经逐题法律实质审核通过，也不是双人专家审签。** 全部保留原始内容，保持外部来源题待教学复核。

## 当前证据与边界

- 1543/1543 条已完整导出题干、选项、答案、解析、来源 metadata/hash，见 `lecoqa-source.jsonl`。
- 全部解析都识别到至少一条形如“法律名第X条：”的标题，共2717条。另有219题解析中的449处非标题条号、349题答案中的678处条号没有独立做法名/版本解析；它们可能是法条内部交叉引用，不能当作已核验。
- 官方全文/摘录规范化匹配2275条；19处文本或条号版本差异涉及15题；423条引用缺官方全文语料。缺口按每部法律、条号、题ID列于 `missing-official-sources.json`。
- 已读取题干答案 {s['reading']['stemAndAnswerReadCount']} 题，题干答案解析三字段全文 {s['reading']['completeStemAnswerAnalysisReadCount']} 题；准确ID在summary。其他题明确未实读。并行全文阅读分片另交付，完成后合并，不提前计数。
- 完整法律推理全部核验通过 **0题**。已核实的是 `manual-findings.json` 中列明的窄点问题，不能把它升级为其余部分正确。
- 44题学科待判定、1题答案为空（10025129），与法律错误单独统计。各问题集合存在交叉，不应用简单相加计算错误率。

## 已确认的主要问题

企业名称不得转让（10024165）、进城落户丧失承包地（10024085/10024089）、自然人商标申请无资格限制（10024548）、双方有过错就平均分担（10024323）、旧借款利率过渡规则（10025006/10025274）、扒窃遗漏公共场所条件（10025143）、宅基地争议否定后续行政起诉（10024627）、技术合同违反原则即当然无效（10024758）、学位程序无法律明文（10024973）、公司担保例外过宽（10025574）等。每条均附原命题、结论、最小修订建议、官方URL；保留适法时间与范围限制。

公司法、治安管理处罚法、民诉法存在旧条号/旧文；19处具体对照见 `citation-differences.json`。这不必然推翻旧咨询发生时的结论，但无日期的现行练习题不得混用。10025395等另有依据不支持问题的缺陷，不能以换条号代替重写论证。

贷款诈骗10025448仅列风险：2万元和必然构成罪的说法未区分行为时间、非法占有目的、立案追诉与定罪量刑；2022最高检/公安部标准45的5万元不得机械替换为所有历史案件统一定罪线。噪声题10025073还须考虑2026-08-15生效生态环境法典，不能止于旧法改名。

## 可复跑方法

保留整个目录，依次运行 `python3 audit-lecoqa.py`、`python3 build-manual-review.py`、`python3 finalize-lecoqa.py`。脚本均以目录内冻结原始快照和官方语料为输入，只写本审计目录。若目录移动，可将各脚本的 `p` 路径改为脚本所在目录。人工窄点记录由build脚本显式列出，并用contentHash绑定原题；不是脚本自动“理解”出来的结论。

`fetch-official.py`记录首次官方抓取；403/超时后的web工具可见文本保存在 `official/web-*.txt`，`merge-web-corpus.py`按官方页面连续行号重建条文，全部已纳入的文种连续段无缺行。`official-corpus-manifest.json`给出URL、版本、生效日期、抓取日期及文件/条文映射SHA-256。抓取时间只记录到日期，不伪造秒级时间。商标法2026修订2027-01-01生效，作为未来版本单列。

规范化比较去标点与条文标题，检查全文相等或不少于15字符的连续摘录包含；这只能证明摘录对应，不证明引文必要、充分、适用正确或教学充分。教学“因此/误区”等仅文本信号；未把没有这些词直接认定为坏题。

与旧规则审计的关系：旧1527可练快照是独立文件 `/tmp/lexaid-question-audit/manifest-before.jsonl`；本审计仅LeCoQA1543题，未把旧门禁统计与新来源门禁混算。
'''
(p/'lecoqa-report.md').write_text(report)
print(json.dumps({'total':s['total'],'reading':{k:v for k,v in s['reading'].items()if not k.endswith('Ids')},'domains':s['confirmedFindingCountsByDomain'],'missingReferences':s['missingOfficialSources']['referenceOccurrences'],'corpus':len(manifest)},ensure_ascii=False,indent=2))
