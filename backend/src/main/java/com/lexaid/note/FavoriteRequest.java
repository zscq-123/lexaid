package com.lexaid.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏请求
 */
@Data
public class FavoriteRequest {

    @NotBlank(message = "targetType 不能为空")
    private String targetType;
    @NotNull(message = "targetId 不能为空")
    private Long targetId;
}
