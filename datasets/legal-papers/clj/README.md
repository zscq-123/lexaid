# 《华夏法学》2024 年论文

`records.json` 收录出版方目录中的 2024 年第 1、2、3 期，分别为 7、10、7 条。每条记录保留姓名和署名顺序，译作保留著者、译者及其角色。论文来源链接打开该篇在期刊网站的独立页面，读者可从那里进入整期 PDF，按记录所列页码查阅原文。[期刊目录](https://clj.ac/archives/)

21 篇记录附有纸本首页「摘要」到「关键词」之间的中文内容，以及按单篇起止页提取的可搜索文字。3 篇译介评论仅收录题录与链接。16 条记录在目录中附 DOI，另外 8 条以官方文章网址作为稳定标识。期刊声明其内容适用 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)；文本经过 PDF 排版转换，略去页眉和页码，图形、表格和脚注的位置以期刊排印为准。[出版方许可说明](https://clj.ac/)

《大数据挖掘行为的认知正义问题研究》在纸本第 14 页标题下署名王雷垠，页尾作者简介同为王雷垠。记录采用纸本署名；网页目录和文章落地页写为周是伦。[春季号第 14 页](https://clj.ac/wp-content/uploads/2025/06/2-CLJ-24S-2.1.pdf#page=18)

## 复建

在项目根目录执行下列命令。采集程序校验三本期刊 PDF 的 SHA-256、文章编号、题名及署名、每一页的印刷页码、作者摘要边界，并在校验通过后生成 JSON。

```sh
python3 -m pip install 'PyMuPDF==2.0' requests beautifulsoup4
python3 datasets/legal-papers/clj/build.py
```

源文件固定为 [春季号](https://clj.ac/wp-content/uploads/2025/06/2-CLJ-24S-2.1.pdf)、[夏季号](https://clj.ac/wp-content/uploads/2025/06/3-CLJ-24SS-2.2.pdf) 和 [秋季号](https://clj.ac/wp-content/uploads/2025/06/4-CLJ-24F-2.3.pdf)。如已下载，可按原文件名放在同一目录，通过 `--pdf-dir /path/to/issues` 复用。网站目录定期更新，当前快照在 2026 年 11 月 8 日核对。输出文件 SHA-256 为 `30bf2622de3937ca8c5c14d356a73e28bd2918ceb16c501cd37555f39e599a58`。
