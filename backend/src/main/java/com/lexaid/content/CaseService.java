package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.content.dto.CaseVO;
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
 * 案例服务：检索、详情、关联
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseInfoMapper caseInfoMapper;
    private final CaseCourtDirectory courtDirectory;
    private final TagService tagService;
    private final RelationService relationService;
    private final FavoriteService favoriteService;
    private final StudyRecordService studyRecordService;

    public PageResult<CaseVO> page(String keyword, String caseType, String court, String courtNode,
                                   String jurisdiction, String languageCode,
                                   Integer year, long page, long size) {
        LambdaQueryWrapper<CaseInfo> qw = new LambdaQueryWrapper<CaseInfo>()
                .select(CaseInfo.class, field -> !"content".equals(field.getProperty())
                        && !"metadata".equals(field.getProperty()));
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(CaseInfo::getTitle, keyword)
                    .or().like(CaseInfo::getSummary, keyword)
                    .or().like(CaseInfo::getHolding, keyword)
                    .or().like(CaseInfo::getContent, keyword));
        }
        if (StringUtils.hasText(caseType)) {
            qw.eq(CaseInfo::getCaseType, caseType);
        }
        if (StringUtils.hasText(court)) {
            qw.like(CaseInfo::getCourt, court);
        }
        if (StringUtils.hasText(courtNode) && !courtDirectory.applyFilter(qw, courtNode)) {
            return new PageResult<>(List.of(), 0);
        }
        if (StringUtils.hasText(jurisdiction)) {
            qw.eq(CaseInfo::getJurisdiction, jurisdiction);
        }
        if (StringUtils.hasText(languageCode)) {
            qw.eq(CaseInfo::getLanguageCode, languageCode);
        }
        if (year != null) {
            qw.apply("YEAR(judge_date) = {0}", year);
        }
        qw.orderByDesc(CaseInfo::getJudgeDate).orderByAsc(CaseInfo::getId);
        Page<CaseInfo> p = caseInfoMapper.selectPage(new Page<>(page, size), qw);
        if (StringUtils.hasText(keyword)) {
            studyRecordService.record(UserContext.userId(), "SEARCH",
                    EntityChecker.CASE, null, "关键词：" + keyword);
        }
        Map<Long, List<String>> tags = tagService.listTags(EntityChecker.CASE,
                p.getRecords().stream().map(CaseInfo::getId).toList());
        List<CaseVO> vos = p.getRecords().stream()
                .map(x -> toVO(x, tags.getOrDefault(x.getId(), List.of()), false))
                .toList();
        return new PageResult<>(vos, p.getTotal());
    }

    public CaseVO detail(Long id) {
        CaseInfo c = caseInfoMapper.selectById(id);
        if (c == null) {
            throw new BizException("案例不存在");
        }
        studyRecordService.record(UserContext.userId(), "VIEW",
                EntityChecker.CASE, id, null);
        return toVO(c, tagService.tagsOf(EntityChecker.CASE, id), true);
    }

    public RelatedGroups related(Long id) {
        return relationService.relatedGroups(EntityChecker.CASE, id);
    }

    private CaseVO toVO(CaseInfo c, List<String> tags, boolean full) {
        return new CaseVO(
                c.getId(), c.getTitle(), c.getCaseNo(), CaseCourtDirectory.classify(c).name(), c.getJudgeDate(),
                c.getCaseType(),
                full ? c.getSummary() : TextUtil.excerpt(c.getSummary(), 120),
                full ? c.getHolding() : TextUtil.excerpt(c.getHolding(), 80),
                c.getProcedure(), full ? c.getContent() : null,
                c.getJurisdiction(), c.getLanguageCode(), c.getStatus(),
                c.getSourceDataset(), c.getSourceUrl(), c.getLicenseName(), tags,
                favoriteService.isFavorite(UserContext.userId(), EntityChecker.CASE, c.getId()));
    }
}
