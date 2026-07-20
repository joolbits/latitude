package com.example.globe.world;

import com.example.globe.core.PolarBarrensBand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-9 P2 KEYSTONE law pins (Crew C, owner flight TEST 113, 2026-07-19): the pure decision math behind
 * the {@code globe:glacial_caves} depth-conditioned biome swap in
 * {@code ChunkGeneratorPopulateBiomesMixin}, plus the blue-ice depth-strata law consumed by
 * {@code PolarBarrensGlacierMixin}. Pure JVM -- no bootstrap, no registries; the flag-parameterized
 * twins are the same single implementations the production overloads fold the static-final flags into
 * (house pattern per the {@code LatitudeV2Flags} testing note: a static-final flag cannot be flipped
 * inside the suite JVM).
 *
 * <h2>The two identity laws this class pins</h2>
 * <ul>
 *   <li><b>Surface-quart identity</b>: {@link LatitudeBiomes#isBelowGlacialCaveCeiling} is the swap's
 *       per-cell Y prefilter -- any quart at/above {@link LatitudeBiomes#GLACIAL_CAVES_CEILING_Y} (48)
 *       can NEVER swap, whatever the flag/band/noise say, so the surface pick order is untouched by
 *       construction (flag-on AND flag-off).</li>
 *   <li><b>Flag-off identity</b>: the column twin with {@code enabled=false} refuses every column, so a
 *       flag-off JVM's resolver can never emit the cave biome (byte-identity half).</li>
 * </ul>
 */
class GlacialCavesBiomeLawTest {

    private static final long ATLAS_SEED = 20260714L; // the pinned seed of the diagnosed atlas runs
    private static final int RADIUS = 10000;          // --size regular
    private static final int DEEP_CAP_Z = 9889;       // |lat| = 89.00 deg -> barrens fraction 1.0
    private static final int FRAY_Z = 9222;           // |lat| = 83.00 deg -> fraction ~0.5 (82->84 band)
    private static final int BELOW_ONSET_Z = 9000;    // |lat| = 81.00 deg -> fraction exactly 0.0

    @BeforeAll
    static void armWorldSeed() {
        LatitudeBiomes.setWorldSeed(ATLAS_SEED);
    }

    @AfterAll
    static void disarmWorldSeed() {
        LatitudeBiomes.setWorldSeed(0L);
    }

    // --- surface-quart identity (the Y prefilter) ---------------------------------------------------

    @Test
    void ceilingPrefilterPinsTheSurfaceQuartIdentity() {
        assertEquals(48, LatitudeBiomes.GLACIAL_CAVES_CEILING_Y,
                "the design-duo fixed line (below the glacier snow cap for the whole 82-90 land distribution)");
        assertEquals(0, LatitudeBiomes.GLACIAL_CAVES_CEILING_Y % 4,
                "the line must be quart-aligned so a quart cell (center Y = (q<<2)+2) is wholly on one side");

        assertTrue(LatitudeBiomes.isBelowGlacialCaveCeiling(47), "just below the line: swappable");
        assertTrue(LatitudeBiomes.isBelowGlacialCaveCeiling(-64), "world floor: swappable");
        assertFalse(LatitudeBiomes.isBelowGlacialCaveCeiling(48),
                "AT the line: surface side -- never swaps (surface-quart identity)");
        assertFalse(LatitudeBiomes.isBelowGlacialCaveCeiling(66), "median polar surface: never swaps");
        assertFalse(LatitudeBiomes.isBelowGlacialCaveCeiling(320), "build ceiling: never swaps");

        // S25 (owner TEST 117, 2026-07-20: caves "should extend down further into the sub y zero zone... it
        // still seems like it ends pretty abruptly"): the prefilter has NO LOWER BOUND -- the swap reaches
        // the WORLD BOTTOM, so the deepslate zone is glacial-caves too (the "abrupt end" was the ice DRESSING
        // thinning below the permafrost band, addressed data-side; the biome identity was already full-depth).
        // A future edit that re-introduces a floor here must fail this pin. (The deep_dark quart exemption and
        // the swap running BEFORE the lush veto -- so dripstone_caves/lush_caves quarts DO swap -- live in the
        // mixin resolver, not this pure helper; the shared-front fray is pinned separately below.)
        for (int y = -64; y < 48; y += 4) {
            assertTrue(LatitudeBiomes.isBelowGlacialCaveCeiling(y),
                    "every sub-ceiling quart down to the world floor stays swappable (no floor) at y=" + y);
        }
    }

    // --- flag-off identity + gate order (the column twin) -------------------------------------------

    @Test
    void disabledTwinRefusesEveryColumn() {
        for (int x = -4096; x <= 4096; x += 512) {
            assertFalse(LatitudeBiomes.glacialCaveColumnApplies(x, DEEP_CAP_Z, RADIUS, false, false),
                    "flag-off must refuse even a deep-cap land column (byte-identity half) x=" + x);
        }
    }

    @Test
    void unarmedRadiusAndOceanColumnsRefuse() {
        assertFalse(LatitudeBiomes.glacialCaveColumnApplies(0, DEEP_CAP_Z, 0, false, true),
                "radius 0 (unarmed JVM) must refuse");
        assertFalse(LatitudeBiomes.glacialCaveColumnApplies(0, DEEP_CAP_Z, -1, false, true),
                "negative radius must refuse");
        assertFalse(LatitudeBiomes.glacialCaveColumnApplies(0, DEEP_CAP_Z, RADIUS, true, true),
                "an ocean-family column must refuse -- the sacred sea keeps its vanilla underground");
    }

    @Test
    void deepCapLandColumnsAlwaysApplyBothHemispheres() {
        for (int x = -4096; x <= 4096; x += 512) {
            assertTrue(LatitudeBiomes.glacialCaveColumnApplies(x, DEEP_CAP_Z, RADIUS, false, true),
                    "89 deg N (fraction 1.0) must apply at x=" + x);
            assertTrue(LatitudeBiomes.glacialCaveColumnApplies(x, -DEEP_CAP_Z, RADIUS, false, true),
                    "89 deg S (fraction 1.0) must apply at x=" + x);
        }
    }

    @Test
    void belowTheBandOnsetNothingApplies() {
        for (int x = -4096; x <= 4096; x += 512) {
            assertFalse(LatitudeBiomes.glacialCaveColumnApplies(x, BELOW_ONSET_Z, RADIUS, false, true),
                    "81 deg (fraction 0.0, pure-math exit) must refuse at x=" + x);
        }
    }

    @Test
    void frayAtEightyThreeDegreesSplitsAndMatchesTheSharedBarrensFront() {
        double latDeg = Math.abs((double) FRAY_Z) * 90.0 / RADIUS;
        boolean sawApply = false;
        boolean sawRefuse = false;
        for (int x = -8192; x <= 8192; x += 64) {
            boolean applies = LatitudeBiomes.glacialCaveColumnApplies(x, FRAY_Z, RADIUS, false, true);
            // THE shared-front pin: the cave column decision must agree EXACTLY with the barrens
            // band+fray law the biome/glacier/carvers ride (one coherent field, one decision -- so
            // biome, glacier, crevasses and cave identity all fray together and none outruns another).
            assertEquals(PolarBarrensBand.isBarrens(latDeg, LatitudeBiomes.polarBarrensFrayNoise(x, FRAY_Z)),
                    applies, "cave column must ride the exact shared barrens fray at x=" + x);
            sawApply |= applies;
            sawRefuse |= !applies;
        }
        assertTrue(sawApply && sawRefuse,
                "83 deg (~50% fray) must contain both applying and refusing columns");
    }

    // --- blue-ice depth strata law ------------------------------------------------------------------

    @Test
    void blueIceDepthLineIsBoundedMonotoneAndNaNSafe() {
        assertEquals(12, LatitudeBiomes.GLACIER_BLUE_ICE_MIN_DEPTH_BLOCKS);
        assertEquals(18, LatitudeBiomes.GLACIER_BLUE_ICE_MAX_DEPTH_BLOCKS);
        assertTrue(LatitudeBiomes.GLACIER_BLUE_ICE_MIN_DEPTH_BLOCKS
                        > PolarBarrensBand.GLACIER_ICE_MIN_BLOCKS,
                "the line must sit deeper than the marginal glacier body, so the frayed edge stays all "
                        + "packed ice and blue ice appears only in thick glacier hearts");

        assertEquals(12, LatitudeBiomes.blueIceStartDepthFromNoise(0.0), "noise 0 -> shallowest line");
        assertEquals(15, LatitudeBiomes.blueIceStartDepthFromNoise(0.5), "mid noise -> mid line");
        assertEquals(18, LatitudeBiomes.blueIceStartDepthFromNoise(0.9999), "top of range -> deepest line");
        assertEquals(18, LatitudeBiomes.blueIceStartDepthFromNoise(1.0), "inclusive top clamps");
        assertEquals(18, LatitudeBiomes.blueIceStartDepthFromNoise(99.0), "out-of-range clamps");
        assertEquals(12, LatitudeBiomes.blueIceStartDepthFromNoise(-1.0), "below-range clamps");
        assertEquals(15, LatitudeBiomes.blueIceStartDepthFromNoise(Double.NaN),
                "NaN reads as 0.5 -- mid-line, never no-stratum on bad data (glacierIceBodyBlocks' NaN law)");

        int prev = Integer.MIN_VALUE;
        for (double n = 0.0; n <= 1.0; n += 0.01) {
            int depth = LatitudeBiomes.blueIceStartDepthFromNoise(n);
            assertTrue(depth >= 12 && depth <= 18, "depth in [12,18] at n=" + n);
            assertTrue(depth >= prev, "monotone non-decreasing at n=" + n);
            prev = depth;
        }
    }

    @Test
    void worldSampledBlueIceLineStaysInRangeAndIsDeterministic() {
        for (int x = -2048; x <= 2048; x += 256) {
            for (int z = 9000; z <= 9900; z += 300) {
                int depth = LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks(x, z);
                assertTrue(depth >= 12 && depth <= 18, "sampled depth in range at " + x + "," + z);
                assertEquals(depth, LatitudeBiomes.polarBarrensBlueIceStartDepthBlocks(x, z),
                        "deterministic (same inputs, same answer, every worldgen thread)");
            }
        }
    }
}
