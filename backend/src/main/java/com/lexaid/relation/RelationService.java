package com.lexaid.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.BizException;
import com.lexaid.common.TextUtil;
import com.lexaid.content.CaseInfo;
import com.lexaid.content.CaseInfoMapper;
import com.lexaid.content.EntityChecker;
import com.lexaid.content.Provision;
import com.lexaid.content.ProvisionMapper;
import com.lexaid.content.ScholarViewpoint;
import com.lexaid.content.ScholarViewpointMapper;
import com.lexaid.exam.Question;
import com.lexaid.exam.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体关联服务：双向查询与写入
 */
@Service
@RequiredArgsConstructor
public class RelationService {

    public static final Set<String> TYPES = Set.of("PROVISION", "CASE", "VIEWPOINT", "QUESTION");
    public static final Set<String> RELATION_TYPES = Set.of(
            "RELIES_ON", "INTERPRETS", "ANALYZES", "EXAMINES", "RELATED");

    private final EntityRelationMapper entityRelationMapper;
    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final QuestionMapper questionMapper;
    private final EntityChecker entityChecker;

    /**
     * 双向查询：返回与指定实体关联的内容，按类型分组
     */
    public RelatedGroups relatedGroups(String sourceType, Long sourceId) {
        validateType(sourceType);
        List<EntityRelation> rels = entityRelationMapper.selectList(
                new LambdaQueryWrapper<EntityRelation>()
                        .and(w -> w.eq(EntityRelation::getSourceType, sourceType)
                                .eq(EntityRelation::getSourceId, sourceId)
                                .or()
                                .eq(EntityRelation::getTargetType, sourceType)
                                .eq(EntityRelation::getTargetId, sourceId)));

        Map<String, LinkedHashSet<Long>> ids = new LinkedHashMap<>();
        ids.put("PROVISION", new LinkedHashSet<>());
        ids.put("CASE", new LinkedHashSet<>());
        ids.put("VIEWPOINT", new LinkedHashSet<>());
        ids.put("QUESTION", new LinkedHashSet<>());
        for (EntityRelation r : rels) {
            if (sourceType.equals(r.getSourceType()) && sourceId.equals(r.getSourceId())) {
                addId(ids, r.getTargetType(), r.getTargetId());
            } else {
                addId(ids, r.getSourceType(), r.getSourceId());
            }
        }
        // 排除源实体自身
        ids.get(sourceType).remove(sourceId);

        return new RelatedGroups(
                buildItems("PROVISION", ids.get("PROVISION"), idSet -> {
                    Map<Long, RelatedItem> m = new HashMap<>();
                    for (Provision p : provisionMapper.selectBatchIds(idSet)) {
                        m.put(p.getId(), provisionItem(p));
                    }
                    return m;
                }),
                buildItems("CASE", ids.get("CASE"), idSet -> {
                    Map<Long, RelatedItem> m = new HashMap<>();
                    for (CaseInfo c : caseInfoMapper.selectBatchIds(idSet)) {
                        m.put(c.getId(), caseItem(c));
                    }
                    return m;
                }),
                buildItems("VIEWPOINT", ids.get("VIEWPOINT"), idSet -> {
                    Map<Long, RelatedItem> m = new HashMap<>();
                    for (ScholarViewpoint v : viewpointMapper.selectBatchIds(idSet)) {
                        m.put(v.getId(), viewpointItem(v));
                    }
                    return m;
                }),
                buildItems("QUESTION", ids.get("QUESTION"), idSet -> {
                    Map<Long, RelatedItem> m = new HashMap<>();
                    for (Question q : questionMapper.classifiedQuestions(List.copyOf(idSet))) {
                        m.put(q.getId(), questionItem(q));
                    }
                    return m;
                }));
    }

    private void addId(Map<String, LinkedHashSet<Long>> map, String type, Long id) {
        if (TYPES.contains(type) && id != null) {
            map.get(type).add(id);
        }
    }

    private List<RelatedItem> buildItems(String type, Set<Long> ids, IdTitleLoader loader) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, RelatedItem> byId = loader.load(ids);
        List<RelatedItem> items = new ArrayList<>();
        for (Long id : ids) {
            if (byId.containsKey(id)) {
                items.add(byId.get(id));
            }
        }
        return items;
    }

    private RelatedItem provisionItem(Provision p) {
        return new RelatedItem("PROVISION", p.getId(),
                p.getLawName() + " " + p.getArticleNo(), TextUtil.excerpt(p.getContent(), 80));
    }

    private RelatedItem caseItem(CaseInfo c) {
        return new RelatedItem("CASE", c.getId(), c.getTitle(), TextUtil.excerpt(c.getSummary(), 80));
    }

    private RelatedItem viewpointItem(ScholarViewpoint v) {
        return new RelatedItem("VIEWPOINT", v.getId(),
                v.getTitle() + "（" + v.getScholarName() + "）", TextUtil.excerpt(v.getSummary(), 80));
    }

    private RelatedItem questionItem(Question q) {
        return new RelatedItem("QUESTION", q.getId(),
                TextUtil.excerpt(q.getStem(), 60), q.getSubject() + "·" + q.getType());
    }

    /**
     * 写入关联（自动补反向记录），已存在的关联直接忽略
     */
    @Transactional
    public void addRelation(String sourceType, Long sourceId, String targetType, Long targetId,
                            String relationType, String description) {
        validateType(sourceType);
        validateType(targetType);
        entityChecker.checkExists(sourceType, sourceId);
        entityChecker.checkExists(targetType, targetId);
        if (!RELATION_TYPES.contains(relationType)) {
            throw new BizException("不支持的关系类型：" + relationType);
        }
        insertQuiet(sourceType, sourceId, targetType, targetId, relationType, description);
        insertQuiet(targetType, targetId, sourceType, sourceId, relationType, description);
    }

    private void insertQuiet(String sType, Long sId, String tType, Long tId,
                             String relType, String desc) {
        try {
            EntityRelation r = new EntityRelation();
            r.setSourceType(sType);
            r.setSourceId(sId);
            r.setTargetType(tType);
            r.setTargetId(tId);
            r.setRelationType(relType);
            r.setDescription(desc);
            entityRelationMapper.insert(r);
        } catch (DuplicateKeyException ignored) {
            // 唯一约束冲突说明已存在，忽略
        }
    }

    private void validateType(String type) {
        if (!TYPES.contains(type)) {
            throw new BizException("不支持的实体类型：" + type);
        }
    }

    /**
     * 根据实体类型与 id 加载标题（AI 上下文引用用）
     */
    public String titleOf(String type, Long id) {
        return switch (type) {
            case "PROVISION" -> {
                Provision p = provisionMapper.selectById(id);
                yield p == null ? null : p.getLawName() + " " + p.getArticleNo();
            }
            case "CASE" -> {
                CaseInfo c = caseInfoMapper.selectById(id);
                yield c == null ? null : c.getTitle();
            }
            case "VIEWPOINT" -> {
                ScholarViewpoint v = viewpointMapper.selectById(id);
                yield v == null ? null : v.getTitle();
            }
            case "QUESTION" -> {
                Question q = questionMapper.selectById(id);
                yield q == null ? null : TextUtil.excerpt(q.getStem(), 60);
            }
            default -> null;
        };
    }

    @FunctionalInterface
    private interface IdTitleLoader {
        Map<Long, RelatedItem> load(Set<Long> ids);
    }
}
