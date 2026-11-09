package com.lexaid.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.BizException;
import com.lexaid.exam.ScenarioGenerator.Exercise;
import com.lexaid.exam.ScenarioGenerator.Source;
import com.lexaid.exam.dto.QuestionOption;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScenarioPracticeService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final QuestionMapper questions;
    private final ScenarioGenerator generator;
    private final StudyRecordService studies;
    private final ScenarioGenerationGate generations = new ScenarioGenerationGate(12);

    public record View(long id, int round, String origin, String topic, String stem, List<QuestionOption> options,
                       String selected, String correctAnswer, String basis, String explanation, String comparison) {}
    private record Saved(long id, long sourceQuestion, long sourceRecord, int round, Exercise exercise, String selected) {
        View view() {
            boolean answered = selected != null;
            return new View(id, round, exercise.origin(), exercise.topic(), exercise.stem(), exercise.options(),
                    selected, answered ? exercise.answer() : null, answered ? exercise.basis() : null,
                    answered ? exercise.explanation() : null, answered ? exercise.comparison() : null);
        }
    }

    // Do not keep a database transaction open while waiting for the model.
    public View open(long user, long question, Long after) {
        Long record = db.query("""
                SELECT IF(is_correct=0,id,NULL) FROM answer_record
                WHERE user_id=? AND question_id=? ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, Long.class) : null, user, question);
        if (record == null) throw new BizException(404, "仅可从自己当前答错的题目开始情景迁移。");

        Saved prior = null;
        int round = 1;
        if (after != null) {
            prior = owned(user, after);
            if (prior.sourceRecord() != record || prior.sourceQuestion() != question || prior.selected() == null) {
                throw new BizException(409, "请先完成当前练习，再开始下一题。");
            }
            round = prior.round() + 1;
        }
        Saved existing = after == null ? latest(user, record) : findRound(user, record, round);
        if (existing != null) return existing.view();

        var key = new ScenarioGenerationGate.Round(user, record, round);
        String previous = prior == null ? "" : prior.exercise().stem();
        return generations.generate(key, () -> generate(question, key, previous));
    }

    private View generate(long question, ScenarioGenerationGate.Round key, String previous) {
        long user = key.user(), record = key.record();
        int round = key.number();
        // Another request may have finished after open() queried the exercise but before this one acquired its slot.
        Saved existing = findRound(user, record, round);
        if (existing != null) return existing.view();
        Question original = questions.classifiedQuestions(List.of(question)).stream().findFirst()
                .orElseThrow(() -> new BizException(404, "原题已删除。"));
        List<String> provisions = db.queryForList("""
                SELECT CONCAT(p.law_name,' ',p.article_no,'：',LEFT(p.content,1600)) FROM legal_provision p
                JOIN entity_relation r ON (r.source_type='QUESTION' AND r.source_id=? AND r.target_type='PROVISION' AND r.target_id=p.id)
                    OR (r.target_type='QUESTION' AND r.target_id=? AND r.source_type='PROVISION' AND r.source_id=p.id)
                ORDER BY p.id LIMIT 2
                """, String.class, question, question);
        Source source = new Source(safe(original.getStem()), safe(original.getOptions()), safe(original.getAnswer()),
                safe(original.getAnalysis()), String.join("\n", provisions), safe(original.getSubject()));
        Exercise draft = generator.generate(source, previous, round);
        String material;
        try {
            material = json.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存情景练习", e);
        }

        // Retries and concurrent opens for the same wrong answer converge on one stored exercise.
        db.update("""
                INSERT INTO wrong_question_practice (user_id,source_question_id,source_record_id,round_no,exercise)
                SELECT ?,?,?,?,? FROM answer_record ar WHERE ar.id=? AND ar.is_correct=0
                    AND NOT EXISTS (SELECT 1 FROM answer_record newer WHERE newer.user_id=ar.user_id
                        AND newer.question_id=ar.question_id AND newer.id>ar.id)
                ON DUPLICATE KEY UPDATE id=wrong_question_practice.id
                """, user, question, record, round, material, record);
        Saved saved = findRound(user, record, round);
        if (saved == null) throw new BizException(409, "原题作答状态发生变化，请刷新错题本。");
        return saved.view();
    }

    @Transactional
    public View submit(long user, long id, String choice) {
        int changed = db.update("""
                UPDATE wrong_question_practice SET selected=?, answered_at=NOW()
                WHERE id=? AND user_id=? AND selected IS NULL
                """, choice, id, user);
        Saved saved = owned(user, id);
        if (changed == 1) {
            studies.record(user, "ANSWER", "QUESTION", saved.sourceQuestion(), "情景迁移：" + saved.exercise().topic());
        }
        return saved.view();
    }

    private Saved findRound(long user, long record, int round) {
        return db.query("SELECT * FROM wrong_question_practice WHERE user_id=? AND source_record_id=? AND round_no=?",
                this::readRow, user, record, round);
    }
    private Saved latest(long user, long record) {
        return db.query("SELECT * FROM wrong_question_practice WHERE user_id=? AND source_record_id=? ORDER BY round_no DESC LIMIT 1",
                this::readRow, user, record);
    }
    private Saved owned(long user, long id) {
        Saved row = db.query("SELECT * FROM wrong_question_practice WHERE user_id=? AND id=?", this::readRow, user, id);
        if (row == null) throw new BizException(404, "练习不存在。");
        return row;
    }
    private Saved readRow(ResultSet rs) throws SQLException {
        if (!rs.next()) return null;
        try {
            return new Saved(rs.getLong("id"), rs.getLong("source_question_id"), rs.getLong("source_record_id"),
                    rs.getInt("round_no"), json.readValue(rs.getString("exercise"), Exercise.class), rs.getString("selected"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("练习记录异常", e);
        }
    }
    private static String safe(String s) { return Objects.toString(s, "").strip(); }
}
