package com.lexaid.note;

import java.time.LocalDateTime;

/**
 * 笔记视图
 */
public record NoteVO(
        Long id,
        String targetType,
        Long targetId,
        String targetTitle,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
