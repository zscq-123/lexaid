# Batch 004 — original mock teaching cases

- Scope: 30 original `mock_exam_bank` cases.
- Result: 30/30 have complete teaching explanations, current independent AI review, and local application to `lexaid`.
- Storage: 30 immutable before/after entries in `mock_exam_content_revision`.
- Safeguards: all 30 after-values matched exactly; replay was idempotent; answer records and active exam snapshots kept the same checksums.
- Release: still requires the existing human/formal release review. AI authorship and cross-review do not count as human approval.

The independent review led to concrete corrections before application: the earnest-money case treats the stated ¥260,000 as recoverable loss and the separate ¥40,000 as avoidable expanded loss; the administrative hearing request period is five days; the ratification example under Civil Code Article 503 is conditional because the stem does not state later performance; and the administrative hearing fee is borne by the authority. Raw and superseded review fragments are preserved in `review-evidence/`; `review-004.jsonl` is the current consolidated review set.

The guarded migrations and per-segment application records are in `segments/`.
