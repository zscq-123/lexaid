package com.lexaid.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 答题明细 Mapper
 */
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /**
     * 查询用户最近一次为错的题目 id（按最近作答时间倒序）
     */
    @Select("""
            SELECT ar.question_id
            FROM answer_record ar
            WHERE ar.user_id = #{userId} AND ar.is_correct = false
              AND ar.id = (SELECT MAX(ar2.id) FROM answer_record ar2
                           WHERE ar2.user_id = #{userId} AND ar2.is_correct = false
                             AND ar2.question_id = ar.question_id)
            ORDER BY ar.answered_at DESC
            """)
    List<Long> selectLatestWrongQuestionIds(@Param("userId") Long userId);

    /**
     * 查询用户最近一次作答过的题目 id（全部，含答对与未判分）
     */
    @Select("""
            SELECT ar.question_id
            FROM answer_record ar
            WHERE ar.user_id = #{userId}
              AND ar.id = (SELECT MAX(ar2.id) FROM answer_record ar2
                           WHERE ar2.user_id = #{userId} AND ar2.question_id = ar.question_id)
            ORDER BY ar.answered_at DESC
            """)
    List<Long> selectLatestAnsweredQuestionIds(@Param("userId") Long userId);

    /**
     * 每日答题数与正确数
     */
    @Select("""
            SELECT DATE(answered_at) AS d, COUNT(*) AS c,
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS ok
            FROM answer_record
            WHERE user_id = #{userId} AND answered_at >= #{from}
            GROUP BY DATE(answered_at)
            """)
    List<Map<String, Object>> selectDailyAnswer(@Param("userId") Long userId,
                                                @Param("from") LocalDate from);

    /**
     * 按类别统计正确率
     */
    @Select("""
            SELECT q.category AS category, COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct
            FROM answer_record ar JOIN question q ON ar.question_id = q.id
            WHERE ar.user_id = #{userId}
            GROUP BY q.category
            ORDER BY total DESC
            """)
    List<Map<String, Object>> selectCategoryAccuracy(@Param("userId") Long userId);
}
