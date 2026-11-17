package com.lexaid.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MultipleChoiceGradingTest {
    private final ExamService grading = new ExamService(null, null, null, null, null, null, new ObjectMapper());

    private Integer score(String reference, String answer) {
        var question = new Question();
        question.setType("MULTIPLE");
        question.setAnswer(reference);
        return grading.initialGrade(question, answer).points();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t　\n", "[]", "[ ]"})
    void omittedAnswersAreSkippedAndExcludedFromScoring(String answer) {
        assertThat(score("[\"A\",\"B\"]", answer)).isNull();
    }

    @Test
    void gradingAcceptsDifferentChoiceOrderAndLegacyPlainKeys() {
        assertThat(score("[\"A\",\"B\"]", "[\"B\",\"A\"]")).isEqualTo(1);
        assertThat(score("A", "A")).isEqualTo(1);
        assertThat(score("[\"A\",\"B\"]", "[\"A\",\"A\"]")).isZero();
        assertThat(score("[\"A\",\"B\"]", "null")).isZero();
        assertThat(score("[\"A\",\"B\"]", "unexpected choice")).isZero();
    }
}
