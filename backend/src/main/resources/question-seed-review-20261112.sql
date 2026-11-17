-- AI-assisted content scope corrections, not human dual approval. See docs/full-question-review-2026-11-12.md.
CREATE TABLE IF NOT EXISTS question_content_revision (
  revision_key VARCHAR(80) NOT NULL PRIMARY KEY,
  question_id BIGINT NOT NULL,
  before_content JSON NOT NULL,
  after_content JSON NOT NULL,
  evidence_urls JSON NOT NULL,
  applied_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)
SELECT '2026-11-12-seed-13',q.id,JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category),
 JSON_OBJECT('type','MULTIPLE','stem','按照犯罪构成四要件理论，下列属于犯罪构成要件的有？','options','[{"key":"A","text":"犯罪主体"},{"key":"B","text":"犯罪主观方面"},{"key":"C","text":"犯罪客体"},{"key":"D","text":"犯罪客观方面"}]','answer','["A","B","C","D"]','analysis','本题限定采用四要件理论，四项分别是犯罪主体、犯罪主观方面、犯罪客体、犯罪客观方面。犯罪构成还存在阶层体系等理论表达，不能将本题限定的理论模型当作唯一理论。','category','刑法'),JSON_ARRAY('https://www.spp.gov.cn/spp/ztk/dfld/202402/t20240220_655235.shtml')
FROM question q WHERE q.id=13 AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(JSON_OBJECT('type','MULTIPLE','stem','下列关于犯罪构成要件，说法正确的有？','options','[{"key":"A","text":"犯罪主体"},{"key":"B","text":"犯罪主观方面"},{"key":"C","text":"犯罪客体"},{"key":"D","text":"犯罪客观方面"}]','answer','["A","B","C","D"]','analysis','通说认为犯罪构成包括犯罪主体、犯罪主观方面、犯罪客体、犯罪客观方面四个要件。','category','刑法') AS CHAR)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)
SELECT '2026-11-12-seed-22',q.id,JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category),
 JSON_OBJECT('type','JUDGE','stem','刑法第264条的条文明确将“秘密窃取”列为盗窃罪的必要要件。','options',NULL,'answer','false','analysis','本题仅核对刑法第264条的文字，该条没有明文写入秘密窃取。秘密性是否属于窃取行为的必要要素存在不同学理观点，不能把条文未明写等同于所有公开取财都构成盗窃；具体案件仍须结合行为方式与其他构成条件判断。','category','刑法'),JSON_ARRAY('https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html')
FROM question q WHERE q.id=22 AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(JSON_OBJECT('type','JUDGE','stem','盗窃罪以秘密窃取为必要要件，公开取走他人财物不构成盗窃罪。','options',NULL,'answer','false','analysis','现代刑法学说认为，盗窃罪的核心是违背占有人意志转移财物占有，秘密窃取并非必要要件，公开窃取也可成立盗窃罪。','category','刑法') AS CHAR)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

UPDATE question q JOIN question_content_revision r ON r.question_id=q.id
SET q.stem=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.stem')),q.answer=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.answer')),q.analysis=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.analysis'))
WHERE r.revision_key IN ('2026-11-12-seed-13','2026-11-12-seed-22')
AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(r.before_content AS CHAR);
UPDATE question_content_revision r JOIN question q ON q.id=r.question_id
SET r.applied_at=COALESCE(r.applied_at,NOW())
WHERE r.revision_key IN ('2026-11-12-seed-13','2026-11-12-seed-22')
AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(r.after_content AS CHAR);
COMMIT;
