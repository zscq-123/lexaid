package com.lexaid.ai.dto;

import com.lexaid.relation.RelatedItem;

import java.util.List;

/**
 * AI 回答
 */
public record ChatResponse(String answer, List<RelatedItem> refs) {
}
