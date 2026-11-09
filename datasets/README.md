# LexAid 公开法律数据

项目内置以下可追溯、可重复导入的数据包。

- 公开题库：LawBench、LexEval、LeCoQA；详见本文件。
- 中国及国际法典/法规：394,122 条；详见 [`legal-codes/README.md`](legal-codes/README.md)。
- 中国及国际案例：19,523 条；详见 [`legal-cases/README.md`](legal-cases/README.md)。
- 中文与英文法学论文，收录范围、摘要和正文来源见[论文数据说明](legal-papers/README.md)。

## 公开法律题库

`lexaid-public-questions.sql.gz` 是可重复导入的 MySQL 压缩脚本，包含以下公开数据：

| 数据集 | 导入范围 | 许可证 | 原始地址 |
| --- | --- | --- | --- |
| LawBench | `data/zero_shot` 全部 20 个任务 | Apache-2.0 | <https://github.com/open-compass/LawBench> |
| LexEval | `data` 全部 23 个任务 | MIT | <https://github.com/CSHaitao/LexEval> |
| LeCoQA | `data/queries.json` 全部问答 | MIT | <https://github.com/oneal2000/LeCoQA> |

每条来源记录保存在 `question_source` 表，包含数据集、原始题号、任务、许可证、来源 URL 和原始元数据。不同数据集中的完全相同题目只存一份，但会保留多条来源记录。
原数据中有 15 条题目未提供参考答案；导入时保留空答案，不会自动生成或猜测答案。

## 答题科目与法典编目

题库将 25,598 道去重后的公开题和 30 道教学示例归入下列学习目录。一级分组和民法细分是 LexAid 的教学安排。课程名称参考教育部高等教育司的说明。[关于法学专业核心课程的说明](https://www.moe.gov.cn/jyb_xxgk/s5743/s5744/A08/201212/t20121218_181006.html)

| 学习大类 | 二级科目 | 题数 |
| --- | --- | ---: |
| 理论与法律史 | 法理学、法律史、法律职业伦理 | 66 |
| 宪法与行政法 | 宪法学、行政法学 | 472 |
| 民商法 | 民法学、合同法、物权法、侵权责任法、人格权法、婚姻家庭与继承法、商法学、知识产权法学 | 7,507 |
| 刑法 | 刑法学 | 8,790 |
| 诉讼与争议解决 | 刑事诉讼法学、民事诉讼法学、行政诉讼法学、仲裁法学 | 1,143 |
| 经济与社会法 | 经济法学、劳动与社会保障法学、环境与资源保护法学 | 954 |
| 国际法 | 国际公法学、国际私法学、国际经济法学 | 75 |
| 综合与待判定 | 跨学科综合 701 题，待判定 5,920 题 | 6,621 |

每题有一个主科目。可用证据包括教学题的考查法条关联、LeCoQA 的相关法规和关键词、来源明确限定的考查任务、题干与参考答案中的法律概念。选择题只读取正确选项。抽取实体的候选列表、判断偏见的选项和没有具体任务对象的程序用语不能单独确定科目。“跨学科综合”表示多个领域的证据并列，“待判定”表示证据不足，归类结果可随规则更新。

法典编目独立于主科目。同一题明确引用多编时，各编均可检索。构建器从站内中国法典数据读取“条号 → 编”，依据题干、正确答案或来源中的明确引用建立关联。只有提到合同、婚姻或侵权等主题的题目仍可按科目筛选。当前 5,480 题关联 18 编，共 6,648 条关系，覆盖民法典七编、刑法两编、民事诉讼法四编及刑事诉讼法五编。编名取自随附法典快照，原题引用的版本和现行法律可能有差异。

## 自动导入

全新数据库执行 `docker compose up -d` 时，MySQL 初始化程序会在基础示例数据之后自动读取该压缩脚本。

已有数据库不会再次执行初始化目录，可手动导入：

```bash
gzip -dc datasets/lexaid-public-questions.sql.gz \
  | mysql -h 127.0.0.1 -P 3308 -u lexaid -p lexaid
```

导入使用 `INSERT IGNORE` 和稳定的来源键，可安全重复执行。

题库导入后可重复执行科目与法典编目升级。升级根据题号及题干的 SHA-256 匹配，只更新两张映射表 `question_legal_subject` 和 `question_code_book`。原有 `question.category` 和 `question_source` 继续记录数据来源和原始任务。

```bash
gzip -dc datasets/question-legal-subjects.sql.gz \
  | mysql -h 127.0.0.1 -P 3308 -u lexaid -p lexaid
```

## 重新构建

先从各项目官方仓库取得数据，再执行：

```bash
node scripts/build-public-question-dataset.mjs \
  --lawbench /path/to/LawBench/data/zero_shot \
  --lexeval /path/to/LexEval/data \
  --lecoqa /path/to/LeCoQA/data/queries.json \
  --output datasets/lexaid-public-questions.sql.gz \
  --manifest datasets/manifest.json
```

构建器在题库旁生成 `question-legal-subjects.sql.gz`，也可以用 `--subjects /path/to/file.sql.gz` 指定输出。已有随附题库、教学示例和中国法典快照时，可单独重建映射并运行回归检查。

```bash
node scripts/build-question-subjects.mjs
node --test scripts/question-subjects.test.mjs scripts/question-dataset.test.mjs
```

## 未直接再发布的数据

- JEC-QA 官方要求使用者本人提交申请，采用 CC BY-NC-ND 4.0，并明确禁止把数据集随项目再次发布。因此这里只包含 LawBench、LexEval 已正式公开的相关子集，没有绕过申请导入 JEC-QA 全量。
- CAIL2019-CJRC 官方仓库只提供比赛说明，未直接提供数据文件和明确的再发布许可。因此这里只包含 LawBench、LexEval 已公开的阅读理解子集。

这些题目可能依据历史法律版本制作。系统将其标记为公开数据来源，但不标记为已按现行法人工复核；不得把题库答案直接当作法律意见。
