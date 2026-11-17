#!/usr/bin/env python3
"""Apply a guarded mixed question/mock-exam segment to a local LexAid database."""
import argparse
import hashlib
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
builder_spec = importlib.util.spec_from_file_location('teaching_segment', ROOT / 'scripts/build-reviewed-teaching-segment.py')
builder = importlib.util.module_from_spec(builder_spec)
builder_spec.loader.exec_module(builder)
question = builder.question

HISTORY_TABLES = ['answer_record', 'exam_session', 'mock_exam_item', 'mock_exam_session',
                  'question_quality_review', 'question_source']


def load(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def logical_question_content(content):
    """Convert MULTIPLE answers serialized as JSON text to their logical array."""
    result = dict(content)
    # This review-only proposal flag is bound by afterHash but is not a
    # question column and is deliberately omitted from the persisted revision.
    result.pop('readyForApplication', None)
    if result.get('type') in {'SINGLE', 'MULTIPLE'} and isinstance(result.get('answer'), str):
        try:
            decoded = json.loads(result['answer'])
        except (TypeError, json.JSONDecodeError):
            decoded = None
        if isinstance(decoded, list):
            result['answer'] = decoded
    return result


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--batch', required=True)
    ap.add_argument('--segment', required=True)
    ap.add_argument('--database', required=True)
    ap.add_argument('--output-root', type=Path, default=ROOT / 'docs/teaching/2026-11-12/batches')
    ap.add_argument('--applications', type=Path, default=ROOT / 'datasets/question-explanations/applications.json')
    args = ap.parse_args()
    if not re.fullmatch(r'\d{3}', args.batch): ap.error('batch must be three digits')
    if not re.fullmatch(r'segment-[a-f0-9]{12}', args.segment): ap.error('invalid segment ID')
    if args.database not in {'lexaid', 'lexaid_upgrade_20261111_check'}: ap.error('database must be a named local development database')
    directory = args.output_root / args.batch / 'segments' / args.segment
    revisions = load(directory / 'revisions.jsonl')
    if not revisions:
        raise ValueError('empty segment')
    for row in revisions:
        review = row['crossReview']
        if (row.get('readyForApplication') is not True or row.get('reviewerKind') != 'AI' or row.get('humanApproval') is not False
                or review.get('verdict') != 'PASS' or review.get('findings') or review.get('reviewerKind') != 'AI'
                or review.get('humanApproval') is not False or review.get('reviewer') == row.get('author')
                or review.get('author') != row.get('author') or review.get('afterHash') != question.digest(row['after'])
                or row.get('afterHash') != question.digest(row['after'])):
            raise ValueError(f"proposal is not currently independently reviewed: {row.get('target')}")
        legal_basis_hash = review.get('legalBasisHash')
        expected_legal_basis_hash = question.digest(row.get('legalBasis'))
        if ((legal_basis_hash is not None and legal_basis_hash != expected_legal_basis_hash)
                or (row.get('legalBasisReviewRequired') is True and legal_basis_hash != expected_legal_basis_hash)):
            raise ValueError(f"proposal legal sources are not currently independently reviewed: {row.get('target')}")
    targets = [row['target'] for row in revisions]
    if len(targets) != len(set(targets)):
        raise ValueError('duplicate targets in segment')
    sql_path = ROOT / f"backend/src/main/resources/teaching-batch-{args.batch}-{args.segment}.sql"
    if not sql_path.exists(): raise FileNotFoundError(sql_path)
    command = ['docker', 'exec', '-i', 'lexaid-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" ' + args.database]

    def query(statement):
        result = subprocess.run(command, input=statement, text=True, capture_output=True, check=True)
        return result.stdout

    def json_rows(output):
        return [json.loads(line) for line in output.splitlines() if line.startswith('{')]

    def preflight(revisions):
        """Reject every live-baseline mismatch before the SQL can partially apply a segment."""
        by_table = {name: [row for row in revisions if row['table'] == name]
                    for name in ('question', 'mock_exam_bank')}
        live = {}
        if by_table['question']:
            ids = ','.join(str(row['id']) for row in by_table['question'])
            statement = (f"SELECT JSON_OBJECT('target',CONCAT('question:',q.id),"
                         f"'before',{question.CURRENT_JSON},'after',{builder.question_after_current()},"
                         f"'hash',{question.HASH_SQL}) "
                         f"FROM {question.JOIN} WHERE q.id IN ({ids}) ORDER BY q.id;")
            live.update({row['target']: row for row in json_rows(query(statement))})
        if by_table['mock_exam_bank']:
            ids = ','.join(str(row['id']) for row in by_table['mock_exam_bank'])
            statement = (f"SELECT JSON_OBJECT('target',CONCAT('mock_exam_bank:',q.id),"
                         f"'before',{builder.mock_current('q')},'after',{builder.mock_current('q')}) "
                         f"FROM mock_exam_bank q WHERE q.id IN ({ids}) ORDER BY q.id;")
            live.update({row['target']: row for row in json_rows(query(statement))})

        archive_tables = {
            'question': ('question_content_revision', by_table['question']),
            'mock_exam_bank': ('mock_exam_content_revision', by_table['mock_exam_bank']),
        }
        archives = {}
        for table, (archive_table, group) in archive_tables.items():
            if not group:
                continue
            if not query("SHOW TABLES LIKE '" + archive_table + "';").strip():
                continue
            keys = ','.join("'" + row['revisionKey'] + "'" for row in group)
            rows = json_rows(query(
                f"SELECT JSON_OBJECT('revision_key',revision_key,'question_id',question_id,"
                f"'before_content',before_content,'after_content',after_content,"
                f"'evidence_urls',evidence_urls,'applied_at',applied_at) "
                f"FROM {archive_table} WHERE revision_key IN ({keys});"))
            archives.update({row['revision_key']: row for row in rows})

        for row in revisions:
            target = row['target']
            current = live.get(target)
            if current is None:
                raise ValueError(f'Preflight refused missing target {target}')
            if row['table'] == 'question':
                expected_before = {**row['before'], **row['beforeClassification']}
                expected_after = {**logical_question_content(row['after']), **row['afterClassification']}
                archive_after = {key: row['after'][key] for key in question.FIELDS}
                archive_after.update(row['afterClassification'])
            else:
                expected_before, expected_after = row['before'], row['after']
                archive_after = expected_after
            urls = sorted({basis['url'] for basis in row['legalBasis']})
            archive = archives.get(row['revisionKey'])
            if archive:
                archive_matches = (
                    archive['question_id'] == row['id']
                    and archive['before_content'] == expected_before
                    and archive['after_content'] == archive_after
                    and archive['evidence_urls'] == urls
                )
                if not archive_matches:
                    raise ValueError(f'Preflight refused archive conflict for {target}')
                if archive['applied_at'] is not None and current['after'] == expected_after:
                    continue
                if (archive['applied_at'] is None and current['before'] == expected_before
                        and current.get('hash') == row['expectedContentHash']):
                    continue
                # A previous guarded SQL run may have written the exact after-state
                # before its final marker/status check failed. An exact unapplied
                # archive plus an exact live after-state is safe to reconcile.
                if archive['applied_at'] is None and current['after'] == expected_after:
                    continue
                raise ValueError(f'Preflight refused changed baseline for {target}')
            if (current['before'] != expected_before
                    or row['table'] == 'question' and current.get('hash') != row['expectedContentHash']):
                raise ValueError(f'Preflight refused changed baseline for {target}')

    def checksums(tables):
        lines = [line.split('\t') for line in query('CHECKSUM TABLE ' + ','.join(tables) + ' EXTENDED;').splitlines()]
        if any(len(line) != 2 or line[1] == 'NULL' for line in lines):
            raise ValueError('Failed to capture stable table checksums')
        return {line[0].split('.')[-1]: line[1] for line in lines}

    preflight(revisions)
    before_history = checksums(HISTORY_TABLES)
    applied = json_rows(query(sql_path.read_text()))
    if len(applied) != len(revisions) or any(row.get('status') != 'APPLIED_OR_ALREADY_APPLIED' for row in applied):
        raise ValueError('One or more rows were not applied: ' + json.dumps(applied, ensure_ascii=False))

    by_table = {name: [row for row in revisions if row['table'] == name] for name in ('question', 'mock_exam_bank')}
    exact = {}
    if by_table['question']:
        ids = ','.join(str(row['id']) for row in by_table['question'])
        statement = f"SELECT JSON_OBJECT('id',q.id,'content',{builder.question_after_current()}) FROM {question.JOIN} WHERE q.id IN ({ids}) ORDER BY q.id;"
        exact.update({f"question:{row['id']}": row['content'] for row in json_rows(query(statement))})
    if by_table['mock_exam_bank']:
        ids = ','.join(str(row['id']) for row in by_table['mock_exam_bank'])
        statement = f"SELECT JSON_OBJECT('id',q.id,'content',{builder.mock_current('q')}) FROM mock_exam_bank q WHERE q.id IN ({ids}) ORDER BY q.id;"
        exact.update({f"mock_exam_bank:{row['id']}": row['content'] for row in json_rows(query(statement))})
    if set(exact) != set(targets): raise ValueError('Post-apply target set differs from revision set')
    for row in revisions:
        expected = logical_question_content(row['after']) if row['table'] == 'question' else row['after']
        if row['table'] == 'question': expected = {**expected, **row['afterClassification']}
        if exact[row['target']] != expected:
            raise ValueError(f"Post-apply content differs for {row['target']}")

    archive = {}
    for table, archive_table in (('question', 'question_content_revision'), ('mock_exam_bank', 'mock_exam_content_revision')):
        group = by_table[table]
        if not group: continue
        keys = ','.join("'" + row['revisionKey'] + "'" for row in group)
        counts = json_rows(query(f"SELECT JSON_OBJECT('total',COUNT(*),'applied',COUNT(applied_at)) FROM {archive_table} WHERE revision_key IN ({keys});"))
        expected_count = {'total': len(group), 'applied': len(group)}
        if counts != [expected_count]: raise ValueError(f'{archive_table} archive count mismatch: {counts}')
        archive[archive_table] = expected_count

    mutable_tables = ['question', 'question_legal_subject', 'mock_exam_bank']
    for table in ('question_content_revision', 'mock_exam_content_revision'):
        exists = query("SHOW TABLES LIKE '" + table + "';").strip()
        if exists: mutable_tables.append(table)
    once = checksums(mutable_tables)
    repeated = json_rows(query(sql_path.read_text()))
    if len(repeated) != len(revisions) or any(row.get('status') != 'APPLIED_OR_ALREADY_APPLIED' for row in repeated):
        raise ValueError('Repeat did not return an idempotent success')
    twice = checksums(mutable_tables)
    after_history = checksums(HISTORY_TABLES)
    if once != twice: raise ValueError('Repeated migration changed content or archive checksums')
    if before_history != after_history: raise ValueError('Answer history or active exam snapshots changed')

    records = [{'target': row['target'], 'id': row['id'], 'table': row['table'],
                'status': 'APPLIED_OR_ALREADY_APPLIED', 'contentHash': row['afterHash'], 'afterHash': row['afterHash'],
                'revisionKey': row['revisionKey']} for row in revisions]
    report = {'batch': args.batch, 'segment': args.segment, 'database': args.database,
              'verifiedAt': datetime.now(timezone.utc).isoformat(),
              'migration': str(sql_path.relative_to(ROOT)),
              'migrationSha256': hashlib.sha256(sql_path.read_bytes()).hexdigest(),
              'records': records, 'exactAfterMatches': len(revisions), 'archives': archive,
              'historyBefore': before_history, 'historyAfter': after_history,
              'historyPreserved': True, 'repeatChecksums': twice, 'idempotent': True,
              'reviewerKind': 'AI', 'humanApproval': False, 'releaseApproval': False}
    (directory / 'applications.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')

    batch_report_path = args.output_root / args.batch / 'applications.json'
    prior = json.loads(batch_report_path.read_text()) if batch_report_path.exists() else {'batch': args.batch, 'database': args.database, 'segments': [], 'records': []}
    segments = {item['segment']: item for item in prior.get('segments', [])}
    segments[args.segment] = {key: report[key] for key in ('segment', 'migration', 'migrationSha256', 'exactAfterMatches', 'historyPreserved', 'idempotent', 'releaseApproval')}
    merged = {row['target']: row for row in prior.get('records', [])}
    for row in records:
        if row['target'] in merged and merged[row['target']].get('contentHash') != row['contentHash']:
            raise ValueError('Batch application history has a different hash for ' + row['target'])
        merged[row['target']] = row
    batch_report = {'batch': args.batch, 'database': args.database,
                    'segments': list(segments.values()), 'records': list(merged.values()),
                    'exactAfterMatches': len(merged), 'historyPreserved': all(item.get('historyPreserved') for item in segments.values()),
                    'idempotent': all(item.get('idempotent') for item in segments.values()), 'releaseApproval': False}
    batch_report_path.write_text(json.dumps(batch_report, ensure_ascii=False, indent=2) + '\n')
    aggregate = json.loads(args.applications.read_text()) if args.applications.exists() else {'records': []}
    all_records = {row['target']: row for row in aggregate.get('records', []) if row.get('target')}
    for row in aggregate.get('records', []):
        if not row.get('target'):
            all_records[f"{row.get('table', 'question')}:{row['id']}"] = row
    for row in records:
        prior_row = all_records.get(row['target'])
        if prior_row and prior_row.get('contentHash') != row['contentHash']:
            raise ValueError('Aggregate application history has a different hash for ' + row['target'])
        all_records[row['target']] = row
    args.applications.parent.mkdir(parents=True, exist_ok=True)
    args.applications.write_text(json.dumps({'records': list(all_records.values())}, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'records'}, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
