package com.lexaid.exam;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AnswerStatusTest {
    @Test
    void blankAndClearedChoicesAreSkippedButFalseAndWrittenBracketsAreAnswers() {
        for (String answer : new String[]{null,""," \n\t　"," [ \n ] "}) assertThat(AnswerStatus.answered("MULTIPLE",answer)).isFalse();
        assertThat(AnswerStatus.answered("JUDGE","false")).isTrue();
        assertThat(AnswerStatus.answered("SHORT","[]")).isTrue();
        var old = new AnswerRecord();
        old.setUserAnswer("[]"); old.setIsCorrect(false); old.setGradePoints(0); old.setGradingStatus("OBJECTIVE");
        assertThat(QuestionGrade.stored(old,"MULTIPLE").status()).isEqualTo("SKIPPED");
        assertThat(QuestionGrade.stored(old,"MULTIPLE").points()).isNull();
    }
}
