package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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
    void s17bStandingFallColumn_nowOwnedByTheDescent_v6() {
        // v6 CORRECTION: the S17 "upward scan" was DEAD CODE (MOTION_BLOCKING counts fluids, javap-verified:
        // blocksMotion() || !getFluidState().isEmpty() -- the heightmap top is one ABOVE the topmost water, so
        // a scan from top+1 walked pure air) and was DELETED with its WATERFALL_UPWARD_SCAN_BLOCKS bound. A
        // standing cascade's crest IS the column surface, so the DOWNWARD descent owns it; the freeze decision
        // for such a column is unchanged.
        assertTrue(PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true),
                "a standing fall column above the freeze floor still freezes -- now via the descent");
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
    void s20HunterCertainOnIce_noOnSolidChanceRemains() {
        // S20 dropped the S18/S19 on-solid CHANCE entirely: the flow tick freezes ONLY when landed AND touching
        // ice, and then with CERTAINTY -- there is no RNG anywhere in the freeze path now (the FREEZE_CHANCE dials
        // are gone). TOUCHING ICE + landed + eligible -> certain freeze.
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(true, true, true),
                "eligible + landed + touching ice -> certain lock (the reroute-hunter / zipper)");
        // LANDED ON ORDINARY SOLID (not touching ice) -> the hunter does NOT freeze: the water keeps spreading and
        // is claimed later by the SETTLED SWEEP. This is the whole S20 shift -- ground freezing left the flow tick.
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(true, true, false),
                "landed on bare solid, not touching ice -> the flow tick leaves it (the sweep owns it once settled)");
    }

    @Test
    void s20SweepFreezesSettledLandedOnly_certainNoDice() {
        // THE SETTLED SWEEP (consumer A, the primary freezer): freezes iff eligible + landed + SETTLED, certain.
        assertTrue(PolarWaterFreezeRule.sweepFreezesSettled(true, true, true),
                "eligible + landed + settled -> certain freeze (a pool ring)");
        // STILL SPREADING (not settled = has a pending fluid tick) -> the sweep NEVER touches it (the core S20 law:
        // "still-spreading water is never swept").
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(true, true, false),
                "not settled (still spreading) -> the sweep leaves it (never freezes moving water)");
        // STILL FALLING (not landed) -> not swept even if settled.
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(true, false, true), "not landed -> not swept");
        // Not eligible (ocean / out-of-zone / flag off / below floor) -> never, even landed and settled.
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(false, true, true), "not eligible -> never swept");
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
    void s20StillFallingAndIneligibleNeverFreeze_bothConsumers() {
        // The landed AND eligible gates PRECEDE the ice/settled contact: touching ice (hunter) or settled (sweep)
        // can never freeze a still-falling or an ineligible block. Still falling (not landed) beside ice / at rest.
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(true, false, true),
                "not landed -> vanilla flow even touching ice (hunter)");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(true, false, true),
                "not landed -> not swept even if settled (sweep)");
        // Not freeze-eligible (ocean / out-of-zone / below the floor / flag off) -> never, even landed + touching/settled.
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(false, true, true), "not eligible -> hunter never");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(false, true, true), "not eligible -> sweep never");
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(false, false, false));
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(false, false, false));
    }

    @Test
    void s20HunterZipperAndRerouteHeal_certainOnIce() {
        // THE ZIPPER: a fresh base on bare solid is NOT frozen by the flow tick (the sweep freezes it once settled);
        // but once ANY base ice exists, the block resting on it (below-ice = touching + landed) is CERTAIN, so the
        // climb is a deterministic 1-block-per-flow-tick zipper -- the freeze cannot be outrun.
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(true, true, false),
                "base on bare solid: the flow tick does not lock it (left to the settled sweep)");
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(true, true, true),
                "block resting on the frozen base (below-ice) -> certain -> the zipper climbs");
        // THE REROUTE-HUNTER: water that reroutes horizontally onto/beside fresh ice is touching-ice -> locks one
        // block out, so the speckle can never escape the growing ice.
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(true, true, true),
                "reroute beside ice -> certain lock (the ice hunts the escaping water)");
    }

    @Test
    void s20BothConsumersComposeWithFreezesFlowingEligibility() {
        // End-to-end shape both mixins use: eligibility = freezesFlowing (skyExposed=false, the floor arm carries
        // it), landed = landedOnSupport, then EITHER the flow-tick hunter (touchingIce) OR the settled sweep (settled).
        boolean eligible = PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, true);
        assertTrue(eligible, "in-zone, non-ocean, above floor -> eligible");
        boolean landed = PolarWaterFreezeRule.landedOnSupport(false, false);
        // Flow-tick hunter: landed on ice-contact -> certain.
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(eligible, landed, true),
                "landed + touching ice -> the hunter locks it");
        // Settled sweep: landed + settled -> certain (this is what actually freezes a settled pool).
        assertTrue(PolarWaterFreezeRule.sweepFreezesSettled(eligible, landed, true),
                "landed + settled -> the sweep freezes it");
        // Same block still FALLING (air below) -> neither consumer acts (the fall runs on).
        boolean falling = PolarWaterFreezeRule.landedOnSupport(true, false);
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(eligible, falling, true), "air below -> hunter waits");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(eligible, falling, true), "air below -> sweep waits");
    }

    @Test
    void s20ExemptionsRePinned_oceanAndOutOfZoneAndFlagOffAndDeepFloor() {
        // THE SEA IS EXEMPT (sacred pin, re-asserted for BOTH S20 consumers): an ocean column is never eligible, so
        // neither the hunter nor the sweep ever freezes it, however landed/settled/ice-touching.
        boolean oceanEligible = PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, false, true);
        assertFalse(oceanEligible, "ocean column never flow-eligible");
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(oceanEligible, true, true), "ocean cascade: hunter never");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(oceanEligible, true, true), "ocean pool: sweep never");
        // Out-of-zone (equatorward of the frayed front) -> not eligible -> both consumers pass to vanilla.
        boolean belowZoneEligible = PolarWaterFreezeRule.freezesFlowing(true, false, 83.9, 0.0, false, true);
        assertFalse(belowZoneEligible);
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(belowZoneEligible, true, true), "below zone: hunter never");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(belowZoneEligible, true, true), "below zone: sweep never");
        // Flag off (byte-identical) -> not eligible -> both pass to vanilla.
        boolean flagOffEligible = PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, false, true);
        assertFalse(flagOffEligible);
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(flagOffEligible, true, true), "flag off: hunter never");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(flagOffEligible, true, true), "flag off: sweep never");
        // Deep-cave floor (covered, below the floor) -> not eligible -> a deep spring stays liquid (B-9 reservoir).
        boolean deepEligible = PolarWaterFreezeRule.freezesFlowing(true, false, 89.0, 0.5, false, false);
        assertFalse(deepEligible, "below the freeze floor: not eligible (B-9 reservoir)");
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(deepEligible, true, true), "deep spring: hunter never");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(deepEligible, true, true), "deep spring: sweep never");
    }

    @Test
    void s18SweepClimbsBottomUp_lowestLandedFirst() {
        // The sweep freezes from the LANDED base upward. Modelled purely: a fresh fall's bottom block rests on
        // the solid surface (landed) and freezes; the block above rests on WATER (that same block, not yet ice)
        // so it is NOT landed this pass -- it waits.
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false), "bottom block on solid: landed, freezes first");
        assertFalse(PolarWaterFreezeRule.landedOnSupport(false, true), "block above (on flowing water): not yet landed");
        // Next pass: the bottom block is now ICE, so the block above rests on ice -> landed -> freezes. The climb.
        assertTrue(PolarWaterFreezeRule.landedOnSupport(false, false), "next pass: now resting on ice -> landed, climbs");
    }

    // ---- S21(d): WATER v5 -- SOURCE-FREEZES-LAST + SWEEP 5x + the SPREAD-STOPPER coverage ------

    @Test
    void s21dSourceFreezePostponedTruthTable() {
        // The pure source-last decision (owner TEST-111: the still-water surface froze his SOURCE first and
        // beheaded the fall). A source touching ANY live flowing water REFUSES to freeze this pass (postpone =
        // true); with no flowing neighbour it freezes (postpone = false). Pass-through of the shim's six-neighbour
        // OR (below + 4 horizontals + ABOVE).
        assertTrue(PolarWaterFreezeRule.sourceFreezePostponed(true),
                "adjacent live flowing water -> the source outlives its fall (postpone)");
        assertFalse(PolarWaterFreezeRule.sourceFreezePostponed(false),
                "no adjacent flowing water -> the source freezes (last, once its flow is all ice/air)");
    }

    @Test
    void s21dSourceFreezesLastEmergentSemantics() {
        // The shim ORs the SIX neighbours (below + 4 horizontals + ABOVE) into adjacentHasFlowing; ABOVE is in
        // the set because a source directly UNDER a live fall is the beheading case too. "Freezes only when the
        // connected flow is fully ice" is then EMERGENT: the OR goes false exactly when no neighbour is flowing.
        boolean aboveFlowing = false || false || false || false || false || true; // only the ABOVE neighbour flows
        assertTrue(PolarWaterFreezeRule.sourceFreezePostponed(aboveFlowing),
                "a source under a live fall (ABOVE flowing) postpones -- above is in the touch set");
        boolean noneFlowing = false || false || false || false || false || false; // fully iced / drained
        assertFalse(PolarWaterFreezeRule.sourceFreezePostponed(noneFlowing),
                "no flowing neighbour (connected flow fully ice) -> the source freezes, last");
    }

    @Test
    void s21dSweepCapIsEight_theFiveXPacing() {
        // SWEEP 5x: the per-column-per-pass cap rises from the S20 effective ~1-2 to 8 (owner's "still ~5x too
        // slow"). 8 / ~1.6 ~= 5, and a deep freezable column of depth D ices in ceil(D/8) passes vs ceil(D/~1.6).
        assertEquals(8, PolarWaterFreezeRule.SWEEP_MAX_PER_COLUMN, "the SWEEP 5x cap");
        assertTrue(PolarWaterFreezeRule.SWEEP_MAX_PER_COLUMN > 2, "strictly faster than the old ~1-2 per pass");
        // D=40 (the glacier-sole floor depth) ices in 5 sweep passes now (was ~25 at ~1.6/pass) = the ~5x.
        assertEquals(5, (int) Math.ceil(
                        (double) PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS / PolarWaterFreezeRule.SWEEP_MAX_PER_COLUMN),
                "a glacier-sole-deep (40) column freezes in 5 sweep passes");
    }

    @Test
    void s21dSweepCapDoesNotAlterTheSettledDecision() {
        // The 5x is a PACING cap only -- the certain settled-sweep decision is unchanged: still eligible + landed
        // + settled, still no RNG. The cap governs how MANY such blocks a pass claims, not WHICH freeze.
        assertTrue(PolarWaterFreezeRule.sweepFreezesSettled(true, true, true));
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(true, true, false), "still-spreading never swept");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(true, false, true), "still-falling never swept");
        assertFalse(PolarWaterFreezeRule.sweepFreezesSettled(false, true, true), "ineligible never swept");
    }

    @Test
    void s21dSpreadStopper_orthogonalIceCaughtDiagonalReliesOnRings() {
        // The SPREAD-STOPPER: a landed flowing block TOUCHING ICE freezes (certain) rather than spreading. The
        // touch set is orthogonal (below + 4 horizontals); a spreading edge block touching ice ORTHOGONALLY is
        // caught (dies at one block)...
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(true, true,
                        PolarWaterFreezeRule.touchingIce(false, true, false, false, false)),
                "ice orthogonally north of a landed edge block -> caught");
        assertTrue(PolarWaterFreezeRule.flowTickHunterFreezes(true, true,
                        PolarWaterFreezeRule.touchingIce(true, false, false, false, false)),
                "ice directly below (the vertical zipper contact) -> caught");
        // ...while a PURELY-DIAGONAL contact is NOT in the touch set (all five orthogonal args false) -> not
        // caught this tick. Per the coverage argument this is sound: water cannot reach a diagonal-only cell
        // without first occupying (and freezing) an orthogonal neighbour of that ice, so the diagonal cell never
        // holds live water; when its orthogonal neighbour freezes it becomes an orthogonal contact next ring.
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(true, true,
                        PolarWaterFreezeRule.touchingIce(false, false, false, false, false)),
                "diagonal-only ice (no orthogonal member) -> not caught this tick; the ring catches it next");
    }

    @Test
    void s21dSpreadStopperStillRequiresLanded_fallsRunFreePastIce() {
        // The spread-stopper never freezes a still-FALLING block even beside ice -- falls run free (S18), so an
        // ice wall beside a fall does not behead it mid-air; it freezes only after it LANDS (the same law that,
        // on the source side, keeps the source from beheading its own fall).
        assertFalse(PolarWaterFreezeRule.flowTickHunterFreezes(true, false,
                        PolarWaterFreezeRule.touchingIce(false, true, false, false, false)),
                "falling water beside an ice wall (not landed) -> keeps falling, not frozen");
    }

    @Test
    void s21dSourceVetoIsIndependentOfFreezeEligibility_sacredExemptionsHold() {
        // The source-last postpone is a SEPARATE gate layered above the freeze decision: a pure boolean of the
        // neighbour scan, it does not consult ocean/zone/floor eligibility, so it can only ever DELAY a freeze the
        // column already qualifies for, never create or move one. Re-pin the sacred exemptions are untouched by
        // S21(d): the sea stays liquid-under-ice, flag-off stays byte-identical.
        assertFalse(PolarWaterFreezeRule.freezesFlowing(true, true, 89.0, 0.5, false, true), "ocean still exempt");
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, true, 90.0, 0.5), "sea sacred pin holds");
        assertFalse(PolarWaterFreezeRule.freezesFlowing(false, false, 89.0, 0.5, false, true), "flag-off unchanged");
        // The postpone answer is orthogonal to any freeze-eligibility context.
        assertTrue(PolarWaterFreezeRule.sourceFreezePostponed(true));
        assertFalse(PolarWaterFreezeRule.sourceFreezePostponed(false));
    }

    // ---- S22 WATER v6 (TEST 113, the FOURTH live failure): tick front / cadence / spread-convert ------

    @Test
    void s22TickFrontRidesTheBarrensBand_82to84() {
        // (a) The tick front IS the shared barrens band decision (ONSET 82 -> FULL 84, smoothstep on the
        // coherent barrens fray) -- one decision with the biome/glacier/crevasse carvers. Pin the anchors this
        // build assumed (defaults; live-tunable dials would move the band AND the front together, by design).
        assertEquals(82.0, PolarBarrensBand.ONSET_DEG, 1e-9, "barrens onset (and so the tick-front onset)");
        assertEquals(84.0, PolarBarrensBand.FULL_DEG, 1e-9, "barrens full (tick front unconditionally on)");
        assertEquals(PolarBarrensBand.ONSET_DEG, PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG, 1e-9,
                "the tick-front cheap gate is KEEP-SHARED with the barrens onset");
        // At/below the onset: never (the owner's 84S pour was ABOVE this; 82 itself is fraction 0).
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(true, 82.0, 0.0), "exactly 82: fraction 0, no front");
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(true, 60.0, 0.0));
        // Inside the fray strip the front follows the band fraction: at 83.0 the smoothstep is exactly 0.5,
        // so a fray sample below 0.5 is barrens (front on) and one at/above 0.5 is not (front off).
        assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 83.0, 0.4), "83 deg, fray-winning column");
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(true, 83.0, 0.6), "83 deg, fray-losing column");
        // At/above FULL the front is unconditionally on -- INCLUDING the owner's exactly-85S pour and the
        // whole old >= 85 zone (superset: no live column loses machinery). Any noise sample passes.
        for (double noise : new double[]{0.0, 0.5, 0.999999}) {
            assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 84.0, noise), "84: fraction 1.0");
            assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 85.0, noise), "85: the owner's pour");
            assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 90.0, noise), "the pole");
        }
        // Hemisphere symmetry + flag/NaN safety (family idioms).
        assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, -84.5, 0.5), "southern hemisphere identical");
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(false, 89.0, 0.5), "flag off: no tick front");
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(true, Double.NaN, 0.5), "NaN latitude: never");
        // NaN noise degrades to 0.5 -- deep-cap coverage never opens a hole on bad data.
        assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 86.0, Double.NaN), "NaN noise at 86: still on");
        assertFalse(PolarWaterFreezeRule.tickFrontFreezes(true, 83.0, Double.NaN), "NaN noise at 83 = 0.5: off");
    }

    @Test
    void s22WorldgenPathStillSeesThe85Law_byteIdentity() {
        // The v6 widening is TICK-TIME ONLY. The worldgen-facing law -- FREEZE_ALL_DEG and every predicate the
        // gen paths call (freezesWater / freezesWaterFrayed / freezesLandWaterSolid) -- is pinned unmoved:
        // 84.5 sits above the barrens FULL_DEG (84), so the tick front covers it live, yet the worldgen razor
        // still refuses everything below 85 -- the two fronts are proven distinct at the same latitude.
        assertEquals(85.0, PolarWaterFreezeRule.FREEZE_ALL_DEG, 1e-9, "the worldgen sea-ice anchor stays 85");
        assertTrue(PolarWaterFreezeRule.tickFrontFreezes(true, 84.5, 0.5), "tick front covers 84.5 live");
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 84.5), "worldgen razor still refuses 84.5");
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 83.5, 0.5),
                "worldgen frayed front still refuses 83.5 (85 +/- 1 strip only)");
        assertFalse(PolarWaterFreezeRule.freezesLandWaterSolid(true, false, 83.0, 0.5),
                "the S14 solid-freeze (a gen-time law) does not follow the tick front");
    }

    @Test
    void s22TickFlowingEligibility_sacredExemptionsVerbatim() {
        // tickFreezesFlowing = the v6 WHERE for hunter/sweep/spread-converter. Ocean FIRST, then flag, then
        // the front, then the freeze floor -- the same order and the same exemptions as freezesLandWaterSolid.
        assertTrue(PolarWaterFreezeRule.tickFreezesFlowing(true, false, 83.0, 0.4, true),
                "83-deg fray-winning land column above the floor: the NEW 82-84 coverage");
        assertTrue(PolarWaterFreezeRule.tickFreezesFlowing(true, false, 89.0, 0.5, true),
                "deep cap unchanged");
        assertFalse(PolarWaterFreezeRule.tickFreezesFlowing(true, true, 89.0, 0.5, true),
                "THE SEA IS EXEMPT -- ocean wins first, even at the pole");
        assertFalse(PolarWaterFreezeRule.tickFreezesFlowing(true, false, 89.0, 0.5, false),
                "below the freeze floor: the B-9 reservoir stays liquid");
        assertFalse(PolarWaterFreezeRule.tickFreezesFlowing(false, false, 89.0, 0.5, true),
                "barrens flag off: byte-identical");
        assertFalse(PolarWaterFreezeRule.tickFreezesFlowing(true, false, 83.0, 0.6, true),
                "fray-losing 82-84 column: no machinery (one decision with the barrens ground)");
        assertFalse(PolarWaterFreezeRule.tickFreezesFlowing(true, false, Double.NaN, 0.5, true), "NaN: never");
    }

    @Test
    void s22RoundRobin_fullChunkCoverageEvery32Ticks_deterministic() {
        // (b) The cadence law: K=8 columns/chunk/tick, index (gameTime*K + i) mod 256 -> every one of the 256
        // columns is visited EXACTLY once per 32-tick window, for any window start, with no RNG.
        assertEquals(8, PolarWaterFreezeRule.SWEEP_COLUMNS_PER_CHUNK_TICK);
        assertEquals(256, PolarWaterFreezeRule.SWEEP_COLUMN_COUNT);
        assertEquals(32, PolarWaterFreezeRule.SWEEP_FULL_COVERAGE_TICKS, "256 / 8");
        for (long start : new long[]{0L, 1L, 17L, 100_000L, Long.MAX_VALUE - 40L, -5L}) {
            Set<Integer> seen = new HashSet<>();
            for (long t = 0; t < PolarWaterFreezeRule.SWEEP_FULL_COVERAGE_TICKS; t++) {
                for (int i = 0; i < PolarWaterFreezeRule.SWEEP_COLUMNS_PER_CHUNK_TICK; i++) {
                    int idx = PolarWaterFreezeRule.sweepColumnIndex(start + t, i);
                    assertTrue(idx >= 0 && idx < 256, "index in [0,256)");
                    assertTrue(seen.add(idx), "no column visited twice inside one coverage window");
                }
            }
            assertEquals(256, seen.size(), "all 256 columns hit within 32 ticks (start=" + start + ")");
        }
        // Determinism: the same (gameTime, slot) always yields the same column -- pure tick math, no RNG.
        assertEquals(PolarWaterFreezeRule.sweepColumnIndex(12345L, 3),
                PolarWaterFreezeRule.sweepColumnIndex(12345L, 3), "pure function");
        // Negative gameTime (hostile clock arithmetic) stays in range via floorMod.
        assertTrue(PolarWaterFreezeRule.sweepColumnIndex(-1L, 0) >= 0, "floorMod handles negatives");
    }

    @Test
    void s22SpreadConvertPredicateTable() {
        // (c) CONVERT-AT-SPREAD: the pure decision the spread mixin feeds. Eligibility carries front/ocean/
        // floor/flag; then falls-run-free governs DOWN spreads; ice-adjacency governs horizontal spreads.
        boolean eligible = PolarWaterFreezeRule.tickFreezesFlowing(true, false, 89.0, 0.5, true);
        assertTrue(eligible);
        // HORIZONTAL spread beside/onto ice -> convert (the ratchet-breaker: the re-spread layer dies at the
        // moment of spread, before it ever exists to schedule ticks).
        assertTrue(PolarWaterFreezeRule.spreadConvertsToIce(eligible, false, false,
                        PolarWaterFreezeRule.touchingIce(false, true, false, false, false)),
                "horizontal spread with a horizontal ice contact -> ice at the destination");
        assertTrue(PolarWaterFreezeRule.spreadConvertsToIce(eligible, false, true,
                        PolarWaterFreezeRule.touchingIce(true, false, false, false, false)),
                "horizontal spread onto ice (below-contact) -> ice");
        // DOWN spread: FALLS RUN FREE -- converts ONLY when landing ON ice; an ice wall BESIDE the falling
        // stream never beheads it mid-air.
        assertTrue(PolarWaterFreezeRule.spreadConvertsToIce(eligible, true, true, true),
                "fall landing on the frozen pile -> locks (the zipper's contact, one tick early)");
        assertFalse(PolarWaterFreezeRule.spreadConvertsToIce(eligible, true, false,
                        PolarWaterFreezeRule.touchingIce(false, true, false, false, false)),
                "fall dropping PAST an ice wall (horizontal contact only) stays water -- falls run free");
        // No ice contact at all -> ordinary spread, water places as vanilla.
        assertFalse(PolarWaterFreezeRule.spreadConvertsToIce(eligible, false, false, false),
                "not ice-adjacent: vanilla spread");
        // Ineligible destinations never convert, whatever the adjacency: the sacred exemptions ride inside
        // tickFreezesFlowing (ocean / below-floor / flag-off / off-front).
        boolean ocean = PolarWaterFreezeRule.tickFreezesFlowing(true, true, 89.0, 0.5, true);
        boolean deep = PolarWaterFreezeRule.tickFreezesFlowing(true, false, 89.0, 0.5, false);
        boolean flagOff = PolarWaterFreezeRule.tickFreezesFlowing(false, false, 89.0, 0.5, true);
        boolean offFront = PolarWaterFreezeRule.tickFreezesFlowing(true, false, 81.0, 0.0, true);
        for (boolean e : new boolean[]{ocean, deep, flagOff, offFront}) {
            assertFalse(e);
            assertFalse(PolarWaterFreezeRule.spreadConvertsToIce(e, false, true, true),
                    "ineligible destination (ocean/deep/flag-off/off-front): water spreads as vanilla");
        }
    }

    @Test
    void s22DescentExtension_pastTheRoofReachToTheLandedBase() {
        // (a2) The descent cap: falls taller than the 16-block roof reach were unreachable (the old wall);
        // the sweep now follows a FLOWING column past the reach down to its landed base, capped at 48.
        assertEquals(48, PolarWaterFreezeRule.FLOWING_DESCENT_CAP_BLOCKS);
        assertTrue(PolarWaterFreezeRule.FLOWING_DESCENT_CAP_BLOCKS
                        > PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS,
                "the flowing descent reaches past the old 16-block wall");
        assertTrue(PolarWaterFreezeRule.FLOWING_DESCENT_CAP_BLOCKS
                        > PolarWaterFreezeRule.LAND_WATER_FREEZE_DEPTH_BLOCKS,
                "the walk can reach the freeze floor (40) before the cap (48), so the freeze floor -- not the"
                        + " cap -- is what protects the B-9 reservoir");
        // The ROOF reach itself is unchanged (shelter scale; deep sealed lakes keep their liquid surface).
        assertEquals(16, PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS, "roof reach unchanged");
    }

    @Test
    void s22TickFloorIsBedInclusive_theTest114PadFencepost() {
        // TEST 114 self-fly finding: OCEAN_FLOOR heightmap sits ONE ABOVE the topmost solid, so a
        // one-deep water sheet resting on the bed (pad: snow@141, water@142 -> worldSurfaceY=143,
        // oceanFloorY=142) sat exactly AT the exclusive worldgen floor and the settled sweep claimed
        // ZERO live. The tick floor's bed term is oceanFloorY-1: the bottom layer becomes eligible.
        assertEquals(142, PolarWaterFreezeRule.landWaterFreezeFloorY(143, 142),
                "worldgen floor UNCHANGED (glacier pass byte-identity)");
        assertEquals(141, PolarWaterFreezeRule.tickLandWaterFreezeFloorY(143, 142),
                "tick floor is bed-inclusive: the pad's water@142 > 141 is now eligible");
        // The B-9 deep reservoir is untouched: when the depth cap (worldSurface-40) wins, both agree.
        assertEquals(PolarWaterFreezeRule.landWaterFreezeFloorY(200, 100),
                PolarWaterFreezeRule.tickLandWaterFreezeFloorY(200, 100),
                "deep-cap regime identical: the reservoir keeps its liquid depth at tick time too");
    }
}
