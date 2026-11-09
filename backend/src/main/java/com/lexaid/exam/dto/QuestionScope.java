package com.lexaid.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Filters shared by exam generation and personal question-bank progress. */
@Data
public class QuestionScope {
    private String category;
    private String discipline;
    private String subject;
    private String codeName;
    private String bookName;

    @Min(value = 1, message = "难度需在 1~5 之间")
    @Max(value = 5, message = "难度需在 1~5 之间")
    private Integer difficulty;
}
