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

## 自动导入

全新数据库执行 `docker compose up -d` 时，MySQL 初始化程序会在基础示例数据之后自动读取该压缩脚本。

已有数据库不会再次执行初始化目录，可手动导入：

```bash
gzip -dc datasets/lexaid-public-questions.sql.gz \
  | mysql -h 127.0.0.1 -P 3308 -u lexaid -p lexaid
```

导入使用 `INSERT IGNORE` 和稳定的来源键，可安全重复执行。

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

## 未直接再发布的数据

- JEC-QA 官方要求使用者本人提交申请，采用 CC BY-NC-ND 4.0，并明确禁止把数据集随项目再次发布。因此这里只包含 LawBench、LexEval 已正式公开的相关子集，没有绕过申请导入 JEC-QA 全量。
- CAIL2019-CJRC 官方仓库只提供比赛说明，未直接提供数据文件和明确的再发布许可。因此这里只包含 LawBench、LexEval 已公开的阅读理解子集。

这些题目可能依据历史法律版本制作。系统将其标记为公开数据来源，但不标记为已按现行法人工复核；不得把题库答案直接当作法律意见。
