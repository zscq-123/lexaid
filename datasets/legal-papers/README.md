# 法学论文

这个快照面向 LexAid 的「学者观点」模块。每条记录对应一篇出版物，保存署名、题名、刊名、年份、来源链接和可核对的 DOI。作者摘要保持原意并标注为 `author_abstract`，只有题录的记录标注为 `metadata_only`。正文与摘要分别保存。

| 期刊和范围 | 记录 | 有作者摘要 | 有可检索正文 |
| --- | ---: | ---: | ---: |
| 《华夏法学》2024 年全年三期 | 24 | 21 | 21 |
| 汉斯出版社《法学》2025 年 Crossref 期刊论文记录 | 404 | 46 | 0 |
| *Laws* 2025 年 Crossref 期刊论文记录 | 100 | 100 | 100 |
| 首批小计 | 528 | 167 | 121 |
| DOAJ / Crossref 另四种国际法学期刊，2024–2025 年 | 240 | 236 | 234 |
| Yale Law Archive 当前公开版本 | 1,855 | 1,098 | 33 |
| 《交大法學評論》DOAJ 期刊目录 | 74 | 0 | 0 |
| OpenAlex 两组法学专题，扣除 18 个已收录 DOI | 3,742 | 0 | 0 |
| 当前合计 | 6,439 | 1,501 | 388 |

**范围与来源**

- 《华夏法学》的记录取自[期刊目录](https://clj.ac/archives/)，原始页码、三篇译介评论和一处作者署名差异见[该刊采集说明](clj/README.md)。出版方声明使用[CC BY 4.0](https://clj.scholasticahq.com/)。
- 汉斯出版社《法学》（*Open Journal of Legal Science*，ISSN 2329-7360）从 [Crossref 的该刊题录](https://api.crossref.org/journals/2329-7360/works?filter=from-pub-date:2025-01-01,until-pub-date:2025-12-31,type:journal-article) 取得当年期刊论文记录。从逐篇 DOI 匹配的出版社页面提取中文题名、原始署名和摘要。该刊作者可选 CC BY 或 CC BY-NC，只有取得明确 CC BY 4.0 证据才收录作者摘要。当前 358 篇仅收录 Crossref 提供的题名和署名，题名使用登记的英语，语言字段保留 `und`（待核对）。[汉斯版权政策](https://www.hanspub.org/AboutUs/Index) · [逐篇核验记录](hans/hans-verified.json)
- 英文期刊 *Laws*（ISSN 2075-471X）取自 [Crossref 的该刊题录](https://api.crossref.org/journals/2075-471X/works?filter=from-pub-date:2025-01-01,until-pub-date:2025-12-31,type:journal-article)。这 100 条记录均有文章级 CC BY 4.0 及作者摘要。正文由出版方的 JATS XML `body` 提取，保留正文段落与章节标题，省略表图、独立公式、脚注和参考文献。[出版方许可](https://www.mdpi.com/journal/laws/about)
- 两刊按 Crossref `published` 日期筛选 2025 年 1 月 1 日至 12 月 31 日，部分 *Laws* 论文于 2025 年在线发表并编入下一年度卷。采集器排除 `update-to` 类型更新和题名明确标示的编务、致谢、勘误、撤稿通知。本批次的 504 条候选均通过此筛选。清单列出候选计数与排除项。

**扩展目录**

- [OpenAlex 法学专题](openalex/README.md)收录 2024–2025 年被 OpenAlex 主要归入 Law 的中文期刊文章，以及 2025 年国际仲裁与投资法、法律冲突与管辖权两个专题的英文期刊文章。该来源收录题名、作者、期刊和标识符。
- [Yale Law Archive](yale/README.md)收录 OSF 官方档案中有署名的当前公开版本，保留预印本 DOI、档案链接和逐篇核验的开放材料。
- [DOAJ 与 Crossref 国际期刊](journals/README.md)覆盖四种法学期刊的 2024–2025 年论文，支持从记录回到出版方核对摘要或正文。
- [《交大法學評論》](nctu/README.md)另收录 DOAJ 已索引的 2017–2025 年题录。

构建器以规范化 DOI 去重。以前收录的论文保留其来源和内容，有 DOI 的期刊记录优先于同篇的 OpenAlex 索引记录。无 DOI 的条目使用各来源稳定标识。各包的输入篇数、与优先来源重复的 DOI 数、实际新增数记录在清单的 `extraSnapshots` 字段。预印本和另有 DOI 的正式发表版本各自保留。

**初始化与复建**

新数据库由项目的 Docker 初始化配置导入。在已有项目数据库中，先执行本目录的 `00-schema-migration.sql`，再将 `lexaid-papers.sql.gz` 解压送入 MySQL 8。来源 ID 与 DOI 唯一约束防止重复，重导更新原记录，保留与笔记关联的数据库 ID。

本目录随附固定输入，离线复建与核验只需 Python 3.10+。

```bash
python3 scripts/build-legal-paper-datasets.py
python3 scripts/legal-paper-dataset.test.py
gzip -t datasets/legal-papers/lexaid-papers.sql.gz
```

如需重新从出版方采集，Crossref 与汉斯目录采集需要 `requests`、`beautifulsoup4`。先从指定两刊取得题录及期刊页，再运行逐篇许可证核验程序（依赖 PyMuPDF），之后重新采集目录以应用更新的许可证据。

```bash
python3 scripts/collect-paper-catalog.py --cache-dir /path/to/cache
python3 datasets/legal-papers/hans/verify.py --catalog /path/to/cache/hans.json --cache-dir /path/to/cache
python3 scripts/collect-paper-catalog.py --cache-dir /path/to/cache
python3 scripts/collect-laws-paper-texts.py --output datasets/legal-papers/laws-texts.json.gz
python3 datasets/legal-papers/clj/build.py
python3 scripts/build-legal-paper-datasets.py
```

`crossref-catalog.json.gz` 是首批题录与摘要输入，`laws-texts.json.gz` 保存按 DOI 索引的出版方正文，期刊及档案子目录保存相应快照。项目默认构建命令一次性组装所有快照。更新扩展目录时，以对应子目录的说明重新采集，再运行总构建命令。导入包与输入的校验和、按刊统计和语言分布见 [manifest.json](manifest.json)。
