package com.lexaid.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 案例实体
 */
@Data
@TableName("case_info")
public class CaseInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String caseNo;
    private String court;
    private LocalDate judgeDate;
    private String caseType;
    private String summary;
    private String holding;
    @TableField("`procedure`")
    private String procedure;
    private String content;
    private String jurisdiction;
    private String languageCode;
    private String status;
    private String sourceDataset;
    private String sourceItemId;
    private String sourceUrl;
    private String licenseName;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
