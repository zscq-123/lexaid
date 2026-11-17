package com.lexaid.ai.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="LEXAID_SEARCH_TEST_DB",matches=".+")
class ExactProvisionDatabaseTest {
    @Test
    void exactArticlePreservesVersionsDeduplicatesCopiesAndNeverPadsWithOtherStatutes() throws Exception {
        try (var conn=DriverManager.getConnection(System.getenv("LEXAID_SEARCH_TEST_DB"),
                System.getenv("LEXAID_SEARCH_TEST_USER"),System.getenv("LEXAID_SEARCH_TEST_PASSWORD"))) {
            var db=new JdbcTemplate(new SingleConnectionDataSource(conn,true));
            db.execute(db.queryForMap("SHOW CREATE TABLE legal_provision").get("Create Table").toString()
                    .replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
            db.execute("CREATE TEMPORARY TABLE entity_tag(target_type VARCHAR(30),target_id BIGINT,tag_id BIGINT)");
            db.execute("CREATE TEMPORARY TABLE tag(id BIGINT,name VARCHAR(100))");
            db.update("""
                    INSERT INTO legal_provision(id,law_name,article_no,category,content,effective_date,promulgation_date) VALUES
                    (1,'中华人民共和国刑法','第二十条','刑法','版本甲','2021-03-01','2020-12-26'),
                    (2,'中华人民共和国刑法','第二十条','刑法','版本甲','2021-03-01','2020-12-26'),
                    (3,'中华人民共和国刑法','第二十条','刑法','版本乙','1997-10-01','1997-03-14'),
                    (4,'中华人民共和国民法典','第二十条','民法','不相关内容','2021-01-01','2020-05-28'),
                    (5,'中华人民共和国刑法','第二十一条','刑法','不相关条款','2021-03-01','2020-12-26')
                    """);
            var search=new PublicSiteSearch(new NamedParameterJdbcTemplate(db));
            for (String query: List.of("《刑法》第20条","刑法第二十条","查询《中华人民共和国刑法》第20条")) {
                var hits=search.search(SiteQueryPlanner.local(query,null).terms(),null,14);
                assertThat(hits).hasSize(2).allMatch(h -> h.title().equals("中华人民共和国刑法 第二十条"));
                assertThat(hits).extracting(h -> h.id()).containsExactly(1L,3L);
                assertThat(hits.get(0).summary()).contains("2021-03-01","2020-12-26","未经人工核验");
            }
            assertThat(search.search(SiteQueryPlanner.local("《刑法》第999条",null).terms(),null,14)).isEmpty();
            assertThat(search.search(SiteQueryPlanner.local("《不存在法》第20条",null).terms(),null,14)).isEmpty();
            assertThat(search.search(List.of("刑法","第二十条","2020年版"),"PROVISION",14))
                    .extracting(h -> h.id()).containsExactly(1L);
            assertThat(search.search(List.of("刑法","第二十条","2026年版"),"PROVISION",14)).isEmpty();
        }
    }
}
