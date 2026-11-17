import json,subprocess,sys
from pathlib import Path
root=Path('/Users/yangyongze/Desktop/lexaid/lexaid'); source=Path('/tmp/lexaid-full-review'); fixture=source/'stale-reading-fixture'; fixture.mkdir(exist_ok=True)
for name in ['lecoqa-source.jsonl','lecoqa-semantic-backend.jsonl','lecoqa-semantic-frontend.jsonl','lecoqa-semantic-legal.jsonl','lecoqa.jsonl','lecoqa-source-verification.jsonl','corrections-independent-review.json','lexeval.jsonl','lawbench.jsonl','seed.jsonl','formal.jsonl']:
 dest=fixture/name
 if dest.exists() or dest.is_symlink():dest.unlink()
 if name in ['lecoqa-source.jsonl','lecoqa-semantic-backend.jsonl']:
  rows=[json.loads(l) for l in (source/name).read_text().splitlines()]
  next(r for r in rows if r['id']==10024055)['contentHash']='0'*64
  dest.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
 else:dest.symlink_to(source/name)
p=subprocess.run([sys.executable,str(root/'scripts/merge-question-content-reviews.py'),'--input',str(fixture),'--output',str(fixture/'out')],capture_output=True,text=True)
print('stale reading merge exit',p.returncode)
if p.returncode:print(p.stderr)
else:
 r=next(json.loads(l) for l in (fixture/'out/lecoqa.jsonl').read_text().splitlines() if json.loads(l)['id']==10024055)
 print('accepted baseHash',r['contentHash'],'semanticReviewHash',r['semanticReview']['contentHash'])
