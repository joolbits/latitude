package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- the SINGLE SOURCE OF TRUTH for the N/S (pole) edge feature geometry, the
 * Z-axis sibling of {@link EdgeGeometry}. Pure math, zero Minecraft imports (Core Logic layer, unit-testable in
 * a plain JVM).
 *
 * <h2>Latitude degrees, not blocks</h2>
 * The anchors are LATITUDE DEGREES from the world center; the pole LINE is 90 deg. One degree of latitude is
 * {@code zRadius / 90} blocks (the Z/latitude radius, NOT the E/W X radius). On a Wide/Mercator world this
 * EQUALS the E/W blocks-per-longitude-degree ({@code xRadius/180} with aspect 2.0); on Classic the polar
 * degrees are 2x coarser. Distance is measured FROM THE POLE and shrinks to 0 AT the pole:
 * {@code distFromPole(deg) = (90 - deg) * blocksPerDegree}.
 *
 * <h2>Lerp-immune for free (TEST 86 protection)</h2>
 * Unlike the EW edge (which needed a new intended-X-radius handshake to escape a lerping vanilla border), the
 * pole line is already anchored to the mod's OWN synced Z radius ({@code LatitudeMath.latitudeRadius}, pushed by
 * {@code GlobeStatePayload.latitudeZRadius}), never the live border half. So B-7's geometry inherits the TEST-86
 * immunity with no new field: a lerping/vandalized border can never slide these lines.
 *
 * <h2>The anchors (design {@code phase5-b7-pole-passage-design-20260713.md} §3.2, S5 tail, and the S16(c) tail)</h2>
 * The polar warning ladder (85/87/88/89.7 after the S13c DANGER retime) and every {@code PolarHazardWindow}
 * onset are KEEP-SHARED and DO NOT MOVE; B-7's lines interleave between them. <b>S16(c) (owner, TEST 106):</b>
 * the crossing prompt and the arrival BOTH move to the pole line -- the prompt opens PRESSED AGAINST THE WALL
 * and the arrival lands ON the line, so the escape trek is 90 -&gt; 88 and the staged ladder simplifies to one
 * wall prompt:
 * <ul>
 *   <li>{@link #ARRIVAL_DEG_POLE} 90 (was 89.5) -- the crossing drops the arriving player ON the pole line
 *       (distance 0), the deepest point of the far side's blizzard. {@code arrivalAbsZ() == zRadius}, so the
 *       teleport lands the player on the line; the S2 hard-stop clamp tolerates standing there
 *       ({@link PoleHardStop#CLAMP_EPSILON}) and only kills OUTWARD motion. The frozen-hearts thaw happens during
 *       the escape trek; the only reprieve is the post-crossing cold GRACE ({@link PoleCrossingGrace}, the
 *       ~45-tick arrival-curtain window) so a low-health crosser cannot die inside the ceremony.</li>
 *   <li>{@link #REARM_DEG_POLE} 88.5 -- the strict walk-out re-arm line (1.5 deg out); floored to
 *       {@code prompt + DEAD_ZONE 64}. With the SEEDED arrival state it carries the self-prompt law now that the
 *       prompt lives at the wall.</li>
 *   <li>{@link #PROMPT_DEG_POLE} 90 (was 89.2) -- the crossing prompt opens pressed against the wall. Its raw
 *       distance is 0, floored to the tiny {@link #WALL_PROMPT_DIST_BLOCKS} epsilon (NOT the 40-block approach
 *       floor -- meaningless at the wall), so the trigger is "at the wall". The gesture re-prompt fires only
 *       inside this same tiny band.</li>
 *   <li>the near-pole edge-re-prompt (was 89.9) COLLAPSED into the wall prompt: its distance is the DISABLED
 *       sentinel {@link #EDGE_REPROMPT_DISABLED_DIST} (see below) -- one prompt, at the wall.</li>
 * </ul>
 *
 * <h2>No fog anchors</h2>
 * The polar whiteout/depth-fog already ramps from the ambient onset to 90 ({@code PolarHazardWindow}, {@code
 * FogRendererPolarSetupMixin}) -- it IS the ceremony fog, free. B-7 adds NO fog anchors and NO fog mixin, so
 * there is no {@code rampStart}/{@code climax} rung here (the fog composition B-5 solved stays solved by adding
 * nothing).
 *
 * <h2>ONE prompt, at the wall -- the seeded arrival still guards self-prompting (S16(c))</h2>
 * Arrival (distance 0) sits INSIDE the wall prompt band (~3) and INSIDE the re-arm line on EVERY world size. A
 * fresh arrival ALWAYS seeds {@link HemispherePassage.Phase#SEEDED_DISARMED} and HOLDS it: no self-prompt at the
 * arrival column is guaranteed by SEEDING (state law -- the ordinary prompt requires ARMED) AND by the collapsed
 * edge-re-prompt (its distance is the disabled sentinel, so the seeded one-shot can never fire). Walking out past
 * 88.5 re-arms; turning back then opens the ordinary prompt AT the wall (a fresh journey). While seeded, only the
 * aim-gated right-click gesture (inside the same wall band) can re-summon it. The old "prompt &lt; arrival" AND
 * "seeded one-shot at the pole" behaviours are RETIRED for this axis; the "one wall prompt" regime is pinned
 * across five radii in the unit tests.
 *
 * <h2>Ordering chain (distance-from-pole, smallest -&gt; largest = pole -&gt; equator)</h2>
 * <pre>
 *   edgeRepromptDist(-1, disabled) &lt; arrivalDist(0) &lt; promptDist(WALL eps) &lt; rearmDist
 * </pre>
 * {@link #resolve} guarantees the chain on every shippable Z radius (the disabled edge-re-prompt sentinel is
 * strictly below the distance-0 arrival, and the re-arm DEAD_ZONE floor keeps {@code rearm > prompt}), and
 * {@link #assertChain} fails loudly on any radius that would tie/invert it.
 *
 * <h2>Effective per-world geometry (blocks from the pole)</h2>
 * <pre>
 *   world (zRadius)       edgeReprompt  arrival  prompt   rearm     regime
 *   Itty-Bitty (3750)        -1.00        0.00     3.00    67.00    one wall prompt
 *   Small-Wide  (7500)       -1.00        0.00     3.00   125.00    one wall prompt
 *   Regular-Wide(10000)      -1.00        0.00     3.00   166.67    one wall prompt
 * </pre>
 * (edgeReprompt = the disabled sentinel -1 on every size; arrival = 90 deg = distance 0; prompt = the WALL
 * epsilon 3; rearm = max(1.5 deg, prompt + DEAD_ZONE 64): Itty 62.5 floored to 67, Small/Regular pure degree
 * 125 / 166.67 since they exceed the 67 floor.)
 */
public final class PoleGeometry {

    private PoleGeometry() {
    }

    // ---- latitude-degree anchors (degrees from world center; the pole is 90) ----

    /** Where the crossing drops the arriving player: the pole LINE itself (90 deg, S16(c), was 89.5), on the far
     *  meridian -- the deepest point of the far side's blizzard (~6 HP/s raw). The escape trek is now 90 -&gt; 88.
     *  The only reprieve is the ~45-tick post-crossing cold GRACE ({@code PoleCrossingGrace}, the arrival-curtain
     *  window) so a low-health crosser cannot die inside the ceremony; the moment it lifts the blizzard owns them.
     *  Its distance-from-pole is 0, so it sits INSIDE the wall prompt line -- self-prompt-freedom is owned by the
     *  SEEDED state, not by geometry (see class doc). Its {@code arrivalAbsZ()} equals {@code zRadius} (the pole
     *  line), so the crossing teleports the player ON the line; the S2 hard-stop clamp tolerates standing there
     *  (see {@link PoleHardStop#CLAMP_EPSILON}) and only stops OUTWARD motion. */
    public static final double ARRIVAL_DEG_POLE = 90.0;
    /** The passage arm re-arms once the player walks back out past this -- 88.5 deg (1.5 deg out). This is the
     *  strict walk-out line that, together with the SEEDED arrival state, carries the self-prompt law now that the
     *  prompt lives AT the wall (S16(c)). Floored to {@code prompt + }{@link EdgeGeometry#DEAD_ZONE_MIN_BLOCKS}
     *  (the anti-machine-gun walk-out hysteresis). */
    public static final double REARM_DEG_POLE = 88.5;
    /** The crossing prompt opens pressed AGAINST THE WALL -- 90 deg (S16(c), was 89.2). Its raw distance-from-pole
     *  is 0, floored NOT to the 40-block approach floor (meaningless at the wall) but to the tiny
     *  {@link #WALL_PROMPT_DIST_BLOCKS} epsilon band, so the prompt is "at the wall" rather than a 40-block
     *  approach ask. The staged ladder collapses: the old 89.9 near-pole edge-re-prompt merges into this one wall
     *  prompt (there is no point poleward of the wall to re-offer at), and the gesture re-prompt (right-click
     *  facing the pole) still fires only inside this same tiny band. */
    public static final double PROMPT_DEG_POLE = 90.0;

    /** S16(c) THE WALL-PROMPT EPSILON (blocks): the prompt opens when {@code distanceToPole <= this} -- pressed
     *  against the pole wall/clamp. Chosen at 3 from the design's "~2-4 block" band: large enough to clear
     *  per-tick position jitter and 1-block standing granularity at the wall (the hard-stop clamp pins the player
     *  within ~1 block of the line, giving {@code distanceToPole} of 0-1), small enough that only a player at the
     *  wall -- not one merely deep in the blizzard -- is offered the crossing. Replaces the 40-block approach
     *  floor {@link EdgeGeometry#PROMPT_MIN_DIST_BLOCKS} the EW edge and the pre-S16 pole prompt used; the re-arm
     *  DEAD_ZONE still floors {@code rearm - prompt >= 64}, so the anti-oscillation hysteresis is unaffected by the
     *  tiny prompt band. */
    public static final double WALL_PROMPT_DIST_BLOCKS = 3.0;

    /** S16(c) DISABLED sentinel for the pole edge-re-prompt distance. The near-pole seeded auto-re-prompt
     *  COLLAPSED into the wall prompt (arrival now lands AT the wall; there is no column poleward of it to
     *  re-offer at). A negative distance can never satisfy the phase machine's {@code distToPole <= edgeRepromptAt}
     *  test ({@link #distanceToPole} clamps {@code >= 0}), so {@link HemispherePassage.Phase#SEEDED_DISARMED}
     *  simply HOLDS (behaves as ordinary DISARMED) until a strict walk-out re-arm past {@link #REARM_DEG_POLE} or
     *  the aim-gated gesture -- exactly the S16(c) "one prompt, at the wall" law. Kept strictly less than
     *  {@code arrivalDist} (0) so the ordering chain {@code edgeReprompt < arrival < prompt < rearm} still holds. */
    public static final double EDGE_REPROMPT_DISABLED_DIST = -1.0;

    /** Blocks per latitude degree for a given intended Z radius: {@code zRadius / 90}. Guarded like
     *  {@link EdgeGeometry#blocksPerDegree} so a degenerate radius can never divide the anchors to nonsense. */
    public static double blocksPerDegree(double zRadiusIntended) {
        return Math.max(1.0, zRadiusIntended) / 90.0;
    }

    /** Distance-from-pole (blocks) for a latitude-degree anchor at a given intended Z radius:
     *  {@code (90 - deg) * blocksPerDegree}. */
    public static double distForDeg(double deg, double zRadiusIntended) {
        return (90.0 - deg) * blocksPerDegree(zRadiusIntended);
    }

    /**
     * Distance (blocks, {@code >= 0}) from world-Z {@code z} to the nearest N/S pole, measured against the
     * INTENDED Z radius (the synced latitude radius, never a live/lerping border half):
     * {@code max(0, zRadiusIntended - |z - centerZ|)}. Sibling of {@link EdgeGeometry#distanceToEdge}. Clamps to
     * 0 for a player AT or BEYOND the pole line (|z| &gt;= zRadius on a Wide world), so a beyond-the-line survivor
     * pressed against the hard-stop clamp is still eligible for the prompt (the mercy exit). NaN {@code z}
     * propagates to NaN, which the phase machine treats as "far" (re-arm, never prompt).
     */
    public static double distanceToPole(double zRadiusIntended, double centerZ, double z) {
        return Math.max(0.0, zRadiusIntended - Math.abs(z - centerZ));
    }

    /**
     * The resolved, per-world block geometry: every distance is measured FROM the pole (shrinks toward the pole)
     * and derived purely from the intended Z radius, with the small-world floors applied and the strict ordering
     * chain {@code edgeRepromptDist < arrivalDist < promptDist < rearmDist} guaranteed. An arrival never
     * self-prompts by STATE law (the S2C arrival seeds {@code SEEDED_DISARMED}; the ordinary prompt requires
     * {@code ARMED}) reinforced by the disabled edge-re-prompt -- see the class doc's S16(c) section.
     */
    public record Resolved(double zRadiusIntended,
                           double promptDist,
                           double rearmDist,
                           double arrivalDist,
                           double edgeRepromptDist) {

        /** {@code |z - centerZ|} at which the crossing drops the arriving player (the ARRIVAL_DEG_POLE column:
         *  90 deg = the pole line, S16(c)). {@code == zRadiusIntended} (arrivalDist is 0). Equivalent to
         *  {@code LatitudeMath.zForLatitudeDeg(90, zRadius)} the server uses for the teleport target. */
        public double arrivalAbsZ() {
            return Math.max(0.0, zRadiusIntended - arrivalDist);
        }
    }

    /**
     * Resolve the block geometry for a world of the given intended Z (latitude) radius. Pure function of
     * {@code zRadius} ONLY -- exactly what makes the pole experience immune to a lerping/vandalized live border.
     * Reuses {@link EdgeGeometry}'s floor constants so the DEAD_ZONE walk-out and the readability floors are one
     * source of truth across both axes.
     */
    public static Resolved resolve(double zRadiusIntended) {
        // S16(c): the prompt and the arrival BOTH anchor to the pole line (90 deg = distance 0). The prompt does
        // NOT use the 40-block approach floor (meaningless at the wall) -- it floors to the tiny WALL epsilon so
        // it opens "at the wall". Re-arm still floors DEAD_ZONE past the prompt (anti-machine-gun hysteresis
        // preserved despite the tiny prompt band). There is NO fog band on the pole axis (the polar whiteout
        // already ramps ambient->90), so re-arm floors directly against the prompt.
        double prompt = Math.max(distForDeg(PROMPT_DEG_POLE, zRadiusIntended), WALL_PROMPT_DIST_BLOCKS);
        // Re-arm (88.5 deg): at least DEAD_ZONE past the prompt line (the anti-machine-gun walk-out hysteresis).
        double rearm = Math.max(distForDeg(REARM_DEG_POLE, zRadiusIntended),
                prompt + EdgeGeometry.DEAD_ZONE_MIN_BLOCKS);
        // Arrival (90 deg, S16(c)): the pole line itself, distance 0. It sits INSIDE the wall prompt band; no
        // self-prompt at the arrival column is guaranteed by the SEEDED state (state law), not by geometry.
        double arrival = distForDeg(ARRIVAL_DEG_POLE, zRadiusIntended); // == 0
        // The near-pole seeded edge-re-prompt COLLAPSED into the wall prompt (S16(c)): a DISABLED sentinel that
        // the phase machine can never satisfy (distToPole >= 0 > this), so SEEDED_DISARMED holds. Kept strictly
        // below arrival (0) so the ordering chain still holds.
        double edgeReprompt = EDGE_REPROMPT_DISABLED_DIST;

        return new Resolved(zRadiusIntended, prompt, rearm, arrival, edgeReprompt);
    }

    /**
     * Assert the strict ordering chain {@code edgeReprompt < arrival < prompt < rearm} for a resolved geometry
     * (S16(c): edgeReprompt is the disabled sentinel -1, strictly below the distance-0 arrival; the arrival then
     * sits strictly below the wall prompt, which sits strictly below the re-arm line -- one wall prompt, seeded
     * self-prompt-freedom). Loud failure (an {@link IllegalStateException}) on any radius that ties/inverts,
     * mirroring {@link EdgeGeometry}'s hardening discipline; never called on the hot path (test/dev use).
     */
    public static void assertChain(Resolved r) {
        boolean ok = r.edgeRepromptDist() < r.arrivalDist()
                && r.arrivalDist() < r.promptDist()
                && r.promptDist() < r.rearmDist();
        if (!ok) {
            throw new IllegalStateException("PoleGeometry ordering violated for zRadius=" + r.zRadiusIntended()
                    + ": edgeReprompt=" + r.edgeRepromptDist() + " arrival=" + r.arrivalDist()
                    + " prompt=" + r.promptDist() + " rearm=" + r.rearmDist());
        }
    }
}
