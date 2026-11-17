from pathlib import Path
import re,json,hashlib,collections,difflib
p=Path('/tmp/lexaid-full-review')
qs=list(map(json.loads,(p/'lecoqa-source.jsonl').read_text().splitlines()))
corpus={j['law']:j for j in [json.loads(f.read_text()) for f in (p/'official').glob('*.json')]}
pat=re.compile(r'(?:^|\n)([^\n：:]{2,150}?)(第[零〇一二三四五六七八九十百千万\d]+条(?:之[一二三四五六七八九十\d]+)?)\s*[：:]')
numchars='零〇一二三四五六七八九十百千万0123456789'
def canon(law):return law.replace('中华人民共和国','').strip().replace('〈','《').replace('〉','》')
def norm(t):return re.sub(r'[^\u4e00-\u9fffA-Za-z0-9]','',re.sub(r'【[^】]*】|〖[^〗]*〗','',t)).lower()
def refs(analysis):
 ms=list(pat.finditer(analysis));out=[]
 for i,m in enumerate(ms):out.append({'law':canon(m[1]),'article':m[2],'text':analysis[m.end():ms[i+1].start() if i+1<len(ms) else len(analysis)].strip()})
 return out
rows=[]; mismatches=[]
for q in qs:
 analysis=q['analysis']or'';answer=q['answer']or'';referenceChecks=[];flags=[]
 for ref in refs(analysis):
  law=ref['law'];co=corpus.get(law);article=ref['article'];body=norm(ref['text']);item={'law':law,'article':article,'quotationChars':len(ref['text'])}
  if not co or not co['articles']:
   item.update(status='NOT_CHECKED_OFFICIAL_TEXT_UNAVAILABLE',officialUrl=co['url'] if co else None)
  elif article not in co['articles']:
   item.update(status='ARTICLE_NUMBER_NOT_IN_FETCHED_VERSION',officialUrl=co['url'],officialVersion=co['promulgated'])
   mismatches.append({'id':q['id'],'stem':q['stem'],'law':law,'article':article,'datasetText':ref['text'],'officialText':None})
  else:
   expected=norm(co['articles'][article]);item.update(officialUrl=co['url'],officialVersion=co['promulgated'],officialEffective=co['effective'],officialTextSha256=hashlib.sha256(co['articles'][article].encode()).hexdigest())
   if body==expected:status='MATCH_NORMALIZED_FULL_ARTICLE'
   elif len(body)>=15 and body in expected:status='MATCH_NORMALIZED_EXCERPT'
   else:
    status='TEXT_DIFFERENCE_REQUIRES_REVIEW';item['similarity']=round(difflib.SequenceMatcher(None,body,expected,autojunk=False).ratio(),4)
    mismatches.append({'id':q['id'],'stem':q['stem'],'law':law,'article':article,'datasetText':ref['text'],'officialText':co['articles'][article]})
   item['status']=status
  referenceChecks.append(item)
 if not answer.strip():flags.append({'code':'EMPTY_ANSWER','level':'confirmed','evidence':'answer is empty'})
 if not analysis.strip():flags.append({'code':'EMPTY_ANALYSIS','level':'confirmed','evidence':'analysis is empty'})
 if q['subject']=='待判定':flags.append({'code':'SUBJECT_NOT_ASSIGNED','level':'confirmed','evidence':'question_legal_subject.subject=待判定'})
 if re.search(r'(?<!劳动)合同法|婚姻法|侵权责任法|民法通则|担保法',answer+' '+analysis):flags.append({'code':'LEGACY_LAW_NAME_MENTION','level':'risk','evidence':'May be historical discussion or a retained cross-reference; not automatically wrong'})
 if re.search(r'(?:年利率|利息|利率).{0,18}(?:24%|36%|百分之二十四|百分之三十六)',answer):flags.append({'code':'LEGACY_LENDING_RATE_CANDIDATE','level':'risk','evidence':'Verify date and transitional rules; do not infer current rule from historical thresholds'})
 if re.search(r'一律|一切|必然|任何.{0,15}(?:都|均)|无论.{0,25}(?:都|均)',answer):flags.append({'code':'ABSOLUTE_PROPOSITION','level':'risk','evidence':'Check omitted conditions/exceptions in full answer'})
 if any(r['status']in('TEXT_DIFFERENCE_REQUIRES_REVIEW','ARTICLE_NUMBER_NOT_IN_FETCHED_VERSION') for r in referenceChecks):flags.append({'code':'CITATION_TEXT_OR_VERSION_DIFFERENCE','level':'risk','evidence':'See per-reference text comparison; difference is not itself a legal error'})
 if any(r['law']=='商标法' for r in referenceChecks):flags.append({'code':'UPCOMING_VERSION','level':'info','evidence':'2026 trademark revision takes effect 2027-01-01; audit 2026-11-12 uses 2019 text','officialUrl':'https://www.cnipa.gov.cn/col/col3684/index.html'})
 gaps=[]
 if any(r['status']=='NOT_CHECKED_OFFICIAL_TEXT_UNAVAILABLE' for r in referenceChecks):gaps.append('One or more cited instruments not compared to official full text')
 gaps+=['Application of law to the question and completeness of exceptions not established by text matching','No second independent human approval','Original consultation date, jurisdiction and applicable-law version are not structured in the question']
 teaching={'answerPresent':bool(answer.strip()),'analysisPresent':bool(analysis.strip()),'parsedLegalBasisCount':len(referenceChecks),'explicitApplicationReasoningSignal':bool(re.search(r'因为|因此|所以|故而|故本案|综上|之所以|理由在于',answer)),'explicitMisconceptionSignal':bool(re.search(r'误区|并不意味着|不等于|不能一概|不应当认为|不能当然|并非',answer)),'note':'Signals are text features, not judgments of teaching sufficiency; generic doctrinal questions may not need a fact scenario'}
 rows.append({'id':q['id'],'source':'LeCoQA','sourceItemIds':[s['itemId']for s in q['sources'] if s['dataset']=='LeCoQA'],'contentHash':q['contentHash'],'stem':q['stem'],'status':'CONFIRMED_DEFECT' if any(f['level']=='confirmed'for f in flags)else 'RISK_REQUIRES_REVIEW' if any(f['level']=='risk'for f in flags)else 'NOT_CLEARED_NO_DEFINITE_DEFECT_BY_AUTOMATED_CHECKS','flags':flags,'citationChecks':referenceChecks,'teachingChecks':teaching,'modelReading':{'status':'NOT_INDIVIDUALLY_READ','scope':None},'legalCorrectness':'NOT_CONFIRMED','unresolved':gaps,'humanDualApproval':False})
(p/'lecoqa.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in rows))
(p/'citation-differences.json').write_text(json.dumps(mismatches,ensure_ascii=False,indent=2)+'\n')
cs=collections.Counter(r['status']for q in rows for r in q['citationChecks'])
s={'total':len(rows),'statuses':dict(collections.Counter(q['status']for q in rows)),'references':dict(cs),'questionsWithAnyOfficialComparison':sum(any(r['status']!='NOT_CHECKED_OFFICIAL_TEXT_UNAVAILABLE'for r in q['citationChecks'])for q in rows),'questionsWithAllReferenceExcerptsMatched':sum(bool(q['citationChecks'])and all(r['status'].startswith('MATCH_')for r in q['citationChecks'])for q in rows),'textDifferences':len(mismatches),'differenceQuestionIds':sorted(set(m['id']for m in mismatches)),'flags':dict(collections.Counter(f['code']for q in rows for f in q['flags']))}
(p/'lecoqa-summary.json').write_text(json.dumps(s,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in s.items()if k!='differenceQuestionIds'},ensure_ascii=False,indent=2))
for m in mismatches[:45]:print(m['id'],m['law'],m['article'],m['stem'][:60],m['datasetText'][:65].replace('\n',' '))
