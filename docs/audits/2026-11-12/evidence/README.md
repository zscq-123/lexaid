# 审计证据与回放

最终结论以父目录 `content-review-summary.json` 和项目 `docs/full-question-review-2026-11-12.md` 为准。`lecoqa-reference-scan-*` 是全文阅读之前冻结的基础引文/风险扫描，其 131 题阅读数量不是最终 1,543 题覆盖数。

三个 `lecoqa-semantic-*.jsonl.gz` 保存独立代理的实际阅读记录；全部标为 AI，不构成人工签署。最终合并继续保留之前的具体问题，不能仅以新分片“本轮未识别问题”覆盖已经附证据的旧问题。

- `replay-inputs.tar.gz`：原始本地导出、固定版本上游、官方语料、历年试题页面及修订前后模拟卷快照。
- `review-ledgers-before-merge.tar.gz`：最终合并所用的原始审计记录、三份阅读分片和修复提案。可重跑汇总，但重跑程序不会产生新的语义阅读或法律认证。
- `replay/`：各来源核对、结构修复、迁移测试、全量转换回归及报告验证脚本。部分脚本固定了本机工作区或 `/tmp/lexaid-full-review` 路径；异机回放需调整路径。生成迁移文件的脚本只生成文件；数据库测试脚本须检查其目标为隔离库。
- `complete-importer-comparison.json`：43 个固定上游文件、24,150 行结构转换对照。
- `formal-real-source-check.jsonl.gz`：298 道历年客观题的答案键、原卷选项对照。来源是署明转载司法部网站的历史页面；不代表按现行法重判。
- `validation.json`、`viewer-check.json`、`merge-regression-check.json`：本地代码、浏览页、失配指纹及补充 URL 检查结果。

在同一工作区重新汇总，可先将两个归档解压至 `/tmp/lexaid-full-review`，随后执行主报告中的两条 Python 命令。请在空目录恢复证据，避免覆盖其他审计输入。两条汇总命令不写数据库。原始审计内容指纹对应 2026-11-11 的基线，修订后指纹在父目录 `after/` 和 `applied-revisions.json` 中。

脚本的指纹断言将来源导出、阅读分片、基础审计、上游核对串联；报告生成还会对照基线与当前清单。曾可复现的失配样本现在返回失败。官方 URL 包括主依据及 `additionalOfficialURL` 等补充依据。

`../files-manifest.json` 记录最终交付文件的大小与 SHA-256。这里没有生产部署、人工双签、批量放行或对原始问答重新作答的记录。
