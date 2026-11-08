package com.lexaid.community.friends;

import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final JdbcTemplate jdbc;

    public record Person(long id, String username, String nickname, String relationship, String requestId, String avatarVersion) {}
    public record Invitation(String requestId, String direction, Person person, String message, LocalDateTime requestedAt) {}

    // Resolve a pair from either user's perspective without exposing private account data.
    private static final String PERSON_COLUMNS = "u.id, u.username, u.nickname, f.request_id, f.requester_id, "
            + "f.state, a.version AS avatar_version";
    private static final String USER_SELECT = "SELECT " + PERSON_COLUMNS + " " + """
            FROM `user` u LEFT JOIN community_friend_pair f
              ON f.user_low = LEAST(u.id, ?) AND f.user_high = GREATEST(u.id, ?)
            LEFT JOIN user_avatar a ON a.user_id=u.id
            """;
    private static final String DIRECTORY_FROM = """
            FROM community_friend_pair f JOIN `user` u
              ON u.id = IF(f.user_low = ?, f.user_high, f.user_low)
            LEFT JOIN user_avatar a ON a.user_id=u.id
            WHERE (f.user_low = ? OR f.user_high = ?)
            """;

    private static Person person(ResultSet rs, long viewer) throws SQLException {
        String state = rs.getString("state");
        String relation = "NONE";
        if ("ACCEPTED".equals(state)) relation = "FRIEND";
        if ("PENDING".equals(state)) relation = rs.getLong("requester_id") == viewer ? "OUTGOING" : "INCOMING";
        return new Person(rs.getLong("id"), rs.getString("username"), rs.getString("nickname"),
                relation, "PENDING".equals(state) ? rs.getString("request_id") : null, rs.getString("avatar_version"));
    }

    public List<Person> search(long viewer, String query) {
        String term = query.strip();
        if (term.isEmpty()) return List.of();
        String sql = USER_SELECT + """
                WHERE u.id <> ? AND (LOCATE(?, u.username) > 0 OR LOCATE(?, u.nickname) > 0)
                ORDER BY (u.username = ?) DESC, u.username, u.id LIMIT 30
                """;
        return jdbc.query(sql, (rs, i) -> person(rs, viewer), viewer, viewer, viewer, term, term, term);
    }

    public Person contact(long viewer, long other) {
        if (other == viewer) throw new BizException("不能添加自己为好友");
        return jdbc.query(USER_SELECT + " WHERE u.id = ?", (rs, i) -> person(rs, viewer),
                viewer, viewer, other).stream().findFirst().orElseThrow(() -> new BizException(404, "账号不存在"));
    }

    public List<Person> friends(long viewer) {
        String sql = "SELECT " + PERSON_COLUMNS + " "
                + DIRECTORY_FROM + " AND f.state = 'ACCEPTED' ORDER BY COALESCE(u.nickname,u.username), u.id";
        return jdbc.query(sql, (rs, i) -> person(rs, viewer), viewer, viewer, viewer);
    }

    public List<Invitation> pending(long viewer) {
        String sql = "SELECT " + PERSON_COLUMNS + ", f.message, f.requested_at "
                + DIRECTORY_FROM + " AND f.state = 'PENDING' ORDER BY f.requested_at DESC, f.request_id";
        return jdbc.query(sql, (rs, i) -> new Invitation(rs.getString("request_id"),
                rs.getLong("requester_id") == viewer ? "OUTGOING" : "INCOMING", person(rs, viewer),
                rs.getString("message"), rs.getTimestamp("requested_at").toLocalDateTime()), viewer, viewer, viewer);
    }

    private record LockedPair(String state, String requestId, long requester) {}
    private static final RowMapper<LockedPair> PAIR_MAPPER = (rs, i) ->
            new LockedPair(rs.getString("state"), rs.getString("request_id"), rs.getLong("requester_id"));

    @Transactional
    public String request(long viewer, long other, String message) {
        if (other == viewer) throw new BizException("不能添加自己为好友");
        long low = Math.min(viewer, other), high = Math.max(viewer, other);
        String token = UUID.randomUUID().toString();
        String greeting = message == null ? "" : message.strip();
        // A canonical PK arbitrates concurrent first invitations. The duplicate
        // branch takes the same row lock without replacing a pending greeting.
        jdbc.update("""
                INSERT INTO community_friend_pair (user_low,user_high,request_id,requester_id,state,message)
                SELECT ?,?,?,?,'PENDING',? FROM `user` WHERE id = ?
                ON DUPLICATE KEY UPDATE user_low = community_friend_pair.user_low
                """, low, high, token, viewer, greeting, other);
        LockedPair pair = jdbc.query("""
                SELECT state,request_id,requester_id FROM community_friend_pair
                 WHERE user_low=? AND user_high=? FOR UPDATE
                """, PAIR_MAPPER, low, high).stream().findFirst().orElseThrow(() -> new BizException(404, "账号不存在"));
        if ("PENDING".equals(pair.state())) {
            if (pair.requester() != viewer) throw new BizException(409, "对方已发来申请，请在新的朋友中处理");
            return pair.requestId();
        }
        if ("ACCEPTED".equals(pair.state())) throw new BizException(409, "你们已经是好友");
        // Reapplying creates a fresh request token. Late responses to an old
        // request can no longer approve the new one, even if the sender matches.
        jdbc.update("""
                UPDATE community_friend_pair SET state='PENDING', request_id=?, requester_id=?, message=?,
                       requested_at=CURRENT_TIMESTAMP(6) WHERE user_low=? AND user_high=?
                """, token, viewer, greeting, low, high);
        return token;
    }

    @Transactional
    public void decide(long viewer, String requestId, boolean accept) {
        LockedPair pair = jdbc.query("""
                SELECT state,request_id,requester_id FROM community_friend_pair
                 WHERE request_id=? AND requester_id<>? AND (user_low=? OR user_high=?) FOR UPDATE
                """, PAIR_MAPPER, requestId, viewer, viewer, viewer).stream().findFirst()
                .orElseThrow(() -> new BizException(404, "未找到可处理的好友申请"));
        String next = accept ? "ACCEPTED" : "DECLINED";
        if (next.equals(pair.state())) return;
        if (!"PENDING".equals(pair.state())) throw new BizException(409, "这条好友申请已经处理过了");
        jdbc.update("UPDATE community_friend_pair SET state=? WHERE request_id=?", next, requestId);
    }

    public void remove(long viewer, long other) {
        jdbc.update("DELETE FROM community_friend_pair WHERE user_low=? AND user_high=? AND state='ACCEPTED'",
                Math.min(viewer, other), Math.max(viewer, other));
    }
}
