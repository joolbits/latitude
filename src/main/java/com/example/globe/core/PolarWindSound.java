package com.example.globe.core;

/**
 * Phase 5 (polar-experience v2, CD finding F2 / R2) -- pure envelope math for the POLAR WIND SOUND BED.
 * Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). Callers pass absolute latitude
 * in DEGREES ({@code |lat|} in {@code [0,90]}, e.g. from {@code LatitudeMath.absLatDegExact}) and read back
 * a playback volume in {@code [0, MAX_VOLUME]} plus the hysteresis thresholds that govern the looping
 * sound instance's lifecycle.
 *
 * <p><b>The wind bed.</b> A single looping vanilla wind sound ({@code ELYTRA_FLYING}, the classic wind
 * rush) whose volume rises from a breath at 85 deg to a howl near the pole, tracking the SAME 85->90 ramp
 * the ambient snow + whiteout fog use, so the storm you SEE and the storm you HEAR thicken together. The
 * volume envelope is eased (a squared ramp) so 85-87 deg is only a whisper and the gale is concentrated in
 * the last few degrees toward 90.
 *
 * <p><b>Hysteresis.</b> The instance is (re)started once {@code |lat| >= START_DEG} and stops itself only
 * once {@code |lat| < STOP_DEG} -- a 0.5 deg dead band so a player walking the 85 deg line never stutters
 * the loop on/off. Between {@code STOP_DEG} and {@code START_DEG} the loop keeps playing at {@link
 * #MIN_ALIVE_VOLUME} (inaudible, but non-zero so the sound engine does not cull the channel), then either
 * climbs back up or, below {@code STOP_DEG}, stops cleanly.
 *
 * <p>Symmetric about the equator because the caller feeds {@code |lat|}; no time/wall-clock input, so
 * there is nothing to "catch up" -- volume is a pure function of the player's current latitude each tick.
 */
public final class PolarWindSound {

    private PolarWindSound() {
    }

    /** Absolute latitude (deg) at/above which the wind loop is (re)started. Matches the ambient snow onset. */
    public static final double START_DEG = 85.0;
    /** Absolute latitude (deg) below which the running loop stops. Below START by a 0.5 deg hysteresis band. */
    public static final double STOP_DEG = 84.5;
    /** Absolute latitude (deg) at which the wind is at its full howl. Matches the ambient full ceiling. */
    public static final double FULL_DEG = 90.0;

    /** Volume of the howl at the pole (fraction of the source's category volume). Below 1.0 -- a bed, not a blast. */
    public static final float MAX_VOLUME = 0.8f;
    /** Pitch of the loop -- slightly lowered from 1.0 for a lower, more menacing wind than the elytra rush. */
    public static final float PITCH = 0.85f;
    /** Easing exponent for the 85->90 volume ramp. >1 concentrates the loudness near the pole so 85-87 is a whisper. */
    public static final double EASE_EXP = 2.0;
    /** Inaudible floor kept on the LIVE loop while it is alive in the hysteresis band, so the engine never
     *  culls the volume-0 channel and re-triggers the loop (which would defeat the hysteresis). */
    public static final float MIN_ALIVE_VOLUME = 0.0015f;

    /** Linear 85->90 progress in {@code [0,1]}: 0 at/below {@link #START_DEG}, 1 at/above {@link #FULL_DEG}. */
    public static double progress(double absLatDeg) {
        double p = (absLatDeg - START_DEG) / (FULL_DEG - START_DEG);
        if (Double.isNaN(p) || p < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, p);
    }

    /**
     * Eased volume of the wind bed in {@code [0, MAX_VOLUME]}: {@code MAX_VOLUME * progress^EASE_EXP}. 0 at
     * or below 85 deg, a whisper through 85-87 deg (progress 0..0.4, squared to 0..0.16), the full howl at 90.
     */
    public static float volume(double absLatDeg) {
        double p = progress(absLatDeg);
        return (float) (MAX_VOLUME * Math.pow(p, EASE_EXP));
    }

    /**
     * The volume to set on the LIVE looping instance each tick: the eased {@link #volume(double)}, but floored
     * to {@link #MIN_ALIVE_VOLUME} while the loop is still alive (i.e. {@code |lat| >= STOP_DEG}) so a near-0
     * volume in the hysteresis band does not get the channel culled. Below {@link #STOP_DEG} the loop is meant
     * to stop entirely (see {@link #shouldStop}), so this returns the true (possibly 0) volume there.
     */
    public static float liveVolume(double absLatDeg) {
        float v = volume(absLatDeg);
        if (absLatDeg >= STOP_DEG && v < MIN_ALIVE_VOLUME) {
            return MIN_ALIVE_VOLUME;
        }
        return v;
    }

    /** True once the player is poleward enough to (re)start the wind loop. */
    public static boolean shouldStart(double absLatDeg) {
        return absLatDeg >= START_DEG;
    }

    /** True once a running loop should stop (the player has retreated below the hysteresis floor). */
    public static boolean shouldStop(double absLatDeg) {
        return absLatDeg < STOP_DEG;
    }
}
