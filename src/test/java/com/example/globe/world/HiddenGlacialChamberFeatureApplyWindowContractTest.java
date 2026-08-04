package com.example.globe.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raw-source laws for the two places {@code HiddenGlacialChamberFeature} touches the live world outside the
 * atomic transaction: the pre-apply build window, and the probe's readability test.
 *
 * <p>Neither can be exercised by a running test. The class extends {@code Feature}, whose class
 * initialisation registers into {@code BuiltInRegistries}, so loading it at all needs a booted Minecraft; and
 * both defects are about what happens when a real {@code WorldGenRegion} misbehaves. Source structure is the
 * honest tripwire, and it is the same one this file's siblings already use for the rarity call-site count and
 * the single {@code setBlock}.
 *
 * <h2>What this class exists to stop happening again</h2>
 * <ul>
 *   <li><b>The unbalanced attempt.</b> {@code WriteTelemetry.recordAttempt()} was called, then roughly
 *       nineteen hundred {@code getBlockState} reads and a role lookup ran with no {@code try} around them.
 *       A {@code RuntimeException} anywhere in that window left the audit's attempt counter incremented with
 *       no outcome ever recorded against it, no {@code abortedRolledBack} increment, no census row -- and the
 *       exception propagated straight out into the chunk decoration loop, which is a chunk-generation
 *       crash.</li>
 *   <li><b>{@code ensureCanWrite} as a read predicate.</b> The probe used it to ask "can I read here?", but
 *       26.2's {@code WorldGenRegion.ensureCanWrite} is a WRITE guard: its out-of-zone branch calls
 *       {@code Util.logAndPauseIfInIde}, which logs an error (and pauses in a dev IDE) every single time. On
 *       a legacy-chunk upgrade that fires for every probe read the planner legitimately makes outside the
 *       write radius. {@code isWithinWriteZone} is exactly the same predicate with no side effect.</li>
 * </ul>
 */
class HiddenGlacialChamberFeatureApplyWindowContractTest {

    private static String featureSource() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/example/globe/world/HiddenGlacialChamberFeature.java"));
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. The pre-apply build window is inside a try/catch that balances the telemetry                        */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void thePreApplyBuildWindowIsWrappedAndItsAbortIsFullyAccounted() throws IOException {
        String source = featureSource();

        int attemptAt = source.indexOf("WriteTelemetry.recordAttempt();");
        assertTrue(attemptAt > 0, "the apply path still opens with recordAttempt()");
        int tryAt = source.indexOf("try {", attemptAt);
        int catchAt = source.indexOf("catch (RuntimeException", attemptAt);
        assertTrue(tryAt > attemptAt && catchAt > tryAt,
                "the whole pre-apply build -- the roleState lookups and the ~1900 getBlockState reads -- "
                        + "must sit inside a try/catch(RuntimeException) opened right after recordAttempt(), "
                        + "or a throw there leaves the attempt counter unbalanced AND crashes chunk gen");

        int planWritesAt = source.indexOf("for (HiddenChamberPlan.Cell cell : plan.writes())", attemptAt);
        assertTrue(planWritesAt > tryAt && planWritesAt < catchAt,
                "the planned-write build loop must be INSIDE the guarded window, not before it");
        int applyAt = source.indexOf("apply(level, writes,", attemptAt);
        assertTrue(applyAt > tryAt && applyAt < catchAt,
                "the transaction itself must be inside the same window: a throw out of applyAtomically is "
                        + "the same unbalanced-telemetry bug one step later");

        String handler = source.substring(catchAt, Math.min(source.length(), catchAt + 1400));
        assertTrue(handler.contains("recordOutcome(false, false)"),
                "the abort must record a FAILED outcome with rollback UNVERIFIED -- nothing proved the "
                        + "world is clean, and an attempt with no outcome makes the audit's arithmetic lie");
        assertTrue(handler.contains("abortedRolledBack.incrementAndGet()"),
                "the abort must show up in the process-cumulative counters");
        assertTrue(handler.contains("Outcome.ROLLED_BACK"),
                "the abort must emit its own census row so an audit can see it happened");
        assertTrue(handler.contains("PRE_APPLY"),
                "a pre-apply abort must be distinguishable in the row from a rolled-back transaction: they "
                        + "have different causes and different clean-up guarantees");
        assertTrue(handler.contains("return false;"),
                "the abort must swallow the throw and answer 'no feature here', never propagate into the "
                        + "chunk decoration loop");
        assertTrue(handler.contains("GlobeMod.LOGGER.error"),
                "an unexpected throw on the shipping write path is an ERROR, not a silent skip");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. The probe reads with isWithinWriteZone, never with ensureCanWrite                                   */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void theProbesCellReadUsesTheSideEffectFreeWriteZonePredicate() throws IOException {
        String source = featureSource();

        int cellAt = source.indexOf("public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ)");
        assertTrue(cellAt > 0, "the probe still has its cell() read");
        int cellEnd = source.indexOf("public boolean savedGlacialQuart", cellAt);
        assertTrue(cellEnd > cellAt);
        String cellBody = source.substring(cellAt, cellEnd);

        assertEquals(-1, cellBody.indexOf("ensureCanWrite"),
                "cell() is a READ: ensureCanWrite's out-of-zone branch calls Util.logAndPauseIfInIde, so "
                        + "using it as a readability predicate spams the log on every legitimate out-of-zone "
                        + "probe read (and on every legacy-chunk upgrade)");
        assertTrue(cellBody.contains("withinWriteZone(pos)"),
                "cell() must ask the probe's own side-effect-free write-zone predicate instead");

        int helperAt = source.indexOf("private boolean withinWriteZone(BlockPos pos)");
        assertTrue(helperAt > 0, "the probe owns that predicate");
        String helper = source.substring(helperAt, source.indexOf("\n        }", helperAt));
        assertTrue(helper.contains("isWithinWriteZone(pos)"),
                "the predicate must be 26.2's WorldGenRegion.isWithinWriteZone -- exactly what ensureCanWrite "
                        + "tests before it logs, with nothing attached to it");
        assertTrue(helper.contains("instanceof WorldGenRegion"),
                "isWithinWriteZone lives on WorldGenRegion, not on the WorldGenLevel interface the feature "
                        + "holds, so the probe has to narrow before it can ask");

        assertTrue(source.contains("level.ensureCanWrite(pos)"),
                "the real WRITE guard in writesStillMatch keeps ensureCanWrite: that call site IS a write "
                        + "check and its warning is wanted");
    }
}
