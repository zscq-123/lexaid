package com.lexaid.exam;

/** Basic teaching eligibility, not a claim that a legal expert verified the material. Aliases: q, s. */
public final class QuestionQuality {
    private QuestionQuality() {}

    public static final String HASH = "SHA2(CAST(JSON_ARRAY(q.type,q.stem,q.options,q.answer,q.analysis,q.category,"
            + QuestionSubjects.DISCIPLINE + "," + QuestionSubjects.SUBJECT + ") AS CHAR),256)";
    private static final String REVIEWED = "EXISTS (SELECT 1 FROM question_quality_review review "
            + "WHERE review.question_id=q.id AND review.content_hash=" + HASH + ")";
    private static final String OPTIONS = "IF(JSON_VALID(q.options),q.options,'null')";
    private static final String ANSWER = "IF(JSON_VALID(q.answer),q.answer,'null')";
    private static final String VALID_OPTIONS = "(SELECT COUNT(DISTINCT choice.option_key) FROM JSON_TABLE("
            + OPTIONS + ", '$[*]' COLUMNS (option_key VARCHAR(32) PATH '$.key' NULL ON ERROR, "
            + "option_text TEXT PATH '$.text' NULL ON ERROR)) choice "
            + "WHERE REGEXP_LIKE(choice.option_key,'^[A-H]$','c') AND choice.option_text REGEXP '[^[:space:]]')";
    private static final String UNIQUE_ANSWERS = "(SELECT COUNT(DISTINCT selected.option_key) FROM JSON_TABLE("
            + ANSWER + ", '$[*]' COLUMNS (option_key VARCHAR(32) PATH '$' NULL ON ERROR)) selected)";

    public static final String REASON = "CASE "
            + "WHEN NOT COALESCE(q.stem REGEXP '[^[:space:]]',0) OR NOT COALESCE(q.answer REGEXP '[^[:space:]]',0) THEN 'MISSING_CONTENT' "
            + "WHEN BINARY q.type NOT IN ('SINGLE','MULTIPLE','JUDGE','SHORT') THEN 'INVALID_TYPE' "
            + "WHEN q.type='JUDGE' AND BINARY q.answer NOT IN ('true','false') THEN 'INVALID_ANSWER' "
            + "WHEN q.type IN ('SINGLE','MULTIPLE') AND (JSON_TYPE(" + OPTIONS + ")!='ARRAY' "
            + "OR 2>JSON_LENGTH(" + OPTIONS + ") OR JSON_TYPE(" + ANSWER + ")!='ARRAY' "
            + "OR 1>JSON_LENGTH(" + ANSWER + ") OR (q.type='SINGLE' AND JSON_LENGTH(" + ANSWER + ")!=1) "
            + "OR JSON_LENGTH(" + OPTIONS + ")!=" + VALID_OPTIONS
            + " OR JSON_LENGTH(" + ANSWER + ")!=" + UNIQUE_ANSWERS + " "
            + "OR NOT COALESCE(JSON_CONTAINS(JSON_EXTRACT(" + OPTIONS + ",'$[*].key')," + ANSWER + "),0)) THEN 'INVALID_CHOICES' "
            + "WHEN NOT COALESCE(q.analysis REGEXP '[^[:space:]]',0) THEN 'MISSING_EXPLANATION' "
            + "WHEN q.analysis REGEXP '请只|仅输出|只需.{0,5}(输出|回答)|输出格式|实体识别|事件触发词|关系抽取|标注任务' THEN 'TASK_INSTRUCTION' "
            + "WHEN " + QuestionSubjects.SUBJECT + "='待判定' THEN 'UNCLASSIFIED' "
            + "WHEN EXISTS (SELECT 1 FROM question_source qs WHERE qs.question_id=q.id) "
            + "AND NOT " + REVIEWED + " THEN 'DATASET_REVIEW' "
            + "ELSE 'ELIGIBLE' END";
    public static final String ELIGIBLE = "(" + REASON + ")='ELIGIBLE'";
}
