#!/usr/bin/env python3
"""Build guarded SQL for complete, cross-reviewed teaching content groups.

This supports question and mock_exam_bank rows in the same atomic segment. Groups
that are incomplete, held for human review, already applied, or not independently
reviewed are left out and described in build-summary.json.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import re
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('question_batch', ROOT / 'scripts/build-teaching-question-batch.py')
question = importlib.util.module_from_spec(spec)
spec.loader.exec_module(question)

MOCK_FIELDS = ('id', 'sourceType', 'paperType', 'kind', 'subject', 'difficulty', 'stem',
               'options', 'answer', 'explanation', 'sourceLabel', 'sourceUrl', 'readingUrl')
MOCK_SECTIONS = question.SECTIONS


def load(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def safe_name(value):
    return re.sub(r'[^A-Za-z0-9_-]+', '-', value).strip('-')


def mock_current(alias='q'):
    pairs = ('id', 'id', 'sourceType', 'source_type', 'paperType', 'paper_type', 'kind', 'kind',
             'subject', 'subject', 'difficulty', 'difficulty', 'stem', 'stem', 'options', 'options_json',
             'answer', 'reference_answer', 'explanation', 'explanation', 'sourceLabel', 'source_label',
             'sourceUrl', 'source_url', 'readingUrl', 'reading_url')
    return 'JSON_OBJECT(' + ','.join(f"'{pairs[i]}',{alias}.{pairs[i+1]}" for i in range(0, len(pairs), 2)) + ')'


def question_after_current():
    """Return the logical after-state of a question row.

    MULTIPLE answers are stored in the question.answer text column as JSON text,
    while reviewed proposals may represent the logical value as a JSON array.
    Keep the raw storage projection for before/hash checks, and parse only for
    comparisons against the reviewed after-state.
    """
    current = question.CURRENT_JSON
    answer_json = "JSON_EXTRACT(q.answer,'$')"
    return ("CASE WHEN q.type IN ('SINGLE','MULTIPLE') AND JSON_VALID(q.answer) THEN "
            f"CASE WHEN JSON_TYPE({answer_json})='ARRAY' "
            f"THEN JSON_SET({current},'$.answer',{answer_json}) "
            f"ELSE {current} END ELSE {current} END")


def question_after_proposal(alias='p'):
    """Normalize MULTIPLE answers serialized as JSON strings in reviewed drafts.

    The database's logical after projection exposes these as JSON arrays. Drafts
    and revision archives retain the original string representation for hashing
    and provenance, so SQL comparisons need a temporary logical projection.
    """
    content = f"{alias}.after_content"
    answer = f"JSON_EXTRACT({content},'$.answer')"
    decoded = f"JSON_EXTRACT(JSON_UNQUOTE({answer}),'$')"
    return (f"CASE WHEN JSON_UNQUOTE(JSON_EXTRACT({content},'$.type')) IN ('SINGLE','MULTIPLE') "
            f"AND JSON_TYPE({answer})='STRING' AND JSON_VALID(JSON_UNQUOTE({answer})) "
            f"AND JSON_TYPE({decoded})='ARRAY' "
            f"THEN JSON_SET({content},'$.answer',{decoded}) ELSE {content} END")


def collect_reviews(draft_root):
    result = {}
    files = set(draft_root.glob('review-*.jsonl'))
    for batch_dir in draft_root.iterdir():
        if batch_dir.is_dir():
            files.update(batch_dir.glob('review-*.jsonl'))
    for file in sorted(files):
        for row in load(file):
            target = row.get('target') or (f"question:{row['id']}" if row.get('id') is not None else None)
            if not target:
                continue
            if target in result:
                raise ValueError(f'Duplicate review for {target}; consolidate/retire stale review files first')
            result[target] = row
    return result


def author_for(file):
    name = file.name
    if name == 'frontend.jsonl': return 'luna_mock_author'
    if name == 'seed.jsonl' or name.startswith('seed-part') and name.endswith('.jsonl'): return 'luna_seed_author'
    if name.startswith('mock-part') and name.endswith('.jsonl'): return 'luna_mock_author'
    if name.startswith('daily-part') and name.endswith('.jsonl'): return 'luna_daily_author'
    if name == 'daily.jsonl': return 'luna_daily_author'
    # Early batches used agent task names rather than the common daily/seed names.
    if name == 'backend.jsonl': return 'luna_seed_author'
    return None


def validate_basis(draft, target):
    if not draft.get('changes'):
        raise ValueError(f'{target}: missing change rationale')
    bases = draft.get('legalBasis') or []
    if not bases:
        # Fact-reading questions can be grounded entirely in the source text
        # frozen in `before`/expectedContentHash. Do not force a fabricated
        # statute URL for questions whose category is explicitly factual.
        after = draft.get('after') or {}
        category = after.get('category') or (draft.get('before') or {}).get('category')
        analysis = after.get('analysis') or ''
        try:
            evidence_basis = analysis.split('【法律依据】', 1)[1].split('【推理过程】', 1)[0]
        except IndexError:
            evidence_basis = ''
        factual_categories = {'事实理解', '案例阅读', '文书审查'}
        factual_source = category in factual_categories and any(
            marker in evidence_basis for marker in ('题干', '证据', '材料', '裁判文书', '摘录')
        )
        stem = after.get('stem') or ''
        prompt_formula = (
            category == '法律推理'
            and '题干' in evidence_basis
            and any(marker in evidence_basis for marker in ('公式', '计算规则', '计息规则'))
            and any(marker in stem for marker in ('计算', '利息', '利率', '贴现'))
        )
        if not (factual_source or prompt_formula):
            raise ValueError(f'{target}: missing legal basis or explicit source-evidence basis')
        return
    for basis in bases:
        if not all(basis.get(key) for key in ('law', 'articles', 'version', 'url')):
            raise ValueError(f'{target}: incomplete legal basis')
        if not isinstance(basis['articles'], list) or not all(isinstance(value, str) and value.strip() for value in basis['articles']):
            raise ValueError(f'{target}: invalid legal article list')
        parsed = urlparse(basis['url'])
        if parsed.scheme != 'https' or not parsed.hostname:
            raise ValueError(f'{target}: legal source must be an HTTPS URL')


def validate_teaching_text(text, target):
    if not isinstance(text, str) or not text.strip():
        raise ValueError(f'{target}: empty teaching explanation')
    if any(text.count(section) != 1 for section in MOCK_SECTIONS):
        raise ValueError(f'{target}: explanation must contain each of the four teaching sections exactly once')
    if len(text) < 150:
        raise ValueError(f'{target}: explanation is too short to teach the question')


def sql_rows(rows):
    out = ["-- Generated by scripts/build-reviewed-teaching-segment.py.",
           "-- AI authored + independently AI reviewed; does not claim human or release approval.",
           'SET NAMES utf8mb4;']
    questions = [r for r in rows if r['table'] == 'question']
    mocks = [r for r in rows if r['table'] == 'mock_exam_bank']
    if questions:
        out.append("CREATE TABLE IF NOT EXISTS question_content_revision (revision_key VARCHAR(80) NOT NULL PRIMARY KEY, question_id BIGINT NOT NULL, before_content JSON NOT NULL, after_content JSON NOT NULL, evidence_urls JSON NOT NULL, applied_at DATETIME NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
        out.extend([
            'DROP TEMPORARY TABLE IF EXISTS teaching_question_proposal;',
            'CREATE TEMPORARY TABLE teaching_question_proposal (id BIGINT PRIMARY KEY,revision_key VARCHAR(80) NOT NULL,expected_hash CHAR(64) NOT NULL,before_content JSON NOT NULL,after_content JSON NOT NULL,evidence_urls JSON NOT NULL) ENGINE=InnoDB;',
            'INSERT INTO teaching_question_proposal VALUES'])
        values = []
        for row in questions:
            before = {**row['before'], **row['beforeClassification']}
            # Proposal-local readiness is review metadata, not a question
            # column. Keep it bound by the reviewed afterHash, but omit it from
            # the persisted question revision payload.
            after = {key: row['after'][key] for key in question.FIELDS}
            after.update(row['afterClassification'])
            urls = sorted({basis['url'] for basis in row['legalBasis']})
            values.append('(' + ','.join([str(row['id'])] + [question.text_sql(v) for v in (row['revisionKey'], row['expectedContentHash'], before, after, urls)]) + ')')
        out.append(',\n'.join(values) + ';')
    if mocks:
        out.append("CREATE TABLE IF NOT EXISTS mock_exam_content_revision (revision_key VARCHAR(80) NOT NULL PRIMARY KEY, question_id BIGINT NOT NULL, before_content JSON NOT NULL, after_content JSON NOT NULL, evidence_urls JSON NOT NULL, applied_at DATETIME NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
        out.extend([
            'DROP TEMPORARY TABLE IF EXISTS teaching_mock_proposal;',
            'CREATE TEMPORARY TABLE teaching_mock_proposal (id BIGINT PRIMARY KEY,revision_key VARCHAR(80) NOT NULL,before_content JSON NOT NULL,after_content JSON NOT NULL,evidence_urls JSON NOT NULL) ENGINE=InnoDB;',
            'INSERT INTO teaching_mock_proposal VALUES'])
        values = []
        for row in mocks:
            urls = sorted({basis['url'] for basis in row['legalBasis']})
            values.append('(' + ','.join([str(row['id'])] + [question.text_sql(v) for v in (row['revisionKey'], row['before'], row['after'], urls)]) + ')')
        out.append(',\n'.join(values) + ';')

    out.append('START TRANSACTION;')
    if questions:
        curr = question.CURRENT_JSON
        after_curr = question_after_current()
        after_proposal = question_after_proposal('p')
        before = question.same(curr, 'p.before_content') + ' AND ' + question.HASH_SQL + '=p.expected_hash'
        archive_match = "r.question_id=p.id AND " + ' AND '.join(question.same(f'r.{key}', f'p.{key}') for key in ('before_content', 'after_content', 'evidence_urls'))
        out.extend([
            'DROP TEMPORARY TABLE IF EXISTS teaching_question_ready;',
            'CREATE TEMPORARY TABLE teaching_question_ready (id BIGINT PRIMARY KEY) ENGINE=InnoDB;',
            f'INSERT INTO teaching_question_ready SELECT q.id FROM {question.JOIN} JOIN teaching_question_proposal p ON p.id=q.id WHERE {before} FOR UPDATE;',
            'INSERT INTO question_content_revision (revision_key,question_id,before_content,after_content,evidence_urls) SELECT p.revision_key,p.id,p.before_content,p.after_content,p.evidence_urls FROM teaching_question_proposal p JOIN teaching_question_ready t ON t.id=p.id ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;',
            f"UPDATE {question.JOIN} JOIN teaching_question_proposal p ON p.id=q.id JOIN teaching_question_ready t ON t.id=q.id JOIN question_content_revision r ON r.revision_key=p.revision_key SET " + ','.join(f"q.{key}=JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.{key}'))" for key in ('stem', 'answer', 'analysis')) + f' WHERE {before} AND {archive_match};',
            f"UPDATE question_content_revision r JOIN teaching_question_proposal p ON p.revision_key=r.revision_key JOIN ({question.JOIN}) ON q.id=p.id SET r.applied_at=COALESCE(r.applied_at,NOW()) WHERE {archive_match} AND {question.same(after_curr, after_proposal)};"
        ])
    if mocks:
        curr = mock_current('q')
        before = question.same(curr, 'p.before_content')
        archive_match = "r.question_id=p.id AND " + ' AND '.join(question.same(f'r.{key}', f'p.{key}') for key in ('before_content', 'after_content', 'evidence_urls'))
        out.extend([
            'DROP TEMPORARY TABLE IF EXISTS teaching_mock_ready;',
            'CREATE TEMPORARY TABLE teaching_mock_ready (id BIGINT PRIMARY KEY) ENGINE=InnoDB;',
            f'INSERT INTO teaching_mock_ready SELECT q.id FROM mock_exam_bank q JOIN teaching_mock_proposal p ON p.id=q.id WHERE {before} FOR UPDATE;',
            'INSERT INTO mock_exam_content_revision (revision_key,question_id,before_content,after_content,evidence_urls) SELECT p.revision_key,p.id,p.before_content,p.after_content,p.evidence_urls FROM teaching_mock_proposal p JOIN teaching_mock_ready t ON t.id=p.id ON DUPLICATE KEY UPDATE revision_key=mock_exam_content_revision.revision_key;',
            f"UPDATE mock_exam_bank q JOIN teaching_mock_proposal p ON p.id=q.id JOIN teaching_mock_ready t ON t.id=q.id JOIN mock_exam_content_revision r ON r.revision_key=p.revision_key SET q.stem=JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.stem')),q.reference_answer=JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.answer')),q.explanation=JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.explanation')) WHERE {before} AND {archive_match};",
            f"UPDATE mock_exam_content_revision r JOIN teaching_mock_proposal p ON p.revision_key=r.revision_key JOIN mock_exam_bank q ON q.id=p.id SET r.applied_at=COALESCE(r.applied_at,NOW()) WHERE {archive_match} AND {question.same(curr, 'p.after_content')};"
        ])
    out.append('COMMIT;')
    if questions:
        after_curr = question_after_current()
        before = question.same(after_curr, question_after_proposal('p'))
        archive_match = "r.question_id=p.id AND " + ' AND '.join(question.same(f'r.{key}', f'p.{key}') for key in ('before_content', 'after_content', 'evidence_urls'))
        out.extend([
            "SELECT JSON_OBJECT('target',CONCAT('question:',p.id),'id',p.id,'table','question','status',CASE WHEN q.id IS NULL THEN 'MISSING_TARGET' WHEN r.revision_key IS NOT NULL AND NOT (" + archive_match + ") THEN 'ARCHIVE_CONFLICT' WHEN " + before + " AND r.applied_at IS NOT NULL THEN 'APPLIED_OR_ALREADY_APPLIED' ELSE 'BASELINE_CHANGED' END) FROM teaching_question_proposal p LEFT JOIN question q ON q.id=p.id LEFT JOIN question_legal_subject s ON s.question_id=q.id LEFT JOIN question_content_revision r ON r.revision_key=p.revision_key ORDER BY p.id;",
            'DROP TEMPORARY TABLE teaching_question_ready;', 'DROP TEMPORARY TABLE teaching_question_proposal;'
        ])
    if mocks:
        curr, after = mock_current('q'), question.same(mock_current('q'), 'p.after_content')
        archive_match = "r.question_id=p.id AND " + ' AND '.join(question.same(f'r.{key}', f'p.{key}') for key in ('before_content', 'after_content', 'evidence_urls'))
        out.extend([
            "SELECT JSON_OBJECT('target',CONCAT('mock_exam_bank:',p.id),'id',p.id,'table','mock_exam_bank','status',CASE WHEN q.id IS NULL THEN 'MISSING_TARGET' WHEN r.revision_key IS NOT NULL AND NOT (" + archive_match + ") THEN 'ARCHIVE_CONFLICT' WHEN " + after + " AND r.applied_at IS NOT NULL THEN 'APPLIED_OR_ALREADY_APPLIED' ELSE 'BASELINE_CHANGED' END) FROM teaching_mock_proposal p LEFT JOIN mock_exam_bank q ON q.id=p.id LEFT JOIN mock_exam_content_revision r ON r.revision_key=p.revision_key ORDER BY p.id;",
            'DROP TEMPORARY TABLE teaching_mock_ready;', 'DROP TEMPORARY TABLE teaching_mock_proposal;'
        ])
    return '\n'.join(out) + '\n'


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--batch', required=True)
    ap.add_argument('--source-root', type=Path, default=ROOT / 'docs/teaching/2026-11-12/batches')
    ap.add_argument('--draft-root', type=Path, default=ROOT / 'datasets/question-explanations/batches')
    ap.add_argument('--output-root', type=Path, default=ROOT / 'docs/teaching/2026-11-12/batches')
    ap.add_argument('--sql-root', type=Path, default=ROOT / 'backend/src/main/resources')
    ap.add_argument('--applications', type=Path, default=ROOT / 'datasets/question-explanations/applications.json')
    args = ap.parse_args()
    if not re.fullmatch(r'\d{3}', args.batch): ap.error('batch must be three digits')
    source_path = args.source_root / args.batch / 'source.jsonl'
    sources = {}
    for row in load(source_path):
        target, source = row['target'], row['source']
        key = target['target']
        if key in sources: raise ValueError(f'Duplicate source target {key}')
        if target['table'] not in {'question', 'mock_exam_bank'}: raise ValueError(f'Unsupported table for {key}')
        if target['baselineHash'] != source['contentHash']: raise ValueError(f'Source snapshot hash differs for {key}')
        sources[key] = row

    draft_dir = args.draft_root / args.batch
    drafts, authors = {}, {}
    for file in sorted(draft_dir.glob('*.jsonl')):
        if file.name.startswith('review-'): continue
        author = author_for(file)
        for row in load(file):
            target = row.get('target')
            if isinstance(target, dict): target = target.get('target')
            if not target: continue
            if target in drafts: raise ValueError(f'Duplicate proposal for {target}')
            drafts[target] = row
            authors[target] = row.get('author') or author
            if not authors[target]: raise ValueError(f'Author attribution is missing for {target}')
    reviews = collect_reviews(args.draft_root)
    applications = {}
    if args.applications.exists():
        for row in json.loads(args.applications.read_text()).get('records', []):
            target = row.get('target') or f"{row.get('table', 'question')}:{row['id']}"
            applications[target] = row

    by_group = {}
    for target, row in sources.items():
        by_group.setdefault(row['target']['contentGroup'], []).append(target)
    eligible, skipped = [], []
    for group, targets in sorted(by_group.items()):
        issues = []
        for target in targets:
            if target not in drafts: issues.append(f'missing proposal {target}'); continue
            draft = drafts[target]
            source = sources[target]['source']
            table = sources[target]['target']['table']
            if draft.get('expectedContentHash') != sources[target]['target']['baselineHash']:
                issues.append(f'baseline mismatch {target}'); continue
            if draft.get('before') != source['before']:
                issues.append(f'before snapshot mismatch {target}'); continue
            if draft.get('readyForApplication') is not True:
                issues.append(f'held for human/evidence review {target}'); continue
            if draft.get('reviewerKind') != 'AI' or draft.get('humanApproval') is not False:
                issues.append(f'invalid author approval metadata {target}'); continue
            if table == 'question':
                allowed = set(question.FIELDS) | {'discipline', 'subject'}
                after_keys = set(draft.get('after', {}))
                allowed_shapes = (
                    set(question.FIELDS),
                    allowed,
                    set(question.FIELDS) | {'readyForApplication'},
                    allowed | {'readyForApplication'},
                )
                if after_keys not in allowed_shapes:
                    issues.append(f'question after-field shape invalid {target}'); continue
                if ('readyForApplication' in draft['after']
                        and draft['after']['readyForApplication'] is not draft.get('readyForApplication')):
                    issues.append(f'readiness metadata mismatch {target}'); continue
                original = source['before']
                after = draft['after']
                if any(after.get(field) != original[field] for field in ('type', 'options', 'category')):
                    issues.append(f'question type/options/category changes need a separate approved flow {target}'); continue
                classification = source['beforeClassification']
                after_classification = {key: after.get(key, classification[key]) for key in ('discipline', 'subject')}
                if after_classification != classification:
                    issues.append(f'question classification change requires separate review {target}'); continue
                validate_teaching_text(after.get('analysis'), target)
            else:
                if set(draft.get('after', {})) != set(MOCK_FIELDS):
                    issues.append(f'mock after-field shape invalid {target}'); continue
                after = draft['after']
                if any(after.get(field) != source['before'][field] for field in MOCK_FIELDS if field not in {'stem', 'answer', 'explanation'}):
                    issues.append(f'mock structural/provenance changes need separate review {target}'); continue
                validate_teaching_text(after.get('explanation'), target)
            validate_basis(draft, target)
            review = reviews.get(target)
            if not review:
                issues.append(f'missing cross-review {target}'); continue
            expected_author = authors[target]
            if review.get('author') != expected_author or review.get('reviewer') == expected_author:
                issues.append(f'cross-review is not independent or author identity differs {target}'); continue
            if review.get('reviewerKind') != 'AI' or review.get('humanApproval') is not False:
                issues.append(f'invalid reviewer approval metadata {target}'); continue
            if review.get('afterHash') != question.digest(draft.get('after')):
                issues.append(f'stale cross-review hash {target}'); continue
            legal_basis_hash = review.get('legalBasisHash')
            expected_legal_basis_hash = question.digest(draft.get('legalBasis'))
            if (legal_basis_hash is not None and legal_basis_hash != expected_legal_basis_hash
                    or draft.get('legalBasisReviewRequired') is True
                    and legal_basis_hash != expected_legal_basis_hash):
                issues.append(f'stale or missing legal-basis cross-review hash {target}'); continue
            if review.get('verdict') != 'PASS' or review.get('findings'):
                issues.append(f'cross-review not PASS {target}'); continue
            if not str(review.get('reviewNotes') or '').strip():
                issues.append(f'missing cross-review rationale {target}'); continue
            if target in applications:
                if applications[target].get('status') != 'APPLIED_OR_ALREADY_APPLIED':
                    issues.append(f'application record is not verified {target}')
                elif applications[target].get('contentHash') != question.digest(draft['after']):
                    issues.append(f'application record has a different content hash {target}')
                else:
                    issues.append(f'already applied {target}')
        applied_count = sum(target in applications and applications[target].get('status') == 'APPLIED_OR_ALREADY_APPLIED' for target in targets)
        if applied_count and applied_count != len(targets):
            issues.append('linked content group has partial application history')
        if issues:
            skipped.append({'contentGroup': group, 'targets': targets, 'reasons': sorted(set(issues))})
        else:
            eligible.extend(targets)

    if not eligible:
        print(json.dumps({'batch': args.batch, 'eligibleRecords': 0, 'eligibleGroups': 0, 'skippedGroups': len(skipped), 'skipped': skipped}, ensure_ascii=False, indent=2))
        return
    rows = []
    for target in eligible:
        source_row, draft, review = sources[target], drafts[target], reviews[target]
        table = source_row['target']['table']
        row = {**draft, 'id': source_row['source']['id'], 'target': target, 'table': table,
               'contentGroup': source_row['target']['contentGroup'], 'author': authors[target],
               'reviewerKind': 'AI', 'humanApproval': False, 'crossReview': review,
               'scopeAsOf': '2026-11-12', 'afterHash': question.digest(draft['after'])}
        if table == 'question':
            classification = source_row['source']['beforeClassification']
            row['beforeClassification'] = classification
            row['afterClassification'] = {key: draft['after'].get(key, classification[key]) for key in ('discipline', 'subject')}
        slug = f"mock-{row['id']}" if table == 'mock_exam_bank' else str(row['id'])
        row['revisionKey'] = f"2026-11-12-teaching-{args.batch}-{slug}"
        rows.append(row)
    segment_hash = hashlib.sha256('\n'.join(eligible).encode()).hexdigest()[:12]
    segment = f'segment-{segment_hash}'
    output = args.output_root / args.batch / 'segments' / segment
    output.mkdir(parents=True, exist_ok=True)
    (output / 'revisions.jsonl').write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in rows))
    sql_path = args.sql_root / f'teaching-batch-{args.batch}-{segment}.sql'
    sql_path.write_text(sql_rows(rows))
    summary = {'batch': args.batch, 'segment': segment, 'records': len(rows),
               'groups': len({row['contentGroup'] for row in rows}),
               'tables': dict(Counter(row['table'] for row in rows)),
               'eligibleTargets': eligible, 'skippedGroups': skipped,
               'reviewerKind': 'AI', 'humanApproval': False, 'releaseApproval': False,
               'migration': str(sql_path.relative_to(ROOT)),
               'migrationSha256': hashlib.sha256(sql_path.read_bytes()).hexdigest()}
    (output / 'build-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
