package com.lexaid.content;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.content.dto.ViewpointVO;
import com.lexaid.relation.RelatedGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学者观点接口
 */
@RestController
@RequestMapping("/api/viewpoints")
@RequiredArgsConstructor
public class ViewpointController {

    private final ViewpointService viewpointService;

    @GetMapping
    public Result<PageResult<ViewpointVO>> page(
            @RequestParam(required = false) String scholar,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(viewpointService.page(scholar, keyword, tag, page, size));
    }

    @GetMapping("/{id}")
    public Result<ViewpointVO> detail(@PathVariable Long id) {
        return Result.ok(viewpointService.detail(id));
    }

    @GetMapping("/{id}/related")
    public Result<RelatedGroups> related(@PathVariable Long id) {
        return Result.ok(viewpointService.related(id));
    }
}
