"""Reproduce explicit AI semantic annotations and evidence binding; not an automated legal verdict engine.
Run after record-semantic.py has captured the actually read 513 rows. No repo or DB writes.
"""
import json,pathlib,hashlib,collections
p=pathlib.Path('/tmp/lexaid-full-review')
out=p/'lecoqa-semantic-legal.jsonl'
rows=[json.loads(l) for l in (p/'lecoqa-semantic-legal-reading-records.jsonl').read_text().splitlines()]
qs={q['id']:q for q in map(json.loads,(p/'lecoqa-source.jsonl').read_text().splitlines())}
# Each entry was assigned after complete model reading. D=legal substance; B=basis; E=editorial; V=version; S=structural.
# Official source aliases and article numbers are explicit, independently recheckable references.
spec='''
10025087|D|C:447|将合法占有主体改为债权人。
10025089|D|C:425|明确动产质押转移占有；不要与抵押混同。
10025105|D|C:453|改为依约确定履行期，未约定或不明一般给予六十日以上，并保留鲜活易腐例外。
10025113|D|C:453|删除约定期不得少于两个月，正确区分约定期与法定六十日宽限。
10025114|E|C:450|校对不可分物与可分物案例，删除乱码，统一前后结论。
10025115|E||恢复被乱码破坏的权利名称和判例文本，无法恢复则保留隔离。
10025126|E|G:44|题干改为债务人请求债权人返还留置财产，并保留该解释条件。
10025129|S||补写可评分参考答案后重新复核；不得用空答案进入练习。
10025141|D|P:17,264|将超过16岁改已满16周岁，并补盗窃入罪条件和刑事责任能力。
10025154|D|P:265|修复废法、数额教导等错字，并把已获利益改为以牟利为目的。
10025163|E||补全自首、坦白、立功及退赔的条件，不能保留未完成的分号片段。
10025178|D|C:278|区分双三分之二参与和参与表决者双四分之三同意，并单独核地方加装程序。
10025183|B|C:291,293|改引必要通行第291条，核对题设通道必要性。
10025186|B|C:236,293|按侵占庭院请求排除妨害补物权依据，不以日照条支持侵占问题。
10025189|B|C:291,293|将采光日照依据替换为必要通行规则，补具体通道事实判断。
10025194|D|C:279|补遵法守规约及利害关系业主一致同意，删除无明显影响即可住改商。
10025201|D|C:724|补非承租人原因且致无法使用的解除条件。
10025228|D|C:1128|兄弟姐妹先死亡的代位人限其子女，与子女支系直系晚辈区分。
10025229|E|C:1127|正文1027改1127。
10025240|D|C:1138|危急消除且能以书面或者录音录像立遗嘱时，口头遗嘱无效。
10025244|D|C:48|补意外事件宣告死亡以事件发生日为死亡日期的例外。
10025250|D|C:1158|遗赠扶养协议相对方改为继承人以外的组织或者个人。
10025252|E|C:1121|继承人死亡改为被继承人死亡。
10025253|D|C:48|宣告死亡日期区分判决作出日与意外事件发生日。
10025260|E|C:1062,1063|正文1162、1163改为1062、1063。
10025261|E|C:1145|未推选时共同担任遗产管理人，勿写遗嘱执行人。
10025267|D|C:679|提供借款者改贷款人，自然人借款交付时成立而非仅签字。
10025268|D|J:1;C:680|先判断放贷主体，持牌金融机构相关业务不适用民间借贷解释。
10025270|V|J:25,31|删除无时间限制的银行同类利率四倍表述，按合同成立及案件时间应用LPR与过渡规则。
10025275|D|X:1,4,5|将信用逾期后果与司法执行限消分开，删除连续三个月自动限高的断言。
10025278|D|C:148,157|受欺诈担保须区分撤销权及撤销后的责任，不能直接写一概无效不担责。
10025281|E||恢复完整列举和研究出处或删除不能核对的17种、9种统计主张。
10025305|D|Y:10,24|先排除本人权利及合法授权，再区分复制和信息网络传播及法定例外。
10025317|D|Y:23|59年改50年；视听发表权自创作完成起算，其他财产权按首次发表规则。
10025319|D|Y:10|删除表演权主体不包括继受主体和特殊作品主体的绝对排除。
10025322|E||补全录音制品播放分支及其法律依据，或删去未完成句。
10025332|D|C:1197|补知道或者应当知道且未采取必要措施的网络服务者责任。
10025335|D|Y:53|补同时损害公共利益和可并处罚款等条件，不把经营额门槛当自动处罚。
10025372|B|C:388,754|用第754条及未能重新订立买卖合同条件解释解除，勿仅引担保从属性。
10025373|E||恢复“使受让人产生更大的”之后的完整句并审查善意标准。
10025379|D|A:19|补法律法规授权的管理公共事务组织可在授权范围实施处罚。
10025381|D|A:29|改为不得对同一违法行为给予两次以上罚款，勿排除依法并处不同处罚。
10025388|D|L:38|删除许可一概自由裁量的说法，分别讨论羁束条件及裁量空间。
10025401|D|L:46,47|补申请人利害关系人依第47条请求听证及期限费用规则。
10025414|E|L:68|题干改为责令停止建造安装使用并立即改正。
10025416|D|L:72|一般责令改正，情节严重才依法处分，补完整层次。
10025423|D|C:505,951|删除无统一行纪资质即当然无效，按具体业务许可与效力规则判断。
10025433|D|P:224,266|先核不同独立行为与同一行为竞合，不得仅因两种描述就数罪并罚。
10025435|E|P:224|修复非法占有目的处乱码并保留构成条件。
10025437|D|P:224|补非法占有、骗取财物及数额较大等条件，避免诱骗行为即完成犯罪。
10025438|E||删除题干未给的空头支票、先履行及数额特别巨大事实，改条件性说明。
10025447|D|P:175之一,193|分清骗取贷款与贷款诈骗，分别审查重大损失和非法占有目的。
10025448|V|P:193;T:45|不得把二万元无时点地当统一定罪线；列行为时间、非法占有及适用解释，2022五万元仅注明立案追诉范围。
10025452|D|P:196|补冒用信用卡诈骗数额较大等构成前提，不以任何使用都已构罪。
10025455|V|P:175之一;T:22|2010规则只能作历史材料；现行追诉标准核重大直接损失，不能仅多次骗贷即追责。
10025459|E|P:198|删除擅加的数额巨大，补主体身份和条件性犯罪判断。
10025463|D|P:167,397|统一为签订履行合同失职被骗罪的条件分析，勿混写玩忽职守罪。
10025471|D|P:25,210|补共同诈骗故意及参与时点，删除与题无关的增值税发票第210条。
10025472|B|P:177之一,210|删除第210条无关引用，另核电诈意见的关联犯罪竞合规则。
10025473|D|P:167|补国有单位直接负责主管人员及重大损失条件，普通财务劳动者不得直接套罪。
10025477|D|C:490|补履行主要义务并被接受可补正书面形式，不能一概认定不成立。
10025489|D|C:663,1067|以证明的附义务约定或法定扶养义务不履行为判断基础，不能以离婚自动推定。
10025492|B|C:1062,1064|用财产处分和公司股权规则重新论证，债务第1064条不能作唯一依据。
10025493|E|C:538|改为债务人无偿处分财产等影响债权实现，纠正债权债务主体。
10025508|B|C:816,820|区分旅客原因与承运人不能正常运输，补当时疫情退改规则，不以相近班次当然剥夺退票。
10025513|D|C:822,823|按已尽必要救助和损害因果判断，拨打急救电话不是自动免责。
10025522|D|C:831|改为未异议属初步证据，允许依反证及检验期限判断货损赔偿。
10025526|B|C:835|分清因不可抗力灭失的运费与违约货损赔偿，并直接回答能否拒付。
10025528|D|C:450,836,837|区分欠运费留置与收货人不明拒收提存，不得混用制度。
10025532|B|C:842|诉讼时效须另查专门法及适用规则，勿直接由责任限额条推导。
10025533|V|H:2,43,310|注明2026年5月1日新法纳入国内海上货运，并按时间效力规则处理历史合同。
10025537|V|F:废止|删除作为现行权利的铁路强制意外险表述，区分历史事故和自愿商业险。
10025539|B|C:824,832|一般货物污染改引货物运输规则，旅客随身行李不是该题直接依据。
10025545|B||删除企业分立合并第41条与旅客损害的错配，补消费者人身损害及请求权竞合依据。
10025546|D|C:823|不得仅踏空就推定重大过失或必定部分责任，按承运人证明及损害原因判断。
10025549|D|C:823,1186|删除无依据的无过错必补偿及题干未给机场过失事实，依法审查救助过错。
10025554|E||统一答案与解析的铁路票价版本，竞争领域市场调节规则不能与旧政府全定价并存。
10025558|D|C:668,679|把实践性及非书面例外限定自然人之间，不扩张到所有民间借贷。
10025568|D|C:490,668|删除非书面当然无效，分别讨论书面要求、成立补正及实体无效事由。
10025571|B|C:925,926|已知代理关系改引925条，未知代理关系才另论926条介入选择权。
10025575|D|C:1064|超日常生活个人负债由债权人证明共同生活经营或共同意思，纠正后段举证责任。
10025590|D|C:1240|减责过错改为重大过失。
10025594|D|C:1244|仅法律规定限额时适用，故意或者重大过失均排除限额保护。
'''
alias={'O':'公司法','U':'民事诉讼法','Z':'治安管理处罚法','C':'民法典','P':'刑法','A':'行政处罚法','L':'行政许可法','Y':'著作权法','J':'最高人民法院关于审理民间借贷案件适用法律若干问题的规定','G':'最高人民法院关于适用《民法典》有关担保制度的解释'}
corpora={a:json.load(open(p/'official'/f'{name}.json')) for a,name in alias.items()}
def cn(n):
 d='零一二三四五六七八九';n=int(n)
 if n<10:return d[n]
 if n<20:return '十'+(d[n%10] if n%10 else '')
 if n<100:return d[n//10]+'十'+(d[n%10] if n%10 else '')
 if n<1000:
  q,r=divmod(n,100);return d[q]+'百'+(('零'+cn(r)) if 0<r<10 else ('一'+cn(r) if 10<=r<20 else cn(r)) if r else '')
 q,r=divmod(n,1000);return d[q]+'千'+(('零'+cn(r)) if 0<r<100 else ('一'+cn(r) if 10<=r<20 else cn(r)) if r else '')
def article_label(n):
 parts=n.split('之');return '第'+cn(parts[0])+'条'+('之'+parts[1] if len(parts)>1 else '')
extras={
 'X':{'law':'最高人民法院关于限制被执行人高消费及有关消费的若干规定','url':'https://www.court.gov.cn/fabu/xiangqing/15046.html','promulgated':'2015-07-06','effective':'2015-07-22','evidenceFile':'semantic-extra-web-results.json','coverage':'official search-rendered excerpts: first, fourth and fifth articles; not full-law certification'},
 'T':{'law':'最高人民检察院公安部关于公安机关管辖的刑事案件立案追诉标准的规定（二）','url':'https://www.spp.gov.cn/xwfbh/wsfbt/202204/t20220429_555906.shtml','promulgated':'2022-04-06','effective':'2022-05-15','evidenceFile':'semantic-prosecution-excerpts.txt','coverage':'official search-rendered excerpts; direct access 403; filing/prosecution criteria only, not universal historical conviction/sentencing thresholds'},
 'H':{'law':'中华人民共和国海商法','url':'https://www.js.msa.gov.cn/art/2026/1/5/art_842_2101.html','promulgated':'2025-10-28','effective':'2026-05-01','evidenceFile':'semantic-extra-web-results.json','coverage':'official browser rendered articles 2,43,310; partial saved output, not full-corpus comparison'},
 'F':{'law':'国务院关于修改和废止部分行政法规的决定（国务院令628号）','url':'https://www.shanwei.gov.cn/gdsw110/zwgk/0200/0202/content/post_219244.html','promulgated':'2012-11-09','effective':'2013-01-01','evidenceFile':'semantic-extra-web-results.json','coverage':'official search-rendered entire decision; also railway implementing notice verified at https://www.12306.cn/%2Fmormhweb/kygfwj/gfxwj/201301/t20130105_1830.html'}
}
lookup={}
for line in spec.strip().splitlines():
 i,domain,refs,fix=line.split('|',3);lookup[int(i)]=(dict(D='LEGAL_SUBSTANCE',B='LEGAL_BASIS',E='EDITORIAL',V='REFERENCE_VERSION',S='STRUCTURAL')[domain],refs,fix)
evidence=[];used=set();missing=[]
for r in rows:
 if r['decision']!='CONFIRMED_DEFECT':
  assert not r['confirmedIssues'];continue
 i=r['id'];q=qs[i]
 if i in lookup:
  domain,refs,fix=lookup[i];official=[];proof=[]
  for group in refs.split(';') if refs else []:
   a,nums=group.split(':',1)
   if a in corpora:
    c=corpora[a];used.add(a)
    for n in nums.split(','):
     label=article_label(n);assert label in c['articles'],(i,label)
     proof.append({'law':c['law'],'article':label,'text':c['articles'][label],'url':c['url'],'promulgated':c['promulgated'],'effective':c['effective'],'retrievedAt':c.get('retrievedAt'),'corpusSha256':hashlib.sha256((p/'official'/f"{c['law']}.json").read_bytes()).hexdigest()})
    official.append({'law':c['law'],'url':c['url'],'articles':[article_label(n) for n in nums.split(',')],'promulgated':c['promulgated'],'effective':c['effective']})
   else:
    c=extras[a];used.add(a);official.append(dict(c,articles=nums.split(','),retrievedAt='2026-11-12'));proof.append(dict(c,articles=nums.split(',')))
  item={'id':i,'contentHash':q['contentHash'],'code':f'FULLTEXT_REVIEW_{i}','domain':domain,'level':'confirmed','evidence':r['reason'],'conclusion':r['reason'],'minimumCorrection':fix,'officialUrls':list(dict.fromkeys(c['url'] for c in official)),'officialSources':official,'evidenceFile':str(p/'lecoqa-semantic-legal-evidence.jsonl'),'evidenceRecordId':i,'localEvidence':{'file':str(p/'lecoqa-source.jsonl'),'recordId':i,'fields':['stem','answer','analysis'],'snapshotSha256':r['sourceSnapshotSha256']},'verifiedScope':'仅确认列明缺陷；依据错配/乱码不等于整题实质法律错误，未认证整题全部法律推理。','verifiedBy':'AI-assisted complete-field reading plus narrow official-source comparison','humanDualApproval':False}
  # Preserve explicit earlier findings, avoiding duplicates upon rerun.
  r['confirmedIssues']=[z for z in r['confirmedIssues'] if not z.get('code','').startswith('FULLTEXT_REVIEW_')]+[item]
  evidence.append({'id':i,'contentHash':q['contentHash'],'sourceRecord':q,'issue':item,'officialArticleEvidence':proof})
 else:
  assert r['confirmedIssues'],i
  # Older findings remain narrow; enrich known official versions by URL without changing their conclusions.
  for z in r['confirmedIssues']:
   sources=[]
   for u in z.get('officialUrls',[]):
    for a,c in corpora.items():
     if c['url']==u:sources.append({k:c[k] for k in ['law','url','promulgated','effective']});used.add(a)
   z['officialSources']=sources
   z['localEvidence']={'file':str(p/'lecoqa-source.jsonl'),'recordId':i,'contentHash':q['contentHash']}
   if any('5228.html' in u for u in z.get('officialUrls',[])):
    z['officialSources'].append({'law':'最高人民法院、最高人民检察院关于办理盗窃刑事案件适用法律若干问题的解释','url':'https://www.court.gov.cn/fabu/xiangqing/5228.html?sid_for_share=95263_4','promulgated':'2013-04-02','effective':'2013-04-04','evidenceFile':str(p/'manual-evidence/盗窃解释.txt'),'retrievedAt':'2026-11-12'})
  evidence.append({'id':i,'contentHash':q['contentHash'],'sourceRecord':q,'earlierConfirmedIssues':r['confirmedIssues']})
assert len(rows)==513 and [r['id'] for r in rows]==list(range(10025085,10025598))
for r in rows:
 assert r['contentHash']==qs[r['id']]['contentHash']
 assert r['fieldsRead']==['stem','options','answer','analysis']
 assert r['legalReasoningVerified'] is False and r['reviewerKind']=='AI'
 assert (r['decision']=='CONFIRMED_DEFECT')==bool(r['confirmedIssues'])
 r['semanticRead']=True
 r['semanticReadScope']='FULL_STEM_OPTIONS_ANSWER_ANALYSIS'
 r['reasoningVerified']=False
 r['disposition']='PRESERVE_SOURCE_PENDING_INDEPENDENT_TEACHING_AND_LEGAL_REVIEW'
 r['decisionMeaning']='Specific defect identified' if r['decision']=='CONFIRMED_DEFECT' else 'Unresolved issue needs more verification' if r['decision']=='REQUIRES_MORE_VERIFICATION' else 'No specific defect identified in this AI reading; not a correctness certification'
out.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
(p/'lecoqa-semantic-legal-evidence.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in evidence))
manifest=[]
for a in sorted(used):
 if a in corpora:
  c=corpora[a];f=p/'official'/f"{c['law']}.json";manifest.append({**{k:c.get(k) for k in ['law','url','promulgated','effective','retrievedAt','coverage','articleCount','textSha256']},'file':str(f),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'usage':'Only the explicit articles listed per finding were used as narrow legal proof.'})
 else:
  c=extras[a];f=p/c['evidenceFile'];manifest.append(dict(c,retrievedAt='2026-11-12',file=str(f),sha256=hashlib.sha256(f.read_bytes()).hexdigest(),usage='Partial browser/search evidence only; no claim of complete official corpus.'))
(p/'lecoqa-semantic-legal-official-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
dec=collections.Counter(r['decision'] for r in rows);dom=collections.defaultdict(set)
for r in rows:
 for z in r['confirmedIssues']:dom[z['domain']].add(r['id'])
summary={'source':'LeCoQA','shard':'sorted indices [1030,1543)','total':513,'ids':[10025085,10025597],'actualFullFieldReading':513,'reviewerKind':'AI','legalReasoningVerifiedCount':0,'reasoningVerifiedMeaning':'No question certified fully correct across all propositions, exceptions, source versions and application facts.','decisionCounts':dict(dec),'confirmedQuestionIds':[r['id'] for r in rows if r['decision']=='CONFIRMED_DEFECT'],'confirmedQuestionCountsByDomain':{k:len(v) for k,v in sorted(dom.items())},'confirmedIdsByDomain':{k:sorted(v) for k,v in sorted(dom.items())},'countingNote':'Domains can overlap per question. Editorial, structural, citation basis and reference version defects are not all substantive legal error. NO_SPECIFIC_DEFECT is not a correctness label.','fullQuestionCountAllShards':1543,'thisShardOnly':True,'sourceSnapshotSha256':rows[0]['sourceSnapshotSha256'],'jsonlSha256':hashlib.sha256(out.read_bytes()).hexdigest(),'disposition':'Preserve original imported content. No repository or database mutation; require independent source/current-law/teaching review before activation.','reproduction':'python3 /tmp/lexaid-full-review/finalize-semantic-legal.py rebinds explicit recorded judgments and validates artifacts. Semantic judgments require real reading; this script does not automate new judgments.','batchReadEvidence':{'directory':str(p/'semantic-batches'),'batches':59,'complete':True},'remainingVerificationIds':[r['id'] for r in rows if r['decision']=='REQUIRES_MORE_VERIFICATION']}
(p/'lecoqa-semantic-legal-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:summary[k] for k in ['total','actualFullFieldReading','legalReasoningVerifiedCount','decisionCounts','confirmedQuestionCountsByDomain','jsonlSha256']},ensure_ascii=False,indent=2))

report=["LeCoQA 最后 513 题全文复核交付（AI，2026-11-12）", "", "范围为按 id 排序 [1030,1543)，ID 10025085 至 10025597。513 题均实际读取题干、options（SHORT 均为 null）、完整答案和完整解析，59 个未截断文本批次已保存。", "", f"逐题结论：确认具体缺陷 {dec['CONFIRMED_DEFECT']} 题，仍需核实 {dec['REQUIRES_MORE_VERIFICATION']} 题，本轮未识别具体缺陷 {dec['NO_SPECIFIC_DEFECT_IDENTIFIED']} 题。整题法律推理全部核验为 0；legalReasoningVerified 与 reasoningVerified 均为 false。未发现具体缺陷并不等于题目法律正确，也不构成上线或双人审核签字。", "", "确认缺陷题按领域去重计数（领域可交叉）："+'；'.join(k+' '+str(len(v))+' 题' for k,v in sorted(dom.items()))+"。结构/乱码/错引/版本问题不得与实质命题错误混成一个法律错误率。", "", "重点：10025178 加装电梯表决门槛；10025194 住改商一致同意；10025228 兄弟姐妹代位范围；10025317 视听作品59年及发表权起点；10025381 禁止重复罚款被扩大；10025522 收货未异议只是初步证据；10025528 留置与提存混同；10025575 夫妻债务举证责任反写；10025594 高危限额遗漏重大过失。", "", "版本补证：10025533 旧海商法排除国内海运已与2026年5月1日新法第43条不符；10025537 铁路旅客强制意外险已在2013年废止。10025448/10025455 旧贷款标准仅作为有时间界限的历史材料，2022五万元贷款诈骗追诉线不可替代全部历史定罪量刑线。", "", "证据：lecoqa-semantic-legal.jsonl 为逐题结果；lecoqa-semantic-legal-evidence.jsonl 保存确认题完整原文与窄范围官方条文；lecoqa-semantic-legal-official-manifest.json 保存 URL、版本、抓取日期、文件SHA及全文/摘录范围。SPP 部分页直开403时只使用官方搜索摘录，明示该局限。", "", "方法：逐题实际阅读后明确记录命题级理由；用已抓取官方法条检查遗漏要件、例外、主体、日期、错误关联及内部矛盾。程序负责完整性/哈希/引用证据绑定，不替代法律论证。保守降级了缺少足够依据的结论；不批量改写原题。原始导入内容、数据库、repo均未变更。", "", "基线全量程序审计 lecoqa-summary.json 的 1543 条、2717 引用及423待查引用为较早独立快照；本交付为其后的513题语义分片，二者不能简单相加为已验证题数。父代理负责与另外两片合并，当前文件不能单独声称1543题由本代理实读。", "", "复跑：python3 /tmp/lexaid-full-review/finalize-semantic-legal.py。固定阅读记录为 lecoqa-semantic-legal-reading-records.jsonl，原始题库为 lecoqa-source.jsonl；重跑只重建证据及检查，不自动产生新语义结论。", "", "确认问题逐条索引：", "", "| ID | 类别 | 确认问题 | 最小修正 |", "|---|---|---|---|"]
for r in rows:
 for z in r['confirmedIssues']:
  report.append('| '+str(r['id'])+' | '+z['domain']+' | '+z['conclusion'].replace('|','/').replace('\n',' ')+' | '+z.get('minimumCorrection','需复核').replace('|','/').replace('\n',' ')+' |')
(p/'lecoqa-semantic-legal-report.md').write_text('\n'.join(report)+'\n')
