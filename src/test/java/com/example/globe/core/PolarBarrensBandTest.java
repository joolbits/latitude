package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>These run with the DEFAULT onset/full (86/88) -- no {@code -D} is set, and {@link LatitudeV2Flags}
 * is read once at class-init, so the flag class's defaults are what's exercised (per its own testing note).
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
    void onsetDefaultsToVegetationFadeFinish() {
        assertEquals(PolarVegetationFade.FULL_DEG, PolarBarrensBand.ONSET_DEG, EPS,
                "The barrens onset is KEEP-SHARED with the vegetation-fade finish (the invisible seam)");
    }

    // --- barrensFraction01: containment below onset (byte-identical region) -----------------------

    @Test
    void fractionIsExactlyZeroBelowAndAtOnset() {
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(0.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(60.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(70.0), EPS);
        assertEquals(0.0, PolarBarrensBand.barrensFraction01(85.999), EPS);
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
        // Defaults 86->88: midpoint 87 -> smoothstep(0.5) = 0.5; 87.5 -> smoothstep(0.75) = 0.84375.
        assertEquals(0.5, PolarBarrensBand.barrensFraction01(87.0), 1e-6);
        assertEquals(0.84375, PolarBarrensBand.barrensFraction01(87.5), 1e-6);
    }

    @Test
    void fractionIsBothHemispheres() {
        // The ramp keys on |deg|, so negative (south) latitudes behave identically.
        assertEquals(PolarBarrensBand.barrensFraction01(87.0), PolarBarrensBand.barrensFraction01(-87.0), EPS);
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
        // At 87.5 (~84% barrens): a low noise sample is barrens, a high one is the surviving snowy_plains.
        assertTrue(PolarBarrensBand.isBarrens(87.5, 0.10));
        assertFalse(PolarBarrensBand.isBarrens(87.5, 0.90));
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
        assertFalse(PolarBarrensBand.overridesSnowyPlains(true, true, 87.5, 0.90));
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
}
