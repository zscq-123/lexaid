#!/usr/bin/env python3
"""Build a guarded migration from one fully authored and cross-reviewed question batch."""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'scripts/build-teaching-question-batch.py'
spec = importlib.util.spec_from_file_location('teaching_batch', MODULE)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


def load(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--batch', required=True, help='Three digit queue batch ID')
    ap.add_argument('--source-root', type=Path, default=ROOT / 'docs/teaching/2026-11-12/batches')
    ap.add_argument('--draft-root', type=Path, default=ROOT / 'datasets/question-explanations/batches')
    ap.add_argument('--output-root', type=Path, default=ROOT / 'docs/teaching/2026-11-12/batches')
    ap.add_argument('--sql-root', type=Path, default=ROOT / 'backend/src/main/resources')
    args = ap.parse_args()
    source_file = args.source_root / args.batch / 'source.jsonl'
    if not source_file.exists(): raise FileNotFoundError(source_file)
    source_rows = load(source_file)
    sources, drafts = {}, {}
    for row in source_rows:
        if row['target']['table'] != 'question':
            raise ValueError('This builder applies question rows only')
        target = row['target']['target']
        if target in sources: raise ValueError(f'Duplicate source target {target}')
        if row['target']['baselineHash'] != row['source']['contentHash']:
            raise ValueError(f'Snapshot and queue hash differ for {target}')
        sources[target] = row
    for file in sorted((args.draft_root / args.batch).glob('*.jsonl')):
        if file.name.startswith('review-'): continue
        for row in load(file):
            target = row.get('target')
            if isinstance(target, dict): target = target.get('target')
            if target in drafts: raise ValueError(f'Duplicate authored target {target}')
            drafts[target] = row
    if drafts.keys() != sources.keys():
        raise ValueError(f'Coverage mismatch: proposals={len(drafts)}, sourceRows={len(sources)}')
    reviews = {}
    for file in sorted(args.draft_root.glob('review-*.jsonl')):
        for row in load(file):
            target = row.get('target') or (f'question:{row["id"]}' if 'id' in row else None)
            if not target: continue
            if target in reviews: raise ValueError(f'Duplicate review for {target}')
            reviews[target] = row
    if not sources.keys() <= reviews.keys():
        raise ValueError(f'Independent review coverage short by {len(sources.keys() - reviews.keys())} records')

    originals, metadata, normalized_drafts, normalized_reviews = {}, {}, {}, {}
    for target, source in sources.items():
        draft, review, snapshot = drafts[target], reviews[target], source['source']
        qid = snapshot['id']
        if draft.get('expectedContentHash') != source['target']['baselineHash']:
            raise ValueError(f'Proposal baseline differs: {target}')
        originals[qid] = {**snapshot['before'], 'contentHash': snapshot['contentHash']}
        metadata[qid] = snapshot['beforeClassification']
        proposal = {**draft, 'id': qid}
        if isinstance(proposal.get('target'), dict): proposal['target'] = target
        normalized_drafts[qid] = proposal
        normalized_reviews[qid] = review
    rows = builder.validate(normalized_drafts, normalized_reviews, originals, metadata, set(originals))
    for row in rows:
        row['revisionKey'] = f"2026-11-12-teaching-{args.batch}-{row['id']}"
        row['target'] = sources[f"question:{row['id']}"]['target']
    output = args.output_root / args.batch
    output.mkdir(parents=True, exist_ok=True)
    (output / 'revisions.jsonl').write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in rows))
    sql_path = args.sql_root / f'question-teaching-batch-{args.batch}.sql'
    sql_path.write_text(builder.build_sql(rows))
    all_subjects = Counter(row['afterClassification']['subject'] for row in rows)
    summary = {'batch': args.batch, 'records': len(rows), 'subjects': dict(all_subjects), 'independentReviewPass': True, 'reviewerKind': 'AI', 'humanApproval': False, 'releaseApproval': False, 'migrationSha256': hashlib.sha256(sql_path.read_bytes()).hexdigest()}
    (output / 'build-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
