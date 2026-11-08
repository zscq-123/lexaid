package com.lexaid.community.posts;

import com.lexaid.common.BizException;
import com.lexaid.common.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostServiceTest {
    private final NamedParameterJdbcTemplate db = mock(NamedParameterJdbcTemplate.class);
    private final PostService service = new PostService(db);

    @BeforeEach
    void login() {
        var principal = new LoginUser(9L, "reader", "reader", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inaccessiblePostCannotBeCommentedOnOrLiked() {
        assertEquals(404, assertThrows(BizException.class, () -> service.addComment(42, "hello")).getCode());
        assertEquals(404, assertThrows(BizException.class, () -> service.like(42, true)).getCode());
        verify(db, times(2)).queryForList(
                argThat(sql -> sql.contains("p.id=:id") && sql.contains("f.state = 'ACCEPTED'") &&
                        sql.contains("p.author_id = :viewer") && sql.endsWith("FOR UPDATE")),
                eq(Map.of("viewer", 9L, "id", 42L)), eq(Long.class));
        verifyNoMoreInteractions(db);
    }

    @Test
    void invalidAudienceOrEmptyPostCannotInsertData() {
        assertThrows(BizException.class, () -> service.create("", "PUBLIC", List.of()));
        assertThrows(BizException.class, () -> service.create("x", "all", List.of()));
        assertThrows(BizException.class, () -> service.feed("public' or 1=1", 1, 10));
        assertThrows(BizException.class, () -> service.feed("all", 1, 1000));
        verifyNoInteractions(db);
    }
}
