package com.example.globe.core;

/**
 * Pure decision for the "all exposed water is frozen at the poles" correctness rule (Core Logic layer,
 * zero Minecraft imports, unit-testable in a plain JVM). Sibling of {@link PolarPrecipitationRule}.
 *
 * <p><b>The bug.</b> Standing at 89 deg S in a full blizzard, Peetsa saw a pool of LIQUID water in his
 * shelter doorway. Vanilla decides "should this exposed water column freeze into ice" per column via
 * {@code Biome.shouldFreeze(LevelReader, BlockPos, boolean)}, whose very first gate is
 * {@code if (this.warmEnoughToRain(pos, seaLevel)) return false;} -- i.e. a biome whose temperature is
 * >= 0.15 never freezes its water. The polar cap is riddled with LATITUDE-BLIND columns: vanilla's noise
 * router places {@code river} (base temperature 0.5) and {@code ocean} (0.5) anywhere including 89 deg,
 * and {@code LatitudeBiomeSource} never re-classifies them, so a polar river/ocean column reports
 * "warm enough to rain" and its water stays liquid forever. Player-placed water (buckets) sitting on a
 * warm-biome column has the same problem. This is the exact water-freezing analogue of the rain-at-the-pole
 * bug that {@link PolarPrecipitationRule} fixes for precipitation.
 *
 * <p><b>The rule.</b> At extreme latitudes every exposed water column must be eligible to freeze,
 * biome-independent. The fix piggybacks on vanilla's OWN freeze mechanic + cadence rather than writing a
 * new one: it neutralises only the single latitude-blind sub-decision -- the {@code warmEnoughToRain}
 * temperature gate inside {@code Biome.shouldFreeze} (see {@code BiomePolarWaterFreezeMixin}) -- so the
 * rest of vanilla's genuine "is this actually freezable exposed water" logic (inside build height, block
 * light &lt; 10, fluid is water, block is a {@code LiquidBlock}, edge-of-water) runs UNCHANGED and only
 * genuine water turns to ice. Because {@code Biome.shouldFreeze} is the single method every freeze path
 * funnels through (ongoing {@code ServerLevel.tickPrecipitation} random-tick freezing, plus the worldgen
 * {@code LakeFeature} and {@code SnowAndFreezeFeature} paths), one hook covers ongoing AND at-generation
 * freezing, and it inherits vanilla's edge-inward, over-time freeze cadence (so this needs no latitude
 * fade -- the spread is a property of vanilla's tick loop, not of latitude).
 *
 * <p><b>Threshold = 85 deg (its OWN anchor -- DECOUPLED from the ambient onset, B-7 S3).</b> This forces
 * exposed water to freeze into ice, which MODIFIES THE WORLD (places ice blocks) and is therefore a
 * WORLDGEN-facing seam that must NOT move. When B-7 S3 shifted the pure-client ambient snow/fog onset
 * {@code PolarHazardWindow.AMBIENT_ONSET_DEG} 85 -> 82 (2026-07-13; S8 moved it again to 80 on 2026-07-14),
 * this constant deliberately STAYED at 85
 * on its own literal anchor: the frozen ice sheet is world-visible and moving it would re-freeze different
 * columns in existing worlds. 85 still coincides with the B-7 frostbite DAMAGE onset
 * ({@code PolarHazardWindow.FROSTBITE_ONSET_DEG}, also 85), so "the water is frozen" and "the cold starts to
 * bite" read as one line, just now inside a whiteout that began a few degrees earlier. It also sits poleward of
 * the naturally-cold bands (subpolar taiga / snowy) whose water vanilla ALREADY freezes on its own, so those
 * are untouched and only the latitude-blind warm columns carried into the deep polar cap are corrected. It
 * stays poleward of {@link PolarPrecipitationRule#FORCE_SNOW_DEG} (75).
 */
public final class PolarWaterFreezeRule {

    private PolarWaterFreezeRule() {
    }

    /**
     * Latitude (deg) at/above which any exposed, genuinely-freezable water column is forced eligible to
     * freeze into ice regardless of its (possibly latitude-blind) biome temperature. 85 deg is this rule's OWN
     * literal anchor -- it is NOT derived from {@code PolarHazardWindow.AMBIENT_ONSET_DEG} (which B-7 S3 moved
     * to 82 and S8 to 80). Because freezing water places ice (a world modification), this worldgen-facing threshold must never
     * move under a client-atmosphere change; see the class javadoc.
     */
    public static final double FREEZE_ALL_DEG = 85.0;

    /**
     * Should an exposed water column be forced eligible to freeze at this latitude?
     *
     * <p>This answers ONLY the latitude question. The caller (the {@code Biome.shouldFreeze} hook) still
     * defers every "is there actually freezable exposed water here" check to vanilla, so a {@code true}
     * here never fabricates ice on a non-water column -- it only removes the biome-temperature veto.
     *
     * @param isGlobeWorld true only on a Latitude globe world; non-globe (vanilla/other) worlds are never
     *                     overridden, so this returns false and vanilla freezing is untouched.
     * @param latDeg       signed OR absolute latitude in degrees; magnitude is taken internally so both
     *                     hemispheres (e.g. +88 and -88) behave identically. NaN -> false.
     * @return true iff on a globe world and {@code |latDeg| >= FREEZE_ALL_DEG}.
     */
    public static boolean freezesWater(boolean isGlobeWorld, double latDeg) {
        if (!isGlobeWorld || Double.isNaN(latDeg)) {
            return false;
        }
        return Math.abs(latDeg) >= FREEZE_ALL_DEG;
    }

    // --- B-9a SEA-FREEZE FRAY (Peetsa 2026-07-16, TEST 99 screenshots: the 85-deg freeze line was a harsh
    // --- razor seam on JourneyMap and in-world) --------------------------------------------------------

    /** Half-width (deg) of the freeze-line fray: the effective threshold wanders {@code 85 +/- 1} deg on a
     *  coherent noise field (the barrens fray idiom -- ValueNoise2D, dedicated salt, Art VI clean). The 85
     *  anchor itself ({@link #FREEZE_ALL_DEG}) does NOT move -- a fray sample of exactly 0.5 reproduces the
     *  razor line bit-for-bit. */
    public static final double FRAY_HALF_WIDTH_DEG = 1.0;

    /** True iff {@code |lat|} lies inside the frayable strip {@code [85-1, 85+1]}. Outside it the frayed
     *  predicate ALWAYS equals the razor predicate (every possible threshold is on the same side), so the
     *  consumer only pays a noise sample -- and can only diverge from the razor -- inside this strip. NaN ->
     *  false (fall back to the razor path). */
    public static boolean inFreezeFrayBand(double latDeg) {
        if (Double.isNaN(latDeg)) {
            return false;
        }
        return Math.abs(Math.abs(latDeg) - FREEZE_ALL_DEG) <= FRAY_HALF_WIDTH_DEG;
    }

    /**
     * The FRAYED freeze predicate ({@code latitude.polarBarrens.enabled} family; the flag gate lives in the
     * consumer -- {@code BiomePolarWaterFreezeMixin} -- so flag-off is the untouched razor
     * {@link #freezesWater}, byte-identical): the column freezes iff {@code |lat| >= 85 + (noise*2-1) * 1},
     * i.e. the freeze FRONT wanders +/- 1 deg on the coherent per-column fray sample. Deterministic per
     * column (seeded world noise), so worldgen-time and ongoing tick-time freezing always agree. A NaN noise
     * sample degrades to 0.5 = the exact razor line (never a hole in the ice sheet on bad data).
     */
    public static boolean freezesWaterFrayed(boolean isGlobeWorld, double latDeg, double frayNoise01) {
        if (!isGlobeWorld || Double.isNaN(latDeg)) {
            return false;
        }
        double n = Double.isNaN(frayNoise01) ? 0.5 : frayNoise01;
        double threshold = FREEZE_ALL_DEG + (n * 2.0 - 1.0) * FRAY_HALF_WIDTH_DEG;
        return Math.abs(latDeg) >= threshold;
    }

    // --- S11(b) FROZEN RIVERS -> COMPLETE ICE (Peetsa 2026-07-16, TEST 101) ---------------------------

    /**
     * S11(b): should a fluid column freeze FULL DEPTH (surface to bed, no water left -- which also kills
     * fish spawns for free)? True iff the flag is on (the barrens worldgen family -- the TEST-101 round's
     * flag law; independent of the tick-time {@code POLAR_WATER_FREEZE} kill switch, which governs the
     * surface-ice correctness fix), the column is RIVER-classified, NOT ocean-classified, and it sits in
     * the full-freeze zone ({@code >= }the 85 anchor including the fray -- the SAME
     * {@link #freezesWaterFrayed} front the surface ice uses, so the solid rivers and the frozen sea edge
     * agree on where winter starts).
     *
     * <p><b>THE SEA IS EXEMPT (regression guard):</b> ocean-family columns keep surface-ice-over-liquid --
     * the under-ice swim, the pole wall's pack-ice reading, and the S7 immersion mechanic all depend on
     * liquid sea under the ice; {@code isOceanColumn} wins over {@code isRiverColumn} if a column somehow
     * reads both. Non-river, non-ocean fluid columns (ponds/lakes) are also left alone -- the B-9 Glacial
     * Caves design owns semi-ice lakes with fish.
     */
    public static boolean freezesRiverSolid(boolean flagOn, boolean isRiverColumn, boolean isOceanColumn,
                                            double latDeg, double frayNoise01) {
        if (!flagOn || !isRiverColumn || isOceanColumn) {
            return false;
        }
        return freezesWaterFrayed(true, latDeg, frayNoise01);
    }

    /**
     * S11(b) ice-kind law for a solid-frozen river (documented choice): the SURFACE block is plain
     * {@code ice} -- the familiar translucent frozen-river skin, visually continuous with vanilla
     * frozen_river and the freeze law's own surface ice -- and everything BELOW is {@code packed_ice}
     * (reads as solid glacial mass, and cannot melt back to water from light updates the way plain ice
     * can, so a torch on the bank never re-opens a fishable hole in the "complete ice" river).
     */
    public static boolean riverIceIsPacked(int depthBelowSurface) {
        return depthBelowSurface > 0;
    }

    // --- S14(b) UNIVERSAL FREEZE (Peetsa 2026-07-17, TEST 104) -----------------------------------------
    // The owner found LIQUID water one block under the surface ice of a frozen LAKE at 89 deg, and
    // WATERFALLS cascading unfrozen into polar country. S11(b) only solid-froze RIVER-classified columns
    // (ponds/lakes were explicitly left for B-9). S14(b) generalises the solid freeze to ALL land-family
    // water and adds the flowing-water (waterfall) rule, all sharing the ONE frayed front the surface ice,
    // the solid rivers, and the frozen-sea edge already use. Two invariants carry over verbatim:
    //   * THE SEA IS EXEMPT -- ocean-family columns keep surface-ice-over-liquid (under-ice swim, the pole
    //     wall's pack-ice reading, and the S7 immersion mechanic all depend on liquid sea under the ice);
    //     the ocean exemption is checked FIRST, before the flag or the front.
    //   * CAVE WATER BELOW THE FLOOR STAYS LIQUID -- the solid freeze stops at a freeze floor pinned to the
    //     glacier body's sole, so B-9's Glacial Caves semi-ice lakes (with fish) have their reservoir.

    /**
     * S14(b): should a NON-OCEAN land-family water column (river, lake, pond, aquifer exposure) freeze
     * SOLID -- surface down to the freeze floor -- at this latitude? The generalisation of
     * {@link #freezesRiverSolid}: it DROPS the river-only requirement (any non-ocean fluid column now
     * freezes) while sharing the ONE frayed front ({@link #freezesWaterFrayed}), so solid lakes, solid
     * rivers, and the frozen-sea edge all agree on where winter starts. A river column
     * ({@code freezesRiverSolid(flag,true,false,lat,fray)}) returns exactly the same answer as
     * {@code freezesLandWaterSolid(flag,false,lat,fray)}, so S13 river behaviour is unchanged by
     * construction.
     *
     * <p><b>THE OCEAN EXEMPTION IS FIRST IN THE CHAIN (S14b re-pin):</b> an ocean-family column returns
     * {@code false} before the flag or the front is consulted -- the dual-read ocean-wins pin the
     * solid-river rule shipped with, re-asserted for the generalised path. The sea keeps
     * surface-ice-over-liquid.
     *
     * @param flagOn        {@code latitude.polarBarrens.enabled} (the S11/S14 worldgen family); off is the
     *                      untouched razor path (rivers keep today's surface-only freeze), byte-identical.
     * @param isOceanColumn true iff the column's biome is {@code BiomeTags.IS_OCEAN}; wins over everything.
     * @param latDeg        signed OR absolute column latitude; magnitude taken internally. NaN -> false.
     * @param frayNoise01   the SAME coherent sea-freeze fray sample the surface-ice law feeds
     *                      {@link #freezesWaterFrayed} (NaN -> the razor line).
     */
    public static boolean freezesLandWaterSolid(boolean flagOn, boolean isOceanColumn,
                                                double latDeg, double frayNoise01) {
        if (isOceanColumn) {
            return false; // THE SEA IS EXEMPT -- ocean-wins, FIRST in the chain.
        }
        if (!flagOn) {
            return false;
        }
        return freezesWaterFrayed(true, latDeg, frayNoise01);
    }

    /**
     * S14(b) FREEZE FLOOR DEPTH (blocks below the water surface). The glacier body pass SKIPS fluid columns,
     * so the glacier is ABSENT over water -- this is the design's "heightmap-minus-N" branch, with N pinned
     * to the glacier body's FULL-BAND SOLE ({@link PolarBarrensBand#GLACIER_SNOW_CAP_BLOCKS} snow cap +
     * {@link PolarBarrensBand#GLACIER_ICE_MAX_BLOCKS} ice body = 40) so a frozen lake reaches as deep as the
     * neighbouring glacier and NO DEEPER: cave water below this floor stays liquid, reserved for B-9's
     * semi-ice lakes with fish. Derived from the glacier constants (both compile-time literals) so the two
     * depth laws move together if either constant is retuned.
     */
    public static final int LAND_WATER_FREEZE_DEPTH_BLOCKS =
            PolarBarrensBand.GLACIER_SNOW_CAP_BLOCKS + PolarBarrensBand.GLACIER_ICE_MAX_BLOCKS;

    /**
     * The lowest world Y a solid land-water freeze reaches for a column, from its two WG heightmaps
     * ({@code worldSurfaceY} = fluid top, {@code oceanFloorY} = submerged bed). Returns the SHALLOWER
     * (higher) of the bed and the depth cap {@code worldSurfaceY - }{@link #LAND_WATER_FREEZE_DEPTH_BLOCKS}:
     * a shallow pond/river freezes to its bed (bed wins -- IDENTICAL to the S13 river freeze, which ran to
     * {@code oceanFloorY}); a deep aquifer exposure stops at the cap, leaving liquid below (the B-9
     * reservation). The freeze loop replaces water in {@code (floorY, worldSurfaceY]} -- the floor itself is
     * the first liquid block left untouched, i.e. EXCLUSIVE, matching the S13 {@code y > oceanFloorY} loop.
     */
    public static int landWaterFreezeFloorY(int worldSurfaceY, int oceanFloorY) {
        return Math.max(oceanFloorY, worldSurfaceY - LAND_WATER_FREEZE_DEPTH_BLOCKS);
    }

    /**
     * S14(b) WATERFALLS: should a FLOWING (non-source) water block freeze to a plain-ice cascade? Shares the
     * ONE frayed front AND the ocean exemption with {@link #freezesLandWaterSolid} (so a flowing block over
     * the sea, or below the zone, or with the flag off, is left liquid); then a flowing block freezes iff it
     * is SKY-EXPOSED (a visible waterfall in the open) OR ABOVE THE FREEZE FLOOR (inside the glacier band).
     * Deep, sky-covered flowing water -- an underground spring below the floor -- stays liquid ("underground
     * springs below the floor untouched"). The ice kind is always PLAIN {@code ice} (documented look: a thin
     * translucent frozen cascade, never the glacial packed body), so this predicate answers only WHETHER, not
     * which-ice.
     *
     * @param skyExposed        the flowing block can see the sky (a waterfall in the open).
     * @param aboveFreezeFloor  the flowing block sits above the column's freeze floor (the glacier band).
     */
    public static boolean freezesFlowing(boolean flagOn, boolean isOceanColumn, double latDeg,
                                         double frayNoise01, boolean skyExposed, boolean aboveFreezeFloor) {
        if (!freezesLandWaterSolid(flagOn, isOceanColumn, latDeg, frayNoise01)) {
            return false;
        }
        return skyExposed || aboveFreezeFloor;
    }
}
