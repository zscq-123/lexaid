#!/usr/bin/env python3
"""Apply one fully cross-reviewed question batch to an approved local database."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('batch', ROOT / 'scripts/build-teaching-question-batch.py')
batch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(batch)
HISTORY = ['answer_record', 'exam_session', 'mock_exam_item', 'mock_exam_session', 'question_quality_review', 'mock_exam_bank', 'question_source']


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--batch', required=True)
    ap.add_argument('--database', required=True)
    args = ap.parse_args()
    if not re.fullmatch(r'\d{3}', args.batch): ap.error('batch must be three digits')
    # The migration path is intentionally restricted to the two local development databases.
    if args.database not in {'lexaid', 'lexaid_upgrade_20261111_check'}: ap.error('database must be a named local development database')
    directory = ROOT / f'docs/teaching/2026-11-12/batches/{args.batch}'
    revisions = batch.records(directory / 'revisions.jsonl')
    if not revisions or any(r['crossReview']['verdict'] != 'PASS' or r['crossReview']['humanApproval'] is not False or r['crossReview']['reviewer'] == r['crossReview']['author'] or r['crossReview']['afterHash'] != batch.digest(r['after']) for r in revisions):
        raise ValueError('All proposals must have a current, independent AI PASS before local application')
    if any(not r['reviewerKind'] == 'AI' or r['humanApproval'] is not False for r in revisions):
        raise ValueError('AI proposals must not claim human approval')
    ids = [r['id'] for r in revisions]
    if len(ids) != len(set(ids)): raise ValueError('duplicate question IDs')
    sqlpath = ROOT / f'backend/src/main/resources/question-teaching-batch-{args.batch}.sql'
    sql = sqlpath.read_text()
    command = ['docker', 'exec', '-i', 'lexaid-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" ' + args.database]

    def query(statement):
        result = subprocess.run(command, input=statement, text=True, capture_output=True, check=True)
        return result.stdout

    def checksums(tables):
        lines = [line.split('\t') for line in query('CHECKSUM TABLE ' + ','.join(tables) + ' EXTENDED;').splitlines()]
        if any(len(line) != 2 or line[1] == 'NULL' for line in lines): raise ValueError('Failed to capture a stable table checksum')
        return {line[0].split('.')[-1]: line[1] for line in lines}

    before_history = checksums(HISTORY)
    applied = [json.loads(line) for line in query(sql).splitlines() if line.startswith('{')]
    if len(applied) != len(revisions) or any(row['status'] != 'APPLIED_OR_ALREADY_APPLIED' for row in applied):
        raise ValueError('One or more rows were not applied: ' + json.dumps([r for r in applied if r.get('status') != 'APPLIED_OR_ALREADY_APPLIED'], ensure_ascii=False))
    id_list = ','.join(map(str, ids))
    current = [json.loads(line) for line in query('SELECT JSON_OBJECT(\'id\',q.id,\'content\',' + batch.CURRENT_JSON + ',\'hash\',' + batch.HASH_SQL + ') FROM ' + batch.JOIN + ' WHERE q.id IN (' + id_list + ');').splitlines()]
    current_by_id = {row['id']: row for row in current}
    if set(current_by_id) != set(ids): raise ValueError('Post-apply question set is incomplete')
    for row in revisions:
        if current_by_id[row['id']]['content'] != {**row['after'], **row['afterClassification']}:
            raise ValueError(f'Post-apply content differs for question:{row["id"]}')
    prefix = f'2026-11-12-teaching-{args.batch}-%'
    archive = json.loads(query("SELECT JSON_OBJECT('total',COUNT(*),'applied',COUNT(applied_at)) FROM question_content_revision WHERE revision_key LIKE '" + prefix + "';"))
    if archive != {'total': len(revisions), 'applied': len(revisions)}: raise ValueError(f'Archive count mismatch: {archive}')
    mutable_tables = ['question', 'question_legal_subject', 'question_content_revision']
    once = checksums(mutable_tables)
    repeated = [json.loads(line) for line in query(sql).splitlines() if line.startswith('{')]
    if len(repeated) != len(ids) or any(row['status'] != 'APPLIED_OR_ALREADY_APPLIED' for row in repeated): raise ValueError('Repeat did not return an idempotent success')
    twice = checksums(mutable_tables)
    after_history = checksums(HISTORY)
    if once != twice: raise ValueError('Repeated migration changed database checksums')
    if before_history != after_history: raise ValueError('Answer or exam history changed')
    after_hashes = {f"question:{row['id']}": row['afterHash'] for row in revisions}
    records = [{'target': f"question:{row['id']}", 'id': row['id'], 'table': 'question', 'status': row['status'], 'contentHash': row['contentHash'], 'afterHash': after_hashes[f"question:{row['id']}"]} for row in applied]
    report = {'batch': args.batch, 'database': args.database, 'verifiedAt': datetime.now(timezone.utc).isoformat(),
              'migrationSha256': __import__('hashlib').sha256(sqlpath.read_bytes()).hexdigest(), 'records': records,
              'exactAfterMatches': len(ids), 'archive': archive, 'historyBefore': before_history,
              'historyAfter': after_history, 'historyPreserved': True, 'repeatChecksums': twice,
              'idempotent': True, 'reviewerKind': 'AI', 'humanApproval': False, 'releaseApproval': False}
    (directory / 'applications.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    aggregate_path = ROOT / 'datasets/question-explanations/applications.json'
    existing = json.loads(aggregate_path.read_text())['records'] if aggregate_path.exists() else []
    merged = {row.get('target') or f"question:{row['id']}": row for row in existing}
    for row in records:
        if row['target'] in merged and merged[row['target']].get('contentHash') != row['contentHash']:
            raise ValueError('Application history already contains a different hash for ' + row['target'])
        merged[row['target']] = row
    aggregate_path.write_text(json.dumps({'records': list(merged.values())}, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'records'}, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
