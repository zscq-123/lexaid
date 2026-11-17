-- Evidence and limitations: docs/question-audit-2026-11-11.md.
-- Only the three unchanged seed records are corrected. Imported data and answer history are untouched.
-- Before/after content is retained; this is an AI-assisted correction, not dual-human approval.
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
SELECT '2026-11-11-seed-15',q.id,JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category),
  JSON_OBJECT('type','MULTIPLE','stem','正当防卫的成立条件包括？','options','[{"key":"A","text":"存在正在进行的不法侵害"},{"key":"B","text":"出于防卫意图"},{"key":"C","text":"针对不法侵害人本人实施"},{"key":"D","text":"不能明显超过必要限度造成重大损害"}]','answer','["A","B","C","D"]','analysis','依据刑法第20条，防卫须针对正在进行的不法侵害并具有防卫意图。防卫过当须同时满足明显超过必要限度与造成重大损害；符合第3款规定的特殊防卫，不属于防卫过当。不能将限度条件简化为不得超过必要限度。','category','刑法'),
  JSON_ARRAY('https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html')
FROM question q WHERE q.id=15
  AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(JSON_OBJECT('type','MULTIPLE','stem','正当防卫的成立条件包括？','options','[{"key":"A","text":"存在正在进行的不法侵害"},{"key":"B","text":"出于防卫意图"},{"key":"C","text":"针对不法侵害人本人实施"},{"key":"D","text":"不能明显超过必要限度造成重大损害"}]','answer','["A","B","C","D"]','analysis','正当防卫要求不法侵害正在进行、具有防卫意图、对象为不法侵害人，且不超过必要限度。','category','刑法') AS CHAR)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)
SELECT '2026-11-11-seed-17',q.id,JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category),
  JSON_OBJECT('type','MULTIPLE','stem','依据民法典第1165条、第1166条，下列属于侵权责任归责原则或过错责任具体形态的有？','options','[{"key":"A","text":"过错责任原则"},{"key":"B","text":"过错推定原则"},{"key":"C","text":"无过错责任原则"},{"key":"D","text":"公平分担损失"}]','answer','["A","B","C"]','analysis','民法典第1165条规定过错责任及依法推定过错的情形，第1166条规定依法适用的无过错责任，因此选择A、B、C。第1186条的依法分担损失属于损失分担规则，不应据此将D列为独立的侵权归责原则；双方无过错也不意味着可脱离法律规定任意分担。','category','民法'),
  JSON_ARRAY('https://www.stats.gov.cn/gk/tjfg/xgfxfg/202503/t20250312_1958939.html','https://www.a-court.gov.cn/xxfb/no1court_412/docs/202401/d_4001042.html')
FROM question q WHERE q.id=17
  AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(JSON_OBJECT('type','MULTIPLE','stem','侵权责任的归责原则包括？','options','[{"key":"A","text":"过错责任原则"},{"key":"B","text":"过错推定原则"},{"key":"C","text":"无过错责任原则"},{"key":"D","text":"公平分担损失"}]','answer','["A","B","C","D"]','analysis','侵权责任编确立了过错责任（含过错推定）与无过错责任原则，民法典第1186条规定了公平分担损失规则。','category','民法') AS CHAR)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

INSERT INTO question_content_revision(revision_key,question_id,before_content,after_content,evidence_urls)
SELECT '2026-11-11-seed-27',q.id,JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category),
  JSON_OBJECT('type','SHORT','stem','简述正当防卫与紧急避险的主要区别。','options',NULL,'answer','要点：1）起因条件不同：正当防卫针对正在进行的不法侵害，紧急避险针对正在发生的危险，并要求不得已；2）对象不同：正当防卫针对不法侵害人，紧急避险通常涉及其他合法利益的损害；3）限度不同：防卫过当须明显超过必要限度并造成重大损害，符合刑法第20条第3款的特殊防卫另依该款处理；紧急避险超过必要限度造成不应有损害的，依法承担责任；4）职责限制：刑法第21条第3款限制的是特定职责人员为避免本人危险而避险，不能扩张为其一切紧急避险均不适用。','analysis','依据刑法第20条、第21条，从起因、对象、限度与职责限制比较。易错点：紧急避险须出于不得已；特定职责人员限制须保留避免本人危险这一限定，防卫过当也不能省略明显超过与重大损害两个条件。','category','刑法'),
  JSON_ARRAY('https://fgk.chinatax.gov.cn/zcfgk/c100009/c5212248/content.html')
FROM question q WHERE q.id=27
  AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(JSON_OBJECT('type','SHORT','stem','简述正当防卫与紧急避险的主要区别。','options',NULL,'answer','要点：1）起因条件不同：正当防卫针对人的不法侵害，紧急避险针对正在发生的危险（可能来自自然力、动物或人的行为）；2）对象不同：正当防卫只能针对不法侵害人本人，紧急避险损害的是第三者的合法权益；3）限度要求不同：正当防卫以制止不法侵害所必需为限，紧急避险要求保全的利益大于损害的利益；4）主体限制不同：紧急避险不适用于职务上、业务上负有特定责任的人。','analysis','从起因、对象、限度与主体限制四个方面回答即可。','category','刑法') AS CHAR)
ON DUPLICATE KEY UPDATE revision_key=question_content_revision.revision_key;

UPDATE question q JOIN question_content_revision r ON r.question_id=q.id
SET q.stem=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.stem')),
    q.answer=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.answer')),
    q.analysis=JSON_UNQUOTE(JSON_EXTRACT(r.after_content,'$.analysis'))
WHERE r.revision_key IN ('2026-11-11-seed-15','2026-11-11-seed-17','2026-11-11-seed-27')
  AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(r.before_content AS CHAR);

UPDATE question_content_revision r JOIN question q ON q.id=r.question_id
SET r.applied_at=COALESCE(r.applied_at,NOW())
WHERE r.revision_key IN ('2026-11-11-seed-15','2026-11-11-seed-17','2026-11-11-seed-27')
  AND BINARY CAST(JSON_OBJECT('type',q.type,'stem',q.stem,'options',q.options,'answer',q.answer,'analysis',q.analysis,'category',q.category) AS CHAR)=BINARY CAST(r.after_content AS CHAR);
COMMIT;
