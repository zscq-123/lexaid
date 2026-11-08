package com.lexaid.stats;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.common.UserContext;
import com.lexaid.stats.dto.CategoryAccuracyVO;
import com.lexaid.stats.dto.DailyStatVO;
import com.lexaid.stats.dto.OverviewVO;
import com.lexaid.stats.dto.StudyRecordVO;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习统计接口
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Validated
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.ok(statsService.overview(UserContext.userId()));
    }

    @GetMapping("/daily")
    public Result<List<DailyStatVO>> daily(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return Result.ok(statsService.daily(UserContext.userId(), days));
    }

    @GetMapping("/category-accuracy")
    public Result<List<CategoryAccuracyVO>> categoryAccuracy() {
        return Result.ok(statsService.categoryAccuracy(UserContext.userId()));
    }

    @GetMapping("/records")
    public Result<PageResult<StudyRecordVO>> records(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(statsService.records(UserContext.userId(), page, size));
    }
}
