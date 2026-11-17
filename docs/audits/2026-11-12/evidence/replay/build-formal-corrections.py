from pathlib import Path
import json,copy
root=Path('/Users/yangyongze/Desktop/lexaid/lexaid');p=Path(__file__).parent
before={r['id']:r for r in map(json.loads,(p/'formal-source.jsonl').read_text().splitlines())}
originals={r['id']:r for r in json.loads((root/'datasets/formal-exams/original-subjective.json').read_text())}
reasons={8000004:'原题把保证人的逃匿与先诉抗辩例外混淆；明确排除一般保证先诉抗辩的法定例外。',8000006:'修正“违法定程序”为“违反法定程序”。',8000008:'明确行政处罚听证申请五日是工作日，不含法定节假日。'}
proposals=[]
for qid,reason in reasons.items():
 old=before[qid];new=copy.deepcopy(old)
 new.update(stem=originals[qid]['stem'],answer=originals[qid]['answer'])
 urls=[originals[qid]['sourceUrl']]
 if qid==8000004:urls.append('https://www.court.gov.cn/zixun/xiangqing/233181.html')
 proposals.append({'id':qid,'revisionKey':f'2026-11-12-formal-{qid}','before':old,'after':new,'reason':reason,'evidenceUrls':urls})
(p/'formal-corrections.json').write_text(json.dumps(proposals,ensure_ascii=False,indent=2)+'\n')
columns={'id':'id','sourceType':'source_type','paperType':'paper_type','kind':'kind','subject':'subject','difficulty':'difficulty','stem':'stem','options':'options_json','answer':'reference_answer','explanation':'explanation','sourceLabel':'source_label','sourceUrl':'source_url','readingUrl':'reading_url'}
content='JSON_OBJECT('+','.join("'"+k+"',q."+v for k,v in columns.items())+')'
def j(v):return "CAST(CONVERT(0x"+json.dumps(v,ensure_ascii=False,separators=(',',':')).encode().hex()+" USING utf8mb4) AS JSON)"
match=lambda a,b:'BINARY CAST('+a+' AS CHAR)=BINARY CAST('+b+' AS CHAR)'
sql=['-- Three focused original mock-exam corrections. Existing exam snapshots are unchanged.',
'''CREATE TABLE IF NOT EXISTS mock_exam_content_revision (
 revision_key VARCHAR(80) PRIMARY KEY,
 question_id BIGINT NOT NULL,
 before_content JSON NOT NULL,
 after_content JSON NOT NULL,
 evidence_urls JSON NOT NULL,
 applied_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TEMPORARY TABLE IF EXISTS lexaid_formal_review_candidates;
CREATE TEMPORARY TABLE lexaid_formal_review_candidates (
 revision_key VARCHAR(80) PRIMARY KEY, question_id BIGINT NOT NULL,
 before_content JSON NOT NULL, after_content JSON NOT NULL, evidence_urls JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO lexaid_formal_review_candidates VALUES''',
',\n'.join("('"+r['revisionKey']+"',"+str(r['id'])+','+j(r['before'])+','+j(r['after'])+','+j(r['evidenceUrls'])+')' for r in proposals)+';',
'START TRANSACTION;',
'INSERT INTO mock_exam_content_revision(revision_key,question_id,before_content,after_content,evidence_urls) SELECT c.revision_key,c.question_id,c.before_content,c.after_content,c.evidence_urls FROM lexaid_formal_review_candidates c JOIN mock_exam_bank q ON q.id=c.question_id WHERE '+match(content,'c.before_content')+' ON DUPLICATE KEY UPDATE revision_key=mock_exam_content_revision.revision_key;',
'UPDATE mock_exam_bank q JOIN lexaid_formal_review_candidates c ON c.question_id=q.id JOIN mock_exam_content_revision r ON r.revision_key=c.revision_key AND r.question_id=q.id SET q.stem=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,\'$.stem\')),q.reference_answer=JSON_UNQUOTE(JSON_EXTRACT(c.after_content,\'$.answer\')) WHERE '+match(content,'c.before_content')+' AND '+match('r.before_content','c.before_content')+' AND '+match('r.after_content','c.after_content')+' AND '+match('r.evidence_urls','c.evidence_urls')+';',
'SET @lexaid_formal_changed=ROW_COUNT();',
'UPDATE mock_exam_content_revision r JOIN lexaid_formal_review_candidates c ON c.revision_key=r.revision_key AND c.question_id=r.question_id JOIN mock_exam_bank q ON q.id=c.question_id SET r.applied_at=COALESCE(r.applied_at,NOW()) WHERE '+match(content,'c.after_content')+' AND '+match('r.before_content','c.before_content')+' AND '+match('r.after_content','c.after_content')+' AND '+match('r.evidence_urls','c.evidence_urls')+';',
'COMMIT;',
'SELECT @lexaid_formal_changed AS changed_this_run;',
"SELECT c.question_id,CASE WHEN "+match(content,'c.after_content')+" AND r.applied_at IS NOT NULL AND "+match('r.before_content','c.before_content')+' AND '+match('r.after_content','c.after_content')+' AND '+match('r.evidence_urls','c.evidence_urls')+" THEN 'APPLIED_OR_ALREADY_APPLIED' ELSE 'SKIPPED_CONTENT_OR_ARCHIVE_MISMATCH' END AS result FROM lexaid_formal_review_candidates c LEFT JOIN mock_exam_bank q ON q.id=c.question_id LEFT JOIN mock_exam_content_revision r ON r.revision_key=c.revision_key AND r.question_id=c.question_id ORDER BY c.question_id;",
'DROP TEMPORARY TABLE lexaid_formal_review_candidates;']
out=root/'backend/src/main/resources/mock-exam-content-review-20261112.sql';out.write_text('\n\n'.join(sql)+'\n')
print(out,len(out.read_bytes()))
