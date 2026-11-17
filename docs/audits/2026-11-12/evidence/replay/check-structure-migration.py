import json,pathlib,subprocess,hashlib
ROOT=pathlib.Path(__file__).resolve().parent
REPO=pathlib.Path('/Users/yangyongze/Desktop/lexaid/lexaid')
proposals=[json.loads(s)for s in (ROOT/'lexeval-corrections.jsonl').read_text().splitlines()]
live_sql=(REPO/'backend/src/main/resources/question-structure-review-20261112.sql').read_text()
# All three application tables are TEMPORARY shadows in one isolated connection.
sql=live_sql.replace('CREATE TABLE IF NOT EXISTS question_content_revision','CREATE TEMPORARY TABLE IF NOT EXISTS question_content_revision')
def strsql(v):return 'NULL' if v is None else f"CONVERT(0x{str(v).encode().hex()} USING utf8mb4)"
def jsonsql(v):return f'CAST({strsql(json.dumps(v,ensure_ascii=False,separators=(",",":")))} AS JSON)'
fields=('type','stem','options','answer','analysis','category')
content="JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category)"
same_after=f'BINARY CAST({content} AS CHAR)=BINARY CAST(e.after_content AS CHAR)'
setup='''SET NAMES utf8mb4;
CREATE TEMPORARY TABLE question(id BIGINT PRIMARY KEY,type VARCHAR(20),stem MEDIUMTEXT,options MEDIUMTEXT,answer MEDIUMTEXT,analysis MEDIUMTEXT,category VARCHAR(50))ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE answer_record(id BIGINT PRIMARY KEY,question_id BIGINT)ENGINE=InnoDB;
CREATE TEMPORARY TABLE expected(id BIGINT PRIMARY KEY,before_content JSON,after_content JSON)ENGINE=InnoDB;
'''
setup+='INSERT INTO question(id,'+','.join(fields)+')VALUES\n'+',\n'.join('('+str(x['id'])+','+','.join(strsql(x['before'][f])for f in fields)+')'for x in proposals)+';\n'
setup+='INSERT INTO expected(id,before_content,after_content)VALUES\n'+',\n'.join(f"({x['id']},{jsonsql(x['before'])},{jsonsql(x['after'])})"for x in proposals)+';\n'
def verify(label):return f'''SELECT COUNT(*) INTO @lexaid_test_matches FROM question q JOIN expected e ON e.id=q.id WHERE {same_after};
SELECT COUNT(*),SUM(applied_at IS NOT NULL) INTO @lexaid_test_archives,@lexaid_test_applied FROM question_content_revision;
SELECT COUNT(*) INTO @lexaid_test_matching_archives FROM question_content_revision r JOIN expected e ON e.id=r.question_id WHERE BINARY CAST(r.before_content AS CHAR)=BINARY CAST(e.before_content AS CHAR) AND BINARY CAST(r.after_content AS CHAR)=BINARY CAST(e.after_content AS CHAR);
SELECT JSON_OBJECT('check','{label}','changed',@lexaid_structure_review_changed,'matchesProposal',@lexaid_test_matches,'archives',@lexaid_test_archives,'appliedArchives',@lexaid_test_applied,'matchingArchives',@lexaid_test_matching_archives);
'''
command=['docker','exec','-i','lexaid-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -r -u "$MYSQL_USER" lexaid_upgrade_20261111_check']
def execute(name,body):
    result=subprocess.run(command,input=body,text=True,capture_output=True,timeout=60)
    (ROOT/f'lexeval-structure-{name}.log').write_text(result.stdout+'\n'+result.stderr)
    if result.returncode: raise RuntimeError(result.stderr[-2500:])
    return [json.loads(line)for line in result.stdout.splitlines()if line.startswith('{')]
baseline=execute('baseline',setup+sql+verify('first')+sql+verify('repeated'))
assert baseline==[
 {'check':'first','changed':87,'matchesProposal':87,'archives':87,'appliedArchives':87,'matchingArchives':87},
 {'check':'repeated','changed':0,'matchesProposal':87,'archives':87,'appliedArchives':87,'matchingArchives':87}],baseline

# Independently change every protected field in six rows, add history to a seventh,
# and seed a conflicting archive for the eighth. None may be overwritten.
modified=setup+sql.split('DROP TEMPORARY TABLE IF EXISTS')[0]
for i,field in enumerate(fields):
    original=proposals[i]['before'][field]
    changed='MANUAL_CHANGE' if original is None else original+' manual-change'
    # type is varchar(20), all other fixture changes fit their real column limits.
    modified+=f"UPDATE question SET {field}={strsql(changed)} WHERE id={proposals[i]['id']};\n"
history_id=proposals[6]['id'];conflict_id=proposals[7]['id']
modified+=f'INSERT INTO answer_record(id,question_id)VALUES(1,{history_id});\n'
modified+=f"INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)VALUES('2026-11-12-lexeval-structure-{conflict_id}',{conflict_id},JSON_OBJECT(),JSON_OBJECT(),JSON_ARRAY('manual-archive'));\n"
modified+='CREATE TEMPORARY TABLE guarded_before AS SELECT * FROM question WHERE id IN('+','.join(str(x['id'])for x in proposals[:8])+');\n'
guard_test=f'''SELECT JSON_OBJECT('check','guardedRows','unchanged',(SELECT COUNT(*)FROM question q JOIN guarded_before b ON b.id=q.id WHERE BINARY CAST({content}AS CHAR)=BINARY CAST(JSON_OBJECT('type',b.type,'stem',b.stem,'options',b.options,'answer',b.answer,'analysis',b.analysis,'category',b.category)AS CHAR)),'historyRows',(SELECT COUNT(*)FROM answer_record),'historyArchiveRows',(SELECT COUNT(*)FROM question_content_revision WHERE question_id={history_id}));
'''
guarded=execute('guarded',modified+sql+verify('guardedFirst')+guard_test+sql+verify('guardedRepeated')+guard_test)
assert guarded==[
 {'check':'guardedFirst','changed':79,'matchesProposal':79,'archives':80,'appliedArchives':79,'matchingArchives':79},
 {'check':'guardedRows','unchanged':8,'historyRows':1,'historyArchiveRows':0},
 {'check':'guardedRepeated','changed':0,'matchesProposal':79,'archives':80,'appliedArchives':79,'matchingArchives':79},
 {'check':'guardedRows','unchanged':8,'historyRows':1,'historyArchiveRows':0}],guarded
report={'sqlBytes':len(live_sql.encode()),'sqlSha256':hashlib.sha256(live_sql.encode()).hexdigest(),'proposalCount':len(proposals),'isolatedDatabase':'lexaid_upgrade_20261111_check','tables':'TEMPORARY question, answer_record, question_content_revision; no durable fixture writes','baseline':baseline,'protectedFields':fields,'guarded':guarded,'passed':True}
(ROOT/'lexeval-structure-migration-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
