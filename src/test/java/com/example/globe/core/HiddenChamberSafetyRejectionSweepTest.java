package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety and rejection-attribution completeness: every {@link HiddenChamberPlan.CellKind} hazard drives the
 * SPECIFIC {@link HiddenChamberPlan.RejectionDetail} it owns, a foreign biome quart at several distinct
 * positions fails closed each time, and a 200-plan sweep re-verifies the structural write-list invariants
 * ({@code HiddenChamberPlanTest}'s own per-invariant checks only ever exercise one hand-picked seed/chunk).
 *
 * <p>Hazards are injected as a Y-BAND across the WHOLE owner chunk ({@code x,z} in {@code 0..15}), not at a
 * single discovered cell: {@link HiddenChamberTerrainFixtures#narrow()} still admits several candidate
 * mouths (different anchors/masks/rotations) that tie for best, so a single-cell hazard just makes the
 * planner fall through to an alternate candidate elsewhere in the SAME owner chunk rather than reject the
 * chunk outright (confirmed empirically against the real class before writing this). A band that is
 * guaranteed to sit inside ANY possible mouth's shaft/mouth-roof range, for every column in the owner
 * chunk, has nowhere left to fall through to.
 */
class HiddenChamberSafetyRejectionSweepTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;
    /** Mid-shaft for {@code narrow()}'s floor (40): inside every achievable drop's shaft range (12..20). */
    private static final int SHAFT_BAND_Y = 34;
    /** One above the mouth's own floor (40): where "gravity above a carved cell" is checked. */
    private static final int ABOVE_MOUTH_Y = 41;

    @Test
    void everyNonGravityHazardKindDrivesItsOwnUnsafeVolumeDetail() {
        record Case(HiddenChamberPlan.CellKind kind, HiddenChamberPlan.RejectionDetail detail) {
        }
        for (Case testCase : new Case[] {
                new Case(HiddenChamberPlan.CellKind.FLUID, HiddenChamberPlan.RejectionDetail.FLUID_IN_VOLUME),
                new Case(HiddenChamberPlan.CellKind.BLOCK_ENTITY, HiddenChamberPlan.RejectionDetail.BLOCK_ENTITY_IN_VOLUME),
                new Case(HiddenChamberPlan.CellKind.BEDROCK, HiddenChamberPlan.RejectionDetail.BEDROCK_IN_VOLUME),
                new Case(HiddenChamberPlan.CellKind.SOLID_UNSAFE, HiddenChamberPlan.RejectionDetail.UNSAFE_SOLID_IN_VOLUME),
                new Case(HiddenChamberPlan.CellKind.UNREADABLE, HiddenChamberPlan.RejectionDetail.UNREADABLE_IN_VOLUME)}) {
            HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.narrow();
            bandHazard(terrain, SHAFT_BAND_Y, testCase.kind());
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
            assertFalse(result.isAccepted(), testCase.kind() + " must reject the whole chunk");
            assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection(), testCase.kind().toString());
            assertEquals(testCase.detail(), result.detail(),
                    testCase.kind() + " must be visible in the rejection detail");
        }
    }

    @Test
    void gravityAboveACarvedCellDrivesGravityAboveCarvedCellDetail() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.narrow();
        bandHazard(terrain, ABOVE_MOUTH_Y, HiddenChamberPlan.CellKind.GRAVITY);
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertFalse(result.isAccepted(), "gravity above every possible mouth's roof must reject the chunk");
        assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection());
        assertEquals(HiddenChamberPlan.RejectionDetail.GRAVITY_ABOVE_CARVED_CELL, result.detail());
    }

    /** Hazards every owner-local column at one Y, the whole owner chunk wide -- see the class doc for why. */
    private static void bandHazard(HiddenChamberTerrainFixtures.Mutable terrain, int y, HiddenChamberPlan.CellKind kind) {
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 15; z++) {
                terrain.hazardAt(x, y, z, kind);
            }
        }
    }

    @Test
    void aForeignBiomeQuartAtSeveralDistinctPositionsEachFailsClosed() {
        // Different quartY bands under the same narrow plateau: each is, independently, somewhere inside
        // the shaft's achievable Y range for SOME legal drop, so each alone must still fail the whole
        // chunk closed -- proving the check applies uniformly across the volume, not just one lucky depth.
        for (int quartY : new int[] {6, 7, 8, 9}) {
            HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.narrow();
            for (int quartX = 0; quartX <= 3; quartX++) {
                for (int quartZ = 0; quartZ <= 3; quartZ++) {
                    terrain.foreignQuartAt(quartX, quartY, quartZ);
                }
            }
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
            assertFalse(result.isAccepted(), "quartY=" + quartY + " foreign band must reject the whole chunk");
            assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection(), "quartY=" + quartY);
            assertEquals(HiddenChamberPlan.RejectionDetail.BIOME_QUART_NOT_GLACIAL, result.detail(),
                    "quartY=" + quartY);
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 200-plan structural sweep                                                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void twoHundredAcceptedPlansAcrossSeedsKeepEveryStructuralWriteInvariant() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
        int checked = 0;
        outer:
        for (long seedOffset = 0; seedOffset < 40; seedOffset++) {
            for (int chunkX = 0; chunkX < 8; chunkX++) {
                for (int chunkZ = 0; chunkZ < 8; chunkZ++) {
                    HiddenChamberPlan.PlanResult result =
                            HiddenChamberPlan.plan(SEED + seedOffset, chunkX, chunkZ, terrain, null);
                    if (!result.isAccepted()) {
                        continue;
                    }
                    checked++;
                    assertPlanInvariants(result.accepted());
                    if (checked >= 200) {
                        break outer;
                    }
                }
            }
        }
        assertTrue(checked >= 200, "expected at least 200 accepted plans, saw " + checked);
    }

    private static void assertPlanInvariants(HiddenChamberPlan.Plan plan) {
        HiddenChamberPlan.EnvelopeDirection direction = plan.direction();
        Set<String> seen = new HashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            String key = cell.x() + ":" + cell.y() + ":" + cell.z();
            assertTrue(seen.add(key), "duplicate write coordinate " + cell + " in " + plan.theme());
            assertTrue(cell.y() >= HiddenChamberPlan.MIN_AUTHORED_Y,
                    plan.theme() + " wrote " + cell + " at or below the world floor");
            assertTrue(direction.containsWriteCell(cell.x(), cell.z()),
                    plan.theme() + " wrote " + cell + " outside the canonical write envelope");
        }
        for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
            assertTrue(mouth.x() >= 0 && mouth.x() < 16 && mouth.z() >= 0 && mouth.z() < 16,
                    plan.theme() + " mouth cell " + mouth + " left the owner chunk");
        }
    }
}
