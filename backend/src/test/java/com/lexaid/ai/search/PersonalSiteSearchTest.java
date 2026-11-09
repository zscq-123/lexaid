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
                long question = insert(jdbc, "INSERT INTO question(type,stem,answer,category) VALUES ('SINGLE','查阅试题','secret','测试任务标签')");
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
    private static long insert(JdbcTemplate jdbc,String query,Object... args) {
        jdbc.update(query,args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
}
