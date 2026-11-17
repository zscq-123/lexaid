package com.lexaid.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.BizException;
import com.lexaid.common.LoginUser;
import com.lexaid.exam.dto.SubmitRequest;
import com.lexaid.relation.RelatedGroups;
import com.lexaid.relation.RelationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamQualityLifecycleTest {
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final ExamSessionMapper sessions = mock(ExamSessionMapper.class);
    private final AnswerRecordMapper answers = mock(AnswerRecordMapper.class);
    private final RelationService relations = mock(RelationService.class);
    private final ExamService service = new ExamService(questions, sessions, answers, null, relations, null, new ObjectMapper());
    private final ExamSession exam = new ExamSession();
    private final AnswerRecord answer = new AnswerRecord();

    @BeforeEach
    void prepare() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(9L, "review-test", "", "USER"), null, List.of()));
        exam.setId(1L); exam.setUserId(9L); exam.setConfig("{\"questionIds\":[2]}");
        exam.setScore(1); exam.setCorrect(1); exam.setTotal(1);
        when(sessions.selectById(1L)).thenReturn(exam);
        when(sessions.selectByIdForUpdate(1L)).thenReturn(exam);
        var question = new Question();
        question.setId(2L); question.setType("JUDGE"); question.setStem("保留的题干");
        question.setAnswer("true"); question.setAnalysis("待复核解析");
        when(questions.classifiedQuestions(List.of(2L))).thenReturn(List.of(question));
        when(questions.eligibleIds(List.of(2L))).thenReturn(List.of());
        when(relations.relatedGroups("QUESTION", 2L)).thenReturn(new RelatedGroups(List.of(),List.of(),List.of(),List.of()));
        answer.setId(3L); answer.setQuestionId(2L); answer.setUserAnswer("false");
        answer.setGradingStatus("OBJECTIVE"); answer.setGradePoints(0); answer.setIsCorrect(false);
        answer.setGradingFeedback("待复核评分解释");
        when(answers.selectList(any())).thenReturn(List.of(answer));
        when(answers.selectLatestWrongQuestionIds(9L)).thenReturn(List.of(2L));
        when(answers.latestAnswer(9L,2L)).thenReturn(answer);
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void previouslyCreatedSessionsCannotLoadOrSubmitQuarantinedQuestions() {
        assertThatThrownBy(() -> service.questionsOf(1L)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(409));
        var submission = new SubmitRequest(); submission.setAnswers(List.of());
        assertThatThrownBy(() -> service.submit(1L, submission)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(409));
        verify(answers, never()).insert(any(AnswerRecord.class));
        verify(sessions, never()).updateById(any(ExamSession.class));
    }

    @Test
    void completedResultsAndWrongBookKeepUserHistoryWithoutUnreviewedAnswers() {
        exam.setEndTime(LocalDateTime.now());
        var result = service.resultOf(1L);
        assertThat(result.details()).singleElement().satisfies(detail -> {
            assertThat(detail.userAnswer()).isEqualTo("false");
            assertThat(detail.score()).isZero();
            assertThat(detail.practiceEligible()).isFalse();
            assertThat(detail.correctAnswer()).isNull();
            assertThat(detail.analysis()).isNull();
            assertThat(detail.feedback()).isNull();
        });
        assertThat(result.score()).isZero();
        assertThat(result.correct()).isZero();
        assertThat(service.wrongQuestions(1,10,false).getRecords()).singleElement().satisfies(detail -> {
            assertThat(detail.userAnswer()).isEqualTo("false");
            assertThat(detail.correctAnswer()).isNull();
            assertThat(detail.analysis()).isNull();
            assertThat(detail.feedback()).isNull();
        });
    }

    @Test
    void historicalBlankRowsAndPendingQuarantineAreNotPresentedAsGraded() {
        exam.setEndTime(LocalDateTime.now());
        answer.setUserAnswer("　 "); answer.setIsCorrect(true); answer.setGradePoints(1);
        var skipped = service.resultOf(1L);
        assertThat(skipped.score()).isZero(); assertThat(skipped.maxScore()).isZero();
        assertThat(skipped.correct()).isZero(); assertThat(skipped.gradedTotal()).isZero();
        answer.setUserAnswer("已答"); answer.setIsCorrect(null); answer.setGradePoints(null);
        answer.setGradingStatus("PENDING");
        assertThat(service.resultOf(1L).details()).singleElement().satisfies(detail ->
                assertThat(detail.gradingStatus()).isEqualTo("QUARANTINED"));
    }
}
