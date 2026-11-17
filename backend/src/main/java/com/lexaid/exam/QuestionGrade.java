package com.lexaid.exam;

/** A null score is excluded from both the earned and available points. */
record QuestionGrade(String status, Integer points, String feedback) {
    static QuestionGrade quarantined() {
        return new QuestionGrade("QUARANTINED", null, "题目已更新或正在复核，暂停评分；已保存的作答仍可查看。");
    }

    static boolean referenceCurrent(Question question, AnswerRecord answer) {
        return answer != null && (question.getUpdatedAt() == null || (answer.getAnsweredAt() != null
                && !question.getUpdatedAt().isAfter(answer.getAnsweredAt())));
    }

    /** Keep completed historical scores, but never repeat unreviewed grading explanations. */
    static QuestionGrade historical(AnswerRecord answer, String type, boolean eligible) {
        QuestionGrade grade = stored(answer, type);
        if (eligible || "SKIPPED".equals(grade.status())) return grade;
        if (grade.points() == null) return quarantined();
        return new QuestionGrade(grade.status(), grade.points(), null);
    }

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

    static QuestionGrade stored(AnswerRecord answer, String type) {
        if (answer != null && !AnswerStatus.answered(type, answer.getUserAnswer())) {
            return new QuestionGrade("SKIPPED", null, "未作答 / 跳过，不计入答题次数、正确率和错题本。");
        }
        return stored(answer);
    }
}
