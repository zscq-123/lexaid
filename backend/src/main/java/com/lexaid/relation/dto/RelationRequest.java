package com.lexaid.relation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String description;
}
