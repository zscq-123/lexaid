package com.lexaid.community.messages;

import com.lexaid.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 给单条私信设置稍后提醒。
 */
@Service
@RequiredArgsConstructor
public class MessageReminderService {

    public record ReminderResult(long reminderId, LocalDateTime remindAt, String preview) {}

    private final JdbcTemplate jdbc;
    private final MessageService messages;

    @Transactional
    public ReminderResult remind(long viewer, long messageId, Integer minutes) {
        if (minutes == null || minutes < 1 || minutes > 7 * 24 * 60) {
            throw new BizException("提醒时间须在 1 分钟至 7 天之间");
        }
        MessageService.Message message = messages.visible(viewer, messageId);
        String preview = preview(message);
        long peerId = message.senderId() == viewer ? message.recipientId() : message.senderId();
        LocalDateTime remindAt = LocalDateTime.now().plusMinutes(minutes);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO message_reminder (user_id, message_id, peer_id, preview, remind_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, viewer);
            statement.setLong(2, messageId);
            statement.setLong(3, peerId);
            statement.setString(4, preview);
            statement.setObject(5, remindAt);
            return statement;
        }, keys);
        return new ReminderResult(Objects.requireNonNull(keys.getKey()).longValue(), remindAt, preview);
    }

    private static String preview(MessageService.Message message) {
        String kind = message.kind();
        String content = message.content();
        if ("IMAGE".equals(kind)) return content == null || content.isBlank() ? "[图片]" : content;
        if ("FILE".equals(kind)) {
            return content == null || content.isBlank()
                    ? "[文件] " + (message.fileName() == null ? "附件" : message.fileName()) : content;
        }
        if ("VOICE".equals(kind)) return "[语音]";
        String text = content == null ? "" : content;
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
