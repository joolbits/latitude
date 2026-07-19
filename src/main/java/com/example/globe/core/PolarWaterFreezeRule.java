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

    // --- S15(b) PERSISTENT ICE (Peetsa 2026-07-17, TEST 105) -------------------------------------------
    // Live evidence (video): "as soon as there is any shelter, the water is liquid instead of ice. Ice
    // needs to be a little more persistent." TWO tick-time causes keep sheltered polar water liquid, each
    // with its own law + consumer, both riding the SAME forced-freeze front the surface ice uses (>= 85,
    // frayed) and the SAME kill switch ({@code POLAR_WATER_FREEZE_ENABLED}):
    //   (1) SKY WAIVER  -- vanilla only tests the MOTION_BLOCKING-heightmap top of each column, so a roof
    //       shadows the water beneath it and it never freezes. In the forced-freeze zone the consumer
    //       (ServerLevelRoofedWaterFreezeMixin) reaches under the cover to the water surface and lets
    //       vanilla's own shouldFreeze freeze it -- "a roof does not save a pond at -30".
    //   (2) NO MELT     -- vanilla melts plain ice when block light > 11 (a torch beside a pond thaws it).
    //       In the forced-freeze zone the consumer (IceBlockPolarNoMeltMixin) cancels that light-driven
    //       melt -- a torch cannot thaw the pole.
    // These are TICK-TIME rules (not worldgen), so there is no atlas axis. The barrens-fray branch lives in
    // the consumers, mirroring BiomePolarWaterFreezeMixin, so flag-off / out-of-zone stays byte-identical.
    // Both laws are pinned to the SAME front the open-water freeze uses -- named as their own predicates so
    // each is unit-tested independently, and asserted to coincide with {@link #freezesWater} exactly the way
    // FREEZE_ALL_DEG and PolarHazardWindow.FROSTBITE_ONSET_DEG are separate constants proven to coincide.

    /**
     * S15(b) SKY WAIVER: in the full-freeze zone, does the tick freeze IGNORE vanilla's sky/cover
     * requirement, so water under a roof still freezes? The sky is waived EXACTLY where the open-water rule
     * forces water to freeze ({@link #freezesWater}) -- one front -- named as its own law so it is pinned
     * independently. NaN latitude / non-globe worlds return {@code false} (vanilla untouched). The frayed
     * variant is {@link #freezeIgnoresSkyFrayed}.
     */
    public static boolean freezeIgnoresSky(boolean isGlobeWorld, double latDeg) {
        return freezesWater(isGlobeWorld, latDeg);
    }

    /**
     * Frayed variant of {@link #freezeIgnoresSky}: the sky is waived on the SAME wandering +/-1 deg front
     * ({@link #freezesWaterFrayed}) the frozen-sea edge uses, so a column's roofed-water freeze and its
     * open-water freeze agree. NaN fray degrades to the exact razor line; non-globe / NaN latitude -> false.
     */
    public static boolean freezeIgnoresSkyFrayed(boolean isGlobeWorld, double latDeg, double frayNoise01) {
        return freezesWaterFrayed(isGlobeWorld, latDeg, frayNoise01);
    }

    /**
     * S15(b) NO MELT (the "ice does not melt in-zone" law): in the full-freeze zone, is the light-driven
     * ice melt CANCELLED? Vanilla melts plain {@code ice} when block light &gt; 11 (so a torch beside a
     * frozen pond re-opens water); at the pole the owner wants ice to persist, so in-zone the consumer
     * cancels {@code IceBlock}'s random-tick melt -- a torch cannot thaw the pole. Melt is cancelled EXACTLY
     * on the forced-freeze front ({@link #freezesWater}), so ice thaws normally equatorward of it (vanilla)
     * and never poleward. <b>Plain ice only</b>: packed/blue ice have no melt tick, and frosted ice keeps
     * its own scheduled (neighbour-count) melt -- neither is a plain {@code IceBlock} random-tick melt.
     * NaN latitude / non-globe -> false (melt proceeds, vanilla). Frayed variant: {@link #iceMeltCancelledFrayed}.
     */
    public static boolean iceMeltCancelled(boolean isGlobeWorld, double latDeg) {
        return freezesWater(isGlobeWorld, latDeg);
    }

    /**
     * Frayed variant of {@link #iceMeltCancelled}: melt is cancelled on the SAME frayed front the ice
     * formed on ({@link #freezesWaterFrayed}), so a column that froze (frayed) will not thaw (frayed) --
     * the no-melt boundary tracks the freeze boundary per column. NaN fray -> the razor line; non-globe /
     * NaN latitude -> false.
     */
    public static boolean iceMeltCancelledFrayed(boolean isGlobeWorld, double latDeg, double frayNoise01) {
        return freezesWaterFrayed(isGlobeWorld, latDeg, frayNoise01);
    }

    /**
     * S15(b) ROOF REACH (blocks): how far below the MOTION_BLOCKING top (the roof) the sky-waived tick
     * freeze descends looking for the topmost water surface. Bounded to shelter/overhang scale -- a
     * sheltered pond within this reach freezes at its surface, while water sealed DEEPER than this (a deep
     * cave lake) keeps its liquid surface, the B-9 Glacial Caves reservoir (the same spirit as the S14
     * "cave water below the floor stays liquid" invariant). 16 = one chunk section: it comfortably covers
     * any built shelter, while a rock ceiling thicker than a chunk section reads as "underground", not
     * "sheltered". The freeze itself is only a SURFACE skin (the water below the frozen block stays liquid),
     * so this reach never disturbs the deep reservoir even if a near-surface cave is grazed.
     */
    public static final int ROOFED_FREEZE_REACH_BLOCKS = 16;

    // --- S17(b) WATERFALL FREEZE v3 (Peetsa 2026-07-18, TEST 107 video: liquid waterfalls STILL cascade) ---
    // ROOT CAUSE (two-flight): an open-air cascade free-falls ABOVE the MOTION_BLOCKING heightmap top (water is
    // not motion-blocking terrain), so the S16 roofed DESCENT -- which walks DOWNWARD from that top -- never
    // reaches the falling column. v3 adds TWO seams, both riding the ONE frayed front + ocean exemption + freeze
    // floor the S14/S15/S16 rules already use, so nothing new about WHERE water freezes, only WHEN it is caught:
    //   * THE FLOW TICK (FlowingFluidWaterfallFreezeMixin on FlowingFluid.tick): the single method every moving
    //     water block funnels through. In-zone, above the floor, non-ocean, a FLOWING (non-source) block that
    //     attempts to flow becomes plain ICE and the vanilla spread is cancelled -- the fall dies at the moment
    //     of motion, a new spring's outflow freezes as it spreads. The decision is {@link #freezesFlowing}.
    //   * THE UPWARD SCAN (below): a bounded belt scanned UPWARD from the MOTION_BLOCKING top catches STANDING
    //     cascade columns that reached equilibrium and stopped re-ticking (the flow tick only fires on active
    //     flow). Same {@link #freezesFlowing} decision, aboveFreezeFloor true by construction (above the surface).

    /**
     * S17(b) UPWARD-SCAN BOUND (blocks above the MOTION_BLOCKING heightmap top). The tickPrecipitation-driven
     * pass scans a belt of this height UPWARD from the column's surface, freezing any FLOWING water it finds
     * (the standing fall columns that free-fell ABOVE the heightmap and so are unreachable by the S16 roofed
     * DESCENT). Bounded to keep the per-tick cost O(1) and small: 24 = a generous open cascade drop (1.5 chunk
     * sections), tall enough to claim a typical multi-block fall in one tick while a taller fall is finished off
     * over subsequent ticks (each frozen block blocks the water above it, so the fall dies from the bottom up as
     * the belt re-scans). The scan stops early at the first solid (non-fluid) block, so it usually pays far less
     * than the cap; every block in the belt is above the surface, hence far above the surface-40 freeze floor,
     * so the frozen ice is always ABOVE the B-9 deep-cave reservoir.
     */
    public static final int WATERFALL_UPWARD_SCAN_BLOCKS = 24;

    // --- S18/S19 (history) -> S20 SETTLED-WATER FREEZE (Peetsa 2026-07-19, TEST 110: the THIRD failed water
    // --- round -> the LIVE-INSTRUMENT LAW) -----------------------------------------------------------------
    // THE ROOT MECHANISM (finally understood, recon + owner verified): a FLOWING water block only receives fluid
    // TICKS while it is actively SPREADING. Once the fluid reaches equilibrium (fully spread) the game STOPS
    // scheduling its tick, so any tick-hosted freeze CHANCE stops rolling. S18/S19 hosted the whole ground/pool
    // freeze on the flow tick with a per-tick chance (S19: 0.2 on solid); most blocks SETTLED before a roll ever
    // landed and then never ticked again -> "the water is not freezing" (TEST 108/109/110, live).
    //
    // S20 THE DIVISION OF LABOUR (the owner's spec: water spreads fully; once SETTLED, it converts). Three
    // consumers, each owning one lifecycle stage, NONE using any randomness at all:
    //   (A) THE SETTLED SWEEP (ServerLevelRoofedWaterFreezeMixin, on tickPrecipitation -- the weather tick).
    //       The PRIMARY ground/pool freezer. Per chunk-tick it sweeps a column belt (heightmap top +24 up /
    //       roof descent 16 down) and freezes SETTLED + LANDED flowing blocks -- CERTAIN, no dice -- bottom-up,
    //       one-or-two per column per pass, so a pool freezes over seconds ring by ring. "SETTLED" is the game's
    //       OWN at-rest definition: the fluid block has NO pending scheduled fluid tick (read at the call site
    //       from {@code level.getFluidTicks().hasScheduledTick}). Still-SPREADING water (a pending tick) is NEVER
    //       swept -- the sweep only ever claims water that has come to rest. Decision: {@link #sweepFreezesSettled}.
    //   (B) THE ICE-TOUCH HUNTER (FlowingFluidWaterfallFreezeMixin, on FlowingFluid.tick -- the flow tick).
    //       After S20 the flow tick's ONLY freeze job. A LANDED flowing block that is TOUCHING ICE converts to
    //       ice -- CERTAIN, no dice. The on-solid CHANCE is GONE (the settled sweep owns ground freezing), so
    //       there is NO RNG in the flow path at all. This clause exists only to LOCK reroutes and drive the
    //       vertical zipper the instant water contacts existing ice, so the spread can never outrun the freeze:
    //       once any base ice exists, the block resting on it is touching-ice -> certain -> a deterministic
    //       ~4 blocks/s vertical zipper, and a horizontal reroute onto/beside fresh ice locks one block out (the
    //       ice HUNTS the escaping water; the speckle heals). Decision: {@link #flowTickHunterFreezes}.
    //   (C) FALLS RUN FREE (same flow-tick mixin): a flowing block still FALLING (air/fluid below -- NOT landed)
    //       passes to vanilla flow untouched, so the fall reaches the ground live before anything freezes (the
    //       S18 law, unchanged). Source water at the top of a fall (Fluids.WATER) is left to the surface freeze
    //       (BiomePolarWaterFreezeMixin via tickPrecipitation), unchanged.
    // TOUCH SET: the block BELOW plus the 4 horizontal neighbours (ABOVE excluded -- water's source direction).
    // Gen-time falls still arrive frozen (S14, unchanged); these laws govern LIVE water only. All exemptions carry
    // over verbatim (WATER-only, ocean-family FIRST, deep-cave freeze floor, flag-off/out-of-zone byte-identical)
    // -- and S20 removes the last RNG: NO random is drawn anywhere in the freeze paths now (both freeze decisions
    // are certain booleans).

    /**
     * S18 LANDED predicate (pure): is a flowing block SUPPORTED below -- i.e. motion-terminated, resting on
     * solid or ice -- rather than still falling? Supported iff the block below is NOT air AND NOT a fluid
     * (water, lava, or any other): "the fall cannot continue and it is not sitting on more water." Ice below
     * counts as support (this is exactly what makes the freeze CLIMB: each frozen block becomes the floor the
     * block above lands on). A base block spreading HORIZONTALLY counts too -- its below is solid. Air-below or
     * fluid-below = still falling / pouring onto water -> NOT landed, so the caller passes it to vanilla flow.
     *
     * @param belowIsAir   the block directly below is air (the fall continues) -> not landed.
     * @param belowIsFluid the block directly below has a non-empty fluid state (water/lava/etc.; sitting on
     *                     more water) -> not landed.
     */
    public static boolean landedOnSupport(boolean belowIsAir, boolean belowIsFluid) {
        return !belowIsAir && !belowIsFluid;
    }

    /**
     * S19 TOUCH SET (pure): is a flowing block TOUCHING ICE? The touch set is the block BELOW plus the FOUR
     * HORIZONTAL neighbours (N/E/S/W) -- FIVE positions. The block ABOVE is deliberately EXCLUDED: water flows
     * DOWN and OUT, so ice above is the water's SOURCE direction, not a landing surface or a reroute contact --
     * counting it would freeze still-falling water beneath a frozen cap and defeat the "water falls freely" law.
     * BELOW-ice is the vertical-climb contact (a block resting on the frozen base); a HORIZONTAL-neighbour ice is
     * the reroute-hunter contact (water spreading around a fresh ice patch). Any single touch -> certain freeze
     * (the S20 {@link #flowTickHunterFreezes} clause). The caller decides what counts as "ice-family" from the
     * live block states (this pure form takes the five booleans so it stays Minecraft-free and unit-testable).
     *
     * @param belowIsIce the block directly below is ice-family (the vertical-climb support).
     * @param northIsIce a horizontal neighbour is ice-family (a reroute-hunter contact) -- any true -> touching.
     */
    public static boolean touchingIce(boolean belowIsIce, boolean northIsIce, boolean eastIsIce,
                                      boolean southIsIce, boolean westIsIce) {
        return belowIsIce || northIsIce || eastIsIce || southIsIce || westIsIce;
    }

    /**
     * S20 the FLOW-TICK ICE-TOUCH HUNTER decision (pure, CERTAIN -- no RNG). Consumer (B) in the S20 division of
     * labour: after S20 this is the flow tick's ONLY freeze job. A flowing block converts to ice on its flow tick
     * iff it is freeze-eligible ({@link #freezesFlowing} -- ocean-exempt, in-zone, above the floor, flag on) AND
     * LANDED ({@link #landedOnSupport}) AND TOUCHING ICE ({@link #touchingIce}). The S18/S19 on-solid CHANCE is
     * GONE (the settled sweep, consumer (A), now owns all ground/pool freezing), so a landed block on ORDINARY
     * support is left to keep spreading and is later claimed by the sweep once it SETTLES -- there is no roll and
     * no RNG in the flow path at all. This clause exists purely to LOCK reroutes and drive the vertical zipper the
     * instant water contacts existing ice: once any base ice exists, the block resting on it is touching-ice ->
     * certain -> a deterministic ~4 blocks/s climb, and a horizontal reroute onto/beside fresh ice locks one block
     * out (the ice hunts the escaping water; the speckle heals). A still-falling, ineligible, or
     * not-touching-ice block never freezes here.
     *
     * @param freezeEligible  {@link #freezesFlowing} for this column/position (the WHERE decision).
     * @param landedOnSupport {@link #landedOnSupport} for this block (the WHEN / still-falling decision).
     * @param touchingIce     {@link #touchingIce} for this block (the reroute/zipper contact).
     */
    public static boolean flowTickHunterFreezes(boolean freezeEligible, boolean landedOnSupport,
                                                boolean touchingIce) {
        return freezeEligible && landedOnSupport && touchingIce;
    }

    /**
     * S20 the SETTLED-SWEEP freeze decision (pure, CERTAIN -- no RNG). Consumer (A) in the S20 division of
     * labour, and the PRIMARY ground/pool freezer: the weather-tick sweep converts a flowing block to ice iff it
     * is freeze-eligible ({@link #freezesFlowing}) AND LANDED ({@link #landedOnSupport}) AND SETTLED. "SETTLED" is
     * the game's own at-rest definition -- the fluid block has NO pending scheduled fluid tick, i.e. it stopped
     * spreading -- read at the call site from {@code level.getFluidTicks().hasScheduledTick(pos, fluid)} (see
     * {@code ServerLevelRoofedWaterFreezeMixin}; the 26.2 tick container matches the scheduled fluid type by
     * reference identity, so the caller checks BOTH water keys -- {@code FLOWING_WATER} from
     * {@code FlowingFluid.spreadTo} and {@code WATER} from {@code LiquidBlock.onPlace}). Still-SPREADING water (a
     * pending tick under either key) is NEVER swept, so the sweep only ever claims water that has come to rest --
     * a pool freezes over seconds, ring by ring, as its outer cells settle. Certain, so the caller draws no random.
     *
     * @param freezeEligible  {@link #freezesFlowing} for this column/position (the WHERE decision).
     * @param landedOnSupport {@link #landedOnSupport} for this block (rests on solid or ice, not falling).
     * @param settled         true iff the fluid block has NO pending scheduled fluid tick (the at-rest gate).
     */
    public static boolean sweepFreezesSettled(boolean freezeEligible, boolean landedOnSupport, boolean settled) {
        return freezeEligible && landedOnSupport && settled;
    }
}
