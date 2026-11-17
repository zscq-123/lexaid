package com.lexaid.auth;

import com.lexaid.auth.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserProfileService profiles = new UserProfileService(jdbc);

    @Test
    void missingProfileUsesSafeDefaults() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());

        var profile = profiles.get(42L);

        assertEquals("UNSPECIFIED", profile.gender());
        assertEquals("UNDECIDED", profile.examGoal());
        assertNull(profile.phone());
        assertNull(profile.legalExamYear());
    }

    @Test
    void saveNormalizesOptionalTextBeforeUpsert() {
        var request = new ProfileUpdateRequest();
        request.setNickname("学习者");
        request.setGender("FEMALE");
        request.setPhone(" 13800138000 ");
        request.setExamGoal("BOTH");
        request.setLegalExamAttempts(1);
        request.setMasterExamAttempts(2);
        request.setLegalExamYear(2027);
        request.setMasterExamYear(2028);
        request.setLegalExamStage(" ");
        request.setMasterExamStage("SPRINT");
        request.setPreparationStatus(null);

        profiles.save(42L, request);

        verify(jdbc).update(startsWith("INSERT INTO user_profile"), eq(42L), eq("FEMALE"), eq("13800138000"),
                eq("BOTH"), eq(1), eq(2), eq(2027), eq(2028), isNull(), eq("SPRINT"), isNull());
    }
}
