package com.lexaid.ai.dto;

import com.lexaid.relation.RelatedItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话记录视图
 */
public record ChatMessageVO(
        Long id,
        String role,
        String content,
        List<RelatedItem> refs,
        LocalDateTime createdAt
) {
}
