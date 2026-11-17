package com.lexaid.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @NotBlank(message = "请选择性别")
    @Pattern(regexp = "UNSPECIFIED|MALE|FEMALE", message = "性别选项无效")
    private String gender;

    @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{5,19}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请选择备考目标")
    @Pattern(regexp = "UNDECIDED|LEGAL_EXAM|LAW_MASTER|BOTH", message = "备考目标选项无效")
    private String examGoal;

    @Min(value = 0, message = "参与次数不能小于0")
    @Max(value = 99, message = "参与次数不能超过99")
    private Integer legalExamAttempts;

    @Min(value = 0, message = "参与次数不能小于0")
    @Max(value = 99, message = "参与次数不能超过99")
    private Integer masterExamAttempts;

    @Min(value = 2000, message = "参考年份不能早于2000年")
    @Max(value = 2100, message = "参考年份不能晚于2100年")
    private Integer legalExamYear;

    @Min(value = 2000, message = "参考年份不能早于2000年")
    @Max(value = 2100, message = "参考年份不能晚于2100年")
    private Integer masterExamYear;

    @Pattern(regexp = "^$|FOUNDATION|INTENSIVE|SPRINT|EXAM_READY|FINISHED", message = "法考备考阶段选项无效")
    private String legalExamStage;

    @Pattern(regexp = "^$|FOUNDATION|INTENSIVE|SPRINT|EXAM_READY|FINISHED", message = "法硕备考阶段选项无效")
    private String masterExamStage;

    @Pattern(regexp = "^$|NOT_STARTED|PREPARING|PAUSED|COMPLETED", message = "备考状态选项无效")
    private String preparationStatus;
}
