package com.lexaid.community.friends;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FriendServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FriendService service = new FriendService(jdbc);

    @Test
    void cannotSendToSelf() {
        assertThrows(BizException.class, () -> service.request(1, 1, "hello"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void firstRequestWinsAndRetriesReturnTheOriginalRequestId() throws Exception {
        lockedPair("PENDING", "initial-token", 1L);
        assertEquals("initial-token", service.request(1, 2, "again"));
        BizException conflict = assertThrows(BizException.class, () -> service.request(2, 1, "reversed"));
        assertEquals(409, conflict.getCode());
        verify(jdbc, never()).update(startsWith("UPDATE"), any(Object[].class));
    }

    @Test
    void resendingAfterDeclineRotatesTheApprovalToken() throws Exception {
        lockedPair("DECLINED", "old-token", 2L);
        String token = service.request(2, 1, "hello");
        assertDoesNotThrow(() -> UUID.fromString(token));
        verify(jdbc).update(contains("SET state='PENDING'"), eq(token), eq(2L), eq("hello"), eq(1L), eq(2L));
    }

    @Test
    void missingOrIneligibleRequestCannotBeAccepted() {
        BizException error = assertThrows(BizException.class, () -> service.decide(3, "stale", true));
        assertEquals(404, error.getCode());
        verify(jdbc).query(contains("request_id=? AND requester_id<>? AND (user_low=? OR user_high=?) FOR UPDATE"),
                any(RowMapper.class), eq("stale"), eq(3L), eq(3L), eq(3L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void retryOfAcceptedRequestDoesNotReopenOrRejectIt() throws Exception {
        lockedPair("ACCEPTED", "token", 1L);
        assertDoesNotThrow(() -> service.decide(2, "token", true));
        assertEquals(409, assertThrows(BizException.class, () -> service.decide(2, "token", false)).getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private void lockedPair(String state, String token, long from) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("state")).thenReturn(state);
        when(row.getString("request_id")).thenReturn(token);
        when(row.getLong("requester_id")).thenReturn(from);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
    }
}
