package com.example.globe.client;

import com.example.globe.util.LatitudeBands;

/**
 * Climate-zone identity and zone-entry notification policy.
 *
 * <p>Two separate questions live here, and conflating them was the defect this class exists to
 * prevent:
 *
 * <ol>
 *   <li><b>Which zone is this?</b> — {@link #segmentFor}. Always the exact band for the latitude.
 *       Zone identity and the displayed zone change AT the real boundary, never before or after it.
 *   <li><b>Should the player be told again?</b> — {@link #shouldAnnounce}. A rate limit on the
 *       full-screen entry title, and nothing more.
 * </ol>
 *
 * <p>An earlier revision applied a two-degree "sticky" buffer to question 1, so the reported zone
 * only flipped once the reading was two degrees past the boundary. That moved the entire transition
 * to 48/52 degrees instead of the real 50, and made the reported zone disagree with the world the
 * player was standing in. The buffer belongs to question 2 alone: the transition always happens on
 * time, and only a repeat ANNOUNCEMENT of it can be withheld.
 */
public final class ZoneTitlePolicy {

    /**
     * Half-width of the "lingering on a boundary" neighbourhood, in degrees of latitude. Applies to
     * notifications only — never to zone identity.
     */
    public static final double REANNOUNCE_BUFFER_DEG = 2.0;

    /** Repeat-notification cooldown in ticks, mirroring the hemisphere title's proven 15s dead time. */
    public static final long REANNOUNCE_COOLDOWN_TICKS = 300L;

    /** A Z step larger than this is a teleport, not travel: always announce, never rate-limit. */
    public static final double TELEPORT_STEP_BLOCKS = 256.0;

    private ZoneTitlePolicy() {
    }

    /**
     * The band index for an absolute latitude — the exact, unbuffered answer.
     *
     * <p>Hemisphere-independent by construction: callers pass an ABSOLUTE latitude, so the northern
     * and southern crossings of a boundary resolve identically.
     */
    public static int segmentFor(double absLatDeg) {
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        int segment = 0; // TROPICAL: lowDeg 0, never itself a boundary to compare against
        for (int i = 1; i < bands.length; i++) {
            if (absLatDeg >= bands[i].lowDeg()) {
                segment = i;
            }
        }
        return segment;
    }

    /**
     * Whether a zone transition that has ALREADY been applied should also be announced.
     *
     * <p>The transition is never in question here — {@link #segmentFor} has already decided it.
     * This governs only whether the full-screen title fires, and exists for one case: loitering on
     * a boundary, where ordinary movement jitter recrosses it repeatedly and would otherwise
     * re-fire the title on every sample.
     *
     * <p>Announces when it is the first zone of the session, when the crossing follows a teleport,
     * when the reading has moved at least {@link #REANNOUNCE_BUFFER_DEG} from where the last
     * announcement fired, or when the cooldown has expired. Suppression therefore requires the
     * player to be BOTH near the last announcement AND inside the cooldown — which is exactly what
     * "lingering on the boundary" means, and nothing else.
     */
    public static boolean shouldAnnounce(
            double absLatDeg,
            long worldTime,
            double lastAnnounceLatDeg,
            long lastAnnounceWorldTime,
            boolean firstZoneOfSession,
            boolean teleported) {
        if (firstZoneOfSession || teleported || Double.isNaN(lastAnnounceLatDeg)) {
            return true;
        }
        boolean nearLastAnnouncement =
                Math.abs(absLatDeg - lastAnnounceLatDeg) < REANNOUNCE_BUFFER_DEG;
        boolean withinCooldown = lastAnnounceWorldTime != Long.MIN_VALUE
                && worldTime - lastAnnounceWorldTime < REANNOUNCE_COOLDOWN_TICKS;
        return !(nearLastAnnouncement && withinCooldown);
    }

    /** True when the Z delta between two consecutive samples is a teleport rather than travel. */
    public static boolean isTeleportStep(double previousZ, double currentZ) {
        return Math.abs(currentZ - previousZ) > TELEPORT_STEP_BLOCKS;
    }
}
