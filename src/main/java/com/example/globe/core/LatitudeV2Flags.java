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
     * Phase 5 Slice B-10 (Polar Outfitting). Default OFF. The master switch for the whole outfitting family --
     * the unified weighted {@link com.example.globe.core.ColdProtection} score (suit 0.25 / leather 0.125), the
     * full-suit warning-silence matrix ({@link com.example.globe.core.PolarColdCues#evaluateLadderFullSuit}),
     * the leather demotion + its once-per-zone reassurance line, the {@code globe:cold_protection} status
     * effect, and the goggle-visor vignette removal.
     *
     * <p><b>Flag-OFF routes the LEGACY path (sweep A3, the no-gap / sequencing law).</b> Leather keeps today's
     * full-negation at four pieces (single-count {@link com.example.globe.core.ColdProtection#damageMultiplier(int)}
     * / {@link com.example.globe.core.ColdProtection#negatesFreezeDamage(int)}), the ladder keeps its
     * HYPOTHERMIA-only suppression ({@link com.example.globe.core.PolarColdCues#evaluateLadder}), and the new
     * items are registered-but-inert (no mod cold weight, no matrix, no effect, no recipe). So there is NEVER a
     * moment where the old leather trick is gone but the suit does not yet protect -- leather demotes ONLY when
     * the suit ships, atomically, under this one flag.
     *
     * <p><b>Items register UNCONDITIONALLY; only BEHAVIOUR/obtainability is gated</b> (mirrors the
     * {@code polar_barrens} precedent). Registries must be consistent across sessions: if item registration
     * were flag-gated, a world saved with a suit piece in a chest and reopened flag-off would reference a
     * missing item. So the {@code Item}s and the {@code MobEffect} register every launch (see
     * {@code com.example.globe.content.PolarOutfitting}); the flag switches the cold weight, the warning matrix,
     * the status effect, the leather demotion, and creative-tab/recipe obtainability. Items are not worldgen, so
     * "flag-off = byte-identical" is trivial on the worldgen axis; flag-off must only preserve today's leather
     * freeze behaviour, which the legacy {@code ColdProtection} path does.
     *
     * <p>Born WITH its {@code build.gradle} client-run forwarding line in the SAME pass (L17 discipline). B-10
     * is presentation/items (no worldgen), so only the {@code runClient} forwarding is needed. Design
     * {@code docs/binder/b10-polar-outfitting-design-20260718.md} (incl. the binding A1-A7 sweep tail).
     */
    public static final boolean POLAR_OUTFITTING_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polarOutfitting.enabled", "false"));

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
     * Absolute latitude (deg) where the Polar Barrens begin to fray in. Default 82 deg -- the owner's chosen
     * value, now an INDEPENDENT constant (S21c 2026-07-19).
     *
     * <p><b>Coupling BROKEN (S21c, deliberate).</b> This USED to nest {@link PolarVegetationFade#FULL_DEG}
     * so the barrens began exactly where the last vegetation died (the "invisible seam"; that is how
     * "Barrens at 82" once carried the onset 86->82 automatically). S21c dropped the vegetation fade's finish
     * to 80 ("veg to 80"), but the owner wants the Barrens BIOME to stay at 82, so the two are now decoupled:
     * the fade strips grass by 80, and the 80-82 band reads as BARE TUNDRA before the barrens claim the land
     * at 82. Live-tunable via {@code -Dlatitude.polarBarrens.onsetDeg}; parsed defensively so a malformed
     * value degrades to the default rather than throwing at class-init.
     */
    public static final double POLAR_BARRENS_ONSET_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.polarBarrens.onsetDeg"), 82.0);

    /**
     * Absolute latitude (deg) at/above which the Polar Barrens are fully dominant. Default 84 deg
     * (S13 2026-07-17: 88->84 so the fray stays +2 deg wide as the onset moved 86->82 -- "Barrens at 82"
     * = fray 82->84). {@link com.example.globe.core.PolarBarrensBand} clamps the effective value to at
     * least {@code onset + 0.5} so the smoothstep denominator stays positive under any {@code -D} pairing.
     * Live-tunable via {@code -Dlatitude.polarBarrens.fullDeg}; parsed defensively.
     */
    public static final double POLAR_BARRENS_FULL_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.polarBarrens.fullDeg"), 84.0);

    /**
     * Phase 5 B-9 P1 GLACIAL CAVES &amp; CREVASSES (Peetsa, TEST-110 flight: polar "caverns are giant
     * voids" -- the crevasses should be "narrow and winding ice labyrinths"; design
     * {@code docs/binder/phase5-b9-glacial-caves-design-20260719.md}, swept 2026-07-19). When on, the
     * carver-list filter in {@code NoiseChunkGeneratorCarveMixin} APPENDS two globe configured carvers
     * to every seed chunk that is barrens-band LAND (the exact shared
     * {@link com.example.globe.core.PolarBarrensBand#isBarrens} latitude+fray decision the barrens
     * biome/glacier use, sampled once per seed chunk at its min corner; sea columns are skipped by a
     * raw-biome-source ocean probe, and the {@code replaceable} tag is the structural backstop where
     * the probe misses). Appending AFTER the raw list preserves every vanilla carver's
     * {@code seed + index} stream, so flag-off is byte-identical by construction (no append, no strip,
     * vanilla's own Iterable returned untouched). NEW CHUNKS ONLY (legacy-worldgen pin); the biome
     * JSON is deliberately NOT touched (its carver list is dead wiring at this seam -- see the mixin).
     *
     * <p><b>The two carvers (vanilla TYPES, our data configs -- vanilla-first, no custom carver
     * code), with the P1 parameter decisions ("dev picks" recorded here, the flag being the family
     * register, like the barrens biome JSON decisions above):</b>
     * <ul>
     *   <li>{@code globe:crevasse} (canyon type): open-top narrow slots through the glacier.
     *       probability 0.14 (owner dial; a deliberately fissured glacier vs vanilla canyon's 0.01 --
     *       parked question #1), y uniform absolute 66..112 (anchored to the REAL polar surface
     *       distribution: median 71-78, p95 115-126), lava_level absolute -56 (below anything the
     *       band can reach -- no lava windows in a glacier). Dev picks: shape thickness trapezoid
     *       0.0..4.0 plateau 1.0 (vanilla 0.0..6.0 plateau 2.0 -- about two-thirds the width, so the
     *       cut reads as a slot, not a ravine room) and yScale 4.0 (vanilla 3.0 -- recovers the
     *       vertical extent the narrower thickness would lose, keeping the design's 20-40 block cut
     *       envelope, bottoms ~26..92; bottoms below sea level 63 POND via aquifers = the fish-lake
     *       seed, wanted). All other shape fields verbatim vanilla canyon.</li>
     *   <li>{@code globe:glacial_tunnels} (cave type): the labyrinth. probability 0.12, y uniform
     *       absolute 30..90 (glacier body under high terrain + upper stone under median terrain),
     *       lava_level absolute -56. Dev picks: horizontal_radius_multiplier 0.49..0.98 (exactly
     *       0.7x vanilla's 0.7..1.4) and vertical_radius_multiplier 0.4..0.65 (exactly 0.5x
     *       vanilla's 0.8..1.3) so cave-carver branching supplies the maze but the corridors stay
     *       narrow and winding; yScale + floor_level verbatim vanilla cave convention.</li>
     * </ul>
     * Both use {@code replaceable #minecraft:overworld_carver_replaceables} AS-IS (design sweep
     * finding 3, DECIDED): the tag contains packed_ice + the snow family but NOT plain ice, so the
     * frozen-sea skin is structurally uncarvable (the sacred under-ice fiction survives) and a
     * crevasse slicing a frozen river leaves the 1-block ice skin spanning the slot -- an ice bridge,
     * real glaciology, kept.
     *
     * <p><b>Byte-identity honesty (design law):</b> gate-1 CANNOT see this seam (the atlas never
     * executes {@code applyCarvers}); flag-off identity rests on the pure
     * {@link com.example.globe.core.GlacialCarverLaw} unit tests + the standing gate-1 re-proof
     * (class-load side) + the orchestrator self-fly as the end-to-end carve proof.
     */
    public static final boolean GLACIAL_CAVES_V1_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.glacialCavesV1", "true")); // P3 LIVE-TEST STAGING (branch-local, B-6/B-7/B-8 precedent): default ON for the first glacial-caves flight so a FRESH world carves the crevasses. REVISIT BEFORE MERGE -- the design default is OFF; the shipped default is Peetsa's call after the flight.

    // ── S36 VOID TAMING (owner 2026-07-21: mechanism C / onset 82 / tame-not-eliminate) ──────────────────
    //
    // Caps the SKY-BREACHING noise voids of the polar underground by wrapping finalDensity on the Phase 4
    // terrain-wrapper rails (see terrain.VoidTamingFunction + core.VoidTamingLaw). EVERY knob is a sysprop
    // so the owner's tuning session needs no rebuild. Flag honesty: ENABLED=false (the default) never
    // installs; ENABLED=true with STRENGTH<=0 ALSO never installs (sweep REQUIRED-FIX 3: identity by
    // construction beats identity by proof) -- the wrapper only exists in the router when it can act.
    // The fill BITES (converts air to solid) only where STRENGTH * gates > 1, so the usable tuning range
    // is ~[1..2]: 1.0 caps necks right at the iso-surface, higher makes the cap firmly solid (sweep 8).

    /** Master switch. Default OFF -- the owner's tuning session flips it with -Dlatitude.voidTaming.enabled=true. */
    public static final boolean VOID_TAMING_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.voidTaming.enabled", "false"));

    /** The K. 0.0 (default) = not installed at all; ~1.0 = cap at the iso-surface; ~1.5 = firm; sane max ~2. */
    public static final double VOID_TAMING_STRENGTH =
            parseDoubleOrDefault(System.getProperty("latitude.voidTaming.strength"), 0.0);

    /** Absolute latitude (deg) where taming begins to feather in. Owner decision: 82 (the barrens line). */
    public static final double VOID_TAMING_ONSET_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.voidTaming.onsetDeg"), 82.0);

    /** Absolute latitude (deg) of full taming strength (smoothstep from onset -- the S28 GlacialBlend
     *  precedent: geography, never a dead-straight wall). */
    public static final double VOID_TAMING_FULL_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.voidTaming.fullDeg"), 85.0);

    /** HARD protect floor (block Y): at/below this the fill NEVER acts -- the glacial-cave labyrinth and
     *  the S35 trap deep-drop voids live below it. */
    public static final int VOID_TAMING_PROTECT_FLOOR_Y =
            (int) Math.round(parseDoubleOrDefault(System.getProperty("latitude.voidTaming.protectFloorY"), 48.0));

    /** Feather (blocks) above the protect floor over which the fill fades out (sweep REQUIRED-FIX 4 -- no
     *  horizontal stone shelf at floor+1). */
    public static final int VOID_TAMING_FLOOR_FEATHER_BLOCKS =
            (int) Math.round(parseDoubleOrDefault(System.getProperty("latitude.voidTaming.floorFeatherBlocks"),
                    (double) VoidTamingLaw.FLOOR_FEATHER_BLOCKS));

    /**
     * S13 (e) POLAR SURFACE ALLOWLIST (Peetsa, TEST-103 flight, 2026-07-17). Default TRUE -- a polar-immersion
     * spawn rule (sibling of the vegetation fade / water freeze), shipped live for the S13 flight; the owner
     * confirms the ship default post-flight like every prior polar rule. This is NOT worldgen (it filters
     * per-tick {@code NATURAL} monster spawns), so there is no byte-identity axis to protect -- the flag is a
     * kill-switch + live dial, and {@code SpawnPlacementsPolarSurfaceMixin}'s first line is gated on it so a
     * dev can turn it fully off. When on: in polar storm country ({@code |lat| >=}
     * {@link com.example.globe.core.PolarSurfaceSpawns#ONSET_DEG}, default = the 80-deg storm onset) a
     * SKY-EXPOSED monster spawn is vetoed unless it is a {@code stray}; caves (no sky) are 100% vanilla, and
     * the surviving surface strays are additionally thinned 1-in-3 so the non-barrens polar biomes lose the
     * stray glut too. Composes with the Solar-Tilt polar-night rule (that rule gates darkness, this one gates
     * type -- documented order in {@code PolarSurfaceSpawns}). The onset dial is owned by
     * {@link com.example.globe.core.PolarSurfaceSpawns} (its own {@code -D}, coupled to the storm onset);
     * this flag is forwarded in build.gradle in the SAME pass (L17 discipline).
     */
    public static final boolean POLAR_SURFACE_SPAWNS_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.polarSurfaceSpawns.enabled", "true"));

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
     * spawn veto, undead sun-burn) fire ONLY at {@code |φ| >= this}.
     *
     * <p><b>Default 60.0 — the OWNER-VERDICT widening to the visual onset (TEST 113, 2026-07-19).</b>
     * History: shipped at 74.5 (sweep A2, BINDING at the time): the "no-villages" line — the worry was a 63°
     * village left under 24/7 winter spawns for weeks would be a siege, not atmosphere — with the explicit
     * reservation that "the owner may WIDEN this dial after flying (toward the 60° visual onset) if the
     * deep-cap-only rule feels too shy." That flight verdict has now landed: the owner flew TEST 113 through
     * the 66.5-74° polar-night country under a dark sky with NO monsters and called it wrong — "monsters
     * should spawn." So the functional floor moves 74.5 -> 60.0, aligning the mob rules with the solar-tilt
     * VISUAL onset (δ_max 30 ⇒ band onset 60°): where the sky says polar night, the night rules apply. The A2
     * village-siege concern is retired by the same owner verdict (villages were independently pulled back to
     * ≤ 80's habitable band anyway; the S13e strays-only surface law ≥ 80 is untouched by this dial). REVISIT
     * only via a new owner verdict; the {@code -Dlatitude.solarTilt.functionalMinDeg} dial remains live for
     * in-flight tuning.
     */
    public static final double SOLAR_TILT_FUNCTIONAL_MIN_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.functionalMinDeg"), 60.0);

    /** Frozen-mode phase (deg), used only when {@link #SOLAR_TILT_YEAR_LENGTH_DAYS} {@code <= 0}. Default 0 →
     *  δ = +δ_max → permanent NORTHERN summer (the owner's original ask). A seed-random 0-vs-180 sign is the
     *  clean future home for "which pole is summer this world" (§9). */
    public static final double SOLAR_TILT_FROZEN_PHASE_DEG =
            parseDoubleOrDefault(System.getProperty("latitude.solarTilt.frozenPhaseDeg"),
                    com.example.globe.core.SolarTilt.DEFAULT_FROZEN_PHASE_DEG);

    /**
     * S13(a) AURORA BOREALIS -- the polar-night sky curtains ({@code client.AuroraRenderer} +
     * {@code core.AuroraLaw}). Default TRUE, but it RIDES INSIDE {@link #SOLAR_TILT_V2_ENABLED}: the aurora keys
     * on the polar-night / midnight-sun dark-sky signal that only exists with the solar tilt on, so the renderer
     * requires BOTH flags. Kept as its OWN flag (not folded into the solar master) so the aurora is an
     * independent kill switch -- a player who wants the tilted sun/seasons but not the aurora sets this off; and
     * a solar-tilt-off world never draws it regardless. Byte-identical off in EITHER case: the renderer's first
     * line returns when solar tilt is off OR this is off, so no COLLECT_SUBMITS geometry is ever emitted. Born
     * WITH its build.gradle client-run forwarding line in the SAME pass (L17 discipline), beside the solar dials.
     */
    public static final boolean AURORA_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.aurora.enabled", "true"));

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
