package com.lexaid.auth.dto;

import java.time.LocalDate;

/**
 * 用户信息视图
 */
public record UserVO(Long id, String username, String nickname, String role, String avatarVersion,
                     String gender, String phone, String examGoal,
                     Integer legalExamAttempts, Integer masterExamAttempts,
                     Integer legalExamYear, Integer masterExamYear,
                     LocalDate legalExamDate, LocalDate masterExamDate,
                     String legalExamStage, String masterExamStage, String preparationStatus) {}
