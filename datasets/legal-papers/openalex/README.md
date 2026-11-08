# OpenAlex 法学期刊论文题录

以 OpenAlex 的 Works API 获取两组检索结果，各组均限定为期刊、文章类型，且上游没有标记为已撤稿或期刊附属文字（paratext）。

|组|发表范围|OpenAlex 分类|
|---|---|---|
|中文|2024 至 2025 年|语言为 `zh`，主要主题归属于 [Law 子领域 3308](https://api.openalex.org/subfields/3308)|
|英文|2025 年|语言为 `en`，主要主题为[国际仲裁与投资法](https://api.openalex.org/topics/T12040)或[法律冲突与管辖权](https://api.openalex.org/topics/T12767)|

2026-11-08 取得的快照有 4,142 条候选，367 条缺少完整署名，另有 15 条仅题为「判決快遞」的《月旦醫事法報告》栏目记录。过滤后保留 3,760 条题录，含中文 1,835 条、英文 1,925 条。3,556 条有 DOI，204 条仅有 OpenAlex 标识。主数据包会继续与既有期刊来源按 DOI 去重。

主题与语言采用 OpenAlex 分类。检索遍历范围内的全部匹配页，按 OpenAlex work ID 排序，并对照上游返回的匹配总数。无题名、无期刊、署名不完整、作者人数达到 API 上限、重复 DOI 或明显属于编务公告的记录会被过滤。署名按上游顺序保留，优先采用原始作者名。原文链接优先使用 DOI，没有 DOI 的条目链接至 OpenAlex work 记录。

本包只收录题录。OpenAlex 提供的元数据采用 CC0；论文和摘要的权利取决于原出版物，各记录的论文许可字段留空。题录采用 `metadata_only`，可在 LexAid 按作者、题名和 DOI 检索。[OpenAlex 数据说明](https://openalex.org/) · [完整数据库快照](https://help.openalex.org/access/snapshot/)

## 固定快照与重建

`source.json.gz` 保存 API 查询筛选条件、命中总数，以及核验和构建所需的归属字段。`records.json.gz` 保存统一导入器所用的记录数组。两份快照都使用 UTF-8 JSON 和固定时间戳的 gzip。

|文件|SHA-256|
|---|---|
|`source.json.gz`|`6a3112250153b5d5269e1db4fd264fbd2d698d63984f68d43cf46924e8df9b88`|
|`records.json.gz`|`6f3896777371326a827e6708c2b5a8276bccc5cce00e2a362a4e1644cce78e1e`|

在项目根目录执行以下命令。

```sh
# 直接核对冻结的源数据与导入记录
python3 datasets/legal-papers/openalex/collect.py --verify
# 从冻结的源数据离线重建相同记录
python3 datasets/legal-papers/openalex/collect.py
# 需要从 OpenAlex 获取新版本时才联网
python3 datasets/legal-papers/openalex/collect.py --refresh
```
