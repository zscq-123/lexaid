package com.lexaid.relation;

import java.util.List;

/**
 * 按类型聚合的关联实体
 */
public record RelatedGroups(
        List<RelatedItem> provisions,
        List<RelatedItem> cases,
        List<RelatedItem> viewpoints,
        List<RelatedItem> questions
) {
}
