package com.lexaid.mockexam;

import com.lexaid.exam.dto.QuestionOption;
import jakarta.validation.constraints.*;
import java.util.List;

public final class MockExam {
    private MockExam() {}
    public record Start(@Pattern(regexp="REAL|MOCK") @NotNull String sourceType,
                        @Pattern(regexp="OBJECTIVE|SUBJECTIVE") @NotNull String paperType,
                        @NotNull @Min(1) @Max(480) Integer durationMinutes) {}
    public record Draft(@NotNull @Size(max=16000) String answer, @NotNull @Min(1) Integer revision) {}
    public record Saved(boolean accepted, int revision, String answer, String status) {}
    public record Section(String kind, int count, int points, int availableCount) {}
    public record Format(String sourceType, String paperType, int durationMinutes, boolean available,
                         List<Section> sections, int totalPoints) {}
    public record Session(long id,String sourceType,String paperType,String status,long startedAt,long deadline,
                          long earlySubmitAt,long serverTime,Long submittedAt,int totalPoints,
                          Integer score,int earnedPoints,List<Item> items) {}
    public record Item(int position,String kind,String stem,List<QuestionOption> options,String subject,int maxPoints,
                       String answer,int revision,String gradeStatus,Integer points,String referenceAnswer,
                       String feedback,String sourceLabel,String sourceUrl,String explanation) {}
}
