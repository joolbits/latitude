package com.example.globe.world;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Deterministic aggregate of every candidate world-safety preflight attempted for one feature call. */
final class PowderTrapWorldSafetyTelemetry {

    private final EnumMap<PowderTrapWorldSafetyFailure, Integer> reasonCounts =
            new EnumMap<>(PowderTrapWorldSafetyFailure.class);
    private int attempts;
    private int acceptedAttempt;

    void record(PowderTrapWorldSafetyResult result) {
        Objects.requireNonNull(result, "result");
        attempts++;
        if (result.isSafe()) {
            if (acceptedAttempt == 0) {
                acceptedAttempt = attempts;
            }
            return;
        }
        if (result.reason() != PowderTrapWorldSafetyFailure.NONE
                && result.reason() != PowderTrapWorldSafetyFailure.NOT_CHECKED) {
            reasonCounts.merge(result.reason(), 1, Integer::sum);
        }
    }

    Map<PowderTrapWorldSafetyFailure, Integer> reasonCounts() {
        EnumMap<PowderTrapWorldSafetyFailure, Integer> snapshot =
                new EnumMap<>(PowderTrapWorldSafetyFailure.class);
        snapshot.putAll(reasonCounts);
        return Collections.unmodifiableMap(snapshot);
    }

    String encodedReasonCounts() {
        if (reasonCounts.isEmpty()) {
            return "none";
        }
        return reasonCounts.entrySet().stream()
                .map(entry -> entry.getKey().name() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    int acceptedAttempt() {
        return acceptedAttempt;
    }

    int attempts() {
        return attempts;
    }
}
