package com.lexaid.exam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单题答案
 */
@Data
public class AnswerItem {

    @NotNull(message = "questionId 不能为空")
    private Long questionId;
    @Size(max = 10000, message = "单题答案最长 10000 个字符")
    private String answer;
}
