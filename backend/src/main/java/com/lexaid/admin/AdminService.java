package com.lexaid.admin;

import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {

    public record TrendPoint(LocalDate date, long registrations, long activeUsers) {}

    public record ActivityCount(String actionType, long count) {}

    public record Overview(long totalUsers, long newUsersToday, long newUsers7Days,
                           long activeUsers7Days, long totalStudyMinutes, long totalAnswers,
                           long totalNotes, long totalPosts, List<TrendPoint> trend,
                           List<ActivityCount> activityDistribution) {}

    public record UserSummary(long id, String username, String nickname, String role,
                              String avatarVersion, LocalDateTime createdAt, LocalDateTime lastActiveAt,
                              long studyMinutes, long answerCount, double accuracy, long noteCount,
                              long favoriteCount, long postCount) {}

    public record RecentActivity(String category, String actionType, String title,
                                 String targetType, Long targetId, LocalDateTime occurredAt) {}

    public record UserDetail(UserSummary profile, Map<String, Long> activityCounts,
                             List<RecentActivity> recentActivities) {}

    private static final Set<String> ROLES = Set.of("", "USER", "ADMIN");
    private static final Set<String> ACTIVITY_FILTERS = Set.of("all", "active7", "new30", "inactive30");
    private static final Map<String, String> SORTS = Map.of(
            "created", "created_at",
            "lastActive", "last_active_at",
            "studyTime", "study_minutes",
            "answers", "answer_count"
    );

    private static final String USER_DATA = """
            SELECT u.id, u.username, u.nickname, u.role, av.version AS avatar_version, u.created_at,
                   NULLIF(GREATEST(
                       COALESCE(sr.last_active_at, '1970-01-01 00:00:00'),
                       COALESCE(ar.last_active_at, '1970-01-01 00:00:00'),
                       COALESCE(cm.last_active_at, '1970-01-01 00:00:00'),
                       COALESCE(cp.last_active_at, '1970-01-01 00:00:00')
                   ), '1970-01-01 00:00:00') AS last_active_at,
                   COALESCE(sr.study_minutes, 0) AS study_minutes,
                   COALESCE(ar.answer_count, 0) AS answer_count,
                   CASE WHEN COALESCE(ar.graded_count, 0) = 0 THEN 0
                        ELSE ROUND(ar.correct_count * 100.0 / ar.graded_count, 1) END AS accuracy,
                   COALESCE(n.note_count, 0) AS note_count,
                   COALESCE(f.favorite_count, 0) AS favorite_count,
                   COALESCE(cp.post_count, 0) AS post_count
            FROM `user` u
            LEFT JOIN user_avatar av ON av.user_id = u.id
            LEFT JOIN (
                SELECT user_id, FLOOR(COALESCE(SUM(duration_sec), 0) / 60) AS study_minutes,
                       MAX(created_at) AS last_active_at
                FROM study_record GROUP BY user_id
            ) sr ON sr.user_id = u.id
            LEFT JOIN (
                SELECT user_id, COUNT(*) AS answer_count,
                       SUM(CASE WHEN is_correct IS NOT NULL THEN 1 ELSE 0 END) AS graded_count,
                       SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct_count,
                       MAX(answered_at) AS last_active_at
                FROM answer_record GROUP BY user_id
            ) ar ON ar.user_id = u.id
            LEFT JOIN (SELECT user_id, COUNT(*) AS note_count FROM note GROUP BY user_id) n ON n.user_id = u.id
            LEFT JOIN (SELECT user_id, COUNT(*) AS favorite_count FROM favorite GROUP BY user_id) f ON f.user_id = u.id
            LEFT JOIN (SELECT user_id, MAX(created_at) AS last_active_at FROM chat_message GROUP BY user_id) cm
                   ON cm.user_id = u.id
            LEFT JOIN (
                SELECT author_id AS user_id, COUNT(*) AS post_count, MAX(created_at) AS last_active_at
                FROM community_post GROUP BY author_id
            ) cp ON cp.user_id = u.id
            """;

    private static final RowMapper<UserSummary> USER_MAPPER = (rs, rowNum) -> new UserSummary(
            rs.getLong("id"), rs.getString("username"), rs.getString("nickname"), rs.getString("role"),
            rs.getString("avatar_version"), time(rs.getTimestamp("created_at")),
            time(rs.getTimestamp("last_active_at")), rs.getLong("study_minutes"),
            rs.getLong("answer_count"), rs.getDouble("accuracy"), rs.getLong("note_count"),
            rs.getLong("favorite_count"), rs.getLong("post_count"));

    private final NamedParameterJdbcTemplate db;

    @Transactional(readOnly = true)
    public Overview overview(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        Map<String, Object> params = Map.of("from", from.atStartOfDay());
        Map<String, Object> totals = db.queryForMap("""
                SELECT (SELECT COUNT(*) FROM `user`) AS total_users,
                       (SELECT COUNT(*) FROM `user` WHERE created_at >= CURRENT_DATE) AS new_users_today,
                       (SELECT COUNT(*) FROM `user` WHERE created_at >= CURRENT_DATE - INTERVAL 6 DAY) AS new_users_7_days,
                       (SELECT COUNT(DISTINCT user_id) FROM (
                           SELECT user_id FROM study_record WHERE created_at >= CURRENT_DATE - INTERVAL 6 DAY
                           UNION SELECT user_id FROM answer_record WHERE answered_at >= CURRENT_DATE - INTERVAL 6 DAY
                           UNION SELECT user_id FROM chat_message WHERE created_at >= CURRENT_DATE - INTERVAL 6 DAY
                           UNION SELECT author_id AS user_id FROM community_post WHERE created_at >= CURRENT_DATE - INTERVAL 6 DAY
                       ) recent_users) AS active_users_7_days,
                       (SELECT FLOOR(COALESCE(SUM(duration_sec), 0) / 60) FROM study_record) AS total_study_minutes,
                       (SELECT COUNT(*) FROM answer_record) AS total_answers,
                       (SELECT COUNT(*) FROM note) AS total_notes,
                       (SELECT COUNT(*) FROM community_post) AS total_posts
                """, Map.of());

        Map<LocalDate, long[]> trend = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) trend.put(from.plusDays(i), new long[2]);
        db.query("""
                SELECT DATE(created_at) AS day, COUNT(*) AS registrations
                FROM `user` WHERE created_at >= :from GROUP BY DATE(created_at)
                """, params, (RowCallbackHandler) rs -> {
            trend.get(rs.getDate("day").toLocalDate())[0] = rs.getLong("registrations");
        });
        db.query("""
                SELECT DATE(event_time) AS day, COUNT(DISTINCT user_id) AS active_users
                FROM (
                    SELECT user_id, created_at AS event_time FROM study_record WHERE created_at >= :from
                    UNION ALL SELECT user_id, answered_at FROM answer_record WHERE answered_at >= :from
                    UNION ALL SELECT user_id, created_at FROM chat_message WHERE created_at >= :from
                    UNION ALL SELECT author_id, created_at FROM community_post WHERE created_at >= :from
                ) activity GROUP BY DATE(event_time)
                """, params, (RowCallbackHandler) rs -> {
            trend.get(rs.getDate("day").toLocalDate())[1] = rs.getLong("active_users");
        });
        List<TrendPoint> trendPoints = trend.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        List<ActivityCount> distribution = db.query("""
                SELECT action_type, COUNT(*) AS action_count FROM study_record
                GROUP BY action_type ORDER BY action_count DESC
                """, Map.of(), (rs, n) -> new ActivityCount(rs.getString("action_type"), rs.getLong("action_count")));
        return new Overview(number(totals, "total_users"), number(totals, "new_users_today"),
                number(totals, "new_users_7_days"), number(totals, "active_users_7_days"),
                number(totals, "total_study_minutes"), number(totals, "total_answers"),
                number(totals, "total_notes"), number(totals, "total_posts"), trendPoints, distribution);
    }

    @Transactional(readOnly = true)
    public PageResult<UserSummary> users(String keyword, String role, String activity, String sort,
                                         String order, int page, int size) {
        String normalizedRole = role == null ? "" : role.strip().toUpperCase();
        if (!ROLES.contains(normalizedRole)) throw new BizException("未知的用户角色");
        if (!ACTIVITY_FILTERS.contains(activity)) throw new BizException("未知的活跃状态");
        String sortColumn = SORTS.get(sort);
        if (sortColumn == null) throw new BizException("未知的排序字段");
        String direction = "asc".equalsIgnoreCase(order) ? "ASC" : "DESC";
        String text = keyword == null ? "" : keyword.strip();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", text);
        params.put("like", "%" + text + "%");
        params.put("role", normalizedRole);
        params.put("size", size);
        params.put("skip", (long) (page - 1) * size);

        String filtered = "SELECT * FROM (" + USER_DATA + """
                ) user_data
                WHERE (:keyword = '' OR username LIKE :like OR nickname LIKE :like)
                  AND (:role = '' OR role = :role)
                """ + activityCondition(activity);
        Long total = db.queryForObject("SELECT COUNT(*) FROM (" + filtered + ") counted", params, Long.class);
        List<UserSummary> records = db.query(filtered + " ORDER BY " + sortColumn + " " + direction
                + ", id DESC LIMIT :size OFFSET :skip", params, USER_MAPPER);
        return new PageResult<>(records, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public UserDetail user(long id) {
        UserSummary profile = db.query("SELECT * FROM (" + USER_DATA + ") user_data WHERE id=:id",
                Map.of("id", id), USER_MAPPER).stream().findFirst()
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        Map<String, Long> counts = new LinkedHashMap<>();
        db.query("""
                SELECT action_type, COUNT(*) AS action_count FROM study_record
                WHERE user_id=:id GROUP BY action_type ORDER BY action_count DESC
                """, Map.of("id", id), (RowCallbackHandler) rs ->
                counts.put(rs.getString("action_type"), rs.getLong("action_count")));
        counts.put("EXAM", profile.answerCount());
        counts.put("NOTE", profile.noteCount());
        counts.put("FAVORITE", profile.favoriteCount());
        counts.put("POST", profile.postCount());

        List<RecentActivity> activities = db.query("""
                SELECT category, action_type, title, target_type, target_id, occurred_at FROM (
                    SELECT 'STUDY' AS category, action_type,
                           COALESCE(NULLIF(detail, ''), action_type) AS title,
                           target_type, target_id, created_at AS occurred_at
                    FROM study_record WHERE user_id=:id
                    UNION ALL
                    SELECT 'ANSWER', 'ANSWER', CONCAT('完成', COALESCE(q.category, '综合'), '题目'),
                           'QUESTION', ar.question_id, ar.answered_at
                    FROM answer_record ar LEFT JOIN question q ON q.id=ar.question_id WHERE ar.user_id=:id
                    UNION ALL
                    SELECT 'NOTE', 'NOTE', CONCAT('记录', target_type, '笔记'), target_type, target_id, created_at
                    FROM note WHERE user_id=:id
                    UNION ALL
                    SELECT 'CHAT', 'CHAT', LEFT(content, 80), NULL, NULL, created_at
                    FROM chat_message WHERE user_id=:id AND role='USER'
                    UNION ALL
                    SELECT 'POST', 'POST', COALESCE(NULLIF(LEFT(caption, 80) COLLATE utf8mb4_unicode_ci, ''), '发布图片动态'),
                           NULL, NULL, created_at FROM community_post WHERE author_id=:id
                ) recent ORDER BY occurred_at DESC LIMIT 20
                """, Map.of("id", id), (rs, n) -> new RecentActivity(rs.getString("category"),
                rs.getString("action_type"), rs.getString("title"), rs.getString("target_type"),
                nullableLong(rs, "target_id"), time(rs.getTimestamp("occurred_at"))));
        return new UserDetail(profile, counts, activities);
    }

    private static String activityCondition(String activity) {
        return switch (activity) {
            case "active7" -> " AND last_active_at >= CURRENT_DATE - INTERVAL 6 DAY";
            case "new30" -> " AND created_at >= CURRENT_DATE - INTERVAL 29 DAY";
            case "inactive30" -> " AND (last_active_at IS NULL OR last_active_at < CURRENT_DATE - INTERVAL 29 DAY)";
            default -> "";
        };
    }

    private static long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
