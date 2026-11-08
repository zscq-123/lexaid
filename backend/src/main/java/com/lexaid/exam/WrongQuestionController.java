package com.lexaid.exam;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.exam.dto.WrongQuestionVO;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本接口
 */
@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
@Validated
public class WrongQuestionController {

    private final ExamService examService;

    @GetMapping
    public Result<PageResult<WrongQuestionVO>> wrongQuestions(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(defaultValue = "false") boolean all) {
        return Result.ok(examService.wrongQuestions(page, size, all));
    }
}
