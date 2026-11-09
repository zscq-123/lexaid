package com.lexaid.mockexam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.BizException;
import com.lexaid.exam.dto.QuestionOption;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import static com.lexaid.mockexam.MockExam.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class MockExamService {
    static final long MINUTE = 60_000L;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private static final String POOL = " FROM mock_exam_bank WHERE source_type=? AND paper_type=? "
            + " AND CHAR_LENGTH(TRIM(reference_answer))>0 ";
    private static final String SNAPSHOT = """
            INSERT INTO mock_exam_item(session_id,ordinal_no,max_points,bank_id,kind,subject,stem,options_json,
                  reference_answer,explanation,source_label,source_url,reading_url,answer)
            SELECT ?,?,?,id,kind,subject,stem,options_json,reference_answer,explanation,source_label,source_url,reading_url,''
              FROM mock_exam_bank WHERE id=?
            """;
    private record Header(long id,String source,String paper,String status,long start,long deadline,Long submitted) {
        boolean active() { return status.equals("ACTIVE"); }
    }

    public List<Format> catalog() {
        List<Format> formats = new ArrayList<>();
        for (String source : List.of("REAL","MOCK")) {
            for (String paper : List.of("OBJECTIVE","SUBJECTIVE")) formats.add(format(source,paper));
        }
        return formats;
    }

    private Format format(String source,String paper) {
        Map<String,Integer> pool = new HashMap<>();
        db.query("SELECT kind,COUNT(*)" + POOL + " GROUP BY kind",
                rs -> { pool.put(rs.getString(1),rs.getInt(2)); },source,paper);
        boolean objective = "OBJECTIVE".equals(paper);
        List<Section> sections;
        if (objective) {
            int multiple = pool.getOrDefault("MULTIPLE",0);
            int indefinite = pool.getOrDefault("INDEFINITE",0);
            int indefiniteQuota = Math.min(Math.max(15,50-multiple),indefinite);
            sections = List.of(new Section("SINGLE",50,1,pool.getOrDefault("SINGLE",0)),
                    new Section("MULTIPLE",50-indefiniteQuota,2,multiple),new Section("INDEFINITE",indefiniteQuota,2,indefinite));
        } else {
            sections = List.of(new Section("SHORT",5,36,pool.getOrDefault("SHORT",0)));
        }
        return new Format(source,paper,objective ? 180 : 240,
                sections.stream().allMatch(s -> s.count()<=s.availableCount()),sections,objective ? 150 : 180);
    }

    public Session start(long user,Start input) {
        // Serialize simultaneous starts for this account, including its first ever exam.
        db.queryForObject("SELECT id FROM `user` WHERE id=? FOR UPDATE", Long.class,user);
        Header existing = activeHeader(user);
        if (existing != null) {
            existing = settleIfDue(existing);
            if (existing.active()) return view(existing,true);
        }
        Format format = format(input.sourceType(),input.paperType());
        if (!format.available()) throw new BizException("此来源的可用试题不足以组成完整试卷");
        long start = System.currentTimeMillis();
        db.update("INSERT INTO mock_exam_session(user_id,source_type,paper_type,started_at,deadline) VALUES (?,?,?,?,?)",
                user,input.sourceType(),input.paperType(),start,start + input.durationMinutes()*MINUTE);
        long id = Objects.requireNonNull(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
        List<Object[]> snapshots = new ArrayList<>();
        for (Section section : format.sections()) {
            if (section.count()==0) continue;
            String ordering = "MOCK".equals(input.sourceType()) ? "(difficulty>=4) DESC, RAND()" : "RAND()";
            List<Long> ids = db.queryForList("SELECT id" + POOL + " AND kind=? ORDER BY " + ordering + " LIMIT ?",
                    Long.class,input.sourceType(),input.paperType(),section.kind(),section.count());
            if (ids.size()!=section.count()) throw new BizException("可用题量发生变化，请重新选卷");
            for (long bankId : ids) snapshots.add(new Object[]{id,snapshots.size()+1,section.points(),bankId});
        }
        db.batchUpdate(SNAPSHOT,snapshots);
        return view(owned(id,user),true);
    }

    public Session active(long user) {
        Header active = activeHeader(user);
        if (active == null) return null;
        active = settleIfDue(active);
        return active.active() ? view(active,true) : null;
    }

    private Header activeHeader(long user) {
        // Lock an existing session by primary key only. A locking lookup for an absent
        // active_user would lock an index gap shared by unrelated users starting simultaneously.
        List<Long> ids = db.queryForList("SELECT id FROM mock_exam_session WHERE user_id=? AND active_slot=1",Long.class,user);
        if (ids.isEmpty()) return null;
        Header h = owned(ids.get(0),user);
        return h.active() ? h : null;
    }

    private Header header(ResultSet rs,int n) throws SQLException {
        return new Header(rs.getLong("id"),rs.getString("source_type"),rs.getString("paper_type"),rs.getString("status"),
                rs.getLong("started_at"),rs.getLong("deadline"),rs.getObject("submitted_at",Long.class));
    }

    private Header owned(long id,long user) {
        return db.query("SELECT * FROM mock_exam_session WHERE id=? AND user_id=? FOR UPDATE",this::header,id,user)
                .stream().findFirst().orElseThrow(() -> new BizException(404,"试卷不存在"));
    }

    public Session session(long user,long id) { return view(settleIfDue(owned(id,user)),true); }

    public List<Session> history(long user) {
        Header h = activeHeader(user);
        if (h!=null) settleIfDue(h);
        return db.query("SELECT * FROM mock_exam_session WHERE user_id=? ORDER BY id DESC LIMIT 30",this::header,user)
                .stream().map(row -> view(row,false)).toList();
    }

    private Session view(Header h,boolean full) {
        List<Item> items = full ? items(h.id(),!h.active()) : List.of();
        int[] score;
        if (full) {
            score = new int[3];
            for (Item item : items) {
                score[0] += item.maxPoints();
                if (item.points()==null) score[2]++;
                else score[1] += item.points();
            }
        } else {
            score = db.queryForObject("""
                SELECT COALESCE(SUM(max_points),0), COALESCE(SUM(points),0), COUNT(*)-COUNT(points)
                  FROM mock_exam_item WHERE session_id=?
                """,(rs,n) -> new int[]{rs.getInt(1),rs.getInt(2),rs.getInt(3)},h.id());
        }
        return new Session(h.id(),h.source(),h.paper(),h.status(),h.start(),h.deadline(),h.start()+30*MINUTE,
                System.currentTimeMillis(),h.submitted(),score[0],!h.active() && score[2]==0 ? score[1] : null,score[1],items);
    }

    private List<Item> items(long id,boolean results) {
        return db.query("SELECT * FROM mock_exam_item WHERE session_id=? ORDER BY ordinal_no",(rs,n) -> new Item(
                rs.getInt("ordinal_no"),rs.getString("kind"),rs.getString("stem"),options(rs.getString("options_json")),
                rs.getString("subject"),rs.getInt("max_points"),rs.getString("answer"),rs.getInt("revision"),
                rs.getString("grade_status"),results ? rs.getObject("points",Integer.class) : null,
                results ? rs.getString("reference_answer") : null,results ? rs.getString("feedback") : null,
                results || rs.getString("reading_url")!=null ? rs.getString("source_label") : null,
                results ? rs.getString("source_url") : rs.getString("reading_url"),
                results ? rs.getString("explanation") : null),id);
    }

    public Saved save(long user,long id,int position,Draft input) {
        Header h = settleIfDue(owned(id,user));
        record Stored(String kind,String options,String answer,int revision) {}
        Stored item = db.query("SELECT kind,options_json,answer,revision FROM mock_exam_item WHERE session_id=? AND ordinal_no=?",
                (rs,n) -> new Stored(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)),id,position)
                .stream().findFirst().orElseThrow(() -> new BizException(404,"题目不存在"));
        if (!h.active()) return new Saved(false,item.revision(),item.answer(),h.status());
        String answer = normalize(item.kind(),input.answer(),item.options());
        if (input.revision()!=item.revision()+1) {
            boolean repeated = input.revision()==item.revision() && answer.equals(item.answer());
            return new Saved(repeated,item.revision(),item.answer(),h.status());
        }
        db.update("UPDATE mock_exam_item SET answer=?,revision=? WHERE session_id=? AND ordinal_no=?",
                answer,input.revision(),id,position);
        return new Saved(true,input.revision(),answer,h.status());
    }

    private List<QuestionOption> options(String raw) {
        if (raw==null) return List.of();
        try { return json.readValue(raw,new TypeReference<>(){}); }
        catch (Exception e) { throw new IllegalStateException("题库选项无法读取",e); }
    }

    private String normalize(String kind,String raw,String optionsJson) {
        if (kind.equals("SHORT")) return raw;
        SortedSet<String> keys = keys(raw);
        if (kind.equals("SINGLE") && keys.size()>1) throw new BizException("单选题只能选一项");
        Set<String> allowed = new HashSet<>();
        options(optionsJson).forEach(option -> allowed.add(option.key()));
        if (!allowed.containsAll(keys)) throw new BizException("答案含有未知选项");
        try { return json.writeValueAsString(keys); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private SortedSet<String> keys(String answer) {
        String text = answer.strip();
        if (text.isEmpty()) return new TreeSet<>();
        if (text.startsWith("[")) {
            try {
                List<String> values = json.readValue(text,new TypeReference<>(){});
                if (values.stream().anyMatch(v -> v==null || !v.matches("[A-Z]"))) throw new IllegalArgumentException();
                return new TreeSet<>(values);
            } catch (Exception e) { throw new BizException("答案选项格式有误"); }
        }
        if (!text.matches("[A-Z]+")) throw new BizException("答案选项格式有误");
        return new TreeSet<>(Arrays.asList(text.split("")));
    }

    public Session submit(long user,long id) {
        Header h = settleIfDue(owned(id,user));
        if (h.active()) {
            long now = System.currentTimeMillis();
            if (now<h.start()+30*MINUTE) throw new BizException("考试满30分钟后才能提前交卷");
            h = finish(h,Math.min(now,h.deadline()));
        }
        return view(h,true);
    }

    private Header settleIfDue(Header h) {
        return h.active() && System.currentTimeMillis()>=h.deadline() ? finish(h,h.deadline()) : h;
    }

    private Header finish(Header h,long time) {
        List<Object[]> grades = new ArrayList<>();
        for (Item item : items(h.id(),true)) {
            String status;
            Integer points;
            if (item.kind().equals("SHORT")) {
                boolean blank = item.answer().isBlank();
                status = blank ? "BLANK" : "PENDING";
                points = blank ? 0 : null;
            } else {
                SortedSet<String> selected = keys(item.answer());
                status = selected.isEmpty() ? "BLANK" : "GRADED";
                points = !selected.isEmpty() && selected.equals(keys(item.referenceAnswer())) ? item.maxPoints() : 0;
            }
            grades.add(new Object[]{status,points,h.id(),item.position()});
        }
        db.batchUpdate("UPDATE mock_exam_item SET grade_status=?,points=? WHERE session_id=? AND ordinal_no=?",grades);
        db.update("UPDATE mock_exam_session SET status='SUBMITTED',submitted_at=? WHERE id=?",time,h.id());
        return new Header(h.id(),h.source(),h.paper(),"SUBMITTED",h.start(),h.deadline(),time);
    }

    public Session retry(long user,long id) {
        Header h = settleIfDue(owned(id,user));
        if (h.active()) throw new BizException("请先交卷");
        db.update("UPDATE mock_exam_item SET grade_status='PENDING',feedback=NULL WHERE session_id=? AND grade_status='UNAVAILABLE'",id);
        return view(h,true);
    }

    public void expire(long id) {
        db.query("SELECT * FROM mock_exam_session WHERE id=? FOR UPDATE",this::header,id).stream()
                .findFirst().ifPresent(this::settleIfDue);
    }
}
