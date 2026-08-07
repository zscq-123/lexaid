package com.lexaid.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 组卷请求
 */
@Data
public class ExamSetupRequest {

    private String category;

    @Min(value = 1, message = "难度需在 1~5 之间")
    @Max(value = 5, message = "难度需在 1~5 之间")
    private Integer difficulty;

    @NotNull(message = "题量不能为空")
    @Min(value = 1, message = "题量至少 1 道")
    @Max(value = 50, message = "单次题量最多 50 道")
    private Integer count;

    private String sourceType;
    private Long sourceId;
}
