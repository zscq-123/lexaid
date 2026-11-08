package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.Result;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.content.dto.SearchItem;
import com.lexaid.exam.Question;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局聚合检索
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final com.lexaid.exam.QuestionMapper questionMapper;
    private final StudyRecordService studyRecordService;

    @GetMapping
    public Result<Map<String, List<SearchItem>>> search(@RequestParam String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        Map<String, List<SearchItem>> result = new LinkedHashMap<>();
        if (StringUtils.hasText(kw)) {
            result.put("provisions", searchProvisions(kw));
            result.put("cases", searchCases(kw));
            result.put("viewpoints", searchViewpoints(kw));
            result.put("questions", searchQuestions(kw));
            studyRecordService.record(UserContext.userId(), "SEARCH",
                    null, null, "全局搜索：" + kw);
        }
        return Result.ok(result);
    }

    private List<SearchItem> searchProvisions(String kw) {
        return provisionMapper.selectList(new LambdaQueryWrapper<Provision>()
                        .select(Provision::getId, Provision::getLawName, Provision::getArticleNo, Provision::getContent)
                        .like(Provision::getContent, kw)
                        .or().like(Provision::getArticleNo, kw)
                        .or().like(Provision::getLawName, kw)
                        .last("LIMIT 10"))
                .stream().map(p -> new SearchItem("PROVISION", p.getId(),
                        p.getLawName() + " " + p.getArticleNo(),
                        TextUtil.excerpt(p.getContent(), 80))).toList();
    }

    private List<SearchItem> searchCases(String kw) {
        return caseInfoMapper.selectList(new LambdaQueryWrapper<CaseInfo>()
                        .select(CaseInfo::getId, CaseInfo::getTitle, CaseInfo::getSummary)
                        .like(CaseInfo::getTitle, kw)
                        .or().like(CaseInfo::getSummary, kw)
                        .last("LIMIT 10"))
                .stream().map(c -> new SearchItem("CASE", c.getId(),
                        c.getTitle(), TextUtil.excerpt(c.getSummary(), 80))).toList();
    }

    private List<SearchItem> searchViewpoints(String kw) {
        return viewpointMapper.selectList(new LambdaQueryWrapper<ScholarViewpoint>()
                        .select(ScholarViewpoint::getId, ScholarViewpoint::getTitle, ScholarViewpoint::getSummary)
                        .like(ScholarViewpoint::getTitle, kw)
                        .or().like(ScholarViewpoint::getScholarName, kw)
                        .or().like(ScholarViewpoint::getDoi, kw)
                        .or().like(ScholarViewpoint::getSummary, kw)
                        .last("LIMIT 10"))
                .stream().map(v -> new SearchItem("VIEWPOINT", v.getId(),
                        v.getTitle(), TextUtil.excerpt(v.getSummary(), 80))).toList();
    }

    private List<SearchItem> searchQuestions(String kw) {
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getStem, Question::getCategory, Question::getType)
                        .like(Question::getStem, kw)
                        .last("LIMIT 10"))
                .stream().map(q -> new SearchItem("QUESTION", q.getId(),
                        TextUtil.excerpt(q.getStem(), 60), q.getCategory() + "·" + q.getType())).toList();
    }
}
