package com.lexaid.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 交卷请求
 */
@Data
public class SubmitRequest {

    @NotNull(message = "answers 不能为空")
    @Valid
    @Size(max = 50, message = "单次最多提交 50 道题")
    private List<@NotNull AnswerItem> answers;
}
