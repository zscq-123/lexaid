package com.lexaid.exam;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wrong-questions")
public class ScenarioPracticeController {
    private final ScenarioPracticeService practice;
    record Answer(@NotNull @Pattern(regexp = "[A-D]") String choice) {}

    @PostMapping("/{questionId}/scenarios")
    public Result<ScenarioPracticeService.View> open(@PathVariable long questionId, @RequestParam(required = false) Long after) {
        return Result.ok(practice.open(UserContext.userId(), questionId, after));
    }
    @PostMapping("/scenarios/{id}/answer")
    public Result<ScenarioPracticeService.View> answer(@PathVariable long id, @Valid @RequestBody Answer answer) {
        return Result.ok(practice.submit(UserContext.userId(), id, answer.choice()));
    }
}
