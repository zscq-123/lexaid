package com.lexaid.stats;

import com.lexaid.content.EntityTitleService;
import com.lexaid.exam.AnswerRecordMapper;
import com.lexaid.stats.dto.OverviewVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    @Test
    void accuracyExcludesSubjectiveAnswersThatWereNotGraded() {
        StudyRecordMapper studyRecordMapper = mock(StudyRecordMapper.class);
        AnswerRecordMapper answerRecordMapper = mock(AnswerRecordMapper.class);
        EntityTitleService entityTitleService = mock(EntityTitleService.class);
        StatsService service = new StatsService(studyRecordMapper, answerRecordMapper, entityTitleService);

        when(answerRecordMapper.totals(7L)).thenReturn(new AnswerRecordMapper.Totals(10, 3, 6, 8, 4));
        when(studyRecordMapper.selectTotalDurationSec(7L)).thenReturn(600);
        when(studyRecordMapper.selectActivityDates(7L)).thenReturn(List.of(LocalDate.now()));

        OverviewVO overview = service.overview(7L);

        assertThat(overview.totalAnswer()).isEqualTo(10);
        assertThat(overview.totalCorrect()).isEqualTo(6);
        assertThat(overview.accuracy()).isEqualTo(75.0);
        assertThat(overview.distinctQuestions()).isEqualTo(3);
        assertThat(overview.repeatedAnswer()).isEqualTo(7);
        assertThat(overview.skippedAnswer()).isEqualTo(4);
        assertThat(overview.totalIncorrect()).isEqualTo(2);
        assertThat(overview.ungradedAnswer()).isEqualTo(2);
    }
}
