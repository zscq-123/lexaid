package com.lexaid.auth;

import com.lexaid.auth.dto.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final JdbcTemplate db;

    public record Data(String gender, String phone, String examGoal,
                       Integer legalExamAttempts, Integer masterExamAttempts,
                       Integer legalExamYear, Integer masterExamYear,
                       String legalExamStage, String masterExamStage,
                       String preparationStatus) {
        static Data empty() {
            return new Data("UNSPECIFIED", null, "UNDECIDED", null, null,
                    null, null, null, null, null);
        }
    }

    public Data get(long userId) {
        return db.query("""
                SELECT gender,phone,exam_goal,legal_exam_attempts,master_exam_attempts,
                       legal_exam_year,master_exam_year,legal_exam_stage,master_exam_stage,preparation_status
                FROM user_profile WHERE user_id=?
                """, (rs, row) -> new Data(
                rs.getString("gender"), rs.getString("phone"), rs.getString("exam_goal"),
                nullableInt(rs.getObject("legal_exam_attempts")), nullableInt(rs.getObject("master_exam_attempts")),
                nullableInt(rs.getObject("legal_exam_year")), nullableInt(rs.getObject("master_exam_year")),
                rs.getString("legal_exam_stage"), rs.getString("master_exam_stage"),
                rs.getString("preparation_status")), userId).stream().findFirst().orElseGet(Data::empty);
    }

    public void save(long userId, ProfileUpdateRequest request) {
        db.update("""
                INSERT INTO user_profile(user_id,gender,phone,exam_goal,legal_exam_attempts,master_exam_attempts,
                  legal_exam_year,master_exam_year,legal_exam_stage,master_exam_stage,preparation_status)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE gender=VALUES(gender),phone=VALUES(phone),exam_goal=VALUES(exam_goal),
                  legal_exam_attempts=VALUES(legal_exam_attempts),master_exam_attempts=VALUES(master_exam_attempts),
                  legal_exam_year=VALUES(legal_exam_year),master_exam_year=VALUES(master_exam_year),
                  legal_exam_stage=VALUES(legal_exam_stage),master_exam_stage=VALUES(master_exam_stage),
                  preparation_status=VALUES(preparation_status)
                """, userId, request.getGender(), blankToNull(request.getPhone()), request.getExamGoal(),
                request.getLegalExamAttempts(), request.getMasterExamAttempts(), request.getLegalExamYear(),
                request.getMasterExamYear(), blankToNull(request.getLegalExamStage()),
                blankToNull(request.getMasterExamStage()), blankToNull(request.getPreparationStatus()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer nullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
