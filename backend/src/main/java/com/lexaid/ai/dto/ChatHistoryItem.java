package com.lexaid.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 历史对话条目
 */
public record ChatHistoryItem(
        @Pattern(regexp = "user|assistant", message = "历史消息角色不合法") String role,
        @NotBlank(message = "历史消息内容不能为空")
        @Size(max = 4000, message = "单条历史消息最长 4000 个字符") String content) {
}
