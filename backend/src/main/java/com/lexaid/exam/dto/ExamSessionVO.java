package com.lexaid.exam.dto;

import java.time.LocalDateTime;

/**
 * 考试历史列表项
 */
public record ExamSessionVO(
        Long id,
        Integer total,
        Integer correct,
        Integer score,
        Integer durationSec,
        String category,
        Integer difficulty,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String discipline,
        String subject,
        String codeName,
        String bookName
) {
}
