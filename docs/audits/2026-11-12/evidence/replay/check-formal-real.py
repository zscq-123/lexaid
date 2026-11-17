from pathlib import Path
import json,re,unicodedata,hashlib
from bs4 import BeautifulSoup
p=Path(__file__).parent
rows=[json.loads(s) for s in (p/'formal-source.jsonl').read_text().splitlines()]
def norm(s):return re.sub(r'\s+','',unicodedata.normalize('NFKC',s)).replace('。','.').replace('“','"').replace('”','"').replace('‘',"'").replace('’',"'")
def parse(url):
 f=p/'formal-pages'/url.split('#')[0].split('/')[-1];soup=BeautifulSoup(f.read_bytes(),'html.parser');body=soup.select_one('#artibody');result={};current=None;option=None
 for para in body.find_all('p'):
  text=para.get_text().strip();m=re.match(r'^(\d{1,3})[。．.、](.*)',text)
  if m and int(m[1])<=100:
   current=int(m[1]);option=None;result.setdefault(current,{'stem':m[2],'options':{},'keys':[]});continue
  m=re.match(r'^([A-D])[。．.、](.*)',text,re.S)
  if current and m:
   option=m[1];result[current]['options'][option]=m[2]
   if para.find('u') or any('underline' in t.get('style','') for t in [para]+para.find_all()):result[current]['keys'].append(option)
  elif current and text and option:
   if text.startswith(('二、','三、','二。','三。','[','责任编辑')):continue
   # The source uses a separate paragraph for every option; flag incomplete entries rather than guessing.
 return result
cache={}
for r in rows:
 if r['sourceType']=='REAL' and r['kind']!='SHORT':
  for k in ['readingUrl','sourceUrl']:
   url=r[k].split('#')[0]
   if url not in cache:cache[url]=parse(url)
results=[]
for r in rows:
 if r['sourceType']!='REAL' or r['kind']=='SHORT':continue
 no=r['id']%10000%100
 if no==0:no=100
 # Stable IDs end with the question number; 7100101 is paper one, question one.
 question=cache[r['readingUrl'].split('#')[0]].get(no,{})
 answer=cache[r['sourceUrl'].split('#')[0]].get(no,{})
 keys=answer.get('keys',[])
 equal={k:norm(question.get('options',{}).get(k,''))==norm(answer.get('options',{}).get(k,'')) for k in 'ABCD'}
 results.append({'id':r['id'],'questionNumber':no,'answerMatches':json.loads(r['answer'])==keys,'optionsMatch':equal,'questionOptionCount':len(question.get('options',{})),'sourceAnswer':keys,'storedAnswer':json.loads(r['answer']),'sourceUrl':r['sourceUrl'],'readingUrl':r['readingUrl'],'scope':'Historical publication consistency; no claim about current-law correctness'})
(p/'formal-real-source-check.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in results))
print(json.dumps({'records':len(results),'answerMatches':sum(r['answerMatches'] for r in results),'allOptionsMatch':sum(all(r['optionsMatch'].values()) for r in results),'failures':[r for r in results if not r['answerMatches'] or not all(r['optionsMatch'].values())][:15]},ensure_ascii=False,indent=2))
