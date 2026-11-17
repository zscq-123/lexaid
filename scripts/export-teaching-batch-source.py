#!/usr/bin/env python3
"""Export exact current source rows for queued teaching batches (read only)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

FIELDS = ('type', 'stem', 'options', 'answer', 'analysis', 'category')
MOCK_FIELDS = ('id', 'sourceType', 'paperType', 'kind', 'subject', 'difficulty', 'stem', 'options', 'answer', 'explanation', 'sourceLabel', 'sourceUrl', 'readingUrl')


def stable(value):
    if isinstance(value, dict): return {key: stable(value[key]) for key in sorted(value)}
    if isinstance(value, list): return [stable(item) for item in value]
    return value


def digest(value):
    encoded = json.dumps(stable(value), ensure_ascii=False, separators=(',', ':')).encode()
    return hashlib.sha256(encoded).hexdigest()


def load(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def mysql(sql, database):
    command = ['docker', 'exec', '-i', 'lexaid-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B --raw -u "$MYSQL_USER" ' + database]
    result = subprocess.run(command, input=sql, text=True, capture_output=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--database', default='lexaid')
    ap.add_argument('--batches', required=True, help='Comma-separated pending batch IDs, for example 002,003,004')
    ap.add_argument('--plan', type=Path, default=Path('datasets/question-explanations/plan.jsonl'))
    ap.add_argument('--output', type=Path, default=Path('docs/teaching/2026-11-12/batches'))
    args = ap.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+', args.database): ap.error('invalid database name')
    batches = set(args.batches.split(','))
    if not batches or any(not re.fullmatch(r'\d{3}', item) for item in batches): ap.error('batches must be three-digit IDs')
    plan = [r for r in load(args.plan) if r['batch'] in batches and r['status'] == 'PENDING_AUTHORING']
    if not plan: ap.error('no pending targets in selected batches')
    if {r['batch'] for r in plan} != batches: ap.error('one or more selected batches are not pending')
    targets = {}
    for table in ('question', 'mock_exam_bank'):
        group = [r for r in plan if r['table'] == table]
        if not group: continue
        ids = sorted({r['id'] for r in group})
        id_list = ','.join(map(str, ids))
        if table == 'question':
            sql = f"""SELECT JSON_OBJECT('id',q.id,'type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category,'discipline',{_discipline},'subject',{_subject},'contentHash',{_hash})
FROM question q LEFT JOIN question_legal_subject s ON s.question_id=q.id WHERE q.id IN ({id_list}) ORDER BY q.id;"""
            found = mysql(sql, args.database)
            for row in found:
                targets[('question', row['id'])] = row
        else:
            sql = f"""SELECT JSON_OBJECT('id',id,'sourceType',source_type,'paperType',paper_type,'kind',kind,'subject',subject,'difficulty',difficulty,'stem',stem,'options',options_json,'answer',reference_answer,'explanation',explanation,'sourceLabel',source_label,'sourceUrl',source_url,'readingUrl',reading_url)
FROM mock_exam_bank WHERE id IN ({id_list}) ORDER BY id;"""
            found = mysql(sql, args.database)
            for row in found: targets[(table, row['id'])] = row
    if len(targets) != len(plan): raise ValueError(f'Expected {len(plan)} rows, exported {len(targets)}')
    exported = []
    for target in plan:
        row = targets[(target['table'], target['id'])]
        if target['table'] == 'question':
            if row['contentHash'] != target['baselineHash']: raise ValueError(f"Question baseline drift: {target['target']}")
            row['before'] = {field: row[field] for field in FIELDS}
            row['beforeClassification'] = {'discipline': row['discipline'], 'subject': row['subject']}
        else:
            row['contentHash'] = digest({key: row[key] for key in MOCK_FIELDS})
            if row['contentHash'] != target['baselineHash']: raise ValueError(f"Mock bank baseline drift: {target['target']}")
            row['before'] = {field: row[field] for field in MOCK_FIELDS}
        exported.append({'target': target, 'source': row})
    for batch in sorted(batches):
        output = args.output / batch
        output.mkdir(parents=True, exist_ok=True)
        rows = [row for row in exported if row['target']['batch'] == batch]
        (output / 'source.jsonl').write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in rows))
        (output / 'source-summary.json').write_text(json.dumps({'batch': batch, 'records': len(rows), 'contentGroups': len({r['target']['contentGroup'] for r in rows}), 'database': args.database, 'baselineHashesMatched': True}, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'batches': sorted(batches), 'records': len(exported), 'contentGroups': len({r['contentGroup'] for r in plan}), 'database': args.database, 'baselineHashesMatched': True}, ensure_ascii=False, indent=2))


_discipline = "COALESCE(s.discipline,CASE TRIM(q.category) WHEN '刑法' THEN '刑法' WHEN '民法' THEN '民商法' WHEN '行政法' THEN '宪法与行政法' ELSE '综合与待判定' END)"
_subject = "COALESCE(s.subject,CASE TRIM(q.category) WHEN '刑法' THEN '刑法学' WHEN '民法' THEN '民法学' WHEN '行政法' THEN '行政法学' ELSE '待判定' END)"
_hash = "SHA2(CAST(JSON_ARRAY(q.type,q.stem,q.options,q.answer,q.analysis,q.category," + _discipline + "," + _subject + ") AS CHAR),256)"


if __name__ == '__main__': main()
