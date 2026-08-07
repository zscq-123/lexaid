package com.lexaid.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 笔记新增/修改请求
 */
@Data
public class NoteRequest {

    @NotBlank(message = "targetType 不能为空")
    private String targetType;
    @NotNull(message = "targetId 不能为空")
    private Long targetId;
    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
