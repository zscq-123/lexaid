package com.lexaid.stats;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习记录实体
 */
@Data
@TableName("study_record")
public class StudyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private Integer durationSec;
    private String detail;
    private LocalDateTime createdAt;
}
