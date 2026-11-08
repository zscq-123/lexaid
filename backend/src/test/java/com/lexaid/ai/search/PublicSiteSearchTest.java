package com.lexaid.ai.search;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicSiteSearchTest {
    private final NamedParameterJdbcTemplate db = mock(NamedParameterJdbcTemplate.class);
    private final PublicSiteSearch search = new PublicSiteSearch(db);

    @Test
    void privateScopesAndEmptyGlobalRequestsNeverReadPublicTables() {
        assertThat(search.find("NOTE", 1, List.of())).isNull();
        assertThat(search.search(List.of("合同"), "FAVORITE", 3)).isEmpty();
        assertThat(search.search(List.of("  "), null, 3)).isEmpty();
        assertThat(search.search(List.of("合同"), null, 0)).isEmpty();
        verifyNoInteractions(db);
    }

    @Test
    void questionSearchBindsPunctuationLiterallyAndDoesNotReadGradingData() {
        String input = "A%'_x";
        when(db.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        search.search(List.of(input, input), "QUESTION", 2);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(db).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("FROM question", "LOCATE(", "options", "tag_names")
                .doesNotContain(input, "answer", "analysis", " LIKE ");
        assertThat(params.getValue().getValue("t0")).isEqualTo("a%'_x");
        assertThat(params.getValue().hasValue("t1")).isFalse();
    }

    @Test
    void paperScopeRestrictsTheViewpointTableBeforeRankingAndUsesViewpointEntityTags() {
        when(db.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        search.search(List.of("著作权"), "PAPER", 5);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(db).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("WHERE summary_kind IN ('author_abstract','metadata_only') HAVING");
        assertThat(params.getValue().getValue("type")).isEqualTo("VIEWPOINT");
    }

    @Test
    void passagesUseThePositionOfTheMatchRatherThanAlwaysTakingTheIntroduction() {
        assertThat(search.find("VIEWPOINT", 429L, List.of("needle"))).isNull();
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(db).query(sql.capture(), params.capture(), any(RowCallbackHandler.class));
        assertThat(sql.getValue()).contains("FROM scholar_viewpoint", "SUBSTRING(", "LOCATE(:t0,", "content", "source_url")
                .contains("法学论文（已收录正文）", "仅收录题录，未收录全文", "学者观点")
                .doesNotContain("AND summary_kind IN")
                .doesNotContain("needle");
        assertThat(params.getValue().getValue("ids")).isEqualTo(List.of(429L));
        assertThat(params.getValue().getValue("length")).isEqualTo(3600);
    }
}
