package com.lexaid.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 法条实体
 */
@Data
@TableName("legal_provision")
public class Provision {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String lawName;
    private String category;
    private String chapter;
    private String articleNo;
    private String content;
    private LocalDate effectiveDate;
    private String issuingAuthority;
    private String jurisdiction;
    private String languageCode;
    private String status;
    private String sourceDataset;
    private String sourceItemId;
    private String sourceUrl;
    private String licenseName;
    private LocalDate promulgationDate;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
