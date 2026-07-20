package com.example.globe.core;

/**
 * Phase 5 Slice B-9/B-7 -- Peetsa stipulation S26 (2026-07-20): MUSIC FADES OUT AT THE DAMAGE LINE. Pure
 * envelope + easing math for the polar MUSIC volume multiplier. Zero Minecraft imports (Core Logic layer,
 * unit-testable in a plain JVM). Callers pass absolute latitude in DEGREES ({@code |lat|} in {@code [0,90]})
 * and read back a music-volume factor in {@code [0,1]} to multiply onto the {@code SoundSource.MUSIC}
 * category volume; the MC-coupled parts (reading latitude, reading the shelter predicate, applying the
 * factor at the sound seam) are thin shims in the client layer.
 *
 * <p><b>Owner verbatim (S26).</b> "let's have music fade completely out by the first latitude where damage
 * begins to happen. So only the sound of the wind is present. Music can resume inside caves, but you must
 * have it fade in/out."
 *
 * <p><b>The latitude envelope.</b> The music factor is a smoothstep that eases 1.0 -&gt; 0.0 across the
 * approach band {@link #FADE_START_DEG} -&gt; {@link #FADE_END_DEG}: full music equatorward of the Barrens
 * onset ({@link LatitudeV2Flags#POLAR_BARRENS_ONSET_DEG}, default 82 deg), thinning through the barrens
 * approach, and dying to silence EXACTLY at {@link PolarHazardWindow#FROSTBITE_ONSET_DEG} (85 deg) -- the
 * first latitude where cold damage begins. Poleward of 85 the exposed surface is music-silent so only the
 * wind bed ({@link PolarWindSound}) is heard.
 *
 * <p><b>Shelter / caves override.</b> When the player is genuinely sheltered (the standing B-7 skylight
 * rule, {@link ColdShelter#isSheltered} -- underground / sealed room), the TARGET returns to 1.0 at any
 * latitude, so "music can resume inside caves". The shelter state is fed in as a boolean the client resolves
 * from the SAME predicate the cold path uses ({@code PolarColdClient.isSheltered}); this law does not
 * reimplement it.
 *
 * <p><b>Eased transition (never a hard cut).</b> {@link #ease} moves the live factor toward the target by a
 * frame-rate-independent exponential step, {@code 1 - exp(-deltaTicks / EASE_TAU_TICKS)}. Because the step
 * is exponential, easing once with {@code deltaTicks=N} is IDENTICAL to easing N times with
 * {@code deltaTicks=1} -- so the fade is deterministic regardless of how the caller batches ticks. The time
 * constant {@link #EASE_TAU_TICKS} is 20 ticks (1.0 s at 20 TPS); a perceptually full fade (~4 time
 * constants, ~98%) lands in ~4 seconds, satisfying the "must fade in/out" law in both directions.
 *
 * <p><b>Degenerate inputs degrade to full music, never to silence.</b> A NaN latitude, NaN/negative
 * deltaTicks, or NaN prior factor all resolve to {@link #FULL} (1.0) -- bad data must never silence the
 * game's music. Flag-off ({@code enabled=false}) pins the factor at 1.0 = byte-identical audio.
 */
public final class PolarMusicLaw {

    private PolarMusicLaw() {
    }

    /** Full music (no attenuation). */
    public static final float FULL = 1.0f;
    /** Silent music (wind only). */
    public static final float SILENT = 0.0f;

    /** Absolute latitude (deg) where the music begins to thin -- the Polar Barrens onset (the approach). */
    public static final double FADE_START_DEG = LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG;
    /** Absolute latitude (deg) where the music has fully died -- the first cold-damage rung (frostbite onset). */
    public static final double FADE_END_DEG = PolarHazardWindow.FROSTBITE_ONSET_DEG;

    /** Exponential ease time constant in ticks (20 ticks = 1.0 s @ 20 TPS). A full fade (~4 tau) lands ~4 s. */
    public static final double EASE_TAU_TICKS = 20.0;

    /**
     * The latitude-only music target in {@code [0,1]}, IGNORING shelter: 1.0 at/below {@link #FADE_START_DEG},
     * smoothstep down to 0.0 at/above {@link #FADE_END_DEG}. A NaN latitude degrades to {@link #FULL}.
     */
    public static float latitudeTarget01(double absLatDeg) {
        if (Double.isNaN(absLatDeg)) {
            return FULL;
        }
        double span = FADE_END_DEG - FADE_START_DEG;
        if (!(span > 0.0)) {
            // Degenerate band (mis-tuned constants): fail safe to full music rather than silence.
            return absLatDeg >= FADE_END_DEG ? SILENT : FULL;
        }
        double s = smoothstep01((absLatDeg - FADE_START_DEG) / span);
        return (float) (1.0 - s);
    }

    /**
     * The music target in {@code [0,1]} the live factor eases toward: {@link #FULL} whenever the player is
     * sheltered (caves / sealed room -- "music can resume inside caves"), otherwise the latitude envelope.
     */
    public static float target01(double absLatDeg, boolean sheltered) {
        if (sheltered) {
            return FULL;
        }
        return latitudeTarget01(absLatDeg);
    }

    /**
     * One frame-rate-independent exponential ease step of the live factor toward {@code target}. NaN/negative
     * {@code deltaTicks}, or NaN {@code prevFactor}/{@code target}, degrade to {@link #FULL} (never silence on
     * bad data). Result is clamped to {@code [0,1]}.
     */
    public static float ease(float prevFactor, float target, double deltaTicks) {
        if (Float.isNaN(prevFactor) || Float.isNaN(target) || Double.isNaN(deltaTicks) || deltaTicks < 0.0) {
            return FULL;
        }
        double alpha = 1.0 - Math.exp(-deltaTicks / EASE_TAU_TICKS);
        double next = prevFactor + (target - prevFactor) * alpha;
        return clamp01f((float) next);
    }

    /**
     * Combined entry: the eased music factor for this tick. {@code enabled} is the {@link
     * LatitudeV2Flags#POLAR_BARRENS_ENABLED} damage-line flag -- when false the factor is pinned at
     * {@link #FULL} (byte-identical audio). A NaN {@code prevFactor} is treated as {@link #FULL} before easing.
     */
    public static float musicFactor01(boolean enabled, double absLatDeg, boolean sheltered,
                                      float prevFactor, double deltaTicks) {
        if (!enabled) {
            return FULL;
        }
        // Degenerate WORLD data (NaN latitude / bad delta) SNAPS to FULL immediately -- easing toward full
        // would leave the game near-silent for seconds on bad data; fail-open must be instant. A NaN
        // prevFactor alone (valid world data) merely SUBSTITUTES full as the starting point and eases
        // normally -- the state is bad, the world is fine, so the fade still behaves.
        if (Double.isNaN(absLatDeg) || Double.isNaN(deltaTicks) || deltaTicks < 0.0) {
            return FULL;
        }
        float prev = Float.isNaN(prevFactor) ? FULL : prevFactor;
        return ease(prev, target01(absLatDeg, sheltered), deltaTicks);
    }

    private static double smoothstep01(double t) {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static float clamp01f(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
