package com.lexaid.exam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB", matches=".+")
class PracticeStatisticsDatabaseTest {
    @Test
    void historicalBlankRowsNeverCreateOrEraseWrongAnswersAndAllCountsReconcile() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"),System.getenv("LEXAID_SEARCH_TEST_PASSWORD"));
        var config = new MybatisConfiguration(new Environment("test",new JdbcTransactionFactory(),source));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(AnswerRecordMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            // All fixture mutations roll back, including when an assertion fails.
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("INSERT INTO `user` (username,password) VALUES (UUID(),'fixture')");
                long user;
                try (var rs=sql.executeQuery("SELECT LAST_INSERT_ID()")) { rs.next(); user=rs.getLong(1); }
                sql.execute("INSERT INTO exam_session(user_id,config) VALUES ("+user+",'{}')");
                long exam;
                try (var rs=sql.executeQuery("SELECT LAST_INSERT_ID()")) { rs.next(); exam=rs.getLong(1); }
                String insert="INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,answered_at) VALUES ("+user+","+exam+",";
                sql.execute(insert+"1,NULL,0,NOW()),("+user+","+exam+",2,'　 ',0,NOW()),("+user+","+exam+",15,' [ ] ',0,NOW())");
                var mapper=session.getMapper(AnswerRecordMapper.class);
                assertThat(mapper.totals(user)).isEqualTo(new AnswerRecordMapper.Totals(0,0,0,0,3));
                assertThat(mapper.selectLatestWrongQuestionIds(user)).isEmpty();
                assertThat(mapper.selectLatestAnsweredQuestionIds(user)).isEmpty();
                sql.execute(insert+"1,'B',0,NOW()),("+user+","+exam+",1,'',0,NOW()),("+user+","+exam+",2,'A',1,NOW()),("+user+","+exam+",27,'书面回答',NULL,NOW())");
                session.clearCache();
                assertThat(mapper.selectLatestWrongQuestionIds(user)).containsExactly(1L);
                assertThat(mapper.latestAnswer(user,1L).getUserAnswer()).isEqualTo("B");
                assertThat(mapper.totals(user)).isEqualTo(new AnswerRecordMapper.Totals(3,3,1,2,4));
                assertThat(mapper.selectLatestAnsweredQuestionIds(user)).hasSize(3);
                assertThat(mapper.selectDailyAnswer(user,java.time.LocalDate.now())).singleElement()
                        .satisfies(row -> assertThat(((Number)row.get("c")).longValue()).isEqualTo(3));
                sql.execute(insert+"1,'A',1,NOW())");
                session.clearCache();
                assertThat(mapper.selectLatestWrongQuestionIds(user)).isEmpty();
                assertThat(mapper.totals(user)).isEqualTo(new AnswerRecordMapper.Totals(4,3,2,3,4));
            } finally { session.rollback(true); }
        }
    }
}
