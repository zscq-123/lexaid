# Yale Law Archive

本快照来自耶鲁法学院图书馆和 Center for Open Science 合作运营的 [Law Archive](https://library.law.yale.edu/research/law-archive)。数据直接取自 OSF 的 [lawarchive 提供方](https://api.osf.io/v2/preprint_providers/lawarchive/) 和[公开预印本列表](https://api.osf.io/v2/preprint_providers/lawarchive/preprints/)。每条记录都检查所属提供方，作品链接指向该提供方的具体版本。

2026 年 11 月 8 日采集时，目录分页共返回 1,890 条记录。按公开、已发表、最新版本且未撤回筛选后，共 **1,855 篇**。排除了 23 条更早版本和 12 条已撤回记录。来源年份是 OSF 的 `date_published`，范围为 2016–2026 年，包含早于该库 2024 年正式上线的历史记录。年份只描述 OSF 登记的发布日期，与出版期刊正式刊载年份可能不同。

- 所有记录均保留完整书目署名、真实标题、具体版本链接，以及 OSF 给该版预印本登记的 DOI。作者按 OSF 的书目贡献者顺序记录，优先使用该论文登记的署名。个别用户资料无法访问时，核对该篇官方 CSL 引文作者。OSF 目录没有提供可逐篇核实的语言字段，因此记为 `und`。
- 逐篇以 OSF 的作品 `license` 关系核对复用许可。998 篇标明 CC BY 4.0、100 篇标明 CC0 1.0，均保留作者填写的 description 作为摘要。757 篇只收录题录。纸本文章或预印本的正式出版版本可能有独立 DOI。
- 对 2026 年 10 月 20 日至 11 月 7 日发布、具备上述许可的 40 篇当前版本检查 OSF 主文件。其中 33 篇有可检索正文。2 篇主文件不符合 PDF 格式或大小条件，5 篇的文件前两页无法匹配登记题名。收录正文要求 PDF 的 SHA-256 与 OSF 文件元数据一致，每页均有可读文本。正文为按页提取的文字，图表版式通过链接回原文件查看。[逐篇原文件与校验和](text-sources.json)
- 下载包 `records.json.gz` 是压缩 JSON 数组。其 SHA-256 为 `d281a29eb87a795c1c5b915a7a207d358d706e1d4f260ffc94f9a3bc4f0b9a19`。LexAid 的论文构建器直接读取它进行离线导入。

重新采集需要 Python、requests 和 PyMuPDF。本次 PyMuPDF 为 2.0。请在项目根目录运行以下命令。缓存目录用于存放按页采集的书目和核验过的 PDF。上游后续发布、修订或撤稿会改变新快照的内容。

```sh
python3 datasets/legal-papers/yale/collect.py --cache-dir /path/to/yale-cache
python3 datasets/legal-papers/yale/verify.py
```

桌面阅读页使用与正文对应的 PDF 物理页界。要从同一批缓存重建页界索引，运行 `python3 datasets/legal-papers/yale/page_boundaries.py --cache-dir /path/to/yale-cache`。脚本逐篇核对 PDF 的 SHA-256，并确认逐页提取的文字与已收录正文完全相同后写出索引。此快照的 33 份 PDF 共 1,247 页。
