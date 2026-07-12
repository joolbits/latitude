package com.example.globe.core;

/**
 * Feature flags gating the Phase 2 (GeoAuthority), Phase 3 (ClimateAuthority), and Biome Consumer
 * slices of the Latitude 2.0 overhaul. All default to {@code false}.
 *
 * <p>{@link #GEO_V2_ENABLED} / {@link #CLIMATE_V2_ENABLED} gate whether each authority is
 * constructed and sampled at all (Phase 2/3: computed-and-discarded). {@link #BIOME_CONSUMER_V2_ENABLED}
 * is a separate, later flag: it gates whether the computed summaries actually CHANGE biome selection.
 * Kept distinct on purpose -- the geoV2/climateV2 flags have an established, tested contract from
 * Phases 2-3 ("computed and discarded, zero biome change"); overloading them to also mean "and drives
 * biomes" would silently break that contract. The consumer flag depends on its authority's flag also
 * being on (if geoV2 is off, there is no GeoSummary to consume no matter what this flag says).
 *
 * <p>{@link #BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED} is a further, independently-gated sub-flag
 * (2026-07-04). The Biome Consumer slice's proof gate found that letting GeoAuthority's
 * {@code isOceanIntent} replace {@code OceanDistanceField} collapses live land fraction from
 * GeoAuthority's own calibrated ~39% to ~13% (see docs/binder/biome-consumer-slice-20260704.md) --
 * a terrain-integration gap (Phase 4), not a GeoAuthority miscalibration. Rather than gate that known-bad
 * behavior behind a code comment saying "don't flip this yet," it gets its OWN flag: with
 * {@code BIOME_CONSUMER_V2_ENABLED=true} alone, only the proven-safe ClimateAuthority land-family reroll
 * is active; the ocean-authority swap additionally requires this flag, so it cannot be enabled by
 * accident and stays clearly walled off until Phase 4 (or an explicit decision to revisit the ocean
 * composition logic) makes it safe to turn on by default.
 *
 * <p>Pure Java, no Minecraft imports -- this class belongs to the Core Logic layer per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 *
 * <p><b>Testing note (sweeper audit #2 finding #27, 2026-07-05):</b> every flag below is
 * {@code static final}, read from {@code System.getProperty} exactly once at class-init -- correct
 * and zero-cost for the shipping default-off path (a real JVM {@code -D} at launch), but it means
 * {@code System.setProperty(...)} called AFTER this class has already loaded has no effect. A test
 * that sets a property post-load and expects the flag to flip will silently keep exercising the
 * flag-off path and can pass for the wrong reason -- exactly the failure class this sweep targets.
 * Flag-on proof/tests must set the property before this class is first touched (e.g. via a forked JVM
 * with {@code -D}, as the headless atlas runner does), not via {@code setProperty} mid-test.
 */
public final class LatitudeV2Flags {

    public static final boolean GEO_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.geoV2.enabled", "false"));

    public static final boolean CLIMATE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.climateV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.oceanAuthority.enabled", "false"));

    // --- Phase 4 (Terrain Integration Spike) flags -------------------------------------------------
    // These gate the one narrow density-function wrapper (GeoTerrainBiasFunction) that biases terrain
    // surface height toward GeoAuthority's continuous land/ocean field. Design:
    // docs/design/terrain-wrapper-design-20260705.md (locked r2). All default to the true no-op:
    // ENABLED=false means the wrapper is never installed (byte-identical flag-off); STRENGTH=0.0 is a
    // second belt-and-suspenders no-op (biased == base); OCEAN_STRENGTH_RATIO=1.0 is the symmetric form.
    //
    // The doubles are parsed defensively: a malformed -D degrades to the no-op default rather than
    // throwing at class-init (a class-init failure here would take down all worldgen). See design §3.

    /** Phase 4 install gate. Default false -> the terrain-bias wrapper is never installed. */
    public static final boolean TERRAIN_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.terrainV2.enabled", "false"));

    /** Phase 4 primary strength knob (the live-tuning knob). Default 0.0 -> installed-but-no-op. */
    public static final double TERRAIN_V2_STRENGTH =
            parseDoubleOrDefault(System.getProperty("latitude.terrainV2.strength"), 0.0);

    /** Phase 4 optional ocean-side asymmetry knob. Default 1.0 -> symmetric (land push == ocean push). */
    public static final double TERRAIN_V2_OCEAN_STRENGTH_RATIO =
            parseDoubleOrDefault(System.getProperty("latitude.terrainV2.oceanStrengthRatio"), 1.0);

    /**
     * Slice C-3 "grade the grip" (TEST 29 live wall): the land01-halfwidth over which the ocean carve
     * takes hold beyond the coastline. The carve's DEPTH was always distance-graded, but its GRIP was
     * instant at the coastline contour, planing tall old-map hills into sheer walls. Full carve applies
     * from |d| >= this value (d = 2*land01-1). 0 disables the ramp (legacy instant grip). Default 0.8:
     * the wall-transect calibration showed 0.4 maps to only ~50 blocks on steep coastline gradients
     * (still cliffy); 0.8 spreads the descent across the land01 0.5->0.1 band (~100-150 blocks there)
     * while land01 <= 0.1 still carves at full strength, so open ocean is untouched. Live-tunable like
     * the other terrainV2 knobs.
     */
    public static final double TERRAIN_V2_GRIP_WIDTH =
            parseDoubleOrDefault(System.getProperty("latitude.terrainV2.gripWidth"), 0.8);

    /**
     * Phase 5 Slice B-2 (Fix 2) sub-flag: floor-sight the live sunk-land mirror veto. Default false.
     * <p>The mirror veto's cheap ({@code skipPreview}, live MIXIN) branch currently reads the
     * fluid-inclusive {@code columnDecisionY} ({@code WORLD_SURFACE_WG}), so a correctly-flooded carved
     * column reads the waterline (63) and the veto never fires -- live shows ocean water tagged
     * savanna/jungle (wrong identity). When this flag is on, that branch instead uses an
     * {@code OCEAN_FLOOR_WG}-based floor estimate (the same source {@code previewFloorHeight} already
     * trusts in the harness {@code !skipPreview} branch), completing C-2's documented intent live.
     * <p>Its own sub-flag (not folded into {@code TERRAIN_V2_ENABLED}) because it is honestly a MAP-WIDE
     * change to the current live config (~30% of sampled columns flip to their C-2-intended ocean
     * identity, with chunk-boundary discontinuities in existing worlds), so it must be independently
     * switchable at B-4 (B-1 amendment 3). Flag-off is byte-identical: the mirror veto only runs while
     * {@code terrainBiasActivelyBiasing()}, and with this off the branch is the unchanged
     * {@code columnDecisionY}.
     */
    public static final boolean TERRAIN_V2_FLOOR_SIGHTED_VETO =
            Boolean.parseBoolean(System.getProperty("latitude.terrainV2.floorSightedVeto", "false"));

    /**
     * Phase 5 carve-aware ocean labels (ocean-label investigation 2026-07-09). Default false.
     * <p>Replaces estimator-based "is this column carved to sea?" reads with the carve's OWN pure
     * analytic target ({@code GeoTerrainBiasFunction.carveTargetYOrMax(x,z)}): a column is labeled
     * ocean iff {@code carveTargetY < seaLevel - 2}. Because the oracle needs no generator, no
     * noiseConfig and no heightmap, it works in EVERY {@code pick()} context — including the
     * input-less "SOURCE" path that vanilla structure eligibility reads, which both existing
     * sunk-land vetoes (and {@link #TERRAIN_V2_FLOOR_SIGHTED_VETO}) structurally cannot reach.
     * Gates three consumers behind this ONE flag: (1) the carve-aware ocean relabel in both
     * {@code pick()} twins (villages stop being eligible over carved sea; sunk rivers convert to the
     * latitude-correct ocean family); (2) the surface-cave clamp judges "near surface" against
     * {@code min(WORLD_SURFACE_WG, carveTarget)} so trench-floor dripstone gets clamped like any
     * near-surface cave exposure; (3) belt+suspenders — {@code StructureBiomeMatchGuardMixin} also
     * cancels clearly-land-only structure starts over carved sea.
     * <p>Independent of {@link #TERRAIN_V2_FLOOR_SIGHTED_VETO} (which stays untouched; in practice
     * this flag supersedes it — the OCEAN_FLOOR_WG estimator over-floods, see the phase 5 plan's
     * OPEN FINDING). Flag-off is byte-identical: every consumer additionally requires
     * {@code terrainBiasActivelyBiasing()}, and the oracle returns {@code +Infinity} whenever no
     * carve applies (S==0, r==0, NoOp provider, land-intent) so it can never relabel unbias-able
     * terrain.
     */
    public static final boolean TERRAIN_V2_CARVE_AWARE_LABELS =
            Boolean.parseBoolean(System.getProperty("latitude.terrainV2.carveAwareLabels", "false"));

    /**
     * Phase 5 Slice B-2 (Fix 1) gate: latitude-aware EDGE OCEAN intent at the projection X-edge.
     * Default false. When on (and geoV2 is live and the terrain bias is actively biasing), {@code pick()}
     * consumes the X-only edge term ({@code GeoSummary.projectionEdgeXOnly01()}) and, frayed on a
     * coherent province-noise field, promotes the outer east/west band to ocean-authority so the world
     * edge reads as an intentional ocean moat (the existing latitude-correct ocean-family logic paints
     * frozen oceans at the poles, so the "ice" edge comes free). No biome clamps; columns with edgeB==0
     * are bitwise-unaffected. See {@code docs/binder/phase5-boundary-experience-plan-20260709.md}.
     */
    public static final boolean BOUNDARY_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.boundaryV2.enabled", "false"));

    /**
     * Phase 5 Slice B-5 (Hemisphere Passage). Default false. Gates the whole opt-in E/W-edge crossing
     * experience -- the approach fog, the two-button "pass through?" prompt, the mirror-X teleport, the
     * arrival title, and the turn-back push. When off, the current live edge behavior (EW sandstorm haze
     * + warnings) is completely untouched: the server passage receiver rejects every C2S answer, the
     * turn-back nudge never fires, and no S2C arrival is ever sent. NOT tied to {@link #BOUNDARY_V2_ENABLED}
     * -- the passage works whatever the edge terrain looks like (the B-2 ocean shore is a visual nicety,
     * not a requirement). A later default-on decision is Peetsa's (design
     * {@code docs/binder/phase5-b5-hemisphere-passage-design-20260710.md}). This flag is born WITH its
     * build.gradle forwarding line beside {@code boundaryV2} in the same pass (L17 discipline).
     */
    public static final boolean PASSAGE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.passageV2.enabled", "true")); // P3 LIVE-TEST STAGING (branch-local): default ON so Peetsa can fly the passage from the Modrinth profile (no custom -D args there). REVISIT BEFORE MERGE -- the shipped default is Peetsa's call after P3.

    /**
     * Polar small-vegetation fade (Peetsa 2026-07-10). Default TRUE since 2026-07-12: the TEST 75 live
     * look found flowers/grass/sugarcane/firefly bushes thriving at 88+ deg because this shipped off --
     * vanilla 26.2 decorates snowy_plains/frozen_river themselves (flower_default, patch_sugar_cane,
     * patch_firefly_bush_near_water), so the polar cap is never bare without the fade. When on (and the world is an
     * armed globe world -- {@code ACTIVE_RADIUS_BLOCKS > 0}), grass/fern/flower/bush ({@code SimpleBlock})
     * and sugarcane ({@code BlockColumn}) surface-vegetation placements are stripped with a latitude-driven
     * keep-chance: full below {@link com.example.globe.core.PolarVegetationFade#ONSET_DEG} deg, smoothstep-
     * fading to zero by {@link com.example.globe.core.PolarVegetationFade#FULL_DEG} deg, frayed on a coherent
     * province-noise field so it thins naturally rather than at a hard ring. Trees are NOT touched (the
     * existing tree-line/extreme-polar guards own those). Explicitly setting the flag off is byte-identical:
     * the guard mixin's very first check returns immediately, and even flag-on every column below the onset
     * keeps chance 1.0. Placement-time only -- existing chunks are never rewritten (legacy-worldgen pin holds).
     */
    public static final boolean POLAR_VEGETATION_FADE_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polarVegetationFade.enabled", "true"));

    /**
     * Polar water-freeze correctness fix (Peetsa 2026-07-12). Default TRUE: he stood at 89 deg S in a full
     * blizzard with a pool of LIQUID water in his doorway and asked for all water frozen by ~85 deg. Vanilla
     * decides water-&gt;ice per column via {@code Biome.shouldFreeze}, whose first gate rejects any biome that is
     * "warm enough to rain"; latitude-blind {@code river}/{@code ocean} columns (base temperature 0.5) placed
     * deep in the polar cap therefore never freeze. When on (and the world is an armed globe world --
     * {@code LatitudeBiomes.getActiveRadiusBlocks() > 0}) the {@code BiomePolarWaterFreezeMixin} neutralises ONLY
     * that temperature veto for columns at/above {@link com.example.globe.core.PolarWaterFreezeRule#FREEZE_ALL_DEG}
     * (85 deg), so vanilla's own genuine-water/light/edge logic freezes the exposed water on its own edge-inward
     * cadence -- ongoing (tick) AND at worldgen. Default-on rather than gated behind a launch flag because it is a
     * correctness fix Peetsa explicitly requested (mirroring {@code POLAR_VEGETATION_FADE_ENABLED}), but it IS a
     * flag because -- unlike the client-only {@link com.example.globe.core.PolarPrecipitationRule} -- this MODIFIES
     * THE WORLD (places ice, including over player-placed water), so it needs a clean kill switch. Explicitly
     * setting it off is byte-identical: the redirect returns vanilla's unmodified {@code warmEnoughToRain} result.
     * <p><b>Gameplay trade-off:</b> because it hooks the same decision vanilla uses for ALL exposed water, it also
     * freezes player-placed water sources at the pole, so water-dependent builds there (farms needing water,
     * open cauldrons/pools) require a heat source or a sheltered/lit spot -- exactly like a real polar base.
     */
    public static final boolean POLAR_WATER_FREEZE_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polarWaterFreeze.enabled", "true"));

    /**
     * Defensive double parse for the Phase 4 knobs: a {@code null} (property unset) or malformed value
     * degrades to {@code fallback} instead of throwing {@link NumberFormatException} at class-init.
     */
    private static double parseDoubleOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private LatitudeV2Flags() {
    }
}
