package com.lexaid.ai.search;

import com.lexaid.relation.RelatedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Exact inventory figures for questions about the size and coverage of the site. */
@Component
@RequiredArgsConstructor
class SiteCatalog {
    private final JdbcTemplate db;

    List<RelatedItem> search(long viewer, String question, List<String> terms) {
        String text = question + " " + String.join(" ", terms);
        List<RelatedItem> items = new ArrayList<>();
        if (text.matches("(?s).*(?:法条|法典).*")) items.add(find(viewer, 2));
        if (text.matches("(?s).*(?:案例|判例).*")) items.add(find(viewer, 3));
        if (text.matches("(?s).*(?:论文|观点|文献).*")) items.add(find(viewer, 4));
        if (text.matches("(?s).*(?:题库|题目|道题|试题|习题).*")) items.add(find(viewer, 5));
        if (text.matches("(?s).*(?:我的|我有|我提交|我发|收藏|笔记|好友|错题|答卷|试卷).*")) items.add(find(viewer, 6));
        return items.isEmpty() ? List.of(find(viewer, 1)) : items;
    }

    RelatedItem find(long viewer, long id) {
        if (id == 1) {
            String body = db.queryForObject("""
                    SELECT CONCAT('法条 ',(SELECT COUNT(*) FROM legal_provision),' 条，案例 ',
                      (SELECT COUNT(*) FROM case_info),' 条，学者观点及论文 ',
                      (SELECT COUNT(*) FROM scholar_viewpoint),' 条，题库 ',(SELECT COUNT(*) FROM question),' 道。')
                    """, String.class);
            return item(id, "站内资料概况", body + " 可检索公开知识库、可见的交流天地帖子和评论，以及本人笔记、收藏、考试、好友和学习记录。");
        }
        if (id == 2) return grouped(id, "法条库", "SELECT jurisdiction, COUNT(*) FROM legal_provision GROUP BY jurisdiction ORDER BY jurisdiction");
        if (id == 3) return grouped(id, "案例库", "SELECT jurisdiction, COUNT(*) FROM case_info GROUP BY jurisdiction ORDER BY jurisdiction");
        if (id == 4) return item(id, "学者观点与论文馆藏", db.queryForObject("""
                SELECT CONCAT('学者观点及论文共 ',COUNT(*),' 条；其中论文 ',
                    COUNT(CASE WHEN summary_kind IN ('author_abstract','metadata_only') THEN 1 END),' 篇，收录正文的论文 ',
                    COUNT(CASE WHEN summary_kind IN ('author_abstract','metadata_only') AND LENGTH(content)>0 THEN 1 END),' 篇。')
                FROM scholar_viewpoint
                """, String.class));
        if (id == 5) return grouped(id, "题库与题目类别", "SELECT category, COUNT(*) FROM question GROUP BY category ORDER BY category");
        if (id == 6) return item(id, "我的学习与交流概况", db.queryForObject("""
                SELECT CONCAT('笔记 ',(SELECT COUNT(*) FROM note WHERE user_id=?),' 条，收藏 ',
                    (SELECT COUNT(*) FROM favorite WHERE user_id=?),' 条，收藏分区 ',
                    (SELECT COUNT(*) FROM favorite_folder WHERE user_id=?),' 个，已交卷 ',
                    (SELECT COUNT(*) FROM exam_session WHERE user_id=? AND end_time IS NOT NULL),' 份，尚未交卷 ',
                    (SELECT COUNT(*) FROM exam_session WHERE user_id=? AND end_time IS NULL),' 份，错题 ',
                    (SELECT COUNT(*) FROM answer_record a WHERE a.user_id=? AND a.is_correct=0 AND NOT EXISTS
                        (SELECT 1 FROM answer_record n WHERE n.user_id=a.user_id AND n.question_id=a.question_id AND n.id>a.id)),
                    ' 道，自己发表的帖子 ',(SELECT COUNT(*) FROM community_post WHERE author_id=?),' 条，已通过的好友 ',
                    (SELECT COUNT(*) FROM community_friend_pair WHERE (user_low=? OR user_high=?) AND state='ACCEPTED'),' 人。')
                """, String.class, viewer, viewer, viewer, viewer, viewer, viewer, viewer, viewer, viewer));
        return null;
    }

    private RelatedItem grouped(long id, String title, String sql) {
        return db.query(sql, rs -> {
            long total = 0;
            StringJoiner groups = new StringJoiner("，");
            while (rs.next()) {
                long count = rs.getLong(2);
                total += count;
                groups.add((rs.getString(1) == null ? "未分类" : rs.getString(1)) + " " + count);
            }
            return item(id, title, "总数 " + total + "。分类计数：" + groups + "。");
        });
    }

    private RelatedItem item(long id, String title, String text) {
        return new RelatedItem("CATALOG", id, title, text);
    }
}
