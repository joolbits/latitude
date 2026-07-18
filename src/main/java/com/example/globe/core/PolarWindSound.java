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
 * <p><b>Shelter.</b> The loop does NOT stop when the player steps under a roof / into a shelter; instead it
 * is MUFFLED to {@link #SHELTERED_VOLUME_SCALE} of its open-air volume (real wind is audible through walls)
 * and returns to full the instant they are sky-exposed again -- see {@link #liveVolume(double, boolean)}.
 * Only true deactivation (off-globe / below the latitude floor / another dimension) silences it.
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
    /**
     * Indoor attenuation for the wind bed. When the player is SHELTERED (no direct sky exposure -- under a
     * roof, in a doorway, inside a structure) the wind is not silenced but MUFFLED to this fraction of its
     * sky-exposed volume: real wind is still clearly audible through walls, just dampened. 0.35 (about a
     * third) keeps the storm present and legible from inside a shelter while reading as distinctly quieter
     * than standing out in the open gale, and it snaps back to full the moment the player is sky-exposed.
     *
     * <p><b>S16(a)(iii) WIND AUDIO (owner, TEST 106 "the storm through a window must read COMPLETE").</b> The
     * window-completion pass audited this driver ({@link com.example.globe.client.PolarWindSoundInstance#tick}
     * calls {@link #liveVolume(double, float)} with the live {@code exposure01}) and it already implements the
     * ask verbatim: the wind is "present but soft -- a storm through walls," muffled to ~0.35x when sheltered
     * (inside the requested 0.3-0.4 window), with a SMOOTH per-tick transition through partial exposure (never
     * a snap). No behaviour change was needed; this is the single dial. */
    public static final float SHELTERED_VOLUME_SCALE = 0.35f;

    /**
     * TEST 78: CONTINUOUS wind-muffle factor for a graded enclosure estimate {@code exposure01} in
     * {@code [0,1]} (see {@link PolarExposure}). Blends between full open-air volume and the sheltered floor:
     * {@code SHELTERED_VOLUME_SCALE + (1 - SHELTERED_VOLUME_SCALE) * exposure01}. So exposure 1.0 -> 1.0 (full
     * howl, matches the old sky-exposed case), exposure 0.0 -> {@link #SHELTERED_VOLUME_SCALE} (0.35, the
     * existing sealed-room floor, unchanged), and a partial exposure (an open doorway, or under Peetsa's arch
     * ~0.9) blends between -- no longer a hard 1.0/0.35 step off a single overhead block.
     */
    public static float windMuffleFactor(float exposure01) {
        float e = exposure01 < 0f ? 0f : (exposure01 > 1f ? 1f : exposure01);
        return SHELTERED_VOLUME_SCALE + (1.0f - SHELTERED_VOLUME_SCALE) * e;
    }

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
     * The volume to set on the LIVE looping instance each tick when the player is SKY-EXPOSED: the eased
     * {@link #volume(double)}, floored to {@link #MIN_ALIVE_VOLUME} while the loop is alive so a near-0 volume
     * in the hysteresis band does not get the channel culled. Convenience alias for {@code liveVolume(lat, true)}.
     */
    public static float liveVolume(double absLatDeg) {
        return liveVolume(absLatDeg, true);
    }

    /**
     * The volume to set on the LIVE looping instance each tick, given the player's latitude AND whether they
     * currently have sky exposure. Sky-exposed ({@code surfaceExposed == true}): the full eased
     * {@link #volume(double)}. Sheltered ({@code false}): that volume scaled by {@link #SHELTERED_VOLUME_SCALE}
     * -- muffled, never silenced -- so the storm is still heard through the walls. In BOTH cases the result is
     * floored to {@link #MIN_ALIVE_VOLUME} while the loop is alive ({@code |lat| >= STOP_DEG}) so a near-0
     * volume never gets the channel culled; below {@link #STOP_DEG} the loop is meant to stop (see
     * {@link #shouldStop}), so the true (possibly 0) volume is returned there.
     */
    public static float liveVolume(double absLatDeg, boolean surfaceExposed) {
        return liveVolume(absLatDeg, surfaceExposed ? 1.0f : 0.0f);
    }

    /**
     * TEST 78 continuous overload: the live loop volume for a graded enclosure estimate {@code exposure01}
     * (see {@link PolarExposure}). Eased {@link #volume(double)} scaled by {@link #windMuffleFactor(float)},
     * then floored to {@link #MIN_ALIVE_VOLUME} while the loop is alive ({@code |lat| >= STOP_DEG}) so a near-0
     * volume never gets the channel culled; below {@link #STOP_DEG} the loop is meant to stop and the true
     * (possibly 0) volume is returned. The boolean overload is exactly this with {@code exposure01 == 1.0}
     * (exposed) or {@code 0.0} (sheltered), so the old 1.0/0.35 endpoints are preserved.
     */
    public static float liveVolume(double absLatDeg, float exposure01) {
        float v = volume(absLatDeg) * windMuffleFactor(exposure01);
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
