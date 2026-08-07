package com.lexaid.stats.dto;

import java.time.LocalDateTime;

/**
 * 学习足迹
 */
public record StudyRecordVO(
        Long id,
        String actionType,
        String targetType,
        Long targetId,
        String targetTitle,
        Integer durationSec,
        String detail,
        LocalDateTime createdAt
) {
}
