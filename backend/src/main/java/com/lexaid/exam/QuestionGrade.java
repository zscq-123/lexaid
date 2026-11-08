package com.lexaid.exam;

/** A null score is excluded from both the earned and available points. */
record QuestionGrade(String status, Integer points, String feedback) {
    static int maxPoints(String type) {
        return "SHORT".equals(type) ? 10 : 1;
    }

    static QuestionGrade stored(AnswerRecord answer) {
        if (answer != null && answer.getGradingStatus() != null) {
            return new QuestionGrade(answer.getGradingStatus(), answer.getGradePoints(), answer.getGradingFeedback());
        }
        if (answer != null && answer.getIsCorrect() != null) {
            return new QuestionGrade("OBJECTIVE", answer.getIsCorrect() ? 1 : 0, null);
        }
        return new QuestionGrade("LEGACY_UNGRADED", null, "这道题在原试卷中未评分。");
    }
}
