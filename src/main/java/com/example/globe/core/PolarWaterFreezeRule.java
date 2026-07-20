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
    // v3 added the FLOW-TICK seam (FlowingFluidWaterfallFreezeMixin on FlowingFluid.tick -- the single method
    // every moving water block funnels through: in-zone, above the floor, non-ocean, a FLOWING block is a freeze
    // candidate at the moment of motion; decision {@link #freezesFlowing}) plus an "upward scan" companion.
    //
    // v6 CORRECTION (TEST 113 forensics, 2026-07-19, bytecode-verified): the S17 "upward scan" was DEAD CODE and
    // has been DELETED (its WATERFALL_UPWARD_SCAN_BLOCKS bound with it). S17's premise -- "water is not
    // motion-blocking terrain, so a cascade free-falls ABOVE the MOTION_BLOCKING heightmap top" -- was FALSE:
    // the 26.2 MOTION_BLOCKING heightmap predicate is javap-verified as
    // {@code blocksMotion() || !getFluidState().isEmpty()}, i.e. FLUIDS COUNT, so getHeightmapPos(MOTION_BLOCKING)
    // sits exactly ONE block above the topmost water (cascade or pool alike). A scan that starts one ABOVE that
    // top therefore starts TWO above the topmost water and walks pure air -- it never froze a block in any
    // flight. The standing-cascade job the scan claimed is really the DOWNWARD descent's (the surface IS the
    // cascade top); the v6 sweep in ServerLevelRoofedWaterFreezeMixin descends the flowing column to its landed
    // base instead (see {@link #FLOWING_DESCENT_CAP_BLOCKS}).

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
    //   (A) THE SETTLED SWEEP (ServerLevelRoofedWaterFreezeMixin; v6 re-hosted it from tickPrecipitation onto a
    //       deterministic ServerLevel.tickChunk round-robin driver -- see the v6 section below for the cadence
    //       math). The PRIMARY ground/pool freezer. Per visit it descends the column (roof reach 16, extended
    //       down a flowing column to its landed base) and freezes SETTLED + LANDED flowing blocks -- CERTAIN,
    //       no dice -- bottom-up, so a pool freezes ring by ring. "SETTLED" is the game's
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
     * labour, and the PRIMARY ground/pool freezer: the sweep (v6: driven from the deterministic
     * {@code ServerLevel.tickChunk} round-robin) converts a flowing block to ice iff it
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

    // --- S21(d) WATER v5 (Peetsa 2026-07-19, TEST 111: "still ~5x too slow", and the source froze FIRST and
    // --- beheaded the fall) -------------------------------------------------------------------------------
    // Two pacing/ordering fixes on top of the S20 division of labour; NO new RNG, all exemptions verbatim.

    /**
     * S21(d) SWEEP PACING CAP: the maximum number of SETTLED + LANDED flowing blocks the settled sweep
     * ({@code ServerLevelRoofedWaterFreezeMixin}, consumer (A)) claims for ONE column in ONE weather-tick pass.
     * Raised from the S20 effective ~1-2 to <b>8</b> -- the owner's "SWEEP 5x" (TEST 111: "still ~5x too slow").
     *
     * <p><b>The pacing math (~5x).</b> The sweep freezes a contiguous BOTTOM-UP RUN of at-rest flowing blocks per
     * column per sweep visit (v6: a visit is a deterministic tickChunk round-robin slot, every
     * {@link #SWEEP_FULL_COVERAGE_TICKS} ticks per column -- no longer a rare random tickPrecipitation column).
     * Prior: effectively ~1-2 blocks/column/pass. Now up to 8, so a
     * standing fall / deep flowing column of freezable depth {@code D} ices in {@code ceil(D/8)} sweep passes
     * instead of {@code ceil(D/~1.6)}: D=16 -> 2 passes (was ~10), D=40 (the glacier-sole floor) -> 5 passes
     * (was ~25) = ~5x fewer sweep visits to full ice for deep columns. A single-layer sheet
     * (D=1) is unchanged -- the cap never binds there; its pace is the per-column visit cadence (v6: fixed at
     * one visit per {@link #SWEEP_FULL_COVERAGE_TICKS} ticks by the deterministic driver). The
     * S20 ICE-TOUCH HUNTER's ~4 blocks/s vertical zipper is unchanged; the 8-cap accelerates the base-laying and
     * the conversion of already-SETTLED standing columns that the hunter (which only fires on active flow ticks)
     * cannot climb on its own. Still bottom-up, still CERTAIN (no dice), still "stop at the first still-falling /
     * still-spreading block" -- the run terminator; only the per-pass yield changed.
     */
    public static final int SWEEP_MAX_PER_COLUMN = 8;

    /**
     * S21(d) SOURCE-FREEZES-LAST decision (pure): should a SOURCE water block POSTPONE its surface freeze because
     * it still touches LIVE flowing water? The owner watched the still-water surface freeze claim his SOURCE
     * FIRST, "beheading" a running waterfall so the body below (cut off from its supply) could never finish
     * freezing. The fix: a source with ANY adjacent live flowing water REFUSES to freeze this pass; it freezes
     * only once its connected flow has all become ice (or drained), i.e. once no adjacent flowing water remains.
     *
     * <p>The consumer ({@code BiomePolarWaterFreezeMixin}, which feeds every {@code Biome.shouldFreeze} source
     * freeze -- ongoing {@code tickPrecipitation} AND the roofed descent's source branch) reads the SIX neighbours
     * -- the block BELOW, the four HORIZONTALS, and ABOVE -- and passes {@code true} iff any is live
     * {@code FLOWING_WATER}. <b>ABOVE is INCLUDED here</b> (unlike the flow-tick {@link #touchingIce} set, which
     * excludes it): a source sitting directly UNDER a live fall is exactly the beheading case -- freezing it caps
     * the fall from below -- so it too must wait. The predicate is a pure pass-through so the "connected flow is
     * fully ice" semantics are EMERGENT (no adjacent flowing left -> not postponed -> the source freezes, last),
     * keeping the shim's neighbour scan the only Minecraft-touching part. Because the flow itself is frozen by the
     * SETTLED SWEEP and the ICE-TOUCH HUNTER (which never depend on the source freezing), a stable fall settles and
     * ices bottom-up, then this postpone lifts -- no deadlock. Accepted edge: a source whose only flowing neighbour
     * is permanently NON-eligible (e.g. flowing sea at a shoreline, or a below-floor B-9 spring) stays liquid -- a
     * 1-block natural water edge, not ice, which reads correctly.
     *
     * @param adjacentHasFlowing true iff at least one of the source's six neighbours is live flowing water.
     * @return true iff the source's freeze must be postponed this pass (there is still live flow to outlive).
     */
    public static boolean sourceFreezePostponed(boolean adjacentHasFlowing) {
        return adjacentHasFlowing;
    }

    // --- S22 WATER v6 (Peetsa 2026-07-19, TEST 113: the FOURTH live water failure) -----------------------
    // Owner flight: an 84S pour froze NOTHING (outside the 85 front while the Barrens/crevasses start at 82);
    // at exactly 85S the pour flooded tens of blocks in 12 s, reached only 20-30% checkerboard ice at 36 s,
    // and the supply kept re-spreading fresh water ON TOP of fresh ice. Forensics (bytecode-verified) found
    // FOUR stacked causes; v6 is their four prescribed fixes, all TICK-TIME-ONLY (worldgen byte-identity; the
    // 85 {@link #FREEZE_ALL_DEG} law stays untouched for every worldgen-facing path) and RNG-free:
    //   (a) COVERAGE: every tick consumer bailed below 84 (FREEZE_ALL-FRAY) while the Barrens band -- and the
    //       B-9 crevasses that expose water -- start at PolarBarrensBand.ONSET_DEG (82). The 82-84 band had NO
    //       freeze machinery at all. Fix: the TICK FRONT below -- the tick consumers now ride the SAME shared
    //       barrens band decision (ONSET 82 -> FULL 84, the barrens fray noise; one decision, Art VI) the
    //       biome placement / glacier body / crevasse carvers already use.
    //   (a2) DEAD SCAN: the S17 upward scan never ran (MOTION_BLOCKING counts fluids -- see the v6 correction
    //       at the S17 section) and the roofed descent capped at 16 and froze only landed blocks, so falls
    //       taller than 16 were unreachable. Fix: scan deleted; descent extended ({@link #FLOWING_DESCENT_CAP_BLOCKS}).
    //   (b) CADENCE: the sweep rode tickPrecipitation, which vanilla calls with probability randomTickSpeed/48
    //       (= 1/16 per chunk-tick at default 3) on ONE random column of 256 -- a SPECIFIC column is visited
    //       every ~205 s on average, while spread runs ~4 cells/s per front cell. Orders of magnitude behind.
    //       Fix: the deterministic round-robin driver below (every column visited every 32 ticks, no RNG).
    //   (c) NO SPREAD-STOPPER: the old coverage ARGUMENT ("the hunter catches every spreading edge cell") had
    //       three real holes -- not-landed-over-water blocks are invisible to both consumers; every freeze's
    //       neighbour updates schedule fresh fluid ticks so the supply re-spreads a NEW layer ON TOP of fresh
    //       ice (the ratchet); and source-freezes-last + the ratchet keeps the supply alive indefinitely.
    //       Fix: CONVERT-AT-SPREAD ({@link #spreadConvertsToIce} + FlowingFluidSpreadConvertMixin) -- ice at
    //       the destination instead of water, which ADVANCES the freeze and terminates the path (denial would
    //       leave the supply recomputing forever).

    /**
     * S22 the TICK FRONT's equatorward-most possible onset (deg) -- {@link PolarBarrensBand#ONSET_DEG} (82),
     * KEEP-SHARED with the Barrens band so the freeze machinery switches on exactly where the barrens (and the
     * B-9 crevasses that expose water) begin. This is the tick consumers' CHEAP pre-gate: below it no noise is
     * ever sampled and vanilla runs untouched, byte-identical. It deliberately does NOT touch
     * {@link #FREEZE_ALL_DEG} (85), which remains the worldgen-facing sea-ice/solid-freeze anchor.
     */
    public static final double TICK_FRONT_ONSET_DEG = PolarBarrensBand.ONSET_DEG;

    /**
     * S22 TICK FRONT decision (pure): is this column inside the tick-time freeze front? TRUE iff the barrens
     * family flag is on AND the column lands on the barrens side of the SAME shared band decision
     * ({@link PolarBarrensBand#isBarrens}: smoothstep ONSET 82 -> FULL 84 against the coherent barrens fray
     * noise) that places the {@code globe:polar_barrens} biome, builds the glacier body, and carves the B-9
     * crevasses. ONE decision (Art VI): wherever barrens ground/crevasse country exists, the freeze machinery
     * is live -- the 82-84 band the owner flew over with a liquid pour is now covered by construction, and at/
     * above FULL_DEG (84) the front is unconditionally on (fraction 1.0), so the whole old >= 85 zone remains
     * covered. TICK-TIME-ONLY by contract: no worldgen path may call this (they keep the 85 law); the
     * consumers gate it on {@code level instanceof ServerLevel}.
     *
     * <p>NaN handling follows the family idiom: NaN latitude -> false ({@code isBarrens}'s fraction is 0.0 on
     * bad data -- never freeze on bad data); a NaN noise sample degrades to 0.5 (the band's median -- at/above
     * FULL_DEG the front stays unconditionally on, never a hole in deep-cap coverage on bad data).
     *
     * @param flagOn            {@code latitude.polarBarrens.enabled} (the shared worldgen-family flag; off is
     *                          the untouched pre-v6 tick behaviour for the 82-84 band).
     * @param latDeg            signed OR absolute column latitude; magnitude taken inside {@code isBarrens}.
     * @param barrensFrayNoise01 the SAME coherent barrens fray sample ({@code LatitudeBiomes
     *                          .polarBarrensFrayNoise}) the biome/glacier/carvers consume. Callers may skip
     *                          the sample at/above {@link PolarBarrensBand#FULL_DEG} (fraction 1.0 -- any
     *                          value passes) and feed any constant.
     */
    public static boolean tickFrontFreezes(boolean flagOn, double latDeg, double barrensFrayNoise01) {
        if (!flagOn) {
            return false;
        }
        double n = Double.isNaN(barrensFrayNoise01) ? 0.5 : barrensFrayNoise01;
        return PolarBarrensBand.isBarrens(latDeg, n);
    }

    /**
     * S22 tick-time flowing-water eligibility -- the v6 WHERE decision for the flow-tick hunter, the settled
     * sweep, and the spread-converter. The tick-time analogue of {@link #freezesFlowing} with the WHERE swapped
     * from the worldgen 85-frayed front to the {@link #tickFrontFreezes TICK FRONT} (82->84 barrens band); the
     * sacred exemptions carry over verbatim and in the same order: <b>ocean FIRST</b> (the sea keeps
     * liquid-under-ice -- under-ice swim, pack-ice wall, S7 immersion), then the flag, then the front, then the
     * freeze floor ({@code aboveFreezeFloor} -- the B-9 deep-cave reservoir below the glacier sole stays
     * liquid). For a column at/above 85 this is a superset of the old behaviour (the front is unconditionally
     * on there); for 82-84 it is the NEW coverage. Pure function of its inputs, no RNG.
     *
     * @param aboveFreezeFloor the candidate block sits above {@link #landWaterFreezeFloorY} for its column.
     */
    public static boolean tickFreezesFlowing(boolean flagOn, boolean isOceanColumn, double latDeg,
                                             double barrensFrayNoise01, boolean aboveFreezeFloor) {
        if (isOceanColumn) {
            return false; // THE SEA IS EXEMPT -- ocean-wins, FIRST in the chain (same order as freezesLandWaterSolid)
        }
        if (!tickFrontFreezes(flagOn, latDeg, barrensFrayNoise01)) {
            return false;
        }
        return aboveFreezeFloor;
    }

    /**
     * S22(a2) FLOWING-COLUMN DESCENT CAP (blocks below the MOTION_BLOCKING top). The v6 sweep starts at the
     * column surface (which, fluids counting, IS the cascade top) and -- once it has found flowing water within
     * the ordinary {@link #ROOFED_FREEZE_REACH_BLOCKS} (16) roof reach -- keeps descending WHILE the blocks are
     * flowing water, down to the fall's LANDED BASE, capped at this many blocks total. 48 replaces the old
     * 16-block wall that made falls taller than 16 unreachable (the TEST-113 (a2) finding): it covers a
     * three-chunk-section drop (a very tall natural cascade) while keeping the per-visit worst case bounded.
     * The FREEZE floor is unchanged -- blocks at/below {@link #landWaterFreezeFloorY} are never frozen however
     * deep the descent walks (the walk may pass the floor; the freeze may not), so the B-9 reservoir holds.
     */
    public static final int FLOWING_DESCENT_CAP_BLOCKS = 48;

    // --- S22(b) THE DETERMINISTIC SWEEP DRIVER (ServerLevel.tickChunk round-robin) ----------------------
    // The sweep no longer rides tickPrecipitation's coin flips. ServerLevelRoofedWaterFreezeMixin injects at
    // ServerLevel.tickChunk (javap-verified: public void tickChunk(LevelChunk, int)), bails on out-of-band
    // chunks with one cheap latitude compare, then sweeps K columns per chunk per tick, chosen round-robin by
    // (gameTime*K + i) mod 256 -- pure tick arithmetic, NO RNG, full 16x16 coverage every 256/K ticks. Cost is
    // bounded and flat: K column visits per in-band chunk per tick, each a heightmap read + a short descent.

    /** S22(b) K: columns swept per in-band chunk per tick. 8 -> with 256 columns per chunk, every column is
     *  visited exactly once every {@link #SWEEP_FULL_COVERAGE_TICKS} (32) ticks = 1.6 s -- ~128x the old
     *  ~205 s/column random cadence, comfortably ahead of the ~4 cells/s spread front, while costing only 8
     *  bounded column visits per chunk-tick. */
    public static final int SWEEP_COLUMNS_PER_CHUNK_TICK = 8;

    /** Columns in a chunk layer (16 x 16) -- the round-robin modulus. */
    public static final int SWEEP_COLUMN_COUNT = 256;

    /** S22(b) full-coverage period: every column of an in-band chunk is visited once per this many ticks
     *  (256 / 8 = 32 ticks = 1.6 s at 20 tps). Deterministic -- a consequence of the round-robin index math,
     *  pinned by {@code sweepColumnIndex}'s coverage test. */
    public static final int SWEEP_FULL_COVERAGE_TICKS = SWEEP_COLUMN_COUNT / SWEEP_COLUMNS_PER_CHUNK_TICK;

    /**
     * S22(b) the round-robin column selector (pure): the packed column index in {@code [0, 256)} for slot
     * {@code i} of this tick's K-column sweep -- {@code (gameTime*K + i) mod 256}. Unpack with
     * {@code x = idx & 15, z = idx >> 4}. Because K divides 256, consecutive ticks tile the index space with
     * no overlap and no gap: all 256 columns are hit exactly once every {@link #SWEEP_FULL_COVERAGE_TICKS}
     * ticks, for ANY gameTime origin (floorMod handles negative/overflowing gameTime arithmetic). Pure tick
     * math -- no RNG, save/load-stable, identical on every chunk (chunk-local column index; the world-space
     * column differs per chunk).
     *
     * @param gameTime {@code ServerLevel.getGameTime()} (a tick counter).
     * @param i        slot in {@code [0, }{@link #SWEEP_COLUMNS_PER_CHUNK_TICK}{@code )}.
     */
    public static int sweepColumnIndex(long gameTime, int i) {
        return (int) Math.floorMod(gameTime * SWEEP_COLUMNS_PER_CHUNK_TICK + i, (long) SWEEP_COLUMN_COUNT);
    }

    /**
     * S22(c) CONVERT-AT-SPREAD decision (pure, CERTAIN -- no RNG): should a water spread INTO {@code dest} be
     * converted to plain ICE at the destination instead of placing water? This is the spread-STOPPER the three
     * TEST-113 ratchet holes demanded -- and it CONVERTS rather than denies: denial leaves the supply
     * recomputing the same spread forever, conversion advances the freeze one cell and TERMINATES the path
     * (the fluid engine sees a solid neighbour and stops scheduling). Consumed by
     * {@code FlowingFluidSpreadConvertMixin} at {@code FlowingFluid.spreadTo} (javap-verified:
     * {@code protected void spreadTo(LevelAccessor, BlockPos, BlockState, Direction, FluidState)}).
     *
     * <p>Clauses, in order:
     * <ul>
     *   <li><b>Eligibility</b> ({@code freezeEligible} = {@link #tickFreezesFlowing} at the DESTINATION):
     *       carries the whole sacred set -- ocean-family exempt (checked first inside it), tick front (the
     *       82->84 barrens band), barrens flag, freeze floor (B-9 reservoir). Not eligible -> water spreads
     *       exactly as vanilla.</li>
     *   <li><b>FALLS RUN FREE</b> (the S18 law, preserved at the spread seam): a STRAIGHT-DOWN spread
     *       ({@code spreadIsDown}) converts ONLY when the block below the destination is already ice
     *       ({@code belowIsIce} -- the fall is landing ON the frozen pile: the zipper's terminal contact, one
     *       tick earlier than the hunter would catch it). A fall dropping past an ice wall into open air stays
     *       WATER -- the cascade reaches the ground live, never beheaded mid-air.</li>
     *   <li><b>Ice-adjacency</b> for horizontal spread: the destination must be TOUCHING ICE per the existing
     *       hunter touch set ({@link #touchingIce}: BELOW + the 4 horizontals, ABOVE excluded). This is the
     *       ratchet-breaker: the re-spread layer that used to ride ON TOP of fresh ice (below-ice contact) or
     *       reroute AROUND it (horizontal contact) now becomes ice AT the moment of spread, before the water
     *       ever exists to schedule ticks.</li>
     * </ul>
     * Source-freezes-last is untouched by construction: only a SPREAD (always a flowing destination) converts;
     * source blocks are never a spread destination, and starving a source's outflow is exactly how it comes to
     * freeze LAST via {@link #sourceFreezePostponed}. Pure function of blockstate-derived booleans, tick-only.
     *
     * @param freezeEligible {@link #tickFreezesFlowing} evaluated at the spread DESTINATION.
     * @param spreadIsDown   the spread direction is straight DOWN (a fall extending), not horizontal.
     * @param belowIsIce     the block directly below the destination is ice-family ({@code BlockTags.ICE}).
     * @param touchingIce    {@link #touchingIce} over the destination's below + 4 horizontal neighbours.
     */
    public static boolean spreadConvertsToIce(boolean freezeEligible, boolean spreadIsDown,
                                              boolean belowIsIce, boolean touchingIce) {
        if (!freezeEligible) {
            return false;
        }
        if (spreadIsDown) {
            return belowIsIce; // falls run free: mid-air past an ice wall stays water; landing ON ice locks
        }
        return touchingIce;
    }
}
