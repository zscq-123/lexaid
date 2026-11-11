package com.lexaid.community.messages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * 轮询到期的私信提醒并通过 WebSocket 推给用户。离线用户会在下次连接前错过提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageReminderWorker {

    private record Due(long id, long userId, String preview) {}

    private final JdbcTemplate jdbc;
    private final MessageNotifier notifier;
    private final MessageWebSocketHandler sockets;

    @Scheduled(fixedDelayString = "${messages.reminder-poll-ms:30000}")
    public void poll() {
        List<Due> due = jdbc.query("""
                SELECT id, user_id, preview FROM message_reminder
                WHERE notified_at IS NULL AND remind_at <= NOW(6)
                ORDER BY remind_at LIMIT 20
                """, (rs, row) -> new Due(rs.getLong("id"), rs.getLong("user_id"), rs.getString("preview")));
        for (Due reminder : due) {
            if (!sockets.isOnline(reminder.userId())) continue;
            try {
                notifier.reminder(reminder.userId(), reminder.preview());
                jdbc.update("UPDATE message_reminder SET notified_at = NOW(6) WHERE id = ? AND notified_at IS NULL",
                        reminder.id());
            } catch (RuntimeException e) {
                log.warn("私信提醒推送失败 reminderId={}", reminder.id(), e);
            }
        }
    }
}
