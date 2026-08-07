package com.lexaid.exam;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答题会话实体
 */
@Data
@TableName("exam_session")
public class ExamSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String config;
    private Integer score;
    private Integer total;
    private Integer correct;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSec;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
