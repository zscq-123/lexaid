package com.lexaid.community.messages;

import com.lexaid.common.BizException;
import com.lexaid.community.friends.FriendService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FriendService friends = mock(FriendService.class);
    private final MessageService messages = new MessageService(jdbc, friends);

    @Test
    void rejectsSelfEmptyOversizedAndNonFriendMessagesBeforeInsert() {
        assertThrows(BizException.class, () -> messages.send(1, 1L, "hello", null));
        assertThrows(BizException.class, () -> messages.send(1, 2L, "   ", null));
        assertThrows(BizException.class, () -> messages.send(1, 2L, "长".repeat(2001), null));
        verifyNoInteractions(jdbc, friends);

        when(jdbc.queryForObject(contains("FROM `user`"), eq(Long.class), eq(2L))).thenReturn(1L);
        when(friends.areFriends(1, 2)).thenReturn(false);
        BizException error = assertThrows(BizException.class, () -> messages.send(1, 2L, "hello", null));
        assertThat(error.getCode()).isEqualTo(403);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aConversationWithNeitherFriendshipNorHistoryIsHidden() {
        when(jdbc.queryForObject(contains("FROM `user`"), eq(Long.class), eq(2L))).thenReturn(1L);
        when(jdbc.queryForObject(contains("COUNT(*) FROM direct_message"), eq(Long.class),
                eq(1L), eq(2L), eq(2L), eq(1L))).thenReturn(0L);
        when(friends.areFriends(1, 2)).thenReturn(false);

        BizException error = assertThrows(BizException.class, () -> messages.history(1, 2, null, 30));
        assertThat(error.getCode()).isEqualTo(403);
    }

    @Test
    void readingAMessagesCountsOnlyUnreadIncomingMessages() {
        when(jdbc.queryForObject(contains("FROM `user`"), eq(Long.class), eq(2L))).thenReturn(1L);
        when(friends.areFriends(1, 2)).thenReturn(true);
        when(jdbc.update(anyString(), any(LocalDateTime.class), eq(2L), eq(1L))).thenReturn(2);
        when(jdbc.queryForObject(contains("read_at IS NULL"), eq(Long.class), eq(1L), eq(1L), eq(1L))).thenReturn(4L);

        MessageService.ReadResult result = messages.markRead(1, 2);
        assertThat(result.peerId()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(2);
        assertThat(result.unreadTotal()).isEqualTo(4);
        assertThat(result.readAt()).isNotNull();
    }

    @Test
    void unreadTotalIsZeroWhenTheDatabaseReturnsNoRow() {
        when(jdbc.queryForObject(contains("read_at IS NULL"), eq(Long.class), eq(1L), eq(1L), eq(1L))).thenReturn(null);
        assertThat(messages.unreadTotal(1)).isZero();
    }

    @Test
    void unknownAttachmentKindsAreRejectedBeforeReadingTheUpload() {
        when(jdbc.queryForObject(contains("FROM `user`"), eq(Long.class), eq(2L))).thenReturn(1L);
        when(friends.areFriends(1, 2)).thenReturn(true);
        BizException error = assertThrows(BizException.class,
                () -> messages.sendAttachment(1, 2L, "VIDEO", "", null, null, null));
        assertThat(error.getMessage()).contains("附件类型");
    }

    @Test
    void onlyTheSenderCanRecallAMessageWithinTwoMinutes() {
        MessageService.Message message = new MessageService.Message(9, 2, 1, "hello", "TEXT",
                null, null, 0, 0, LocalDateTime.now(), false, false, null, true, null);
        when(jdbc.query(contains("FROM direct_message"), any(RowMapper.class), eq(1L), eq(1L), eq(9L), eq(1L), eq(1L), eq(1L)))
                .thenReturn(List.of(message));
        assertThat(assertThrows(BizException.class, () -> messages.recall(1, 9)).getCode()).isEqualTo(403);

        MessageService.Message old = new MessageService.Message(9, 1, 2, "hello", "TEXT",
                null, null, 0, 0, LocalDateTime.now().minusMinutes(3), false, false, null, false, null);
        when(jdbc.query(contains("FROM direct_message"), any(RowMapper.class), eq(1L), eq(1L), eq(9L), eq(1L), eq(1L), eq(1L)))
                .thenReturn(List.of(old));
        assertThat(assertThrows(BizException.class, () -> messages.recall(1, 9)).getCode()).isEqualTo(409);
    }
}
