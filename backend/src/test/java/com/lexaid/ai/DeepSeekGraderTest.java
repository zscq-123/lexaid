package com.lexaid.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekGraderTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final DeepSeekGrader grader = new DeepSeekGrader(client, new ObjectMapper());
    private final List<DeepSeekGrader.Submission> questions = List.of(
            new DeepSeekGrader.Submission(11, "题甲", "答案甲", "作答甲"),
            new DeepSeekGrader.Submission(12, "题乙", "答案乙", "作答乙"));

    @Test
    void matchesGradesByQuestionIdEvenWhenReturnedOutOfOrder() {
        when(client.complete(anyList(), eq(true))).thenReturn("""
                {"grades":[{"questionId":12,"score":0,"feedback":"未回答本题。"},
                           {"questionId":11,"score":7,"feedback":"遗漏一个要点。"}]}
                """);
        assertThat(grader.grade(questions)).containsExactlyInAnyOrderEntriesOf(Map.of(
                11L, new DeepSeekGrader.Grade(7, "遗漏一个要点。"), 12L, new DeepSeekGrader.Grade(0, "未回答本题。")));
    }

    @Test
    void rejectsAnInvalidBatchWithoutReturningPartialOrGuessedGrades() {
        String first = "{\"questionId\":11,\"score\":7,\"feedback\":\"遗漏一个要点。\"}";
        String second = "{\"questionId\":12,\"score\":5,\"feedback\":\"理由不充分。\"}";
        for (String malformed : List.of(
                "[" + first + "]",
                "[" + first + "," + first + "]",
                "[" + first + "," + second.replace("12", "13") + "]",
                "[" + first + "," + second.replace("5", "11") + "]",
                "[" + first + "," + second.replace("5", "\"5\"") + "]",
                "[" + first + "," + second.replace("理由不充分。", " ") + "]",
                "[" + first + "," + second + "]} trailing")) {
            when(client.complete(anyList(), eq(true))).thenReturn("{\"grades\":" + malformed + "}");
            assertThatThrownBy(() -> grader.grade(questions)).isInstanceOf(DeepSeekClient.UnavailableException.class);
        }
    }
}
