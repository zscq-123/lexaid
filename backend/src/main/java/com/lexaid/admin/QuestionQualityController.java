package com.lexaid.admin;

import com.lexaid.common.Result;
import com.lexaid.exam.QuestionQuality;
import com.lexaid.exam.QuestionSubjects;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** Protected by the same /api/admin/** authorization rule as the admin console. */
@RestController
@RequestMapping("/api/admin/question-quality")
@RequiredArgsConstructor
@Validated
public class QuestionQualityController {
    private final JdbcTemplate db;

    @GetMapping
    public Result<List<Map<String,Object>>> queue(@RequestParam(defaultValue="0") @Min(0) long after,
                                                  @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return Result.ok(db.queryForList("SELECT * FROM (SELECT q.id,q.type,q.stem,q.answer,q.analysis,"
                + QuestionSubjects.SUBJECT + " AS subject," + QuestionQuality.REASON + " AS reason,"
                + QuestionQuality.HASH + " AS contentHash,(SELECT GROUP_CONCAT(DISTINCT qs.dataset ORDER BY qs.dataset) "
                + "FROM question_source qs WHERE qs.question_id=q.id) AS datasets "
                + QuestionSubjects.FROM_QUESTION + " WHERE q.id>?) audit WHERE reason!='ELIGIBLE' ORDER BY id LIMIT ?", after,limit));
    }
}
