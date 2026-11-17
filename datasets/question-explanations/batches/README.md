# Subsequent batch workspace

The live queue is `../plan.jsonl`; source snapshots are frozen from the local database and must match each target's planned content hash before authoring. Export pending rows with:

```bash
python3 scripts/export-teaching-batch-source.py --batches 009,010,011
```

This writes one `source.jsonl` under `docs/teaching/2026-11-12/batches/<batch>/`. A row keeps the queue target and a complete `source` copy. For a `question`, `source.before` contains the original six question fields and `source.beforeClassification` records discipline and subject. For a `mock_exam_bank` record, `source.before` retains all 13 legal content/provenance fields. The snapshot is read-only evidence; later database drift requires a new snapshot and renewed review.

Store one author JSONL file per batch. For linked daily and mock exam records, author both targets in the same batch and carry `contentGroup` into each proposal. Each proposal records the target string, exact expected hash, complete `before` and `after`, legalBasis items with law/article/version/official URL, specific changes, review notes, `readyForApplication`, `reviewerKind:'AI'`, and `humanApproval:false`. Question explanations and mock bank explanations use the four sections `【适用范围】【法律依据】【推理过程】【易错点】` and must teach the actual question, not echo an audit finding.

Independent reviews use the canonical digest of the proposal's `after` object: recursively sort object keys, serialize compact JSON as UTF-8 with Unicode preserved, then compute SHA-256. Bind each review to that digest. After any substantive edit, rerun the independent review. A PASS from an AI reviewer remains an AI review, never a human signoff.

Applications will be generated from complete reviewed batches with content-hash guards, immutable before/after archives, idempotent output, and row-level skip reasons. Daily question corrections write `question_content_revision`; formal bank corrections write `mock_exam_content_revision`. Never update an active `mock_exam_item` snapshot or answer history. Keep every revised source candidate on the formal release review gate.

## Building and applying reviewed segments

Linked daily/mock records are validated and applied as one content group. A batch may be processed in reviewed segments while the remaining groups are being authored or held for human review. The builder only includes complete groups with a matching baseline, a current independent AI `PASS`, `readyForApplication: true`, and no application record; incomplete or held groups are listed in the segment summary.

```bash
python3 scripts/build-reviewed-teaching-segment.py --batch 009
python3 scripts/apply-reviewed-teaching-segment.py \
  --batch 009 --segment segment-<hash-from-build-summary> --database lexaid
```

The build writes normalized revisions and a guarded migration under `docs/teaching/2026-11-12/batches/<batch>/segments/<segment>/` and `backend/src/main/resources/`. The apply command is restricted to the named local development databases. It verifies exact after-content, revision archives, repeat idempotency, and unchanged answer history and active exam snapshots. It updates the aggregate application index used by `plan-question-explanations.py`; no AI review is treated as human or release approval.

Keep the live cross-review records consolidated in `datasets/question-explanations/batches/<batch>/review-<batch>.jsonl`. Raw, superseded, stale-hash, or requested-change review fragments belong under the corresponding `docs/teaching/.../review-evidence/` directory so the planner does not mistake them for current approval or report duplicate reviews.
