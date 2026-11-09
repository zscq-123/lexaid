package com.lexaid.community.posts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class DiscussionSummaryController {
    private final DiscussionSummaryService service;

    @GetMapping("/posts/{id}/discussion-summary")
    public Result<DiscussionSummaryService.View> summary(@PathVariable long id) throws JsonProcessingException {
        return Result.ok(service.read(id));
    }

    @GetMapping("/discussion-summaries")
    public Result<PageResult<DiscussionSummaryService.Card>> library(
            @RequestParam(defaultValue = "") @Size(max=200) String keyword,
            @RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(50) int size) {
        return Result.ok(service.library(keyword,page,size));
    }
}
