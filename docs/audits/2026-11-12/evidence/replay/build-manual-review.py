from pathlib import Path
import json,re,hashlib
p=Path('/tmp/lexaid-full-review')
qs={q['id']:q for q in map(json.loads,(p/'lecoqa-source.jsonl').read_text().splitlines())}
co={j['law']:j for j in [json.loads(f.read_text())for f in (p/'official').glob('*.json')]}
def url(law):return co[law]['url']
findings=[]
def add(i,code,domain,level,evidence,conclusion,correction,urls):
 findings.append(dict(id=i,contentHash=qs[i]['contentHash'],code=code,domain=domain,level=level,evidence=evidence,conclusion=conclusion,minimumCorrection=correction,officialUrls=urls,verifiedScope='仅核验此条列明的问题；不代表整题全部论证通过',verifiedBy='AI-assisted official-source review',humanDualApproval=False))
# Compare dated law versions, keeping substantive correctness separate from quotation/number changes.
for m in json.loads((p/'citation-differences.json').read_text()):
 add(m['id'],'OUTDATED_OR_DIFFERENT_REFERENCE_'+m['law']+'_'+m['article'],'REFERENCE_VERSION','confirmed',m['law']+m['article']+'：'+m['datasetText'][:180],'引文与截至2026-11-12所取现行版本的同名同条原文不同；参见citation-differences.json完整前后文本。旧咨询发生时间未知，不能反推历史答案当时违法。','先标明适用日期并核对新旧对应条号；涉及规则变更时重写论证，禁止仅批量替换条号。',[url(m['law'])])
add(10024085,'URBAN_RESIDENCE_NOT_FORFEITURE','LEGAL_SUBSTANCE','confirmed','农村居民户口转为非农业户口的，其已丧失了农村土地承包经营权','答案与现行农村土地承包法27条及本题解析直接相反。','承包期内不得因农户进城落户而收回承包地；转让或交回应遵循依法自愿有偿原则。',[url('农村土地承包法')])
add(10024089,'URBAN_RESIDENCE_EXCEPTION_WRONG','LEGAL_SUBSTANCE','confirmed','发包方收回承包地情形只有一种，即承包期内，承包方全家迁入城镇，转为非农业户口','考入公务员不当然收回的主结论不因此推翻，但这一例外与现行27条相反。','删除该例外；保留不因家庭成员考入公务员而当然收回的结论，并按现行27条说明进城落户保护。',[url('农村土地承包法')])
add(10024165,'ENTITY_NAME_CAN_BE_TRANSFERRED','LEGAL_SUBSTANCE','confirmed','企业名称为人格权，不得转让','与民法典1013、1016条允许依法转让名称的规定直接相反。','法人、非法人组织可以依法转让或者许可他人使用自己的名称，并依法办理登记手续。其余商号比较仍需单独复核。',[url('民法典')])
add(10024323,'FAULT_NOT_AUTOMATIC_HALF_SHARE','LEGAL_SUBSTANCE','confirmed','双方均有过错，应平均负担该部分损失','民法典592条规定各自承担相应责任，不能从双方有过错推出必然平均分担；所引898条是贵重物未声明，与本题自然灾害减损问题不相称。','按不可抗力实际影响、减损义务、过错和因果关系确定相应责任，删除当然平均分担；核对180、590、591、592、897条。',[url('民法典')])
add(10024548,'TRADEMARK_APPLICANT_OVERGENERALIZED','LEGAL_SUBSTANCE','confirmed','任何自然人都可以在我国申请商标，没有生产经营资格的限制','现行商标法4条和国家知识产权局对国内自然人申请的主体资格材料要求不支持无条件概括；不能混同国内与境外自然人。','改为自然人在生产经营活动中确有使用需要时可以依法申请；国内自然人应按办理指南提交相应经营主体资格证明，具体身份分别说明。',['https://www.cnipa.gov.cn/jact/front/mailpubdetail.do?sysid=13&transactId=483024',url('商标法')])
add(10024627,'LAND_DISPUTE_ADMINISTRATIVE_LITIGATION_OMITTED','LEGAL_SUBSTANCE','confirmed','不能找法院。只有您的合法权利被侵犯时，法院才有权做出裁决','不能直接提起一般权属民事诉讼与不能诉讼不同；土地管理法14明确政府处理决定后30日内可以起诉。','权属争议应先协商或由有权政府处理；不服处理决定的，可自接到通知之日起30日内向人民法院起诉。',[url('土地管理法')])
add(10024758,'PRINCIPLES_NOT_AUTOMATIC_INVALIDITY','LEGAL_SUBSTANCE','confirmed','违反自愿、公平、诚实信用原则的技术合同应属当然无效、自始无效','民法典147至151条规定重大误解、欺诈、胁迫、显失公平等撤销机制；不能把违反原则一概等同当然无效。850条特定无效事由应单独适用。','删除这一概括，按具体事实区分无效、可撤销及违约责任，再说明技术合同850条的特定情形。',[url('民法典')])
for i in [10025006,10025274]:
 add(i,'PRE_20200820_LOAN_TRANSITION_RATE','LEGAL_SUBSTANCE','confirmed','将2020年8月20日前成立借款的后段利息保护上限写为合同成立时4倍LPR','2020年底修正的民间借贷解释31条规定，在其列明的过渡情形下，后段适用起诉时的利率保护标准；本题混用2020年8月版本和2020年底版本。','对2020年8月20日之后新受理的一审案件、合同成立于该日之前的，依法分段：前段按当事人请求适用当时司法解释，后段按起诉时规定的保护标准。须标明案件受理时间。',[url('最高人民法院关于审理民间借贷案件适用法律若干问题的规定')])
add(10025143,'PICKPOCKET_LOCATION_ELEMENT_OMITTED','LEGAL_SUBSTANCE','confirmed','扒窃…是指偷盗他人随身携带的财物，无论…价值几何，均构成盗窃罪','盗窃刑事解释3条的扒窃定义包括公共场所或者公共交通工具这一场所要件；答案缺漏。取消数额门槛也不等于无需其他犯罪成立条件。','补充在公共场所或者公共交通工具上盗窃他人随身携带财物；说明不以数额较大为必要，但仍需符合犯罪成立及责任条件。',['https://www.court.gov.cn/fabu/xiangqing/5228.html?sid_for_share=95263_4',url('刑法')])
add(10024973,'DEGREE_PROCEDURE_NOW_EXPRESSLY_REGULATED','LEGAL_SUBSTANCE','confirmed','目前关于授予学士学位的具体程序并无明确的法律规定','2025年施行学位法39、41条已明确拟不授予/撤销前的告知陈述申辩及复核救济；不能用该概括免除程序审查。','按学位法的条件、程序和救济分别作答；考试作弊是否足以不授予及程序是否合法须结合事实，不能预设程序瑕疵一概不违法。',['https://www.moe.gov.cn/jyb_sjzl/sjzl_zcfg/zcfg_jyfl/202404/t20240426_1127804.html'])
add(10024973,'DEGREE_REGULATIONS_REPEALED','REFERENCE_VERSION','confirmed','学位条例第二条','学位法45条规定2025-01-01施行并同时废止学位条例；题目无历史适用日期。','将适用版本改为学位法并重写相关程序论证；原始导入内容保留。',['https://www.moe.gov.cn/jyb_sjzl/sjzl_zcfg/zcfg_jyfl/202404/t20240426_1127804.html'])
add(10025073,'NOISE_LAW_REPEALED','REFERENCE_VERSION','confirmed','环境噪声污染防治法第六十一条','旧环境噪声污染防治法已被2022年噪声法替代；截至本次日期还须考虑2026-08-15施行的生态环境法典，不能直接把2022文本当最终现行法。','标为过时依据并整体重审；开发商必然回购也不能由该引文直接推出，应补足合同解除/修复/赔偿等适用条件。',['https://www.mee.gov.cn/xxgk2018/xxgk/xxgk03/202202/t20220222_969609.html','https://ecro.mee.gov.cn/fgbz/fl/202604/t20260416_1148993.html'])
add(10025442,'ILLEGAL_FUNDRAISING_OLD_ARTICLE','REFERENCE_VERSION','risk','非法集资刑事解释第四条','2022修正已重排非法占有目的条款至7条；已检索最高法修正公告，逐条官方全文尚未纳入本次自动语料。','补录2022官方全文并确认4→7映射及内容，补明行为时间。',['https://www.court.gov.cn/zixun/xiangqing/346881.html','https://www.court.gov.cn/zixun/xiangqing/346891.html'])
add(10025448,'LOAN_FRAUD_THRESHOLD_AND_ELEMENTS','LEGAL_SUBSTANCE','risk','骗取贷款数额在二万以上的，构成贷款诈骗罪','2022最高检、公安部立案追诉标准45条为5万元且明确非法占有目的。原题2万元及必然构成犯罪的概括未标年代，也未区分立案追诉和定罪量刑；此处不把5万元擅自改成所有历史案件的定罪标准。','补明行为/适法时间、非法占有目的、立案追诉与定罪量刑的区别；由刑事专业复核后重写，禁止简单把2改5即放行。',['https://www.spp.gov.cn/xwfbh/wsfbt/202204/t20220429_555906.shtml',url('刑法')])
add(10025574,'GUARANTEE_EXCEPTION_OVERBROAD','LEGAL_SUBSTANCE','confirmed','公司为其直接或者间接控制的公司…相互担保等商业合作关系…合同有效','现行民法典担保制度解释8条所列免决议事由不支持将所有控制关系、相互担保都概括为当然有效；公司法引用还为旧16条。','按现行公司法15条及担保制度解释7、8、9条分别审查担保类型、决议、善意及法定例外；删除相互担保即当然有效的概括。',[url('最高人民法院关于适用《民法典》有关担保制度的解释'),url('公司法')])
for i,code,evid,concl,corr,law in [
 (10025062,'PENALTY_NOT_PRIVATE_FINE','治安管理处罚法17、19、20条','引用共同违法及从轻从重处罚条文，不能直接证明物业公司是否有权罚款；现行条文还已变。','核对行政处罚实施主体及物业合同规则，用有对应关系的依据重写解析。','行政处罚法'),
 (10025395,'PENALTY_NOT_LICENSING_BASIS','治安管理处罚法第十条','治安处罚种类不能充当公安行政许可的设定/实施依据；行政许可法24为委托实施，也不枚举具体许可事项。','补具体许可法律依据和主管机关权限；删除以处罚种类证明许可权限的关联。','行政许可法')]:add(i,code,'LEGAL_BASIS','confirmed',evid,concl,corr,[url(law)])
for i,code,evid,corr,law in [
 (10024586,'LICENSE_TRANSFER_WORD_MIXUP','不影响转让合同的效力','将许可语境中的转让合同改为许可合同；区分商标转让核准与许可备案。','商标法'),
 (10024801,'MORTGAGOR_MORTGAGEE_WORD_MIXUP','抵押人不得就抵押财产行使抵押权','此处主体应为抵押权人；按相邻句及担保解释54核对。','民法典'),
 (10025108,'WRONG_GUARANTEE_INTERPRETATION_TITLE','现《担保法司法解释》第62','改为民法典有关担保制度解释第62条；旧108条只能明确标作历史比较。','最高人民法院关于适用《民法典》有关担保制度的解释'),
 (10024930,'GARBLED_SCHOOL_TEXT','许欸笑傲如果没有尽到职责','修复乱码为学校，并补欺凌防控、报告及教育管理责任依据。','民法典'),
 (10024637,'GARBLED_COMPENSATION_TEXT','政府应当向您政府土地补偿费…种植了你作为','修正支付、农作物等文字，区分土地补偿费、安置补助费及费用分配。','土地管理法'),
 (10024638,'GARBLED_EXPROPRIATION_TEXT','及时政府被没有处置','修正即使政府并没有处置；另补征收效力与闲置处置的直接依据。','土地管理法')]:add(i,code,'EDITORIAL','confirmed',evid,'原文存在可复现的用词/乱码问题。',corr,[url(law)])
# Risk dispositions: read the actual proposition; keyword hits alone do not establish an error.
risks={10024161:'商号侵权和损害存在过度概括及致命/知名疑似错字，需结合混淆可能性逐项重写。',10024169:'答案含重复和拼接式案例叙述；未核实引述案例原文，不能据此认定案例为伪造。',10024432:'超龄劳动关系应核对渐进式延迟退休及最新劳动争议规则，不能按年龄统一否认主体资格。',10024868:'题干问所有旅游经营者，答案只列旅行社设立条件；需缩窄题干或扩大区分类别。',10024869:'出境和边境旅游还须相应经营许可，教学答案宜明确例外。',10024887:'问题问保险公司责任，答案只给第三人诉讼地位，未解释责任范围。',10024405:'个人另行提诉与直接参加公益诉讼混在一起；须按公益诉讼解释9、16条区分。',10024930:'只有未成年人保护法30条不能充分支撑校园欺凌责任分析，应补39条及民法典1199至1201条。',10024978:'不能仅凭招生教师身份就一概认定刑法国家机关工作人员，需具体查明授权/委托和职务。',10025069:'题干询问全部责任而答案只给行政责任，未覆盖相应民事赔偿等。',10025073:'整改无效即开发商必然回购缺乏合同解除等条件论证。',10025342:'虚构租赁物无效与单方欺诈可撤销关系需专业解释，现有737条不足以支撑全部例外结论。',10025353:'融资租赁24%上限未列日期与业务性质，不能直接机械替换成民间借贷4倍LPR。',10025125:'旧担保法司法解释112被用作直接依据，需核对新法对应；基本结论不能仅因旧标题判错。',10025127:'旧担保法司法解释112被用作直接依据，需核对新法对应；基本结论不能仅因旧标题判错。',10024931:'低龄刑事责任分组需细化12至14周岁的严格条件与核准追诉，原概括不足作评分标准。'}
for i,msg in risks.items():add(i,'MANUAL_REVIEW_RISK','TEACHING_OR_APPLICATION','risk',qs[i]['stem'],msg,'保持待复核；补条件、适法时间和对应依据后重新审核。',[])
(p/'manual-findings.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2)+'\n')
readlog={}
for f in sorted((p/'read-batches').glob('*.txt')):
 if not (f.stem.isdigit() or f.stem.startswith('unclassified-')):continue
 for i in re.findall(r'^(\d{8}) ',f.read_text(),re.M):
  i=int(i);readlog[i]={'stem':True,'answer':True,'analysis':'FULL' if f.stem.startswith('unclassified-') else 'CITATION_HEADERS_ONLY','completeRecord':f.stem.startswith('unclassified-'),'evidenceFile':str(f.relative_to(p))}
prior=json.loads(Path('/tmp/lexaid-question-audit/sample-review.json').read_text())['reviewed']
fullids={q['id']for q in prior if q['id']in qs}|{10024700,10025007,10025022,10025199,10025578,10025006,10024758,10024801,10024586,10025108,10024165,10024323,10024548,10025143,10025129}
for i in fullids:readlog[i]={'stem':True,'answer':True,'analysis':'FULL','completeRecord':True,'evidenceFile':'lecoqa-source.jsonl','note':'Full fields rendered and read in model tool output; earlier fixed-sample record where applicable'}
# Carry prior independent point-check record as evidence, not a fabricated approval.
(p/'prior-lecoqa-sample.json').write_text(json.dumps([q for q in prior if q['id']in qs],ensure_ascii=False,indent=2)+'\n')
(p/'reading-log.json').write_text(json.dumps({str(i):v for i,v in sorted(readlog.items())},ensure_ascii=False,indent=2)+'\n')
print('manual findings',len(findings),'questions',len({f['id']for f in findings}),'read stem/answer',len(readlog),'full records',sum(v['completeRecord']for v in readlog.values()))
