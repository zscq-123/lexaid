# 题库审核记录（修复前快照）

日期：2026-11-11。只读数据库；未修改题库或登记审核人。

## 范围与口径

- 全量基础规则扫描 25,628 题，逐题 ID、来源、首个原因与内容指纹见 manifest-before.jsonl。该清单不是逐题法律正确性认证。
- 阅读了 30 道种子题；对其中 6 题、10 道原可练 LeCoQA 风险样本及 1 道已隔离 LeCoQA 对照题作官方来源定点核对，共 17 题。
- 这是按风险定向抽样，不能用问题比例估计全库错误率；未进行双人专家复核。

本清单固定使用审核开始时编译的 QuestionQuality.REASON，不使用后续修复规则。旧基线可练为 1,527；后续外部数据复核门禁的结果必须另行计算，不能混入此文件。

## 全量规则结果

- ELIGIBLE: 1527
- BENCHMARK_REVIEW: 9639
- UNCLASSIFIED: 3029
- SUBJECT_CONFLICT: 24
- MISSING_CONTENT: 15
- TASK_INSTRUCTION: 11391
- INVALID_CHOICES: 3

原可练 1,527 题 = 种子 30 + LeCoQA 1,497。LeCoQA 共 1,543 题，另外 44 题待分类、1 题答案为空、1 题被科目冲突规则拦截。

## 需要修复的三类问题

1. LeCoQA 未被教学复核门禁覆盖。定点核对发现旧版本依据、答案与解析内部不一致、担保例外泛化、引用不支持答案等多种问题。建议将 LeCoQA 与另两类导入数据同样纳入带内容指纹的教学复核，保留原文。它会暂时隔离此前放行的 1,497 题；这不是认定该 1,497 题全部错误。
2. SUBJECT_CONFLICT 只看题干合同/借款和少数否定关键词。24 条命中包含 23 道刑事案情的 LawBench 任务、1 道 LeCoQA 刑法167条问题。建议取消这一硬门禁或只作人工排查提示；基准教学复核门禁仍可保护练习入口。
3. 种子17将公平分担损失当作应选归责原则；种子27遗漏特定职责人员限制中的避免本人危险条件。可在保留原始审计证据后作最小修正。

旧法名关键词命中可练题31道，仅是排查线索：其中含合法历史案例讨论及仍援引旧名称的法律条文，不能一刀切判错。

## 固定样本逐题结果

|题号|审核前门禁|定点结论|问题/核对摘要|
|---|---|---|---|
|1|ELIGIBLE|NO_ISSUE_FOUND_IN_CHECKED_POINT|选项A与刑法20条第2款对应；本次核对防卫过当的法律后果。 依据：[国家税务总局：中华人民共和国刑法（2023年修正）](https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html)|
|6|ELIGIBLE|NO_ISSUE_FOUND_IN_CHECKED_POINT|十八周岁以上为成年人，与民法典17条对应。 依据：[国家统计局：中华人民共和国民法典](https://www.stats.gov.cn/gk/tjfg/xgfxfg/202503/t20250312_1958939.html)|
|11|ELIGIBLE|NO_ISSUE_FOUND_IN_CHECKED_POINT|题干三个条件齐备时不予行政处罚，与行政处罚法33条第1款对应。 依据：[市场监管总局：中华人民共和国行政处罚法（2021年修订）](https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/tzsbs/art/2023/art_3712fbfaaed942b3b6b66f14d5288723.html)|
|15|ELIGIBLE|EXPLANATION_OVERSTRICT|参考答案D保留了法定条件，但解析仅说不超过必要限度，遗漏防卫过当须明显超过并造成重大损害的组合条件。 依据：[国家税务总局：中华人民共和国刑法（2023年修正）](https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html)|
|17|ELIGIBLE|ANSWER_SCOPE_ERROR|题干问归责原则却将公平分担损失D也判对；应区分1165、1166归责与1186依法分担损失规则。建议限定题干并改为ABC。 依据：[国家统计局：中华人民共和国民法典](https://www.stats.gov.cn/gk/tjfg/xgfxfg/202503/t20250312_1958939.html)、[上海一中院：侵权责任纠纷开庭，法官这样建议（2024-01-09）](https://www.a-court.gov.cn/xxfb/no1court_412/docs/202401/d_4001042.html)|
|27|ELIGIBLE|ANSWER_OVERGENERALIZATION|参考答案将特定职责人员限制扩大到一切紧急避险，漏掉避免本人危险这一限定。 依据：[国家税务总局：中华人民共和国刑法（2023年修正）](https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html)|
|10024199|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|解析仍以旧治安管理处罚法87条说明检查；2026年生效文本相关规则在103条，内容亦有变化。 依据：[市场监管总局：中华人民共和国治安管理处罚法（2025年修订）](https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2025/art_8a1c722747214869b28c0ad7a0f8bb7a.html)|
|10024436|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|解析以旧公司法44条三至十三人董事会为无版本法律依据；新法68条已调整。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10024437|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|解析沿用旧公司法216条术语；新法265条的实际控制人等表述已调整。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10024438|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|监事劳动关系题的法律依据列旧公司法44条董事会组成；同时有过时与依据针对性问题。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10024439|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|劳动关系题解析附旧公司法44条，未明确历史版本或与现行制度区别。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10024497|ELIGIBLE|ANSWER_ANALYSIS_MISMATCH|同一题答案以公司法47条讲经理解聘，解析以47条讲董事会会议召集；现行47条是注册资本，需重写版本和论证。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10024706|ELIGIBLE|OUTDATED_OR_UNVERSIONED_BASIS|公司人格否认/一人公司财产独立仍引用旧20、63条；现行公司法相关责任在23条，未标历史适用。 依据：[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10025574|ELIGIBLE|ANSWER_EXCEPTIONS_OVERBROAD|担保回答把控制公司及互保关系笼统当作免决议例外，遗漏全资/上市公司等条件；效力应区分是否对公司发生效力。 依据：[最高人民法院：民法典有关担保制度的解释](https://www.court.gov.cn/fabu/xiangqing/282721.html)、[市场监管总局：中华人民共和国公司法（2023年修订）](https://www.samr.gov.cn/xyjgs/flfg/art/2024/art_d4e84936a8a744f8928ac7aec7594345.html)|
|10025062|ELIGIBLE|IRRELEVANT_AND_OUTDATED_BASIS|题问物业是否有罚款权，解析列共同治安违法和从重从轻处罚条文，不能支撑权限结论且条号旧；应对应行政处罚实施主体。 依据：[市场监管总局：中华人民共和国行政处罚法（2021年修订）](https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/tzsbs/art/2023/art_3712fbfaaed942b3b6b66f14d5288723.html)、[市场监管总局：中华人民共和国治安管理处罚法（2025年修订）](https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2025/art_8a1c722747214869b28c0ad7a0f8bb7a.html)|
|10025395|ELIGIBLE|IRRELEVANT_AND_OUTDATED_BASIS|题问公安行政许可，解析用旧治安处罚种类条文支撑，混淆许可与处罚；应另核对具体许可设定依据。 依据：[市场监管总局：中华人民共和国治安管理处罚法（2025年修订）](https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2025/art_8a1c722747214869b28c0ad7a0f8bb7a.html)|
|10025463|SUBJECT_CONFLICT|FALSE_POSITIVE_SUBJECT_RULE|题干有购销合同但答案与解析均讨论刑法167条；不能因题干缺少刑/罪等字而认定科目冲突。答案本身仍需教学复核。 依据：[国家税务总局：中华人民共和国刑法（2023年修正）](https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html)|

## 最小修改建议

种子15：保持答案ABCD，解析明确防卫过当须同时满足明显超过必要限度与造成重大损害；特殊防卫依刑法20条第3款处理。

种子17：题干限定为依据民法典1165、1166条考察归责；答案改为 A/B/C，解析明确1186是依法分担损失规则。

4）主体限制不同：刑法第21条第3款限制的是为避免本人危险而采取的紧急避险；职务上、业务上负有特定责任的人不能借此逃避其特定职责，并非其一切紧急避险均不适用。

依据刑法第20条、第21条，从起因、对象、限度与职责限制比较。正当防卫针对正在进行的不法侵害；紧急避险还须出于不得已。注意第21条第3款仅限制特定职责人员为避免本人危险而避险，不能省略这一限定。

## 边界

上述控制题的结论仅为本次检查点未发现问题，不是专家认证。所有输出保留审核前 contentHash，后续修订后应重算指纹。若旧题已形成作答记录，应保留历史记录，避免把内容修订冒充当时的判题依据。
