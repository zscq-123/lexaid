# LexAid 公开案例数据

本目录保存可重复导入 MySQL 的中文与国际案例压缩 SQL。所有记录均保留法域、语言、来源数据集、来源 ID、原文/数据集链接、许可和数据集特有元数据；`INSERT IGNORE` 配合 `(source_dataset, source_item_id)` 唯一键保证重复导入不会产生副本。

## 已纳入来源

| 数据集 | 记录数 | 内容 | 许可 | 说明 |
| --- | ---: | --- | --- | --- |
| Chinese Law Corpus · Guiding Cases | 278 | 最高人民法院指导性案例 | CC0 1.0 | 导入裁判要点、基本案情、裁判结果、裁判理由、相关法条及官方原文链接 |
| Chinese Law Corpus · Gazette Cases | 445 | 《最高人民法院公报》案例 | CC0 1.0 | 导入摘要、全文、案号、法院、文书类型和公报期次 |
| LexGLUE · SCOTUS | 7,800 | 美国最高法院意见全文 | CC BY 4.0 | 导入 train/validation/test 全部分片；主题标签写入类型与元数据 |
| LexGLUE · ECtHR Task A | 11,000 | 欧洲人权法院案件事实 | CC BY 4.0 | 导入 train/validation/test 全部分片；违反条款标签写入裁判结论与元数据 |

合计 **19,523 条来源记录**。ECtHR 中一组正文相同但位于不同源行的记录，通过 `split + row index + text hash` 分别保留；来源 ID 仍保持稳定且可重复构建。

来源链接：

- Chinese Law Corpus：<https://github.com/lttxzmj/chinese-law-corpus>
- LexGLUE：<https://huggingface.co/datasets/coastalcph/lex_glue>
- LexGLUE 论文与代码：<https://github.com/coastalcph/lex-glue>

## 数据边界

- 中文案例是公开案例精选，不是中国裁判文书网的全量裁判文书。
- LexGLUE SCOTUS 保留法院意见全文和主题标签，但预处理版本不含稳定案号、裁判日期或原始案件 URL；因此以正文 SHA-256 作为来源 ID。
- LexGLUE ECtHR Task A 提供案件事实段落与违反条款标签，不是 HUDOC 完整判决；预处理版本不含案件名称/申请号，因此显示标题为稳定哈希生成的本地标识。
- 案例仅供学习和检索，不构成法律意见；历史案件适用的法律可能已修订。

## 文件

- `00-schema-migration.sql`：扩展 `case_info`，增加全文和来源字段，幂等执行。
- `lexaid-cases-cn-guiding.sql.gz`：中国指导性案例。
- `lexaid-cases-cn-gazette.sql.gz`：中国公报案例。
- `lexaid-cases-us-{train,validation,test}.sql.gz`：LexGLUE SCOTUS；按原始分片拆分，避免单文件超过常见 Git 托管上限。
- `lexaid-cases-echr.sql.gz`：LexGLUE ECtHR Task A。
- `manifest.json`：精确记录数、文件大小和 SHA-256。

## 重建

依赖 Python 3.10+ 与 `pyarrow`。下载 Chinese Law Corpus 以及 LexGLUE 对应 Parquet 分片后执行：

```bash
python3 scripts/build-legal-case-datasets.py \
  --china-corpus-root /path/to/chinese-law-corpus \
  --scotus-parquet /path/to/scotus-train.parquet \
  --scotus-parquet /path/to/scotus-validation.parquet \
  --scotus-parquet /path/to/scotus-test.parquet \
  --ecthr-parquet /path/to/ecthr_a-train.parquet \
  --ecthr-parquet /path/to/ecthr_a-validation.parquet \
  --ecthr-parquet /path/to/ecthr_a-test.parquet \
  --output-dir datasets/legal-cases \
  --manifest datasets/legal-cases/manifest.json
```

验证：

```bash
python3 scripts/legal-case-dataset.test.py
gzip -t datasets/legal-cases/*.sql.gz
```
