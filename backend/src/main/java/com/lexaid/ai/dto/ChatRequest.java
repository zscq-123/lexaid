package com.lexaid.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 提问请求
 */
@Data
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    private String message;
    private String contextType;
    private Long contextId;
    private List<ChatHistoryItem> history;
}
