import json,re,collections,hashlib,pathlib,subprocess,datetime,os
B=pathlib.Path(os.environ.get('LEXAID_AUDIT_DIR','/tmp/lexaid-full-review'))
rows=[json.loads(x) for x in (B/'lawbench-export.jsonl').read_text().splitlines()]
checks={x['source_item_id']:x for x in map(json.loads,(B/'lawbench-source-check.jsonl').read_text().splitlines())}
provenance=json.loads((B/'lawbench-source'/'provenance.json').read_text())
commit=provenance['commit']
groups=collections.defaultdict(list)
for r in rows:groups[r['id']].append(r)
questions={qid:rr[0] for qid,rr in groups.items()}
def canon(s):return re.sub(r'\s+','',s or '')
def sha(x):return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
sql="""SELECT q.id,SHA2(CAST(JSON_ARRAY(q.type,q.stem,q.options,q.answer,q.analysis,q.category,COALESCE(s.discipline,CASE TRIM(q.category) WHEN '刑法' THEN '刑法' WHEN '民法' THEN '民商法' WHEN '行政法' THEN '宪法与行政法' ELSE '综合与待判定' END),COALESCE(s.subject,CASE TRIM(q.category) WHEN '刑法' THEN '刑法学' WHEN '民法' THEN '民法学' WHEN '行政法' THEN '行政法学' ELSE '待判定' END)) AS CHAR),256) FROM question q LEFT JOIN question_legal_subject s ON s.question_id=q.id WHERE EXISTS(SELECT 1 FROM question_source qs WHERE qs.question_id=q.id AND qs.dataset='LawBench') ORDER BY q.id;"""
p=subprocess.run(['docker','exec','-i','lexaid-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],input=sql,text=True,capture_output=True,check=True)
hashes={int(a):b for a,b in (line.split('\t') for line in p.stdout.splitlines())}
# Official benchmark objective, not a legal-correctness judgment.
tasks={
'1-1':('TEACHING_CANDIDATE_REQUIRES_REWRITE','法条原文记忆','核实具体法律版本与原文，补充条文含义、适用条件及出处后再作为法条学习题。'),
'1-2':('TEACHING_CANDIDATE_REQUIRES_REWRITE','法律知识单选','保留题干选项和来源答案；逐项核对现行或题设时点法律，补齐选项论证与依据。'),
'2-1':('REJECT_CURRENT_PRACTICE_USE','文书字词校对','归入文书校对评测集；保留原句、改后句、编辑差异，使用校对指标，禁止当作法律论述题评分。'),
'2-2':('REJECT_CURRENT_PRACTICE_USE','争议焦点标签识别','归入标签分类评测；保留任务说明与类别集合，用标签指标评价，勿把一个标签当作法律论证。'),
'2-3':('REJECT_CURRENT_PRACTICE_USE','婚姻纠纷多标签识别','保留多标签本体及任务说明，放入评测集；若改教学题必须重新编写事实、问题与适用规则。'),
'2-4':('REJECT_CURRENT_PRACTICE_USE','咨询主题分类','归入咨询路由评测；明确回答类别而非咨询实体答案，禁止以类别标签回答用户法律问题。'),
'2-5':('REJECT_CURRENT_PRACTICE_USE','法律文本阅读抽取','可归入独立阅读理解练习或评测；保留文段和问题，使用抽取答案指标，不能宣称检验法律适用。'),
'2-6':('REJECT_CURRENT_PRACTICE_USE','命名实体抽取','归入实体识别评测，保留类型集合与允许空结果的语义；不要为无实体负例伪造参考答案。'),
'2-7':('REJECT_CURRENT_PRACTICE_USE','新闻摘要生成','归入摘要评测或文书阅读模块，允许多种忠实摘要；不得用唯一法律答案或法律论述评分。'),
'2-8':('REJECT_CURRENT_PRACTICE_USE','诉辩论点匹配','保留明确的诉辩观点匹配任务与五个候选项，按匹配准确率评测；不是判断该观点法律上正确。'),
'2-9':('REJECT_CURRENT_PRACTICE_USE','事件类别检测','归入事件检测评测；保留事件类别集合，不与触发词抽取题混作同一问题。'),
'2-10':('REJECT_CURRENT_PRACTICE_USE','事件触发词抽取','归入触发词抽取评测；保留事件集合，答案是原句词片段而非法律结论。'),
'3-1':('REJECT_CURRENT_PRACTICE_USE','历史事实法条预测','保留为裁判标签预测评测；不能把历史引用条号当作当前唯一法律适用答案。教学改写需独立核对版本与要件。'),
'3-2':('TEACHING_CANDIDATE_REQUIRES_REWRITE','场景法律依据生成','核对每条引用的法律名称、条号、版本与场景要件，补充适用推理及反例，再决定是否准入。'),
'3-3':('REJECT_CURRENT_PRACTICE_USE','历史事实罪名预测','保留为罪名标签预测评测；教学题应补齐构成要件和证据，不用源标签代替法律论证。'),
'3-4':('REJECT_CURRENT_PRACTICE_USE','刑期月数回归（未附法条正文）','保留为数值预测评测；不得把历史刑期月数当作事实唯一推出的规范性答案，也不能按简答题十分制判分。'),
'3-5':('REJECT_CURRENT_PRACTICE_USE','刑期月数回归（附法条正文）','保留为数值预测评测，并保留所附条文版本；勿以单一历史月数代表规范性量刑结论。'),
'3-6':('TEACHING_CANDIDATE_REQUIRES_REWRITE','案例分析单选','核实题设事实、源答案及选项论证，明确历史/现行规则，补充法律依据与易错点。'),
'3-7':('TEACHING_CANDIDATE_REQUIRES_REWRITE','犯罪金额数值计算','列出计入及排除的金额项目和算式，区分犯罪金额、分赃、退赔；已给出总额的题需改为抽取题或重写后再用。'),
'3-8':('TEACHING_CANDIDATE_REQUIRES_REWRITE','法律咨询回答生成','核实问题的事实前提及每项回答/法条支持关系，补足时点、地区和例外；不能把网络咨询原答案当作核验结论。')}
missing_contract={'2-1','2-2','2-3','2-4','2-6','2-7','2-8','2-9','2-10','3-1','3-3','3-4','3-5','3-7'}
raw_stem=collections.defaultdict(list);identity=collections.defaultdict(list)
for qid,r in questions.items():
 raw_stem[canon(r['stem'])].append(qid)
 identity[(r['metadata']['taskCode'],canon(r['stem']),canon(r['options']))].append(qid)
manual_ids={next(r['id'] for r in rows if r['metadata']['taskCode']==t) for t in tasks}
manual_ids|={r['id'] for r in rows if not (r['answer'] or '').strip()}
manual_ids|={10004526,10004984,10008973,10008975}
manual_notes={10008969:'完整阅读后复核纯算术：两次被盗本金1500+7000=8500；不能再把分赃金额重复计入。未核定法律定性。',10008973:'完整阅读后复核纯算术：1424+1542+422+130+422=3940；正文开头已直接给出3940元总额。未核定法律定性。',10008975:'完整阅读发现正文已给出合计48704元，另有退赔6860元；源答案没有扣退赔，但缺少教学推理。不据此核定法律正确性。',10009468:'题设明确提到行为在醉驾入刑前，源回答转向醉驾犯罪处罚及升学影响，未解释该时间前提及航空招生依据；需独立实体法与招生规则核验，未作法律正确性结论。'}
result=[];issues_count=collections.Counter();decisions=collections.Counter();task_stats=collections.defaultdict(collections.Counter)
for qid,r in sorted(questions.items()):
 task=r['metadata']['taskCode'];decision,objective,action=tasks[task]
 issues=[];observations=[];sources=[]
 for src in groups[qid]:
  check=checks[src['source_item_id']]
  sources.append({'source_item_id':src['source_item_id'],'source_task':src['source_task'],'task_code':src['metadata']['taskCode'],'source_url':f'https://github.com/open-compass/LawBench/blob/{commit}/data/zero_shot/{task}.json','source_index':int(src['source_item_id'].split(':')[1]),'source_sha256':check['source_hash'],'official_comparison':{k:v for k,v in check.items() if k.endswith('_match')}})
 source_verified=all(all(s['official_comparison'].values()) for s in sources)
 if not source_verified:issues.append({'code':'SOURCE_TRANSFORM_MISMATCH','evidence':sources,'action':'本地内容或导入转换与固定上游不一致，定位差异后重新审计，不沿用旧来源核对状态。'})
 if r['analysis']==r['metadata']['instruction']:
  issues.append({'code':'INSTRUCTION_STORED_AS_EXPLANATION','evidence':{'analysis':r['analysis'],'equals_source_instruction':True},'action':'从教学解析中移除指令；原始指令留在评测任务/来源中。教学候选必须另写依据与推理，不把去掉指令当作完成审核。'})
 if decision=='REJECT_CURRENT_PRACTICE_USE':
  issues.append({'code':'BENCHMARK_OBJECTIVE_NOT_CURRENT_PRACTICE','evidence':{'official_objective':objective,'stored_type':r['type'],'answer_excerpt':r['answer'][:280]},'action':action})
 if task in missing_contract:
  issues.append({'code':'TASK_CONTRACT_OMITTED_FROM_DISPLAYED_PROMPT','evidence':{'instruction_excerpt':r['metadata']['instruction'][:300],'stem_excerpt':r['stem'][:300]},'action':'当前题干只保留输入材料，任务目标留在不可见来源/事后解析；若保留为独立评测，必须显示明确任务说明和标签/输出约束。'})
 if not (r['stem'] or '').strip():issues.append({'code':'EMPTY_STEM','evidence':{'stem':r['stem']},'action':'停止当前练习用途并对照来源修复。'})
 if not (r['answer'] or '').strip():
  issues.append({'code':'EMPTY_NER_RESULT_NOT_TEACHING_ANSWER' if task=='2-6' else 'EMPTY_REFERENCE','evidence':{'source_answer':r['metadata']['originalAnswer'],'stem_excerpt':r['stem']},'action':'该实体抽取空结果与官方原始记录一致；保留负例语义，勿补写虚构实体。当前法律简答题不可用。' if task=='2-6' else '核对来源，缺参考答案不可作为可评分练习。'})
 if r['subject']=='待判定':issues.append({'code':'LEGAL_SUBJECT_UNCLASSIFIED','evidence':{'category':r['category'],'discipline':r['discipline'],'subject':r['subject']},'action':'不得用任务名代替法律科目；教学候选结合完整题干与选项重新分类。'})
 choice={'applicable':r['type'] in ['SINGLE','MULTIPLE']}
 if choice['applicable']:
  try:
   opts=json.loads(r['options']);answer=json.loads(r['answer']);keys=[x['key'] for x in opts]
   ok=isinstance(opts,list) and len(opts)>=2 and len(keys)==len(set(keys)) and all(re.fullmatch('[A-H]',x) for x in keys) and all(x.get('text','').strip() for x in opts) and isinstance(answer,list) and len(answer)>=1 and len(answer)==len(set(answer)) and set(answer)<=set(keys) and (r['type']!='SINGLE' or len(answer)==1)
   choice.update({'valid_structure':bool(ok),'option_count':len(opts),'keys':keys,'answer_keys':answer})
  except Exception as e:ok=False;choice['valid_structure']=False;choice['error']=str(e)
  if not ok:issues.append({'code':'INVALID_CHOICE_STRUCTURE','evidence':choice,'action':'核对原始任务后修复选项/参考键，保留隔离。'})
 explicit=re.search(r'(?:正确答案\s*[:：]?|\[正确答案\]|答案\s*[:：])\s*[A-H]',r['stem'])
 leak={'explicit_answer_marker_found':bool(explicit),'scope':'检测明确答案字母标记；没有命中不代表完成语义泄漏审查。'}
 if explicit:issues.append({'code':'EXPLICIT_ANSWER_MARKER_IN_STEM','evidence':{'match':explicit.group(0)},'action':'去除答案标记并回查原始来源。'})
 if task=='3-7':
  n=re.search(r'(\d+(?:\.\d+)?)',r['answer'])
  for m in re.finditer(r'(?:合计|总计|共计|共|累计|总价值)[^。；;\n]{0,18}?(\d+(?:\.\d+)?)元',r['stem']):
   if n and float(m.group(1))==float(n.group(1)):
    issues.append({'code':'REFERENCE_TOTAL_ALREADY_STATED','evidence':{'matched_text':m.group(0),'stem_offset':m.start(),'reference':r['answer']},'action':'这道原始评测输入已出现参考总额；用于计算训练时需重写或改标抽取练习，不能以命中总额证明计算能力。'})
    break
 dup=raw_stem[canon(r['stem'])];ident=identity[(task,canon(r['stem']),canon(r['options']))]
 duplicates={'same_stem_other_ids':[i for i in dup if i!=qid],'same_task_stem_options_other_ids':[i for i in ident if i!=qid]}
 if len(ident)>1 and len({questions[i]['answer'] for i in ident})>1:
  issues.append({'code':'MULTIPLE_REFERENCE_SUMMARIES','evidence':{'ids':ident,'answers':[questions[i]['answer'] for i in ident]},'action':'同一新闻的不同摘要表达与官方原始记录一致；应保留多参考摘要或去重，不能把表达差异直接判作法律答案矛盾。'})
 elif len(dup)>1:
  observations.append({'code':'SAME_STEM_CONTEXT_DISTINCTION','evidence':{'ids':dup,'task_codes':sorted({questions[i]['metadata']['taskCode'] for i in dup}),'options_differ':len({questions[i]['options'] for i in dup})>1},'conclusion':'去重与答案冲突检查必须同时纳入任务说明、选项；此处仅有同题干不足以认定冲突。'})
 if len(sources)>1:observations.append({'code':'DEDUPLICATED_SOURCE_ITEMS','source_item_ids':[x['source_item_id'] for x in sources],'conclusion':'多个原始样本映射同一规范化题目；来源映射保留。'})
 if decision=='TEACHING_CANDIDATE_REQUIRES_REWRITE':issues.append({'code':'LEGAL_AND_PEDAGOGICAL_REVIEW_NOT_COMPLETED','evidence':{'answer_excerpt':r['answer'][:300],'official_task':objective},'action':action})
 legal_refs=sorted(set(re.findall(r'《[^》]{2,80}》',r['stem']+'\n'+r['answer']+'\n'+(r['options'] or ''))))
 row={'question_id':qid,'dataset':'LawBench','content_hash':hashes[qid],'content_hash_algorithm':'QuestionQuality.HASH MySQL SHA2(JSON_ARRAY(...),256)','snapshot_updated_at':r['updated_at'],'audit_scope':'all_rows_programmatic_content_and_official_source_comparison','task_code':task,'official_objective':objective,'decision':decision,'legal_correctness':'NOT_VERIFIED','teaching_explanation_present':False,'structure_check':choice,'answer_leakage_check':leak,'classification':{'discipline':r['discipline'],'subject':r['subject'],'original_category':r['category']},'evidence':{'stem_excerpt':r['stem'][:400],'answer_excerpt':r['answer'][:400],'stem_characters':len(r['stem']),'answer_characters':len(r['answer']),'legal_titles_detected':legal_refs},'issues':issues,'observations':observations,'duplicate_check':duplicates,'sources':sources,'action':action,'manual_content_reading':{'performed':qid in manual_ids,'scope':'完整阅读本地内容并比照该任务目标；并非法律专家核验' if qid in manual_ids else '未逐题进行模型语义阅读；已执行全量内容与来源检查','note':manual_notes.get(qid,'已核对任务目标、题干/选项/答案格式及解析实为任务指令。' if qid in manual_ids else None)}}
 row.update({'id':qid,'contentHash':hashes[qid],'source_verification_status':'VERIFIED_OFFICIAL_SOURCE_AND_TRANSFORM' if source_verified else 'SOURCE_OR_TRANSFORM_MISMATCH','legal_reasoning_verified':False,'upstream_commit':commit,'upstream_urls':[s['source_url'] for s in sources]})
 result.append(row);decisions[decision]+=1;task_stats[task]['unique_questions']+=1;task_stats[task][decision]+=1
 for i in issues:issues_count[i['code']]+=1
(B/'lawbench.jsonl').write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in result))
summary={'dataset':'LawBench','source_rows':len(rows),'unique_questions':len(questions),'official_commit':commit,'official_source_comparisons':len(checks),'official_mismatches':{k:sum(not c[k] for c in checks.values()) for k in next(iter(checks.values())) if k.endswith('_match')},'decisions':dict(decisions),'issue_counts':dict(issues_count),'manual_read_count':len(manual_ids),'manual_read_ids':sorted(manual_ids),'legal_correctness_verified_count':0,'choice_question_count':sum(x['structure_check']['applicable'] for x in result),'choice_structure_failures':sum(x['structure_check'].get('valid_structure') is False for x in result),'task_counts':dict(task_stats),'scope_limit':'全量逐行内容/结构/来源审计完成；仅列出的样本进行了完整语义阅读。未对全库法律正确性逐条检索验证，没有教学准入通过结论。','files':{'ledger':'/tmp/lexaid-full-review/lawbench.jsonl','full_database_export':'/tmp/lexaid-full-review/lawbench-export.jsonl','official_source_files':'/tmp/lexaid-full-review/lawbench-source','source_comparison':'/tmp/lexaid-full-review/lawbench-source-check.jsonl'}}
(B/'lawbench-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in summary.items() if k not in ['task_counts','manual_read_ids','files']},ensure_ascii=False,indent=2))
