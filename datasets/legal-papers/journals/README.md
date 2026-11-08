# 四刊法学论文快照

从 DOAJ 文章索引与 Crossref 期刊作品接口获取 2024–2025 年的四本英文法学期刊记录。两边按 DOI 合并后共 270 条候选，保留 240 篇具有署名的论文、案例评析及专题文章。各篇的许可证据、DOAJ 文章 ID、出版社链接及排除清单在 [provenance.json](provenance.json)。

| 期刊 | DOAJ 候选 | Crossref 候选 | DOI 并集 | 收录 | 有作者摘要 | 有正文文本 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| [German Law Journal](https://doaj.org/toc/2071-8322) | 183 | 187 | 187 | 160 | 158 | 160 |
| [Utrecht Law Review](https://doaj.org/toc/1871-515X) | 37 | 38 | 38 | 37 | 37 | 37 |
| [Utrecht Journal of International and European Law](https://doaj.org/toc/2053-5341) | 8 | 8 | 8 | 6 | 4 | 0 |
| [Tilburg Law Review](https://doaj.org/toc/2211-2545) | 37 | 36 | 37 | 37 | 37 | 37 |
| **合计** | **265** | **269** | **270** | **240** | **236** | **234** |

DOAJ 使用文章年份字段，Crossref 使用 `from-pub-date` 和 `until-pub-date` 过滤期刊论文。每个 DOI 保留原始署名，两个索引年份有差异时取 DOAJ 编目年份。剔除缺少完整署名、封面目录、明确标示的编务、勘误及撤稿等 30 条记录。Tilburg 的 DOAJ 索引使用电子 ISSN 2211-2545，Crossref 检索使用印刷 ISSN 2211-0046。

**文本来源**

- 摘要取自 DOAJ、Crossref 或出版方摘要区。展示摘要需有该 DOI 的 Crossref 正式版本 CC BY 4.0 声明，或同篇出版物的 CC BY 4.0 许可。每篇保存作者、期刊、 DOI 与原文链接；无摘要的条目标为 `metadata_only`。
- *German Law Journal* 正文来自 Cambridge 出版页。抽取前同时核对网页 DOI 和文章版权声明，再提取正文的标题、段落。下面的案例可用于核对其出版社原文和许可。[示例论文](https://doi.org/10.1017/glj.2024.36)
- *Utrecht Law Review* 与 *Tilburg Law Review* 从出版社页面的 `citation_xml_url` 获取 JATS，核对 XML 正文 DOI 及该篇的 CC BY 4.0 许可，再提取章节和段落。正文文本用于检索，图、表、公式、附录、参考文献及版面请查阅出版方原件。个别 Tilburg DOI 缺少 Crossref 登记，使用带有匹配 DOI 的出版社文章页。[Utrecht 示例](https://utrechtlawreview.org/articles/10.36633/ulr.1069) · [Tilburg 示例](https://tilburglawreview.com/articles/10.5334/tilr.395)
- *Utrecht Journal of International and European Law* 通过出版社新站的期号目录匹配 DOI，保留当前可用的单篇网址；本快照收录可核对的元数据与获许可作者摘要。[出版方期号目录](https://utrechtjournal.org/issue/archive)

**复建与校验**

固定输入 `records.json.gz` 的 SHA-256 为 `6649ae0fe2237ce8416bb4c993a58b1bec99a03e04f827026daa1046566f657a`。验证离线输入运行第一条命令。重新获取公网数据时需要 Python 3.10+、`requests`、`beautifulsoup4` 和 `lxml`；原始响应会缓存在调用方指定的目录。

```bash
python3 datasets/legal-papers/journals/verify.py
python3 datasets/legal-papers/journals/collect.py --cache-dir /path/to/journal-responses
```
