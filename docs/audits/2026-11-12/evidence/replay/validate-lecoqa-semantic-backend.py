import collections, datetime, hashlib, json
from pathlib import Path
b=Path('/tmp/lexaid-full-review')
p=b/'lecoqa-semantic-backend.jsonl'
sorted_source=sorted(map(json.loads,(b/'lecoqa-source.jsonl').read_text().splitlines()),key=lambda x:x['id'])
source=sorted_source[:515]
review=list(map(json.loads,p.read_text().splitlines()))
assert len(review)==515 and len({x['id'] for x in review})==515
assert [x['id'] for x in review]==[x['id'] for x in source]
assert [x['sourceIndex'] for x in review]==list(range(515))
for x,s in zip(review,source):
    assert x['contentHash']==s['contentHash']
    assert x['fieldsRead']==['stem','answer','analysis']
    assert x['fullContentRead'] is True and x['reviewerKind']=='AI'
    assert x['humanReview'] is False and x['legalReasoningVerified'] is False
    assert x['decision'] in {'CONFIRMED_DEFECT','REQUIRES_MORE_VERIFICATION','NO_SPECIFIC_DEFECT_IDENTIFIED'} and x['reason'].strip()
    if x['decision']=='CONFIRMED_DEFECT':
        assert x['confirmedIssues'] and all(i.get('issue') and i.get('officialUrl','').startswith('https://') and i.get('version') for i in x['confirmedIssues'])
report={
    'scope':{'source':'lecoqa-source.jsonl','sortBy':'id','indexRange':[0,515],'firstId':source[0]['id'],'lastId':source[-1]['id']},
    'records':len(review),'fullContentReadRecords':sum(x['fullContentRead'] for x in review),
    'fieldsRead':['stem','answer','analysis'],'decisionCounts':dict(collections.Counter(x['decision'] for x in review)),
    'reviewerKind':'AI','humanReviewedRecords':0,'fullLegalReasoningVerifiedRecords':0,
    'validation':{'uniqueIds':True,'exactScopeCoverage':True,'contentHashesMatchSource':True,'requiredFieldsComplete':True,'allConfirmedIssuesHaveOfficialUrlAndVersion':True},
    'reviewArtifact':str(p),'reviewArtifactSha256':hashlib.sha256(p.read_bytes()).hexdigest(),
    'sourceArtifactSha256':hashlib.sha256((b/'lecoqa-source.jsonl').read_bytes()).hexdigest(),
    'method':'AI逐批完整阅读范围内全部题干、答案、解析后逐条撰写记录；针对确定问题复用官方语料或检索官方来源。验证脚本只检查覆盖和字段完整性，不产生语义审核结论。',
    'limits':['已阅读不等于法律正确性通过。','CONFIRMED_DEFECT确认记录所列窄点，不代表整题法律推理已完整核验。','NO_SPECIFIC_DEFECT_IDENTIFIED仅表示本轮实读未识别具体缺陷，不能作为教学批准。','未核完全部法律依据，515条legalReasoningVerified均为false，未登记人工审核或改变题库隔离状态。'],
    'knownPendingExample':{'id':10024458,'decision':'REQUIRES_MORE_VERIFICATION','note':'奋斗者协议与奖金对价论断仍需区分真正无单位安排的自愿延时和法定加班；官方案例确认格式条款预先放弃法定加班费无效，但尚未逐一核实题称多数裁判及奖金事实。','officialUrl':'https://chinajob.mohrss.gov.cn/h5/c/2022-07-18/356295.shtml'},
    'validatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()
}
(b/'lecoqa-semantic-backend-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
