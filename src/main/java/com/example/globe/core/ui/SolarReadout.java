package com.example.globe.core.ui;

import com.example.globe.core.SolarTilt;

/**
 * The vanilla-clock SOLAR READOUT (HUD Studio round 10 item l): a short line naming the current solar state, shown
 * on the HUD when the player is holding/carrying a clock. A pure Java kernel with ZERO Minecraft imports so the
 * classifier is plain-JVM unit-testable (same discipline as {@link SolarTilt} and {@link HudLayoutMath}); the
 * MC-touching reads (latitude from the world border, the overworld clock time, the {@code SOLAR_TILT_*} dials) all
 * happen at the HUD call site, which derives {@code (signedLatDeg, deltaDeg, hourAngleRad)} and hands them here.
 *
 * <p>It reads the SAME {@link SolarTilt} evaluator the sky renderer and the mob rules read (§8 one-evaluator law),
 * so the readout can never disagree with what the sky is actually doing: {@link SolarTilt#isMidnightSun} /
 * {@link SolarTilt#isPolarNight} decide the two polar regimes, and otherwise the state comes from the live
 * elevation ({@link SolarTilt#solarElevationDeg}) plus which way the sun leans on the NORTH-SOUTH axis
 * ({@link SolarTilt#solarDirection}'s {@code south} component).
 *
 * <p><b>Why north/south only:</b> the design names the "Sun low in the north" family, and the N-S lean is exactly
 * the axis this tilted-pole world makes interesting (a southern observer's sun sits north, a northern observer's
 * sits south, and a polar sun swings low across one of them). The east/west half-of-day distinction rides on the
 * hour-angle SIGN convention (a P2 render concern, not settled for the readout), so it is deliberately omitted
 * rather than shipped possibly-mirrored. Copy is deliberately SHORT (the owner can rename any string at P4); each
 * state carries its own literal so a rename is a one-line edit and every state is explicit in the tests.
 */
public final class SolarReadout {

    private SolarReadout() {
    }

    /** Elevation (deg) at/under which the sun reads as "low" rather than "high". A round, legible cut a touch above
     *  the horizon so a barely-risen sun is "low" and a climbed sun is "high" (owner-tunable at P4). */
    public static final double LOW_ELEVATION_MAX_DEG = 20.0;

    /** The named solar states, each with its short HUD copy. Renaming is a one-line change here. */
    public enum State {
        MIDNIGHT_SUN("Midnight Sun"),
        POLAR_NIGHT("Polar Night"),
        NIGHT("Night"),
        SUN_LOW_NORTH("Sun low in the north"),
        SUN_HIGH_NORTH("Sun high in the north"),
        SUN_LOW_SOUTH("Sun low in the south"),
        SUN_HIGH_SOUTH("Sun high in the south");

        private final String copy;

        State(String copy) {
            this.copy = copy;
        }

        /** The short HUD line for this state. */
        public String copy() {
            return copy;
        }
    }

    /**
     * Classify the current solar state for a signed latitude (NORTH positive, per {@link SolarTilt}), the day's
     * declination δ, and the hour angle H.
     * <ol>
     *   <li>Midnight-sun band ⇒ {@link State#MIDNIGHT_SUN}; polar-night band ⇒ {@link State#POLAR_NIGHT} (both from
     *       {@link SolarTilt}, so the readout matches the sky's own band logic exactly).</li>
     *   <li>Otherwise, sun below the horizon ⇒ {@link State#NIGHT}.</li>
     *   <li>Otherwise, which way the sun leans (the sign of {@link SolarTilt#solarDirection}'s {@code south}
     *       component: {@code >= 0} ⇒ south, {@code < 0} ⇒ north) with a low/high elevation split at
     *       {@link #LOW_ELEVATION_MAX_DEG}.</li>
     * </ol>
     * NaN-safe: any NaN input degrades to {@link State#NIGHT} (the inert, correct-when-off-globe default).
     */
    public static State classify(double signedLatDeg, double deltaDeg, double hourAngleRad) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg) || Double.isNaN(hourAngleRad)) {
            return State.NIGHT;
        }
        if (SolarTilt.isMidnightSun(signedLatDeg, deltaDeg)) {
            return State.MIDNIGHT_SUN;
        }
        if (SolarTilt.isPolarNight(signedLatDeg, deltaDeg)) {
            return State.POLAR_NIGHT;
        }
        double elevation = SolarTilt.solarElevationDeg(signedLatDeg, deltaDeg, hourAngleRad);
        if (Double.isNaN(elevation) || elevation <= SolarTilt.SUN_UP_THRESHOLD_DEG) {
            return State.NIGHT;
        }
        boolean low = elevation <= LOW_ELEVATION_MAX_DEG;
        double south = SolarTilt.solarDirection(signedLatDeg, deltaDeg, hourAngleRad)[2];
        if (south >= 0.0) {
            return low ? State.SUN_LOW_SOUTH : State.SUN_HIGH_SOUTH;
        }
        return low ? State.SUN_LOW_NORTH : State.SUN_HIGH_NORTH;
    }

    /** Convenience: the HUD copy string for {@link #classify}. */
    public static String copy(double signedLatDeg, double deltaDeg, double hourAngleRad) {
        return classify(signedLatDeg, deltaDeg, hourAngleRad).copy();
    }
}
