package com.lexaid.stats.dto;

/**
 * 学习概览
 */
public record OverviewVO(
        long totalStudyMinutes,
        long totalAnswer,
        long totalCorrect,
        double accuracy,
        long streakDays,
        long distinctQuestions,
        long gradedAnswer,
        long skippedAnswer,
        long repeatedAnswer,
        long totalIncorrect,
        long ungradedAnswer
) {
}
