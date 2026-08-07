package com.lexaid.stats.dto;

import java.time.LocalDate;

/**
 * 每日统计
 */
public record DailyStatVO(
        LocalDate date,
        long answerCount,
        long correctCount,
        long studyMinutes
) {
}
