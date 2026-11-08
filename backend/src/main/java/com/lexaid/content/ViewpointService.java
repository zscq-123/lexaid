package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.content.dto.ViewpointVO;
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
 * 学者观点服务：检索、详情、关联
 */
@Service
@RequiredArgsConstructor
public class ViewpointService {

    private final ScholarViewpointMapper viewpointMapper;
    private final TagService tagService;
    private final RelationService relationService;
    private final FavoriteService favoriteService;
    private final StudyRecordService studyRecordService;

    public PageResult<ViewpointVO> page(String scholar, String keyword, String tag,
                                        String languageCode, boolean papersOnly, long page, long size) {
        // More than 80 non-whitespace code points guarantee the excerpt ends within
        // this prefix, even after Java trims controls and collapses whitespace.
        LambdaQueryWrapper<ScholarViewpoint> qw = new QueryWrapper<ScholarViewpoint>().select(
                "id", "scholar_name", "title", "summary",
                "CASE WHEN CHAR_LENGTH(REGEXP_REPLACE(LEFT(content,512),'[[:space:][:cntrl:]]','')) > 80 "
                        + "THEN LEFT(content,512) ELSE content END AS content",
                "source", "publish_year", "language_code", "source_dataset", "source_url",
                "doi", "license_name", "summary_kind"
        ).lambda();
        if (StringUtils.hasText(scholar)) {
            qw.like(ScholarViewpoint::getScholarName, scholar.trim());
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(ScholarViewpoint::getScholarName, keyword)
                    .or().like(ScholarViewpoint::getTitle, keyword)
                    .or().like(ScholarViewpoint::getSummary, keyword)
                    .or().like(ScholarViewpoint::getContent, keyword)
                    .or().like(ScholarViewpoint::getDoi, keyword));
        }
        if (StringUtils.hasText(languageCode)) {
            qw.and(w -> w.eq(ScholarViewpoint::getLanguageCode, languageCode)
                    .or().likeRight(ScholarViewpoint::getLanguageCode, languageCode + "-"));
        }
        if (papersOnly) {
            qw.in(ScholarViewpoint::getSummaryKind, "author_abstract", "metadata_only");
        }
        if (StringUtils.hasText(tag)) {
            List<Long> ids = tagService.idsWithTag(EntityChecker.VIEWPOINT, tag);
            if (ids.isEmpty()) {
                return new PageResult<>(List.of(), 0);
            }
            qw.in(ScholarViewpoint::getId, ids);
        }
        qw.orderByAsc(ScholarViewpoint::getId);
        Page<ScholarViewpoint> p = viewpointMapper.selectPage(new Page<>(page, size), qw);
        if (StringUtils.hasText(keyword)) {
            studyRecordService.record(UserContext.userId(), "SEARCH",
                    EntityChecker.VIEWPOINT, null, "关键词：" + keyword);
        }
        Map<Long, List<String>> tags = tagService.listTags(EntityChecker.VIEWPOINT,
                p.getRecords().stream().map(ScholarViewpoint::getId).toList());
        List<ViewpointVO> vos = p.getRecords().stream()
                .map(x -> toVO(x, tags.getOrDefault(x.getId(), List.of()), false))
                .toList();
        return new PageResult<>(vos, p.getTotal());
    }

    public ViewpointVO detail(Long id) {
        ScholarViewpoint v = viewpointMapper.selectById(id);
        if (v == null) {
            throw new BizException("观点不存在");
        }
        studyRecordService.record(UserContext.userId(), "VIEW",
                EntityChecker.VIEWPOINT, id, null);
        return toVO(v, tagService.tagsOf(EntityChecker.VIEWPOINT, id), true);
    }

    public RelatedGroups related(Long id) {
        return relationService.relatedGroups(EntityChecker.VIEWPOINT, id);
    }

    private ViewpointVO toVO(ScholarViewpoint v, List<String> tags, boolean full) {
        return new ViewpointVO(
                v.getId(), v.getScholarName(), v.getTitle(),
                full ? v.getSummary() : TextUtil.excerpt(v.getSummary(), 120),
                full ? v.getContent() : TextUtil.excerpt(v.getContent(), 80),
                v.getSource(), v.getPublishYear(), v.getLanguageCode(), v.getSourceDataset(),
                v.getSourceUrl(), v.getDoi(), v.getLicenseName(), v.getSummaryKind(), tags,
                favoriteService.isFavorite(UserContext.userId(), EntityChecker.VIEWPOINT, v.getId()));
    }
}
