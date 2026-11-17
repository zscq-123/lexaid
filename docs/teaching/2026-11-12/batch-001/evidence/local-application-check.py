import importlib.util
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from datetime import datetime, timezone

root=pathlib.Path('/Users/yangyongze/Desktop/lexaid/lexaid')
spec=importlib.util.spec_from_file_location('batch',root/'scripts/build-teaching-question-batch.py')
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
db=sys.argv[1]
assert re.fullmatch(r'[a-zA-Z0-9_]+',db)
directory=root/'docs/teaching/2026-11-12/batch-001'
rows=b.records(directory/'revisions.jsonl')
assert len(rows)==216 and all(r['crossReview']['verdict']=='PASS' and r['crossReview']['afterHash']==b.digest(r['after']) for r in rows)
command=['docker','exec','-i','lexaid-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" '+db]
def query(sql):
    r=subprocess.run(command,input=sql,text=True,capture_output=True)
    if r.returncode:raise RuntimeError(r.stderr[:3000])
    return r.stdout
def checksums(tables):
    result=query('CHECKSUM TABLE '+','.join(tables)+' EXTENDED;')
    pairs=[line.split('\t') for line in result.splitlines()]
    assert all(len(p)==2 and p[1]!='NULL' for p in pairs),pairs
    return {p[0].split('.')[-1]:p[1] for p in pairs}
history=['answer_record','exam_session','mock_exam_item','mock_exam_session','question_quality_review','mock_exam_bank','question_source']
before=checksums(history)
sql=(root/'backend/src/main/resources/question-teaching-batch-001.sql').read_text()
applied=[json.loads(l) for l in query(sql).splitlines() if l.startswith('{')]
assert len(applied)==216 and all(r['status']=='APPLIED_OR_ALREADY_APPLIED' for r in applied),[r for r in applied if r['status']!='APPLIED_OR_ALREADY_APPLIED']
ids=','.join(str(r['id']) for r in rows)
observed=[json.loads(l) for l in query("SELECT JSON_OBJECT('id',q.id,'content',"+b.CURRENT_JSON+",'hash',"+b.HASH_SQL+") FROM "+b.JOIN+' WHERE q.id IN ('+ids+');').splitlines()]
current={r['id']:r for r in observed}
assert len(current)==216
for row in rows:
    assert current[row['id']]['content']=={**row['after'],**row['afterClassification']},row['id']
archive=json.loads(query("SELECT JSON_OBJECT('total',COUNT(*),'applied',COUNT(applied_at)) FROM question_content_revision WHERE revision_key LIKE '2026-11-12-teaching-001-%';"))
assert archive=={'total':216,'applied':216},archive
stable_tables=['question','question_legal_subject','question_content_revision']
first=checksums(stable_tables)
repeat=[json.loads(l) for l in query(sql).splitlines() if l.startswith('{')]
assert len(repeat)==216 and all(r['status']=='APPLIED_OR_ALREADY_APPLIED' for r in repeat)
second=checksums(stable_tables)
after=checksums(history)
assert before==after,{'before':before,'after':after}
assert first==second,{'first':first,'second':second}
report={'database':db,'verifiedAt':datetime.now(timezone.utc).isoformat(),'migrationSha256':hashlib.sha256(sql.encode()).hexdigest(),'records':applied,'exactAfterMatches':216,'archive':archive,'historyBefore':before,'historyAfter':after,'historyPreserved':True,'repeatPreservedChecksums':first,'idempotent':True,'releaseApproval':False}
name='applications.json' if db=='lexaid' else 'evidence/isolated-applications.json'
(directory/name).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='records'},ensure_ascii=False,indent=2))
