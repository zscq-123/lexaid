package com.lexaid.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 提问请求
 */
@Data
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 4000, message = "问题最长 4000 个字符")
    private String message;
    private String contextType;
    private Long contextId;
    @Valid
    @Size(max = 20, message = "最多携带 20 条历史消息")
    private List<ChatHistoryItem> history;
}
