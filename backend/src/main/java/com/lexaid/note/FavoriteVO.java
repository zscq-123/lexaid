package com.lexaid.note;

import java.time.LocalDateTime;

/**
 * 收藏列表项
 */
public record FavoriteVO(Long id, String targetType, Long targetId, String title,
                         LocalDateTime createdAt, Long folderId) {
}
