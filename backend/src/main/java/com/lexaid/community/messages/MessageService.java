package com.lexaid.community.messages;

import com.lexaid.common.BizException;
import com.lexaid.common.image.SanitizedImage;
import com.lexaid.community.friends.FriendService;
import com.lexaid.resource.ResourceFile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One-to-one messages between accepted friends. Writes go through REST so validation,
 * friendship checks and persistence stay in one transaction; WebSocket is only the push channel.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    public record Quoted(long id, long senderId, String kind, String preview) {}

    public record Message(long id, long senderId, long recipientId, String content, String kind,
                          String fileName, String mediaType, long fileSize, int durationSeconds,
                          LocalDateTime createdAt, boolean read, boolean recalled,
                          LocalDateTime recalledAt, boolean recallable, Quoted quoted) {}

    public record Conversation(long peerId, String username, String nickname, String avatarVersion,
                               long lastMessageId, String lastMessage, LocalDateTime lastMessageAt,
                               boolean lastMessageMine, long unreadCount, boolean muted, boolean pinned) {}

    public record History(List<Message> records, boolean hasMore, boolean friend,
                          boolean muted, boolean pinned) {}

    public record ConversationSettings(boolean muted, boolean pinned) {}

    public record ReadResult(long peerId, int updated, LocalDateTime readAt, long unreadTotal) {}

    public record Attachment(String kind, String fileName, String mediaType, byte[] bytes) {}

    public record ClearResult(long peerId, long clearedBeforeId, long unreadTotal) {}

    public record DeleteResult(long messageId, long unreadTotal) {}

    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_FILE = "FILE";
    public static final String KIND_VOICE = "VOICE";

    private static final int MAX_CONTENT = 2000;
    private static final int MAX_PAGE_SIZE = 50;

    private static final String MESSAGE_COLUMNS = """
            id, sender_id, recipient_id, content, kind, file_name, media_type, file_size,
            duration_seconds, created_at, read_at, recalled_at,
            quote_id, quote_sender_id, quote_kind, quote_preview,
            TIMESTAMPDIFF(SECOND, created_at, NOW(6)) <= 120 AS recallable
            """;

    private static final String CONVERSATIONS = """
            SELECT m.id, m.sender_id, m.created_at,
                   CASE
                     WHEN m.recalled_at IS NOT NULL THEN '消息已撤回'
                     WHEN m.kind = 'IMAGE' THEN
                       IF(CHAR_LENGTH(TRIM(m.content)) > 0, LEFT(m.content, 160), '[图片]')
                     WHEN m.kind = 'FILE' THEN
                       IF(CHAR_LENGTH(TRIM(m.content)) > 0, LEFT(m.content, 160),
                          CONCAT('[文件] ', COALESCE(m.file_name, '附件')))
                     WHEN m.kind = 'VOICE' THEN '[语音]'
                     ELSE LEFT(m.content, 160)
                   END AS content,
                   u.id AS peer_id, u.username, u.nickname, a.version AS avatar_version,
                   COALESCE(s.muted, 0) AS muted, COALESCE(s.pinned, 0) AS pinned,
                   (SELECT COUNT(*) FROM direct_message x
                     LEFT JOIN message_conversation_clear xc
                       ON xc.user_id = ? AND xc.peer_id = u.id
                     WHERE x.sender_id = u.id AND x.recipient_id = ?
                       AND x.read_at IS NULL AND x.recalled_at IS NULL
                       AND x.id > COALESCE(xc.cleared_before_id, 0)
                       AND NOT EXISTS (SELECT 1 FROM message_user_delete md
                                       WHERE md.user_id = ? AND md.message_id = x.id)) AS unread_count
            FROM direct_message m
            JOIN (
                SELECT visible.peer_id, MAX(visible.id) AS last_id
                FROM (
                    SELECT IF(dm.sender_id = ?, dm.recipient_id, dm.sender_id) AS peer_id, dm.id
                    FROM direct_message dm
                    LEFT JOIN message_conversation_clear c
                      ON c.user_id = ? AND c.peer_id = IF(dm.sender_id = ?, dm.recipient_id, dm.sender_id)
                    WHERE (dm.sender_id = ? OR dm.recipient_id = ?)
                      AND dm.id > COALESCE(c.cleared_before_id, 0)
                      AND NOT EXISTS (SELECT 1 FROM message_user_delete md
                                      WHERE md.user_id = ? AND md.message_id = dm.id)
                ) visible
                GROUP BY visible.peer_id
            ) latest ON latest.last_id = m.id
            JOIN `user` u ON u.id = latest.peer_id
            LEFT JOIN user_avatar a ON a.user_id = u.id
            LEFT JOIN message_conversation_setting s ON s.user_id = ? AND s.peer_id = u.id
            ORDER BY COALESCE(s.pinned, 0) DESC, m.id DESC
            """;

    private static final String PAIR = """
            ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
            """;

    private static final RowMapper<Message> MESSAGE_MAPPER = (rs, row) -> {
        Timestamp readAt = rs.getTimestamp("read_at");
        Timestamp recalledAt = rs.getTimestamp("recalled_at");
        Long fileSize = rs.getObject("file_size") == null ? null : rs.getLong("file_size");
        LocalDateTime created = time(rs.getTimestamp("created_at"));
        boolean recallable = recalledAt == null && rs.getBoolean("recallable");
        Long quoteId = rs.getObject("quote_id") == null ? null : rs.getLong("quote_id");
        Quoted quoted = quoteId == null ? null : new Quoted(quoteId, rs.getLong("quote_sender_id"),
                rs.getString("quote_kind"), rs.getString("quote_preview"));
        return new Message(rs.getLong("id"), rs.getLong("sender_id"), rs.getLong("recipient_id"),
                rs.getString("content"), rs.getString("kind"), rs.getString("file_name"),
                rs.getString("media_type"), fileSize == null ? 0 : fileSize,
                rs.getInt("duration_seconds"), created, readAt != null, recalledAt != null,
                time(recalledAt), recallable, quoted);
    };

    private final JdbcTemplate jdbc;
    private final FriendService friends;

    @Transactional(readOnly = true)
    public List<Conversation> conversations(long viewer) {
        return jdbc.query(CONVERSATIONS, (rs, row) -> new Conversation(
                rs.getLong("peer_id"), rs.getString("username"), rs.getString("nickname"),
                rs.getString("avatar_version"), rs.getLong("id"), rs.getString("content"),
                time(rs.getTimestamp("created_at")), rs.getLong("sender_id") == viewer,
                rs.getLong("unread_count"), rs.getBoolean("muted"), rs.getBoolean("pinned")),
                viewer, viewer, viewer, viewer, viewer, viewer, viewer, viewer, viewer, viewer);
    }

    @Transactional(readOnly = true)
    public History history(long viewer, long peerId, Long beforeId, int size) {
        requirePeer(viewer, peerId);
        if (beforeId != null && beforeId <= 0) throw new BizException("消息游标无效");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BizException("每页消息数量超出范围");

        long cleared = clearedBefore(viewer, peerId);
        long total = countVisiblePair(viewer, peerId, cleared);
        boolean friend = friends.areFriends(viewer, peerId);
        if (total == 0 && !friend && cleared == 0) {
            throw new BizException(403, "你们还不是好友，无法查看私信");
        }

        String sql = "SELECT " + MESSAGE_COLUMNS
                + " FROM direct_message WHERE " + PAIR + " AND id > ?"
                + " AND NOT EXISTS (SELECT 1 FROM message_user_delete md"
                + " WHERE md.user_id = ? AND md.message_id = direct_message.id)";
        List<Object> params = new ArrayList<>(List.of(viewer, peerId, peerId, viewer, cleared, viewer));
        if (beforeId != null) {
            sql += " AND id < ?";
            params.add(beforeId);
        }
        sql += " ORDER BY id DESC LIMIT ?";
        params.add(size + 1);
        List<Message> rows = jdbc.query(sql, MESSAGE_MAPPER, params.toArray());
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, size));
        Collections.reverse(rows);
        ConversationSettings settings = settings(viewer, peerId);
        return new History(rows, hasMore, friend, settings.muted(), settings.pinned());
    }

    @Transactional
    public Message send(long viewer, Long recipientId, String content, Long quoteId) {
        String text = messageText(content);
        long recipient = requireRecipient(viewer, recipientId);
        Quoted quote = buildQuote(viewer, recipient, quoteId);
        long id = insert(viewer, recipient, text, KIND_TEXT, null, null, null, null, null, quote);
        return find(id);
    }

    @Transactional
    public Message sendAttachment(long viewer, Long recipientId, String kindValue, String content,
                                  Integer durationSeconds, MultipartFile upload, Long quoteId) {
        String kind = kindValue == null ? "" : kindValue.strip().toUpperCase(Locale.ROOT);
        if (!KIND_IMAGE.equals(kind) && !KIND_FILE.equals(kind) && !KIND_VOICE.equals(kind)) {
            throw new BizException("消息附件类型无效");
        }
        String caption = caption(content);
        long recipient = requireRecipient(viewer, recipientId);
        Quoted quote = buildQuote(viewer, recipient, quoteId);
        String fileName;
        String mediaType;
        long fileSize;
        byte[] fileBytes;
        Integer duration = null;
        if (KIND_IMAGE.equals(kind)) {
            SanitizedImage image = SanitizedImage.read(upload, 1920);
            mediaType = image.mediaType();
            fileName = "图片." + (mediaType.endsWith("png") ? "png" : "jpg");
            fileSize = image.bytes().length;
            fileBytes = image.bytes();
        } else if (KIND_FILE.equals(kind)) {
            ResourceFile file = ResourceFile.read(upload);
            fileName = file.fileName();
            mediaType = file.mediaType();
            fileSize = file.bytes().length;
            fileBytes = file.bytes();
        } else {
            if (durationSeconds == null || durationSeconds < 1 || durationSeconds > 300) {
                throw new BizException("语音时长须为 1 至 300 秒");
            }
            VoiceClip voice = VoiceClip.read(upload);
            fileName = voice.fileName();
            mediaType = voice.mediaType();
            fileSize = voice.bytes().length;
            fileBytes = voice.bytes();
            duration = durationSeconds;
        }
        long id = insert(viewer, recipient, caption, kind, fileName, mediaType, fileSize, duration, fileBytes, quote);
        return find(id);
    }

    @Transactional
    public Message forward(long viewer, long id, Long recipientId) {
        Message source = findForParticipant(viewer, id);
        if (source.recalled()) throw new BizException(409, "已撤回的消息不能转发");
        long recipient = requireRecipient(viewer, recipientId);
        byte[] bytes = null;
        if (source.fileSize() > 0) bytes = attachment(viewer, id).bytes();
        long newId = insert(viewer, recipient, source.content(), source.kind(), source.fileName(),
                source.mediaType(), source.fileSize() > 0 ? source.fileSize() : null,
                source.durationSeconds() > 0 ? source.durationSeconds() : null, bytes, source.quoted());
        return find(newId);
    }

    @Transactional
    public DeleteResult deleteForUser(long viewer, long id) {
        findForParticipant(viewer, id);
        jdbc.update("INSERT IGNORE INTO message_user_delete (user_id, message_id) VALUES (?, ?)", viewer, id);
        return new DeleteResult(id, unreadTotal(viewer));
    }

    @Transactional(readOnly = true)
    public Message visible(long viewer, long id) {
        Message message = findForParticipant(viewer, id);
        if (message.recalled()) throw new BizException(404, "消息已撤回");
        return message;
    }

    @Transactional
    public Message recall(long viewer, long id) {
        Message message = findForParticipant(viewer, id);
        if (message.senderId() != viewer) throw new BizException(403, "只能撤回自己发送的消息");
        if (message.recalled()) return message;
        if (!message.recallable()) throw new BizException(409, "消息发送超过 2 分钟，无法撤回");
        jdbc.update("""
                UPDATE direct_message
                SET content = '', recalled_at = ?, file_name = NULL, media_type = NULL,
                    file_size = NULL, duration_seconds = NULL, file_bytes = NULL
                WHERE id = ? AND recalled_at IS NULL
                """, LocalDateTime.now(), id);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<Message> search(long viewer, long peerId, String keyword, int limit) {
        requirePeer(viewer, peerId);
        String text = keyword == null ? "" : keyword.strip();
        if (text.isEmpty()) throw new BizException("请输入查找内容");
        if (text.codePointCount(0, text.length()) > 100) throw new BizException("查找内容不能超过 100 字");
        if (limit < 1 || limit > 50) throw new BizException("查找结果数量超出范围");
        long cleared = clearedBefore(viewer, peerId);
        String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM direct_message WHERE " + PAIR
                        + " AND id > ? AND recalled_at IS NULL AND (content LIKE ? OR file_name LIKE ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM message_user_delete md"
                        + " WHERE md.user_id = ? AND md.message_id = direct_message.id)"
                        + " ORDER BY id DESC LIMIT ?",
                MESSAGE_MAPPER, viewer, peerId, peerId, viewer, cleared, like, like, viewer, limit);
    }

    @Transactional(readOnly = true)
    public ConversationSettings settings(long viewer, long peerId) {
        return jdbc.query("SELECT muted, pinned FROM message_conversation_setting "
                                + "WHERE user_id = ? AND peer_id = ?",
                        (rs, row) -> new ConversationSettings(rs.getBoolean("muted"), rs.getBoolean("pinned")),
                        viewer, peerId).stream().findFirst()
                .orElse(new ConversationSettings(false, false));
    }

    @Transactional
    public ConversationSettings updateSettings(long viewer, long peerId, Boolean muted, Boolean pinned) {
        requirePeer(viewer, peerId);
        boolean nextMuted = Boolean.TRUE.equals(muted);
        boolean nextPinned = Boolean.TRUE.equals(pinned);
        jdbc.update("""
                INSERT INTO message_conversation_setting (user_id, peer_id, muted, pinned)
                VALUES (?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE muted = incoming.muted, pinned = incoming.pinned, updated_at = NOW(6)
                """, viewer, peerId, nextMuted, nextPinned);
        return new ConversationSettings(nextMuted, nextPinned);
    }

    @Transactional(readOnly = true)
    public Attachment attachment(long viewer, long id) {
        return jdbc.query("""
                        SELECT d.kind, d.file_name, d.media_type, d.file_bytes
                        FROM direct_message d
                        LEFT JOIN message_conversation_clear c
                          ON c.user_id = ? AND c.peer_id = IF(d.sender_id = ?, d.recipient_id, d.sender_id)
                        WHERE d.id = ? AND (d.sender_id = ? OR d.recipient_id = ?)
                          AND d.recalled_at IS NULL AND d.file_bytes IS NOT NULL
                          AND d.id > COALESCE(c.cleared_before_id, 0)
                          AND NOT EXISTS (SELECT 1 FROM message_user_delete md
                                          WHERE md.user_id = ? AND md.message_id = d.id)
                        """, (rs, row) -> new Attachment(rs.getString("kind"), rs.getString("file_name"),
                        rs.getString("media_type"), rs.getBytes("file_bytes")), viewer, viewer, id, viewer, viewer, viewer)
                .stream().findFirst()
                .orElseThrow(() -> new BizException(404, "消息附件不存在或已撤回"));
    }

    @Transactional
    public ReadResult markRead(long viewer, long peerId) {
        requirePeer(viewer, peerId);
        if (!friends.areFriends(viewer, peerId) && countPair(viewer, peerId) == 0) {
            throw new BizException(403, "你们还不是好友，无法操作该会话");
        }
        LocalDateTime readAt = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE direct_message SET read_at = ?
                WHERE sender_id = ? AND recipient_id = ? AND read_at IS NULL AND recalled_at IS NULL
                """, readAt, peerId, viewer);
        return new ReadResult(peerId, updated, readAt, unreadTotal(viewer));
    }

    @Transactional(readOnly = true)
    public long unreadTotal(long viewer) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM direct_message x
                LEFT JOIN message_conversation_clear c
                  ON c.user_id = ? AND c.peer_id = x.sender_id
                WHERE x.recipient_id = ? AND x.read_at IS NULL AND x.recalled_at IS NULL
                  AND x.id > COALESCE(c.cleared_before_id, 0)
                  AND NOT EXISTS (SELECT 1 FROM message_user_delete md
                                  WHERE md.user_id = ? AND md.message_id = x.id)
                """, Long.class, viewer, viewer, viewer);
        return count == null ? 0 : count;
    }

    @Transactional
    public ClearResult clear(long viewer, long peerId) {
        requirePeer(viewer, peerId);
        Long max = jdbc.query("SELECT COALESCE(MAX(id), 0) FROM direct_message WHERE " + PAIR,
                        (rs, row) -> rs.getLong(1), viewer, peerId, peerId, viewer)
                .stream().findFirst().orElse(0L);
        if (max > 0) {
            jdbc.update("""
                    INSERT INTO message_conversation_clear (user_id, peer_id, cleared_before_id, cleared_at)
                    VALUES (?, ?, ?, NOW(6)) AS incoming
                    ON DUPLICATE KEY UPDATE
                        cleared_before_id = GREATEST(message_conversation_clear.cleared_before_id,
                                                     incoming.cleared_before_id),
                        cleared_at = NOW(6)
                    """, viewer, peerId, max);
        }
        return new ClearResult(peerId, max, unreadTotal(viewer));
    }

    private Message find(long id) {
        return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM direct_message WHERE id = ?",
                        MESSAGE_MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new BizException(404, "消息不存在"));
    }

    private Message findForParticipant(long viewer, long id) {
        return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM direct_message d "
                                + "LEFT JOIN message_conversation_clear c "
                                + "ON c.user_id = ? AND c.peer_id = IF(d.sender_id = ?, d.recipient_id, d.sender_id) "
                                + "WHERE d.id = ? AND (d.sender_id = ? OR d.recipient_id = ?) "
                                + "AND d.id > COALESCE(c.cleared_before_id, 0) "
                                + "AND NOT EXISTS (SELECT 1 FROM message_user_delete md "
                                + "WHERE md.user_id = ? AND md.message_id = d.id)",
                        MESSAGE_MAPPER, viewer, viewer, id, viewer, viewer, viewer).stream().findFirst()
                .orElseThrow(() -> new BizException(404, "消息不存在"));
    }

    private long insert(long sender, long recipient, String content, String kind, String fileName,
                        String mediaType, Long fileSize, Integer durationSeconds, byte[] fileBytes, Quoted quote) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO direct_message
                        (sender_id, recipient_id, content, kind, file_name, media_type, file_size,
                         duration_seconds, file_bytes, quote_id, quote_sender_id, quote_kind, quote_preview)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sender);
            statement.setLong(2, recipient);
            statement.setString(3, content);
            statement.setString(4, kind);
            statement.setString(5, fileName);
            statement.setString(6, mediaType);
            if (fileSize == null) statement.setNull(7, Types.BIGINT);
            else statement.setLong(7, fileSize);
            if (durationSeconds == null) statement.setNull(8, Types.INTEGER);
            else statement.setInt(8, durationSeconds);
            if (fileBytes == null) statement.setNull(9, Types.BLOB);
            else statement.setBytes(9, fileBytes);
            if (quote == null) {
                statement.setNull(10, Types.BIGINT);
                statement.setNull(11, Types.BIGINT);
                statement.setNull(12, Types.VARCHAR);
                statement.setNull(13, Types.VARCHAR);
            } else {
                statement.setLong(10, quote.id());
                statement.setLong(11, quote.senderId());
                statement.setString(12, quote.kind());
                statement.setString(13, quote.preview());
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private Quoted buildQuote(long viewer, long recipient, Long quoteId) {
        if (quoteId == null) return null;
        Message source = findForParticipant(viewer, quoteId);
        if (source.recalled()) throw new BizException(409, "引用的消息已撤回");
        boolean samePair = (source.senderId() == viewer && source.recipientId() == recipient)
                || (source.senderId() == recipient && source.recipientId() == viewer);
        if (!samePair) throw new BizException(403, "只能引用当前会话中的消息");
        String preview = previewOf(source.kind(), source.content(), source.fileName());
        if (preview.codePointCount(0, preview.length()) > 300) {
            preview = preview.substring(0, preview.offsetByCodePoints(0, 300));
        }
        return new Quoted(source.id(), source.senderId(), source.kind(), preview);
    }

    private static String previewOf(String kind, String content, String fileName) {
        if (KIND_IMAGE.equals(kind)) {
            return content == null || content.isBlank() ? "[图片]" : content;
        }
        if (KIND_FILE.equals(kind)) {
            return content == null || content.isBlank()
                    ? "[文件] " + (fileName == null ? "附件" : fileName) : content;
        }
        if (KIND_VOICE.equals(kind)) return "[语音]";
        return content == null ? "" : content;
    }

    private long requireRecipient(long viewer, Long recipientId) {
        if (recipientId == null || recipientId <= 0) throw new BizException("请选择接收人");
        if (recipientId == viewer) throw new BizException("不能给自己发送私信");
        if (!exists(recipientId)) throw new BizException(404, "账号不存在");
        if (!friends.areFriends(viewer, recipientId)) throw new BizException(403, "只能给好友发送私信");
        return recipientId;
    }

    private void requirePeer(long viewer, long peerId) {
        if (peerId <= 0) throw new BizException("会话参数无效");
        if (peerId == viewer) throw new BizException("不能与自己建立会话");
        if (!exists(peerId)) throw new BizException(404, "账号不存在");
    }

    private boolean exists(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE id = ?", Long.class, userId);
        return count != null && count > 0;
    }

    private long countPair(long viewer, long peerId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM direct_message WHERE " + PAIR,
                Long.class, viewer, peerId, peerId, viewer);
        return count == null ? 0 : count;
    }

    private long countVisiblePair(long viewer, long peerId, long clearedBefore) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM direct_message WHERE " + PAIR
                        + " AND id > ? AND NOT EXISTS (SELECT 1 FROM message_user_delete md"
                        + " WHERE md.user_id = ? AND md.message_id = direct_message.id)",
                Long.class, viewer, peerId, peerId, viewer, clearedBefore, viewer);
        return count == null ? 0 : count;
    }

    private long clearedBefore(long viewer, long peerId) {
        return jdbc.query("SELECT cleared_before_id FROM message_conversation_clear "
                                + "WHERE user_id = ? AND peer_id = ?",
                        (rs, row) -> rs.getLong(1), viewer, peerId)
                .stream().findFirst().orElse(0L);
    }

    private static String messageText(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) throw new BizException("消息内容不能为空");
        if (text.codePointCount(0, text.length()) > MAX_CONTENT) throw new BizException("单条消息不能超过 2000 字");
        return text;
    }

    private static String caption(String value) {
        String text = value == null ? "" : value.strip();
        if (text.codePointCount(0, text.length()) > MAX_CONTENT) throw new BizException("单条消息不能超过 2000 字");
        return text;
    }

    private static LocalDateTime time(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
