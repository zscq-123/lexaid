package com.lexaid.mockexam;

import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.lexaid.mockexam.MockExam.*;

@RestController
@RequestMapping("/api/mock-exams")
@RequiredArgsConstructor
public class MockExamController {
    private final MockExamService exams;
    @GetMapping("/catalog") public Result<List<Format>> catalog() { return Result.ok(exams.catalog()); }
    @GetMapping("/active") public Result<Session> active() { return Result.ok(exams.active(UserContext.userId())); }
    @GetMapping("/history") public Result<List<Session>> history() { return Result.ok(exams.history(UserContext.userId())); }
    @PostMapping("/sessions") public Result<Session> start(@Valid @RequestBody Start input) {
        return Result.ok(exams.start(UserContext.userId(),input));
    }
    @GetMapping("/sessions/{id}") public Result<Session> session(@PathVariable long id) {
        return Result.ok(exams.session(UserContext.userId(),id));
    }
    @PutMapping("/sessions/{id}/answers/{position}")
    public Result<Saved> save(@PathVariable long id,@PathVariable int position,@Valid @RequestBody Draft input) {
        return Result.ok(exams.save(UserContext.userId(),id,position,input));
    }
    @PostMapping("/sessions/{id}/submit") public Result<Session> submit(@PathVariable long id) {
        return Result.ok(exams.submit(UserContext.userId(),id));
    }
    @PostMapping("/sessions/{id}/retry-grading") public Result<Session> retry(@PathVariable long id) {
        return Result.ok(exams.retry(UserContext.userId(),id));
    }
}
