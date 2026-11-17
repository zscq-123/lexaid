package com.lexaid.community.messages;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageReminderWorkerTest {

    @Test
    void marksAReminderOnlyAfterAtLeastOneSuccessfulDelivery() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MessageNotifier notifier = mock(MessageNotifier.class);
        MessageWebSocketHandler sockets = mock(MessageWebSocketHandler.class);
        MessageReminderWorker worker = new MessageReminderWorker(jdbc, notifier, sockets);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(9L);
        when(row.getLong("user_id")).thenReturn(42L);
        when(row.getString("preview")).thenReturn("复习提醒");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(sockets.isOnline(42L)).thenReturn(true);

        when(notifier.reminder(42L, "复习提醒")).thenReturn(false);
        worker.poll();
        verify(jdbc, never()).update(anyString(), eq(9L));

        when(notifier.reminder(42L, "复习提醒")).thenReturn(true);
        worker.poll();
        verify(jdbc).update(anyString(), eq(9L));
    }
}
