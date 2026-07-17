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
     * arrival title, and the turn-back push. NOTE (2026-07-12, degree-geometry slice): the EW edge
     * PRESENTATION (storm onset / haze ramp / banner at ~177.5-179 deg, anchored to the intended
     * X radius) is a GLOBAL redesign per Peetsa's explicit directive ("make the world border features
     * begin at 176 or 177... fog, particles, warnings") and is deliberately NOT gated here -- flag-off
     * disables only the CROSSING (prompt/teleport/curtain/arrival/nudge): the server passage receiver
     * rejects every C2S answer, the
     * turn-back nudge never fires, and no S2C arrival is ever sent. NOT tied to {@link #BOUNDARY_V2_ENABLED}
     * -- the passage works whatever the edge terrain looks like (the B-2 ocean shore is a visual nicety,
     * not a requirement). A later default-on decision is Peetsa's (design
     * {@code docs/binder/phase5-b5-hemisphere-passage-design-20260710.md}). This flag is born WITH its
     * build.gradle forwarding line beside {@code boundaryV2} in the same pass (L17 discipline).
     */
    public static final boolean PASSAGE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.passageV2.enabled", "true")); // DEFAULT ON since 2026-07-12: Peetsa live-approved the passage at P3 ("everything felt good") and it is consensual by design (prompt-gated); the flag remains the kill switch.

    /**
     * Phase 5 Slice B-7 (Pole Passage). Default false. Gates the opt-in N/S pole CROSSING surface -- the (P2)
     * approach prompt at 89.2 deg, the two-button "pass through?" screen, the over-the-pole teleport to the
     * ANTIPODAL meridian (longitude L -> L+180 via {@code PoleArrivalSearch.antipodalX} + yaw+180 -- [P3 fix
     * 2026-07-14: antipodal meridian, not mirrorX]), the deep 89.5-deg arrival (S5) with its post-crossing cold
     * grace, the turn-back push, the {@code axis=POLE} netcode, AND the Wide-world pole hard-stop clamp (S2).
     * Flag-off is byte-identical FOR THAT SURFACE: no pole prompt/teleport/curtain/title/nudge exists, the
     * server rejects every {@code axis=POLE} answer, and the Wide pole stays the unmarked endless death plain
     * it is now (the clamp only exists to be the wall the crossing is the door through).
     *
     * <p><b>What this flag does NOT gate (F2 honesty rescope).</b> The B-7 S3/S4/S6 polar-experience rebalances
     * are GLOBAL and deliberately un-gated (cold-pacing/survival corrections, not crossing features -- the same
     * global-vs-gated split B-5 drew for the EW edge presentation): the frostbite band [85,88) + its F3 frost
     * cue, the ambient snow/fog onset move 85 -&gt; 82 -&gt; 80 (S8), the S4 shelter pause, and the S6 frozen-wounds heal lock
     * are live regardless of this flag. "Flag-off = byte-identical" therefore applies to the
     * crossing/clamp/netcode/nudge ONLY; the polar cold PACING is a separate, global change this pass ships.
     *
     * <p><b>Zero worldgen.</b> Unlike B-6, B-7 is pure presentation + teleport + movement clamp -- no per-world
     * capture, no atlas gate, no mirror-band strips. It works on EVERY existing world (including Peetsa's live
     * ones), so there is no world-state to persist here. NOT tied to {@link #PASSAGE_V2_ENABLED} (the EW axis) --
     * the server passage receiver routes by {@link PassageAxis}, gating EW on {@code PASSAGE_V2_ENABLED} and POLE
     * on this. A later default-on decision is Peetsa's post-P3 call, same as B-5's history. Born WITH its
     * build.gradle client-run forwarding line in the SAME pass (L17 discipline). Design
     * {@code docs/binder/phase5-b7-pole-passage-design-20260713.md} (incl. the binding S1-S6 tail).
     */
    public static final boolean POLE_PASSAGE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polePassageV2.enabled", "true")); // P3 LIVE-TEST STAGING (branch-local, B-6 precedent): default ON for the TEST 97 maiden pole flight. REVISIT BEFORE MERGE -- the shipped default is Peetsa's call after P3.

    /**
     * Phase 5 Slice B-5 (Hemisphere Passage polish, item 1): keep GENERATED STRUCTURES out of an absolute band
     * inward from the E/W (X) world-border edge (Peetsa saw a structure at the border, TEST 83). Default TRUE.
     *
     * <p><b>Its OWN flag, NOT tied to {@link #PASSAGE_V2_ENABLED}.</b> This changes WORLDGEN (which chunks get
     * a structure placed), and worldgen must never silently change under a UI/experience flag -- so the veto
     * gets a dedicated switch a config/launch can pin, independent of whether the crossing experience is on.
     *
     * <p><b>Default-on is defensible</b> because (a) it only affects NEWLY generated chunks -- blocks already
     * on disk are never rewritten (placement-time only, legacy-worldgen pin holds); (b) the planned B-6
     * mirror-band wrap needs a clean, structure-free edge anyway; and (c) it is conservative -- SURFACE
     * structures only, in the outer {@link com.example.globe.core.EdgeStructureVeto#bandBlocks(double)} band per side
     * (TEST 89: degree-anchored at 173 deg, floored at 600 blocks -- covers the visible storm band plus a
     * village's fan-out), leaving underground mineshafts/strongholds (End access) untouched. Explicitly
     * setting it off is byte-identical: the mixin's first check returns immediately. Born WITH its build.gradle
     * forwarding line (L17 discipline), beside {@code passageV2}.
     *
     * <p><b>Accepted upgrade edge case (torn structure at the generation frontier).</b> A multi-chunk surface
     * structure inside the edge band whose ANCHOR chunk generated PRE-upgrade (so its early chunks placed)
     * but whose remaining overlap chunks generate POST-upgrade will have those NEW portions vetoed -- a
     * possible one-time partially-built structure right at the pre/post generation frontier near the edge.
     * Narrow (only band-straddling, frontier-straddling, multi-chunk structures in existing worlds), purely
     * cosmetic, and ACCEPTED (sweep LOW, 2026-07-12): exempting already-anchored starts would need
     * chunk-generation-state queries -- complexity not worth it for a band whose structures we are removing
     * anyway. Fresh worlds can never hit it.
     */
    public static final boolean EDGE_STRUCTURE_VETO_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.edgeStructureVeto.enabled", "true"));

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
     * Phase 5 Slice B-8 (Polar Barrens). Default FALSE -- this CHANGES WORLDGEN (rewrites the deep-cap
     * {@code snowy_plains} monoculture to the first-party {@code globe:polar_barrens} biome), the
     * highest-risk class, so it ships the disciplined way: off-by-default, proven byte-identical
     * flag-off, atlas-gated. When on (and the world is an armed globe world,
     * {@code LatitudeBiomes.ACTIVE_RADIUS_BLOCKS > 0}) the {@code pick()} final override rewrites ONLY
     * inland {@code minecraft:snowy_plains} to {@code globe:polar_barrens} on the coherent
     * {@link com.example.globe.core.PolarBarrensBand} fray between {@link #POLAR_BARRENS_ONSET_DEG} and
     * {@link #POLAR_BARRENS_FULL_DEG}; {@code ice_spikes} accents and real-mountain alpine picks survive
     * because they are not {@code snowy_plains}; coasts/rivers are untouched. It also (flag-on only)
     * appends the barrens to the biome-source candidate pool + the custom-feature index so its features
     * decorate, and drives the {@code PolarBarrensSurfaceMixin} snow/powder/ice ground (surface skin
     * only -- see below).
     *
     * <p><b>Feature list = snowy_plains MINUS surface vegetation (canonical doc -- biome JSON cannot
     * carry comments).</b> The barrens is a SURFACE identity; the underground must stay alive
     * (coordinator order 2026-07-14: a player tunneling under the pole -- which the shelter/frozen-wounds
     * mechanics deliberately reward -- must find ores, lakes, springs and geodes like anywhere else). So
     * {@code data/globe/worldgen/biome/polar_barrens.json} carries vanilla snowy_plains' feature list
     * with every ore/lake/spring/geode/disk/underground entry kept in its EXACT step (lava lakes,
     * amethyst_geode, monster rooms, all 25 ores + underwater_magma + the 3 disks, springs,
     * glow_lichen -- kept as an underground entry: its own placement is surface-relative
     * {@code <= -13}, it can never surface -- and freeze_top_layer), DROPPING exactly the eight surface
     * vegetation entries: {@code trees_snowy}, {@code flower_default}, {@code patch_grass_badlands},
     * {@code brown_mushroom_normal} + {@code red_mushroom_normal} (near-surface placements:
     * heightmap MOTION_BLOCKING with a small y-spread -- vegetation, not cave decoration),
     * {@code patch_pumpkin}, {@code patch_sugar_cane}, {@code patch_firefly_bush_near_water}.
     * <b>Why the RNG-subset property holds (design A3/A5):</b> a strict subset in identical steps adds
     * NO new nodes to the FeatureSorter graph (every kept feature is already indexed via snowy_plains, a
     * possible biome wherever the barrens appears) and -- because no step keeps two entries that were not
     * already consecutive in snowy_plains (machine-verified at build time) -- NO new ordering edges
     * either, so the sorted feature index and every decoration salt are unchanged: zero decoration-RNG
     * shift, flag-on or off. Surface bleakness comes from the {@code PolarBarrensSurfaceMixin} skin
     * substitution (gated to within {@code PolarBarrensBand.SURFACE_SKIN_MARGIN_BLOCKS} of the world
     * surface on non-fluid columns, so underground ore_dirt/ore_gravel veins and underwater floors stay
     * native) plus {@code freeze_top_layer}; near-surface vegetation is additionally stripped by the
     * polar vegetation fade, which is fully bare at/above the barrens onset.
     *
     * <p><b>Byte-identical flag-off</b>: every barrens consumer additionally requires this flag, so
     * flag-off the override never fires, the pool is the unchanged passthrough, the surface mixin's first
     * check returns immediately, and the {@code globe:polar_barrens} biome JSON is present-but-unplaced
     * (it registers UNCONDITIONALLY so a flag-on-then-off world never references a missing biome; its
     * flag-off visibility in F3/{@code /locate} is inherent and documented, not a bug). Placement-time
     * only -- existing chunks are never rewritten (legacy-worldgen pin). Born WITH its build.gradle
     * biomePreview forwarding lines in the SAME pass (L17 discipline). Design
     * {@code docs/binder/phase5-b8-snow-barrens-design-20260714.md}.
     */
    public static final boolean POLAR_BARRENS_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polarBarrens.enabled", "true")); // P3 LIVE-TEST STAGING (branch-local, B-6/B-7 precedent): default ON for the TEST 99 flight so a FRESH world generates the Barrens. REVISIT BEFORE MERGE.

    /**
     * Absolute latitude (deg) where the Polar Barrens begin to fray in. Default = the vegetation-fade
     * finish ({@link PolarVegetationFade#FULL_DEG}, 86 deg) so the barrens start exactly where the grass
     * ends -- the owner's chosen invisible seam (KEEP-SHARED: tuning the veg fade's finish moves this
     * default with it). Live-tunable via {@code -Dlatitude.polarBarrens.onsetDeg}; parsed defensively so
     * a malformed value degrades to the default rather than throwing at class-init.
     */
    public static final double POLAR_BARRENS_ONSET_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.polarBarrens.onsetDeg"), PolarVegetationFade.FULL_DEG);

    /**
     * Absolute latitude (deg) at/above which the Polar Barrens are fully dominant. Default 88 deg.
     * {@link com.example.globe.core.PolarBarrensBand} clamps the effective value to at least
     * {@code onset + 0.5} so the smoothstep denominator stays positive under any {@code -D} pairing.
     * Live-tunable via {@code -Dlatitude.polarBarrens.fullDeg}; parsed defensively.
     */
    public static final double POLAR_BARRENS_FULL_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.polarBarrens.fullDeg"), 88.0);

    // --- Solar Tilt + Seasons (Phase 5B-adjacent, P1) ---------------------------------------------------
    // ONE master kill-switch (visual + functional + seasons together — the functional layer is DERIVED from
    // the same SolarTilt evaluator that drives the visuals, so a split flag would let the sky and the mobs
    // disagree; §2/§8 one-evaluator law) plus four live-tuning dials. Master default OFF (byte-identical
    // flag-off: every solar mixin's first line is gated on SOLAR_TILT_V2_ENABLED). The doubles are parsed
    // defensively (malformed -D degrades to the shipped default rather than throwing at class-init). All are
    // forwarded in build.gradle in the SAME pass (L17 discipline) so a dev run agrees with a shipped jar the
    // moment the flag is flipped for a P2/P3 flight. Design: docs/binder/solar-tilt-design-20260716.md.

    /** Master kill-switch for Solar Tilt (sky path tilt + seasons + the effective-sun mob rules). Default
     *  false → byte-identical flag-off. */
    public static final boolean SOLAR_TILT_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.solarTiltV2.enabled", "true")); // P3 LIVE-TEST STAGING (branch-local, precedent x3): default ON for the TEST 101 first sun flight. REVISIT BEFORE MERGE.

    /** Axial-tilt amplitude δ_max (deg). Default 30 → midnight-sun / polar-night onset at a round, visible
     *  60° (§11 "delta pick"; real Earth 23.5° → 66.5°, deep in the storm cap; max theatrical ≈ 35). */
    public static final double SOLAR_TILT_DELTA_MAX_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.deltaMaxDeg"),
                    com.example.globe.core.SolarTilt.DEFAULT_DELTA_MAX_DEG);

    /** Game-year length (days). Default 360 → 180 game-days between solstices (owner's schedule).
     *  {@code <= 0} ⇒ FROZEN mode (δ constant at {@link #SOLAR_TILT_FROZEN_PHASE_DEG}), which reproduces the
     *  owner's original "one pole is summer forever" world as a single config value (§4e). */
    public static final double SOLAR_TILT_YEAR_LENGTH_DAYS =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.yearLengthDays"),
                    com.example.globe.core.SolarTilt.DEFAULT_YEAR_LENGTH_DAYS);

    /**
     * Functional-layer latitude floor (deg): the effective-sun mob rules (polar-night dark-spawn, midnight-sun
     * spawn veto, undead sun-burn) fire ONLY at {@code |φ| >= this} — the visuals keep the 60° onset, but the
     * mob overrides are held back to the extreme cap. Default 74.5 (sweep A2, BINDING): the "no-villages"
     * line — a 63° village left under 24/7 winter spawns for weeks would be a siege, not atmosphere. The owner
     * may WIDEN this dial after flying (toward the 60° visual onset) if the deep-cap-only rule feels too shy.
     */
    public static final double SOLAR_TILT_FUNCTIONAL_MIN_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.functionalMinDeg"), 74.5);

    /** Frozen-mode phase (deg), used only when {@link #SOLAR_TILT_YEAR_LENGTH_DAYS} {@code <= 0}. Default 0 →
     *  δ = +δ_max → permanent NORTHERN summer (the owner's original ask). A seed-random 0-vs-180 sign is the
     *  clean future home for "which pole is summer this world" (§9). */
    public static final double SOLAR_TILT_FROZEN_PHASE_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.frozenPhaseDeg"),
                    com.example.globe.core.SolarTilt.DEFAULT_FROZEN_PHASE_DEG);

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
