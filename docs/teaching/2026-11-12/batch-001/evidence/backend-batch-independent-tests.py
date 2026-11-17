import json,gzip,importlib.util,copy,hashlib,subprocess
from pathlib import Path
root=Path('/Users/yangyongze/Desktop/lexaid/lexaid')
spec=importlib.util.spec_from_file_location('tests',root/'scripts/test-teaching-question-batch.py');t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t)
d=next(x for x in map(json.loads,(root/'datasets/question-explanations/2026-11-12/backend.jsonl').read_text().splitlines()) if x['id']==10024085)
row={**d,'revisionKey':'2026-11-12-teaching-001-10024085','beforeClassification':{'discipline':'综合与待判定','subject':'待判定'},'afterClassification':{'discipline':'民商法','subject':'物权法'}}
passed=[]
r=t.execute(row,pre='DELETE FROM question_legal_subject;',repeat=True)
assert [x['status'] for x in r if 'status' in x]==['APPLIED_OR_ALREADY_APPLIED']*2
assert next(x for x in r if x.get('probe')=='current')['content']=={**row['after'],**row['afterClassification']}
passed.append('missing classification row with fallback baseline inserts exact classification and reruns safely')
for key in ['before_content','after_content','evidence_urls','question_id']:
 before={**row['before'],**row['beforeClassification']};after={**row['after'],**row['afterClassification']};urls=sorted({x['url'] for x in row['legalBasis']});qid=row['id']
 if key=='before_content':before['answer']='altered archive before'
 if key=='after_content':after['analysis']='altered archive after'
 if key=='evidence_urls':urls=['https://example.org/different-evidence']
 if key=='question_id':qid+=1
 pre='INSERT INTO question_content_revision VALUES ('+','.join([t.batch.text_sql(row['revisionKey']),str(qid),t.batch.text_sql(before),t.batch.text_sql(after),t.batch.text_sql(urls),'NULL'])+');'
 r=t.execute(row,pre=pre)
 assert next(x for x in r if 'status' in x)['status']=='ARCHIVE_CONFLICT',key
 assert next(x for x in r if x.get('probe')=='current')['content']=={**row['before'],**row['beforeClassification']},key
 assert next(x for x in r if x.get('probe')=='archive')['applied']==0
 passed.append('archive '+key+' mismatch rejects update')
original=t.batch.build_sql
t.batch.build_sql=lambda rows:original(rows).replace('COMMIT;','ROLLBACK;')
r=t.execute(row)
assert next(x for x in r if x.get('probe')=='current')['content']=={**row['before'],**row['beforeClassification']}
assert next(x for x in r if x.get('probe')=='archive')['count']==0
passed.append('explicit rollback restores both content and classification and removes archive insert')
t.batch.build_sql=original
# Existing archive with exact payload but not yet marked applied may finish safely.
pre='INSERT INTO question_content_revision VALUES ('+','.join([t.batch.text_sql(row['revisionKey']),str(row['id']),t.batch.text_sql({**row['before'],**row['beforeClassification']}),t.batch.text_sql({**row['after'],**row['afterClassification']}),t.batch.text_sql(sorted({x['url'] for x in row['legalBasis']})),'NULL'])+');'
r=t.execute(row,pre=pre)
assert next(x for x in r if 'status' in x)['status']=='APPLIED_OR_ALREADY_APPLIED'
assert next(x for x in r if x.get('probe')=='archive')=={'probe':'archive','count':1,'applied':1}
passed.append('exact pending archive applies once')
out={'tests':len(passed),'passed':passed,'persistentDatabaseMutation':False,'reviewer':'backend_audit'}
Path('/tmp/lexaid-full-review/backend-batch-independent-tests.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print(json.dumps(out,ensure_ascii=False))
