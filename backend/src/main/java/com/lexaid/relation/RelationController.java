package com.lexaid.relation;

import com.lexaid.common.Result;
import com.lexaid.relation.dto.RelationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关联接口
 */
@RestController
@RequestMapping("/api/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    @GetMapping("/related")
    public Result<RelatedGroups> related(@RequestParam String sourceType,
                                         @RequestParam Long sourceId) {
        return Result.ok(relationService.relatedGroups(sourceType, sourceId));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody RelationRequest req) {
        relationService.addRelation(req.getSourceType(), req.getSourceId(),
                req.getTargetType(), req.getTargetId(), req.getRelationType(), req.getDescription());
        return Result.ok();
    }
}
