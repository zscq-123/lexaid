package com.lexaid.mockexam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DeepSeekClient;
import com.lexaid.ai.DeepSeekGrader;
import com.lexaid.common.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import static com.lexaid.mockexam.MockExam.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB",matches=".+")
class MockExamDatabaseTest {
    Connection conn;
    JdbcTemplate db;
    MockExamService exams;
    MockExamWorker worker;
    DeepSeekGrader grader;
    @BeforeEach void init() throws Exception {
        conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),System.getenv("LEXAID_SEARCH_TEST_USER"),
                System.getenv("LEXAID_SEARCH_TEST_PASSWORD"));
        var source = new SingleConnectionDataSource(conn,true);
        db = new JdbcTemplate(source);
        db.execute("CREATE TEMPORARY TABLE `user` (id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO `user` VALUES (1),(2)");
        try(var in = MockExamDatabaseTest.class.getResourceAsStream("/mock-exams.sql")) {
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE")
                    .replaceAll(",\\s*CONSTRAINT fk_mock_[^\\n]+","");
            for (String sql : ddl.split(";")) if(!sql.isBlank()) db.execute(sql);
        }
        exams = new MockExamService(db,new ObjectMapper());
        grader = mock(DeepSeekGrader.class);
        worker = new MockExamWorker(db,new TransactionTemplate(new DataSourceTransactionManager(source)),exams,grader);
    }
    @AfterEach void close() throws Exception { if (conn!=null) conn.close(); }
    void add(int id,String kind) {
        boolean subj=kind.equals("SHORT");
        db.update("INSERT INTO mock_exam_bank(id,source_type,paper_type,kind,subject,difficulty,stem,options_json,"
                        + "reference_answer,explanation,source_label,source_url) VALUES (?,'MOCK',?,?, '民法',4,?,?,?,?,?,?)",
                id,subj?"SUBJECTIVE":"OBJECTIVE",kind,"测试题 "+id,
                subj?null:"[{\"key\":\"A\",\"text\":\"a\"},{\"key\":\"B\",\"text\":\"b\"}]",
                subj?"案例参考":kind.equals("MULTIPLE")?"AB":"A","只在交卷后展示","来源标签","https://example.com/with-solutions");
    }

    @Test void ownsOneActiveExamAndGradesSavedSnapshotAtTheThirtyMinuteBoundary() {
        for(int i=1;i<=100;i++) add(i,i<=50?"SINGLE":i<=85?"MULTIPLE":"INDEFINITE");
        var start = new Start("MOCK","OBJECTIVE",180);
        var session = exams.start(1,start);
        long id=session.id();
        assertThat(session.items()).hasSize(100).allMatch(i -> i.referenceAnswer()==null && i.explanation()==null && i.sourceUrl()==null);
        assertThat(session.totalPoints()).isEqualTo(150);
        assertThat(exams.start(1,start).id()).isEqualTo(id);
        assertThatThrownBy(() -> exams.session(2,id)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> exams.save(2,id,1,new Draft("A",1))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> exams.submit(1,id)).hasMessageContaining("满30分钟");
        assertThat(exams.save(1,id,1,new Draft("A",1)).accepted()).isTrue();
        assertThat(exams.save(1,id,1,new Draft("A",1)).accepted()).isTrue();
        assertThat(exams.save(1,id,1,new Draft("B",1)).accepted()).isFalse();
        assertThat(exams.save(1,id,51,new Draft("[\"B\",\"A\"]",1)).answer()).isEqualTo("[\"A\",\"B\"]");
        assertThat(exams.save(1,id,86,new Draft("B",1)).accepted()).isTrue();
        assertThat(exams.active(1).items().get(0).answer()).isEqualTo("[\"A\"]");
        db.update("UPDATE mock_exam_bank SET reference_answer='B'");
        db.update("UPDATE mock_exam_session SET started_at=? WHERE id=?",System.currentTimeMillis()-30*60_000,id);
        var result=exams.submit(1,id);
        assertThat(result.score()).isEqualTo(3);
        assertThat(result.items().get(0).referenceAnswer()).isEqualTo("A");
        assertThat(result.items().get(1).gradeStatus()).isEqualTo("BLANK");
        assertThat(exams.save(1,id,1,new Draft("B",2)).accepted()).isFalse();
        assertThat(exams.submit(1,id).score()).isEqualTo(3);
        assertThat(exams.active(1)).isNull();
        assertThat(exams.history(1)).singleElement().satisfies(s -> assertThat(s.items()).isEmpty());
    }

    @Test void timesOutAndRetriesSubjectiveUsingReferenceAndActualQuestionMaximum() {
        for (int i=1;i<=5;i++) add(i,"SHORT");
        var session=exams.start(1,new Start("MOCK","SUBJECTIVE",30));
        long id=session.id();
        exams.save(1,id,2,new Draft("书面答案",1));
        db.update("UPDATE mock_exam_session SET deadline=? WHERE id=?",System.currentTimeMillis()-1000,id);
        worker.expireDue();
        assertThat(exams.save(1,id,2,new Draft("截止后修改",2)).accepted()).isFalse();
        var result=exams.session(1,id);
        assertThat(result.score()).isNull();
        assertThat(result.submittedAt()).isEqualTo(result.deadline());
        assertThat(result.items().get(1).gradeStatus()).isEqualTo("PENDING");
        when(grader.grade(anyList())).thenThrow(new DeepSeekClient.UnavailableException("测试离线"));
        worker.gradeNext();
        assertThat(exams.session(1,id).items().get(1).gradeStatus()).isEqualTo("UNAVAILABLE");
        exams.retry(1,id);
        doAnswer(inv -> {
            List<DeepSeekGrader.Submission> batch=inv.getArgument(0);
            assertThat(batch).singleElement().satisfies(s -> {
                assertThat(s.referenceAnswer()).isEqualTo("案例参考"); assertThat(s.userAnswer()).isEqualTo("书面答案");
                assertThat(s.maxPoints()).isEqualTo(36);
            });
            return Map.of(batch.get(0).questionId(),new DeepSeekGrader.Grade(27,"缺少一处理由"));
        }).when(grader).grade(anyList());
        worker.gradeNext();
        assertThat(exams.session(1,id).score()).isEqualTo(27);
        assertThat(exams.session(1,id).items().get(1).feedback()).isEqualTo("缺少一处理由");
    }
}
