#!/usr/bin/env python3
"""Build a deterministic teaching rewrite queue from the audited candidate records."""
import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import re

TEACHING_SECTIONS = ('【适用范围】', '【法律依据】', '【推理过程】', '【易错点】')


def records(path):
    raw = gzip.decompress(path.read_bytes()).decode() if path.suffix == '.gz' else path.read_text()
    return [json.loads(line) for line in raw.splitlines() if line.strip()]


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--audit', required=True, type=Path)
    ap.add_argument('--current', required=True, type=Path)
    ap.add_argument('--metadata', required=True, type=Path)
    ap.add_argument('--lexeval-source', required=True, type=Path)
    ap.add_argument('--drafts', required=True, type=Path)
    ap.add_argument('--applications', type=Path)
    ap.add_argument('--output', required=True, type=Path)
    args = ap.parse_args()
    current = {r['id']: r for r in records(args.current)}
    metadata = {r['id']: r for r in records(args.metadata)}
    candidates, sources = set(), defaultdict(set)
    for dataset in ['seed', 'lawbench', 'lexeval', 'lecoqa']:
        for r in records(args.audit / (dataset + '.jsonl.gz')):
            qid = r['id']
            decision = r.get('decision', r.get('disposition', ''))
            if dataset in {'seed', 'lecoqa'} or decision.startswith('TEACHING_CANDIDATE'):
                candidates.add(qid)
                sources[qid].add(dataset)
    lecoqa = records(args.audit / 'lecoqa.jsonl.gz')
    first = {r['id'] for r in lecoqa if r['status'] == 'CONFIRMED_DEFECT'}
    assert len(first) == 216 and first <= candidates
    formal = records(args.audit / 'formal.jsonl.gz')
    lex = {r['sourceItemId']: r['id'] for r in records(args.lexeval_source)}
    groups = defaultdict(list)
    for qid in sorted(candidates):
        m = metadata[qid]
        priority = 0 if qid in first else 1 if qid < 10000000 else 3 if 'lecoqa' in sources[qid] else 4
        group = f'question:{qid}'
        groups[group].append({'target': group, 'table': 'question', 'id': qid, 'contentGroup': group,
                              'title': m['title'], 'subject': m['subject'], 'discipline': m['discipline'],
                              'sources': sorted(sources[qid]), 'baselineHash': current[qid]['contentHash'],
                              'baselineEligibility': current[qid]['reason'], 'priority': priority})
    for r in formal:
        match = next((re.search(r'/data/(\d_\d)\.json#L(\d+)$', u) for u in r['sourceUrls'] if re.search(r'/data/(\d_\d)\.json#L(\d+)$', u)), None)
        linked = lex.get(f'{match[1]}:{int(match[2])-1:04d}') if match else None
        group = f'question:{linked}' if linked in candidates else f'mock_exam_bank:{r["id"]}'
        priority = 2 if linked in candidates else 1 if r['id'] >= 8000000 else 5
        row = {'target': f'mock_exam_bank:{r["id"]}', 'table': 'mock_exam_bank', 'id': r['id'], 'contentGroup': group,
               'title': r['stem'][:180], 'subject': '跟随关联教学题' if linked in candidates else '原创综合案例' if priority == 1 else '2017年历史试题',
               'discipline': '模拟考试', 'sources': ['formal'], 'baselineHash': r['currentContentHash'],
               'baselineEligibility': 'INDEPENDENT_EXAM_POOL', 'priority': priority}
        groups[group].append(row)
        for sibling in groups[group]:
            sibling['priority'] = min(sibling['priority'], priority)
    batches = defaultdict(list)
    for key, group in groups.items():
        first_row = group[0]
        bucket = (min(r['priority'] for r in group), first_row['discipline'], first_row['subject'])
        batches[bucket].append((key, group))
    plan, batch_no = [], 1
    for priority in range(6):
        buckets = [key for key in batches if key[0] == priority]
        # The initial, explicitly confirmed defect batch is one auditable delivery.
        if priority == 0:
            ordered = sorted([item for key in buckets for item in batches[key]])
            segments = [ordered]
        else:
            segments = [sorted(batches[key])[i:i+50] for key in sorted(buckets) for i in range(0, len(batches[key]), 50)]
        for segment in segments:
            for key, group in segment:
                for row in group:
                    row['batch'] = f'{batch_no:03d}'
                    row['status'] = 'PENDING_AUTHORING'
                    row['relatedTargets'] = [r['target'] for r in group if r['target'] != row['target']]
                    plan.append(row)
            batch_no += 1
    drafts, reviews = {}, {}
    author_files = set(args.drafts.rglob('*.jsonl'))
    author_root = args.drafts.parent / 'batches'
    if author_root.exists(): author_files.update(author_root.rglob('*.jsonl'))
    for file in sorted(author_files):
        if file.name.startswith('review-'): continue
        for row in records(file):
            target = row.get('target')
            if isinstance(target, dict): target = target.get('target')
            if not target and 'id' in row: target = f'question:{row["id"]}'
            if not target: continue
            if target in drafts: raise ValueError(f'Duplicate authored record: {target}')
            drafts[target] = row
    for file in sorted(file for file in author_files if file.name.startswith('review-')):
        for row in records(file):
            target = row.get('target')
            if not target and 'id' in row: target = f'question:{row["id"]}'
            if not target: continue
            if target in reviews: raise ValueError(f'Duplicate cross-review: {target}')
            reviews[target] = row
    applications = {}
    if args.applications and args.applications.exists():
        for application in json.loads(args.applications.read_text())['records']:
            target = application.get('target') or f'question:{application["id"]}'
            if target in applications: raise ValueError(f'Duplicate application status: {target}')
            applications[target] = application
    for row in plan:
        draft = drafts.get(row['target'])
        if draft:
            expected = draft.get('expectedContentHash', draft.get('expectedContentHash'))
            if expected != row['baselineHash']: raise ValueError(f'Baseline hash differs: {row["target"]}')
            row['status'] = 'DRAFT_AWAITING_CROSS_REVIEW' if draft.get('readyForApplication') is True else 'AWAITING_HUMAN_REVIEW'
            review = reviews.get(row['target'])
            if review:
                review_hash = review.get('afterHash')
                if review_hash != digest(draft.get('after')):
                    row['reviewIssue'] = 'STALE_CROSS_REVIEW_HASH'
                    if draft.get('readyForApplication') is True:
                        row['status'] = 'AUTHORING_REQUIRES_EVIDENCE'
                elif ((review.get('legalBasisHash') is not None and review.get('legalBasisHash') != digest(draft.get('legalBasis')))
                      or (draft.get('legalBasisReviewRequired') is True and review.get('legalBasisHash') != digest(draft.get('legalBasis')))):
                    row['reviewIssue'] = 'STALE_OR_MISSING_LEGAL_BASIS_REVIEW_HASH'
                    if draft.get('readyForApplication') is True:
                        row['status'] = 'AUTHORING_REQUIRES_EVIDENCE'
                elif review.get('verdict') == 'PASS' and not review.get('findings') and review.get('reviewer') != review.get('author') and review.get('humanApproval') is False and draft.get('readyForApplication') is True:
                    row['status'] = 'CROSS_REVIEWED_AWAITING_APPLICATION'
                else:
                    row['reviewIssue'] = 'UNRESOLVED_OR_NOT_INDEPENDENT'
                    if draft.get('readyForApplication') is True:
                        row['status'] = 'AUTHORING_REQUIRES_EVIDENCE'
            teaching_field = 'analysis' if row['table'] == 'question' else 'explanation'
            teaching_text = (draft.get('after') or {}).get(teaching_field)
            if (draft.get('readyForApplication') is True
                    and (not isinstance(teaching_text, str) or len(teaching_text) < 150
                         or any(teaching_text.count(section) != 1 for section in TEACHING_SECTIONS))):
                row['status'] = 'AUTHORING_REQUIRES_EVIDENCE'
                row['reviewIssue'] = 'INVALID_TEACHING_EXPLANATION_SHAPE'
        application = applications.get(row['target'])
        if application and application['status'] == 'APPLIED_OR_ALREADY_APPLIED':
            applied_after_hash = application.get('afterHash')
            current_after_hash = digest(draft.get('after')) if draft else None
            if applied_after_hash and current_after_hash == applied_after_hash:
                row['status'] = 'APPLIED_AWAITING_RELEASE_REVIEW'
                row['appliedContentHash'] = applied_after_hash
            else:
                row['status'] = 'AUTHORING_REQUIRES_EVIDENCE'
                row['reviewIssue'] = 'APPLICATION_HASH_MISMATCH_OR_MISSING_AFTER_HASH'
    assert len(plan) == len({r['target'] for r in plan})
    summary = {'dailyCandidates': len(candidates), 'independentExamRecords': len(formal), 'targetRecords': len(plan),
               'contentGroups': len(groups), 'sharedSourceExamRecords': sum(r['table'] == 'mock_exam_bank' and bool(r['relatedTargets']) for r in plan),
               'batches': batch_no - 1, 'firstBatch': sum(r['batch'] == '001' for r in plan),
               'statuses': dict(Counter(r['status'] for r in plan)),
               'excludedBenchmarkRecords': len(current) - len(candidates),
               'scope': 'All audited teaching candidates plus independent exam records. NLP/exact-prediction benchmark tasks remain excluded, not relabeled as teaching explanations.'}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'plan.jsonl').write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in plan))
    (args.output / 'plan-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
