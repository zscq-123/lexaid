package com.lexaid.content;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.content.dto.ProvisionVO;
import com.lexaid.relation.RelatedGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 法条接口
 */
@RestController
@RequestMapping("/api/provisions")
@RequiredArgsConstructor
public class ProvisionController {

    private final ProvisionService provisionService;

    @GetMapping
    public Result<PageResult<ProvisionVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String lawName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(provisionService.page(keyword, lawName, category, tag, page, size));
    }

    @GetMapping("/{id}")
    public Result<ProvisionVO> detail(@PathVariable Long id) {
        return Result.ok(provisionService.detail(id));
    }

    @GetMapping("/{id}/related")
    public Result<RelatedGroups> related(@PathVariable Long id) {
        return Result.ok(provisionService.related(id));
    }
}
