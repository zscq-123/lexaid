package com.lexaid.exam;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ScenarioGenerationGateTest {
    @Test
    void concurrentDuplicateDoesNotCallModelOrHoldUpOtherLearners() throws Exception {
        var gate = new ScenarioGenerationGate(2);
        var first = new ScenarioGenerationGate.Round(1, 21, 1);
        var other = new ScenarioGenerationGate.Round(2, 41, 1);
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var pending = CompletableFuture.supplyAsync(() -> gate.generate(first, () -> {
            calls.incrementAndGet();
            entered.countDown();
            try { return finish.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        }));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> gate.generate(first, calls::incrementAndGet)).isInstanceOf(BizException.class);
            assertThat(calls).hasValue(1);
            assertThat(gate.generate(other, () -> "other")).isEqualTo("other");
        } finally { finish.countDown(); }
        assertThat(pending.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(gate.generate(first, () -> "retry")).isEqualTo("retry");
    }

    @Test
    void capacityIsBoundedAndErrorsReleaseTheSlot() {
        var gate = new ScenarioGenerationGate(1);
        var first = new ScenarioGenerationGate.Round(1, 21, 1);
        var other = new ScenarioGenerationGate.Round(2, 41, 1);
        assertThatThrownBy(() -> gate.generate(first, () -> gate.generate(other, () -> "called")))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(429));
        assertThat(gate.generate(other, () -> "ready")).isEqualTo("ready");
    }
}
