package com.lexaid.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学者观点实体
 */
@Data
@TableName("scholar_viewpoint")
public class ScholarViewpoint {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scholarName;
    private String title;
    private String summary;
    private String content;
    private String source;
    private Integer publishYear;
    private String languageCode;
    private String sourceDataset;
    private String sourceItemId;
    private String sourceUrl;
    private String doi;
    private String licenseName;
    private String summaryKind;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
