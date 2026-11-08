package com.lexaid.content.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 法条列表项 / 详情视图
 */
public record ProvisionVO(
        Long id,
        String lawName,
        String category,
        String chapter,
        String articleNo,
        String content,
        LocalDate effectiveDate,
        String issuingAuthority,
        String jurisdiction,
        String languageCode,
        String status,
        String sourceDataset,
        String sourceUrl,
        String licenseName,
        LocalDate promulgationDate,
        List<String> tags,
        boolean favorite
) {
}
