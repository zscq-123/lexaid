package com.lexaid.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import com.lexaid.stats.StudyRecordService;
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
                insert(db,"INSERT INTO answer_record(user_id,session_id,question_id,is_correct) VALUES (?,?,1,0)",alice,exam);
                Question original = new Question();
                original.setStem("关于正当防卫，下列说法正确的是？");
                original.setAnswer("[\"A\"]");
                original.setAnalysis("正当防卫明显超过必要限度造成重大损害的，应当负刑事责任，但是应当减轻或者免除处罚。");
                QuestionMapper questions = mock(QuestionMapper.class);
                when(questions.classifiedQuestions(List.of(1L))).thenReturn(List.of(original));
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
                insert(db,"INSERT INTO answer_record(user_id,session_id,question_id,is_correct) VALUES (?,?,1,1)",alice,exam);
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
}
