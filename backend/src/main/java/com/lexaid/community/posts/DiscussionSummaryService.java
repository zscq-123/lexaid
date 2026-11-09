package com.lexaid.community.posts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.PageResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DiscussionSummaryService {
    private static final Logger log = LoggerFactory.getLogger(DiscussionSummaryService.class);
    private static final int SAMPLE = 24;
    private static final String COUNT = "(SELECT COUNT(*) FROM community_post_comment c WHERE c.post_id=p.id)";
    private static final String LATEST = "(SELECT COALESCE(MAX(c.id),0) FROM community_post_comment c WHERE c.post_id=p.id)";
    private static final String FRESH = "d.source_count=" + COUNT + " AND d.source_latest=" + LATEST
            + " AND d.caption_hash=SHA2(p.caption,256)";
    /** Shared by the knowledge library and site AI. Aliases are d (digest) and p (source post). */
    public static final String PUBLIC_ARCHIVE_FROM = " FROM community_discussion_summary d JOIN community_post p ON p.id=d.post_id"
            + " WHERE p.visibility='PUBLIC' AND d.state='READY' AND d.point_count>0 AND " + FRESH;
    private final PostService posts;
    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate transactions;
    private final DiscussionDigest digest;
    private final ObjectMapper json;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "discussion-summary");
        thread.setDaemon(true);
        return thread;
    });

    public DiscussionSummaryService(PostService posts, NamedParameterJdbcTemplate db,
                                   TransactionTemplate transactions, DiscussionDigest digest, ObjectMapper json) {
        this.posts = posts;
        this.db = db;
        this.transactions = transactions;
        this.digest = digest;
        this.json = json;
    }

    public record Source(long id, String authorName, String text, int page) {}
    public record View(long postId, String status, long commentCount, int sampledComments, LocalDateTime generatedAt,
                       String topic, List<DiscussionDigest.Point> points, List<Source> sources) {}
    public record Card(long postId, String topic, long commentCount, int sampledComments, LocalDateTime generatedAt) {}
    private record Version(long count, long latest, int authors, String captionHash, boolean published) {
        boolean eligible() { return published && count>=3 && authors>=2; }
    }
    private record Row(String state,int attempts,long count,long latest,String hash,String token,
                       boolean due,String payload,LocalDateTime generated) {
        boolean matches(Version v) { return count==v.count && latest==v.latest && hash.equals(v.captionHash); }
    }
    private record Job(long id,String token,Version version,List<DiscussionDigest.Material> materials) {}

    /** Called after insertion while the comment transaction holds the post lock. */
    static void queueAfterComment(NamedParameterJdbcTemplate db,long id) {
        db.update("""
                INSERT INTO community_discussion_summary(post_id)
                SELECT p.id FROM community_post p JOIN community_post_comment c ON c.post_id=p.id
                WHERE p.id=:id AND p.visibility='PUBLIC' GROUP BY p.id
                HAVING COUNT(*)>=3 AND COUNT(DISTINCT c.author_id)>=2
                ON DUPLICATE KEY UPDATE attempts=0,
                   next_attempt=IF(state IN ('RUNNING','PENDING'),next_attempt,CURRENT_TIMESTAMP(6)),
                   state=IF(state='RUNNING','RUNNING','PENDING')
                """,Map.of("id",id));
    }

    @Transactional(readOnly = true)
    public View read(long id) throws JsonProcessingException {
        var post = posts.get(id); // Same 404 and audience rules as the original thread.
        Version v = version(id);
        Row row = findRow(id);
        if (!v.eligible()) return empty(id,"INELIGIBLE",post.commentCount());
        if (row == null || !row.state.equals("READY") || !row.matches(v)) {
            return empty(id, !digest.available() || (row!=null && row.state.equals("FAILED") && row.matches(v))
                    ? "UNAVAILABLE" : "PENDING",v.count);
        }
        var summary = json.readValue(row.payload, DiscussionDigest.Summary.class);
        Set<Long> cited = new LinkedHashSet<>();
        summary.points().forEach(point -> cited.addAll(point.sourceIds()));
        var sources = materials(id).stream().filter(s->cited.contains(s.id()))
                .map(s->new Source(s.id(),s.authorName(),s.text(),s.page())).toList();
        // A removed account can cascade-delete comments. Never serve unsupported claims.
        if (sources.size()!=cited.size()) return empty(id,"PENDING",v.count);
        return new View(id,"READY",v.count,(int)Math.min(SAMPLE,v.count),row.generated,summary.topic(),summary.points(),sources);
    }

    private static View empty(long id,String state,long count) {
        return new View(id,state,count,0,null,null,List.of(),List.of());
    }

    @Transactional(readOnly = true)
    public PageResult<Card> library(String keyword,int page,int size) {
        String from = PUBLIC_ARCHIVE_FROM;
        String term = keyword.strip();
        if (!term.isEmpty()) from += " AND (LOCATE(:term,d.search_text)>0 OR LOCATE(:term,p.caption)>0)";
        var args = Map.of("term",term,"skip",(long)(page-1)*size,"size",size);
        long count = db.queryForObject("SELECT COUNT(*)"+from,args,Long.class);
        List<Card> cards = db.query("SELECT d.post_id,JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.topic')) AS topic,"
                + "d.source_count,d.generated_at"+from+" ORDER BY d.generated_at DESC,d.post_id DESC LIMIT :size OFFSET :skip",
                args,(rs,n)->new Card(rs.getLong(1),rs.getString(2),rs.getLong(3),
                        Math.min(SAMPLE,rs.getInt(3)),rs.getTimestamp(4).toLocalDateTime()));
        return new PageResult<>(cards,count);
    }

    private Version version(long id) {
        return db.queryForObject("""
                SELECT COUNT(c.id),COALESCE(MAX(c.id),0),COUNT(DISTINCT c.author_id),SHA2(p.caption,256),p.visibility
                FROM community_post p LEFT JOIN community_post_comment c ON c.post_id=p.id
                WHERE p.id=:id GROUP BY p.id
                """,Map.of("id",id),(rs,n)->new Version(rs.getLong(1),rs.getLong(2),rs.getInt(3),
                rs.getString(4),"PUBLIC".equals(rs.getString(5))));
    }

    private List<DiscussionDigest.Material> materials(long id) {
        var args = Map.of("id",id,"sample",SAMPLE);
        var source = new ArrayList<>(db.query("""
                SELECT p.author_id,COALESCE(NULLIF(u.nickname,''),u.username),p.caption
                FROM community_post p JOIN `user` u ON u.id=p.author_id WHERE p.id=:id
                """,args,(rs,n)->new DiscussionDigest.Material(0,rs.getLong(1),rs.getString(2),rs.getString(3),0)));
        source.addAll(db.query("""
                SELECT c.id,c.author_id,COALESCE(NULLIF(u.nickname,''),u.username),c.content
                FROM community_post_comment c JOIN `user` u ON u.id=c.author_id
                WHERE c.post_id=:id ORDER BY c.id DESC LIMIT :sample
                """,args,(rs,n)->new DiscussionDigest.Material(rs.getLong(1),rs.getLong(2),rs.getString(3),
                rs.getString(4),n/10+1)));
        return source;
    }

    private Row findRow(long id) {
        return db.query("""
                SELECT state,attempts,source_count,source_latest,caption_hash,lease_token,
                       next_attempt<=CURRENT_TIMESTAMP(6),payload,generated_at
                FROM community_discussion_summary WHERE post_id=:id
                """,Map.of("id",id),(rs,n)->new Row(rs.getString(1),rs.getInt(2),rs.getLong(3),rs.getLong(4),
                rs.getString(5),rs.getString(6),rs.getBoolean(7),rs.getString(8),
                rs.getObject(9, LocalDateTime.class))).stream().findFirst().orElse(null);
    }

    @Scheduled(fixedDelayString = "${ai.discussion.poll-interval-ms:30000}")
    public void tick() {
        if (!digest.available() || !busy.compareAndSet(false,true)) return;
        worker.execute(() -> {
            try { processOne(); }
            catch (RuntimeException ex) { log.warn("讨论整理任务未完成（{}）",ex.getClass().getSimpleName()); }
            finally { busy.set(false); }
        });
    }

    void processOne() {
        var ids = db.queryForList("""
                SELECT post_id FROM community_discussion_summary
                WHERE (state IN ('PENDING','RUNNING') OR (state='FAILED' AND attempts<3))
                AND next_attempt<=CURRENT_TIMESTAMP(6) ORDER BY next_attempt,post_id LIMIT 1
                """,Map.of(),Long.class);
        if (ids.isEmpty()) return;
        Job job = transactions.execute(tx->claim(ids.get(0)));
        if (job==null) return;
        DiscussionDigest.Summary result;
        try { result = digest.generate(job.materials); }
        catch (Exception ex) {
            log.warn("帖子 {} 的观点整理暂不可用（{}）",job.id,ex.getClass().getSimpleName());
            result=null;
        }
        DiscussionDigest.Summary answer=result;
        transactions.executeWithoutResult(tx->finish(job,answer));
    }

    private boolean lockPost(long id) {
        return !db.queryForList("SELECT id FROM community_post WHERE id=:id FOR UPDATE",Map.of("id",id),
                Long.class).isEmpty();
    }

    private Job claim(long id) {
        if (!lockPost(id)) return null;
        Row row=findRow(id);
        if (row==null || !row.due || row.state.equals("READY") || (row.state.equals("FAILED") && row.attempts>=3)) return null;
        Version v=version(id);
        if (!v.eligible()) {
            db.update("DELETE FROM community_discussion_summary WHERE post_id=:id",Map.of("id",id));
            return null;
        }
        String token=UUID.randomUUID().toString();
        db.update("""
                UPDATE community_discussion_summary SET state='RUNNING',lease_token=:token,
                    next_attempt=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 3 MINUTE),attempts=:attempts,
                    source_count=:count,source_latest=:latest,caption_hash=:hash,
                    payload=NULL,search_text=NULL,point_count=0
                WHERE post_id=:id
                """,Map.of("id",id,"token",token,"count",v.count,"latest",v.latest,"hash",v.captionHash,
                "attempts",row.matches(v)?Math.min(3,row.attempts+1):1));
        return new Job(id,token,v,materials(id));
    }

    private void finish(Job job,DiscussionDigest.Summary answer) {
        if (!lockPost(job.id)) return;
        Row row=findRow(job.id);
        if (row==null || !job.token.equals(row.token)) return;
        Version now=version(job.id);
        if (!now.eligible()) {
            db.update("DELETE FROM community_discussion_summary WHERE post_id=:id",Map.of("id",job.id));
        } else if (!job.version.equals(now)) {
            db.update("""
                    UPDATE community_discussion_summary SET state='PENDING',lease_token=NULL,attempts=0,
                    next_attempt=CURRENT_TIMESTAMP(6) WHERE post_id=:id
                    """,Map.of("id",job.id));
        } else if (answer==null) {
            db.update("""
                    UPDATE community_discussion_summary SET state='FAILED',lease_token=NULL,
                    next_attempt=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 10 MINUTE) WHERE post_id=:id
                    """,Map.of("id",job.id));
        } else {
            try {
                String text=answer.searchText();
                db.update("""
                        UPDATE community_discussion_summary SET state='READY',lease_token=NULL,payload=:payload,
                        search_text=:text,point_count=:points,generated_at=CURRENT_TIMESTAMP(6) WHERE post_id=:id
                        """,Map.of("id",job.id,"payload",json.writeValueAsString(answer),"text",text,"points",answer.points().size()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法保存观点整理",e);
            }
        }
    }

    // A user deletion may cascade to comments without calling PostService.
    @Scheduled(initialDelay = 900000, fixedDelay = 900000)
    public void reconcileChangedSources() {
        db.update("UPDATE community_discussion_summary d JOIN community_post p ON p.id=d.post_id"
                + " SET d.state='PENDING',d.attempts=0,d.next_attempt=CURRENT_TIMESTAMP(6)"
                + " WHERE d.state IN ('READY','FAILED') AND (p.visibility<>'PUBLIC' OR NOT ("+FRESH+"))",Map.of());
    }

    @PreDestroy
    void close() { worker.shutdownNow(); }
}
