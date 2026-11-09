package com.lexaid.content;

import com.lexaid.common.PageResult;
import com.lexaid.common.Result;
import com.lexaid.content.dto.ProvisionCategoryVO;
import com.lexaid.content.dto.ProvisionVO;
import com.lexaid.relation.RelatedGroups;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 法条接口
 */
@RestController
@RequestMapping("/api/provisions")
@RequiredArgsConstructor
@Validated
public class ProvisionController {

    private final ProvisionService provisionService;
    private final ProvisionOutline outline;

    @GetMapping
    public Result<PageResult<ProvisionVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String lawName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) String languageCode,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @Size(max=2000) String code,
            @RequestParam(required = false) @Size(max=1200) String path,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(provisionService.page(
                keyword, lawName, category, jurisdiction, languageCode, tag, page, size, code, path));
    }

    @GetMapping("/outline/codes")
    public Result<List<ProvisionOutline.Code>> codes(@RequestParam(required=false) String jurisdiction) {
        return Result.ok(outline.codes(jurisdiction));
    }

    @GetMapping("/outline")
    public Result<List<ProvisionOutline.Node>> directory(@RequestParam @NotBlank @Size(max=16) String jurisdiction,
                                                          @RequestParam @NotBlank @Size(max=2000) String code) {
        return Result.ok(outline.tree(jurisdiction, code));
    }

    @GetMapping("/categories")
    public Result<List<ProvisionCategoryVO>> categories() {
        return Result.ok(provisionService.categories());
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
