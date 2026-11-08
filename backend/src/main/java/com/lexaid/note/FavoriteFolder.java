package com.lexaid.note;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("favorite_folder")
public class FavoriteFolder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
}
