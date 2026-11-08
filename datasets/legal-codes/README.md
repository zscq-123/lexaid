# LexAid 公开法典与法规数据

本目录保存可直接导入 MySQL 8 的确定性压缩 SQL。四个来源共 **394,122 条**，每条均保留法域、语言、状态、来源数据集、来源项 ID、原文链接、许可和扩展元数据。`(source_dataset, source_item_id)` 唯一键及 `INSERT IGNORE` 使重复导入保持幂等。

## 已纳入的数据

| 数据集 | 法域 | 粒度 | 记录数 | 快照/范围 | 许可 |
| --- | --- | --- | ---: | --- | --- |
| [Chinese Law Corpus](https://github.com/lttxzmj/chinese-law-corpus) | 中国 | 法条 | 56,182 | 1,513 部现行或待生效法律法规，2026-11-05 | CC0 1.0 |
| [United States Code](https://uscode.house.gov/download/download.shtml) | 美国 | section | 60,880 | 官方 USLM XML，release point 119-138 | 美国政府作品，公有领域 |
| [EURLEX57K](https://huggingface.co/datasets/jonathanli/eurlex) | 欧盟 | CELEX 文书 | 57,000 | 英文文书，1958–2016 | CC BY-SA 4.0 |
| [i.AI Lex API](https://lex.lab.i.ai.gov.uk/) | 英国 | 法律/法定文书 | 220,060 | `legislation.parquet`，2026-11-01 | Open Government Licence 3.0 |

清单、分类统计、文件大小和 SHA-256 位于 [`manifest.json`](manifest.json)。

## 数据边界

- 中国和美国数据按法条/section 导入，可直接进行条文级全文检索。
- 欧盟数据是 EURLEX57K 的历史研究语料，不代表 2016 年后的最新欧盟法状态；正式引用应回到每条记录的 EUR-Lex 原文链接核验。
- 英国导入的是 Lex API `legislation.parquet` 中的全部 220,060 条文书级记录。正文列使用该快照提供的 `description`；其中 9,024 条原数据为空，构建时以标题作为可检索回退。来源元数据中的 `official`、`xml`、`llm_ocr` 均原样保留，机器识别内容应回到 legislation.gov.uk 核验。
- 法律数据只用于学习和检索辅助，不构成法律意见。

## 导入

新建 Docker 数据卷时，`docker compose up -d` 会按中国、美国、欧盟、英国的顺序自动导入。已有数据库可手动执行：

```bash
docker exec -i lexaid-mysql mysql -uroot -plexaid-root-local lexaid < datasets/legal-codes/00-schema-migration.sql
gzip -dc datasets/legal-codes/lexaid-laws-cn.sql.gz | docker exec -i lexaid-mysql mysql -ulexaid -plexaid-local lexaid
gzip -dc datasets/legal-codes/lexaid-laws-us.sql.gz | docker exec -i lexaid-mysql mysql -ulexaid -plexaid-local lexaid
gzip -dc datasets/legal-codes/lexaid-laws-eu.sql.gz | docker exec -i lexaid-mysql mysql -ulexaid -plexaid-local lexaid
gzip -dc datasets/legal-codes/lexaid-laws-uk.sql.gz | docker exec -i lexaid-mysql mysql -ulexaid -plexaid-local lexaid
```

如使用自定义密码，请相应替换命令。命令行密码仅适合本机开发环境。

## 重新构建

构建脚本需要 Python 3.10+ 和 `pyarrow`，输入为 Chinese Law Corpus 的 `laws/` 目录、美国法典 USLM XML 解压目录、EURLEX57K 三个 parquet split，以及英国 Lex API 的 `legislation.parquet`：

```bash
python3 -m pip install pyarrow
python3 scripts/build-legal-code-datasets.py \
  --china-dir /path/to/chinese-law-corpus/laws \
  --us-dir /path/to/uscode-xml \
  --us-release-point 119-138 \
  --eu-parquet /path/to/eurlex-train.parquet \
  --eu-parquet /path/to/eurlex-validation.parquet \
  --eu-parquet /path/to/eurlex-test.parquet \
  --uk-parquet /path/to/legislation.parquet \
  --output-dir datasets/legal-codes \
  --manifest datasets/legal-codes/manifest.json
```

压缩文件使用固定 gzip 时间戳；相同输入会生成相同校验和。
