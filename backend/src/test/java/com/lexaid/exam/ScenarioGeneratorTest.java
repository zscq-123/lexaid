package com.lexaid.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScenarioGeneratorTest {
    private final DeepSeekClient ai = mock(DeepSeekClient.class);
    private final ScenarioGenerator generator = new ScenarioGenerator(ai, new ObjectMapper());
    private final ScenarioGenerator.Source source = new ScenarioGenerator.Source(
            "从原题给出的事实中识别双方约定的具体法律要件，以下哪一个选项符合参考依据？",
            "[{\"key\":\"A\",\"text\":\"错误的干扰选项\"},{\"key\":\"B\",\"text\":\"正确选项提到的条件\"}]",
            "[\"B\"]", "参考解析明确了题目应考察的具体条件。", "法条中包含相邻的另一项规则。", "民法");
    private static final String reply = """
            {"topic":"约定条件的识别","stem":"某展览馆和摄影师约定一组履行条件，交付当天双方各自完成了不同步骤。结合以下信息，应当如何判断？",
            "options":["一项条件","另一项条件","二者均成立","都不成立"],"answer":"A","basis":"正确选项提到的条件",
            "explanation":"根据新设事实应当逐项核对条件。","comparison":"原题直接选择规则，新题要结合履行步骤判断。"}
            """;

    @Test
    void returnsScopedExerciseWithExactEvidence() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), eq(true))).thenReturn(reply);
        var exercise = generator.generate(source, "", 1);
        assertThat(exercise.origin()).isEqualTo("AI 生成");
        assertThat(exercise.options()).hasSize(4);
        assertThat(exercise.basis()).isEqualTo("正确选项提到的条件");
    }

    @Test
    void rejectsHallucinatedEvidenceDistractorsAndCopiedScenarios() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), eq(true))).thenReturn(
                reply.replace("正确选项提到的条件", "不存在的法规依据"), reply.replace("正确选项提到的条件", "法条中包含相邻的另一项规则"), reply.replace("正确选项提到的条件", "错误的干扰选项"),
                reply.replace("另一项条件", "一项条件"), reply.replace("某展览馆和摄影师约定一组履行条件，交付当天双方各自完成了不同步骤。结合以下信息，应当如何判断？", source.stem()),
                reply.replace("约定条件的识别", "民法"), reply);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> generator.generate(source, "", 1)).isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> generator.generate(source, "某展览馆和摄影师约定一组履行条件，交付当天双方各自完成了不同步骤。结合以下信息，应当如何判断？", 2))
                .isInstanceOf(BizException.class);
    }

    @Test
    void unavailableOrUnusableSourceDoesNotBecomeAQuestion() {
        assertThatThrownBy(() -> generator.generate(source, "", 1)).isInstanceOf(BizException.class);
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), eq(true))).thenReturn("{\"unavailable\":true}", "{\"answer\":2}");
        assertThatThrownBy(() -> generator.generate(source, "", 1)).hasMessageContaining("原题材料不足");
        assertThatThrownBy(() -> generator.generate(source, "", 1)).hasMessageContaining("校验");
    }
}
