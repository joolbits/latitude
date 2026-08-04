package com.example.globe.world;

import com.example.globe.core.CaveDropTrap;
import com.example.globe.core.HiddenChamberPlan;
import com.example.globe.core.HiddenChamberTerrainFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rollback proof at the transaction layer ({@link CaveDropTrap#applyAtomically}), driven by a REAL accepted
 * {@link HiddenChamberPlan.Plan}'s own write list (not a hand-picked toy scene), plus the raw-source laws
 * the world layer must keep: exactly one {@code applyAtomically(} call site and no {@code setBlock(} in
 * {@code HiddenGlacialChamberFeature.java} outside the one adapter it hands to the transaction.
 */
class HiddenGlacialChamberFeatureRollbackContractTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /** A real accepted plan's own writes, each mapped to a distinct pair of initial/desired token states. */
    private static List<CaveDropTrap.AtomicStateChange<String>> chamberShapedChanges() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertTrue(result.isAccepted(), "the reference terrain must accept: " + result.detail());
        List<HiddenChamberPlan.Cell> writes = result.accepted().writes();
        assertTrue(writes.size() > 20, "a real chamber plan must carry a substantial write list, saw "
                + writes.size());
        List<CaveDropTrap.AtomicStateChange<String>> changes = new ArrayList<>(writes.size());
        for (int index = 0; index < writes.size(); index++) {
            changes.add(new CaveDropTrap.AtomicStateChange<>(
                    "natural-" + index, writes.get(index).role().name() + "-" + index));
        }
        return changes;
    }

    private static List<String> initialStates(List<CaveDropTrap.AtomicStateChange<String>> changes) {
        List<String> states = new ArrayList<>(changes.size());
        for (CaveDropTrap.AtomicStateChange<String> change : changes) {
            states.add(change.expected());
        }
        return states;
    }

    @Test
    void injectingFailureAtTheFirstWriteRollsBackToExactInitialState() {
        List<CaveDropTrap.AtomicStateChange<String>> changes = chamberShapedChanges();
        assertRollbackExact(changes, index -> index == 0, null);
    }

    @Test
    void injectingFailureAtAMiddleWriteRollsBackToExactInitialState() {
        List<CaveDropTrap.AtomicStateChange<String>> changes = chamberShapedChanges();
        int middle = changes.size() / 2;
        assertRollbackExact(changes, index -> index == middle, null);
    }

    @Test
    void injectingFailureAtTheLastWriteRollsBackToExactInitialState() {
        List<CaveDropTrap.AtomicStateChange<String>> changes = chamberShapedChanges();
        int last = changes.size() - 1;
        assertRollbackExact(changes, index -> index == last, null);
    }

    @Test
    void injectingFailureInTheFinalizerRollsBackToExactInitialState() {
        List<CaveDropTrap.AtomicStateChange<String>> changes = chamberShapedChanges();
        assertRollbackExact(changes, null, () -> false);
    }

    /**
     * Runs one chamber-shaped transaction with the given failure seam, and asserts: the transaction
     * reports failure with rollback verified, the fake world's final state is byte-identical to its
     * initial state, and the applied-then-rolled-back count matches exactly the writes that were actually
     * attempted before the injected failure fired.
     */
    private static void assertRollbackExact(List<CaveDropTrap.AtomicStateChange<String>> changes,
                                             java.util.function.IntPredicate failureInjector,
                                             java.util.function.BooleanSupplier finalizerOverride) {
        List<String> initial = initialStates(changes);
        List<String> world = new ArrayList<>(initial);
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger finalizerCalls = new AtomicInteger();

        CaveDropTrap.AtomicStateAdapter<String> adapter = new CaveDropTrap.AtomicStateAdapter<>() {
            @Override
            public String read(int index) {
                return world.get(index);
            }

            @Override
            public boolean write(int index, String state) {
                writeCalls.incrementAndGet();
                world.set(index, state);
                return true;
            }
        };
        java.util.function.BooleanSupplier finalizer = finalizerOverride != null
                ? () -> {
                    finalizerCalls.incrementAndGet();
                    return finalizerOverride.getAsBoolean();
                }
                : () -> {
                    finalizerCalls.incrementAndGet();
                    return true;
                };

        CaveDropTrap.AtomicResult transaction =
                CaveDropTrap.applyAtomically(changes, adapter, failureInjector, finalizer);

        assertFalse(transaction.success(), "an injected failure must never report success");
        assertTrue(transaction.rollbackVerified(), "every applied write must be verified restored");
        assertEquals(initial, world, "the fake world's final state must be byte-identical to its initial state");

        if (failureInjector != null) {
            // Every index strictly before the injected failure was written once (forward) then restored
            // once (reverse): exactly two write() calls per applied-then-rolled-back index.
            int failedAt = firstFailingIndex(changes.size(), failureInjector);
            assertEquals(failedAt * 2, writeCalls.get(),
                    "expected " + failedAt + " applied-then-rolled-back writes (two calls each)");
            assertEquals(0, finalizerCalls.get(), "the finalizer must never run after an earlier write fails");
        } else {
            // Every write succeeded (forward), the finalizer then failed, and every applied write was
            // rolled back (reverse): two write() calls per index, and the finalizer ran exactly once.
            assertEquals(changes.size() * 2, writeCalls.get(),
                    "expected every write applied then rolled back after the finalizer failed");
            assertEquals(1, finalizerCalls.get());
        }
    }

    private static int firstFailingIndex(int size, java.util.function.IntPredicate failureInjector) {
        for (int index = 0; index < size; index++) {
            if (failureInjector.test(index)) {
                return index;
            }
        }
        throw new IllegalStateException("failureInjector never fired within " + size + " writes");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Raw-source laws                                                                                       */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void exactlyOneApplyAtomicallyCallSiteAndNoOtherSetBlockOutsideItsAdapter() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/example/globe/world/HiddenGlacialChamberFeature.java"));
        assertEquals(1, occurrences(source, "applyAtomically("),
                "exactly one applyAtomically( call site: the atomic transaction must be the single write path");
        assertEquals(1, occurrences(source, "setBlock("),
                "the only setBlock( in this file must be inside the AtomicStateAdapter passed to applyAtomically");

        int applyAtomicallyAt = source.indexOf("applyAtomically(");
        int setBlockAt = source.indexOf("setBlock(");
        assertTrue(setBlockAt >= 0 && applyAtomicallyAt >= 0);
        assertTrue(applyAtomicallyAt < setBlockAt,
                "the only setBlock( must sit textually AFTER applyAtomically( -- it is the write() method "
                        + "of the AtomicStateAdapter defined inline as one of applyAtomically('s own arguments");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int cursor = haystack.indexOf(needle);
        while (cursor >= 0) {
            count++;
            cursor = haystack.indexOf(needle, cursor + needle.length());
        }
        return count;
    }
}
