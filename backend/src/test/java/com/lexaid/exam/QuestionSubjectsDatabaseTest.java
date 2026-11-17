package com.lexaid.exam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.lexaid.exam.dto.ExamSetupRequest;
import com.lexaid.exam.dto.SubjectCount;
import com.lexaid.exam.dto.QuestionScope;
import com.lexaid.exam.dto.QuestionProgress;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Temporary tables shadow existing data on one MySQL connection. */
@EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
class QuestionSubjectsDatabaseTest {
    private SqlSession session;

    @BeforeEach
    void prepareBank() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"));
        var config = new MybatisConfiguration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(QuestionMapper.class);
        config.addMapper(AnswerRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        session = factory.openSession();
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE question(id BIGINT PRIMARY KEY,category VARCHAR(60),difficulty INT,type VARCHAR(20),stem TEXT,options TEXT,answer TEXT,analysis TEXT)");
            sql.execute("CREATE TEMPORARY TABLE question_source(question_id BIGINT,dataset VARCHAR(64))");
            sql.execute("CREATE TEMPORARY TABLE question_quality_review(question_id BIGINT,content_hash CHAR(64))");
            sql.execute("CREATE TEMPORARY TABLE question_legal_subject(question_id BIGINT PRIMARY KEY,discipline VARCHAR(60),subject VARCHAR(60))");
            sql.execute("CREATE TEMPORARY TABLE question_code_book(question_id BIGINT,code_name VARCHAR(64),book_name VARCHAR(64),PRIMARY KEY(question_id,code_name,book_name))");
            sql.execute("CREATE TEMPORARY TABLE answer_record(user_id BIGINT, question_id BIGINT, is_correct BOOLEAN, user_answer TEXT)");
            sql.execute("INSERT INTO question(id,category,difficulty,type) VALUES(1,'法律咨询',3,'MULTIPLE'),(2,'民法',3,'JUDGE'),(3,'旧来源任务',4,'SHORT'),(4,'法律摘要',3,'SHORT'),(5,'刑法',2,'SINGLE')");
            sql.execute("UPDATE question SET stem='教学题干',analysis='根据题目中的关键事实分析适用条件。',options='[{\"key\":\"A\",\"text\":\"选项一\"},{\"key\":\"B\",\"text\":\"选项二\"}]',answer=CASE WHEN type='JUDGE' THEN 'true' ELSE '[\"A\"]' END");
            sql.execute("INSERT INTO question_legal_subject VALUES(1,'民商法','合同法'),(4,'刑法','刑法学')");
            sql.execute("INSERT INTO question_code_book VALUES(1,'民法典','第一编 总则'),(1,'民法典','第三编 合同'),(2,'民法典','第一编 总则')");
            sql.execute("INSERT INTO answer_record VALUES(7,1,1,'[\"A\"]'),(7,2,0,'false'),(7,4,NULL,'书面回答'),(8,3,1,'回答')");
        }
    }

    @AfterEach
    void close() {
        if (session != null) session.close();
    }

    @Test
    void countsSamplingQuestionLabelsAndStatisticsUseTheSameClassification() {
        var mapper = session.getMapper(QuestionMapper.class);
        var counts = mapper.subjectCounts();
        assertThat(counts.stream().mapToLong(SubjectCount::count).sum()).isEqualTo(4);
        assertThat(counts).contains(new SubjectCount("民商法","民法学",3,1),new SubjectCount("刑法","刑法学",2,1));
        assertThat(counts).noneMatch(c -> c.subject().equals("待判定"));
        assertThat(mapper.subjectsOf(List.of())).isEmpty();
        assertThat(mapper.subjectsOf(List.of(1L))).singleElement().satisfies(s -> assertThat(s.subject()).isEqualTo("合同法"));
        assertThat(mapper.classifiedQuestions(List.of(1L))).singleElement().satisfies(q -> {
            assertThat(q.getCategory()).isEqualTo("法律咨询");
            assertThat(q.getDiscipline()).isEqualTo("民商法");
            assertThat(q.getSubject()).isEqualTo("合同法");
        });
        var books = mapper.codeBookCounts();
        assertThat(mapper.booksOf(List.of(1L))).hasSize(2).allMatch(b -> b.codeName().equals("民法典"));
        assertThat(books.stream().filter(b -> b.bookName()==null).mapToLong(b -> b.count()).sum()).isEqualTo(2);
        assertThat(books.stream().filter(b -> b.bookName()!=null).mapToLong(b -> b.count()).sum()).isEqualTo(3);
        var req = new ExamSetupRequest();
        req.setDiscipline("民商法");
        req.setDifficulty(3);
        req.setCodeName("民法典");
        assertThat(mapper.draw(req, null, List.of(), 10)).containsExactlyInAnyOrder(1L,2L);
        assertThat(mapper.draw(req, List.of(2L,3L,4L), List.of(), 10)).containsExactly(2L);
        assertThat(mapper.draw(req, null, List.of(2L), 10)).containsExactly(1L);
        req.setBookName("第三编 合同");
        assertThat(mapper.draw(req, null, List.of(), 10)).containsExactly(1L);
        req.setSubject("侵权责任法");
        assertThat(mapper.draw(req, null, List.of(), 10)).isEmpty();
        var legacy = new ExamSetupRequest();
        legacy.setCategory("法律摘要");
        assertThat(mapper.draw(legacy,null,List.of(),10)).containsExactly(4L);
        var stats = session.getMapper(AnswerRecordMapper.class).selectCategoryAccuracy(7L);
        assertThat(stats).extracting(r -> r.get("category")).containsExactlyInAnyOrder("合同法","民法学");
    }

    @Test
    void progressCountsUniqueAnsweredQuestionsWithinTheSelectedBank() throws Exception {
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO answer_record VALUES(7,1,0,'[]'),(7,1,0,'[ ]'),(7,3,NULL,NULL),"
                    + "(7,3,NULL,CONCAT(CHAR(9),'　',CHAR(10))),(7,5,0,''),"
                    + "(8,1,0,' [ ] '),(8,4,NULL,'[]')");
        }
        var mapper = session.getMapper(QuestionMapper.class);
        var scope = new QuestionScope();
        assertThat(mapper.progress(7L, scope)).isEqualTo(new QuestionProgress(4, 3));
        assertThat(mapper.progress(8L, scope)).isEqualTo(new QuestionProgress(4, 1));
        assertThat(mapper.progress(9L, scope)).isEqualTo(new QuestionProgress(4, 0));
        scope.setCodeName("民法典");
        assertThat(mapper.progress(7L, scope)).isEqualTo(new QuestionProgress(2, 2));
        scope.setBookName("第三编 合同");
        assertThat(mapper.progress(8L, scope)).isEqualTo(new QuestionProgress(1, 0));
        scope.setDiscipline("民商法");
        scope.setSubject("合同法");
        scope.setDifficulty(3);
        assertThat(mapper.progress(7L, scope)).isEqualTo(new QuestionProgress(1, 1));
        scope.setDifficulty(4);
        assertThat(mapper.progress(7L, scope)).isEqualTo(new QuestionProgress(0, 0));
    }

    @Test
    void quarantineAndReviewApplyToCountsSamplingAndCoverageTogether() throws Exception {
        var mapper = session.getMapper(QuestionMapper.class);
        var req = new ExamSetupRequest();
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO question_source VALUES(1,'LawBench')");
            sql.execute("UPDATE question SET analysis='请只给出答案的序号' WHERE id=4");
            sql.execute("UPDATE question SET answer='[\"Z\"]' WHERE id=5");
        }
        assertThat(mapper.draw(req,null,List.of(),50)).containsExactly(2L);
        assertThat(mapper.progress(7L,req)).isEqualTo(new QuestionProgress(1,1));
        assertThat(mapper.qualityCounts()).extracting(QuestionMapper.QualityCount::reason)
                .containsExactlyInAnyOrder("ELIGIBLE","UNCLASSIFIED","TASK_INSTRUCTION","INVALID_CHOICES","DATASET_REVIEW");
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO question_quality_review SELECT q.id," + QuestionQuality.HASH
                    + QuestionSubjects.FROM_QUESTION + " WHERE q.id=1");
        }
        session.clearCache();
        assertThat(mapper.draw(req,null,List.of(),50)).containsExactlyInAnyOrder(1L,2L);
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE question SET analysis='修改后的解析需要重新复核' WHERE id=1");
        }
        session.clearCache();
        assertThat(mapper.draw(req,null,List.of(),50)).containsExactly(2L);
    }

    @Test
    void malformedChoicesCannotBeReleasedAsPractice() throws Exception {
        var mapper = session.getMapper(QuestionMapper.class);
        for (String options : List.of("not json", "[]", "[{\"key\":\"A\"},{\"key\":\"B\"}]",
                "[{\"key\":\"A\",\"text\":\"一\"},{\"key\":\"A\",\"text\":\"二\"}]")) {
            try (var sql = session.getConnection().prepareStatement("UPDATE question SET options=? WHERE id=5")) {
                sql.setString(1,options); sql.executeUpdate();
            }
            session.clearCache();
            assertThat(mapper.eligibleIds(List.of(5L))).isEmpty();
        }
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE question SET answer='[\"A\",\"A\"]' WHERE id=1");
            sql.execute("UPDATE question SET answer='TRUE' WHERE id=2");
        }
        session.clearCache();
        assertThat(mapper.eligibleIds(List.of(1L,2L))).isEmpty();
    }

    @Test
    void importedExplanationsRequireReviewAndContractWordsDoNotDetermineCriminalClassification() throws Exception {
        var mapper = session.getMapper(QuestionMapper.class);
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO question_source VALUES(4,'LeCoQA')");
            sql.execute("UPDATE question SET stem='国有单位负责人签订合同严重失职，致使国家利益遭受重大损失，如何评价？' WHERE id=4");
        }
        assertThat(mapper.eligibleIds(List.of(4L))).isEmpty();
        assertThat(mapper.qualityCounts()).extracting(QuestionMapper.QualityCount::reason).contains("DATASET_REVIEW");
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO question_quality_review SELECT q.id," + QuestionQuality.HASH
                    + QuestionSubjects.FROM_QUESTION + " WHERE q.id=4");
        }
        session.clearCache();
        assertThat(mapper.eligibleIds(List.of(4L))).containsExactly(4L);
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE question SET answer='内容更新，旧复核失效' WHERE id=4");
        }
        session.clearCache();
        assertThat(mapper.eligibleIds(List.of(4L))).isEmpty();
    }
}
