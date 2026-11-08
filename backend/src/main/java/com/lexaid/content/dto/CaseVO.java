package com.lexaid.content.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 案例列表项 / 详情视图
 */
public record CaseVO(
        Long id,
        String title,
        String caseNo,
        String court,
        LocalDate judgeDate,
        String caseType,
        String summary,
        String holding,
        String procedure,
        String content,
        String jurisdiction,
        String languageCode,
        String status,
        String sourceDataset,
        String sourceUrl,
        String licenseName,
        List<String> tags,
        boolean favorite
) {
}
