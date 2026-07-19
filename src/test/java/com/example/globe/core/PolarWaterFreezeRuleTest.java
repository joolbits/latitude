package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWaterFreezeRule} -- the "all exposed water is frozen at the poles"
 * correctness rule (sibling of {@link PolarPrecipitationRuleTest}).
 *
 * <p>The rule forces any exposed water column eligible to freeze at {@code |lat| >= FREEZE_ALL_DEG}
 * (85 deg) on globe worlds only. These tests pin the threshold boundary, hemisphere symmetry (the caller
 * feeds an absolute latitude, but the helper also abs-es internally so a signed input works), the
 * non-globe safety gate, alignment with the ambient/precipitation thresholds, and NaN handling.
 */
class PolarWaterFreezeRuleTest {

    // ---- threshold boundary ----------------------------------------------------------------

    @Test
    void doesNotFreezeJustBelowThreshold() {
        // 84.999 deg is still equatorward of the freeze cap: vanilla temperature veto stands.
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 84.999));
    }

    @Test
    void freezesExactlyAtThreshold() {
        // The threshold itself is inclusive (>=), so 85.0 forces freeze eligibility.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, PolarWaterFreezeRule.FREEZE_ALL_DEG));
    }

    @Test
    void freezesWellPastThreshold() {
        // Peetsa's report was 89 deg S.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 89.0));
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 90.0));
    }

    @Test
    void doesNotFreezeInSubpolarBand() {
        // ~66.5 deg subpolar: naturally-cold biomes here already freeze via vanilla and must NOT be
        // second-guessed by this rule, so the latitude gate is false below 85.
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 66.5));
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 80.0));
    }

    // ---- hemisphere symmetry (magnitude is taken internally) -------------------------------

    @Test
    void freezesInSouthernHemisphereToo() {
        // A negative (southern) latitude of the same magnitude behaves identically -- Peetsa was at 89 S.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, -89.0));
    }

    @Test
    void southernSubThresholdDoesNotFreeze() {
        assertFalse(PolarWaterFreezeRule.freezesWater(true, -80.0));
    }

    @Test
    void bothHemispheresAgreeAtThreshold() {
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 85.0));
        assertTrue(PolarWaterFreezeRule.freezesWater(true, -85.0));
    }

    // ---- threshold alignment ---------------------------------------------------------------

    @Test
    void waterFreezeStaysAt85_decoupledFromAmbient() {
        // B-7 S3 guard: freezing water MODIFIES THE WORLD (places ice), a worldgen-facing seam that must NOT
        // move. When S3 shifted the pure-client ambient snow/fog onset 85 -> 82 (and S8 to 80), this threshold deliberately
        // STAYED at 85 on its own literal anchor -- it is NOT derived from AMBIENT_ONSET_DEG.
        assertEquals(85.0, PolarWaterFreezeRule.FREEZE_ALL_DEG, 1e-9,
                "the water-freeze line is a worldgen seam and must stay at 85 deg");
        assertNotEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, PolarWaterFreezeRule.FREEZE_ALL_DEG,
                "water-freeze (85) is decoupled from the client ambient onset (now 80)");
        // It DOES coincide with the frostbite DAMAGE onset (also its own 85 anchor), so "the water is frozen"
        // and "the cold starts to bite" still read as one line.
        assertEquals(PolarHazardWindow.FROSTBITE_ONSET_DEG, PolarWaterFreezeRule.FREEZE_ALL_DEG, 1e-9);
    }

    @Test
    void freezeThresholdIsPolewardOfPrecipitationClamp() {
        // Frozen water coincides WITH the ambient window (85); the rain->snow clamp sits a margin BELOW it (75).
        assertTrue(PolarWaterFreezeRule.FREEZE_ALL_DEG > PolarPrecipitationRule.FORCE_SNOW_DEG);
    }

    // ---- non-globe safety gate -------------------------------------------------------------

    @Test
    void neverFreezesOnNonGlobeWorldEvenAtPole() {
        // Vanilla / non-globe worlds are never touched, even at 90 deg.
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 90.0));
    }

    @Test
    void neverFreezesOnNonGlobeWorldAnywhere() {
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 88.0));
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 10.0));
    }

    // ---- NaN safety ------------------------------------------------------------------------

    @Test
    void nanLatitudeDoesNotFreeze() {
        assertFalse(PolarWaterFreezeRule.freezesWater(true, Double.NaN));
    }

    // ---- B-9a: the sea-freeze FRAY (the razor-seam fix) --------------------------------------

    @Test
    void frayNeutralNoiseReproducesTheRazorLineExactly() {
        // Noise 0.5 = threshold exactly 85: the anchor constant does not move (its own pin above stays),
        // and a neutral sample is bit-for-bit the razor predicate.
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.999, 0.5));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.0, 0.5));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, -85.0, 0.5), "hemisphere-symmetric");
    }

    @Test
    void frayWandersPlusMinusOneDegree() {
        // Noise 1.0 pushes the front poleward to 86: 85.5 stays liquid there...
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.5, 1.0));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 86.0, 1.0));
        // ...noise 0.0 pulls it equatorward to 84: 84.0 freezes there.
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.0, 0.0));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 83.9, 0.0));
        assertEquals(1.0, PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG, 1e-9);
    }

    @Test
    void outsideTheFrayStripFrayedEqualsRazorForEveryNoise() {
        // The consumer only samples noise inside [84,86]; pin WHY that is sound -- outside the strip every
        // possible frayed threshold lands on the same side as the razor.
        for (double noise : new double[]{0.0, 0.25, 0.5, 0.75, 0.999999}) {
            assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 83.9, noise), "below the strip: liquid");
            assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 86.1, noise), "above the strip: frozen");
        }
        assertTrue(PolarWaterFreezeRule.inFreezeFrayBand(84.0));
        assertTrue(PolarWaterFreezeRule.inFreezeFrayBand(86.0));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(83.99));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(86.01));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(Double.NaN), "NaN falls back to the razor path");
    }

    // ---- S11(b): frozen rivers -> complete ice -----------------------------------------------

    @Test
    void riverColumnFreezesSolidInZone() {
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 86.0, 0.5),
                "a river column in the full-freeze zone freezes surface to bed");
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, -87.5, 0.5), "both hemispheres");
        // The zone is the SAME frayed front the surface ice uses: 84.2 freezes when the fray dips (noise 0).
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 84.2, 0.0));
    }

    @Test
    void oceanColumnStaysSurfaceIceOverLiquid() {
        // THE SEA IS EXEMPT: under-ice swim / the pole wall / S7 immersion depend on liquid under the ice.
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, false, true, 89.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, true, 89.0, 0.5),
                "ocean classification WINS over river if a column somehow reads both");
        // Non-river, non-ocean fluid columns (ponds/lakes) wait for B-9's semi-ice lakes.
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, false, false, 89.0, 0.5));
    }

    @Test
    void belowZoneRiversAndFlagOffUnchanged() {
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 83.9, 0.0),
                "below the frayed zone (threshold 84 at noise 0): liquid river");
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 80.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(false, true, false, 89.0, 0.5),
                "barrens flag off: byte-identical, rivers keep today's surface-only freeze");
    }

    @Test
    void riverIceKindPlainSurfacePackedBelow() {
        assertFalse(PolarWaterFreezeRule.riverIceIsPacked(0), "surface block: plain ice (the familiar skin)");
        assertTrue(PolarWaterFreezeRule.riverIceIsPacked(1), "below the surface: packed (no melt-back holes)");
        assertTrue(PolarWaterFreezeRule.riverIceIsPacked(12));
    }

    @Test
    void fraySafetyFallbacks() {
        // NaN noise degrades to the exact razor line (never a hole in the ice sheet on bad data)...
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.0, Double.NaN));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.999, Double.NaN));
        // ...and non-globe / NaN latitude never freeze, same as the razor predicate.
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(false, 90.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, Double.NaN, 0.5));
    }

    // ---- S14(b): UNIVERSAL FREEZE (all land-family water, waterfalls) -------------------------

    @Test
    void landWaterColumnFreezesSolidInZoneAndSubsumesRivers() {
        // A LAKE/POND column (non-river, non-ocean) now freezes solid in the full-freeze zone -- the owner's
        // "liquid one block under a frozen lake at 89 deg" fix (S11(b) explicitly left these for B-9).
        assertTrue(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 89.0, 0.5),
                "a non-ocean land-family column in the full-freeze zone freezes surface to the floor");
        // RIVER BEHAVIOUR UNCHANGED FROM S13: a river column's old answer equals the generalised answer, so
        // rivers are subsumed, not altered (isRiver was the only dropped requirement; ocean-exempt stands).
        for (double lat : new double[]{85.0, 86.0, 89.0, -87.5}) {
            assertEquals(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, lat, 0.5),
                    PolarWaterFreezeRule.freezesLandWaterSolid(true, false, lat, 0.5),
                    "generalised land-water freeze must match the S13 river answer for river columns");
        }
    }

    @Test
    void oceanColumnExemptFromUniversalFreeze_sacredPinReasserted() {
        // THE SEA IS SACRED, re-pinned for the generalised path: ocean-family columns keep surface-ice-over-
        // liquid (under-ice swim / the pole wall / S7 immersion depend on it). Ocean wins FIRST in the chain,
        // even at the pole and even with the flag on.
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, true, 90.0, 0.5),
                "ocean column never solid-freezes, even at 90 deg");
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, true, 85.0, 0.0),
                "ocean exemption is checked before flag/front");
        // The flowing rule inherits the same exemption (a cascade over the sea leaves the sea alone).
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, true, true));
    }

    @Test
    void belowZoneAndFlagOffLeaveLandWaterLiquid() {
        // Below the frayed zone (threshold 84 at noise 0): liquid.
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 83.9, 0.0));
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 80.0, 0.5));
        // Barrens flag off: byte-identical, land water keeps today's surface-only freeze.
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(false, false, 89.0, 0.5));
        // Flowing likewise: flag-off and below-zone never freeze a cascade.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, true, true));
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 83.9, 0.0, true, true));
    }

    @Test
    void universalFreezeSharesTheOneFrayedFront() {
        // Same +-1 deg wander as the surface ice / solid rivers / frozen-sea edge: front dips to 84 at noise 0
        // and pushes to 86 at noise 1.
        assertTrue(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 84.2, 0.0), "front dips to 84");
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 85.5, 1.0), "front pushes to 86");
        assertEquals(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.5, 1.0),
                PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 85.5, 1.0),
                "the land-water decision IS the shared frayed front for non-ocean columns");
    }

    @Test
    void hemisphereSymmetryUniversal() {
        assertTrue(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, -89.0, 0.5),
                "southern pole freezes identically to northern");
    }

    // ---- S14(b): the freeze FLOOR (glacier-sole depth; the B-9 reservation) ------------------

    @Test
    void freezeFloorDepthIsPinnedToTheGlacierSole() {
        // The freeze floor is "heightmap-minus-N", N = the glacier body's full-band sole (snow cap + ice max),
        // so a frozen lake reaches as deep as the neighbouring glacier and NO DEEPER -- documented + pinned so
        // it tracks the glacier constants if they move.
        assertEquals(40, PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS);
        assertEquals(PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS + PolarBarrensBand.GLACIER_ICE_MAX_BLOCKS,
                PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS,
                "freeze-floor depth must equal the glacier's full-band sole");
    }

    @Test
    void shallowPondFreezesToBed_identicalToTheS13RiverLoop() {
        // A shallow pond/river: the bed wins, so the whole body freezes surface-to-bed exactly like the S13
        // river solid-freeze (which ran to oceanFloorY). worldSurface 70, bed 67 -> floor 67 (the bed).
        assertEquals(67, PolarWaterFreezeRule.landWaterFreezeFloorY(70, 67));
        assertEquals(64, PolarWaterFreezeRule.landWaterFreezeFloorY(65, 64), "one-block-deep puddle");
    }

    @Test
    void deepColumnLeavesLiquidBelowFloor_theB9Reservation() {
        // A DEEP aquifer exposure: the depth cap wins, freeze stops at surface-40, water below stays LIQUID
        // (reserved for B-9's semi-ice cave lakes with fish). worldSurface 70, bed 5 -> floor 30 = 70-40.
        assertEquals(30, PolarWaterFreezeRule.landWaterFreezeFloorY(70, 5));
        assertEquals(63 - PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS,
                PolarWaterFreezeRule.landWaterFreezeFloorY(63, -20),
                "a sea-level deep pool caps 40 below the surface, leaving cave water liquid");
    }

    // ---- S14(b): WATERFALLS (flowing water) ---------------------------------------------------

    @Test
    void flowingWaterFreezesWhenExposedOrAboveFloor_liquidWhenDeepAndCovered() {
        // A flowing (non-source) block in the zone freezes to a plain-ice cascade when SKY-EXPOSED...
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, true, false),
                "an exposed waterfall freezes");
        // ...or when ABOVE THE FREEZE FLOOR (inside the glacier band)...
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true),
                "flowing water above the freeze floor freezes");
        // ...but a deep, sky-covered flowing spring BELOW the floor stays liquid ("underground springs
        // below the floor untouched").
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, false),
                "a covered underground spring below the floor stays liquid");
    }

    @Test
    void universalFreezeNaNSafe() {
        // NaN latitude never freezes (solid or flowing); NaN fray degrades to the exact razor line.
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, Double.NaN, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, Double.NaN, 0.5, true, true));
        assertTrue(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 85.0, Double.NaN),
                "NaN fray -> razor line (85), never a hole in the ice on bad data");
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 84.999, Double.NaN));
    }

    // ---- S15(b): PERSISTENT ICE -- SKY WAIVER (roofed water still freezes) --------------------

    @Test
    void skyWaiverIsWaivedExactlyOnTheFreezeFront() {
        // The sky/cover requirement is waived exactly where open water is forced to freeze -- ONE front.
        assertFalse(PolarWaterFreezeRule.freezeIgnoresSky(true, 84.999), "just equatorward: sky NOT waived");
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSky(true, PolarWaterFreezeRule.FREEZE_ALL_DEG),
                "at 85 the roof no longer saves the water");
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSky(true, 89.0), "Peetsa's 89 deg shelter");
        // Pin the deliberate coupling to the open-water front (like FROSTBITE_ONSET_DEG == FREEZE_ALL_DEG).
        for (double lat : new double[]{60.0, 80.0, 84.999, 85.0, 86.0, 89.0, 90.0, -88.0}) {
            assertEquals(PolarWaterFreezeRule.freezesWater(true, lat),
                    PolarWaterFreezeRule.freezeIgnoresSky(true, lat),
                    "the sky waiver rides the open-water freeze front exactly");
        }
    }

    @Test
    void skyWaiverHemisphereSymmetricNonGlobeAndNaNSafe() {
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSky(true, -89.0), "southern pole waives the sky too");
        assertFalse(PolarWaterFreezeRule.freezeIgnoresSky(false, 90.0), "non-globe world untouched at 90 deg");
        assertFalse(PolarWaterFreezeRule.freezeIgnoresSky(true, Double.NaN), "NaN latitude -> not waived");
    }

    @Test
    void skyWaiverFrayedSharesTheOneWanderingFront() {
        // Neutral noise reproduces the razor line; the front wanders +-1 deg exactly like the frozen-sea edge.
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 85.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 84.999, 0.5));
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 84.0, 0.0), "front dips to 84 at noise 0");
        assertFalse(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 85.5, 1.0), "front pushes to 86 at noise 1");
        assertTrue(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 85.0, Double.NaN), "NaN fray -> razor line");
        assertEquals(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.5, 1.0),
                PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, 85.5, 1.0),
                "the frayed sky waiver IS the frayed open-water front");
    }

    // ---- S15(b): PERSISTENT ICE -- NO MELT ("iceMeltsInZone == false") ------------------------

    @Test
    void iceMeltCancelledExactlyOnTheFreezeFront() {
        // In the forced-freeze zone the light-driven melt is cancelled (a torch cannot thaw the pole)...
        assertTrue(PolarWaterFreezeRule.iceMeltCancelled(true, 89.0), "polar ice never melts");
        assertTrue(PolarWaterFreezeRule.iceMeltCancelled(true, PolarWaterFreezeRule.FREEZE_ALL_DEG));
        assertTrue(PolarWaterFreezeRule.iceMeltCancelled(true, -85.0), "southern pole too");
        // ...but equatorward of the front, ice melts normally (vanilla) -- this is the "iceMeltsInZone == false
        // only IN the zone" boundary: below it the predicate is false so the consumer never cancels.
        assertFalse(PolarWaterFreezeRule.iceMeltCancelled(true, 84.999), "just equatorward: vanilla melt stands");
        assertFalse(PolarWaterFreezeRule.iceMeltCancelled(true, 66.5), "subpolar ice melts by vanilla rules");
        assertFalse(PolarWaterFreezeRule.iceMeltCancelled(false, 90.0), "non-globe world untouched");
        assertFalse(PolarWaterFreezeRule.iceMeltCancelled(true, Double.NaN), "NaN latitude -> vanilla melt");
    }

    @Test
    void iceMeltCancelledFrayedTracksTheFreezeBoundaryPerColumn() {
        assertTrue(PolarWaterFreezeRule.iceMeltCancelledFrayed(true, 85.0, 0.5));
        assertFalse(PolarWaterFreezeRule.iceMeltCancelledFrayed(true, 84.999, 0.5));
        assertTrue(PolarWaterFreezeRule.iceMeltCancelledFrayed(true, 84.0, 0.0), "no-melt dips to 84 with the freeze");
        assertFalse(PolarWaterFreezeRule.iceMeltCancelledFrayed(true, 85.5, 1.0), "no-melt pushes to 86 with the freeze");
        assertTrue(PolarWaterFreezeRule.iceMeltCancelledFrayed(true, 85.0, Double.NaN), "NaN fray -> razor line");
    }

    @Test
    void skyWaiverAndNoMeltShareTheExactSameZone() {
        // Both persistent-ice laws are pinned to the SAME forced-freeze zone, so a column whose roofed water
        // freezes is exactly a column whose ice will not melt -- proven equal across the boundary and the fray.
        for (double lat : new double[]{60.0, 80.0, 84.0, 84.999, 85.0, 85.5, 86.0, 89.0, 90.0, -87.5}) {
            assertEquals(PolarWaterFreezeRule.freezeIgnoresSky(true, lat),
                    PolarWaterFreezeRule.iceMeltCancelled(true, lat),
                    "sky-waiver and no-melt share one razor front");
            for (double noise : new double[]{0.0, 0.5, 1.0}) {
                assertEquals(PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, lat, noise),
                        PolarWaterFreezeRule.iceMeltCancelledFrayed(true, lat, noise),
                        "sky-waiver and no-melt share one frayed front");
            }
        }
    }

    @Test
    void s16bTickFreezeV2_flowingWaterFreezesRegardlessOfSky_within_reach() {
        // S16(b): the tick-time consumer (ServerLevelRoofedWaterFreezeMixin) freezes a FLOWING block found
        // within the 16-block roof reach by calling freezesFlowing(barrens, ocean, lat, fray, skyExposed=false,
        // aboveFreezeFloor=true) -- aboveFreezeFloor is true BY CONSTRUCTION inside the reach (16 << the 40-block
        // glacier-sole floor), so the sky arm is irrelevant and an in-zone waterfall freezes sky OR no sky.
        final boolean SKY = false; // the mixin never claims the block is sky-exposed; the floor arm carries it.
        // In-zone, land, barrens on -> freezes (a covered OR open waterfall, same answer).
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, SKY, true),
                "an in-zone flowing block within reach freezes regardless of sky");
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 85.0, 0.5, SKY, true),
                "freezes right at the 85-deg front");
        // THE SEA IS EXEMPT (the sacred pin): an ocean-family column never flow-freezes.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, SKY, true),
                "ocean column stays liquid under its ice -- the sacred pin");
        // Out-of-zone (equatorward of the frayed front) -> vanilla (no flow-freeze).
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 83.9, 0.5, SKY, true),
                "below the zone the flowing water stays liquid (vanilla)");
        // Barrens flag off -> the flowing freeze is the untouched vanilla path.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, SKY, true),
                "flag-off leaves flowing water exactly as vanilla");
        // The DEEP-CAVE flow exemption is the mixin's reach floor (blocks below top-16 are never scanned);
        // the predicate's own deep exemption (covered AND below the glacier sole) also stays liquid.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, SKY, false),
                "a covered flowing spring below the freeze floor stays liquid (the B-9 reservation)");
    }

    @Test
    void roofReachIsShelterScaleNotDeepCave() {
        // The sky-waived freeze reaches shelter/overhang scale, deliberately shallower than the glacier sole:
        // a deep sealed cave lake (below the reach) keeps its liquid surface, the B-9 reservation.
        assertEquals(16, PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS, "one chunk section");
        assertTrue(PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS > 0);
        assertTrue(PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS < PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS,
                "roof reach stays shallower than the glacier-sole depth (deep cave water stays liquid)");
    }

    // ---- S17(b): WATERFALL FREEZE v3 -- the FLOW-TICK seam + the UPWARD SCAN ------------------

    @Test
    void s17bFlowTick_movingWaterFreezesInZone_thePureDecision() {
        // The flow-tick consumer (FlowingFluidWaterfallFreezeMixin) calls freezesFlowing(barrens=true, ocean,
        // lat, fray, skyExposed=FALSE, aboveFreezeFloor) -- skyExposed is always false, the floor arm decides.
        // A moving (flowing) block in-zone, non-ocean, above the freeze floor -> freezes to plain ice.
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true),
                "a flowing block in-zone above the floor freezes (the fall dies at the moment of motion)");
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, PolarWaterFreezeRule.FREEZE_ALL_DEG, 0.5, false, true),
                "freezes right at the 85-deg front");
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, -88.0, 0.5, false, true), "southern pole too");
    }

    @Test
    void s17bFlowTick_newSpringOutflowFreezes_sameAsAnyMovingBlock() {
        // A new spring's first spread is just FLOWING water above the floor -- the flow tick freezes it exactly
        // like a fall's descending block, so "new springs freeze immediately" is the same pure decision.
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 86.0, 0.5, false, true),
                "a spring's outflow (flowing, above floor) freezes as it spreads");
    }

    @Test
    void s17bUpwardScan_aboveHeightmapFallColumnFreezes() {
        // The upward scan freezes standing flowing columns that free-fell ABOVE the MOTION_BLOCKING top. Every
        // belt block is above the surface, hence aboveFreezeFloor is true by construction -> the same freeze.
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true),
                "a standing fall column above the surface is caught by the upward scan");
        // The scan bound is a documented, bounded belt (cost is O(1)-ish per tick).
        assertEquals(24, PolarWaterFreezeRule.WATERFALL_UPWARD_SCAN_BLOCKS, "24-block upward belt");
        assertTrue(PolarWaterFreezeRule.WATERFALL_UPWARD_SCAN_BLOCKS > 0);
    }

    @Test
    void s17bDeepCaveFallStaysLiquid_theB9Reservation() {
        // A deep spring/fall BELOW the freeze floor, covered (skyExposed=false, aboveFreezeFloor=false) stays
        // liquid -- the flow tick's floor arm is false there, reserving the B-9 semi-ice cave lakes.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, false),
                "a covered flowing spring below the freeze floor stays liquid");
    }

    @Test
    void s17bOceanCascadeExempt_sacredPin() {
        // An ocean-family column never flow-freezes even above the floor (ocean is checked FIRST): the sea keeps
        // its liquid-under-ice for the under-ice swim / pole wall / S7 immersion.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, false, true),
                "flowing water over an ocean column stays liquid");
    }

    @Test
    void s17bOutOfZoneAndFlagOffLeaveMovingWaterVanilla() {
        // Equatorward of the frayed front the flow tick never freezes (vanilla flow)...
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 83.9, 0.0, false, true),
                "below the zone (threshold 84 at noise 0): moving water stays liquid");
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 80.0, 0.5, false, true));
        // ...and with the barrens family flag off, the flow tick is the untouched vanilla path.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, false, true),
                "flag-off leaves moving water exactly as vanilla (byte-identical)");
    }

    @Test
    void s17bFlowTickSharesTheOneFrayedFront() {
        // The flow tick rides the SAME +-1 deg wandering front as the surface ice / roofed descent, so a column's
        // moving-water freeze and its surface freeze agree: front dips to 84 at noise 0, pushes to 86 at noise 1.
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 84.2, 0.0, false, true), "front dips to 84");
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, false, 85.5, 1.0, false, true), "front pushes to 86");
        assertEquals(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.5, 1.0),
                PolarWaterFreezeRule.freezesFlowing(true, false, 85.5, 1.0, false, true),
                "the flow-tick decision (non-ocean, above floor) IS the shared frayed front");
    }

    // ---- S18/S19: GRADUAL BOTTOM-UP WATERFALL FREEZE (landed law + TWO-SPEED chance) -------------------

    @Test
    void s18LandedTruthTable_onlySupportedWaterIsLanded() {
        // The landed law: a flowing block is LANDED (motion-terminated) iff its below is neither air nor a fluid.
        // AIR below  -> still falling -> NOT landed (passes to vanilla flow, the fall runs to the ground).
        assertFalse(PolarWaterFreezeRule.landedOnSupport(true, false), "air below: still falling, not landed");
        // FLUID below (water/lava/other) -> sitting on more water -> NOT landed.
        assertFalse(PolarWaterFreezeRule.landedOnSupport(false, true), "water below: not landed (pouring onto water)");
        // SOLID below (not air, not fluid) -> landed. This is the ground -- and a horizontally-spreading base
        // block also reads here (its below is solid).
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false), "solid below: landed");
        // ICE below (not air, not fluid) -> landed. THIS IS THE CLIMB: each frozen block becomes the support the
        // water above lands on, so the freeze walks bottom-up. Ice is not a fluid, so the same predicate covers it.
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false),
                "ice below: landed -- the freeze climbs on its own frozen layers");
    }

    @Test
    void s19TwoSpeedDials_onSolidRolls_onIceCertain() {
        // S19 replaced the single 0.5 dial with TWO speeds. The on-solid dial is consulted with a strict < roll.
        assertEquals(0.2, PolarWaterFreezeRule.FREEZE_CHANCE_ON_SOLID, 1e-9, "the slow on-solid dial");
        assertEquals(1.0, PolarWaterFreezeRule.FREEZE_CHANCE_ON_ICE, 1e-9, "the certain on-ice dial");
        // ON SOLID (touchingIce=false): the roll decides against 0.2. Under -> locks; at/above -> the spread widens.
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, false, 0.0), "roll 0.0 < 0.2 -> freezes");
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, false, 0.1999), "just under 0.2 -> freezes");
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, false, 0.2),
                "exactly at the dial -> does NOT freeze (strict <), the base pour widens this tick");
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, false, 0.5), "above 0.2 -> widens");
        // TOUCHING ICE (touchingIce=true): CERTAIN -- freezes for ANY roll, even a losing 0.9999/1.0 (the roll is
        // never consulted). This is the reroute-hunter certainty + the deterministic vertical zipper.
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, true, 0.9999), "touching ice -> certain");
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, true, 1.0), "touching ice -> certain (roll ignored)");
    }

    @Test
    void s19TouchSet_belowPlusFourHorizontals_aboveExcluded() {
        // The touch set is BELOW + the 4 horizontal neighbours; ABOVE is not an argument at all (excluded by
        // construction -- water's source direction). Any single member true -> touching.
        assertFalse(PolarWaterFreezeRule.touchingIce(false, false, false, false, false), "no ice anywhere -> not touching");
        assertTrue(PolarWaterFreezeRule.touchingIce(true, false, false, false, false), "below is ice -> the vertical-climb contact");
        assertTrue(PolarWaterFreezeRule.touchingIce(false, true, false, false, false), "north neighbour ice -> reroute-hunter");
        assertTrue(PolarWaterFreezeRule.touchingIce(false, false, true, false, false), "east neighbour ice");
        assertTrue(PolarWaterFreezeRule.touchingIce(false, false, false, true, false), "south neighbour ice");
        assertTrue(PolarWaterFreezeRule.touchingIce(false, false, false, false, true), "west neighbour ice");
    }

    @Test
    void s19StillFallingAndIneligibleNeverFreeze_evenTouchingIce() {
        // The landed AND eligible gate PRECEDES the ice branch: touching ice can never freeze a still-falling or
        // an ineligible block. Still falling (not landed) beside ice -> the fall must reach the ground.
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(true, false, true, 0.0),
                "not landed -> vanilla flow even touching ice");
        // Not freeze-eligible (ocean / out-of-zone / below the floor / flag off) -> never, even landed and touching ice.
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(false, true, true, 0.0),
                "not eligible -> never freezes even landed and touching ice");
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(false, false, false, 0.0));
    }

    @Test
    void s19ClimbAndRerouteHeal_certainOnIce() {
        // THE ZIPPER: a fresh base landed on solid rolls slowly (0.2); once it is ice, the block above rests on ice
        // (below-ice = touching + landed) -> CERTAIN, so the climb is a deterministic 1-block-per-flow-tick zipper.
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, false, 0.5), "base on bare solid: slow roll may miss");
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, true, 0.5),
                "next block resting on the frozen base (below-ice) -> certain -> the zipper climbs");
        // THE REROUTE-HUNTER: water that reroutes horizontally onto/beside fresh ice is touching-ice -> locks one
        // block out, so the speckle heals even for a rerouting spread that a slow roll would have let escape.
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(true, true, true, 0.9),
                "reroute beside ice -> certain lock (the ice hunts the escaping water)");
    }

    @Test
    void s19LandedFreezeComposesWithFreezesFlowingEligibility() {
        // End-to-end shape the flow-tick mixin uses: eligibility = freezesFlowing (skyExposed=false, the floor arm
        // carries it), landed = landedOnSupport, touchingIce from the block states, roll = the tick RNG. In-zone,
        // non-ocean, above floor, landed on solid, winning on-solid roll -> freezes.
        boolean eligible = PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true);
        assertTrue(PolarWaterFreezeRule.flowTickFreezesLanded(eligible, PolarWaterFreezeRule.landedOnSupport(false, false), false, 0.1),
                "landed on solid, in-zone, winning roll -> the base of the fall locks");
        // Same block still FALLING (air below) -> the fall runs on, no freeze.
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(eligible, PolarWaterFreezeRule.landedOnSupport(true, false), false, 0.1),
                "same in-zone block but air below -> still falling, vanilla flow");
    }

    @Test
    void s19ExemptionsRePinned_oceanAndOutOfZoneAndFlagOff() {
        // THE SEA IS EXEMPT (sacred pin, re-asserted for the two-speed path): an ocean cascade is never eligible, so
        // even landed on solid AND touching ice with a winning roll it stays liquid.
        boolean oceanEligible = PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, false, true);
        assertFalse(oceanEligible, "ocean column never flow-eligible");
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(oceanEligible, true, true, 0.0), "ocean cascade stays liquid");
        // Out-of-zone (equatorward of the frayed front) -> not eligible -> landed water still passes to vanilla.
        boolean belowZoneEligible = PolarWaterFreezeRule.freezesFlowing(true, false, 83.9, 0.0, false, true);
        assertFalse(belowZoneEligible);
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(belowZoneEligible, true, true, 0.0), "below the zone: vanilla");
        // Flag off (byte-identical) -> not eligible -> vanilla flow even landed and touching ice.
        boolean flagOffEligible = PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, false, true);
        assertFalse(flagOffEligible);
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(flagOffEligible, true, true, 0.0), "flag off: byte-identical");
        // Deep-cave floor (covered, below the floor) -> not eligible -> a landed deep spring still stays liquid
        // (the B-9 reservation is preserved even when the water is technically supported below and beside ice).
        boolean deepEligible = PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, false);
        assertFalse(deepEligible, "below the freeze floor: not eligible (B-9 reservoir)");
        assertFalse(PolarWaterFreezeRule.flowTickFreezesLanded(deepEligible, true, true, 0.0), "deep landed spring stays liquid");
    }

    @Test
    void s18UpwardScanClimbsBottomUp_lowestLandedFirst() {
        // The upward-scan mixin freezes the LOWEST LANDED flowing block one-per-pass. Modelled purely: a fresh
        // fall's bottom block rests on the solid surface (landed) and freezes; the block above rests on WATER
        // (that same block, not yet ice) so it is NOT landed this pass -- it waits.
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false), "bottom block on solid: landed, freezes first");
        assertFalse(PolarWaterFreezeRule.landedOnSupport(false, true), "block above (on flowing water): not yet landed");
        // Next pass: the bottom block is now ICE, so the block above rests on ice -> landed -> freezes. The climb.
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false), "next pass: now resting on ice -> landed, climbs");
        // The scan belt bound is unchanged and bounded.
        assertEquals(24, PolarWaterFreezeRule.WATERFALL_UPWARD_SCAN_BLOCKS, "24-block upward belt (unchanged)");
    }
}
