package com.lexaid.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 答题明细 Mapper
 */
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    @Select("""
            SELECT session_id FROM answer_record
            WHERE grading_status = 'PENDING' AND (grade_lease_until IS NULL OR grade_lease_until <= NOW())
            ORDER BY id LIMIT 1
            """)
    Long nextGradingSession();

    @Select("""
            SELECT * FROM answer_record WHERE session_id = #{sessionId}
              AND grading_status = 'PENDING' AND (grade_lease_until IS NULL OR grade_lease_until <= NOW())
            ORDER BY id LIMIT 4 FOR UPDATE
            """)
    List<AnswerRecord> nextGradingBatch(@Param("sessionId") long sessionId);

    @Update("UPDATE answer_record SET grade_token = #{token}, grade_lease_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id = #{id}")
    void claimGrade(@Param("id") long id, @Param("token") String token);

    @Update("""
            UPDATE answer_record SET grading_status = #{status}, grade_points = #{points}, grading_feedback = #{feedback},
              is_correct = #{fullCredit}, grade_token = NULL, grade_lease_until = NULL
            WHERE id = #{id} AND grade_token = #{token} AND grading_status = 'PENDING'
            """)
    int finishGrade(@Param("id") long id, @Param("token") String token, @Param("status") String status,
                    @Param("points") Integer points, @Param("feedback") String feedback, @Param("fullCredit") Boolean fullCredit);

    @Update("UPDATE answer_record ar JOIN question q ON q.id=ar.question_id " + QuestionSubjects.JOIN_SUBJECTS
            + " SET ar.grading_status='PENDING',ar.grading_feedback=NULL "
            + "WHERE ar.session_id=#{sessionId} AND ar.grading_status='UNAVAILABLE' AND "
            + AnswerStatus.ANSWERED + " AND " + QuestionQuality.ELIGIBLE)
    void retryUnavailable(@Param("sessionId") long sessionId);

    /**
     * 查询用户最近一次为错的题目 id（按最近作答时间倒序）
     */
    String LATEST_ANSWER = " FROM answer_record ar JOIN question q ON q.id=ar.question_id "
            + "WHERE ar.user_id=#{userId} AND " + AnswerStatus.ANSWERED
            + " AND ar.id=(SELECT MAX(ar2.id) FROM answer_record ar2 WHERE ar2.user_id=ar.user_id "
            + "AND ar2.question_id=ar.question_id AND " + AnswerStatus.NEWER_ANSWERED + ") ";

    @Select("SELECT ar.question_id" + LATEST_ANSWER + " AND ar.is_correct=0 ORDER BY ar.answered_at DESC,ar.id DESC")
    List<Long> selectLatestWrongQuestionIds(@Param("userId") Long userId);

    /**
     * 查询用户最近一次作答过的题目 id（全部，含答对与未判分）
     */
    @Select("SELECT ar.question_id" + LATEST_ANSWER + " ORDER BY ar.answered_at DESC,ar.id DESC")
    List<Long> selectLatestAnsweredQuestionIds(@Param("userId") Long userId);

    @Select("SELECT ar.*" + LATEST_ANSWER + " AND ar.question_id=#{questionId}")
    AnswerRecord latestAnswer(@Param("userId") Long userId, @Param("questionId") Long questionId);

    record Totals(long attempts, long distinctQuestions, long correct, long graded, long skipped) {}

    @Select("SELECT COALESCE(SUM(answered),0) AS attempts, COUNT(DISTINCT IF(answered,question_id,NULL)) AS distinctQuestions, "
            + "COALESCE(SUM(answered AND is_correct=1),0) AS correct, "
            + "COALESCE(SUM(answered AND is_correct IS NOT NULL),0) AS graded, COALESCE(SUM(NOT answered),0) AS skipped "
            + "FROM (SELECT ar.question_id,ar.is_correct,COALESCE((" + AnswerStatus.ANSWERED + "),0) AS answered "
            + "FROM answer_record ar JOIN question q ON q.id=ar.question_id WHERE ar.user_id=#{userId}) records")
    Totals totals(@Param("userId") Long userId);

    /**
     * 每日答题数与正确数
     */
    @Select("""
            SELECT DATE(answered_at) AS d, COUNT(*) AS c,
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS ok
            FROM answer_record ar JOIN question q ON q.id=ar.question_id
            WHERE ar.user_id = #{userId} AND answered_at >= #{from} AND
            """ + AnswerStatus.ANSWERED + """
            GROUP BY DATE(answered_at)
            """)
    List<Map<String, Object>> selectDailyAnswer(@Param("userId") Long userId,
                                                @Param("from") LocalDate from);

    /**
     * 按类别统计正确率
     */
    @Select("SELECT " + QuestionSubjects.DISCIPLINE + " AS discipline, " + QuestionSubjects.SUBJECT +
            " AS category, COUNT(*) AS total, SUM(ar.is_correct = 1) AS correct " +
            " FROM answer_record ar JOIN question q ON ar.question_id=q.id " + QuestionSubjects.JOIN_SUBJECTS +
            " WHERE ar.user_id=#{userId} AND " + AnswerStatus.ANSWERED + " AND ar.is_correct IS NOT NULL GROUP BY 1,2 ORDER BY total DESC,1,2")
    List<Map<String, Object>> selectCategoryAccuracy(@Param("userId") Long userId);
}
