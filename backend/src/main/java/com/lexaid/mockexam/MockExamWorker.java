package com.lexaid.mockexam;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.ai.DeepSeekGrader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MockExamWorker {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final MockExamService exams;
    private final DeepSeekGrader grader;

    @Scheduled(fixedDelayString="${mock-exam.expiry-poll-ms:5000}")
    public void expireDue() {
        var ids = db.queryForList("SELECT id FROM mock_exam_session WHERE status='ACTIVE' AND deadline<=? ORDER BY deadline LIMIT 50",
                Long.class,System.currentTimeMillis());
        ids.forEach(exams::expire);
    }

    private record Job(long session,int position,String token,DeepSeekGrader.Submission item) {}

    @Scheduled(fixedDelayString="${mock-exam.grading-poll-ms:2000}")
    public void gradeNext() {
        Job job = tx.execute(t -> claim());
        if (job==null) return;
        try {
            var grade = grader.grade(List.of(job.item())).get(job.item().questionId());
            finish(job,"GRADED",grade.score(),grade.feedback());
        } catch (DeepSeekClient.UnavailableException ex) {
            finish(job,"UNAVAILABLE",null,ex.getMessage());
        }
    }

    private Job claim() {
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        var rows = db.query("""
                SELECT session_id,ordinal_no,bank_id,stem,reference_answer,answer,max_points FROM mock_exam_item
                 WHERE grade_status='PENDING' AND claim_until<=? ORDER BY claim_until,session_id,ordinal_no
                 LIMIT 1 FOR UPDATE SKIP LOCKED
                """,(rs,n) -> new Job(rs.getLong(1),rs.getInt(2),token,new DeepSeekGrader.Submission(rs.getLong(3),
                rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7))),now);
        if (rows.isEmpty()) return null;
        Job job = rows.get(0);
        db.update("UPDATE mock_exam_item SET claim_token=?,claim_until=? WHERE session_id=? AND ordinal_no=?",
                token,now+5*MockExamService.MINUTE,job.session(),job.position());
        return job;
    }

    private void finish(Job job,String status,Integer points,String feedback) {
        db.update("""
                UPDATE mock_exam_item SET grade_status=?,points=?,feedback=?,claim_token=NULL,claim_until=0
                 WHERE session_id=? AND ordinal_no=? AND grade_status='PENDING' AND claim_token=?
                """,status,points,feedback,job.session(),job.position(),job.token());
    }
}
