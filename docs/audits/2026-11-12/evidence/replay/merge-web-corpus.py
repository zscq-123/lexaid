from pathlib import Path
import re,json,hashlib,datetime
p=Path('/tmp/lexaid-full-review/official')
pat=re.compile(r'(?:^|\n)\s*(第[零〇一二三四五六七八九十百千万\d]+条(?:之[一二三四五六七八九十\d]+)?)[\s\u3000]*(.*?)(?=(?:\n\s*第[零〇一二三四五六七八九十百千万\d]+条)|\Z)',re.S)
for law in ['劳动合同法','公司法','刑法','治安管理处罚法','行政处罚法','行政许可法','著作权法','民事诉讼法','保险法','物业管理条例','旅游法','合伙企业法','政府信息公开条例','最高人民法院关于审理建设工程施工合同纠纷案件适用法律问题的解释（一）']:
 fs=list(p.glob('web-'+law+'-*.txt'))
 if law=='劳动合同法':fs+=[p/'labor-web-1.txt']
 if not fs:continue
 lines={int(n):t for f in fs for n,t in re.findall(r'^L(\d+): (.*)$',f.read_text(),re.M)}
 missing=[i for i in range(min(lines),max(lines)+1)if i not in lines]
 text='\n'.join(t for _,t in sorted(lines.items()))
 articles={m[1]:m[2].strip() for m in pat.finditer(text)}
 out=json.loads((p/(law+'.json')).read_text());old=out.get('directFetchAttempt') or {k:v for k,v in out.items()if k!='articles'}
 first=fs[0].read_text().splitlines()[0];url=re.search(r'\((https?://[^)]+)\)',first)
 out.update({'status':'downloaded_via_web_tool','fetchMethod':'official page rendered text via web tool, merged numbered lines','directFetchAttempt':old,'url':url[1]if url else old['url'],'retrievedAt':'2026-11-12','articles':articles,'articleCount':len(articles),'textSha256':hashlib.sha256(text.encode()).hexdigest(),'coverage':{'firstLine':min(lines),'lastLine':max(lines),'missingLines':missing},'rawSegments':[f.name for f in fs]})
 (p/(law+'.json')).write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
 (p/(law+'.txt')).write_text(text)
 print(law,len(articles),'missinglines',len(missing),'firstarticle',next(iter(articles),'none'))
