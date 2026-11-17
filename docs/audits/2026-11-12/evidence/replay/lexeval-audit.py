#!/usr/bin/env python3
"""Offline replay of the complete LexEval snapshot audit; never modifies a database.
Run from this directory: python3 lexeval-audit.py
Inputs: lexeval-source.jsonl, lexeval-upstream/*.json, lexeval-correction-provenance.jsonl.
It reuses the checked-in importer to propose structural corrections, but never signs legal review.
"""
import collections, hashlib, json, pathlib, re, subprocess

ROOT = pathlib.Path(__file__).resolve().parent
REPO = pathlib.Path('/Users/yangyongze/Desktop/lexaid/lexaid')
COMMIT = '044c695f62894deef41ad9a30797e1da0404945c'
BASE = f'https://github.com/CSHaitao/LexEval/blob/{COMMIT}/'
def sha(v):
    return hashlib.sha256(v if isinstance(v, bytes) else json.dumps(v, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
def norm(v): return re.sub(r'\s+', '', str(v or ''))
def clean(v): return re.sub(r'[ \t]+\n', '\n', str(v or '').replace('\r\n','\n').replace('\r','\n')).strip()
def lines(name): return [json.loads(s) for s in (ROOT / name).read_text().splitlines() if s.strip()]
def write_lines(name, rows): (ROOT / name).write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows))

# Dispositions concern suitability for ordinary legal-learning practice, not correctness of the NLP gold answer.
# The task taxonomy and metrics were checked against the official tasks.png and evaluation/evaluate.py.
TASKS = {
 '1_1': ('TEACHING_CANDIDATE_REWRITE', '法律概念选择题', '补充概念适用条件、每个选项的理由和法律依据后再审核'),
 '1_2': ('TEACHING_CANDIDATE_REWRITE', '法条内容辨认题', '补充准确法律名称、条号、版本和原文出处，不能把片段当完整条文'),
 '1_3': ('TEACHING_CANDIDATE_REWRITE', '法律演变比较题', '明确比较版本和截至日期，对照各版原文解释变化'),
 '2_1': ('REJECT_CURRENT_PRACTICE_NLP', '法律要素标签识别', '转入NLP评测资料；若改教学题，改写为事实与法律要件对应并提供推理'),
 '2_2': ('TEACHING_CANDIDATE_REWRITE', '证据事实核验题', '标明材料中哪些是主张、质证意见和法院认定，逐选项说明支持证据'),
 '2_3': ('TEACHING_CANDIDATE_REWRITE', '案例阅读理解题', '补充原裁判出处、问题所依赖的原文位置与答案依据'),
 '2_4': ('REJECT_CURRENT_PRACTICE_NLP', '关系三元组抽取', '移至关系抽取基准区；保留实体与关系标签，不作为法律结论评分'),
 '2_5': ('REJECT_CURRENT_PRACTICE_NLP', '命名实体识别', '移至实体识别基准区；实体标签不是法律教学解析'),
 '3_1': ('REJECT_CURRENT_PRACTICE_PREDICTION', '罪名标签预测', '保留原预测任务；若改教学题，补齐构成要件事实、反例和定罪依据'),
 '3_2': ('REJECT_CURRENT_PRACTICE_PREDICTION', '法条标签预测', '保留原预测任务；补充法律版本和事实适用论证后才能改为教学题'),
 '3_3': ('REJECT_CURRENT_PRACTICE_PREDICTION', '精确刑期预测', '停止以原案月份作为唯一学习正确答案；需量刑情节、规则和可接受区间'),
 '3_4': ('TEACHING_CANDIDATE_REWRITE', '多步法律推理考试题', '保留考试问题，补充各推理步骤、适用版本与选项辨析'),
 '3_5': ('TEACHING_CANDIDATE_REWRITE', '法律计算题', '修正任务标签为法律计算，列明计算前提、公式、适用版本及边界'),
 '3_6': ('REJECT_CURRENT_PRACTICE_NLP', '诉辩论点匹配', '移至论点匹配基准区；配对文本不等于法理推断或教学解析'),
 '4_1': ('REJECT_CURRENT_PRACTICE_NLP', '相似案例识别', '保留检索基准；另补相似性标准及关键法律事实差异后才能教学评分'),
 '4_2': ('TEACHING_CANDIDATE_REWRITE', '法律文书审查题', '提供逐项纠错依据，区分刻意设置的错误文书与正确法律规则'),
 '5_1': ('REJECT_CURRENT_PRACTICE_NLP', '摘要生成', '移至生成评测区；原摘要是参考输出，需独立信息覆盖评价量表'),
 '5_2': ('REJECT_CURRENT_PRACTICE_GENERATION', '裁判分析生成', '移至生成评测区；补齐裁判出处和事实，建立推理量表，不能直接按参考段落判法律正确'),
 '5_3': ('REJECT_CURRENT_PRACTICE_NLP', '法律翻译', '移至翻译练习区并配置语种与可接受译法量表，不按普通法律简答评分'),
 '5_4': ('TEACHING_CANDIDATE_REWRITE', '开放论述题', '补充论证评价量表、时代背景和参考材料，允许合理的不同论证'),
 '6_1': ('TEACHING_CANDIDATE_REWRITE', '偏见与歧视辨析', '补充法律和伦理依据，区分题中错误观点与教学结论'),
 '6_2': ('TEACHING_CANDIDATE_REWRITE', '伦理行为辨析', '区分伦理评价、违法和犯罪，补充适用条件与逐项理由'),
 '6_3': ('TEACHING_CANDIDATE_REWRITE', '隐私人格权益辨析', '补充隐私或人格权益的适用条件、例外、版本和选项解释'),
}

rows = lines('lexeval-source.jsonl')
upstream = {task: [json.loads(s) for s in (ROOT/'lexeval-upstream'/f'{task}.json').read_text().splitlines() if s.strip()] for task in TASKS}
node = '''import {readFileSync} from 'node:fs';
const {toQuestion}=await import(process.argv[1]);
const rows=JSON.parse(readFileSync(0,'utf8'));
process.stdout.write(JSON.stringify(rows.map(x=>toQuestion({question:x.input,answer:x.answer,instruction:x.instruction,category:x.category,difficulty:3}))));'''
originals = [upstream[r['metadata']['taskCode']][int(r['sourceItemId'].split(':')[1])] for r in rows]
converted = json.loads(subprocess.run(['node','--input-type=module','-e',node,(REPO/'scripts/build-public-question-dataset.mjs').as_uri()], input=json.dumps([{**u,'category':r['category']} for r,u in zip(rows,originals)],ensure_ascii=False),text=True,capture_output=True,check=True).stdout)
byid = collections.defaultdict(list)
for i,r in enumerate(rows): byid[r['id']].append(i)
unique = {qid: rows[idx[0]] for qid,idx in byid.items()}

# Compare complete stems and option texts, ignoring only whitespace and option ordering.
duplicate_groups = collections.defaultdict(list)
for qid,r in unique.items():
    options = json.loads(r['options']) if r['options'] else []
    key = (norm(r['stem']), tuple(sorted(norm(o['text']) for o in options)))
    ans = json.loads(r['answer']) if r['type'] in ('SINGLE','MULTIPLE') else r['answer']
    semantic = tuple(sorted(norm(o['text']) for o in options if o['key'] in ans)) if isinstance(ans,list) else norm(ans)
    duplicate_groups[key].append((qid, semantic))
conflicts = [g for g in duplicate_groups.values() if len({str(x[1]) for x in g}) > 1]
conflict_peers = {qid:[peer for peer,_ in g if peer!=qid] for g in conflicts for qid,_ in g}
provenance = collections.defaultdict(list)
for p in lines('lexeval-correction-provenance.jsonl'): provenance[p['questionId']].append(p)
inspected_excerpts = {next(r['id'] for r in rows if r['metadata']['taskCode']==task) for task in TASKS}
inspected_excerpts.update(conflict_peers)
inspected_excerpts.update([10017188,10017350,10017462,10018629,10021700,10021701,10021702,10022924,10022925,10022926,10023685,10023686,10023687])
audit, proposals, checks = [], [], []
for qid,idx in byid.items():
    r = rows[idx[0]]; codes = sorted({rows[i]['metadata']['taskCode'] for i in idx})
    options = json.loads(r['options']) if r['options'] else []
    selected = json.loads(r['answer']) if r['type'] in ('SINGLE','MULTIPLE') else []
    reasons = []; actions = []; source_checks=[]
    for i in idx:
        src=rows[i]; u=originals[i]; new=converted[i]
        meta_ok = clean(u['answer'])==src['metadata']['originalAnswer'] and clean(u['instruction'])==src['metadata']['instruction']
        before_structure = {k:src[k] for k in ('type','stem','options','answer')}
        expected_structure = {'type':new['type'],'stem':new['stem'],'options':json.dumps(new['options'],ensure_ascii=False,separators=(',',':')) if new['options'] else None,'answer':json.dumps(new['answer'],ensure_ascii=False,separators=(',',':')) if isinstance(new['answer'],list) else new['answer']}
        same_structure = before_structure==expected_structure
        source_checks.append({'sourceItemId':src['sourceItemId'],'upstreamUrl':BASE+f'data/{src["metadata"]["taskCode"]}.json#L{int(src["sourceItemId"].split(":")[1])+1}','upstreamRecordHash':sha(u),'metadataMatchesUpstream':meta_ok,'matchesCurrentImporterStructure':same_structure,'upstreamInputLength':len(u['input']),'upstreamInputHash':sha(u['input'])})
        checks.append(source_checks[-1])
    instruction_only = any(norm(r['analysis'])==norm(rows[i]['metadata']['instruction']) for i in idx) or r['analysis']=='下面是一段新闻报道，请用一句话给出这段报道的摘要。'
    if instruction_only:
        reasons.append('TASK_INSTRUCTION_IS_NOT_ANALYSIS');actions.append('将当前analysis保留于来源档案，教学解析需另写；不能仅更换指令措辞')
    elif not r['analysis']:
        reasons.append('MISSING_TEACHING_ANALYSIS');actions.append('补充可核验的教学解析')
    if not r['answer'].strip():
        reasons.append('UPSTREAM_ANSWER_EMPTY' if all(not clean(originals[i]['answer']) for i in idx) else 'ANSWER_LOST_IN_IMPORT')
        actions.append('原始空答案不得猜填；向源数据或命题材料补证后再修订')
    if r['type']=='SHORT' and any(not c.startswith('5_') for c in codes):
        repaired = all(converted[i]['type'] in ('SINGLE','MULTIPLE') for i in idx)
        reasons.append('CHOICE_LABEL_IMPORT_REPAIRABLE' if repaired else 'CHOICE_REFERENCE_NEEDS_REVIEW')
        actions.append('使用完整匹配的选项标签重建选择题结构' if repaired else '保持隔离；人工核查空标签或括注歧义，不能截断答案')
    if qid in conflict_peers:
        reasons.append('IDENTICAL_PROMPT_DIFFERENT_REFERENCE');actions.append('按关联ID对照原裁判，补齐原告和金额事实；暂不将任一参考段落作为唯一正确答案')
    if '3_5' in codes and r['sourceTask']=='法律推理':
        reasons.append('SOURCE_TASK_LABEL_COLLISION');actions.append('官方3_5为Legal Calculation，应与3_4 Multi-hop Reasoning分开标注')
    all_text='\n'.join(str(r.get(k)or'') for k in ('stem','options','answer'))
    legacy=sorted(set(re.findall('合同法|婚姻法|民法通则|物权法|侵权责任法',all_text)))
    if legacy: reasons.append('LAW_VERSION_CONTEXT_REQUIRED');actions.append('核实历史情境和适用版本；出现旧法名称本身不等于答案错误')
    placeholders=sorted(set(re.findall('×{2,}|时间时间+|x{3,}|X{3,}',r['stem'])))
    if placeholders: reasons.append('MASKED_FACTS_PRESENT');actions.append('核对被遮蔽的事实是否影响教学作答；不自动补全姓名或案情')
    chinese=len(re.findall(r'[\u4e00-\u9fff]',all_text)); latin=len(re.findall('[A-Za-z]',all_text))
    primary=codes[0]; disposition, task_desc, action=TASKS[primary]
    actions.insert(0,action)
    review={'questionId':qid,'id':qid,'dataset':'LexEval','contentHash':r['contentHash'],'sourceItemIds':[rows[i]['sourceItemId'] for i in idx],'taskCodes':codes,'sourceTasks':sorted({rows[i]['sourceTask']for i in idx}),'type':r['type'],'disposition':disposition,'taskInterpretation':task_desc,'reasonCodes':reasons,'actions':list(dict.fromkeys(actions)),
      'evidence':{'stemExcerpt':r['stem'][:1200],'stemLength':len(r['stem']),'optionsCount':len(options),'selectedAnswer':r['answer'][:800],'selectedOptionExcerpts':[{'key':o['key'],'text':o['text'][:280]}for o in options if o['key']in selected],'analysisText':r['analysis'],'analysisIsTaskInstruction':instruction_only,'legacyLawMentions':legacy,'maskedFactMarkers':placeholders,'conflictingQuestionIds':conflict_peers.get(qid,[]),'languageSignals':{'hanCharacters':chinese,'latinLetters':latin,'translationTask':'5_3'in codes}},
      'sourceVerification':source_checks,'assessmentScope':{'allStoredFieldsScanned':True,'allUpstreamRecordsCompared':True,'analystExcerptInspected':qid in inspected_excerpts,'everyLegalAnswerExpertVerified':False,'legalCorrectness':'NOT_ASSESSED','reviewSignaturesCreated':False},'taskDefinitionSource':BASE+'figure/tasks.png'}
    audit.append(review)
    if 'CHOICE_LABEL_IMPORT_REPAIRABLE' in reasons:
        # No proposal is permitted unless its one provenance entry matches the pinned source exactly.
        assert len(idx)==1 and len(provenance[qid])==1 and provenance[qid][0]['dataset']=='LexEval'
        i=idx[0]; u=originals[i]; new=converted[i]
        assert all(s['metadataMatchesUpstream'] for s in source_checks)
        assert r['stem']==clean(u['input']) and r['answer']==clean(u['answer']) and r['options'] is None
        assert new['options'] and all(o['text'] in clean(u['input']) for o in new['options'])
        before={k:r[k] for k in ('type','stem','options','answer','analysis','category')}
        after={**before,'type':new['type'],'stem':new['stem'],'options':json.dumps(new['options'],ensure_ascii=False,separators=(',',':')),'answer':json.dumps(new['answer'],ensure_ascii=False,separators=(',',':'))}
        proposals.append({'id':qid,'questionId':qid,'expectedContentHash':r['contentHash'],'reason':'CHOICE_LABEL_IMPORT_REPAIRABLE','before':before,'after':after,'changedFields':['type','stem','options','answer'],'analysisPreserved':True,'legalReviewApproved':False,'provenanceCount':1,'upstreamRecordHash':sha(u),'upstreamSource':source_checks[0]['upstreamUrl'],'originalAnswer':u['answer'],'completeInputCompared':True,'completeOptionsCompared':True})

write_lines('lexeval.jsonl',audit)
write_lines('lexeval-corrections.jsonl',proposals)
write_lines('lexeval-source-verification.jsonl',checks)
summary={'dataset':'LexEval','upstreamCommit':COMMIT,'sourceRecordCount':len(rows),'uniqueQuestionCount':len(audit),'collapsedSourceRecords':len(rows)-len(audit),'taskCount':len(TASKS),'sourceMetadataMatches':sum(x['metadataMatchesUpstream']for x in checks),'matchesCurrentImporterStructure':sum(x['matchesCurrentImporterStructure']for x in checks),'structuralCorrectionProposals':len(proposals),'correctionProposalUniqueIds':len({x['id']for x in proposals}),'dispositions':dict(collections.Counter(x['disposition']for x in audit)),'reasonCounts':dict(collections.Counter(c for x in audit for c in x['reasonCodes'])),'taskSourceCounts':dict(collections.Counter(r['metadata']['taskCode']for r in rows)),'taskUniqueQuestionCounts':{t:len({r['id']for r in rows if r['metadata']['taskCode']==t})for t in TASKS},'duplicatePromptReferenceVariantGroups':[[x[0]for x in g]for g in conflicts],'explicitMultipleInstructionSingleAnswerRows':sum(bool(re.search('多项选择|多选|不定项',r['metadata']['instruction'])) and r['type']=='SINGLE'for r in rows),'analystExcerptInspectedIds':sorted(inspected_excerpts),'legalAnswersIndividuallyVerified':0,'limits':['全量规则扫描和上游完整字段对照，不等于逐题法律专家核验','任务用途结论来自官方任务分类及实际字段；教学候选均需补充解析与依据','旧法提及可能属于历史情境或错误选项，不能自动判错','同题不同生成参考不必然有一份错误，但不能当作唯一标准答案','仅输出修复建议，没有修改数据库或伪造审核人'],
 'officialReferences':[BASE+'README_ZH.md',BASE+'figure/tasks.png',BASE+'code/evaluation/evaluate.py'],
 'inputFingerprints':{p.name:sha(p.read_bytes())for p in [ROOT/'lexeval-source.jsonl',ROOT/'lexeval-correction-provenance.jsonl']},'upstreamFileFingerprints':{f'data/{t}.json':sha((ROOT/'lexeval-upstream'/f'{t}.json').read_bytes())for t in TASKS}}
summary['outputFingerprints']={name:sha((ROOT/name).read_bytes())for name in ['lexeval.jsonl','lexeval-corrections.jsonl','lexeval-source-verification.jsonl']}
(ROOT/'lexeval-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:summary[k]for k in ['sourceRecordCount','uniqueQuestionCount','sourceMetadataMatches','matchesCurrentImporterStructure','structuralCorrectionProposals','dispositions','reasonCounts','duplicatePromptReferenceVariantGroups']},ensure_ascii=False,indent=2))
