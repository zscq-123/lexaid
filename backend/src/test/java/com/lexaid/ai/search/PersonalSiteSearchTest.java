package com.lexaid.ai.search;

import com.lexaid.admin.AdminService;
import com.lexaid.community.friends.FriendService;
import com.lexaid.stats.StatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PersonalSiteSearchTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
    void readsOnlyCurrentUsersOwnedRecordsAndVisiblePosts() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                var source = new SingleConnectionDataSource(conn, true);
                var jdbc = new JdbcTemplate(source);
                var search = new PersonalSiteSearch(new NamedParameterJdbcTemplate(source), mock(StatsService.class), new FriendService(jdbc));
                String unique = UUID.randomUUID().toString().substring(0, 8);
                long alice = insert(jdbc, "INSERT INTO `user` (username,password,nickname) VALUES (?,?,?)", "alice"+unique, "fixture", "甲"+unique);
                long bob = insert(jdbc, "INSERT INTO `user` (username,password,nickname) VALUES (?,?,?)", "bob"+unique, "fixture", "乙"+unique);

                long note = insert(jdbc, "INSERT INTO note(user_id,target_type,target_id,content) VALUES (?,'QUESTION',1,?)", alice, unique);
                long folder = insert(jdbc,"INSERT INTO favorite_folder(user_id,name) VALUES (?,?)",alice,unique);
                long favorite = insert(jdbc,"INSERT INTO favorite(user_id,target_type,target_id,folder_id) VALUES (?,'QUESTION',1,?)",alice,folder);
                long activity = insert(jdbc,"INSERT INTO study_record(user_id,action_type,detail) VALUES (?,'SEARCH',?)",alice,unique);
                long exam = insert(jdbc,"INSERT INTO exam_session(user_id,config,end_time,total) VALUES (?,JSON_OBJECT(),NOW(),1)",alice);
                insert(jdbc,"INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points) VALUES (?,?,1,?,0,0)", alice,exam,unique);

                for (var ref : List.of(new Source("NOTE", note), new Source("FAVORITE",favorite), new Source("FOLDER",folder),
                        new Source("STUDY",activity), new Source("EXAM",exam), new Source("WRONG",1L))) {
                    assertThat(search.find(alice,ref.type,ref.id,List.of(unique))).isNotNull();
                    assertThat(search.search(alice,List.of(unique),ref.type,10)).extracting("id").contains(ref.id);
                    assertThat(search.find(bob,ref.type,ref.id,List.of())).isNull();
                    assertThat(search.search(bob,List.of(unique),ref.type,10)).isEmpty();
                }
                long post = insert(jdbc,"INSERT INTO community_post(author_id,caption,visibility) VALUES (?,'测试','FRIENDS')",alice);
                insert(jdbc,"INSERT INTO community_post_comment(post_id,author_id,content) VALUES (?,?,?)",post,alice,unique);
                assertThat(search.search(bob,List.of(unique),"POST",5)).isEmpty();
                assertThat(search.find(bob,"POST",post,List.of())).isNull();
                jdbc.update("INSERT INTO community_friend_pair(user_low,user_high,request_id,requester_id,state) VALUES (?,?,?,?,'ACCEPTED')",
                        Math.min(alice,bob),Math.max(alice,bob),UUID.randomUUID().toString(),alice);
                assertThat(search.search(bob,List.of(unique),"FRIEND_POST",5)).singleElement()
                        .satisfies(hit->assertThat(hit.summary()).contains(unique));
                assertThat(search.find(bob,"FRIEND",alice,List.of())).isNotNull();
                jdbc.update("DELETE FROM community_friend_pair WHERE user_low=? AND user_high=?",Math.min(alice,bob),Math.max(alice,bob));
                assertThat(search.find(bob,"POST",post,List.of(unique))).isNull();
                assertThat(search.find(bob,"FRIEND",alice,List.of())).isNull();
                assertThat(search.search(alice,List.of(),"MY_POST",5)).extracting("id").contains(post);
                jdbc.update("DELETE FROM community_post WHERE id=?",post);
                assertThat(search.find(alice,"POST",post,List.of())).isNull();
                assertThat(search.search(alice,List.of(),null,5)).isEmpty();
                assertThat(search.search(alice,List.of(),"MEMBER",5)).isEmpty();
                assertThat(search.find(alice,"STATS",bob,List.of())).isNull();
                assertThat(search.search(alice,List.of(unique),null,12)).isNotEmpty();
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
    void questionsAndReportsSearchAndShowTheirLawClassification() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                var source = new SingleConnectionDataSource(conn, true);
                var jdbc = new JdbcTemplate(source);
                var db = new NamedParameterJdbcTemplate(source);
                String unique = UUID.randomUUID().toString().substring(0, 8);
                String topic = "合同法测试" + unique;
                String book = "第三编 合同 " + unique;
                long user = insert(jdbc, "INSERT INTO `user` (username,password) VALUES (?,?)", "subjects"+unique, "fixture");
                long question = insert(jdbc, "INSERT INTO question(type,stem,options,answer,analysis,category) VALUES ('SINGLE','查阅试题','[{\"key\":\"A\",\"text\":\"一\"},{\"key\":\"B\",\"text\":\"二\"}]','[\"A\"]','secret教学解析','测试任务标签')");
                jdbc.update("INSERT INTO question_legal_subject(question_id,discipline,subject) VALUES (?,'民商法',?)", question, topic);
                jdbc.update("INSERT INTO question_code_book(question_id,code_name,book_name) VALUES (?,'中华人民共和国民法典',?)", question, book);
                long exam = insert(jdbc, "INSERT INTO exam_session(user_id,config,end_time,total) VALUES (?,JSON_OBJECT('codeName','中华人民共和国民法典','bookName',?),NOW(),1)", user, book);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points) VALUES (?,?,?,'B',0,0)", user, exam, question);

                var site = new PublicSiteSearch(db);
                for (String term : List.of(topic, book)) {
                    assertThat(site.search(List.of(term), "QUESTION", 5)).singleElement().satisfies(hit -> {
                        assertThat(hit.id()).isEqualTo(question);
                        assertThat(hit.summary()).contains("民商法", topic, book).doesNotContain("测试任务标签", "secret");
                    });
                }
                assertThat(new SiteCatalog(jdbc).find(user, 5).summary()).contains("民商法 / " + topic + " 1");
                var personal = new PersonalSiteSearch(db, mock(StatsService.class), new FriendService(jdbc));
                assertThat(personal.search(user, List.of(topic), "WRONG", 5)).singleElement()
                        .satisfies(hit -> assertThat(hit.summary()).contains("民商法", topic).doesNotContain("测试任务标签"));
                assertThat(personal.search(user, List.of(topic), "EXAM", 5)).singleElement().satisfies(hit -> {
                    assertThat(hit.title()).contains("中华人民共和国民法典", book);
                    assertThat(hit.summary()).contains("民商法", topic);
                });
                assertThat(new AdminService(db).user(user).recentActivities()).extracting(AdminService.RecentActivity::title)
                        .contains("完成" + topic + "题目");
            } finally {
                conn.rollback();
            }
        }
    }

    private record Source(String type,long id) {}

    @Test
    @EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
    void quarantineHidesTeachingMaterialFromHistoryAndSearchMatches() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                var source = new SingleConnectionDataSource(conn, true);
                var jdbc = new JdbcTemplate(source);
                var search = new PersonalSiteSearch(new NamedParameterJdbcTemplate(source), mock(StatsService.class), new FriendService(jdbc));
                String unique = UUID.randomUUID().toString().substring(0, 8);
                String reference = "待隐藏参考" + unique;
                String analysis = "待隐藏解析" + unique;
                String feedback = "待隐藏反馈" + unique;
                String ownAnswer = "我的历史作答" + unique;
                long user = insert(jdbc, "INSERT INTO `user`(username,password) VALUES (?,?)", "review" + unique, "fixture");
                long question = insert(jdbc, "INSERT INTO question(type,stem,answer,analysis,category) VALUES ('SHORT',?,?,?,'民法')",
                        "审查测试题" + unique, reference, analysis);
                long exam = insert(jdbc, "INSERT INTO exam_session(user_id,config,end_time,total,score) VALUES (?,JSON_OBJECT(),NOW(),1,3)", user);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points,grading_feedback) VALUES (?,?,?,?,0,3,?)",
                        user, exam, question, ownAnswer, feedback);
                for (var ref : List.of(new Source("WRONG", question), new Source("EXAM", exam))) {
                    assertThat(search.find(user, ref.type, ref.id, List.of()).summary()).contains(reference, analysis, feedback, ownAnswer);
                    for (String hidden : List.of(reference, analysis, feedback)) {
                        assertThat(search.search(user, List.of(hidden), ref.type, 10)).extracting("id").contains(ref.id);
                    }
                }
                jdbc.update("UPDATE question SET analysis=? WHERE id=?", "请只输出任务结果 " + analysis, question);
                for (var ref : List.of(new Source("WRONG", question), new Source("EXAM", exam))) {
                    assertThat(search.find(user, ref.type, ref.id, List.of()).summary())
                            .contains(ownAnswer, "题目已更新或正在复核", "不能作为当前答案依据", "历史得分", "3")
                            .doesNotContain(reference, analysis, feedback, "请只输出");
                    for (String hidden : List.of(reference, analysis, feedback)) {
                        assertThat(search.search(user, List.of(hidden), ref.type, 10)).isEmpty();
                    }
                    assertThat(search.search(user, List.of(ownAnswer), ref.type, 10)).extracting("id").contains(ref.id);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
    void referenceVisibilityDependsOnEachAttemptRelativeToTheQuestionRevision() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                var source = new SingleConnectionDataSource(conn, true);
                var jdbc = new JdbcTemplate(source);
                var search = new PersonalSiteSearch(new NamedParameterJdbcTemplate(source), mock(StatsService.class), new FriendService(jdbc));
                String unique = UUID.randomUUID().toString().substring(0, 8);
                String revisedAnswer = "修订后参考" + unique;
                String revisedAnalysis = "修订后解析" + unique;
                String oldFeedback = "旧评分反馈" + unique;
                String newFeedback = "新评分反馈" + unique;
                long user = insert(jdbc, "INSERT INTO `user`(username,password) VALUES (?,?)", "revision" + unique, "fixture");
                long question = insert(jdbc, "INSERT INTO question(type,stem,answer,analysis,category,updated_at) VALUES ('SHORT',?,'原答案','原解析','民法','2026-01-01 00:00:00')", "修订测试" + unique);
                long oldExam = insert(jdbc, "INSERT INTO exam_session(user_id,config,end_time,total) VALUES (?,JSON_OBJECT(),NOW(),1)", user);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points,grading_feedback,answered_at) VALUES (?,?,?,'修订前的个人答案',0,3,?,'2026-01-01 01:00:00')",
                        user, oldExam, question, oldFeedback);
                assertThat(search.find(user, "EXAM", oldExam, List.of()).summary()).contains("原答案", "原解析", oldFeedback);

                jdbc.update("UPDATE question SET answer=?,analysis=?,updated_at='2026-01-02 00:00:00' WHERE id=?", revisedAnswer, revisedAnalysis, question);
                for (var ref : List.of(new Source("WRONG", question), new Source("EXAM", oldExam))) {
                    assertThat(search.find(user, ref.type, ref.id, List.of()).summary())
                            .contains("修订前的个人答案", "题目已更新或正在复核", "不能作为当前答案依据", "历史得分", "3")
                            .doesNotContain(revisedAnswer, revisedAnalysis, oldFeedback);
                }

                long newExam = insert(jdbc, "INSERT INTO exam_session(user_id,config,end_time,total) VALUES (?,JSON_OBJECT(),NOW(),1)", user);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points,grading_feedback,answered_at) VALUES (?,?,?,'修订后的个人答案',0,4,?,'2026-01-03 00:00:00')",
                        user, newExam, question, newFeedback);
                assertThat(search.find(user, "EXAM", oldExam, List.of()).summary())
                        .doesNotContain(revisedAnswer, revisedAnalysis, oldFeedback);
                for (var ref : List.of(new Source("WRONG", question), new Source("EXAM", newExam))) {
                    assertThat(search.find(user, ref.type, ref.id, List.of()).summary())
                            .contains(revisedAnswer, revisedAnalysis, newFeedback, "修订后的个人答案")
                            .doesNotContain("题目已更新或正在复核", oldFeedback);
                }
                for (String term : List.of(revisedAnswer, revisedAnalysis, newFeedback)) {
                    assertThat(search.search(user, List.of(term), "EXAM", 10)).extracting("id").containsExactly(newExam);
                }
                assertThat(search.search(user, List.of(oldFeedback), "EXAM", 10)).isEmpty();
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LEXAID_SEARCH_TEST_DB", matches = ".+")
    void examSummaryRecomputesScoresWithoutHistoricalSkippedAnswers() throws Exception {
        try (var conn = DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"), System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            conn.setAutoCommit(false);
            try {
                var source = new SingleConnectionDataSource(conn, true);
                var jdbc = new JdbcTemplate(source);
                var search = new PersonalSiteSearch(new NamedParameterJdbcTemplate(source), mock(StatsService.class), new FriendService(jdbc));
                String unique = UUID.randomUUID().toString().substring(0, 8);
                long user = insert(jdbc, "INSERT INTO `user`(username,password) VALUES (?,?)", "skipped" + unique, "fixture");
                long question = insert(jdbc, "INSERT INTO question(type,stem,options,answer,analysis,category) VALUES ('MULTIPLE',?,'[{\"key\":\"A\",\"text\":\"一\"},{\"key\":\"B\",\"text\":\"二\"}]','[\"A\"]','教学解析','民法')", "统计测试" + unique);
                long exam = insert(jdbc, "INSERT INTO exam_session(user_id,config,end_time,total,score,correct) VALUES (?,JSON_OBJECT(),NOW(),3,999,999)", user);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points) VALUES (?,?,?,'[\"A\"]',1,1)", user, exam, question);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points) VALUES (?,?,?,'[\"B\"]',0,0)", user, exam, question);
                insert(jdbc, "INSERT INTO answer_record(user_id,session_id,question_id,user_answer,is_correct,grade_points) VALUES (?,?,?,'[]',1,1)", user, exam, question);
                assertThat(search.find(user, "EXAM", exam, List.of()).summary())
                        .contains("已得 1 分，当前已评分满分 2 分，答对 1 道", "未作答 / 跳过")
                        .doesNotContain("999");
            } finally {
                conn.rollback();
            }
        }
    }

    private static long insert(JdbcTemplate jdbc,String query,Object... args) {
        jdbc.update(query,args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
}
