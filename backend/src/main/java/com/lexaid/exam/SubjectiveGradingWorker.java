package com.lexaid.exam;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.ai.DeepSeekGrader;
import com.lexaid.ai.DeepSeekGrader.Grade;
import com.lexaid.ai.DeepSeekGrader.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pending rows form a persistent queue. No database transaction spans the remote call. */
@Component
@RequiredArgsConstructor
public class SubjectiveGradingWorker {
    private final TransactionTemplate transactions;
    private final ExamSessionMapper sessions;
    private final AnswerRecordMapper answers;
    private final QuestionMapper questions;
    private final DeepSeekGrader grader;

    private record Batch(long sessionId, String token, List<Submission> submissions,
                         Map<Long, Long> recordIds, Map<Long, QuestionGrade> results) {}

    @Scheduled(fixedDelayString = "${ai.grading.poll-interval-ms:1500}")
    public void processNextBatch() {
        Long sessionId = answers.nextGradingSession();
        if (sessionId == null) return;
        Batch batch = transactions.execute(tx -> claim(sessionId));
        if (batch == null) return;
        if (!batch.submissions().isEmpty()) {
            try {
                Map<Long, Grade> grades = grader.grade(batch.submissions());
                for (Submission item : batch.submissions()) {
                    Grade grade = grades.get(item.questionId());
                    batch.results().put(item.questionId(), new QuestionGrade("AI_GRADED", grade.score(), grade.feedback()));
                }
            } catch (DeepSeekClient.UnavailableException ex) {
                for (Submission item : batch.submissions()) {
                    batch.results().put(item.questionId(), new QuestionGrade("UNAVAILABLE", null, ex.getMessage()));
                }
            }
        }
        transactions.executeWithoutResult(tx -> finish(batch));
    }

    private Batch claim(long sessionId) {
        if (sessions.selectByIdForUpdate(sessionId) == null) return null;
        List<AnswerRecord> records = answers.nextGradingBatch(sessionId);
        if (records.isEmpty()) return null;
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : questions.selectBatchIds(records.stream().map(AnswerRecord::getQuestionId).toList())) {
            byId.put(q.getId(), q);
        }
        String token = UUID.randomUUID().toString();
        List<Submission> submissions = new ArrayList<>();
        Map<Long, Long> recordIds = new HashMap<>();
        Map<Long, QuestionGrade> results = new HashMap<>();
        int chars = 0;
        for (AnswerRecord record : records) {
            long id = record.getQuestionId();
            Question q = byId.get(id);
            if (q == null || !StringUtils.hasText(q.getAnswer())) {
                results.put(id, new QuestionGrade("NO_REFERENCE", null, "参考答案不可用，此题不计入得分。"));
            } else {
                int size = q.getStem().length() + q.getAnswer().length() + record.getUserAnswer().length();
                // A long case can be graded alone without truncating the evidence supplied to the model.
                if (!submissions.isEmpty() && chars + size > 48000) break;
                chars += size;
                submissions.add(new Submission(id, q.getStem(), q.getAnswer(), record.getUserAnswer()));
            }
            answers.claimGrade(record.getId(), token);
            recordIds.put(id, record.getId());
        }
        return new Batch(sessionId, token, submissions, recordIds, results);
    }

    private void finish(Batch batch) {
        ExamSession session = sessions.selectByIdForUpdate(batch.sessionId());
        if (session == null) return;
        int earned = 0;
        int fullCreditCount = 0;
        for (var entry : batch.results().entrySet()) {
            QuestionGrade grade = entry.getValue();
            Boolean fullCredit = grade.points() == null ? null : grade.points() == QuestionGrade.maxPoints("SHORT");
            int applied = answers.finishGrade(batch.recordIds().get(entry.getKey()), batch.token(),
                    grade.status(), grade.points(), grade.feedback(), fullCredit);
            if (applied > 0 && grade.points() != null) {
                earned += grade.points();
                if (fullCredit) fullCreditCount++;
            }
        }
        if (earned != 0) {
            session.setScore(session.getScore() + earned);
            session.setCorrect(session.getCorrect() + fullCreditCount);
            sessions.updateById(session);
        }
    }
}
