#!/usr/bin/env python3
"""Exercise content migration guards using session-only MySQL shadow tables.

No persistent data is modified. Requires the existing local question schemas.
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import subprocess

spec = importlib.util.spec_from_file_location('batch', Path(__file__).with_name('build-teaching-question-batch.py'))
batch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(batch)


def fixture(row):
    before = {**row['before'], **row['beforeClassification']}
    sql = '''CREATE TEMPORARY TABLE question (id BIGINT PRIMARY KEY,type VARCHAR(32),stem MEDIUMTEXT,options MEDIUMTEXT,answer MEDIUMTEXT,analysis MEDIUMTEXT,category VARCHAR(64),updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TEMPORARY TABLE question_legal_subject (question_id BIGINT PRIMARY KEY,discipline VARCHAR(64) NOT NULL,subject VARCHAR(64) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TEMPORARY TABLE question_content_revision (revision_key VARCHAR(80) PRIMARY KEY,question_id BIGINT NOT NULL,before_content JSON NOT NULL,after_content JSON NOT NULL,evidence_urls JSON NOT NULL,applied_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TEMPORARY TABLE teaching_history (question_id BIGINT, answer TEXT, analysis TEXT, score DECIMAL(6,2));
INSERT INTO teaching_history VALUES (''' + str(row['id']) + ", 'historical student answer', 'historical grading reason', 73.50);\n"
    sql += 'INSERT INTO question(id,' + ','.join(batch.FIELDS) + ') VALUES (' + str(row['id']) + ',' + ','.join(batch.text_sql(before[k]) for k in batch.FIELDS) + ');\n'
    sql += 'INSERT INTO question_legal_subject(question_id,discipline,subject) VALUES (' + str(row['id']) + ',' + batch.text_sql(before['discipline']) + ',' + batch.text_sql(before['subject']) + ');\n'
    return sql


def execute(row, pre='', repeat=False, baseline=True):
    setup = fixture(row)
    if not baseline:
        setup += 'DELETE FROM question;\n'
    # MySQL's ordinary CREATE TABLE checks the persistent dictionary even when
    # a same-name temporary table exists. The fixture supplies that schema;
    # omit only this permanent DDL while exercising all generated DML unchanged.
    migration = batch.build_sql([row]).splitlines()
    permanent_ddl = [line for line in migration if line.startswith('CREATE TABLE IF NOT EXISTS question_content_revision ')]
    assert len(permanent_ddl) == 1
    migration = '\n'.join(line for line in migration if line not in permanent_ddl) + '\n'
    sql = setup + pre + '\n' + migration
    if repeat:
        sql += migration
    sql += "SELECT JSON_OBJECT('probe','current','content'," + batch.CURRENT_JSON + ",'hash'," + batch.HASH_SQL + ') FROM ' + batch.JOIN + ';\n'
    sql += "SELECT JSON_OBJECT('probe','archive','count',COUNT(*),'applied',COUNT(applied_at)) FROM question_content_revision;\n"
    sql += "SELECT JSON_OBJECT('probe','history','question_id',question_id,'answer',answer,'analysis',analysis,'score',score) FROM teaching_history;\n"
    result = subprocess.run(['docker', 'exec', '-i', 'lexaid-mysql', 'sh', '-c',
                             'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" "$MYSQL_DATABASE"'],
                            input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr[:2000])
    data = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
    history = next(r for r in data if r.get('probe') == 'history')
    assert history == {'probe': 'history', 'question_id': row['id'], 'answer': 'historical student answer', 'analysis': 'historical grading reason', 'score': 73.50}
    return data


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--revisions', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    rows = batch.records(args.revisions)
    row = next(r for r in rows if r['beforeClassification'] != r['afterClassification'])
    passed = []
    result = execute(row, repeat=True)
    assert [r['status'] for r in result if 'status' in r] == ['APPLIED_OR_ALREADY_APPLIED'] * 2
    assert next(r for r in result if r.get('probe') == 'current')['content'] == {**row['after'], **row['afterClassification']}
    assert next(r for r in result if r.get('probe') == 'archive') == {'probe': 'archive', 'count': 1, 'applied': 1}
    passed += ['baseline content and classification applied', 'repeat is idempotent', 'archive retains one applied revision']
    for field in batch.FIELDS + ('discipline', 'subject'):
        table = 'question' if field in batch.FIELDS else 'question_legal_subject'
        value = 'SINGLE' if field == 'type' else 'drift'
        result = execute(row, f'UPDATE {table} SET {field}={batch.text_sql(value)};')
        assert next(r for r in result if 'status' in r)['status'] == 'BASELINE_CHANGED', field
        current = next(r for r in result if r.get('probe') == 'current')['content']
        assert current[field] == value
        assert next(r for r in result if r.get('probe') == 'archive')['count'] == 0
        passed.append('skip drifted ' + field)
    conflict = 'INSERT INTO question_content_revision VALUES (' + batch.text_sql(row['revisionKey']) + ',' + str(row['id']) + ",JSON_OBJECT('conflict',true),JSON_OBJECT('conflict',true),JSON_ARRAY(),NULL);"
    result = execute(row, conflict)
    assert next(r for r in result if 'status' in r)['status'] == 'ARCHIVE_CONFLICT'
    assert next(r for r in result if r.get('probe') == 'current')['content'] == {**row['before'], **row['beforeClassification']}
    passed.append('archive conflict does not overwrite question or archive')
    result = execute(row, baseline=False)
    assert next(r for r in result if 'status' in r)['status'] == 'MISSING_QUESTION'
    passed.append('missing target reported')
    # Check validation rejects a content edit after independent review.
    draft = copy.deepcopy(row)
    draft['after']['answer'] += ' changed after review'
    originals = {row['id']: {**row['before'], 'contentHash': row['expectedContentHash']}}
    try:
        batch.validate({row['id']: draft}, {row['id']: row['crossReview']}, originals,
                       {row['id']: row['beforeClassification']}, {row['id']})
        raise AssertionError('stale review accepted')
    except ValueError as error:
        assert 'different draft' in str(error)
    passed += ['stale independent review rejected']
    for name, mutate in [
        ('human approval cannot be claimed by AI', lambda d, r: d.update(humanApproval=True)),
        ('self review cannot approve a draft', lambda d, r: r.update(reviewer=r['author'])),
        ('unresolved findings block a draft', lambda d, r: r.update(verdict='CHANGES_REQUESTED', findings=['Unresolved legal rule'])),
        ('unverified original content is rejected', lambda d, r: d['before'].update(stem='drift')),
    ]:
        d, r = copy.deepcopy(row), copy.deepcopy(row['crossReview'])
        mutate(d, r)
        try:
            batch.validate({row['id']: d}, {row['id']: r}, originals, {row['id']: row['beforeClassification']}, {row['id']})
        except ValueError:
            passed.append(name)
        else:
            raise AssertionError(name)
    malicious = copy.deepcopy(row)
    malicious['after']['stem'] = '<img src=x onerror=alert(1)>'
    malicious['after']['analysis'] = '<script>alert(1)</script>'
    preview = batch.build_preview([malicious])
    assert '<img src=x' not in preview and '<script>alert(1)</script>' not in preview
    assert '&lt;img src=x onerror=alert(1)&gt;' in preview
    passed += ['preview escapes untrusted question content', 'historical answer/reason/score unchanged in every scenario']
    report = {'tests': len(passed), 'passed': passed, 'persistentDatabaseMutation': False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
