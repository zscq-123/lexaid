package com.lexaid.community.posts;

import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostService {
    public record Post(long id, long authorId, String authorName, String authorAvatarVersion,
                       String caption, String visibility, LocalDateTime createdAt, List<Long> imageIds,
                       boolean liked, long likeCount, long commentCount) {}
    public record Comment(long id, long authorId, String authorName, String authorAvatarVersion,
                          String content, LocalDateTime createdAt) {}

    private final NamedParameterJdbcTemplate db;

    // Friendship is evaluated on each read (including the image URL), never at publish time.
    private static final String IS_FRIEND = """
            EXISTS (SELECT 1 FROM community_friend_pair f
                    WHERE f.user_low = LEAST(p.author_id, :viewer)
                      AND f.user_high = GREATEST(p.author_id, :viewer) AND f.state = 'ACCEPTED')
            """;
    private static final String VISIBLE = "(p.visibility = 'PUBLIC' OR p.author_id = :viewer OR " + IS_FRIEND + ")";

    private static final String POSTS = """
            SELECT p.id, p.author_id, COALESCE(NULLIF(u.nickname,''), u.username) AS author_name,
                   a.version AS avatar_version, p.caption, p.visibility, p.created_at,
                   (SELECT GROUP_CONCAT(i.id ORDER BY i.position_no) FROM community_post_image i
                    WHERE i.post_id = p.id) AS image_ids,
                   EXISTS (SELECT 1 FROM community_post_like l WHERE l.post_id=p.id AND l.user_id=:viewer) AS liked,
                   (SELECT COUNT(*) FROM community_post_like l WHERE l.post_id=p.id) AS like_count,
                   (SELECT COUNT(*) FROM community_post_comment c WHERE c.post_id=p.id) AS comment_count
            FROM community_post p JOIN `user` u ON u.id=p.author_id
            LEFT JOIN user_avatar a ON a.user_id=p.author_id
            """;
    private static final String COMMENTS = """
            SELECT c.id, c.author_id, COALESCE(NULLIF(u.nickname,''), u.username) AS author_name,
                   a.version AS avatar_version, c.content, c.created_at
            FROM community_post_comment c JOIN `user` u ON u.id=c.author_id
            LEFT JOIN user_avatar a ON a.user_id=c.author_id
            """;
    private static final RowMapper<Post> POST_MAPPER = (rs, n) -> {
        String imageIds = rs.getString("image_ids");
        List<Long> images = imageIds == null ? List.of() :
                Arrays.stream(imageIds.split(",")).map(Long::valueOf).toList();
        return new Post(rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"),
                rs.getString("avatar_version"), rs.getString("caption"), rs.getString("visibility"),
                rs.getTimestamp("created_at").toLocalDateTime(), images,
                rs.getBoolean("liked"), rs.getLong("like_count"), rs.getLong("comment_count"));
    };
    private static final RowMapper<Comment> COMMENT_MAPPER = (rs, n) -> new Comment(rs.getLong("id"),
            rs.getLong("author_id"), rs.getString("author_name"), rs.getString("avatar_version"), rs.getString("content"),
            rs.getTimestamp("created_at").toLocalDateTime());

    /** Audience condition for queries using post alias p and the authenticated :viewer parameter. */
    public static String scopePredicate(String scope) {
        return switch (scope) {
            case "all" -> VISIBLE;
            case "friends" -> IS_FRIEND;
            case "mine" -> "p.author_id = :viewer";
            default -> throw new BizException("未知的信息流类型");
        };
    }

    @Transactional(readOnly = true)
    public PageResult<Post> feed(String scope, int page, int size) {
        String condition = " WHERE " + scopePredicate(scope);
        Map<String, Object> params = Map.of("viewer", UserContext.userId(), "size", size, "skip", offset(page, size));
        long count = db.queryForObject("SELECT COUNT(*) FROM community_post p" + condition, params, Long.class);
        List<Post> posts = db.query(POSTS + condition + " ORDER BY p.id DESC LIMIT :size OFFSET :skip",
                params, POST_MAPPER);
        return new PageResult<>(posts, count);
    }

    public Post get(long id) {
        return db.query(POSTS + " WHERE p.id=:id AND " + VISIBLE, accessParams(id), POST_MAPPER)
                .stream().findFirst().orElseThrow(PostService::notFound);
    }

    @Transactional
    public Post create(String caption, String visibility, List<PostImage> images) {
        String text = caption == null ? "" : caption.strip();
        if (text.isEmpty() && images.isEmpty()) throw new BizException("请添加图片或填写正文");
        if (text.codePointCount(0, text.length()) > 2200) throw new BizException("帖子正文最多 2200 字");
        if (!"PUBLIC".equals(visibility) && !"FRIENDS".equals(visibility)) throw new BizException("未知的可见范围");
        long postId = insert("""
                INSERT INTO community_post (author_id,caption,visibility) VALUES (:author,:caption,:visibility)
                """, Map.of("author", UserContext.userId(), "caption", text, "visibility", visibility));
        for (int i = 0; i < images.size(); i++) {
            PostImage image = images.get(i);
            db.update("""
                    INSERT INTO community_post_image (post_id,position_no,media_type,image_bytes)
                    VALUES (:id,:pos,:type,:data)
                    """, Map.of("id", postId, "pos", i, "type", image.mediaType(), "data", image.bytes()));
        }
        return get(postId);
    }

    public PostImage image(long postId, long imageId) {
        return db.query("""
                        SELECT i.media_type, i.image_bytes FROM community_post_image i
                        JOIN community_post p ON p.id=i.post_id WHERE p.id=:id AND i.id=:image AND
                        """ + VISIBLE, Map.of("id", postId, "image", imageId, "viewer", UserContext.userId()),
                (rs, n) -> new PostImage(rs.getString("media_type"), rs.getBytes("image_bytes")))
                .stream().findFirst().orElseThrow(PostService::notFound);
    }

    @Transactional(readOnly = true)
    public PageResult<Comment> comments(long id, int page, int size) {
        long skip = offset(page, size);
        requireVisible(id, false);
        long count = db.queryForObject("SELECT COUNT(*) FROM community_post_comment WHERE post_id=:id",
                Map.of("id", id), Long.class);
        List<Comment> records = db.query(COMMENTS + " WHERE c.post_id=:id ORDER BY c.id DESC LIMIT :size OFFSET :skip",
                Map.of("id", id, "size", size, "skip", skip), COMMENT_MAPPER);
        return new PageResult<>(records, count);
    }

    @Transactional
    public Comment addComment(long id, String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 500) {
            throw new BizException("评论须为 1 至 500 字");
        }
        requireVisible(id, true);
        long commentId = insert("""
                INSERT INTO community_post_comment (post_id,author_id,content) VALUES (:id,:viewer,:text)
                """, Map.of("id", id, "viewer", UserContext.userId(), "text", text));
        DiscussionSummaryService.queueAfterComment(db, id);
        return db.queryForObject(COMMENTS + " WHERE c.id=:id", Map.of("id", commentId), COMMENT_MAPPER);
    }

    @Transactional
    public void like(long id, boolean liked) {
        requireVisible(id, true);
        if (liked) {
            db.update("""
                    INSERT INTO community_post_like (post_id,user_id) VALUES (:id,:viewer)
                    ON DUPLICATE KEY UPDATE user_id = :viewer
                    """, accessParams(id));
        } else {
            db.update("DELETE FROM community_post_like WHERE post_id=:id AND user_id=:viewer", accessParams(id));
        }
    }

    public void delete(long id) {
        if (db.update("DELETE FROM community_post WHERE id=:id AND author_id=:viewer", accessParams(id)) == 0) {
            throw notFound();
        }
    }

    private void requireVisible(long id, boolean lock) {
        String sql = "SELECT p.id FROM community_post p WHERE p.id=:id AND " + VISIBLE + (lock ? " FOR UPDATE" : "");
        if (db.queryForList(sql, accessParams(id), Long.class).isEmpty()) throw notFound();
    }

    private long insert(String sql, Map<String, Object> params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(sql, new MapSqlParameterSource(params), keys, new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static Map<String, Object> accessParams(long id) {
        return Map.of("id", id, "viewer", UserContext.userId());
    }

    private static BizException notFound() {
        return new BizException(404, "帖子不存在或不可见");
    }

    private static long offset(int page, int size) {
        if (page < 1 || size < 1 || size > 50) throw new BizException("页码须为正数，每页最多 50 条");
        return ((long) page - 1) * size;
    }
}
