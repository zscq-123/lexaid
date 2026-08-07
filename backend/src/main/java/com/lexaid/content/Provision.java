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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
