package com.lexaid.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexaid.exam.dto.QuestionCategoryCount;
import com.lexaid.exam.dto.CodeBookCount;
import com.lexaid.exam.dto.ExamSetupRequest;
import com.lexaid.exam.dto.QuestionSubject;
import com.lexaid.exam.dto.QuestionScope;
import com.lexaid.exam.dto.QuestionProgress;
import com.lexaid.exam.dto.QuestionBook;
import com.lexaid.exam.dto.SubjectCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目 Mapper
 */
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("SELECT q.id,q.type,q.stem," + QuestionSubjects.SUBJECT + " AS subject " +
            QuestionSubjects.FROM_QUESTION + " WHERE q.stem LIKE CONCAT('%',#{keyword},'%') " +
            "OR " + QuestionSubjects.SUBJECT + "=#{keyword} OR " + QuestionSubjects.DISCIPLINE + "=#{keyword} LIMIT 10")
    List<Question> searchForLibrary(@Param("keyword") String keyword);

    @Select("""
            SELECT category, difficulty, COUNT(*) AS count
            FROM question
            WHERE category REGEXP '[^[:space:]]'
            GROUP BY category, difficulty
            ORDER BY category, difficulty
            """)
    List<QuestionCategoryCount> categoryCounts();

    @Select("SELECT " + QuestionSubjects.DISCIPLINE + " AS discipline, " + QuestionSubjects.SUBJECT +
            " AS subject, q.difficulty, COUNT(*) AS count " + QuestionSubjects.FROM_QUESTION +
            " GROUP BY 1,2,3 ORDER BY 1,2,3")
    List<SubjectCount> subjectCounts();

    // Each verified link contributes once to its book and once to the code subtotal.
    @Select("SELECT " + QuestionSubjects.DISCIPLINE + " AS discipline, " + QuestionSubjects.SUBJECT +
            " AS subject, b.code_name AS codeName, IF(levels.by_book=1,b.book_name,NULL) AS bookName, " +
            " q.difficulty, COUNT(DISTINCT q.id) AS count " + QuestionSubjects.FROM_QUESTION +
            " JOIN question_code_book b ON b.question_id=q.id CROSS JOIN (SELECT 0 AS by_book UNION ALL SELECT 1) levels " +
            " GROUP BY 1,2,3,4,5 ORDER BY 1,2,3,4,5")
    List<CodeBookCount> codeBookCounts();

    String IDS = "<foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach>";

    @Select("<script>SELECT q.id AS questionId, " + QuestionSubjects.DISCIPLINE + " AS discipline, " +
            QuestionSubjects.SUBJECT + " AS subject " + QuestionSubjects.FROM_QUESTION +
            "<choose><when test='ids != null and !ids.isEmpty()'> WHERE q.id IN " + IDS +
            "</when><otherwise> WHERE 1=0 </otherwise></choose></script>")
    List<QuestionSubject> subjectsOf(@Param("ids") List<Long> ids);

    @Select("<script>SELECT question_id AS questionId, code_name AS codeName, book_name AS bookName " +
            "FROM question_code_book WHERE question_id IN " + IDS + " ORDER BY question_id,code_name,book_name</script>")
    List<QuestionBook> booksOf(@Param("ids") List<Long> ids);

    @Select("<script>SELECT q.*, " + QuestionSubjects.DISCIPLINE + " AS discipline, " +
            QuestionSubjects.SUBJECT + " AS subject " + QuestionSubjects.FROM_QUESTION + " WHERE q.id IN " + IDS + "</script>")
    List<Question> classifiedQuestions(@Param("ids") List<Long> ids);

    String POOL_FILTERS =
            "<if test=\"req.category != null and req.category != ''\"> AND q.category=#{req.category} </if>" +
            "<if test='req.difficulty != null'> AND q.difficulty=#{req.difficulty} </if>" +
            "<if test=\"req.discipline != null and req.discipline != ''\"> AND " +
                QuestionSubjects.DISCIPLINE + "=#{req.discipline} </if>" +
            "<if test=\"req.subject != null and req.subject != ''\"> AND " +
                QuestionSubjects.SUBJECT + "=#{req.subject} </if>" +
            "<if test=\"req.codeName != null and req.codeName != ''\"> AND EXISTS (SELECT 1 FROM question_code_book b " +
                "WHERE b.question_id=q.id AND b.code_name=#{req.codeName} " +
                "<if test=\"req.bookName != null and req.bookName != ''\">AND b.book_name=#{req.bookName}</if>) </if>";

    // Each submitted question appears at most once in the user's set, regardless of grading state.
    // A cleared multiple-choice answer is stored as []; the same literal can be a written answer.
    @Select("<script>SELECT COUNT(*) AS total, COUNT(done.question_id) AS answered " + QuestionSubjects.FROM_QUESTION +
            " LEFT JOIN (SELECT question_id, MAX(REGEXP_REPLACE(user_answer,'[[:space:]]+','') != '[]') AS has_choice " +
            " FROM answer_record WHERE user_id=#{userId} AND user_answer REGEXP '[^[:space:]]' GROUP BY question_id) done " +
            " ON done.question_id=q.id AND (q.type != 'MULTIPLE' OR done.has_choice=1) " +
            "<where>" + POOL_FILTERS + "</where></script>")
    QuestionProgress progress(@Param("userId") Long userId, @Param("req") QuestionScope scope);

    @Select("<script>SELECT q.id " + QuestionSubjects.FROM_QUESTION + "<where>" + POOL_FILTERS +
            "<if test='ids != null'> AND q.id IN " + IDS + " </if>" +
            "<if test='excluded != null and !excluded.isEmpty()'> AND q.id NOT IN " +
                "<foreach collection='excluded' item='id' open='(' close=')' separator=','>#{id}</foreach> </if>" +
            "</where> ORDER BY RAND() LIMIT #{limit}</script>")
    List<Long> draw(@Param("req") ExamSetupRequest req, @Param("ids") List<Long> ids,
                   @Param("excluded") List<Long> excluded, @Param("limit") int limit);
}
