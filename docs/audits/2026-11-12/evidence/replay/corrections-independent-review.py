import json,pathlib,re,hashlib,collections,subprocess,datetime
B=pathlib.Path('/tmp/lexaid-full-review'); R=pathlib.Path('/Users/yangyongze/Desktop/lexaid/lexaid')
corrections=[json.loads(x) for x in (B/'lexeval-corrections.jsonl').read_text().splitlines()]
source_rows=[json.loads(x) for x in (B/'lexeval-source.jsonl').read_text().splitlines()]
source_by_id=collections.defaultdict(list)
for s in source_rows:source_by_id[s['id']].append(s)
checks={r['sourceItemId']:r for r in map(json.loads,(B/'lexeval-source-verification.jsonl').read_text().splitlines())}
prov=collections.defaultdict(list)
for p in map(json.loads,(B/'lexeval-correction-provenance.jsonl').read_text().splitlines()):prov[p['questionId']].append(p)
ids=[int(x['id']) for x in corrections]
sql='SELECT question_id,COUNT(*),GROUP_CONCAT(DISTINCT dataset ORDER BY dataset) FROM question_source WHERE question_id IN ('+','.join(map(str,ids))+') GROUP BY question_id;'
p=subprocess.run(['docker','exec','-i','lexaid-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],input=sql,text=True,capture_output=True,check=True)
db_sources={int(x[0]):{'count':int(x[1]),'datasets':x[2].split(',')} for x in (line.split('\t') for line in p.stdout.splitlines())}
upstream={}
def clean(s):return re.sub(r'[ \t]+\n','\n',str(s).replace('\r\n','\n').replace('\r','\n')).strip()
results=[]
for c in corrections:
 qid=c['id'];before=c['before'];after=c['after'];sources=source_by_id[qid];ps=prov[qid];issues=[];verified=[]
 def check(name,ok,evidence=None):
  if not ok:issues.append({'code':name,'evidence':evidence})
  return bool(ok)
 baseline=all(all(before.get(k)==s.get(k) for k in ['type','stem','options','answer','analysis','category']) for s in sources)
 check('BASELINE_DIFFERS_FROM_SOURCE_EXPORT',baseline)
 check('UNEXPECTED_CHANGED_FIELDS',set(c['changedFields'])=={'type','stem','options','answer'})
 check('ANALYSIS_OR_CATEGORY_CHANGED',before['analysis']==after['analysis'] and before['category']==after['category'])
 check('MISSING_OR_CONFLICTING_PROVENANCE',db_sources[qid]['count']==len(ps)==1 and db_sources[qid]['datasets']==['LexEval'],db_sources[qid])
 for pp in ps:
  task,index=pp['sourceItemId'].split(':');idx=int(index)
  if task not in upstream:upstream[task]=[json.loads(x) for x in (B/'lexeval-upstream'/f'{task}.json').read_text().splitlines() if x.strip()]
  u=upstream[task][idx];text=clean(u['input']);answer=clean(u['answer']);instruction=clean(u['instruction'])
  check('BEFORE_INPUT_MISMATCH',before['stem']==text)
  check('BEFORE_ANSWER_MISMATCH',before['answer']==answer)
  check('INSTRUCTION_PROVENANCE_MISMATCH',instruction==pp['metadata']['instruction']==before['analysis'])
  # Independent parser: only line-start labels; do not call the implementation under review.
  markers=list(re.finditer(r'(?m)^([A-H])[:：][ \t]*',text))
  check('OPTION_MARKERS_NOT_ORDERED', [m.group(1) for m in markers]==['A','B','C','D'])
  expected_stem=text[:markers[0].start()].strip()
  options=[{'key':m.group(1),'text':text[m.end():markers[i+1].start() if i+1<len(markers) else len(text)].strip()} for i,m in enumerate(markers)]
  expected_answer=answer.removeprefix('选项').replace(' ','')
  check('SOURCE_ANSWER_NOT_SIMPLE_LABEL',bool(re.fullmatch('[A-H]+',expected_answer)))
  keys=list(expected_answer)
  check('ANSWER_KEYS_INVALID',len(keys)==len(set(keys)) and set(keys)<=set(o['key'] for o in options))
  # A trailing colon introducing the now-separated choices is display punctuation, not removed content.
  check('STEM_INFORMATION_LOSS',after['stem']==re.sub(r'[:：]$','',expected_stem).strip(),{'expected':expected_stem,'actual':after['stem']})
  check('OPTIONS_INFORMATION_LOSS',json.loads(after['options'])==options)
  check('ANSWER_KEYS_CHANGED',json.loads(after['answer'])==keys)
  expected_type='SINGLE' if len(keys)==1 else 'MULTIPLE'
  check('TYPE_DISAGREES_WITH_SOURCE_LABELS',after['type']==expected_type)
  v=checks[pp['sourceItemId']]
  check('SOURCE_VERIFICATION_OR_URL_MISMATCH',v['metadataMatchesUpstream'] and c['upstreamSource']==v['upstreamUrl'] and c['upstreamRecordHash']==v['upstreamRecordHash'])
  verified.append({'sourceItemId':pp['sourceItemId'],'upstreamUrl':v['upstreamUrl'],'upstreamRecordHash':v['upstreamRecordHash'],'independentCanonicalRecordSha256':hashlib.sha256(json.dumps(u,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest(),'fullInputCompared':True,'fullOptionsCompared':True,'originalAnswerCompared':True})
 results.append({'id':qid,'expectedContentHash':c['expectedContentHash'],'structuralConclusion':'PASS_SOURCE_PRESERVING_STRUCTURE_ONLY' if not issues else 'FAIL','issues':issues,'formattingNormalization':['移除引出选项的题干末尾冒号，不改变字词和选项'] if qid==10023745 else [],'beforeType':before['type'],'afterType':after['type'],'answerKeys':json.loads(after['answer']),'optionCount':len(json.loads(after['options'])),'sources':verified,'manualFullContentRead':True,'manualReadingScope':'完整阅读题干、全部选项、源答案及转换结果；完整before与固定上游由独立程序逐字段核对。','humanReview':False,'legalReviewApproved':False})
report={'schemaVersion':1,'reviewerKind':'INDEPENDENT_AI_AGENT','reviewedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'87条结构修复的独立来源与转换审核，不是人工法律准入审核','inputFile':'/tmp/lexaid-full-review/lexeval-corrections.jsonl','inputSha256':hashlib.sha256((B/'lexeval-corrections.jsonl').read_bytes()).hexdigest(),'importerSha256':hashlib.sha256((R/'scripts/build-public-question-dataset.mjs').read_bytes()).hexdigest(),'counts':{'reviewed':len(results),'fullSourceCompared':len(results),'manualFullContentRead':len(results),'structurePass':sum(r['structuralConclusion'].startswith('PASS') for r in results),'structureFail':sum(bool(r['issues']) for r in results),'humanReview':0,'legalApproved':0},'upstreamCommit':'044c695f62894deef41ad9a30797e1da0404945c','records':results,'parserBoundaryReview':{'status':'REGRESSION_FOUND_REPORTED_TO_PARENT','finding':'整字段答案正则当前不支持LawBench原生[正确答案]A<eoa>包装；会将1-2及2-8共1000条原选择题回退SHORT。','affectedLawBenchTasks':['1-2','2-8'],'affectedSourceItems':1000,'action':'保持整字段校验，但显式解析成对的官方答案包装，补回归；本审核未修改代码。'},'substantiveContentFlags':[{'id':10021709,'status':'SOURCE_ANSWER_REQUIRES_SUBSTANTIVE_REVIEW','sourceAnswer':'选项D','finding':'题目问符合劳动法的行为，源答案选仅因发现前科而解除的D，但C被依法追究刑事责任本身有劳动法第二十五条依据；A/B对应第二十六条并受通知和其他条件约束。结构修复应忠实保留源数据而继续隔离，不能标为法律审核通过。','officialSource':'https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2023/art_d9aa750028b14b99a776cb93726a360d.html','articles':['第二十五条','第二十六条'],'replacementAnswerApproved':False}],'limits':['人工审核人数为0；此为AI独立复核。','87条均实际阅读，法律适用并未逐条核验；结构与来源一致不能代替答案正确。','当前来源中的表述缺项、歧义或法律错误保持隔离，本任务没有覆盖修改源答案。']}
if (B/'corrections-parser-boundaries.json').exists():
 report['parserBoundaryReview']=json.loads((B/'corrections-parser-boundaries.json').read_text())
(B/'corrections-independent-review.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report['counts'],ensure_ascii=False));print(json.dumps([x for x in results if x['issues']],ensure_ascii=False))
