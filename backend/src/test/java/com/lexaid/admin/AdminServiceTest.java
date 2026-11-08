package com.lexaid.admin;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminServiceTest {
    private final NamedParameterJdbcTemplate db = mock(NamedParameterJdbcTemplate.class);
    private final AdminService service = new AdminService(db);

    @Test
    void rejectsUnknownFiltersBeforeBuildingSql() {
        assertThrows(BizException.class, () -> service.users("", "OWNER", "all", "created", "desc", 1, 20));
        assertThrows(BizException.class, () -> service.users("", "USER", "online", "created", "desc", 1, 20));
        assertThrows(BizException.class, () -> service.users("", "USER", "all", "password", "desc", 1, 20));
        verifyNoInteractions(db);
    }
}
