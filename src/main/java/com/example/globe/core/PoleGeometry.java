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
 * <h2>The anchors (design {@code phase5-b7-pole-passage-design-20260713.md} §3.2 + the binding S5 tail)</h2>
 * The polar warning ladder (85/87/89/89.7) and every {@code PolarHazardWindow} onset (85/87/87.5/88) are
 * KEEP-SHARED and DO NOT MOVE; B-7's four lines interleave between them:
 * <ul>
 *   <li>{@link #ARRIVAL_DEG_POLE} 89.5 -- where the crossing DROPS the arriving player (0.5 deg from the pole,
 *       DEEP in the far side's blizzard). S5 (owner, 2026-07-13: "cinematic + no damage reprieve -- still
 *       trekking for their lives"): moved 88.0 -&gt; 89.5 so the crosser emerges INTO the mirrored whiteout
 *       (~2.2-2.6 HP/s unprotected under Slowness III) and fights their way OUT -- the frozen-hearts thaw
 *       happens during the escape (~88 deg), not at touchdown. The only reprieve is the post-crossing cold
 *       GRACE ({@link PoleCrossingGrace}, the ~45-tick arrival-curtain window, wired in {@code GlobeMod}) so a
 *       low-health crosser cannot die inside the ceremony.</li>
 *   <li>{@link #REARM_DEG_POLE} 88.5 -- the strict walk-out re-arm line (1.5 deg out); floored to
 *       {@code prompt + DEAD_ZONE 64}.</li>
 *   <li>{@link #PROMPT_DEG_POLE} 89.2 -- the crossing prompt opens (0.8 deg out), 1.2 deg past the 88-deg DANGER (S13c retime)
 *       rung so the ladder line lands FIRST and the prompt is the consensual counter-offer; floored to
 *       {@link EdgeGeometry#PROMPT_MIN_DIST_BLOCKS} 40.</li>
 *   <li>{@link #EDGE_REPROMPT_DEG_POLE} 89.9 -- the SEEDED walk-to-the-pole auto-re-prompt (0.1 deg out, hard by
 *       the pole); floored to {@link EdgeGeometry#EDGE_REPROMPT_MIN_DIST_BLOCKS} 8.</li>
 * </ul>
 *
 * <h2>No fog anchors</h2>
 * The polar whiteout/depth-fog already ramps from the ambient onset to 90 ({@code PolarHazardWindow}, {@code
 * FogRendererPolarSetupMixin}) -- it IS the ceremony fog, free. B-7 adds NO fog anchors and NO fog mixin, so
 * there is no {@code rampStart}/{@code climax} rung here (the fog composition B-5 solved stays solved by adding
 * nothing).
 *
 * <h2>ONE uniform arrival regime (S5 -- the two-regime split is GONE)</h2>
 * Arrival (0.5 deg out) sits INSIDE the prompt line (0.8 deg) and INSIDE the re-arm line (1.5 deg) on EVERY
 * world size (with floors: Itty arrival 20.83 vs prompt 40 vs rearm 104 -- still inside both). So a fresh
 * arrival ALWAYS seeds {@link HemispherePassage.Phase#SEEDED_DISARMED} and HOLDS it: no self-prompt at the
 * arrival column is guaranteed by SEEDING (state law -- the ordinary prompt requires ARMED, and the seeded
 * one-shot fires only at the nearer {@code edgeRepromptDist}), NOT by arrival-outside-prompt geometry. Walking
 * out past 88.5 re-arms (turning back then prompts at 89.2 -- a fresh journey); while still seeded, only the
 * 89.9 one-shot edge re-prompt speaks. The old "prompt &lt; arrival" invariant is RETIRED for this axis; the
 * uniform seeded regime is pinned across five radii in the unit tests (supersedes the dual-regime pins).
 *
 * <h2>Ordering chain (distance-from-pole, smallest -&gt; largest = pole -&gt; equator)</h2>
 * <pre>
 *   edgeRepromptDist &lt; arrivalDist &lt; promptDist &lt; rearmDist
 * </pre>
 * {@link #resolve} guarantees the chain on every shippable Z radius via the SAME floor constants
 * {@link EdgeGeometry} uses (one source of truth), and {@link #assertChain} fails loudly on any sub-shippable
 * radius that would tie/invert it.
 *
 * <h2>Effective per-world geometry (blocks from the pole)</h2>
 * <pre>
 *   world (zRadius)       edgeReprompt  arrival  prompt   rearm     regime
 *   Itty-Bitty (3750)         8.00       20.83    40.00   104.00    seeded (uniform)
 *   Small-Wide  (7500)        8.33       41.67    66.67   130.67    seeded (uniform)
 *   Regular-Wide(10000)      11.11       55.56    88.89   166.67    seeded (uniform)
 * </pre>
 * (Itty: edgeReprompt = 0.1 deg = 4.17 floored to 8; arrival = 0.5 deg = 20.83 UNFLOORED, sitting between the
 * 8 and 40 floors; prompt = 0.8 deg = 33.3 floored to 40; rearm = 1.5 deg = 62.5 floored to prompt+64 = 104.
 * Small: rearm = 1.5 deg = 125 floored to prompt+64 = 130.67. Regular: all pure degrees.)
 */
public final class PoleGeometry {

    private PoleGeometry() {
    }

    // ---- latitude-degree anchors (degrees from world center; the pole is 90) ----

    /** Where the crossing drops the arriving player: 0.5 deg from the pole (89.5 deg), DEEP in the far side's
     *  blizzard (S5: the escape trek IS the arrival experience; the ~45-tick post-crossing grace is the only
     *  reprieve). INSIDE the prompt line by design -- self-prompt-freedom is owned by the SEEDED state, not by
     *  geometry (see class doc). */
    public static final double ARRIVAL_DEG_POLE = 89.5;
    /** The passage arm re-arms once the player walks back out past this -- 88.5 deg (1.5 deg out). Floored to
     *  {@code prompt + }{@link EdgeGeometry#DEAD_ZONE_MIN_BLOCKS} (the anti-machine-gun walk-out). */
    public static final double REARM_DEG_POLE = 88.5;
    /** The crossing prompt opens at/inside this -- 89.2 deg (0.8 deg out), 1.2 deg past the 88-deg DANGER rung (S13c retime) so
     *  the ladder line lands first and the prompt is the counter-offer. Floored to
     *  {@link EdgeGeometry#PROMPT_MIN_DIST_BLOCKS}. */
    public static final double PROMPT_DEG_POLE = 89.2;
    /** The post-arrival auto-re-prompt line -- 89.9 deg (0.1 deg out, hard by the pole). Only reachable in the
     *  {@link HemispherePassage.Phase#SEEDED_DISARMED} state: a just-arrived player who walks all the way to the
     *  pole is re-offered the crossing ONCE. Floored to {@link EdgeGeometry#EDGE_REPROMPT_MIN_DIST_BLOCKS}. */
    public static final double EDGE_REPROMPT_DEG_POLE = 89.9;

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
     * {@code ARMED}), not by geometry -- see the class doc's S5 section.
     */
    public record Resolved(double zRadiusIntended,
                           double promptDist,
                           double rearmDist,
                           double arrivalDist,
                           double edgeRepromptDist) {

        /** {@code |z - centerZ|} at which the crossing drops the arriving player (the ARRIVAL_DEG_POLE column:
         *  89.5 deg = 0.5 deg from the pole). Clamped non-negative for degenerate tiny radii. Equivalent to
         *  {@code LatitudeMath.zForLatitudeDeg(89.5, zRadius)} the server uses for the teleport target. */
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
        // Nearest-to-pole first, flooring and then re-tightening the ordering OUTWARD so no floor can invert it.
        // The prompt (89.2 deg) shares the EW 40-block floor. There is NO fog band on the pole axis (the polar
        // whiteout already ramps ambient->90), so re-arm floors directly against the prompt by the DEAD_ZONE.
        double prompt = Math.max(distForDeg(PROMPT_DEG_POLE, zRadiusIntended),
                EdgeGeometry.PROMPT_MIN_DIST_BLOCKS);
        // Re-arm (88.5 deg): at least DEAD_ZONE past the prompt line (the anti-machine-gun walk-out hysteresis).
        double rearm = Math.max(distForDeg(REARM_DEG_POLE, zRadiusIntended),
                prompt + EdgeGeometry.DEAD_ZONE_MIN_BLOCKS);
        // Arrival (89.5 deg, S5): a pure degree column, NOT floored -- it must sit strictly between the
        // edge-re-prompt line and the prompt line (edgeReprompt < arrival < prompt), which holds on every
        // shippable radius (at 3750: 8 < 20.83 < 40; a tie needs a sub-shippable radius). assertChain catches
        // any radius that would violate it rather than moving real geometry to hide it.
        double arrival = distForDeg(ARRIVAL_DEG_POLE, zRadiusIntended);
        // The post-arrival auto-re-prompt (89.9 deg), hard by the pole, floored clear of the pole line.
        double edgeReprompt = Math.max(distForDeg(EDGE_REPROMPT_DEG_POLE, zRadiusIntended),
                EdgeGeometry.EDGE_REPROMPT_MIN_DIST_BLOCKS);

        return new Resolved(zRadiusIntended, prompt, rearm, arrival, edgeReprompt);
    }

    /**
     * Assert the strict ordering chain {@code edgeReprompt < arrival < prompt < rearm} for a resolved geometry
     * (S5: the old {@code prompt < arrival} self-prompt invariant is RETIRED -- seeding owns self-prompt-freedom;
     * the chain now pins the arrival column strictly between the edge-re-prompt line and the prompt line). Loud
     * failure (an {@link IllegalStateException}) on any sub-shippable radius that ties/inverts, mirroring
     * {@link EdgeGeometry}'s hardening discipline; never called on the hot path (test/dev use).
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
