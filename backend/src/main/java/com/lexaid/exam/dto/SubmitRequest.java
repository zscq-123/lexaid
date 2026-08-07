package com.lexaid.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 交卷请求
 */
@Data
public class SubmitRequest {

    @NotNull(message = "answers 不能为空")
    @Valid
    private List<AnswerItem> answers;
}
