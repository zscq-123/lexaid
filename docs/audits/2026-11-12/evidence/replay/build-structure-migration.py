import json,pathlib
ROOT=pathlib.Path(__file__).resolve().parent
DEST=pathlib.Path('/Users/yangyongze/Desktop/lexaid/lexaid/backend/src/main/resources/question-structure-review-20261112.sql')
rows=[json.loads(x) for x in (ROOT/'lexeval-corrections.jsonl').read_text().splitlines()]
assert len(rows)==len({x['id']for x in rows})==87
def sqljson(x):
    value=json.dumps(x,ensure_ascii=False,separators=(',',':')).encode().hex()
    return f'CAST(CONVERT(0x{value} USING utf8mb4) AS JSON)'
content="JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category)"
def equal(a,b):return f'BINARY CAST({a} AS CHAR)=BINARY CAST({b} AS CHAR)'
values=[]
for x in rows:
    assert x['before']['analysis']==x['after']['analysis'] and x['before']['category']==x['after']['category']
    values.append(f"('2026-11-12-lexeval-structure-{x['id']}',{x['id']},{sqljson(x['before'])},{sqljson(x['after'])},{sqljson([x['upstreamSource']])})")
sql='''-- Generated from the 87 verified LexEval structural corrections; not legal-content approval.
-- Upstream commit: 044c695f62894deef41ad9a30797e1da0404945c.
-- Only exact before-content matches with no answer_record are changed. All history blocks conversion.
-- Keeps analysis/category unchanged; archives complete before/after fields and pinned evidence URL.
-- JSON is encoded as UTF-8 hex so SQL backslash modes cannot alter the audited content.
CREATE TABLE IF NOT EXISTS question_content_revision (
  revision_key VARCHAR(80) NOT NULL PRIMARY KEY,
  question_id BIGINT NOT NULL,
  before_content JSON NOT NULL,
  after_content JSON NOT NULL,
  evidence_urls JSON NOT NULL,
  applied_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS lexaid_structure_review_candidates;
CREATE TEMPORARY TABLE lexaid_structure_review_candidates (
  revision_key VARCHAR(80) NOT NULL PRIMARY KEY,
  question_id BIGINT NOT NULL UNIQUE,
  before_content JSON NOT NULL,
  after_content JSON NOT NULL,
  evidence_urls JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO lexaid_structure_review_candidates(revision_key,question_id,before_content,after_content,evidence_urls) VALUES
'''+',\n'.join(values)+''';

START TRANSACTION;
INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)
SELECT c.revision_key,c.question_id,c.before_content,c.after_content,c.evidence_urls
FROM lexaid_structure_review_candidates c JOIN question q ON q.id=c.question_id
WHERE '''+equal(content,'c.before_content')+'''
  AND NOT EXISTS(SELECT 1 FROM answer_record ar WHERE ar.question_id=q.id)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

UPDATE question q JOIN lexaid_structure_review_candidates c ON c.question_id=q.id
JOIN question_content_revision r ON r.revision_key=c.revision_key AND r.question_id=q.id
SET q.type=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,'$.type')),
    q.stem=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,'$.stem')),
    q.options=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,'$.options')),
    q.answer=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,'$.answer'))
WHERE '''+equal(content,'c.before_content')+'''
  AND '''+equal('r.before_content','c.before_content')+'''
  AND '''+equal('r.after_content','c.after_content')+'''
  AND '''+equal('r.evidence_urls','c.evidence_urls')+'''
  AND NOT EXISTS(SELECT 1 FROM answer_record ar WHERE ar.question_id=q.id);
SET @lexaid_structure_review_changed=ROW_COUNT();

UPDATE question_content_revision r JOIN lexaid_structure_review_candidates c ON c.revision_key=r.revision_key AND c.question_id=r.question_id
JOIN question q ON q.id=c.question_id
SET r.applied_at=COALESCE(r.applied_at,NOW())
WHERE '''+equal(content,'c.after_content')+'''
  AND '''+equal('r.before_content','c.before_content')+'''
  AND '''+equal('r.after_content','c.after_content')+'''
  AND '''+equal('r.evidence_urls','c.evidence_urls')+'''
  AND NOT EXISTS(SELECT 1 FROM answer_record ar WHERE ar.question_id=q.id);
COMMIT;

SELECT @lexaid_structure_review_changed AS changed_this_run;
-- Retain explicit skipped IDs in the execution log; do not infer completion from an exit code.
SELECT c.question_id,
 CASE WHEN q.id IS NULL THEN 'SKIPPED_MISSING_QUESTION'
      WHEN r.revision_key IS NOT NULL AND NOT ('''+equal('r.before_content','c.before_content')+' AND '+equal('r.after_content','c.after_content')+' AND '+equal('r.evidence_urls','c.evidence_urls')+''') THEN 'SKIPPED_ARCHIVE_CONFLICT'
      WHEN '''+equal(content,'c.after_content')+''' AND r.applied_at IS NOT NULL THEN 'APPLIED_OR_ALREADY_APPLIED'
      WHEN EXISTS(SELECT 1 FROM answer_record ar WHERE ar.question_id=q.id) THEN 'SKIPPED_HAS_ANSWER_HISTORY'
      WHEN NOT ('''+equal(content,'c.before_content')+''') THEN 'SKIPPED_CONTENT_CHANGED'
      ELSE 'NOT_APPLIED_REQUIRES_INVESTIGATION' END AS result
FROM lexaid_structure_review_candidates c LEFT JOIN question q ON q.id=c.question_id
LEFT JOIN question_content_revision r ON r.revision_key=c.revision_key AND r.question_id=c.question_id
ORDER BY c.question_id;
DROP TEMPORARY TABLE lexaid_structure_review_candidates;
'''
DEST.write_text(sql)
print(json.dumps({'path':str(DEST),'bytes':DEST.stat().st_size,'proposals':len(rows)}))
