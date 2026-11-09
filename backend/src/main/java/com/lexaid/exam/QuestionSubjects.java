package com.lexaid.exam;

/** Shared SQL projection for the primary legal classification of a question (aliases q and s). */
public final class QuestionSubjects {
    private QuestionSubjects() {}

    public static final String JOIN_SUBJECTS = " LEFT JOIN question_legal_subject s ON s.question_id=q.id ";
    public static final String FROM_QUESTION = " FROM question q " + JOIN_SUBJECTS;
    public static final String DISCIPLINE = """
            COALESCE(s.discipline, CASE TRIM(q.category)
              WHEN '刑法' THEN '刑法' WHEN '民法' THEN '民商法' WHEN '行政法' THEN '宪法与行政法'
              ELSE '综合与待判定' END)
            """;
    public static final String SUBJECT = """
            COALESCE(s.subject, CASE TRIM(q.category)
              WHEN '刑法' THEN '刑法学' WHEN '民法' THEN '民法学' WHEN '行政法' THEN '行政法学'
              ELSE '待判定' END)
            """;
}
