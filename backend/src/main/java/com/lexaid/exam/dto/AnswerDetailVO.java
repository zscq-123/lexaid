package com.lexaid.exam.dto;

import com.lexaid.relation.RelatedItem;

import java.util.List;

/**
 * 交卷结果中的逐题解析
 */
public record AnswerDetailVO(
        Long questionId,
        String type,
        String stem,
        List<QuestionOption> options,
        String userAnswer,
        String correctAnswer,
        Boolean isCorrect,
        String analysis,
        List<RelatedItem> related,
        Integer score,
        int maxScore,
        String gradingStatus,
        String feedback,
        String discipline,
        String subject,
        List<QuestionBook> codeBooks
) {
}
