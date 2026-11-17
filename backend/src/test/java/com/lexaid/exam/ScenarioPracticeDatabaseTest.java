package com.lexaid.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import com.lexaid.stats.StudyRecordService;
import com.lexaid.exam.dto.QuestionOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScenarioPracticeDatabaseTest {
    @Test
    @EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
    void recordsArePrivateResumableAndDoNotModifyThePublicQuestionBank() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
                String unique = UUID.randomUUID().toString();
                long alice = insert(db,"INSERT INTO `user` (username,password) VALUES (?, 'fixture')","a"+unique);
                long bob = insert(db,"INSERT INTO `user` (username,password) VALUES (?, 'fixture')","b"+unique);
                long exam = insert(db,"INSERT INTO exam_session(user_id,config) VALUES (?, '{}')",alice);
                insert(db,"INSERT INTO answer_record(user_id,session_id,question_id,is_correct,user_answer) VALUES (?,?,1,0,'B')",alice,exam);
                Question original = new Question();
                original.setStem("关于正当防卫，下列说法正确的是？");
                original.setAnswer("[\"A\"]");
                original.setAnalysis("正当防卫明显超过必要限度造成重大损害的，应当负刑事责任，但是应当减轻或者免除处罚。");
                QuestionMapper questions = mock(QuestionMapper.class);
                when(questions.classifiedQuestions(List.of(1L))).thenReturn(List.of(original));
                when(questions.eligibleIds(List.of(1L))).thenReturn(List.of(1L));
                ObjectMapper json = new ObjectMapper();
                var ai = mock(DeepSeekClient.class);
                var studies = mock(StudyRecordService.class);
                var service = new ScenarioPracticeService(db,json,questions,new ScenarioGenerator(ai,json),studies);
                int publicCount = db.queryForObject("SELECT COUNT(*) FROM question",Integer.class);

                var open = service.open(alice,1,null);
                assertThat(open.origin()).isEqualTo("内置练习");
                assertThat(open.stem()).contains("仓库");
                assertThat(open.correctAnswer()).isNull();
                assertThat(open.basis()).isNull();
                assertThat(open.comparison()).isNull();
                assertThat(service.open(alice,1,null)).isEqualTo(open);
                assertThatThrownBy(()->service.open(bob,1,null)).isInstanceOf(BizException.class);
                assertThatThrownBy(()->service.open(alice,1,open.id())).isInstanceOf(BizException.class);
                assertThatThrownBy(()->service.submit(bob,open.id(),"A")).isInstanceOf(BizException.class);
                var result=service.submit(alice,open.id(),"A");
                assertThat(result.selected()).isEqualTo("A");
                assertThat(result.correctAnswer()).isEqualTo("B");
                assertThat(result.comparison()).isNotBlank();
                assertThat(service.submit(alice,open.id(),"B")).isEqualTo(result);
                verify(studies,times(1)).record(eq(alice),eq("ANSWER"),eq("QUESTION"),eq(1L),anyString());

                when(ai.isConfigured()).thenReturn(true);
                when(ai.complete(anyList(),eq(true))).thenReturn("""
                        {"topic":"防卫限度","stem":"临时展馆中，针对仍在进行的轻微侵害，防卫人明显超出制止侵害所需的程度。现已确认造成重大损害，应当考虑哪一判断？",
                        "options":["第一种","第二种","第三种","第四种"],"answer":"C","basis":"明显超过必要限度造成重大损害",
                        "explanation":"需要核对限度和损害两个条件。","comparison":"两道题都考察明显超过必要限度且造成重大损害的条件。"}
                        """);
                var second=service.open(alice,1,open.id());
                assertThat(second.id()).isNotEqualTo(open.id());
                assertThat(second.round()).isEqualTo(2);
                assertThat(service.open(alice,1,open.id())).isEqualTo(second);
                assertThat(service.open(alice,1,null)).isEqualTo(second);
                assertThat(second.origin()).isEqualTo("内置练习");
                service.submit(alice,second.id(),"C");
                var third = service.open(alice,1,second.id());
                assertThat(third.origin()).isEqualTo("AI 生成");
                assertThat(service.open(alice,1,second.id())).isEqualTo(third);
                verify(ai,times(1)).complete(anyList(),eq(true));
                assertThat(db.queryForObject("SELECT COUNT(*) FROM question",Integer.class)).isEqualTo(publicCount);
                insert(db,"INSERT INTO answer_record(user_id,session_id,question_id,is_correct,user_answer) VALUES (?,?,1,0,NULL)",alice,exam);
                assertThat(service.open(alice,1,null).id()).isEqualTo(third.id());
                insert(db,"INSERT INTO answer_record(user_id,session_id,question_id,is_correct,user_answer) VALUES (?,?,1,1,'A')",alice,exam);
                assertThatThrownBy(()->service.open(alice,1,null)).isInstanceOf(BizException.class);
            } finally {
                conn.rollback();
            }
        }
    }

    private static long insert(JdbcTemplate db,String query,Object... params) {
        db.update(query,params);
        return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
    void anExistingScenarioCannotBeSubmittedAfterItsSourceIsQuarantined() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
                var fixture = fixture(db);
                var opened = fixture.service.open(fixture.user, fixture.question, null);
                db.update("UPDATE question SET analysis='请只输出任务结果',updated_at=updated_at WHERE id=?", fixture.question);
                assertThatThrownBy(() -> fixture.service.submit(fixture.user, opened.id(), "A"))
                        .isInstanceOf(BizException.class).hasMessageContaining("复核");
                // Even a stale mapper read must not bypass the predicate on the actual write.
                when(fixture.questions.eligibleIds(List.of(fixture.question))).thenReturn(List.of(fixture.question));
                assertThatThrownBy(() -> fixture.service.submit(fixture.user, opened.id(), "A"))
                        .isInstanceOf(BizException.class);
                assertThat(db.queryForObject("SELECT selected FROM wrong_question_practice WHERE id=?", String.class, opened.id())).isNull();
                verifyNoInteractions(fixture.studies);
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
    void generationDoesNotPersistAnExerciseWhenTheSourceIsQuarantinedWhileWaitingForTheModel() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
                var fixture = fixture(db);
                when(fixture.generator.generate(any(),anyString(),anyInt())).thenAnswer(call -> {
                    db.update("UPDATE question SET analysis='请只输出任务结果',updated_at=updated_at WHERE id=?", fixture.question);
                    return draft();
                });
                assertThatThrownBy(() -> fixture.service.open(fixture.user, fixture.question, null))
                        .isInstanceOf(BizException.class).hasMessageContaining("复核");
                assertThat(db.queryForObject("SELECT COUNT(*) FROM wrong_question_practice WHERE user_id=?", Integer.class, fixture.user)).isZero();
                // Exercise the conditional INSERT as well as the post-generation mapper check.
                when(fixture.questions.eligibleIds(List.of(fixture.question))).thenReturn(List.of(fixture.question));
                assertThatThrownBy(() -> fixture.service.open(fixture.user, fixture.question, null))
                        .isInstanceOf(BizException.class);
                assertThat(db.queryForObject("SELECT COUNT(*) FROM wrong_question_practice WHERE user_id=?", Integer.class, fixture.user)).isZero();
                verify(fixture.generator,times(2)).generate(any(),anyString(),anyInt());
                verifyNoInteractions(fixture.studies);
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
    void correctedSourceBlocksBothPendingAndAnsweredCachedScenarios() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
                var fixture = fixture(db);
                var answered = fixture.service.open(fixture.user, fixture.question, null);
                fixture.service.submit(fixture.user, answered.id(), "A");
                var pending = fixture.service.open(fixture.user, fixture.question, answered.id());
                db.update("UPDATE question SET answer='纠正后的参考答案',analysis='纠正后的完整教学解析',updated_at='2026-01-03 00:00:00' WHERE id=?", fixture.question);
                assertThat(fixture.questions.eligibleIds(List.of(fixture.question))).containsExactly(fixture.question);

                assertThatThrownBy(() -> fixture.service.submit(fixture.user, pending.id(), "A"))
                        .isInstanceOf(BizException.class).hasMessageContaining("已更新");
                assertThatThrownBy(() -> fixture.service.submit(fixture.user, answered.id(), "A"))
                        .isInstanceOf(BizException.class).hasMessageContaining("已更新");
                assertThatThrownBy(() -> fixture.service.open(fixture.user, fixture.question, null))
                        .isInstanceOf(BizException.class);
                assertThatThrownBy(() -> fixture.service.open(fixture.user, fixture.question, answered.id()))
                        .isInstanceOf(BizException.class);
                assertThat(db.queryForObject("SELECT selected FROM wrong_question_practice WHERE id=?", String.class, pending.id())).isNull();
                assertThat(db.queryForObject("SELECT selected FROM wrong_question_practice WHERE id=?", String.class, answered.id())).isEqualTo("A");
                verify(fixture.studies,times(1)).record(eq(fixture.user),eq("ANSWER"),eq("QUESTION"),eq(fixture.question),anyString());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
    void contentCorrectionDuringGenerationPreventsSavingEvenWhenTheQuestionRemainsEligible() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
                var fixture = fixture(db);
                when(fixture.generator.generate(any(),anyString(),anyInt())).thenAnswer(call -> {
                    db.update("UPDATE question SET answer='纠正后的参考答案',analysis='纠正后的完整教学解析',updated_at='2026-01-03 00:00:00' WHERE id=?", fixture.question);
                    return draft();
                });
                assertThatThrownBy(() -> fixture.service.open(fixture.user, fixture.question, null))
                        .isInstanceOf(BizException.class).hasMessageContaining("已更新");
                assertThat(fixture.questions.eligibleIds(List.of(fixture.question))).containsExactly(fixture.question);
                assertThat(db.queryForObject("SELECT COUNT(*) FROM wrong_question_practice WHERE user_id=?", Integer.class, fixture.user)).isZero();
                verify(fixture.generator,times(1)).generate(any(),anyString(),anyInt());
                verifyNoInteractions(fixture.studies);
            } finally {
                conn.rollback();
            }
        }
    }

    private record Fixture(long user, long question, ScenarioPracticeService service, QuestionMapper questions,
                           ScenarioGenerator generator, StudyRecordService studies) {}

    private static Fixture fixture(JdbcTemplate db) {
        long user = insert(db, "INSERT INTO `user`(username,password) VALUES (?,'fixture')", "quarantine" + UUID.randomUUID());
        long question = insert(db, "INSERT INTO question(type,stem,answer,analysis,category,updated_at) VALUES ('SHORT','独立案情分析','参考答案','完整教学解析','民法','2026-01-01 00:00:00')");
        long exam = insert(db, "INSERT INTO exam_session(user_id,config) VALUES (?, '{}')", user);
        insert(db, "INSERT INTO answer_record(user_id,session_id,question_id,is_correct,user_answer,answered_at) VALUES (?,?,?,0,'我的作答','2026-01-02 00:00:00')", user, exam, question);
        Question original = new Question();
        original.setId(question);
        original.setStem("独立案情分析");
        original.setAnswer("参考答案");
        original.setAnalysis("完整教学解析");
        var questions = mock(QuestionMapper.class);
        when(questions.classifiedQuestions(List.of(question))).thenReturn(List.of(original));
        when(questions.eligibleIds(List.of(question))).thenAnswer(call -> db.queryForList("SELECT q.id "
                + QuestionSubjects.FROM_QUESTION + " WHERE q.id=? AND " + QuestionQuality.ELIGIBLE, Long.class, question));
        var generator = mock(ScenarioGenerator.class);
        when(generator.generate(any(),anyString(),anyInt())).thenReturn(draft());
        var studies = mock(StudyRecordService.class);
        return new Fixture(user, question, new ScenarioPracticeService(db,new ObjectMapper(),questions,generator,studies), questions,generator,studies);
    }

    private static ScenarioGenerator.Exercise draft() {
        return new ScenarioGenerator.Exercise("测试", "测试考点", "独立的测试情景",
                List.of(new QuestionOption("A", "第一项"), new QuestionOption("B", "第二项")),
                "A", "完整教学解析", "测试教学解释", "两个情景的对比");
    }
}
