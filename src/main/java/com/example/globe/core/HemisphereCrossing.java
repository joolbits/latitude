package com.example.globe.core;

/**
 * Phase 5 Slice B-3 (P2) -- pure crossing/debounce/stack logic for the HEMISPHERE-TITLE channel.
 * Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). One axis-agnostic
 * {@link #evaluate} drives BOTH the N/S title at the equator (Z vs centerZ) and the E/W title at
 * the prime meridian (X vs centerX): the caller feeds a 1-D coordinate + its center and reads back
 * whether a title should FIRE plus the next tracking state. The 0deg,0deg non-overlap rule is served
 * by {@link #composeLines}: the caller keeps one slot per axis in a single shared title channel and
 * asks this to order them (N/S first, then E/W) so the intersection renders ONE stacked title, never
 * two competing single-line titles.
 *
 * <p><b>Debounce / hysteresis</b> (mirrors the pre-P2 in-overlay N/S logic so behavior is unchanged
 * for the equator axis and identical-by-construction for the new meridian axis):
 * <ul>
 *   <li>A dead-zone band around center where {@code sideOf}=0 (unknown) -- sitting on the line never
 *       resolves to a hemisphere, so it can't flip/re-fire tick to tick. Its half-width is the degree-first
 *       {@link #deadZoneBlocks} (TEST 92: ~0.75 deg per axis, jitter-floored/capped), passed IN by the caller
 *       per axis, so the title fires within ~1 deg of the line on every world size instead of a flat 64 blocks
 *       (which was ~3 deg of longitude on the smallest world).</li>
 *   <li>An actual center CROSSING is required ({@code lastObserved} and {@code coord} on opposite
 *       sides of {@code center}), not merely a changed side, so drifting out of the dead zone back to
 *       the same hemisphere never fires.</li>
 *   <li>{@link #MAX_STEP_BLOCKS} teleport guard: a jump larger than this re-seeds without firing.</li>
 *   <li>{@link #COOLDOWN_MS} per-axis re-arm so hovering across the line can't machine-gun titles.</li>
 * </ul>
 *
 * <p><b>Side convention:</b> negative side of center = {@code -1}, positive = {@code +1}, dead zone =
 * {@code 0}. The caller maps sign to a label per axis: Z {@code -1}=North / {@code +1}=South;
 * X {@code -1}=West / {@code +1}=East (matching {@code LatitudeMath.hemisphere}/{@code hemisphereEW},
 * which call {@code z<centerZ}=N and {@code x<centerX}=W).
 */
public final class HemisphereCrossing {

    /** MAX half-width of the on-the-line dead zone, in blocks (matches the pre-P2 EQUATOR_STABLE_DIST). Still
     *  the large-world value and the cap for {@link #deadZoneBlocks}, and the shared anti-machine-gun constant
     *  {@code EdgeGeometry.DEAD_ZONE_MIN_BLOCKS} couples to. TEST 92 made the EFFECTIVE dead zone degree-first
     *  (see {@link #deadZoneBlocks}); this constant is now the CEILING, not the flat value. */
    public static final double DEAD_ZONE_BLOCKS = 64.0;
    /** TEST 92 degree-first dead-zone target: the on-the-line band is this many DEGREES of the relevant axis
     *  wide (clamped to [{@link #DEAD_ZONE_FLOOR_BLOCKS}, {@link #DEAD_ZONE_BLOCKS}]), so the hemisphere title
     *  fires within ~1 deg of the line on every world size instead of the old flat 64 blocks (~3 deg of
     *  longitude on the smallest world). See {@link #deadZoneBlocks}. */
    public static final double DEAD_ZONE_DEG = 0.75;
    /** Jitter floor (blocks) for the degree-first dead zone: even on the tiniest world it never shrinks below
     *  this, so per-tick positional jitter (~2 blocks) can never flip the resolved side and machine-gun the
     *  title. 16 blocks is ~8x jitter -- ample, and still only ~0.77 deg of longitude on xRadius 3750. */
    public static final double DEAD_ZONE_FLOOR_BLOCKS = 16.0;
    /** A single sample step larger than this is treated as a teleport: re-seed, do not fire. SEPARATE from the
     *  dead zone (TEST 92 did NOT touch this jump guard). */
    public static final double MAX_STEP_BLOCKS = 256.0;
    /** Per-axis re-arm after a title fires, in wall-clock ms. */
    public static final long COOLDOWN_MS = 15_000L;

    private HemisphereCrossing() {
    }

    /**
     * TEST 92 degree-first on-the-line dead zone: {@code clamp(DEAD_ZONE_DEG * blocksPerDegree, FLOOR, MAX)}.
     * The old flat {@link #DEAD_ZONE_BLOCKS} (64) was ~3 deg of longitude on the smallest world (xRadius 3750),
     * so the hemisphere title only resolved a side -- and therefore only fired -- once the player was 3 deg
     * past the line. Sizing the band as a small fraction of a degree (with the {@link #DEAD_ZONE_FLOOR_BLOCKS}
     * jitter floor and the {@link #DEAD_ZONE_BLOCKS} cap) fires it within ~1 deg on every world size, on BOTH
     * axes: the caller passes each axis's own blocks-per-degree -- latitude = latitudeRadius/90 (Z / equator),
     * longitude = xRadius/180 (X / prime meridian). {@code min(64, 0.75 deg)} then {@code max(16, ...)}:
     * on large worlds 0.75 deg exceeds 64 blocks so the cap wins (an even tighter title); on tiny worlds the
     * 16-block floor wins; in between it is exactly 0.75 deg.
     *
     * @param blocksPerDegree blocks per one degree of the axis being tracked ({@code radius/deg-span}); a
     *                        non-positive or NaN value degrades to the jitter floor (never a zero/negative band).
     */
    public static double deadZoneBlocks(double blocksPerDegree) {
        if (Double.isNaN(blocksPerDegree) || blocksPerDegree <= 0.0) {
            return DEAD_ZONE_FLOOR_BLOCKS;
        }
        double byDeg = DEAD_ZONE_DEG * blocksPerDegree;
        return Math.max(DEAD_ZONE_FLOOR_BLOCKS, Math.min(DEAD_ZONE_BLOCKS, byDeg));
    }

    /** Side of {@code coord} relative to {@code center}: {@code -1} negative, {@code +1} positive,
     *  {@code 0} inside the [center-deadZone, center+deadZone] dead band. */
    public static int sideOf(double coord, double center, double deadZone) {
        double d = coord - center;
        if (Math.abs(d) < deadZone) {
            return 0;
        }
        return d < 0.0 ? -1 : 1;
    }

    /**
     * Pure transition for one axis. Given the previous tracking state ({@code lastObserved},
     * {@code lastStableSide}, {@code lastFireMs}) and a fresh sample ({@code coord} at wall time
     * {@code nowMs}), returns whether a title should fire and the next tracking state to persist.
     *
     * @param coord         current 1-D position on this axis (Z for N/S, X for E/W)
     * @param center        the axis center (border centerZ / centerX)
     * @param lastObserved  previous observed coord, or {@link Double#NaN} if unseeded
     * @param lastStableSide previous stable side ({@code -1}/{@code +1}), or {@code 0} if unseeded
     * @param nowMs         current wall-clock time (ms)
     * @param lastFireMs    wall time of the last fire on this axis, or {@link Long#MIN_VALUE} if never
     * @param deadZone      dead-band half-width ({@link #DEAD_ZONE_BLOCKS})
     * @param maxStep       teleport guard ({@link #MAX_STEP_BLOCKS})
     * @param cooldownMs    per-axis re-arm ({@link #COOLDOWN_MS})
     */
    public static Result evaluate(double coord, double center,
                                  double lastObserved, int lastStableSide,
                                  long nowMs, long lastFireMs,
                                  double deadZone, double maxStep, long cooldownMs) {
        // Back-compat 9-arg entry: the teleport guard measures against {@code lastObserved} (the same
        // reference used for crossing detection), preserving the original single-reference behavior for
        // every existing caller/test. The banded path uses {@link #evaluateCore} with a SEPARATE raw
        // per-tick reference (see B-4 item 2 fix below).
        return evaluateCore(coord, center, lastObserved, lastObserved, lastStableSide, nowMs, lastFireMs,
                deadZone, maxStep, cooldownMs);
    }

    /**
     * Core transition with a SEPARATE teleport-guard reference. B-4 item 2 root cause: on a genuine walked
     * crossing the caller HOLDS {@code lastObserved} at the last confident (out-of-dead-zone) position while
     * the player traverses the dead band (so the crossing is measured from real hemisphere ground). Using
     * that same held value for the teleport guard inflates the step: on the WIDE 2:1 world a player who
     * establishes a confident sample a few degrees East (e.g. x=+333 at 3degE, latitudeRadius/60-scale
     * bands) and then walks back across the meridian resolves on the far side while {@code lastObserved} is
     * still +333 -- a >256-block "step" that the guard mis-reads as a teleport and SUPPRESSES the crossing.
     * The fix threads a distinct {@code lastRawObserved} (the PREVIOUS TICK's actual coordinate, advanced
     * every sample incl. inside the dead zone) for the guard, so it catches only true one-tick jumps
     * (/tp, dimension change) while a smoothly-walked crossing -- small per-tick raw step -- always fires.
     */
    static Result evaluateCore(double coord, double center,
                               double lastObserved, double lastRawObserved, int lastStableSide,
                               long nowMs, long lastFireMs,
                               double deadZone, double maxStep, long cooldownMs) {
        int side = sideOf(coord, center, deadZone);
        // Only advance the observed sample once we are clearly out of the dead band (or seeding from
        // NaN); inside the band we keep the last confident position so a later crossing is measured
        // from real hemisphere ground, not from a wobble on the line.
        boolean updateSample = Math.abs(coord - center) >= deadZone || Double.isNaN(lastObserved);
        double newObserved = updateSample ? coord : lastObserved;

        // Unseeded observed: seed only.
        if (Double.isNaN(lastObserved)) {
            int seededSide = side != 0 ? side : lastStableSide;
            return new Result(false, seededSide, newObserved);
        }
        // Inside the dead band: cannot resolve a hemisphere; hold the stable side.
        if (side == 0) {
            return new Result(false, lastStableSide, newObserved);
        }
        // Unseeded stable side: seed it, no fire (we have nothing to have crossed from).
        if (lastStableSide == 0) {
            return new Result(false, side, newObserved);
        }
        // Teleport guard: a large ONE-TICK jump (measured against the raw previous coord, not the held
        // confident observed) re-seeds the side without firing. When the raw reference is unseeded (NaN)
        // fall back to the confident observed so seeding never spuriously trips the guard.
        double rawRef = Double.isNaN(lastRawObserved) ? lastObserved : lastRawObserved;
        double step = Math.abs(coord - rawRef);
        if (step > maxStep) {
            return new Result(false, side, newObserved);
        }
        boolean crossed = (lastObserved > center && coord < center)
                || (lastObserved < center && coord > center);
        boolean changed = side != lastStableSide && crossed;
        boolean cooled = lastFireMs == Long.MIN_VALUE || (nowMs - lastFireMs) >= cooldownMs;
        boolean fire = changed && cooled;
        return new Result(fire, side, newObserved);
    }

    /**
     * B-4 anti-spam kind: which title (if any) a banded crossing should show. {@link #FULL} = the big
     * center-screen title (first approach / after leaving the band); {@link #SMALL} = the unobtrusive
     * action-bar message (a re-crossing while still within the band); {@link #NONE} = no crossing fired.
     */
    public enum Fire {
        NONE, FULL, SMALL
    }

    /**
     * B-4 hemisphere-title ANTI-SPAM, revised for PER-HEMISPHERE full titles (Peetsa's round-2 design):
     * EACH SIDE of the line gets the big center-screen title ONCE per visit-episode. We track which sides
     * have already been FULL-announced since the player last LEFT the band ({@code negSideAnnounced} /
     * {@code posSideAnnounced}); crossing INTO a not-yet-announced side while inside {@code band} shows the
     * FULL title (and marks that side announced), crossing back into an already-announced side shows only
     * the small action-bar message, and leaving the band (>= {@code band} from center, either hemisphere)
     * resets BOTH flags so the next visit re-announces both sides. So the canonical sequence
     * cross->FULL(neg side), re-cross->FULL(pos side, its first visit!), third cross->SMALL, leave band->
     * both re-arm. Fixes the round-1 single-flag behavior where the second hemisphere never got its FULL.
     *
     * <p>Layered on the unchanged {@link #evaluateCore} crossing/debounce core: the dead zone,
     * genuine-crossing requirement, per-tick teleport guard and per-axis cooldown all still gate whether
     * ANY message fires; this only decides FULL vs SMALL per side and manages the per-side re-arm.
     *
     * <p>The {@code band} is a per-axis distance the caller derives from the world radius, NOT a magic
     * block count: Z (N/S) = {@code latitudeRadius/30} (== 3 deg of latitude over the 90-deg Z radius),
     * X (E/W) = {@code xRadius/60} (== 3 deg of longitude over the 180-deg X radius).
     *
     * @param lastRawObserved  the previous tick's RAW coordinate (advanced every sample incl. dead zone);
     *                         feeds the teleport guard so a walked crossing across the wide dead band is not
     *                         mistaken for a jump (B-4 item 2). Seed with {@link Double#NaN}.
     * @param negSideAnnounced whether the negative side (N / W) already got its FULL this visit-episode
     * @param posSideAnnounced whether the positive side (S / E) already got its FULL this visit-episode
     * @param band             hysteresis half-width in blocks around {@code center} (see above)
     */
    public static BandedResult evaluateBanded(double coord, double center,
                                              double lastObserved, double lastRawObserved, int lastStableSide,
                                              boolean negSideAnnounced, boolean posSideAnnounced,
                                              long nowMs, long lastFireMs,
                                              double deadZone, double band, double maxStep, long cooldownMs) {
        Result base = evaluateCore(coord, center, lastObserved, lastRawObserved, lastStableSide,
                nowMs, lastFireMs, deadZone, maxStep, cooldownMs);
        Fire kind = Fire.NONE;
        boolean nextNeg = negSideAnnounced;
        boolean nextPos = posSideAnnounced;
        if (base.fire()) {
            int side = base.newStableSide(); // the side we just crossed INTO
            boolean announced = side < 0 ? negSideAnnounced : posSideAnnounced;
            if (!announced) {
                kind = Fire.FULL;
                if (side < 0) {
                    nextNeg = true;
                } else {
                    nextPos = true;
                }
            } else {
                kind = Fire.SMALL; // this side already got its FULL this episode
            }
        }
        // Leaving the band (either hemisphere) re-arms BOTH sides for the next visit-episode. Evaluated on
        // the raw current distance so a player who wanders >= 3 deg off the line re-announces both sides.
        if (Math.abs(coord - center) >= band) {
            nextNeg = false;
            nextPos = false;
        }
        // Raw reference always advances to the current position (every tick, even inside the dead zone or
        // on a teleport) so the guard next tick measures the true one-tick step.
        return new BandedResult(kind, base.newStableSide(), base.newObserved(), coord, nextNeg, nextPos);
    }

    /**
     * The 0deg,0deg stack rule: order the two per-axis title lines into a single title's line list,
     * N/S first then E/W, dropping nulls. Zero lines -> empty (nothing to render); one -> a single-line
     * title; both (crossings within the same display window) -> ONE stacked two-line title. Never
     * returns duplicated or reordered lines, so the shared channel can't render two competing titles.
     */
    public static String[] composeLines(String northSouthLine, String eastWestLine) {
        boolean hasNs = northSouthLine != null && !northSouthLine.isEmpty();
        boolean hasEw = eastWestLine != null && !eastWestLine.isEmpty();
        if (hasNs && hasEw) {
            return new String[] {northSouthLine, eastWestLine};
        }
        if (hasNs) {
            return new String[] {northSouthLine};
        }
        if (hasEw) {
            return new String[] {eastWestLine};
        }
        return new String[0];
    }

    /** Outcome of {@link #evaluate}: whether to fire a title plus the next tracking state to store. */
    public record Result(boolean fire, int newStableSide, double newObserved) {
    }

    /**
     * Outcome of {@link #evaluateBanded}: which title kind to show plus the next tracking state to persist
     * for this axis -- the confident observed ({@code newObserved}), the raw per-tick guard reference
     * ({@code newRawObserved}), and the two per-side FULL-announced flags for the next tick.
     */
    public record BandedResult(Fire fire, int newStableSide, double newObserved, double newRawObserved,
                               boolean nextNegSideAnnounced, boolean nextPosSideAnnounced) {
    }
}
