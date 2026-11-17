package com.lexaid.exam;

/** One definition of an actual answer, shared by practice, coverage and statistics. */
public final class AnswerStatus {
    private AnswerStatus() {}

    public static final String ANSWERED = "ar.user_answer REGEXP '[^[:space:]]' "
            + "AND (q.type != 'MULTIPLE' OR REGEXP_REPLACE(ar.user_answer,'[[:space:]]+','') != '[]')";
    public static final String NEWER_ANSWERED = "ar2.user_answer REGEXP '[^[:space:]]' "
            + "AND (q.type != 'MULTIPLE' OR REGEXP_REPLACE(ar2.user_answer,'[[:space:]]+','') != '[]')";

    public static boolean answered(String type, String answer) {
        if (answer == null) return false;
        String compact = answer.replaceAll("(?U)\\s+", "");
        return !compact.isEmpty() && !("MULTIPLE".equals(type) && "[]".equals(compact));
    }
}
