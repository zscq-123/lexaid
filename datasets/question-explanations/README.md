# 教学题改写批次

范围按 2026-11-12 全量审计的教学候选清单固定，逐题修正题干、参考答案、法律版本、适用条件和推理过程。已有审计指出的错误优先处理；不能把 NLP 标注任务、精确预测基准或单纯输出格式说明直接包装成教学解析。

## 范围与分批

| 项目 | 数量 |
| --- | ---: |
| 日常题库教学候选 | 10,945 |
| 独立模拟卷记录 | 658 |
| 待处理目标记录 | 11,603 |
| 按已识别同源关系合并的内容组 | 11,278 |
| 日常题与模拟卷共享题源的记录 | 325 |
| 批次 | 277 |
| 第 001 批已确认问题题 | 216 |

内容组只合并已验证的 LexEval 源条目对应关系，不代表完成了所有题干的语义去重。第 001 批为 216 道已确认缺陷的 LeCoQA 题；后续优先处理种子题、原创模拟案例以及两处使用的同源题，再按学科处理其他候选。第 002 批起每批最多 50 个内容组，同组的日常题和模拟卷记录必须关联修订，不能只更新一处。277 是依照学科分组后的批次数，不是用总量直接除以 50。

`plan.jsonl` 保存每条记录的批次、原始内容哈希、来源、学科、关联目标和状态；`plan-summary.json` 汇总当前进度。历史真题应保留原考试时点，新增现行法对照解析；不得静默覆盖原卷答案或据现行法重新评分。

## 写作与审核状态

每题解析包含「适用范围」「法律依据」「推理过程」「易错点」。需要收窄题干时，记录具体改动及原因；不能补造案件事实。法条注明版本与官方链接，区分现行、历史及尚未生效的规则。

| 状态 | 含义 |
| --- | --- |
| `PENDING_AUTHORING` | 尚未完成教学改写 |
| `AUTHORING_REQUIRES_EVIDENCE` | 已有草稿但依据尚未解决 |
| `DRAFT_AWAITING_CROSS_REVIEW` | 作者认为可交叉复核，尚未证明复核通过 |
| `AWAITING_HUMAN_REVIEW` | 作者或证据明确要求法律人工复核；AI 复核 PASS 不会自动解除此状态 |
| `CROSS_REVIEWED_AWAITING_APPLICATION` | 独立 AI 交叉复核通过，内容仍须经守卫迁移应用 |
| `APPLIED_AWAITING_RELEASE_REVIEW` | 修订已写入本地题库；正式发布审核仍待完成 |

AI 编写与独立 AI 交叉审核记录均标为 `reviewerKind=AI`、`humanApproval=false`。交叉审核绑定修改后六个内容字段的 SHA-256；内容再次改变后，旧审核不能继续作为通过依据。分类修正另存前后值。迁移不写 `question_quality_review`，不会伪造人工双人审核或自动解除现有外部题库门禁。

## 第 001 批资料

- `2026-11-12/{backend,frontend,legal}.jsonl`：3 个作者分片，合计 216 道提案。
- `2026-11-12/review-*.jsonl`：独立交叉复核记录。
- `../../docs/teaching/2026-11-12/batch-001/revisions.jsonl`：合并后的修订档案，包括原文、改后内容、依据、修改点、分类和审核。
- `../../docs/teaching/2026-11-12/batch-001/preview.html`：可搜索的逐题预览。
- `../../backend/src/main/resources/question-teaching-batch-001.sql`：从已通过交叉复核的档案生成的迁移。

迁移先锁定目标行，核对六个原始内容字段、学科分类和原内容哈希，再归档和更新。重复执行不新增修订；内容漂移、缺失目标或同名归档冲突会明确报告。事务不会改写 `answer_record` 或历史模拟卷快照；更新题干或解析会让旧内容哈希对应的审核失效。

## 重建与验证

在仓库根目录执行，所有输入已留在仓库内，无需依赖 `/tmp`。

```bash
python3 scripts/build-teaching-question-batch.py \
  --drafts datasets/question-explanations/2026-11-12 \
  --originals docs/teaching/2026-11-12/batch-001/evidence/lecoqa-originals.jsonl.gz \
  --metadata docs/teaching/2026-11-12/batch-001/evidence/question-metadata.jsonl.gz \
  --audit docs/audits/2026-11-12/lecoqa.jsonl.gz \
  --output docs/teaching/2026-11-12/batch-001 \
  --sql backend/src/main/resources/question-teaching-batch-001.sql

python3 scripts/test-teaching-question-batch.py \
  --revisions docs/teaching/2026-11-12/batch-001/revisions.jsonl \
  --output docs/teaching/2026-11-12/batch-001/evidence/migration-tests.json

node scripts/teaching-preview-check.mjs

python3 scripts/plan-question-explanations.py \
  --audit docs/audits/2026-11-12 \
  --current docs/teaching/2026-11-12/batch-001/before/manifest.jsonl.gz \
  --metadata docs/teaching/2026-11-12/batch-001/evidence/question-metadata.jsonl.gz \
  --lexeval-source docs/teaching/2026-11-12/batch-001/evidence/lexeval-source-ids.jsonl.gz \
  --drafts datasets/question-explanations/2026-11-12 \
  --applications docs/teaching/2026-11-12/batch-001/applications.json \
  --output datasets/question-explanations
```

数据库测试使用会话临时表，不修改持久数据库；预览测试使用本地 Chrome（可用 `CHROME_BINARY` 指定路径）。实际迁移执行方法见主 README。当前批次的验证结果与尚未完成范围，以 `docs/teaching/2026-11-12/batch-001/README.md` 和队列状态为准。
