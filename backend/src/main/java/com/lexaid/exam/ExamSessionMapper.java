package com.lexaid.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 答题会话 Mapper
 */
public interface ExamSessionMapper extends BaseMapper<ExamSession> {
    @Select("SELECT * FROM exam_session WHERE id = #{id} FOR UPDATE")
    ExamSession selectByIdForUpdate(@Param("id") long id);
}
