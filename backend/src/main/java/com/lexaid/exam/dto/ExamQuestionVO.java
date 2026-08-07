package com.lexaid.exam.dto;

import com.lexaid.relation.RelatedItem;

import java.util.List;

/**
 * 试卷题目视图（不含答案与解析）
 */
public record ExamQuestionVO(
        Long id,
        String type,
        String stem,
        List<QuestionOption> options,
        Integer difficulty,
        String category,
        List<RelatedItem> related
) {
}
