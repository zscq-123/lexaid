package com.lexaid.relation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建关联请求
 */
@Data
public class RelationRequest {

    @NotBlank(message = "sourceType 不能为空")
    private String sourceType;
    @NotNull(message = "sourceId 不能为空")
    private Long sourceId;
    @NotBlank(message = "targetType 不能为空")
    private String targetType;
    @NotNull(message = "targetId 不能为空")
    private Long targetId;
    @NotBlank(message = "relationType 不能为空")
    private String relationType;
    @Size(max = 255, message = "关系描述最长 255 个字符")
    private String description;
}
