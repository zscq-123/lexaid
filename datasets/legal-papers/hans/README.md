# 汉斯《法学》论文许可核对

`hans-verified.json` 保存 46 篇通过逐篇核对的 DOI 和出版方 PDF 链接。核对范围是 Crossref 2025 年该刊的 404 条记录。每条收录的证据均满足以下条件。

- 出版方网页的 `citation_doi` 与 Crossref DOI 一致，`citation_pdf_url` 指向出版方的 PDF。
- PDF 首页出现同一 DOI，前三页内同一页同时出现对本文的 CC BY 4.0 明确许可声明和对应许可证链接。

此次另有 358 条的文章页返回站点验证页或首页，未取得逐篇 PDF 证据。需要查看作者摘要、图表和正式排版时，可按 DOI 查找对应的出版方页面。已核实的文章页提供中文摘要，HTML 正文区为空，全文由 PDF 提供。

在项目根目录运行以下命令可用已缓存的 Crossref `hans.json` 与文章页再次核对。依赖 Python、requests、BeautifulSoup 和 PyMuPDF。核对器以本地缓存复用已取得的 PDF，结果按 DOI 排序，列出未通过的原因。

```bash
python3 datasets/legal-papers/hans/verify.py \
  --catalog /path/to/cache/hans.json \
  --cache-dir /path/to/cache
```
