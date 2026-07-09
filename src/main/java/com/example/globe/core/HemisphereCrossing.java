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
 *   <li>{@link #DEAD_ZONE_BLOCKS} band around center where {@code sideOf}=0 (unknown) -- sitting on
 *       the line never resolves to a hemisphere, so it can't flip/re-fire tick to tick.</li>
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

    /** Half-width of the on-the-line dead zone, in blocks (matches the pre-P2 EQUATOR_STABLE_DIST). */
    public static final double DEAD_ZONE_BLOCKS = 64.0;
    /** A single sample step larger than this is treated as a teleport: re-seed, do not fire. */
    public static final double MAX_STEP_BLOCKS = 256.0;
    /** Per-axis re-arm after a title fires, in wall-clock ms. */
    public static final long COOLDOWN_MS = 15_000L;

    private HemisphereCrossing() {
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
        // Teleport guard: a large jump re-seeds the side without firing.
        double step = Math.abs(coord - lastObserved);
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
}
