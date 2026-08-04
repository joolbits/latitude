package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two occurrence-law properties {@code HiddenChamberPlanTest.theOccurrenceGateIsOneInFourteenStableAndSeedSensitive}
 * does not cover: that {@link HiddenChamberPlan#plan} itself never calls {@link HiddenChamberPlan#occurrenceGate}
 * (verified by source structure, since the call site count inside {@code HiddenGlacialChamberFeature.java}
 * is already pinned by {@code HiddenGlacialChamberFeatureContractTest}), and neighbourhood independence: a
 * chunk's own accepted plan does not change depending on whether an unrelated, non-overlapping snowy patch
 * (standing in for a neighbour chunk's own mouth) exists elsewhere in the probe window.
 */
class HiddenChamberOccurrenceNeighborhoodTest {

    private static final long SEED = 0x5EED_C0FFEEL;

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. plan() never self-calls occurrenceGate()                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void planNeverCallsOccurrenceGateFromItsOwnBody() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/example/globe/core/HiddenChamberPlan.java"));
        String signature = "public static PlanResult plan(long worldSeed, int chunkX, int chunkZ,";
        int signatureAt = source.indexOf(signature);
        assertTrue(signatureAt >= 0, "the plan() signature must be present verbatim");

        int bodyStart = source.indexOf('{', signatureAt);
        assertTrue(bodyStart >= 0, "plan() must open a body");
        int depth = 0;
        int bodyEnd = -1;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    bodyEnd = index;
                    break;
                }
            }
        }
        assertTrue(bodyEnd > bodyStart, "plan()'s body must close its own braces");

        String body = source.substring(bodyStart, bodyEnd + 1);
        assertFalse(body.contains("occurrenceGate("),
                "plan() must never consult occurrenceGate() itself -- rarity is applied by the world layer "
                        + "exactly once, AFTER a plan is accepted, so it can never change chunk eligibility");
        // Sanity: prove the extraction actually captured a non-trivial method body, so an empty match
        // (a signature-only slice) could never pass the assertion above for a hollow reason.
        assertTrue(body.contains("rankCandidates") && body.contains("collectFootprints"),
                "the extracted body must be plan()'s real implementation, not a truncated slice");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. Neighbourhood independence                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void twoAdjacentChunksEachIndependentlyAcceptWithTheirOwnMouths() {
        HiddenChamberTerrainFixtures.Mutable terrainA = HiddenChamberTerrainFixtures.roomy();
        HiddenChamberPlan.PlanResult resultA = HiddenChamberPlan.plan(SEED, 0, 0, terrainA, null);
        HiddenChamberTerrainFixtures.Mutable terrainB = HiddenChamberTerrainFixtures.roomy();
        HiddenChamberPlan.PlanResult resultB = HiddenChamberPlan.plan(SEED, 1, 0, terrainB, null);

        assertTrue(resultA.isAccepted(), "chunk A must accept its own mouth: " + resultA.detail());
        assertTrue(resultB.isAccepted(), "chunk B must accept its own mouth: " + resultB.detail());
        assertEquals(resultA.accepted().mouthCentroid(), resultB.accepted().mouthCentroid(),
                "planning is chunk-local and stateless: two independently-called owner chunks with the "
                        + "identical terrain shape must land on the identical relative mouth");
    }

    @Test
    void aFarAwaySnowyPatchStandingInForANeighboursOwnMouthNeverChangesThisChunksPlan() {
        HiddenChamberTerrainFixtures.Mutable withoutNeighbourMouth = HiddenChamberTerrainFixtures.roomy();
        HiddenChamberPlan.PlanResult before =
                HiddenChamberPlan.plan(SEED, 4, -9, withoutNeighbourMouth, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertTrue(before.isAccepted(), "the baseline terrain must accept: " + before.detail());

        // A second, independent snowy island far outside this chunk's own anchor-scan range (0..15) and
        // far outside its read/write envelope in any of the four possible directions: exactly what a
        // neighbour chunk's own mouth-eligible terrain would look like from this probe's point of view.
        HiddenChamberTerrainFixtures.Mutable withNeighbourMouth = HiddenChamberTerrainFixtures.roomy();
        withNeighbourMouth.extraSnowyIsland(-38, -33, -38, -33, 40);
        HiddenChamberPlan.PlanResult after =
                HiddenChamberPlan.plan(SEED, 4, -9, withNeighbourMouth, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertTrue(after.isAccepted(), "the terrain plus a neighbour's own mouth must still accept: " + after.detail());

        assertEquals(before.accepted().writes(), after.accepted().writes(),
                "chunk A's own plan must be byte-identical whether or not a neighbour's own, "
                        + "non-overlapping mouth exists elsewhere in the probe window");
        assertEquals(before.accepted().theme(), after.accepted().theme());
        assertEquals(before.accepted().direction(), after.accepted().direction());
    }
}
