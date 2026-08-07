package com.lexaid.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单题答案
 */
@Data
public class AnswerItem {

    @NotNull(message = "questionId 不能为空")
    private Long questionId;
    private String answer;
}
