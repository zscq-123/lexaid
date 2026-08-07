package com.lexaid.exam.dto;

import java.util.List;

/**
 * 交卷结果
 */
public record ExamResultVO(
        Long sessionId,
        Integer score,
        Integer total,
        Integer correct,
        List<AnswerDetailVO> details
) {
}
