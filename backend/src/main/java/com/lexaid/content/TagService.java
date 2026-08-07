package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签服务：批量查询实体标签
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final EntityTagMapper entityTagMapper;
    private final TagMapper tagMapper;

    /**
     * 批量查询某类型多个实体的标签，返回 id -> 标签名列表
     */
    public Map<Long, List<String>> listTags(String type, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<EntityTag> ets = entityTagMapper.selectList(new LambdaQueryWrapper<EntityTag>()
                .eq(EntityTag::getTargetType, type)
                .in(EntityTag::getTargetId, ids));
        if (ets.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = ets.stream().map(EntityTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> nameMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> result = new HashMap<>();
        for (EntityTag et : ets) {
            result.computeIfAbsent(et.getTargetId(), k -> new ArrayList<>())
                    .add(nameMap.getOrDefault(et.getTagId(), ""));
        }
        return result;
    }

    public List<String> tagsOf(String type, Long id) {
        return listTags(type, List.of(id)).getOrDefault(id, List.of());
    }

    /**
     * 根据标签名查询某类型实体 id 列表（空则返回空列表）
     */
    public List<Long> idsWithTag(String type, String tagName) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
        if (tag == null) {
            return List.of();
        }
        return entityTagMapper.selectList(new LambdaQueryWrapper<EntityTag>()
                        .eq(EntityTag::getTargetType, type)
                        .eq(EntityTag::getTagId, tag.getId()))
                .stream().map(EntityTag::getTargetId).toList();
    }
}
