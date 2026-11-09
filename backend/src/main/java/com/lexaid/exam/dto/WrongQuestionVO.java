package com.lexaid.exam.dto;

import com.lexaid.relation.RelatedItem;

import java.util.List;

/**
 * 错题本条目
 */
public record WrongQuestionVO(
        Long questionId,
        String type,
        String stem,
        List<QuestionOption> options,
        String userAnswer,
        String correctAnswer,
        String analysis,
        String category,
        Boolean isCorrect,
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
