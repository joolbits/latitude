package com.example.globe.world;

import com.example.globe.core.GlacialBlend;
import com.example.globe.core.PolarBarrensBand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final int DEEP_CAP_Z = 9889;       // |lat| = 89.00 deg -> blend threshold 1.0 (always)
    private static final int BLEND_Z = 9222;          // |lat| = 83.00 deg -> threshold ~0.68 (78->86 band)
    // S28: the underground blend onset is 78 deg (far equatorward of the OLD 82 barrens onset). 80 deg now
    // sits INSIDE the blend lead-in (~16% glacial) where the old barrens-fray gate said "nothing" -- the
    // equatorward extension the owner asked for. 76.5 deg is genuinely below the blend onset (nothing).
    private static final int LEAD_IN_Z = 8888;        // |lat| = 79.99 deg -> threshold ~0.156 (old gate: 0)
    private static final int BELOW_BLEND_ONSET_Z = 8500; // |lat| = 76.50 deg -> threshold exactly 0.0

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
        // mixin resolver, not this pure helper; the shared underground blend front is pinned separately below.)
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
    void belowTheBlendOnsetNothingApplies() {
        for (int x = -4096; x <= 4096; x += 512) {
            assertFalse(LatitudeBiomes.glacialCaveColumnApplies(x, BELOW_BLEND_ONSET_Z, RADIUS, false, true),
                    "76.5 deg (below the 78 blend onset, threshold 0.0, pure-math exit) must refuse at x=" + x);
        }
    }

    @Test
    void s28UndergroundNowExtendsEquatorwardOfTheOldBarrensOnset() {
        // The whole point of S28: the underground glacial identity onsets at 78 deg, so the 78-82 lead-in
        // (where the OLD barrens-fray gate said "nothing", barrens onset 82) now has glacial columns. At
        // 80 deg the blend threshold is ~0.156, so a wide x-sweep must contain BOTH glacial and non-glacial
        // columns -- the gradual transition, not a hard switch.
        double latDeg = Math.abs((double) LEAD_IN_Z) * 90.0 / RADIUS;
        assertTrue(PolarBarrensBand.barrensFraction01(latDeg) <= 0.0,
                "sanity: 80 deg is below the OLD barrens onset (the old gate would refuse the whole band)");
        boolean sawApply = false;
        boolean sawRefuse = false;
        for (int x = -16384; x <= 16384; x += 64) {
            if (LatitudeBiomes.glacialCaveColumnApplies(x, LEAD_IN_Z, RADIUS, false, true)) {
                sawApply = true;
            } else {
                sawRefuse = true;
            }
        }
        assertTrue(sawApply,
                "80 deg must now contain glacial columns (equatorward extension the old fray gate lacked)");
        assertTrue(sawRefuse, "80 deg (~16% blend) must still be mostly non-glacial -- a lead-in, not the cap");
    }

    @Test
    void blendAtEightyThreeDegreesSplitsAndMatchesTheSharedUndergroundBlendFront() {
        double latDeg = Math.abs((double) BLEND_Z) * 90.0 / RADIUS;
        boolean sawApply = false;
        boolean sawRefuse = false;
        for (int x = -8192; x <= 8192; x += 64) {
            boolean applies = LatitudeBiomes.glacialCaveColumnApplies(x, BLEND_Z, RADIUS, false, true);
            // THE shared-front pin (S28): the cave column decision must agree EXACTLY with the underground
            // BLEND law the crevasses/tunnels/locator ride (GlacialBlend.undergroundGlacial on the 640-block
            // region field, ONE decision -- so biome, crevasses and cave identity all blend together and
            // none outruns another). Deliberately NOT the surface barrens fray any more.
            assertEquals(GlacialBlend.undergroundGlacial(latDeg, LatitudeBiomes.glacialBlendRegionNoise(x, BLEND_Z)),
                    applies, "cave column must ride the exact shared underground blend at x=" + x);
            sawApply |= applies;
            sawRefuse |= !applies;
        }
        assertTrue(sawApply && sawRefuse,
                "83 deg (~68% blend) must contain both applying and refusing columns");
    }

    // --- S28 cross-pin: all three underground consumers ride ONE law --------------------------------

    @Test
    void allThreeUndergroundConsumersShareTheExactBlendLaw() {
        // The biome swap (glacialCaveColumnApplies), the crevasse/tunnel carver append and the /latdev
        // locator all delegate their lat+region decision to glacialBlendColumnApplies -> undergroundGlacial
        // on the SAME 640-block region field. This pins that single shared law across a table of latitudes
        // (below-onset, lead-in, mid-blend, deep-cap) and x positions, so a future edit that split any one
        // consumer off the seam fails here.
        int[] zs = { BELOW_BLEND_ONSET_Z, LEAD_IN_Z, BLEND_Z, DEEP_CAP_Z };
        for (int z : zs) {
            double absLat = Math.abs((double) z) * 90.0 / RADIUS;
            for (int x = -6144; x <= 6144; x += 96) {
                boolean seamLaw = GlacialBlend.undergroundGlacial(absLat, LatitudeBiomes.glacialBlendRegionNoise(x, z));
                // the shared column decision equals the pure law (with the pure-math onset cheap-out folded in)
                assertEquals(seamLaw, LatitudeBiomes.glacialBlendColumnApplies(x, z, RADIUS),
                        "glacialBlendColumnApplies == undergroundGlacial(regionNoise) at x=" + x + " z=" + z);
                // the biome-swap consumer (non-ocean land, flag on) rides the exact same shared law
                assertEquals(LatitudeBiomes.glacialBlendColumnApplies(x, z, RADIUS),
                        LatitudeBiomes.glacialCaveColumnApplies(x, z, RADIUS, false, true),
                        "glacialCaveColumnApplies (non-ocean, flag on) == the shared blend at x=" + x + " z=" + z);
            }
        }
    }

    // --- S28 tuning-constant pins (the region field is dedicated + region-scale, not the surface fray) ---

    @Test
    void blendRegionFieldUsesADedicated640BlockFieldDistinctFromTheSurfaceFray() throws Exception {
        // Pin the private tuning literals so a future edit cannot silently shrink the region cells back to
        // chunk scale (which would re-introduce the S27 fray flicker) or collide the field with the surface
        // barrens fray. Reflection reads the private static-final constants directly.
        int blendScale = intField("GLACIAL_BLEND_REGION_SCALE_BLOCKS");
        int fraySurfaceScale = intField("POLAR_BARRENS_FRAY_SCALE_BLOCKS");
        assertEquals(640, blendScale, "the underground blend cells are 640 blocks (geography, not confetti)");
        assertEquals(64, fraySurfaceScale, "the SURFACE barrens fray stays chunk-scale (64) -- untouched by S28");
        assertTrue(blendScale >= 8 * fraySurfaceScale,
                "the blend field must be an order of magnitude coarser than the surface fray");

        long blendSalt = longField("GLACIAL_BLEND_REGION_SALT");
        long fraySalt = longField("POLAR_BARRENS_FRAY_SALT");
        assertNotEquals(fraySalt, blendSalt,
                "the blend field must use its OWN dedicated salt, never the surface barrens fray salt");

        // Behavioral corollary: the two fields disagree at sampled columns (dedicated salt+scale), and the
        // blend field is deterministic.
        boolean sawDisagreement = false;
        for (int x = -2048; x <= 2048; x += 128) {
            double blend = LatitudeBiomes.glacialBlendRegionNoise(x, BLEND_Z);
            assertEquals(blend, LatitudeBiomes.glacialBlendRegionNoise(x, BLEND_Z), 0.0,
                    "the blend region field is deterministic at x=" + x);
            if (Math.abs(blend - LatitudeBiomes.polarBarrensFrayNoise(x, BLEND_Z)) > 1e-6) {
                sawDisagreement = true;
            }
        }
        assertTrue(sawDisagreement, "the blend field is a genuinely different field from the surface fray");
    }

    private static int intField(String name) throws Exception {
        Field f = LatitudeBiomes.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static long longField(String name) throws Exception {
        Field f = LatitudeBiomes.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(null);
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
