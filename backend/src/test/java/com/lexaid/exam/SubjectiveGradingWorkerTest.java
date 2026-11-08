package com.lexaid.exam;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.ai.DeepSeekGrader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubjectiveGradingWorkerTest {
    private final ExamSessionMapper sessions = mock(ExamSessionMapper.class);
    private final AnswerRecordMapper answers = mock(AnswerRecordMapper.class);
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final DeepSeekGrader grader = mock(DeepSeekGrader.class);
    private final SubjectiveGradingWorker worker = new SubjectiveGradingWorker(
            new TransactionTemplate(mock(PlatformTransactionManager.class)), sessions, answers, questions, grader);
    private final ExamSession session = new ExamSession();
    private final Question question = new Question();

    @BeforeEach
    void prepare() {
        session.setId(1L);
        session.setScore(1);
        session.setCorrect(1);
        AnswerRecord row = new AnswerRecord();
        row.setId(3L);
        row.setQuestionId(2L);
        row.setUserAnswer("考生答案");
        question.setId(2L);
        question.setStem("案例分析");
        question.setAnswer("参考依据");
        when(answers.nextGradingSession()).thenReturn(1L);
        when(sessions.selectByIdForUpdate(1L)).thenReturn(session);
        when(answers.nextGradingBatch(1L)).thenReturn(List.of(row));
        when(questions.selectBatchIds(List.of(2L))).thenReturn(List.of(question));
    }

    @Test
    void awardsPartialPointsOnceAndRejectsAStaleLease() {
        var request = new DeepSeekGrader.Submission(2L, "案例分析", "参考依据", "考生答案");
        when(grader.grade(List.of(request))).thenReturn(Map.of(2L, new DeepSeekGrader.Grade(7, "缺少主观要件。")));
        when(answers.finishGrade(eq(3L), anyString(), eq("AI_GRADED"), eq(7), eq("缺少主观要件。"), eq(false)))
                .thenReturn(1, 0);
        worker.processNextBatch();
        worker.processNextBatch();
        assertThat(session.getScore()).isEqualTo(8);
        assertThat(session.getCorrect()).isEqualTo(1);
        verify(sessions, times(1)).updateById(session);
    }

    @Test
    void providerFailureDoesNotCountAsWrongOrZero() {
        when(grader.grade(anyList())).thenThrow(new DeepSeekClient.UnavailableException("服务暂不可用"));
        worker.processNextBatch();
        verify(answers).finishGrade(eq(3L), anyString(), eq("UNAVAILABLE"), isNull(), eq("服务暂不可用"), isNull());
        verify(sessions, never()).updateById(any(ExamSession.class));
    }

    @Test
    void aRemovedReferenceDoesNotGetSentForGrading() {
        question.setAnswer("");
        worker.processNextBatch();
        verifyNoInteractions(grader);
        verify(answers).finishGrade(eq(3L), anyString(), eq("NO_REFERENCE"), isNull(), anyString(), isNull());
    }

    @Test
    void historicalUnscoredAnswersRemainUnscored() {
        AnswerRecord old = new AnswerRecord();
        assertThat(QuestionGrade.stored(old).status()).isEqualTo("LEGACY_UNGRADED");
        assertThat(QuestionGrade.stored(old).points()).isNull();
        old.setIsCorrect(true);
        assertThat(QuestionGrade.stored(old).points()).isEqualTo(1);
    }
}
