package com.lexaid.exam;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目实体
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private String category;
    @TableField(exist = false)
    private String discipline;
    @TableField(exist = false)
    private String subject;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
