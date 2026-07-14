package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- the SINGLE SOURCE OF TRUTH for the E/W (antimeridian) edge
 * feature geometry. Pure math, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 *
 * <h2>Why this class exists (the TEST 86 flight-recorder finding)</h2>
 * Every edge feature -- the approach fog, the crossing prompt, the arm re-arm line, the single white
 * advisory banner (TEST 89 retired the two-tier system and the EW dust particles), the mirror-teleport
 * arrival -- USED to derive its trigger distances from
 * the LIVE {@code WorldBorder} size ({@code getSize()*0.5}). In Peetsa's TEST-86 world the vanilla border was
 * mid-LERP at join (a stale {@code BorderLerp*} persisted in level.dat, growing halfSize over ~4 minutes), so
 * every one of those lines physically SLID ~100 blocks during his session -- a very plausible contributor to
 * the walked-repro failures. This class fixes that at the root: ALL edge geometry is derived from the mod's
 * OWN INTENDED X radius (the value {@code GlobeMod.setGlobeBorder} computes: {@code zRadius} in Classic,
 * {@code zRadius * MERCATOR_ASPECT} in Wide/Mercator), NOT the live border. The vanilla border stays the
 * physical wall; a lerping or {@code /worldborder}-vandalized border can no longer move our lines.
 *
 * <h2>Degrees, not blocks (Peetsa's directive)</h2>
 * The anchors are LONGITUDE DEGREES from the world center. The antimeridian edge is 180 deg; 1 deg of
 * longitude is {@code xRadiusIntended / 180} blocks. Peetsa asked the whole edge experience to be COMPACT --
 * fog within ~2.5 deg of the wall, a crossing that lands ~2 deg in, and a HEADS-UP advisory that leads the
 * fog rather than landing on top of it. The edge-flow rework (2026-07-13, this pass) resettled the anchors on
 * Peetsa's confirmed B-5 flight:
 * <ul>
 *   <li>{@link #ADVISORY_DEG} 176.0 -- the single white advisory banner fires here (4 deg out), the OUTERMOST
 *       edge element and a genuine heads-up: it now leads the fog by 0.5 deg instead of arming ON the fog
 *       onset (Peetsa: the old advisory "lands almost simultaneously" with the fog). Decoupled from
 *       {@link #RAMP_START_DEG} -- this is the banner-visibility cap now (nothing edge-related shows equatorward
 *       of here).</li>
 *   <li>{@link #REARM_DEG} 177.0 -- the passage arm re-arms once the player walks back out past this line
 *       (3 deg out). Moved 178 -&gt; 177 in lockstep with the prompt so the walk-back distance
 *       ({@code prompt -> rearm}) stays a modest 1 deg, exactly as before -- the whole cycle shifted 1 deg
 *       outward, it did not stretch. On real worlds the degree anchor dominates cleanly; on the tiny Itty-Bitty
 *       world the {@link #DEAD_ZONE_MIN_BLOCKS} anti-machine-gun floor binds (as it does for every line).</li>
 *   <li>{@link #RAMP_START_DEG} 177.5 -- fog begins (2.5 deg out). UNCHANGED. The fog onset and its inner
 *       full-opacity {@link #CLIMAX_DEG} are both untouched by this pass, so the fog subsystem
 *       ({@code FogRendererPassageSetupMixin}, {@code GlobeClientState.ewIntensity01}) is byte-identical ONLY where the 177.5-deg term dominates rampStart (xRadius >= ~8640).</li>
 *   <li>{@link #PROMPT_DEG} 178.0 -- the crossing prompt opens (2 deg out). Moved 179 -&gt; 178 (Peetsa's
 *       confirmed flow offers the crossing at 178, not 179). Now sits INSIDE the fog onset (the prompt fires
 *       while the fog is still building, ~1/3 opacity) and coincides with {@link #ARRIVAL_DEG}.</li>
 *   <li>{@link #CLIMAX_DEG} 179.0 -- fog reaches FULL opacity here (1 deg out) and holds to the wall. This is
 *       the OLD prompt position; the edge-flow rework DECOUPLED it from {@link #PROMPT_DEG} (which moved to 178)
 *       precisely so the fog experience -- onset 177.5, full 179 -- stays exactly as shipped and a crossing at
 *       178 still emerges into the THINNING fog edge, not a full whiteout. Read by the fog curves as the
 *       {@code fogClimaxDist}.</li>
 *   <li>{@link #ARRIVAL_DEG} 178.0 -- the longitude the crossing DROPS the player at on the far side, 2 deg
 *       from the wall. UNCHANGED, and now coincides EXACTLY with {@link #PROMPT_DEG} 178 (arrival lands ON the
 *       prompt line). Still 0.5 deg poleward of the fog onset (177.5), so you emerge in the thinning fog
 *       looking inward. Landing on the prompt line is SAFE because the S2C arrival ALWAYS seeds the arm
 *       disarmed and the prompt requires ARMED -- there is no self-prompt (pinned in {@code HemispherePassage}
 *       tests).</li>
 *   <li>{@link #EDGE_REPROMPT_DEG} 179.6 -- the post-arrival EDGE auto-re-prompt line (0.4 deg out, hard by the
 *       wall). NOT part of the ordinary approach: it only fires for a player who has just ARRIVED (the
 *       SEEDED_DISARMED state) and then walks all the way to the wall, re-offering the crossing ONCE without a
 *       click or a walk-out. See {@link HemispherePassage} for the state machine.</li>
 * </ul>
 *
 * <h2>The nested ordering invariant (edge-flow rework)</h2>
 * The old nest was {@code prompt(179) > rearm(178) > rampStart(177.5)}. Moving the prompt in to 178 shrank the
 * prompt-to-fog gap to 0.5 deg -- NARROWER than the 64-block {@link #DEAD_ZONE_MIN_BLOCKS} re-arm floor on
 * every real world -- so the re-arm line can no longer fit between the prompt and the fog onset; it MUST sit
 * outside the fog. So the new nest, in DEGREES (edge -&gt; equator), is
 * {@code climax(179) > prompt(178) > rampStart(177.5) > rearm(177) > advisory(176)}, with
 * {@code edgeReprompt(179.6)} hard by the wall inside them all. Translated to DISTANCE-FROM-EDGE (which shrinks
 * toward the wall) that inverts to the chain {@link #resolve} guarantees on EVERY world size:
 * <pre>
 *   edgeRepromptDist &lt; climaxDist &lt; promptDist(==arrivalDist) &lt; rampStartDist &lt; rearmDist &lt; advisoryDist
 * </pre>
 * ARRIVAL is not a separate rung: it sits EXACTLY on {@code promptDist}. The re-arm line moved OUTSIDE the fog
 * band this pass (a disarmed player now walks out of the fog to re-arm), which is the honest consequence of the
 * 64-block floor being wider than the new 0.5-deg fog band. The fog band itself ({@code climaxDist} up to
 * {@code rampStartDist}) is unchanged.
 *
 * <h2>Block floors for small worlds</h2>
 * On the smallest world (Itty-Bitty Classic, xRadius 3750) one degree is only ~20.8 blocks, so the pure degree
 * spacing would put the whole experience inside a handful of blocks. The floors keep it readable and keep the
 * hysteresis honest, without ever breaking the ordering:
 * <ul>
 *   <li>{@link #PROMPT_MIN_DIST_BLOCKS} 40 -- the prompt (and, coupled to it, arrival and the fog climax) never
 *       sits closer than 40 blocks from the wall.</li>
 *   <li>{@link #DEAD_ZONE_MIN_BLOCKS} 64 -- the re-arm line stays at least 64 blocks past the prompt line
 *       (the DEAD_ZONE anti-machine-gun discipline; {@code == HemisphereCrossing.DEAD_ZONE_BLOCKS}).</li>
 *   <li>{@link #FOG_BAND_MIN_BLOCKS} 60 -- the fog ramp (onset down to the full-fog climax) is at least
 *       60 blocks wide so the "weather front rolling in" reads instead of snapping on.</li>
 *   <li>{@link #ORDER_MIN_STEP_BLOCKS} 8 -- when a floor pushes a nearer line out, the next line out is kept at
 *       least this much farther so the chain never inverts or ties.</li>
 *   <li>{@link #EDGE_REPROMPT_MIN_DIST_BLOCKS} 8 -- the edge auto-re-prompt never sits closer than 8 blocks
 *       from the wall, comfortably outside the vanilla border's ~5-block warning/damage-safe zone.</li>
 * </ul>
 *
 * <h2>Effective per-world geometry (blocks from the wall)</h2>
 * On Small/Regular Wide the pure degree values hold un-floored; the floors bind only on Itty-Bitty. Columns are
 * distance-from-edge (blocks); the ordering chain above reads left-to-right smallest-to-largest.
 * <pre>
 *   world (xRadius)     edgeReprompt  climax   prompt   rampStart  rearm    advisory   arrival
 *   Itty-Bitty (3750)      8.33        40.0     41.67    100.0      108.0    116.0      41.67   (floors bind)
 *   Small-Wide (15000)    33.33        83.33   166.67    208.33     250.0    333.33    166.67   (pure degrees)
 *   Regular-Wide (20000)  44.44       111.11   222.22    277.78     333.33   444.44    222.22   (pure degrees)
 * </pre>
 * (On Itty: climax floors to 40; prompt = 2 deg = 41.67 &gt; 40 so the degree wins; rampStart is driven by the
 * 60-block fog-band floor to climax+60 = 100; rearm by the ordering step to rampStart+8 = 108; advisory by the
 * ordering step to rearm+8 = 116. On Itty the fog climax and arrival both floor to essentially the prompt line,
 * so an Itty arrival lands at the inner fog -- a documented consequence of that world being ~146 blocks wide
 * across the whole experience.)
 *
 * <h2>NOT in scope of this class</h2>
 * The polar N/S geometry, the polar warning-ladder degree constants (KEEP-SHARED with the EW axis via
 * {@code LatitudeMath.POLAR_STAGE_*}), and the polar fog are the OTHER axis -- untouched. The
 * {@code EdgeStructureVeto} band is a separate placement-determinism concern; it is ALSO degree-anchored now
 * (its own 173-deg anchor, poleward of this visual ramp plus a fan-out buffer), but it derives its own width
 * and does not read this record.
 */
public final class EdgeGeometry {

    private EdgeGeometry() {
    }

    // ---- degree anchors (longitude degrees from world center; the antimeridian edge is 180) ----

    /** The single white advisory banner fires at/inside this -- 176 deg (4 deg out), the OUTERMOST edge
     *  element and the banner-visibility cap. Edge-flow rework: decoupled from {@link #RAMP_START_DEG} so the
     *  advisory LEADS the fog by 0.5 deg (a heads-up before the fog) instead of arming on the fog onset. */
    public static final double ADVISORY_DEG = 176.0;
    /** The passage arm re-arms once the player is farther out than this -- 177 deg (3 deg out). Edge-flow
     *  rework: moved 178 -&gt; 177 in lockstep with the prompt (179 -&gt; 178), so the walk-back gap
     *  {@code prompt -> rearm} stays a modest 1 deg. Now sits OUTSIDE the fog onset (the 64-block dead-zone floor
     *  is wider than the new 0.5-deg fog band, so re-arm can no longer fit inside the fog). */
    public static final double REARM_DEG = 177.0;
    /** Fog onset (2.5 deg out). UNCHANGED by the edge-flow rework -- nothing terrain-visible shows equatorward
     *  of here. */
    public static final double RAMP_START_DEG = 177.5;
    /** The crossing prompt opens at/inside this -- 178 deg (2 deg out). Edge-flow rework: moved 179 -&gt; 178
     *  (Peetsa's confirmed flow). Coincides with {@link #ARRIVAL_DEG}. */
    public static final double PROMPT_DEG = 178.0;
    /** Fog reaches FULL opacity at/inside this -- 179 deg (1 deg out) -- and holds to the wall. Edge-flow
     *  rework: this is the OLD prompt position, now a DEDICATED anchor decoupled from {@link #PROMPT_DEG} so the
     *  fog band (onset 177.5, full 179) is byte-identical to what shipped and a 178-deg crossing still emerges
     *  into thinning fog rather than a full whiteout. Read as the {@code fogClimaxDist}. */
    public static final double CLIMAX_DEG = 179.0;
    /** The longitude the crossing drops the arriving player at on the far side: 2 deg from the wall (178 deg).
     *  UNCHANGED, and now coincides EXACTLY with {@link #PROMPT_DEG} -- arrival lands ON the prompt line. Still
     *  0.5 deg poleward of the fog onset (177.5), so the player emerges in the thinning fog looking inward.
     *  Landing on the prompt line is safe: the S2C arrival ALWAYS seeds the arm DISARMED and the prompt requires
     *  ARMED, so there is no self-prompt (see {@link HemispherePassage}'s arrival contract). */
    public static final double ARRIVAL_DEG = 178.0;
    /** The post-arrival EDGE auto-re-prompt line -- 179.6 deg (0.4 deg out, hard by the wall). Only reachable in
     *  the SEEDED_DISARMED state: a player who has just arrived and then walks all the way to the wall is
     *  re-offered the crossing ONCE here (no click, no walk-out). Placed just inside the fog climax and well
     *  outside the vanilla border's ~5-block warning/damage-safe zone. See {@link HemispherePassage}. */
    public static final double EDGE_REPROMPT_DEG = 179.6;

    // ---- block floors (small-world readability + hysteresis discipline) ----

    /** The prompt line (and, coupled to it, arrival and the fog climax) never sits closer than this to the wall
     *  (readable approach even on Itty-Bitty). */
    public static final double PROMPT_MIN_DIST_BLOCKS = 40.0;
    /** The re-arm line stays at least this far past the prompt line (anti-machine-gun DEAD_ZONE discipline,
     *  == {@link HemisphereCrossing#DEAD_ZONE_BLOCKS}). */
    public static final double DEAD_ZONE_MIN_BLOCKS = 64.0;
    /** The fog ramp (onset -> full-fog climax) is at least this wide so the front reads as gathering. */
    public static final double FOG_BAND_MIN_BLOCKS = 60.0;
    /** Strict-ordering epsilon: when a floor pushes a nearer line out, the next line out is kept at least this
     *  much farther so the ordering chain never inverts or ties. */
    public static final double ORDER_MIN_STEP_BLOCKS = 8.0;
    /** The edge auto-re-prompt never lands closer than this to the wall (~8 blocks), so it stays outside the
     *  vanilla border's ~5-block warning/damage-safe zone even on the tiniest world. */
    public static final double EDGE_REPROMPT_MIN_DIST_BLOCKS = 8.0;

    /** Blocks per longitude degree for a given intended X radius: {@code xRadius / 180}. */
    public static double blocksPerDegree(double xRadiusIntended) {
        return Math.max(1.0, xRadiusIntended) / 180.0;
    }

    /** Distance-from-edge (blocks) for a longitude-degree anchor at a given intended X radius:
     *  {@code (180 - deg) * blocksPerDegree}. */
    public static double distForDeg(double deg, double xRadiusIntended) {
        return (180.0 - deg) * blocksPerDegree(xRadiusIntended);
    }

    /**
     * Distance (blocks, {@code >= 0}) from world-X {@code x} to the nearest E/W edge, measured against the
     * INTENDED X radius (NOT any live border size): {@code max(0, xRadiusIntended - |x - centerX|)}. This is
     * the ONE quantity every edge feature reads, on both client and server, so a lerping/vandalized border
     * can never make the client and server disagree about where the edge is.
     */
    public static double distanceToEdge(double xRadiusIntended, double centerX, double x) {
        return Math.max(0.0, xRadiusIntended - Math.abs(x - centerX));
    }

    /**
     * The resolved, per-world block geometry: every distance is measured FROM the E/W edge (shrinks toward
     * the wall) and derived purely from the intended X radius, with the small-world floors applied and the
     * strict ordering chain
     * {@code edgeRepromptDist < climaxDist < promptDist < rampStartDist < rearmDist < advisoryDist} guaranteed
     * (arrival sits exactly on {@code promptDist}).
     */
    public record Resolved(double xRadiusIntended,
                           double promptDist,
                           double rearmDist,
                           double rampStartDist,
                           double fogClimaxDist,
                           double arrivalDist,
                           double advisoryDist,
                           double edgeRepromptDist) {

        /** {@code |x - centerX|} at which the crossing drops the arriving player (the ARRIVAL_DEG column:
         *  178 deg = ~2 deg from the wall, on the prompt line). Clamped non-negative for degenerate tiny radii. */
        public double arrivalAbsX() {
            return Math.max(0.0, xRadiusIntended - arrivalDist);
        }
    }

    /**
     * Resolve the block geometry for a world of the given intended X radius. Pure function of {@code xRadius}
     * ONLY -- this is exactly what makes the whole edge experience immune to a lerping/vandalized live border
     * (the old code fed it {@code border.getSize()*0.5}).
     */
    public static Resolved resolve(double xRadiusIntended) {
        // Nearest-to-edge first, flooring and then re-tightening the ordering OUTWARD so no floor can invert it.
        // The fog full-opacity point (179 deg) is the innermost of the visible lines; it shares the prompt's
        // 40-block floor so it stays byte-identical to the old climax(==prompt@179).
        double climax = Math.max(distForDeg(CLIMAX_DEG, xRadiusIntended), PROMPT_MIN_DIST_BLOCKS);
        // The prompt (178 deg) -- one degree poleward of the climax. arrival lands exactly on it.
        double prompt = Math.max(distForDeg(PROMPT_DEG, xRadiusIntended), PROMPT_MIN_DIST_BLOCKS);
        // NOTE (sweep, staged-flow round): the climax<prompt adjacency is the one chain link without an
        // ORDER_STEP floor -- deliberately. A tie needs xRadius <= 3600, below every shippable size (min
        // 3750), and adding a floor here MOVES real geometry on small worlds (climax+8 > the 178-deg prompt
        // at 3750), which a hardening must never do. assertChain still fails loudly if a sub-shippable
        // radius ever ties.
        // Fog onset (177.5 deg): at least FOG_BAND_MIN past the climax so the front reads, and strictly outside
        // the prompt line by ORDER_STEP.
        double rampStart = Math.max(
                Math.max(distForDeg(RAMP_START_DEG, xRadiusIntended), climax + FOG_BAND_MIN_BLOCKS),
                prompt + ORDER_MIN_STEP_BLOCKS);
        // Re-arm (177 deg): at least DEAD_ZONE past the prompt line (the anti-machine-gun hysteresis), and
        // strictly OUTSIDE the fog onset by ORDER_STEP (re-arm sits equatorward of the fog now).
        double rearm = Math.max(
                Math.max(distForDeg(REARM_DEG, xRadiusIntended), prompt + DEAD_ZONE_MIN_BLOCKS),
                rampStart + ORDER_MIN_STEP_BLOCKS);
        // Advisory banner (176 deg): the outermost element, strictly outside the re-arm line by ORDER_STEP.
        double advisory = Math.max(distForDeg(ADVISORY_DEG, xRadiusIntended), rearm + ORDER_MIN_STEP_BLOCKS);
        // Arrival lands EXACTLY on the prompt line (ARRIVAL_DEG == PROMPT_DEG). The disarmed-arrival seed makes
        // this self-prompt-free on every world (evaluate/evaluatePhase open only when ARMED).
        double arrival = prompt;
        // The post-arrival edge auto-re-prompt (179.6 deg), hard by the wall, floored clear of the vanilla
        // border warning/damage-safe zone.
        double edgeReprompt = Math.max(distForDeg(EDGE_REPROMPT_DEG, xRadiusIntended), EDGE_REPROMPT_MIN_DIST_BLOCKS);

        return new Resolved(xRadiusIntended, prompt, rearm, rampStart, climax, arrival, advisory, edgeReprompt);
    }
}
