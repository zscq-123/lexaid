package com.lexaid.content.dto;

import java.util.List;

/**
 * 观点列表项 / 详情视图
 */
public record ViewpointVO(
        Long id,
        String scholarName,
        String title,
        String summary,
        String content,
        String source,
        Integer publishYear,
        String languageCode,
        String sourceDataset,
        String sourceUrl,
        String doi,
        String licenseName,
        String summaryKind,
        List<String> tags,
        boolean favorite
) {
}
