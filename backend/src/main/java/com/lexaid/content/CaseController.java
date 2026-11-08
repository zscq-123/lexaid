package com.lexaid.content;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.content.dto.CaseVO;
import com.lexaid.content.dto.CourtOption;
import com.lexaid.relation.RelatedGroups;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 案例接口
 */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Validated
public class CaseController {

    private final CaseService caseService;
    private final CaseCourtDirectory courtDirectory;

    @GetMapping("/courts")
    public Result<List<CourtOption>> courts() {
        return Result.ok(courtDirectory.options());
    }

    @GetMapping
    public Result<PageResult<CaseVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String court,
            @RequestParam(required = false) String courtNode,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) String languageCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(caseService.page(
                keyword, caseType, court, courtNode, jurisdiction, languageCode, year, page, size));
    }

    @GetMapping("/{id}")
    public Result<CaseVO> detail(@PathVariable Long id) {
        return Result.ok(caseService.detail(id));
    }

    @GetMapping("/{id}/related")
    public Result<RelatedGroups> related(@PathVariable Long id) {
        return Result.ok(caseService.related(id));
    }
}
