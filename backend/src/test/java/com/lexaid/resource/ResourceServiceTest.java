package com.lexaid.resource;

import com.lexaid.common.BizException;
import com.lexaid.common.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceServiceTest {

    private final NamedParameterJdbcTemplate db = mock(NamedParameterJdbcTemplate.class);
    private final ResourceService resources = new ResourceService(db);

    @BeforeEach
    void login() {
        var principal = new LoginUser(7L, "reader", "reader", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static MockMultipartFile text(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void invalidFiltersMetadataAndFilesCannotReachTheDatabase() {
        assertThrows(BizException.class, () -> resources.list("public' OR 1=1", "", "", "", "latest", 1, 12));
        assertThrows(BizException.class, () -> resources.list("community", "", "", "", "latest", 1, 51));
        assertThrows(BizException.class, () -> resources.list("community", "", "未知分类", "", "latest", 1, 12));
        assertThrows(BizException.class, () -> resources.list("community", "", "", "exe", "latest", 1, 12));
        assertThrows(BizException.class, () -> resources.list("community", "", "", "", "random", 1, 12));

        assertThrows(BizException.class, () -> resources.create("标题", "", "其他", "FRIENDS",
                text("笔记.txt", "内容")));
        assertThrows(BizException.class, () -> resources.create("标题", "", "未知分类", "PRIVATE",
                text("笔记.txt", "内容")));
        assertThrows(BizException.class, () -> resources.create("长".repeat(121), "", "其他", "PRIVATE",
                text("笔记.txt", "内容")));
        assertThrows(BizException.class, () -> resources.create("标题", "", "其他", "PRIVATE",
                new MockMultipartFile("file", "fake.pdf", "application/pdf",
                        "<svg/>".getBytes(StandardCharsets.UTF_8))));
        verifyNoInteractions(db);
    }

    @Test
    void aNonOwnerCannotEditOrDeleteAnotherUsersResource() {
        when(db.update(anyString(), anyMap())).thenReturn(0);
        assertThat(assertThrows(BizException.class,
                () -> resources.update(12, "标题", "", "其他", "PRIVATE")).getCode()).isEqualTo(404);
        assertThat(assertThrows(BizException.class, () -> resources.delete(12)).getCode()).isEqualTo(404);
    }

    @Test
    void inaccessibleDownloadDoesNotIncrementTheCounter() {
        when(db.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        assertThat(assertThrows(BizException.class, () -> resources.download(12)).getCode()).isEqualTo(404);
        verify(db, never()).update(anyString(), anyMap());
    }

    @Test
    void aVisibleDownloadReturnsTheFileAndCountsIt() {
        var bytes = "正当防卫".getBytes(StandardCharsets.UTF_8);
        when(db.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(new ResourceService.Download("笔记.txt", "text/plain", bytes)));
        var file = resources.download(12);
        assertThat(file.fileName()).isEqualTo("笔记.txt");
        assertThat(file.bytes()).isEqualTo(bytes);
        verify(db).update(argThat(sql -> sql.startsWith("UPDATE learning_resource")
                        && sql.contains("download_count = download_count + 1")
                        && sql.contains("updated_at = updated_at")),
                eq(Map.of("id", 12L)));
    }
}
