package com.lexaid.exam;

import com.lexaid.common.BizException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Bound in-flight generations on one server without queuing duplicate model calls or holding DB locks. */
final class ScenarioGenerationGate {
    record Round(long user, long record, int number) {}
    private final int limit;
    private final Set<Round> inFlight = new HashSet<>();

    ScenarioGenerationGate(int limit) { this.limit = limit; }

    <T> T generate(Round round, Supplier<T> task) {
        synchronized (inFlight) {
            if (inFlight.contains(round)) throw new BizException(409, "这道变式正在准备中，请稍后重新打开。");
            if (inFlight.size() >= limit) throw new BizException(429, "同时生成的练习较多，请稍后再试。");
            inFlight.add(round);
        }
        try {
            return task.get();
        } finally {
            synchronized (inFlight) { inFlight.remove(round); }
        }
    }
}
