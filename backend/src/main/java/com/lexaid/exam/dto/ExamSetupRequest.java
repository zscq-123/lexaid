package com.lexaid.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组卷请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamSetupRequest extends QuestionScope {

    @NotNull(message = "题量不能为空")
    @Min(value = 1, message = "题量至少 1 道")
    @Max(value = 50, message = "单次题量最多 50 道")
    private Integer count;

    private String sourceType;
    private Long sourceId;
}
