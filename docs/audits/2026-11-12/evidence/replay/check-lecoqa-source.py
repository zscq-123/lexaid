from pathlib import Path
import json,re,hashlib
from datetime import datetime,timezone
p=Path(__file__).parent
upstream=json.loads((p/'lecoqa-upstream.json').read_text(), parse_constant=lambda _: None)
index={str(r.get('query_id',i)):r for i,r in enumerate(upstream)}
local=[json.loads(s) for s in (p/'lecoqa-source.jsonl').read_text().splitlines()]
manifest=json.loads((p/'lecoqa-source-manifest.json').read_text())
def clean(value):
 text='' if value is None else str(value)
 text=re.sub(r'[ \t]+\n','\n',re.sub(r'\r\n?','\n',text))
 return re.sub(r'^[\t\n\v\f\r \u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\t\n\v\f\r \u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$', '', text)
results=[]
for row in local:
 sources=[s for s in row['sources'] if s['dataset']=='LeCoQA']
 for source in sources:
  original=index[source['itemId']]
  expected={'type':'SHORT','stem':clean(original.get('问题')),'options':None,'answer':clean(original.get('答案文本')),'analysis':'\n\n'.join(k+'：'+clean(v) for k,v in original.get('相关法规',{}).items()),'category':'法律咨询'}
  checks={key:row[key]==value for key,value in expected.items()}
  m=source['metadata']
  checks['keywords']=m['keywords']==original.get('关键词')
  checks['statuteNames']=m['statuteNames']==original.get('match_name',[])
  checks['originalQueryId']=m['originalQueryId']==original.get('query_id')
  results.append({'id':row['id'],'contentHash':row['contentHash'],'sourceItemId':source['itemId'],'checks':checks,'matched':all(checks.values()),'sourceUrl':'https://github.com/oneal2000/LeCoQA/blob/'+manifest['commit']+'/data/queries.json'})
assert len(results)==len(upstream)==1543
(p/'lecoqa-source-verification.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in results))
manifest.update(verifiedAt=datetime.now(timezone.utc).isoformat(),sourceRecords=len(upstream),compared=len(results),matched=sum(r['matched'] for r in results),mismatches=[r for r in results if not r['matched']])
(p/'lecoqa-source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in manifest.items() if k!='mismatches'},ensure_ascii=False))
assert not manifest['mismatches'],json.dumps(manifest['mismatches'][:3],ensure_ascii=False)
