package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- pure re-arm / band state logic for the E/W-edge crossing
 * prompt. Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). Sibling of
 * {@link HemisphereCrossing}, but a DIFFERENT shape: {@code HemisphereCrossing} models crossing the
 * CENTER line (equator / prime meridian) to fire a title; this models arming a one-shot PROMPT as the
 * player approaches the OUTER X edge (the antimeridian, {@code dist -> 0}). There is no "crossing" here,
 * only distance-to-edge with asymmetric hysteresis, so {@code HemisphereCrossing.evaluateBanded} does not
 * fit (its dead-zone/center-crossing machinery is meaningless for an approach-to-edge arm).
 *
 * <p><b>The distance axis.</b> {@code distToEdge} is blocks from the player to the nearest E/W world-border
 * edge ({@code xRadius - |x - centerX|}), a non-negative value that shrinks to 0 AT the edge. It is the same
 * quantity {@code GlobeClientState.distanceToEwBorderBlocks} already computes client-side, so the client can
 * drive the whole prompt off it with only a C2S answer sent to the server (the server re-validates -- see
 * {@link #serverAcceptsCross}).
 *
 * <p><b>Asymmetric hysteresis (why it can't oscillate).</b> Two thresholds, deliberately far apart:
 * <ul>
 *   <li>{@link #PROMPT_AT} (100 blocks, == {@code GlobeClientState.ewWarningStage} stage-2 distance):
 *       when ARMED and {@code distToEdge <= PROMPT_AT}, open the prompt ONCE and DISARM.</li>
 *   <li>{@link #FOG_START} (500 blocks, == {@code GlobeClientState.ewIntensity01} haze ramp start): the
 *       approach fog begins here; the arm does NOT re-arm here.</li>
 *   <li>Re-arm ONLY at {@code distToEdge > FOG_START + }{@link #REARM_MARGIN} (== 564 blocks): the player
 *       must leave the ENTIRE fog band plus a {@code DEAD_ZONE}-wide margin before the prompt can arm again.</li>
 * </ul>
 * Between {@code PROMPT_AT} (100) and {@code FOG_START + REARM_MARGIN} (564) the arm state is STICKY: a
 * disarmed player who wanders out to 300 and back never re-prompts, and an armed player who has not yet
 * reached 100 stays armed without flapping. So sliding N/S along the edge (dist roughly constant, small),
 * or hovering right at {@code PROMPT_AT}, can never machine-gun the prompt.
 *
 * <p><b>Spawned-in-band => DISARMED contract.</b> A hemisphere crossing lands the player at the IDENTICAL
 * border distance on the FAR side (mirror-X keeps {@code |x - centerX|}), i.e. still {@code <= PROMPT_AT}.
 * If arrival seeded {@code armed=true} the player would be re-prompted on the very next tick forever. So the
 * S2C arrival seeds {@code armed=false}, and {@link #evaluate} respects it: a disarmed player in-band stays
 * disarmed until a full exit past {@code FOG_START + REARM_MARGIN}. This is exactly the same math as the
 * anti-oscillation rule -- the "arrival" is just an externally-seeded disarmed-in-band state.
 *
 * <p>All thresholds are absolute blocks (not fractions): the fog band must fit sanely inside the SMALLEST
 * world. On Itty-Bitty Classic (xRadius 3750) {@code FOG_START + REARM_MARGIN} = 564 is ~15% of the radius,
 * leaving the inner ~85% prompt-free; {@link HemispherePassageTest} pins that it fits.
 */
public final class HemispherePassage {

    /** Distance-to-edge (blocks) at/below which an ARMED player is prompted, then disarmed. Matches the
     *  {@code ewWarningStage} stage-2 distance so the prompt lands where the edge warning is already loud. */
    public static final double PROMPT_AT = 100.0;

    /** Distance-to-edge (blocks) where the approach fog begins. Matches {@code ewIntensity01}'s 500-block
     *  haze ramp so the new passage fog and the existing EW haze share an onset. Not an arming threshold. */
    public static final double FOG_START = 500.0;

    /** Re-arm margin beyond {@link #FOG_START}, in blocks. {@code >= DEAD_ZONE} (64) per the sweeper's band
     *  discipline: the player must clear the whole fog band AND this margin before the prompt re-arms. */
    public static final double REARM_MARGIN = HemisphereCrossing.DEAD_ZONE_BLOCKS; // 64

    /** Distance-to-edge above which a disarmed player re-arms. Asymmetric vs {@link #PROMPT_AT} (100 vs 564). */
    public static final double REARM_AT = FOG_START + REARM_MARGIN; // 564

    /** Server anti-exploit slack (blocks) added to {@link #PROMPT_AT} when re-validating a C2S cross: the
     *  player may drift a little between the client opening the prompt and the answer reaching the server, but
     *  a spoofed answer from deep inland (dist >> this) is rejected. Small vs {@link #FOG_START}. */
    public static final double SERVER_CROSS_SLACK = 32.0;

    private HemispherePassage() {
    }

    /**
     * Pure per-tick transition of the passage arm off the current distance-to-edge.
     *
     * @param armed       previous arm state (true = a prompt may open when the player reaches the edge)
     * @param distToEdge  blocks to the nearest E/W border edge ({@code >= 0}); NaN is treated as "far"
     *                    (re-arm/no-prompt) so a transient bad read can never trap the player disarmed.
     * @return the decision: whether to open the prompt this tick, and the next arm state to persist.
     */
    public static Decision evaluate(boolean armed, double distToEdge) {
        // NaN guard: an unknown distance behaves like "well outside" -- re-arm, never prompt.
        if (Double.isNaN(distToEdge)) {
            return new Decision(false, true);
        }
        boolean nextArmed = armed;
        // Re-arm only after clearing the whole fog band + margin (asymmetric, sticky between 100 and 564).
        if (!nextArmed && distToEdge > REARM_AT) {
            nextArmed = true;
        }
        boolean openPrompt = nextArmed && distToEdge <= PROMPT_AT;
        if (openPrompt) {
            nextArmed = false; // disarm on open -- one prompt per approach.
        }
        return new Decision(openPrompt, nextArmed);
    }

    /**
     * Server-side anti-exploit gate for a C2S {@code cross=true}: is the sender actually near enough to the
     * edge that a legitimate client prompt could have opened? The server NEVER trusts the client's own
     * arm/prompt state -- it re-derives distance from the player's authoritative position and accepts only
     * within {@link #PROMPT_AT} + {@link #SERVER_CROSS_SLACK}.
     *
     * @param distToEdge server-computed blocks to the nearest E/W edge; NaN or negative -> reject.
     */
    public static boolean serverAcceptsCross(double distToEdge) {
        if (Double.isNaN(distToEdge) || distToEdge < 0.0) {
            return false;
        }
        return distToEdge <= PROMPT_AT + SERVER_CROSS_SLACK;
    }

    /**
     * Mirror an X coordinate about the border center: the "same latitude, opposite hemisphere" target keeps
     * Z and reflects X, {@code targetX = 2*centerX - x}. IDENTICAL in Classic and Mercator -- only the range
     * of valid {@code x} differs (Mercator's xRadius is 2x Classic's), never the formula. Reflecting about
     * the (0,0) border center makes it the X border-half geometry, never the Z latitude radius.
     */
    public static double mirrorX(double x, double centerX) {
        return 2.0 * centerX - x;
    }

    /** Outcome of {@link #evaluate}: open the prompt this tick, and the next arm state to persist. */
    public record Decision(boolean openPrompt, boolean nextArmed) {
    }
}
