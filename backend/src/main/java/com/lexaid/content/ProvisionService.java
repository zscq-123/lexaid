package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.content.dto.ProvisionCategoryVO;
import com.lexaid.content.dto.ProvisionVO;
import com.lexaid.note.FavoriteService;
import com.lexaid.relation.RelatedGroups;
import com.lexaid.relation.RelationService;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 法条服务：检索、详情、关联
 */
@Service
@RequiredArgsConstructor
public class ProvisionService {

    private final ProvisionMapper provisionMapper;
    private final TagService tagService;
    private final RelationService relationService;
    private final FavoriteService favoriteService;
    private final StudyRecordService studyRecordService;
    private final ProvisionOutline outline;

    public PageResult<ProvisionVO> page(String keyword, String lawName, String category,
                                        String jurisdiction, String languageCode, String tag,
                                        long page, long size, String code, String path) {
        LambdaQueryWrapper<Provision> qw = new LambdaQueryWrapper<>();
        outline.filter(qw, jurisdiction, code, path);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Provision::getLawName, keyword)
                    .or().like(Provision::getChapter, keyword)
                    .or().like(Provision::getArticleNo, keyword)
                    .or().like(Provision::getContent, keyword));
        }
        if (StringUtils.hasText(lawName)) {
            qw.like(Provision::getLawName, lawName);
        }
        if (StringUtils.hasText(category)) {
            qw.eq(Provision::getCategory, category);
        }
        if (StringUtils.hasText(jurisdiction)) {
            qw.eq(Provision::getJurisdiction, jurisdiction);
        }
        if (StringUtils.hasText(languageCode)) {
            qw.eq(Provision::getLanguageCode, languageCode);
        }
        if (StringUtils.hasText(tag)) {
            List<Long> ids = tagService.idsWithTag(EntityChecker.PROVISION, tag);
            if (ids.isEmpty()) {
                return new PageResult<>(List.of(), 0);
            }
            qw.in(Provision::getId, ids);
        }
        qw.orderByAsc(Provision::getId);
        Page<Provision> p = provisionMapper.selectPage(new Page<>(page, size), qw);
        outline.fillLegacyPaths(p.getRecords());
        if (StringUtils.hasText(keyword)) {
            studyRecordService.record(UserContext.userId(), "SEARCH",
                    EntityChecker.PROVISION, null, "关键词：" + keyword);
        }
        Map<Long, List<String>> tags = tagService.listTags(EntityChecker.PROVISION,
                p.getRecords().stream().map(Provision::getId).toList());
        List<ProvisionVO> vos = p.getRecords().stream()
                .map(x -> toVO(x, tags.getOrDefault(x.getId(), List.of()), false))
                .toList();
        return new PageResult<>(vos, p.getTotal());
    }

    public List<ProvisionCategoryVO> categories() {
        return provisionMapper.selectCategories();
    }

    public ProvisionVO detail(Long id) {
        Provision p = provisionMapper.selectById(id);
        if (p == null) {
            throw new BizException("法条不存在");
        }
        outline.fillLegacyPaths(List.of(p));
        studyRecordService.record(UserContext.userId(), "VIEW",
                EntityChecker.PROVISION, id, null);
        return toVO(p, tagService.tagsOf(EntityChecker.PROVISION, id), true);
    }

    public RelatedGroups related(Long id) {
        return relationService.relatedGroups(EntityChecker.PROVISION, id);
    }

    private ProvisionVO toVO(Provision p, List<String> tags, boolean full) {
        return new ProvisionVO(
                p.getId(), p.getLawName(), p.getCategory(), p.getChapter(), p.getArticleNo(),
                full ? p.getContent() : TextUtil.excerpt(p.getContent(), 120),
                p.getEffectiveDate(), p.getIssuingAuthority(), p.getJurisdiction(),
                p.getLanguageCode(), p.getStatus(), p.getSourceDataset(), p.getSourceUrl(),
                p.getLicenseName(), p.getPromulgationDate(), tags,
                favoriteService.isFavorite(UserContext.userId(), EntityChecker.PROVISION, p.getId()));
    }
}
