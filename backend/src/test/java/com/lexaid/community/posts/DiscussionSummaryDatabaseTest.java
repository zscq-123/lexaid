package com.lexaid.community.posts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DiscussionModel;
import com.lexaid.ai.search.PersonalSiteSearch;
import com.lexaid.common.BizException;
import com.lexaid.common.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Use a dedicated, migrated test database with no other summary workers. */
@EnabledIfEnvironmentVariable(named="LEXAID_DISCUSSION_TEST_DB",matches=".+")
class DiscussionSummaryDatabaseTest {
    @Test
    void popularPublicDiscussionIsArchivedOnceAndRecheckedOnChanges() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LEXAID_DISCUSSION_TEST_DB"),
                System.getenv("LEXAID_DISCUSSION_TEST_USER"),System.getenv("LEXAID_DISCUSSION_TEST_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);
        var db=new NamedParameterJdbcTemplate(ds);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var posts=new PostService(db);
        var model=mock(DiscussionModel.class);
        when(model.isConfigured()).thenReturn(true);
        var json=new ObjectMapper();
        var summary=new DiscussionSummaryService(posts,db,tx,new DiscussionDigest(model,json),json);
        var siteSearch=new PersonalSiteSearch(db,null,null);
        String prefix="digest_"+UUID.randomUUID()+"_";
        var cited=new AtomicLong();
        try {
            long alice=account(jdbc,prefix+"a"), bob=account(jdbc,prefix+"b");
            login(alice);
            long post=tx.execute(s->posts.create("危险是否还在持续？","PUBLIC",List.of()).id());
            tx.execute(s->posts.addComment(post,"我认为还在持续"));
            login(bob);
            tx.execute(s->posts.addComment(post,"我认为已经结束"));
            assertEquals("INELIGIBLE",summary.read(post).status());
            cited.set(tx.execute(s->posts.addComment(post,"应区分前后两个时间点").id()));
            assertEquals("PENDING",summary.read(post).status());
            when(model.complete(anyList(),eq(true))).thenAnswer(i->
                    "{\"topic\":\"危险持续性\",\"points\":[{\"kind\":\"MIXED\",\"text\":\"区分两个时间点\","
                    +"\"evidence\":[{\"id\":"+cited.get()+",\"quote\":\"前后两个时间点\"}]}]}");
            summary.processOne();
            summary.processOne();
            verify(model,times(1)).complete(anyList(),eq(true));
            var ready=summary.read(post);
            assertEquals("READY",ready.status());
            assertEquals(cited.get(),ready.sources().get(0).id());
            assertEquals(1,ready.sources().get(0).page());
            assertTrue(summary.library("两个时间点",1,10).getRecords().stream().anyMatch(c->c.postId()==post));
            assertEquals(post,siteSearch.search(bob,List.of("危险持续性"),"POST",10).get(0).id());
            String aiSource=siteSearch.find(bob,"POST",post,List.of()).summary();
            assertTrue(aiSource.contains("AI 观点整理"));
            assertTrue(aiSource.contains("折中：区分两个时间点（评论 #"+cited.get()+"）"));

            tx.execute(s->posts.addComment(post,"再举一个例子"));
            assertEquals("PENDING",summary.read(post).status());
            assertEquals(0,summary.library("两个时间点",1,10).getTotal());
            assertTrue(siteSearch.search(bob,List.of("危险持续性"),"POST",10).isEmpty());
            assertFalse(siteSearch.find(bob,"POST",post,List.of()).summary().contains("AI 观点整理"));
            doAnswer(i -> {
                summary.processOne(); // A second poll cannot claim the in-flight source version.
                tx.execute(s -> posts.addComment(post,"刚补充了关键事实"));
                return "{\"topic\":\"旧主题\",\"points\":[]}";
            }).when(model).complete(anyList(),eq(true));
            summary.processOne();
            assertEquals("PENDING",summary.read(post).status()); // Obsolete result was discarded.
            verify(model,times(2)).complete(anyList(),eq(true));
            doReturn("{\"points\":[]}").when(model).complete(anyList(),eq(true));
            summary.processOne();
            assertEquals("UNAVAILABLE",summary.read(post).status());
            summary.processOne();
            verify(model,times(3)).complete(anyList(),eq(true));

            jdbc.update("UPDATE community_post SET visibility='FRIENDS' WHERE id=?",post);
            assertEquals(404, assertThrows(BizException.class,()->summary.read(post)).getCode());
            assertNull(siteSearch.find(bob,"POST",post,List.of()));
            login(alice);
            assertEquals("INELIGIBLE",summary.read(post).status());
            assertFalse(siteSearch.find(alice,"POST",post,List.of()).summary().contains("AI 观点整理"));
            assertTrue(summary.library("",1,10).getRecords().stream().noneMatch(c->c.postId()==post));
            posts.delete(post);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM community_discussion_summary WHERE post_id=?",Integer.class,post));
        } finally {
            summary.close();
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM `user` WHERE username IN (?,?)",prefix+"a",prefix+"b");
        }
    }

    private long account(JdbcTemplate jdbc,String username) {
        jdbc.update("INSERT INTO `user`(username,password) VALUES(?,?)",username,"test-only");
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?",Long.class,username);
    }

    private void login(long id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(id,"tester","tester","USER"),null,List.of()));
    }
}
