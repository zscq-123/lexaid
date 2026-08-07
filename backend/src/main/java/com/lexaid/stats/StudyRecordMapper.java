package com.lexaid.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学习记录 Mapper
 */
public interface StudyRecordMapper extends BaseMapper<StudyRecord> {

    /**
     * 累计学习时长（秒）
     */
    @Select("SELECT COALESCE(SUM(duration_sec), 0) FROM study_record WHERE user_id = #{userId}")
    Integer selectTotalDurationSec(@Param("userId") Long userId);

    /**
     * 有学习记录/作答的日期
     */
    @Select("""
            SELECT DISTINCT DATE(created_at) FROM study_record
            WHERE user_id = #{userId} ORDER BY 1 DESC
            """)
    List<LocalDate> selectActivityDates(@Param("userId") Long userId);

    /**
     * 每日学习时长（秒）
     */
    @Select("""
            SELECT DATE(created_at) AS d, SUM(duration_sec) AS s
            FROM study_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY DATE(created_at)
            """)
    List<Map<String, Object>> selectDailyStudy(@Param("userId") Long userId,
                                                @Param("from") LocalDate from);
}
