package com.lexaid.content;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.content.dto.CaseVO;
import com.lexaid.relation.RelatedGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 案例接口
 */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @GetMapping
    public Result<PageResult<CaseVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String court,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(caseService.page(keyword, caseType, court, year, page, size));
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
