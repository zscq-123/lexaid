package com.lexaid.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexaid.exam.dto.QuestionCategoryCount;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目 Mapper
 */
public interface QuestionMapper extends BaseMapper<Question> {

    @Select("""
            SELECT category, difficulty, COUNT(*) AS count
            FROM question
            WHERE category REGEXP '[^[:space:]]'
            GROUP BY category, difficulty
            ORDER BY category, difficulty
            """)
    List<QuestionCategoryCount> categoryCounts();
}
