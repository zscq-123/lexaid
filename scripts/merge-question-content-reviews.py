#!/usr/bin/env python3
"""Combine source checks and separately written AI reading records; never create approvals."""
import argparse
from collections import Counter
import json
from pathlib import Path
import shutil


def lines(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line]


def write_lines(path, rows):
    path.write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in rows))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    source = {row['id']: row for row in lines(args.input / 'lecoqa-source.jsonl')}
    ordered_ids = sorted(source)
    assert len(ordered_ids) == 1543
    reading = {}
    shard_counts = {}
    for name, start, stop in [('backend', 0, 515), ('frontend', 515, 1030), ('legal', 1030, 1543)]:
        records = lines(args.input / f'lecoqa-semantic-{name}.jsonl')
        assert len(records) == stop - start, f'{name}: incomplete shard'
        assert {r['id'] for r in records} == set(ordered_ids[start:stop]), f'{name}: incorrect scope'
        for row in records:
            qid = row['id']
            assert qid not in reading and row['contentHash'] == source[qid]['contentHash']
            assert {'stem', 'answer', 'analysis'} <= set(row['fieldsRead'])
            assert row['reviewerKind'] == 'AI' and row.get('humanReview') is not True
            assert row['decision'] in {'CONFIRMED_DEFECT', 'REQUIRES_MORE_VERIFICATION', 'NO_SPECIFIC_DEFECT_IDENTIFIED'}
            assert len(row['reason'].strip()) >= 10, f'{qid}: missing concrete reading note'
            assert not row.get('humanDualApproval', False)
            if row['decision'] == 'CONFIRMED_DEFECT':
                assert row.get('confirmedIssues'), f'{qid}: confirmed finding has no evidence record'
            reading[qid] = row
        shard_counts[name] = len(records)
    base = lines(args.input / 'lecoqa.jsonl')
    assert len(base) == len(source) and {r['id'] for r in base} == set(source)
    checks = {r['id']: r for r in lines(args.input / 'lecoqa-source-verification.jsonl')}
    for row in base:
        reviewed = reading[row['id']]
        assert reviewed['contentHash'] == row['contentHash'] == source[row['id']]['contentHash'], f"{row['id']}: reading/base/source fingerprints differ"
        row['automatedAndInitialRiskStatus'] = row['status']
        prior_findings = [finding for finding in row.get('manualFindings', []) if finding.get('level') == 'confirmed']
        row['status'] = 'CONFIRMED_DEFECT' if prior_findings else reviewed['decision']
        row['semanticReview'] = reviewed
        row['initialRiskReading'] = row.get('semanticRead')
        row['semanticRead'] = {'stem': True, 'answer': True, 'analysis': 'FULL', 'completeRecord': True, 'reviewerKind': 'AI'}
        row['modelReading'] = {'status': 'AI_COMPLETE_FIELDS_READ', 'scope': '完整阅读题干、答案、解析；具体确认与未核验事项见 semanticReview', 'fieldsRead': reviewed['fieldsRead']}
        row['legalReasoningVerified'] = reviewed['legalReasoningVerified']
        row['legalCorrectness'] = 'NOT_FULLY_VERIFIED' if not reviewed['legalReasoningVerified'] else 'VERIFIED_WITHIN_RECORDED_SCOPE'
        row['actions'] = [reviewed['reason']] + [finding['conclusion'] for finding in prior_findings] + row.get('unresolved', [])
        if row['status'] != reviewed['decision']:
            row['reviewReconciliation'] = '保留先前已附依据的具体缺陷；本轮全文阅读记录未构成对该窄点结论的反证。'
        if row['status'] == 'CONFIRMED_DEFECT':
            row['flags'] = row.get('flags', []) + ['CONFIRMED_CONTENT_ISSUE']
        check = checks[row['id']]
        assert check['matched'] and check['contentHash'] == row['contentHash']
        row['sourceVerification'] = [{'upstreamUrl': check['sourceUrl'], 'sourceItemId': check['sourceItemId'], 'matched': True}]
        urls = []
        for issue in reviewed.get('confirmedIssues', []):
            for key in ['officialUrl', 'officialURL', 'additionalOfficialUrl', 'additionalOfficialURL']:
                if issue.get(key):
                    urls.extend(issue[key] if isinstance(issue[key], list) else [issue[key]])
            urls += issue.get('officialUrls', [])
            urls += [item['url'] for item in issue.get('officialSources', []) if item.get('url')]
        for finding in prior_findings:
            urls += finding.get('officialUrls', [])
        row['sourceUrls'] = sorted(set(row.get('sourceUrls', []) + urls))
    write_lines(args.output / 'lecoqa.jsonl', base)
    write_lines(args.output / 'lecoqa-semantic.jsonl', [reading[qid] for qid in sorted(reading)])
    independent = json.loads((args.input / 'corrections-independent-review.json').read_text())
    independent_records = {r['id']: r for r in independent['records']}
    concerns = {r['id']: r for r in independent['substantiveContentFlags']}
    lex = lines(args.input / 'lexeval.jsonl')
    for row in lex:
        if row['id'] in independent_records:
            detail = independent_records[row['id']]
            assert detail['expectedContentHash'] == row['contentHash'] and detail['manualFullContentRead']
            row['independentFullContentRead'] = True
            row['independentStructureReview'] = detail
        if row['id'] in concerns:
            concern = concerns[row['id']]
            row['substantiveConcern'] = concern
            row['reasonCodes'] += [concern['status']]
            row['actions'] += [concern['finding']]
            row['sourceUrls'] = [concern['officialSource']]
    write_lines(args.output / 'lexeval.jsonl', lex)
    for name in ['lawbench.jsonl', 'seed.jsonl', 'formal.jsonl']:
        shutil.copyfile(args.input / name, args.output / name)
    summary = {'lecoqa': {'total': len(base), 'fullFieldsReadByAI': len(reading), 'shards': shard_counts,
                          'semanticDecisions': dict(Counter(r['decision'] for r in reading.values())),
                          'combinedDecisions': dict(Counter(r['status'] for r in base)),
                          'priorFindingsRetainedBeyondShardDecision': sum('reviewReconciliation' in r for r in base),
                          'legalReasoningFullyVerified': sum(r['legalReasoningVerified'] for r in reading.values()),
                          'confirmedIssues': sum(len(r.get('confirmedIssues', [])) for r in reading.values())},
               'lexevalIndependentCompleteRead': len(independent_records),
               'humanApprovalsCreated': 0,
               'scope': 'Complete reading records are not full legal certification; source consistency and individual issue findings remain separate.'}
    (args.output / 'content-review-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
