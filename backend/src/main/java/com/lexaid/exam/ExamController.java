package com.lexaid.exam;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.exam.dto.ExamQuestionVO;
import com.lexaid.exam.dto.ExamResultVO;
import com.lexaid.exam.dto.ExamSessionVO;
import com.lexaid.exam.dto.ExamSetupRequest;
import com.lexaid.exam.dto.QuestionCategoryCount;
import com.lexaid.exam.dto.QuestionProgress;
import com.lexaid.exam.dto.QuestionScope;
import com.lexaid.exam.dto.CodeBookCount;
import com.lexaid.exam.dto.SubjectCount;
import com.lexaid.exam.dto.SubmitRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * 答题接口
 */
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
@Validated
public class ExamController {

    private final ExamService examService;

    @GetMapping("/categories")
    public Result<List<QuestionCategoryCount>> categories() {
        return Result.ok(examService.categoryCounts());
    }

    @GetMapping("/subjects")
    public Result<List<SubjectCount>> subjects() {
        return Result.ok(examService.subjectCounts());
    }

    @GetMapping("/code-books")
    public Result<List<CodeBookCount>> codeBooks() {
        return Result.ok(examService.codeBookCounts());
    }

    @GetMapping("/progress")
    public Result<QuestionProgress> progress(@Valid @ModelAttribute QuestionScope scope) {
        return Result.ok(examService.progress(scope));
    }

    @PostMapping("/sessions")
    public Result<Map<String, Long>> create(@Valid @RequestBody ExamSetupRequest req) {
        Long sessionId = examService.createSession(req);
        return Result.ok(Map.of("sessionId", sessionId));
    }

    @GetMapping("/sessions/{id}")
    public Result<List<ExamQuestionVO>> getSession(@PathVariable Long id) {
        return Result.ok(examService.questionsOf(id));
    }

    @PostMapping("/sessions/{id}/submit")
    public Result<ExamResultVO> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest req) {
        return Result.ok(examService.submit(id, req));
    }

    @GetMapping("/sessions/{id}/result")
    public Result<ExamResultVO> result(@PathVariable Long id) {
        return Result.ok(examService.resultOf(id));
    }

    @PostMapping("/sessions/{id}/retry-grading")
    public Result<ExamResultVO> retryGrading(@PathVariable Long id) {
        return Result.ok(examService.retryGrading(id));
    }

    @GetMapping("/sessions")
    public Result<PageResult<ExamSessionVO>> history(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(examService.history(page, size));
    }

}
