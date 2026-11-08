package com.lexaid.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.BizException;
import com.lexaid.common.PageResult;
import com.lexaid.common.TextUtil;
import com.lexaid.common.UserContext;
import com.lexaid.exam.dto.AnswerDetailVO;
import com.lexaid.exam.dto.AnswerItem;
import com.lexaid.exam.dto.ExamQuestionVO;
import com.lexaid.exam.dto.ExamResultVO;
import com.lexaid.exam.dto.ExamSessionVO;
import com.lexaid.exam.dto.ExamSetupRequest;
import com.lexaid.exam.dto.QuestionCategoryCount;
import com.lexaid.exam.dto.QuestionOption;
import com.lexaid.exam.dto.SubmitRequest;
import com.lexaid.exam.dto.WrongQuestionVO;
import com.lexaid.relation.EntityRelation;
import com.lexaid.relation.EntityRelationMapper;
import com.lexaid.relation.RelatedItem;
import com.lexaid.relation.RelationService;
import com.lexaid.stats.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 答题服务：组卷、取卷、判分、错题
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final QuestionMapper questionMapper;
    private final ExamSessionMapper examSessionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final EntityRelationMapper entityRelationMapper;
    private final RelationService relationService;
    private final StudyRecordService studyRecordService;
    private final ObjectMapper objectMapper;

    public List<QuestionCategoryCount> categoryCounts() {
        return questionMapper.categoryCounts();
    }

    /**
     * 组卷：按条件随机选题，支持按实体优先取关联题
     */
    public Long createSession(ExamSetupRequest req) {
        int count = req.getCount();
        List<Long> chosen = new ArrayList<>();
        if (StringUtils.hasText(req.getSourceType()) && req.getSourceId() != null) {
            List<Long> relatedIds = relatedQuestionIds(req.getSourceType(), req.getSourceId());
            if (!relatedIds.isEmpty()) {
                List<Long> candidates = questionMapper.selectList(buildWrapper(req.getCategory(), req.getDifficulty())
                                .in(Question::getId, relatedIds)
                                .last("ORDER BY RAND() LIMIT " + count))
                        .stream().map(Question::getId).toList();
                chosen.addAll(candidates);
            }
        }
        int remain = count - chosen.size();
        if (remain > 0) {
            LambdaQueryWrapper<Question> fillQw = buildWrapper(req.getCategory(), req.getDifficulty());
            if (!chosen.isEmpty()) {
                fillQw.notIn(Question::getId, chosen);
            }
            fillQw.last("ORDER BY RAND() LIMIT " + remain);
            chosen.addAll(questionMapper.selectList(fillQw).stream().map(Question::getId).toList());
        }
        if (chosen.isEmpty()) {
            throw new BizException("没有符合条件的题目");
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("category", req.getCategory());
        config.put("difficulty", req.getDifficulty());
        config.put("count", count);
        config.put("sourceType", req.getSourceType());
        config.put("sourceId", req.getSourceId());
        config.put("questionIds", chosen);
        String configJson;
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("组卷配置序列化失败", e);
        }
        ExamSession session = new ExamSession();
        session.setUserId(UserContext.userId());
        session.setConfig(configJson);
        session.setScore(0);
        session.setTotal(chosen.size());
        session.setCorrect(0);
        session.setStartTime(LocalDateTime.now());
        examSessionMapper.insert(session);
        return session.getId();
    }

    private LambdaQueryWrapper<Question> buildWrapper(String category, Integer difficulty) {
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>().select(Question::getId);
        if (StringUtils.hasText(category)) {
            qw.eq(Question::getCategory, category);
        }
        if (difficulty != null) {
            qw.eq(Question::getDifficulty, difficulty);
        }
        return qw;
    }

    /**
     * 查询与某实体关联的题目 id（双向）
     */
    private List<Long> relatedQuestionIds(String sourceType, Long sourceId) {
        List<EntityRelation> rels = entityRelationMapper.selectList(
                new LambdaQueryWrapper<EntityRelation>()
                        .and(w -> w.eq(EntityRelation::getSourceType, "QUESTION")
                                .eq(EntityRelation::getTargetType, sourceType)
                                .eq(EntityRelation::getTargetId, sourceId)
                                .or()
                                .eq(EntityRelation::getTargetType, "QUESTION")
                                .eq(EntityRelation::getSourceType, sourceType)
                                .eq(EntityRelation::getSourceId, sourceId)));
        Set<Long> ids = new LinkedHashSet<>();
        for (EntityRelation r : rels) {
            if ("QUESTION".equals(r.getSourceType())) {
                ids.add(r.getSourceId());
            } else {
                ids.add(r.getTargetId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 取卷：返回不含答案与解析的题目
     */
    public List<ExamQuestionVO> questionsOf(Long sessionId) {
        ExamSession session = requireOwnedSession(sessionId);
        List<Question> questions = loadQuestions(session);
        return questions.stream().map(this::toExamQuestion).toList();
    }

    /**
     * 交卷判分
     */
    @Transactional
    public ExamResultVO submit(Long sessionId, SubmitRequest req) {
        ExamSession session = lockOwnedSession(sessionId);
        if (session.getEndTime() != null) return resultOf(sessionId);
        List<Question> questions = loadQuestions(session);
        Map<Long, String> answers = new HashMap<>();
        for (AnswerItem answer : req.getAnswers()) {
            answers.put(answer.getQuestionId(), answer.getAnswer());
        }

        LocalDateTime now = LocalDateTime.now();
        int score = 0;
        int correct = 0;
        for (Question question : questions) {
            String answer = answers.get(question.getId());
            QuestionGrade grade = initialGrade(question, answer);
            Boolean fullCredit = grade.points() == null ? null : grade.points() == QuestionGrade.maxPoints(question.getType());
            if (grade.points() != null) score += grade.points();
            if (Boolean.TRUE.equals(fullCredit)) correct++;
            AnswerRecord row = new AnswerRecord();
            row.setSessionId(sessionId);
            row.setUserId(session.getUserId());
            row.setQuestionId(question.getId());
            row.setUserAnswer(answer);
            row.setIsCorrect(fullCredit);
            row.setGradingStatus(grade.status());
            row.setGradePoints(grade.points());
            row.setGradingFeedback(grade.feedback());
            row.setAnsweredAt(now);
            answerRecordMapper.insert(row);
        }

        session.setScore(score);
        session.setCorrect(correct);
        session.setTotal(questions.size());
        session.setEndTime(now);
        session.setDurationSec((int) Duration.between(session.getStartTime(), now).getSeconds());
        examSessionMapper.updateById(session);
        studyRecordService.record(session.getUserId(), "ANSWER", "QUESTION", null, "提交试卷，共 " + questions.size() + " 题");
        return resultOf(sessionId);
    }

    @Transactional
    public ExamResultVO retryGrading(Long sessionId) {
        ExamSession session = lockOwnedSession(sessionId);
        if (session.getEndTime() == null) throw new BizException("该试卷尚未交卷");
        answerRecordMapper.retryUnavailable(sessionId);
        return resultOf(sessionId);
    }

    private ExamSession lockOwnedSession(Long sessionId) {
        ExamSession session = examSessionMapper.selectByIdForUpdate(sessionId);
        checkOwner(session);
        return session;
    }

    /** Re-read one committed snapshot while the background worker may be updating scores. */
    @Transactional(readOnly = true)
    public ExamResultVO resultOf(Long sessionId) {
        ExamSession session = requireOwnedSession(sessionId);
        if (session.getEndTime() == null) throw new BizException("该试卷尚未交卷");
        Map<Long, AnswerRecord> records = answerRecordMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getSessionId, sessionId).orderByAsc(AnswerRecord::getId))
                .stream().collect(Collectors.toMap(AnswerRecord::getQuestionId, r -> r, (older, newer) -> newer));
        int maxScore = 0;
        int gradedCount = 0;
        List<AnswerDetailVO> details = new ArrayList<>();
        for (Question q : loadQuestions(session)) {
            AnswerRecord ar = records.get(q.getId());
            QuestionGrade grade = QuestionGrade.stored(ar);
            int max = QuestionGrade.maxPoints(q.getType());
            if (grade.points() != null) {
                maxScore += max;
                gradedCount++;
            }
            details.add(new AnswerDetailVO(q.getId(), q.getType(), q.getStem(), parseOptions(q),
                    ar == null ? null : ar.getUserAnswer(), displayAnswer(q),
                    ar == null ? null : ar.getIsCorrect(), q.getAnalysis(), relatedItems(q.getId()),
                    grade.points(), max, grade.status(), grade.feedback()));
        }
        return new ExamResultVO(sessionId, session.getScore(), session.getTotal(), session.getCorrect(),
                gradedCount, details, maxScore);
    }

    /**
     * 历史列表
     */
    public PageResult<ExamSessionVO> history(long page, long size) {
        Page<ExamSession> p = examSessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExamSession>()
                        .eq(ExamSession::getUserId, UserContext.userId())
                        .orderByDesc(ExamSession::getId));
        List<ExamSessionVO> vos = p.getRecords().stream().map(this::toSessionVO).toList();
        return new PageResult<>(vos, p.getTotal());
    }

    /**
     * 错题本：最近错题分页（Java 内存分页）
     */
    public PageResult<WrongQuestionVO> wrongQuestions(long page, long size, boolean all) {
        List<Long> ids = all
                ? answerRecordMapper.selectLatestAnsweredQuestionIds(UserContext.userId())
                : answerRecordMapper.selectLatestWrongQuestionIds(UserContext.userId());
        int from = (int) Math.min((page - 1) * size, ids.size());
        int to = (int) Math.min(page * size, ids.size());
        List<Long> pageIds = ids.subList(from, to);
        if (pageIds.isEmpty()) {
            return new PageResult<>(List.of(), ids.size());
        }
        List<Question> questions = questionMapper.selectBatchIds(pageIds);
        Map<Long, Question> qMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        // 最近一次错答内容
        Map<Long, AnswerRecord> lastAnswer = new HashMap<>();
        for (Long qid : pageIds) {
            AnswerRecord ar = answerRecordMapper.selectOne(new LambdaQueryWrapper<AnswerRecord>()
                    .eq(AnswerRecord::getUserId, UserContext.userId())
                    .eq(AnswerRecord::getQuestionId, qid)
                    .orderByDesc(AnswerRecord::getId)
                    .last("LIMIT 1"));
            if (ar != null) {
                lastAnswer.put(qid, ar);
            }
        }
        List<WrongQuestionVO> vos = new ArrayList<>();
        for (Long qid : pageIds) {
            Question q = qMap.get(qid);
            if (q == null) {
                continue;
            }
            AnswerRecord ar = lastAnswer.get(qid);
            QuestionGrade grade = QuestionGrade.stored(ar);
            vos.add(new WrongQuestionVO(q.getId(), q.getType(), q.getStem(), parseOptions(q),
                    ar == null ? null : ar.getUserAnswer(), displayAnswer(q), q.getAnalysis(),
                    q.getCategory(), ar == null ? null : ar.getIsCorrect(), relatedItems(q.getId()),
                    grade.points(), QuestionGrade.maxPoints(q.getType()), grade.status(), grade.feedback()));
        }
        return new PageResult<>(vos, ids.size());
    }

    private ExamSession requireOwnedSession(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        checkOwner(session);
        return session;
    }

    private void checkOwner(ExamSession session) {
        if (session == null) {
            throw new BizException("试卷不存在");
        }
        if (!session.getUserId().equals(UserContext.userId())) {
            throw new BizException(403, "无权访问他人的试卷");
        }
    }

    private List<Question> loadQuestions(ExamSession session) {
        try {
            Map<String, Object> config = objectMapper.readValue(session.getConfig(),
                    new TypeReference<>() {
                    });
            Object idsObj = config.get("questionIds");
            if (idsObj instanceof List<?> list) {
                List<Long> ids = list.stream()
                        .map(v -> ((Number) v).longValue())
                        .toList();
                if (!ids.isEmpty()) {
                    Map<Long, Question> map = questionMapper.selectBatchIds(ids).stream()
                            .collect(Collectors.toMap(Question::getId, q -> q));
                    List<Question> ordered = new ArrayList<>();
                    for (Long id : ids) {
                        Question q = map.get(id);
                        if (q != null) {
                            ordered.add(q);
                        }
                    }
                    return ordered;
                }
            }
        } catch (Exception e) {
            throw new BizException("试卷数据异常");
        }
        throw new BizException("试卷数据异常");
    }

    private ExamQuestionVO toExamQuestion(Question q) {
        return new ExamQuestionVO(q.getId(), q.getType(), q.getStem(),
                parseOptions(q), q.getDifficulty(), q.getCategory(), relatedItems(q.getId()));
    }

    private ExamSessionVO toSessionVO(ExamSession s) {
        String category = null;
        Integer difficulty = null;
        try {
            Map<String, Object> config = objectMapper.readValue(s.getConfig(), new TypeReference<>() {
            });
            category = (String) config.get("category");
            difficulty = config.get("difficulty") == null ? null : ((Number) config.get("difficulty")).intValue();
        } catch (Exception ignored) {
        }
        return new ExamSessionVO(s.getId(), s.getTotal(), s.getCorrect(), s.getScore(),
                s.getDurationSec(), category, difficulty, s.getStartTime(), s.getEndTime());
    }

    private QuestionGrade initialGrade(Question q, String answer) {
        if (!StringUtils.hasText(q.getAnswer())) {
            return new QuestionGrade("NO_REFERENCE", null, "题库未提供参考答案，此题不计入得分。");
        }
        if ("SHORT".equals(q.getType())) {
            if (!StringUtils.hasText(answer)) return new QuestionGrade("BLANK", 0, "未作答。");
            return new QuestionGrade("PENDING", null, null);
        }
        Boolean correct = grade(q, answer);
        if (correct == null) return new QuestionGrade("LEGACY_UNGRADED", null, "此题型暂不支持评分。");
        return new QuestionGrade("OBJECTIVE", correct ? 1 : 0, null);
    }

    /**
     * 客观题判分：单选/判断严格相等，多选全对得分
     */
    private Boolean grade(Question q, String userAnswer) {
        return switch (q.getType()) {
            case "SINGLE" -> {
                List<String> keys = parseAnswerKeys(q);
                yield !keys.isEmpty() && Objects.equals(userAnswer, keys.get(0));
            }
            case "MULTIPLE" -> {
                List<String> correct = parseAnswerKeys(q);
                List<String> user = parseUserKeys(userAnswer);
                Collections.sort(correct);
                Collections.sort(user);
                yield !correct.isEmpty() && correct.equals(user);
            }
            case "JUDGE" -> Objects.equals(q.getAnswer(), userAnswer);
            default -> null;
        };
    }

    private List<String> parseAnswerKeys(Question q) {
        try {
            return objectMapper.readValue(q.getAnswer(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of(q.getAnswer() == null ? "" : q.getAnswer());
        }
    }

    private List<String> parseUserKeys(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(answer, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of(answer);
        }
    }

    private List<QuestionOption> parseOptions(Question q) {
        if (!StringUtils.hasText(q.getOptions())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(q.getOptions(), new TypeReference<List<QuestionOption>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 展示用正确答案
     */
    private String displayAnswer(Question q) {
        return switch (q.getType()) {
            case "SINGLE", "MULTIPLE" -> String.join(",", parseAnswerKeys(q));
            case "JUDGE" -> "true".equalsIgnoreCase(q.getAnswer()) ? "正确" : "错误";
            case "SHORT" -> q.getAnswer();
            default -> TextUtil.excerpt(q.getAnswer(), 200);
        };
    }

    private List<RelatedItem> relatedItems(Long questionId) {
        var groups = relationService.relatedGroups("QUESTION", questionId);
        List<RelatedItem> items = new ArrayList<>();
        items.addAll(groups.provisions());
        items.addAll(groups.cases());
        items.addAll(groups.viewpoints());
        return items;
    }
}
