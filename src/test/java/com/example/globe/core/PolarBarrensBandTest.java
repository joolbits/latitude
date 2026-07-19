package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarBarrensBand} (Phase 5 Slice B-8 Polar Barrens placement + surface math).
 *
 * <p>Covers the four contracts the world-side wiring relies on: the poleward
 * {@link PolarBarrensBand#barrensFraction01(double)} ramp (onset/full/ordering/NaN/monotonic), the
 * {@link PolarBarrensBand#isBarrens(double, double)} noise fray, the full
 * {@link PolarBarrensBand#overridesSnowyPlains(boolean, boolean, double, double)} placement predicate
 * (snowy_plains-only, polar-only, fray-only), and the
 * {@link PolarBarrensBand#surfaceKind(double, double)} block classifier (thresholds + powder precedence).
 *
 * <p>These run with the DEFAULT onset/full (82/84 since S13) -- no {@code -D} is set, and
 * {@link LatitudeV2Flags} is read once at class-init, so the flag class's defaults are what's exercised
 * (per its own testing note).
 */
class PolarBarrensBandTest {

    private static final double EPS = 1e-9;

    // --- ramp: onset / full / ordering -----------------------------------------------------------

    @Test
    void bandOrderingOnsetStrictlyBelowFull() {
        // The smoothstep denominator (FULL-ONSET) must be strictly positive under every -D pairing.
        assertTrue(PolarBarrensBand.FULL_DEG > PolarBarrensBand.ONSET_DEG,
                "FULL_DEG must be strictly greater than ONSET_DEG");
        assertTrue(PolarBarrensBand.FULL_DEG >= PolarBarrensBand.ONSET_DEG + 0.5,
                "FULL_DEG is clamped to at least ONSET_DEG + 0.5");
    }

    @Test
    void onsetIsDecoupledFromVegetationFadeFinish() {
        // S21c BROKE the KEEP-SHARED coupling: the barrens onset is now an INDEPENDENT 82 deg, while the
        // vegetation fade finishes at 80 ("veg to 80"). The 80-82 band is bare tundra before the barrens claim.
        //
        // INIT-ORDER-PROOF (S21c small-fix round): PolarBarrensBand.ONSET_DEG mirrors
        // LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG, a static-final captured from System.getProperty ONCE at
        // class-init -- so this test asserts the CAPTURED CONSTANTS directly, never a re-read sysprop. Order-
        // independent as long as the suite JVM never carries these -D keys; the guards make any future pollution
        // (gradle test-JVM -D, or a mid-suite setProperty -- both forbidden per the LatitudeV2Flags testing
        // note) fail LOUDLY with the cause named, instead of flaking the literals by class-load order.
        assertNull(System.getProperty("latitude.polarBarrens.onsetDeg"),
                "suite JVM must not carry -Dlatitude.polarBarrens.onsetDeg (static-init capture: defaults law)");
        assertNull(System.getProperty("latitude.polarVegetationFade.fullDeg"),
                "suite JVM must not carry -Dlatitude.polarVegetationFade.fullDeg (static-init capture: defaults law)");
        assertEquals(82.0, PolarBarrensBand.ONSET_DEG, EPS,
                "The barrens onset is an independent literal 82 deg (S21c)");
        assertNotEquals(PolarVegetationFade.FULL_DEG, PolarBarrensBand.ONSET_DEG,
                "S21c: barrens onset (82) is decoupled from the veg-fade finish (now 80)");
    }

    // --- barrensFraction01: containment below onset (byte-identical region) -----------------------

    @Test
    void fractionIsExactlyZeroBelowAndAtOnset() {
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(0.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(60.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(70.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(81.999), EPS);
        // The exact onset boundary itself must be fully zero so the placement override never fires there.
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(PolarBarrensBand.ONSET_DEG), EPS);
    }

    @Test
    void fractionIsExactlyOneAtAndAboveFull() {
        assertEquals(1.0, PolarBarrensBand.barrensFraction01(PolarBarrensBand.FULL_DEG), EPS);
        assertEquals(1.0, PolarBarrensBand.barrensFraction01(89.0), EPS);
        assertEquals(1.0, PolarBarrensBand.barrensFraction01(90.0), EPS);
    }

    @Test
    void fractionIsSmoothstepMidBand() {
        // Defaults 82->84: midpoint 83 -> smoothstep(0.5) = 0.5; 83.5 -> smoothstep(0.75) = 0.84375.
        assertEquals(0.5, PolarBarrensBand.barrensFraction01(83.0), 1e-6);
        assertEquals(0.84375, PolarBarrensBand.barrensFraction01(83.5), 1e-6);
    }

    @Test
    void fractionIsBothHemispheres() {
        // The ramp keys on |deg|, so negative (south) latitudes behave identically.
        assertEquals(PolarBarrensBand.barrensFraction01(83.0), PolarBarrensBand.barrensFraction01(-83.0), EPS);
        assertEquals(1.0, PolarBarrensBand.barrensFraction01(-90.0), EPS);
    }

    @Test
    void fractionNaNIsZero() {
        // A NaN latitude must never place barrens (the safe, byte-identical direction).
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(Double.NaN), EPS);
    }

    @Test
    void fractionMonotonicNonDecreasing() {
        double prev = -1.0;
        for (double deg = 80.0; deg <= 90.0; deg += 0.1) {
            double f = PolarBarrensBand.barrensFraction01(deg);
            assertTrue(f >= prev - EPS, "barrensFraction01 must be monotonically non-decreasing in |deg|");
            prev = f;
        }
    }

    // --- isBarrens: noise fray -------------------------------------------------------------------

    @Test
    void isBarrensNeverBelowOnset() {
        // Below onset the fraction is 0, so no noise value (in [0,1)) can be < 0 -> never barrens.
        assertFalse(PolarBarrensBand.isBarrens(80.0, 0.0));
        assertFalse(PolarBarrensBand.isBarrens(80.0, 0.5));
        assertFalse(PolarBarrensBand.isBarrens(PolarBarrensBand.ONSET_DEG, 0.0));
    }

    @Test
    void isBarrensAlwaysAtFull() {
        // At/above full the fraction is 1.0, so every noise sample in [0,1) is < 1 -> always barrens.
        assertTrue(PolarBarrensBand.isBarrens(88.0, 0.0));
        assertTrue(PolarBarrensBand.isBarrens(88.0, 0.999));
        assertTrue(PolarBarrensBand.isBarrens(90.0, 0.999));
    }

    @Test
    void isBarrensFraysInBand() {
        // At 83.5 (~84% barrens): a low noise sample is barrens, a high one is the surviving snowy_plains.
        assertTrue(PolarBarrensBand.isBarrens(83.5, 0.10));
        assertFalse(PolarBarrensBand.isBarrens(83.5, 0.90));
    }

    // --- overridesSnowyPlains: full placement predicate ------------------------------------------

    @Test
    void overrideOnlyRewritesSnowyPlains() {
        // On the fray + polar, but the pick is NOT snowy_plains (e.g. ice_spikes / snowy_slopes): survive.
        assertFalse(PolarBarrensBand.overridesSnowyPlains(false, true, 88.0, 0.0),
                "ice_spikes accents and mountain alpine picks are not snowy_plains -> survive by construction");
        // The pick IS snowy_plains, polar, deep in the cap: rewrite.
        assertTrue(PolarBarrensBand.overridesSnowyPlains(true, true, 88.0, 0.0));
    }

    @Test
    void overrideOnlyInPolarBand() {
        // snowy_plains at a barrens-latitude noise value but NOT flagged polar-land: never rewrite.
        assertFalse(PolarBarrensBand.overridesSnowyPlains(true, false, 88.0, 0.0),
                "the override is gated to the polar land band");
    }

    @Test
    void overrideOnlyOnFray() {
        // snowy_plains, polar, but below onset: never rewrite (byte-identical region).
        assertFalse(PolarBarrensBand.overridesSnowyPlains(true, true, 80.0, 0.0));
        // snowy_plains, polar, on the fray but the noise says "surviving snowy_plains": not rewritten.
        assertFalse(PolarBarrensBand.overridesSnowyPlains(true, true, 83.5, 0.90));
    }

    // --- surfaceKind: block classifier -----------------------------------------------------------

    @Test
    void surfaceKindDefaultsToSnowBlock() {
        assertEquals(PolarBarrensBand.SURFACE_KIND_SNOW_BLOCK,
                PolarBarrensBand.surfaceKind(0.0, 0.0));
        assertEquals(PolarBarrensBand.SURFACE_KIND_SNOW_BLOCK,
                PolarBarrensBand.surfaceKind(0.5, 0.5));
        // Just below both thresholds -> still snow_block.
        assertEquals(PolarBarrensBand.SURFACE_KIND_SNOW_BLOCK,
                PolarBarrensBand.surfaceKind(PolarBarrensBand.POWDER_SNOW_KEEP_THRESHOLD - 1e-6,
                        PolarBarrensBand.ICE_KEEP_THRESHOLD - 1e-6));
    }

    @Test
    void surfaceKindPowderAtThreshold() {
        assertEquals(PolarBarrensBand.SURFACE_KIND_POWDER_SNOW,
                PolarBarrensBand.surfaceKind(PolarBarrensBand.POWDER_SNOW_KEEP_THRESHOLD, 0.0));
        assertEquals(PolarBarrensBand.SURFACE_KIND_POWDER_SNOW,
                PolarBarrensBand.surfaceKind(0.99, 0.0));
    }

    @Test
    void surfaceKindIceAtThreshold() {
        assertEquals(PolarBarrensBand.SURFACE_KIND_ICE,
                PolarBarrensBand.surfaceKind(0.0, PolarBarrensBand.ICE_KEEP_THRESHOLD));
        assertEquals(PolarBarrensBand.SURFACE_KIND_ICE,
                PolarBarrensBand.surfaceKind(0.0, 0.99));
    }

    @Test
    void surfaceKindPowderTakesPrecedenceOverIce() {
        // A powder pocket overlapping an ice patch reads as the hidden trap (the gameplay point).
        assertEquals(PolarBarrensBand.SURFACE_KIND_POWDER_SNOW,
                PolarBarrensBand.surfaceKind(0.99, 0.99));
    }

    // --- isSurfaceSkin: the underground/underwater protection gate --------------------------------
    // The barrens biome carries snowy_plains' full underground feature subset (ore_dirt/ore_gravel/
    // disks write dirt+gravel through the same ProtoChunk hook the surface mixin intercepts), so the
    // substitutions MUST be confined to the exposed surface skin: the living underground stays native.

    @Test
    void surfaceSkinAcceptsTheSurfaceBandOnLand() {
        // Land column: WORLD_SURFACE_WG == OCEAN_FLOOR_WG == terrain top (70).
        assertTrue(PolarBarrensBand.isSurfaceSkin(70, 70, 70), "the surface block itself");
        assertTrue(PolarBarrensBand.isSurfaceSkin(66, 70, 70), "surface-rule dirt band");
        assertTrue(PolarBarrensBand.isSurfaceSkin(
                        70 - PolarBarrensBand.SURFACE_SKIN_MARGIN_BLOCKS, 70, 70),
                "the exact margin boundary is still skin");
    }

    @Test
    void surfaceSkinRejectsTheUnderground() {
        // ore_dirt / ore_gravel veins below the skin must stay native (underground alive).
        assertFalse(PolarBarrensBand.isSurfaceSkin(
                70 - PolarBarrensBand.SURFACE_SKIN_MARGIN_BLOCKS - 1, 70, 70));
        assertFalse(PolarBarrensBand.isSurfaceSkin(20, 70, 70), "deep ore vein");
        assertFalse(PolarBarrensBand.isSurfaceSkin(-40, 70, 70), "deepslate-depth vein");
    }

    @Test
    void surfaceSkinRejectsFluidColumns() {
        // Water column: WORLD_SURFACE_WG = water top (62), OCEAN_FLOOR_WG = submerged floor (50).
        // The floor gravel/dirt (surface rule, disk_sand/clay/gravel) is underwater ground, not skin --
        // even when it is within the depth margin of the water surface.
        assertFalse(PolarBarrensBand.isSurfaceSkin(61, 62, 50), "just under the water surface");
        assertFalse(PolarBarrensBand.isSurfaceSkin(50, 62, 50), "the submerged floor itself");
    }

    @Test
    void surfaceSkinToleratesAThinNonBlockingTop() {
        // A snow layer (counted by WORLD_SURFACE_WG, not motion-blocking) puts the two heightmaps one
        // apart on an ordinary land column; that must still read as land.
        assertTrue(PolarBarrensBand.isSurfaceSkin(70, 71, 70));
    }

    // ---- S11(a): the lush-cave veto ----------------------------------------------------------

    @Test
    void lushCaveCellInBandRemaps() {
        assertTrue(PolarBarrensBand.vetoesLushCaveCell(true, true, 87.0),
                "a lush_caves cell inside the barrens band remaps to the column surface biome");
        assertTrue(PolarBarrensBand.vetoesLushCaveCell(true, true, PolarBarrensBand.ONSET_DEG),
                "band-gated on the core onset (82 since S13), inclusive");
        assertTrue(PolarBarrensBand.vetoesLushCaveCell(true, true, -89.0), "hemisphere-symmetric");
    }

    @Test
    void lushCaveCellOutOfBandUntouched() {
        assertFalse(PolarBarrensBand.vetoesLushCaveCell(true, true, 81.9), "below the onset: untouched");
        assertFalse(PolarBarrensBand.vetoesLushCaveCell(true, true, 40.0));
        assertFalse(PolarBarrensBand.vetoesLushCaveCell(true, true, Double.NaN), "bad read: untouched");
    }

    @Test
    void dripstoneAndDeepDarkUntouchedEvenInBand() {
        // Only lush is owner-banned (reads tropical); dripstone reads stone, deep_dark is structure-tied.
        // The mixin passes cellIsLushCaves=false for every other cave biome (exact-id match).
        assertFalse(PolarBarrensBand.vetoesLushCaveCell(true, false, 89.0));
    }

    @Test
    void lushCaveVetoFlagOffUntouched() {
        assertFalse(PolarBarrensBand.vetoesLushCaveCell(false, true, 89.0),
                "barrens flag off: byte-identical, lush cells pass through");
    }

    // ---- B-9a: the glacier depth law ---------------------------------------------------------

    @Test
    void glacierSnowCapIsTheOwnersTenBlockFloor() {
        assertEquals(10, PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS,
                "'10 blocks at least of snow' -- the owner's floor");
    }

    @Test
    void glacierIceBodyIsZeroOutsideTheBandAndThickensIn() {
        // No glacier at/below the onset (fraction 0) -- columns outside the band are bitwise untouched.
        assertEquals(0, PolarBarrensBand.glacierIceBodyBlocks(PolarBarrensBand.ONSET_DEG, 0.5));
        assertEquals(0, PolarBarrensBand.glacierIceBodyBlocks(80.0, 0.9));
        // Neutral noise (0.5 = no wobble): the body rides the band-fraction smoothstep -- a marginal glacier
        // just inside the onset, the full 30-block body at/above FULL_DEG ("the glacier thickens in").
        int nearEdge = PolarBarrensBand.glacierIceBodyBlocks(PolarBarrensBand.ONSET_DEG + 0.1, 0.5);
        int mid = PolarBarrensBand.glacierIceBodyBlocks(
                (PolarBarrensBand.ONSET_DEG + PolarBarrensBand.FULL_DEG) / 2.0, 0.5);
        int full = PolarBarrensBand.glacierIceBodyBlocks(PolarBarrensBand.FULL_DEG, 0.5);
        assertTrue(nearEdge >= 1 && nearEdge <= PolarBarrensBand.GLACIER_ICE_MIN_BLOCKS + 2,
                "marginal glacier at the frayed edge, was " + nearEdge);
        assertTrue(mid > nearEdge, "the body thickens with the band");
        assertEquals(PolarBarrensBand.GLACIER_ICE_MAX_BLOCKS, full, "full body at/above 84 (S13)");
        assertEquals(full, PolarBarrensBand.glacierIceBodyBlocks(89.5, 0.5), "holds poleward");
    }

    @Test
    void glacierDepthWobbleIsBoundedAndSoleStaysAboveTheLivingUnderground() {
        // Wobble spans +/- 6 around the fraction ramp; the hard cap keeps the TOTAL glacier (cap + ice)
        // within 10 + 36 = 46 blocks below the surface, so the ore-rich underground below always survives.
        int deepest = PolarBarrensBand.glacierIceBodyBlocks(90.0, 0.999999);
        int shallowest = PolarBarrensBand.glacierIceBodyBlocks(90.0, 0.0);
        assertEquals(PolarBarrensBand.GLACIER_ICE_MAX_BLOCKS + PolarBarrensBand.GLACIER_DEPTH_WOBBLE_BLOCKS,
                deepest, "deepest sole = max body + wobble (36)");
        assertEquals(PolarBarrensBand.GLACIER_ICE_MAX_BLOCKS - PolarBarrensBand.GLACIER_DEPTH_WOBBLE_BLOCKS,
                shallowest, "shallowest full-band sole = max body - wobble (24)");
        assertTrue(PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS + deepest <= 48,
                "total glacier depth stays within the underground-stays-alive bound");
        // In-band, the body is never zero (a barrens column always has at least a sliver of ice)...
        assertTrue(PolarBarrensBand.glacierIceBodyBlocks(PolarBarrensBand.ONSET_DEG + 0.05, 0.0) >= 1);
        // ...and NaN noise degrades to the no-wobble ramp, never to no-glacier (83 = mid-band since S13).
        assertEquals(PolarBarrensBand.glacierIceBodyBlocks(83.0, 0.5),
                PolarBarrensBand.glacierIceBodyBlocks(83.0, Double.NaN));
    }
}
