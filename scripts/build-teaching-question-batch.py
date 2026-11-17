#!/usr/bin/env python3
"""Validate independently reviewed teaching drafts and build a guarded data migration.

This records AI content corrections, never human approval or practice release.
The SQL preserves the original revision and skips a row if any baseline field drifted.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import html
import json
from pathlib import Path
from urllib.parse import urlparse

FIELDS = ('type', 'stem', 'options', 'answer', 'analysis', 'category')
SECTIONS = ('【适用范围】', '【法律依据】', '【推理过程】', '【易错点】')
SHARDS = {'backend': ('backend_audit', 'question_audit'),
          'frontend': ('frontend_search_audit', 'backend_audit'),
          'legal': ('question_audit', 'frontend_search_audit')}
CLASSIFICATIONS = {
    10024085: ('民商法', '物权法'), 10024089: ('民商法', '物权法'),
    10024405: ('诉讼与争议解决', '民事诉讼法学'),
    10024627: ('诉讼与争议解决', '行政诉讼法学'),
    10024637: ('宪法与行政法', '行政法学'), 10024638: ('宪法与行政法', '行政法学'),
    10024930: ('民商法', '侵权责任法'),
    10024973: ('诉讼与争议解决', '行政诉讼法学'),
    10025073: ('经济与社会法', '环境与资源保护法学'),
}
DISCIPLINE = "COALESCE(s.discipline,CASE TRIM(q.category) WHEN '刑法' THEN '刑法' WHEN '民法' THEN '民商法' WHEN '行政法' THEN '宪法与行政法' ELSE '综合与待判定' END)"
SUBJECT = "COALESCE(s.subject,CASE TRIM(q.category) WHEN '刑法' THEN '刑法学' WHEN '民法' THEN '民法学' WHEN '行政法' THEN '行政法学' ELSE '待判定' END)"
VALUES = [f'q.{key}' for key in FIELDS] + [DISCIPLINE, SUBJECT]
HASH_SQL = 'SHA2(CAST(JSON_ARRAY(' + ','.join(VALUES) + ') AS CHAR),256)'
CURRENT_JSON = 'JSON_OBJECT(' + ','.join(f"'{key}',{value}" for key, value in zip(FIELDS + ('discipline', 'subject'), VALUES)) + ')'
JOIN = 'question q LEFT JOIN question_legal_subject s ON s.question_id=q.id'


def records(path):
    raw = gzip.decompress(path.read_bytes()).decode() if path.suffix == '.gz' else path.read_text()
    return [json.loads(line) for line in raw.splitlines() if line.strip()]


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def text_sql(value):
    if value is None:
        return 'NULL'
    if not isinstance(value, str):
        value = json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    return "CONVERT(0x" + value.encode().hex() + " USING utf8mb4)" if value else "''"


def same(left, right):
    return f'BINARY CAST({left} AS CHAR)=BINARY CAST({right} AS CHAR)'


def validate(drafts, reviews, originals, metadata, expected):
    if set(drafts) != expected or set(reviews) != expected:
        raise ValueError(f'Incomplete coverage: drafts={len(drafts)}, reviews={len(reviews)}, expected={len(expected)}')
    rows = []
    for qid in sorted(expected):
        draft, review, original, meta = drafts[qid], reviews[qid], originals[qid], metadata[qid]
        def require(condition, message):
            if not condition:
                raise ValueError(f'{qid}: {message}')
        require(draft['expectedContentHash'] == original['contentHash'], 'baseline hash differs')
        require(draft['before'] == {k: original[k] for k in FIELDS}, 'original content differs')
        require(set(draft['after']) in (set(FIELDS), set(FIELDS) | {'discipline', 'subject'}), 'unexpected/missing after fields')
        require(draft['readyForApplication'] is True, 'not ready for application')
        require(draft['reviewerKind'] == review['reviewerKind'] == 'AI', 'review kind must be explicit')
        require(draft['humanApproval'] is False and review['humanApproval'] is False, 'AI cannot assert human approval')
        require(review['verdict'] == 'PASS' and not review['findings'], 'unresolved cross-review findings')
        require(review['afterHash'] == digest(draft['after']), 'review belongs to a different draft')
        require(review['reviewer'] != review['author'], 'reviewer must differ from author')
        require(bool(review['reviewNotes'].strip()), 'missing review rationale')
        after = draft['after']
        require(after['type'] == original['type'] and after['options'] == original['options'], 'batch does not change answer type/options')
        require(after['category'] == original['category'], 'category changes need a separate review')
        require(all(isinstance(after[k], str) and after[k].strip() for k in ('stem', 'answer', 'analysis')), 'empty question/answer/explanation')
        require(all(after['analysis'].count(s) == 1 for s in SECTIONS), 'missing or repeated teaching section')
        require(len(after['analysis']) >= 150, 'explanation needs substantive reasoning')
        require(bool(draft['changes']) and bool(draft['legalBasis']), 'missing change/evidence record')
        for basis in draft['legalBasis']:
            require(all(basis.get(k) for k in ('law', 'articles', 'version', 'url')), 'incomplete legal basis')
            require(isinstance(basis['articles'], list) and all(isinstance(a, str) and a.strip() for a in basis['articles']), 'invalid article list')
            parsed = urlparse(basis['url'])
            require(parsed.scheme == 'https' and bool(parsed.hostname), 'invalid source URL')
        classification = {key: meta[key] for key in ('discipline', 'subject')}
        after_classification = {key: after.get(key, classification[key]) for key in ('discipline', 'subject')}
        if qid in CLASSIFICATIONS:
            require(classification['subject'] == '待判定', 'classification baseline differs')
            after_classification = dict(zip(('discipline', 'subject'), CLASSIFICATIONS[qid]))
        else:
            require(after_classification == classification, 'unreviewed classification changes')
        rows.append({**draft, 'scopeAsOf': '2026-11-12', 'afterHash': digest(after), 'crossReview': review,
                     'beforeClassification': classification, 'afterClassification': after_classification,
                     'revisionKey': f'2026-11-12-teaching-001-{qid}'})
    return rows


def build_sql(rows):
    out = ["-- Generated by scripts/build-teaching-question-batch.py. Do not edit generated data.",
           "-- AI authored + independently AI reviewed; does not create question_quality_review or release held questions.",
           "SET NAMES utf8mb4;",
           "CREATE TABLE IF NOT EXISTS question_content_revision (revision_key VARCHAR(80) NOT NULL PRIMARY KEY, question_id BIGINT NOT NULL, before_content JSON NOT NULL, after_content JSON NOT NULL, evidence_urls JSON NOT NULL, applied_at DATETIME NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
           "DROP TEMPORARY TABLE IF EXISTS teaching_proposal;",
           "CREATE TEMPORARY TABLE teaching_proposal (id BIGINT PRIMARY KEY, revision_key VARCHAR(80) NOT NULL, expected_hash CHAR(64) NOT NULL, before_content JSON NOT NULL, after_content JSON NOT NULL, evidence_urls JSON NOT NULL) ENGINE=InnoDB;",
           "INSERT INTO teaching_proposal VALUES"]
    values = []
    for row in rows:
        before = {**row['before'], **row['beforeClassification']}
        after = {**row['after'], **row['afterClassification']}
        urls = sorted({b['url'] for b in row['legalBasis']})
        values.append('(' + ','.join([str(row['id'])] + [text_sql(v) for v in (row['revisionKey'], row['expectedContentHash'], before, after, urls)]) + ')')
    out.append(',\n'.join(values) + ';')
    before_match = same(CURRENT_JSON, 'p.before_content') + ' AND ' + HASH_SQL + '=p.expected_hash'
    archive_match = 'r.question_id=p.id AND ' + ' AND '.join(same('r.' + key, 'p.' + key) for key in ('before_content', 'after_content', 'evidence_urls'))
    out += ["DROP TEMPORARY TABLE IF EXISTS teaching_ready;",
            "CREATE TEMPORARY TABLE teaching_ready (id BIGINT PRIMARY KEY) ENGINE=InnoDB;",
            "DROP TEMPORARY TABLE IF EXISTS teaching_subject_change;",
            "CREATE TEMPORARY TABLE teaching_subject_change (question_id BIGINT PRIMARY KEY,discipline VARCHAR(64) NOT NULL,subject VARCHAR(64) NOT NULL) ENGINE=InnoDB;",
            "START TRANSACTION;",
            # Lock target rows before deriving eligibility, so concurrent edits cannot be overwritten.
            f"INSERT INTO teaching_ready SELECT q.id FROM {JOIN} JOIN teaching_proposal p ON p.id=q.id WHERE {before_match} FOR UPDATE;",
            "INSERT INTO question_content_revision (revision_key,question_id,before_content,after_content,evidence_urls) SELECT p.revision_key,p.id,p.before_content,p.after_content,p.evidence_urls FROM teaching_proposal p JOIN teaching_ready t ON t.id=p.id ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;",
            f"UPDATE {JOIN} JOIN teaching_proposal p ON p.id=q.id JOIN teaching_ready t ON t.id=q.id JOIN question_content_revision r ON r.revision_key=p.revision_key SET " + ','.join(f"q.{key}=JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.{key}'))" for key in ('stem', 'answer', 'analysis')) + f" WHERE {before_match} AND {archive_match};",
            # q now has the reviewed text; classification is written only for the exact intermediate state.
            "INSERT INTO teaching_subject_change SELECT q.id,JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.discipline')),JSON_UNQUOTE(JSON_EXTRACT(p.after_content,'$.subject')) FROM " + JOIN + " JOIN teaching_proposal p ON p.id=q.id JOIN teaching_ready t ON t.id=q.id JOIN question_content_revision r ON r.revision_key=p.revision_key WHERE " + archive_match + ' AND ' + same(CURRENT_JSON, "JSON_SET(p.after_content,'$.discipline',JSON_EXTRACT(p.before_content,'$.discipline'),'$.subject',JSON_EXTRACT(p.before_content,'$.subject'))") + ';',
            "INSERT INTO question_legal_subject (question_id,discipline,subject) SELECT question_id,discipline,subject FROM teaching_subject_change WHERE TRUE ON DUPLICATE KEY UPDATE discipline=VALUES(discipline),subject=VALUES(subject);",
            f"UPDATE question_content_revision r JOIN teaching_proposal p ON p.revision_key=r.revision_key JOIN ({JOIN}) ON q.id=p.id SET r.applied_at=COALESCE(r.applied_at,NOW()) WHERE {archive_match} AND {same(CURRENT_JSON, 'p.after_content')};",
            "COMMIT;",
            "SELECT JSON_OBJECT('id',p.id,'revisionKey',p.revision_key,'contentHash'," + HASH_SQL + ",'status',CASE WHEN q.id IS NULL THEN 'MISSING_QUESTION' WHEN r.revision_key IS NOT NULL AND NOT (" + archive_match + ") THEN 'ARCHIVE_CONFLICT' WHEN " + same(CURRENT_JSON, 'p.after_content') + " AND r.applied_at IS NOT NULL THEN 'APPLIED_OR_ALREADY_APPLIED' ELSE 'BASELINE_CHANGED' END) FROM teaching_proposal p LEFT JOIN question q ON q.id=p.id LEFT JOIN question_legal_subject s ON s.question_id=q.id LEFT JOIN question_content_revision r ON r.revision_key=p.revision_key ORDER BY p.id;",
            "DROP TEMPORARY TABLE teaching_subject_change;", "DROP TEMPORARY TABLE teaching_ready;", "DROP TEMPORARY TABLE teaching_proposal;"]
    return '\n'.join(out) + '\n'


def build_preview(rows):
    esc = lambda s: html.escape('' if s is None else str(s), quote=True)
    cards = []
    for row in rows:
        before, after = row['before'], row['after']
        bases = ''.join(f'<li>{esc(b["law"])} · {esc("、".join(b["articles"]))} · {esc(b["version"])} <a href="{esc(b["url"])}" target="_blank" rel="noopener noreferrer">官方依据</a></li>' for b in row['legalBasis'])
        cards.append(f'<article id="q{row["id"]}"><h2>{row["id"]} · {esc(after["stem"])}</h2><p class="tag">{esc(row["afterClassification"]["subject"])} · AI 交叉复核完成 · 待正式发布审核</p><h3>参考答案</h3><p>{esc(after["answer"])}</p><h3>题目解析</h3><p class="analysis">{esc(after["analysis"])}</p><h3>法律依据与版本</h3><ul>{bases}</ul><details><summary>修改点与原文</summary><ul>' + ''.join(f'<li>{esc(c)}</li>' for c in row['changes']) + f'</ul><h4>原题</h4><p>{esc(before["stem"])}</p><h4>原答案</h4><p>{esc(before["answer"])}</p><h4>原解析</h4><p>{esc(before["analysis"])}</p></details></article>')
    return '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>LexAid 教学题第一批 · 216题</title><style>body{max-width:960px;margin:0 auto;padding:24px;font:16px/1.8 system-ui;color:#182d39;background:#f5f8fa}header,article{padding:24px;background:white;border:1px solid #dce5eb;border-radius:14px;margin-bottom:18px}h1{font-size:26px}h2{font-size:20px}h3{font-size:17px;margin-bottom:6px}p{overflow-wrap:anywhere}.tag{color:#526674;font-size:14px}.analysis{white-space:pre-wrap}input{box-sizing:border-box;width:100%;padding:12px;font:inherit;border:1px solid #9db1be;border-radius:8px}a{color:#1363a3}details{border-top:1px solid #ddd;padding-top:12px}summary{cursor:pointer}@media(max-width:600px){body{padding:10px}header,article{padding:16px}}</style><header><h1>LexAid 教学题修订 · 第 001 批</h1><p>216 道已确认问题题，逐题修正答案并补写适用范围、法律依据、推理过程、易错点。适用时点为 2026-11-12，题干另有历史时点的依题干。</p><p>这里是可审阅的修订内容。AI 编写与另一 AI 交叉复核不等于人工法律审核；本批不写入人工双人审核记录，也不自动解除练习题质量门禁。</p><input id="search" placeholder="搜索题号、题干、答案或法条" aria-label="搜索题目"><p id="count" role="status"></p></header><main>' + ''.join(cards) + '</main><script>const cards=[...document.querySelectorAll("article")],input=document.getElementById("search"),count=document.getElementById("count");function filter(){const q=input.value.trim().toLowerCase();let n=0;for(const card of cards){card.hidden=!card.textContent.toLowerCase().includes(q);if(!card.hidden)n++}count.textContent=`显示 ${n} / ${cards.length} 道题`}input.addEventListener("input",filter);filter();</script></html>'


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--drafts', required=True, type=Path)
    ap.add_argument('--originals', required=True, type=Path)
    ap.add_argument('--metadata', required=True, type=Path)
    ap.add_argument('--audit', required=True, type=Path)
    ap.add_argument('--output', required=True, type=Path)
    ap.add_argument('--sql', required=True, type=Path)
    args = ap.parse_args()
    drafts, reviews = {}, {}
    for name, (author, reviewer) in SHARDS.items():
        shard_ids = set()
        for row in records(args.drafts / (name + '.jsonl')):
            if row['id'] in drafts:
                raise ValueError('Duplicate draft')
            drafts[row['id']] = row
            shard_ids.add(row['id'])
        for row in records(args.drafts / ('review-' + name + '.jsonl')):
            if row['id'] in reviews or row['id'] not in shard_ids or row['author'] != author or row['reviewer'] != reviewer:
                raise ValueError('Duplicate or non-independent cross review')
            reviews[row['id']] = row
    originals = {r['id']: r for r in records(args.originals)}
    metadata = {r['id']: r for r in records(args.metadata)}
    expected = {r['id'] for r in records(args.audit) if r['status'] == 'CONFIRMED_DEFECT'}
    if len(expected) != 216:
        raise ValueError('Unexpected batch scope')
    rows = validate(drafts, reviews, originals, metadata, expected)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'revisions.jsonl').write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows))
    (args.output / 'preview.html').write_text(build_preview(rows))
    args.sql.write_text(build_sql(rows))
    summary = {'batch': '001', 'records': len(rows), 'classificationCorrections': sum(r['beforeClassification'] != r['afterClassification'] for r in rows), 'scopeAsOf': '2026-11-12', 'reviewKind': 'AI_AUTHOR_AND_INDEPENDENT_AI_REVIEW', 'humanApproval': False, 'releaseApproval': False, 'subjects': dict(Counter(r['afterClassification']['subject'] for r in rows)), 'migrationSha256': hashlib.sha256(args.sql.read_bytes()).hexdigest()}
    (args.output / 'build-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
