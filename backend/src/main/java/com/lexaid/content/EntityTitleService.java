package com.lexaid.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexaid.common.TextUtil;
import com.lexaid.exam.Question;
import com.lexaid.exam.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体标题解析（笔记、统计、收藏展示共用）
 */
@Component
@RequiredArgsConstructor
public class EntityTitleService {

    private final ProvisionMapper provisionMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ScholarViewpointMapper viewpointMapper;
    private final QuestionMapper questionMapper;
    private final JdbcTemplate jdbc;

    public String title(String type, Long id) {
        Map<Long, String> m = batch(type, List.of(id));
        return m.getOrDefault(id, "内容已删除");
    }

    public Map<Long, String> batch(String type, Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return m;
        }
        switch (type) {
            case EntityChecker.PROVISION -> {
                for (Provision p : provisionMapper.selectList(new LambdaQueryWrapper<Provision>()
                        .select(Provision::getId, Provision::getLawName, Provision::getArticleNo).in(Provision::getId, ids))) {
                    m.put(p.getId(), p.getLawName() + " " + p.getArticleNo());
                }
            }
            case EntityChecker.CASE -> {
                for (CaseInfo c : caseInfoMapper.selectList(new LambdaQueryWrapper<CaseInfo>()
                        .select(CaseInfo::getId, CaseInfo::getTitle).in(CaseInfo::getId, ids))) {
                    m.put(c.getId(), c.getTitle());
                }
            }
            case EntityChecker.VIEWPOINT -> {
                for (ScholarViewpoint v : viewpointMapper.selectList(new LambdaQueryWrapper<ScholarViewpoint>()
                        .select(ScholarViewpoint::getId, ScholarViewpoint::getTitle, ScholarViewpoint::getScholarName)
                        .in(ScholarViewpoint::getId, ids))) {
                    m.put(v.getId(), v.getTitle() + "（" + v.getScholarName() + "）");
                }
            }
            case EntityChecker.QUESTION -> {
                for (Question q : questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getStem).in(Question::getId, ids))) {
                    m.put(q.getId(), TextUtil.excerpt(q.getStem(), 60));
                }
            }
            case "MESSAGE" -> {
                String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
                jdbc.query("SELECT id, kind, content, file_name, recalled_at FROM direct_message WHERE id IN ("
                                + placeholders + ")",
                        rs -> {
                            String title;
                            if (rs.getTimestamp("recalled_at") != null) {
                                title = "消息已撤回";
                            } else {
                                String kind = rs.getString("kind");
                                String content = rs.getString("content");
                                title = switch (kind) {
                                    case "IMAGE" -> content == null || content.isBlank() ? "[图片]" : content;
                                    case "FILE" -> content == null || content.isBlank()
                                            ? "[文件] " + rs.getString("file_name") : content;
                                    case "VOICE" -> "[语音]";
                                    default -> content == null ? "" : content;
                                };
                                title = TextUtil.excerpt(title, 60);
                            }
                            m.put(rs.getLong("id"), title);
                        }, ids.toArray());
            }
            default -> {
            }
        }
        return m;
    }
}
