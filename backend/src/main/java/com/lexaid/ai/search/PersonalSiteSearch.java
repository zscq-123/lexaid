package com.lexaid.ai.search;

import com.lexaid.common.BizException;
import com.lexaid.community.friends.FriendService;
import com.lexaid.community.posts.PostService;
import com.lexaid.relation.RelatedItem;
import com.lexaid.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Reads the same user-owned or audience-visible records made available by the site. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalSiteSearch {
    private static final List<String> SEARCHABLE = List.of("POST", "NOTE", "FAVORITE", "FOLDER", "EXAM", "WRONG", "FRIEND", "STUDY");
    private final NamedParameterJdbcTemplate db;
    private final StatsService stats;
    private final FriendService friends;

    public List<RelatedItem> search(long userId, List<String> terms, String scope, int limit) {
        if (limit <= 0 || userId <= 0 || (scope == null && terms.isEmpty())) return List.of();
        int take = Math.min(limit, 20);
        List<RelatedItem> found = new ArrayList<>();
        for (String type : scope == null ? SEARCHABLE : List.of(scope)) {
            found.addAll(read(userId, type, null, terms, take));
        }
        // Keep insertion order for equal relevance, including recency within each source.
        found.sort(Comparator.comparingInt((RelatedItem r) -> relevance(r, terms)).reversed());
        return found.stream().limit(take).toList();
    }

    public RelatedItem find(long userId, String type, long id, List<String> terms) {
        if (id <= 0 || userId <= 0 || type == null) return null;
        return read(userId, type, id, terms, 1).stream().findFirst().orElse(null);
    }

    private List<RelatedItem> read(long user, String type, Long id, List<String> terms, int limit) {
        String source = TABLES.get(type);
        if (source != null) return records(user, type, id, terms, limit, source, null);
        return switch (type) {
            case "EXAM" -> exams(user, id, terms, limit);
            case "POST", "MY_POST", "FRIEND_POST" -> posts(user, type, id, terms, limit);
            case "STATS" -> id == null || id == user ? List.of(learningStats(user, terms)) : List.of();
            case "MEMBER" -> members(user, id, terms, limit);
            default -> List.of();
        };
    }

    private static final String TARGET_JOINS = """
            LEFT JOIN legal_provision p ON x.target_type='PROVISION' AND p.id=x.target_id
            LEFT JOIN case_info c ON x.target_type='CASE' AND c.id=x.target_id
            LEFT JOIN scholar_viewpoint v ON x.target_type='VIEWPOINT' AND v.id=x.target_id
            LEFT JOIN question q ON x.target_type='QUESTION' AND q.id=x.target_id
            """;
    private static final String TARGET_TITLE = """
            COALESCE(CONCAT(p.law_name,' ',p.article_no), c.title,
                     IF(v.id IS NULL,NULL,CONCAT_WS(' · ',v.title,v.scholar_name)), q.stem)
            """.strip();
    private static final String NOTES = """
            SELECT x.id, CONCAT('学习笔记 · ', COALESCE(%s,'原内容已删除')) title,
                   CONCAT_WS(' · ',x.updated_at,x.content) summary,x.updated_at recent
            FROM note x %s WHERE x.user_id=:viewer
            """.formatted(TARGET_TITLE, TARGET_JOINS);
    private static final String FAVORITES = """
            SELECT x.id, COALESCE(%s,'原内容已删除') title,
                   CONCAT_WS(' · ','我的收藏',COALESCE(f.name,'未分区'),x.created_at,
                   COALESCE(p.content,c.summary,v.summary,q.stem,'')) summary,x.id recent
            FROM favorite x %s LEFT JOIN favorite_folder f ON f.id=x.folder_id AND f.user_id=x.user_id
            WHERE x.user_id=:viewer
            """.formatted(TARGET_TITLE, TARGET_JOINS);
    private static final String FOLDERS = """
            SELECT f.id, f.name title, CONCAT_WS(' · ', '我的收藏分区', CONCAT(COUNT(x.id), ' 条收藏'),
               GROUP_CONCAT(LEFT(%s,80) ORDER BY x.id DESC SEPARATOR '、')) summary, f.id recent
            FROM favorite_folder f LEFT JOIN favorite x ON x.user_id=f.user_id AND x.folder_id=f.id
            %s WHERE f.user_id=:viewer GROUP BY f.id,f.name
            """.formatted(TARGET_TITLE, TARGET_JOINS);
    private static final String ACTIVITY = """
            SELECT x.id,CONCAT('学习记录 · ',CASE x.action_type WHEN 'CHAT' THEN 'AI 问答'
              WHEN 'VIEW' THEN '浏览' WHEN 'ANSWER' THEN '答题' WHEN 'SEARCH' THEN '检索'
              WHEN 'NOTE' THEN '笔记' ELSE x.action_type END) title,
              CONCAT_WS(' · ',x.created_at,%s,x.detail, CONCAT(x.duration_sec,' 秒')) summary,x.id recent
            FROM study_record x %s WHERE x.user_id=:viewer
            """.formatted(TARGET_TITLE, TARGET_JOINS);
    private static final String WRONG = """
            SELECT q.id,CONCAT('错题 · ',LEFT(q.stem,100)) title,
               CONCAT_WS(' · ',q.category,q.stem,CONCAT('我的答案：',COALESCE(a.user_answer,'')),
               CONCAT('参考答案：',q.answer),CONCAT('得分：',a.grade_points),a.grading_feedback,q.analysis) summary,
               a.id recent
            FROM answer_record a JOIN question q ON q.id=a.question_id
            WHERE a.user_id=:viewer AND a.is_correct=0 AND NOT EXISTS (
               SELECT 1 FROM answer_record b WHERE b.user_id=a.user_id AND b.question_id=a.question_id AND b.id>a.id)
            """;
    private static final String CONTACTS = """
            SELECT u.id,COALESCE(NULLIF(u.nickname,''),u.username) title,
               CONCAT_WS(' · ',u.username,CASE WHEN f.state='ACCEPTED' THEN '我的好友'
                   WHEN f.requester_id=:viewer THEN '我发出的好友申请' ELSE '收到的好友申请' END,
                   IF(f.state='PENDING',f.message,NULL)) summary,f.requested_at recent
            FROM community_friend_pair f JOIN `user` u ON u.id=IF(f.user_low=:viewer,f.user_high,f.user_low)
            WHERE (f.user_low=:viewer OR f.user_high=:viewer) AND f.state IN ('PENDING','ACCEPTED')
            """;
    private static final Map<String, String> TABLES = Map.of(
            "NOTE", NOTES, "FAVORITE", FAVORITES, "FOLDER", FOLDERS,
            "STUDY", ACTIVITY, "WRONG", WRONG, "FRIEND", CONTACTS);
    private static final String EXAMS = """
            SELECT e.id,CONCAT(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.config,'$.category')),'null'),'综合'),'试卷 #',e.id) title,
               CONCAT_WS(' · ',IF(e.end_time IS NULL,'进行中','已交卷'),e.start_time,CONCAT(e.total,' 题'),
                   IF(e.end_time IS NULL,NULL,CONCAT('已得 ',e.score,' 分，当前已评分满分 ',
                     (SELECT COALESCE(SUM(IF(t.type='SHORT',10,1)),0) FROM answer_record ar
                      JOIN question t ON t.id=ar.question_id WHERE ar.user_id=e.user_id AND ar.session_id=e.id
                      AND (ar.grade_points IS NOT NULL OR ar.is_correct IS NOT NULL)),
                     ' 分，答对 ',e.correct,' 道'))) summary,
               e.id recent
            FROM exam_session e WHERE e.user_id=:viewer
            """;
    private static final String EXAM_ANSWER = """
            FROM answer_record a JOIN question q ON q.id=a.question_id
            WHERE a.user_id=:viewer AND a.session_id=%s
              AND EXISTS (SELECT 1 FROM exam_session graded WHERE graded.id=a.session_id
                          AND graded.user_id=a.user_id AND graded.end_time IS NOT NULL)
            """;
    private static final String ANSWER_TEXT = "CONCAT_WS(' ',q.stem,q.category,q.answer,q.analysis,a.user_answer,a.grading_feedback)";

    private List<RelatedItem> exams(long user, Long id, List<String> terms, int limit) {
        String matching = contains(terms, ANSWER_TEXT);
        String extra = "EXISTS (SELECT 1 " + EXAM_ANSWER.formatted("s.id") + " AND (" + matching + "))";
        return records(user, "EXAM", id, terms, limit, EXAMS, extra).stream().map(exam -> {
            if (exam.summary().startsWith("进行中")) return exam;
            String sql = """
                    SELECT q.stem, q.answer, q.analysis, a.user_answer, COALESCE(a.grade_points,a.is_correct) earned, a.grading_feedback
                    """ + EXAM_ANSWER.formatted(":id") + " ORDER BY (" + matching + ") DESC, a.id LIMIT 5";
            List<String> excerpts = db.query(sql, params(user, exam.id(), terms, 5), (rs, i) ->
                    snippet(rs.getString("stem"), terms, 140)
                    + "；我的答案：" + snippet(rs.getString("user_answer"), terms, 200)
                    + "；参考答案：" + snippet(rs.getString("answer"), terms, 230)
                    + "；解析：" + snippet(rs.getString("analysis"), terms, 180)
                    + "；" + (rs.getObject("earned") == null ? "尚无得分" : "得分 " + rs.getInt("earned"))
                    + " " + snippet(rs.getString("grading_feedback"), terms, 180));
            return appendDetails(exam, "", excerpts);
        }).toList();
    }

    private List<RelatedItem> posts(long user, String type, Long id, List<String> terms, int limit) {
        String audience = PostService.scopePredicate(switch (type) {
            case "MY_POST" -> "mine";
            case "FRIEND_POST" -> "friends";
            default -> "all";
        });
        String source = """
                SELECT p.id, CONCAT(COALESCE(NULLIF(u.nickname,''),u.username),'的帖子 · ',LEFT(p.caption,72)) title,
                    CONCAT_WS(' · ', p.created_at, u.username, IF(p.visibility='FRIENDS','好友可见','公开'),
                        CONCAT((SELECT COUNT(*) FROM community_post_image i WHERE i.post_id=p.id),' 张图片'),
                        p.caption) summary, p.id recent
                FROM community_post p JOIN `user` u ON u.id=p.author_id WHERE
                """ + audience;
        String commentsFrom = " FROM community_post_comment c JOIN `user` u ON u.id=c.author_id ";
        String matching = contains(terms, "CONCAT_WS(' ',u.nickname,u.username,c.content)");
        String extra = "EXISTS (SELECT 1 " + commentsFrom + " WHERE c.post_id=s.id AND (" + matching + "))";
        return records(user, "POST", id, terms, limit, source, extra).stream().map(post -> {
            String sql = "SELECT COALESCE(NULLIF(u.nickname,''),u.username) author, c.content " + commentsFrom
                    + " JOIN community_post p ON p.id=c.post_id WHERE p.id=:id AND " + audience
                    + " ORDER BY (" + matching + ") DESC, c.id DESC LIMIT 4";
            List<String> excerpts = db.query(sql, params(user, post.id(), terms, 4), (rs, i) ->
                    rs.getString("author") + "：" + snippet(rs.getString("content"), terms, 400));
            return appendDetails(post, "评论：\n", excerpts);
        }).toList();
    }

    private static RelatedItem appendDetails(RelatedItem item, String label, List<String> excerpts) {
        if (excerpts.isEmpty()) return item;
        return new RelatedItem(item.type(), item.id(), item.title(),
                item.summary() + "\n" + label + String.join("\n", excerpts));
    }

    private RelatedItem learningStats(long user, List<String> terms) {
        var overview = stats.overview(user);
        String detail = "累计作答 %d 题，满分 %d 题，正确率 %.2f%%，学习 %d 分钟，连续学习 %d 天。"
                .formatted(overview.totalAnswer(), overview.totalCorrect(), overview.accuracy(),
                        overview.totalStudyMinutes(), overview.streakDays());
        detail += "\n最近 30 天有活动的日期（作答/正确/学习分钟）：\n" + stats.daily(user, 30).stream()
                .filter(day -> day.answerCount() > 0 || day.studyMinutes() > 0)
                .map(day -> day.date() + " " + day.answerCount() + "/" + day.correctCount() + "/" + day.studyMinutes())
                .collect(Collectors.joining("，"));
        detail += "\n类别正确率：" + stats.categoryAccuracy(user).stream()
                .map(cat -> cat.category() + " " + cat.correct() + "/" + cat.total() + "（" + cat.accuracy() + "%）")
                .collect(Collectors.joining("，"));
        return new RelatedItem("STATS", user, "我的学习统计", snippet(detail, terms, 2600));
    }

    private List<RelatedItem> members(long user, Long id, List<String> terms, int limit) {
        if (id != null) {
            if (id == user) return List.of();
            try {
                return List.of(member(friends.contact(user, id)));
            } catch (BizException e) {
                if (e.getCode() == 404) return List.of();
                throw e;
            }
        }
        Map<Long, RelatedItem> results = new LinkedHashMap<>();
        for (String term : terms) {
            for (var person : friends.search(user, term)) results.putIfAbsent(person.id(), member(person));
        }
        return results.values().stream().limit(limit).toList();
    }

    private static RelatedItem member(FriendService.Person person) {
        String nickname = person.nickname();
        String title = nickname == null || nickname.isBlank() ? person.username() : nickname;
        return new RelatedItem("MEMBER", person.id(), title, "站内用户 · 用户名：" + person.username());
    }

    private List<RelatedItem> records(long viewer, String type, Long id, List<String> terms, int limit,
                                      String source, String extraMatch) {
        String matches = contains(terms, "CONCAT_WS(' ',s.title,s.summary)");
        String where = id != null ? "s.id=:id" : "(" + matches + (extraMatch == null ? "" : " OR " + extraMatch) + ")";
        String sql = "SELECT s.id, s.title, s.summary FROM (" + source + ") s WHERE " + where
                + " ORDER BY " + titleScore(terms) + " DESC, s.recent DESC, s.id DESC LIMIT :limit";
        return db.query(sql, params(viewer, id, terms, limit), (rs, i) -> new RelatedItem(type, rs.getLong("id"),
                snippet(rs.getString("title"), List.of(), 150), snippet(rs.getString("summary"), terms, id == null ? 900 : 3000)));
    }

    private static MapSqlParameterSource params(long viewer, Long id, List<String> terms, int limit) {
        var args = new MapSqlParameterSource().addValue("viewer", viewer).addValue("limit", limit).addValue("id", id);
        for (int i = 0; i < terms.size(); i++) args.addValue("t" + i, terms.get(i));
        return args;
    }

    private static String contains(List<String> terms, String field) {
        if (terms.isEmpty()) return "1=1";
        return IntStream.range(0, terms.size()).mapToObj(i -> "LOCATE(:t" + i + "," + field + ")>0")
                .collect(Collectors.joining(" OR "));
    }

    private static String titleScore(List<String> terms) {
        if (terms.isEmpty()) return "(0+0)";
        return IntStream.range(0, terms.size()).mapToObj(i -> "(LOCATE(:t" + i + ",s.title)>0)")
                .collect(Collectors.joining("+"));
    }

    private static int relevance(RelatedItem item, List<String> terms) {
        int result = 0;
        String title = item.title().toLowerCase(Locale.ROOT);
        String body = item.summary().toLowerCase(Locale.ROOT);
        for (String word : terms) {
            String term = word.toLowerCase(Locale.ROOT);
            if (title.contains(term)) result += 3;
            if (body.contains(term)) result++;
        }
        return result;
    }

    private static String snippet(String text, List<String> terms, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        int first = terms.stream().map(term -> lower.indexOf(term.toLowerCase(Locale.ROOT)))
                .filter(position -> position >= 0).min(Comparator.naturalOrder()).orElse(0);
        int start = Math.min(text.length() - max, Math.max(0, first - max / 4));
        return (start > 0 ? "…" : "") + text.substring(start, start + max) + (start + max < text.length() ? "…" : "");
    }
}
