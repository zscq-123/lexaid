package com.lexaid.stats.dto;

/**
 * 类别正确率
 */
public record CategoryAccuracyVO(
        String category,
        long total,
        long correct,
        double accuracy
) {
}
