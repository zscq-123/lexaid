import json, subprocess, pathlib, urllib.request, concurrent.futures,os,hashlib
base=pathlib.Path(os.environ.get('LEXAID_AUDIT_DIR','/tmp/lexaid-full-review'))
(base/'lawbench-source').mkdir(parents=True,exist_ok=True)
sql="""SELECT JSON_OBJECT('id',q.id,'type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category,'discipline',s.discipline,'subject',s.subject,'updated_at',q.updated_at,'source_item_id',qs.source_item_id,'source_task',qs.source_task,'source_url',qs.source_url,'metadata',qs.metadata,'source_id',qs.id) FROM question q JOIN question_source qs ON qs.question_id=q.id LEFT JOIN question_legal_subject s ON s.question_id=q.id WHERE qs.dataset='LawBench' ORDER BY q.id,qs.id;"""
proc=subprocess.run(['docker','exec','-i','lexaid-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],input=sql,text=True,capture_output=True,check=True)
rows=[json.loads(line) for line in proc.stdout.splitlines()]
(base/'lawbench-export.jsonl').write_text(''.join(json.dumps(x,ensure_ascii=False)+'\n' for x in rows))
def fetch(url):
    req=urllib.request.Request(url,headers={'User-Agent':'LexAid-content-audit'})
    with urllib.request.urlopen(req,timeout=45) as r: return r.read()
commit=os.environ.get('LAW_BENCH_COMMIT','e30981bb3ff54c41571f222e0b23e92d27375388')
tasks=sorted({x['metadata']['taskCode'] for x in rows})
def download(task):
    raw=fetch(f'https://raw.githubusercontent.com/open-compass/LawBench/{commit}/data/zero_shot/{task}.json')
    (base/'lawbench-source'/f'{task}.json').write_bytes(raw)
    return task,len(json.loads(raw))
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    counts=dict(pool.map(download,tasks))
(base/'lawbench-source'/'README.md').write_bytes(fetch(f'https://raw.githubusercontent.com/open-compass/LawBench/{commit}/README.md'))
(base/'lawbench-source'/'provenance.json').write_text(json.dumps({'repository':'https://github.com/open-compass/LawBench','commit':commit,'task_counts':counts},indent=2))
files=[{'path':f.name,'url':f'https://raw.githubusercontent.com/open-compass/LawBench/{commit}/'+('data/zero_shot/'+f.name if f.suffix=='.json' else f.name),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size} for f in sorted((base/'lawbench-source').iterdir()) if f.name!='provenance.json']
(base/'lawbench-source-manifest.json').write_text(json.dumps({'repository':'https://github.com/open-compass/LawBench','commit':commit,'task_counts':counts,'files':files},indent=2))
print(json.dumps({'db_source_rows':len(rows),'unique_questions':len({x['id'] for x in rows}),'official_commit':commit,'official_tasks':counts},ensure_ascii=False))
