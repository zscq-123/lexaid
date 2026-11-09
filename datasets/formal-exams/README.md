# 模拟考试题源

正式考试独立使用 `mock_exam_bank`。它与日常练习中的 `question`、错题记录及题库进度各自计数。

| 题源 | 导入内容 | 来源与使用方式 |
| --- | --- | --- |
| LexEval | 325 道经过筛选的多步推理、职业行为和权益保护案例题 | 数据发布版本固定为 `044c695f62894deef41ad9a30797e1da0404945c`。保留发布者的答案，署名与许可文本随库附带。任务 3_4 由作者标注为根据法考题适配，平台将它用于模拟卷。详见[数据与许可](https://github.com/CSHaitao/LexEval/tree/044c695f62894deef41ad9a30797e1da0404945c)和[论文附录 C](https://arxiv.org/abs/2409.20288)。 |
| LexAid 原创案例 | 18 道综合客观题及 12 道主观案例题，含参考答案和规范来源 | 题目及评分要点见 `original-objective.json` 和 `original-subjective.json`，`sourceUrl` 指向作为命题依据的规范。 |
| 历年国家司法考试 | 题号、客观题参考选项及主观题评分依据，与相应的公开原卷阅读链接配对 | `linked-real.json` 保存年份、卷号、题号和出处。题面通过公开的原卷链接阅读；成绩页面提供参考答案出处。历史题按当年资料作答。 |

2017 年[试卷发布公告](https://sft.hunan.gov.cn/sft/xxgk_71079/gzdt/sjdt/201709/t20170921_4565990.html)列明公开试题与参考答案的日期。真题答题卡链接到转载自司法部的[客观原卷](https://edu.sina.com.cn/official/2017-09-18/doc-ifykymue6828659.shtml)及[主观原卷](https://edu.sina.com.cn/official/2017-09-18/doc-ifykymue6830771.shtml)。每道客观题按原卷选项顺序核对了答案页的下划线以及另一份[参考答案存档](https://xue.jschina.com.cn/lfv5/files_upload/f05d51e4-28bb-4a0f-8d2b-8a4992b2ab11/0.pdf)中的字母。剔除卷一第 40 题及卷三第 15 题，分别因答案冲突和选项异文无法通过双校。主观题由平台按原卷年度拟定评分要点，原卷开放论述也接受有依据的其他答案。

客观卷一次 100 题、150 分，分单选、多选和不定项；本库当前可抽 50/35/15 题，每题 1/2/2 分。主观卷组 5 道综合题、各 36 分。2026 年官方客观每卷 180 分钟，主观 240 分钟，主观 5 题的等额分配是 LexAid 的组卷方式。模拟考试允许自定义 1–480 分钟；从开考满 30 分钟才可提前交卷，届时未交由服务器按截止时刻保存的作答结算。[司法部 2026 年考试公告](https://www.moj.gov.cn/pub/sfbgwapp/zwgk/tzggApp/202606/t20260605_536064.html)

当前共 658 道题。历年国家司法考试通过原卷答题卡作答，模拟卷在站内展示题干、选项及材料。

| 来源 | 单选 | 多选 | 不定项 | 主观综合题 |
| --- | ---: | ---: | ---: | ---: |
| 2017 年真实试卷 | 148 | 105 | 45 | 5 |
| 模拟练习 | 153 | 145 | 45 | 12 |

`manifest.json` 记录正式题量及压缩文件 SHA-256。生成器要求四个非空且互异的选项，过滤缺参考答案、无效答案、题面含答案提示及完全重复的记录。同一任务的通用题干最多保留两个变体。部分可有一个或多个正确答案的中性题干被编排为不定项，选择过程使用题干和选项的稳定摘要，与正确选项数无关。不定项评分仍使用原始答案。

模拟客观卷优先抽取应用与综合推理题。入池要求题干或多个选项具备具体案情和法律分析线索，排除缺少前文、题型要求与答案数量矛盾、纯常识及复查发现的歧义。多主体、多环节问题与原创综合题优先进入较高难度层。难度是课程编排标签，尚无与真实考生表现对照的难度标定。法考历年题在现行制度、条款和答案上可能与题源年度不同。

## 重建和导入

从仓库根目录运行。在线重建需要 Node.js 和 Python 3。构建器默认通过 HTTPS 下载固定版本，逐个比对 `lexeval-sources.json` 记录的 SHA-256，同时校验并保留上游许可文件。

```sh
node scripts/build-formal-exam-bank.mjs
node --test scripts/formal-exam-bank.test.mjs
```

离线重建时，把锁定版本的 `data/3_4.json`、`data/6_2.json` 和 `data/6_3.json`与仓库根目录的 `LICENSE` 放在同一个输入目录，将该目录作为脚本参数。两种方式产生相同的第 27 号修订包 `formal-exam-bank-reviewed.sql.gz`。第 26 号已发布文件 `formal-exam-bank.sql.gz` 作为只读基线保留。新安装按表结构、26、27 顺序导入；已导入 26 的站点执行 27。数据更新在一个事务内按稳定题号更新，已开考试使用创建时记录的试卷快照。

```sh
gzip -dc datasets/formal-exams/formal-exam-bank-reviewed.sql.gz | docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```
