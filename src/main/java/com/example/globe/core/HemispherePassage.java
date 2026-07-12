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
 * <p><b>Asymmetric hysteresis (why it can't oscillate, and why re-arm is a MODEST walk-back).</b> The
 * anti-machine-gun guarantee needs only ONE thing: a re-arm distance far enough past the prompt line that a
 * player cannot cross it by positional jitter. It does NOT need to be tied to the (much wider) visual fog
 * band -- an earlier design set re-arm at 564 (the whole fog band + margin), which meant a player who
 * declined the prompt had to trek 464 blocks inland before it would ever offer again; "I left the area and
 * came back" never re-armed, which read as a hard one-shot-forever bug (TEST 83). The thresholds:
 * <ul>
 *   <li>{@link #PROMPT_AT} (100 blocks, == {@code GlobeClientState.ewWarningStage} stage-2 distance):
 *       when ARMED and {@code distToEdge <= PROMPT_AT}, open the prompt ONCE and DISARM.</li>
 *   <li>Re-arm at {@code distToEdge > }{@link #REARM_AT} (== {@code PROMPT_AT + }{@link #REARM_MARGIN} ==
 *       250 blocks): the player need only walk a modest {@link #REARM_MARGIN} (150) blocks back from the
 *       prompt line before the prompt can arm again -- their intuitive "I left the edge and returned".</li>
 *   <li>{@link #FOG_START} (500 blocks, == {@code GlobeClientState.ewIntensity01} haze ramp start): the
 *       approach fog begins here. This is PURELY visual (a haze onset); it is NOT an arming threshold and
 *       re-arm deliberately sits INSIDE it -- the light outer fog is still visible when the prompt re-arms.</li>
 * </ul>
 * Between {@code PROMPT_AT} (100) and {@code REARM_AT} (250) the arm state is STICKY: a disarmed player who
 * wobbles a few blocks across the prompt line never re-prompts, and an armed player who has not yet reached
 * 100 stays armed without flapping. The 150-block dead band is ~30x any per-tick position jitter, so sliding
 * N/S along the edge (dist roughly constant, small), or hovering right at {@code PROMPT_AT}, can never
 * machine-gun the prompt; only a deliberate 150-block walk-out-and-back re-offers it.
 *
 * <p><b>Spawned-in-band => DISARMED contract.</b> A hemisphere crossing lands the player at the IDENTICAL
 * border distance on the FAR side (mirror-X keeps {@code |x - centerX|}), i.e. still {@code <= PROMPT_AT}.
 * If arrival seeded {@code armed=true} the player would be re-prompted on the very next tick forever. So the
 * S2C arrival seeds {@code armed=false}, and {@link #evaluate} respects it: a disarmed player in-band stays
 * disarmed until they walk back out past {@link #REARM_AT}. This is exactly the same math as the
 * anti-oscillation rule -- the "arrival" is just an externally-seeded disarmed-in-band state.
 *
 * <p>All thresholds are absolute blocks (not fractions): the fog band must fit sanely inside the SMALLEST
 * world. On Itty-Bitty Classic (xRadius 3750) {@link #FOG_START} = 500 is ~13% of the radius and
 * {@link #REARM_AT} = 250 is well inside that, leaving the vast interior prompt-free; {@link HemispherePassageTest}
 * pins that it fits.
 */
public final class HemispherePassage {

    /** Distance-to-edge (blocks) at/below which an ARMED player is prompted, then disarmed. Matches the
     *  {@code ewWarningStage} stage-2 distance so the prompt lands where the edge warning is already loud. */
    public static final double PROMPT_AT = 100.0;

    /** Distance-to-edge (blocks) where the approach fog begins. Matches {@code ewIntensity01}'s 500-block
     *  haze ramp so the new passage fog and the existing EW haze share an onset. PURELY visual -- NOT an
     *  arming threshold, and deliberately WIDER than {@link #REARM_AT} (the arm re-arms inside the light fog). */
    public static final double FOG_START = 500.0;

    /** Re-arm margin beyond {@link #PROMPT_AT}, in blocks: how far back from the prompt line a declined player
     *  must walk before the prompt re-arms. Sized only to beat positional jitter (>> {@code DEAD_ZONE} = 64,
     *  ~30x per-tick jitter), NOT to the visual fog band -- a modest, intuitive "I left the edge and came back"
     *  rather than the old 464-block trek that read as one-shot-forever (TEST 83). */
    public static final double REARM_MARGIN = 150.0;

    /** Distance-to-edge above which a disarmed player re-arms. Asymmetric vs {@link #PROMPT_AT} (100 vs 250):
     *  the 150-block sticky dead band kills oscillation without stranding the player disarmed. */
    public static final double REARM_AT = PROMPT_AT + REARM_MARGIN; // 250

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

    // ---- B-5-P2 approach fog (A2): pure distance->fog curves for the REAL DEPTH FOG mixin ----
    //
    // The approach to the E/W edge is NOT a flat screen tint (Peetsa vetoed that): it drives Minecraft's OWN
    // render-distance fog exactly like {@code FogRendererPolarSetupMixin} does for the pole, so it is depth-
    // correct and wall-aware (a shelter wall two blocks away stays crisp; the exterior seen out a doorway reads
    // heavy). These are the pure, testable curves; the client mixin (FogRendererPassageSetupMixin) does the GL-
    // side FogData mutation + haze-palette tint. The intensity axis is the SAME distance-to-edge the prompt arms
    // on, so the fog and the prompt share one onset ({@link #FOG_START}) and one climax ({@link #PROMPT_AT}).
    //
    // Curve choice -- "a weather front rolling in": ease-IN (exponent > 1) over the band. At {@link #FOG_START}
    // (500 blocks) the fog is barely-there and distant (a haze on the horizon); it thickens STEEPLY into a near-
    // opaque wall as the player closes on the prompt band, so the edge reads as a storm gathering ahead rather
    // than a fog that snaps on. Below {@link #PROMPT_AT} the fraction clamps to 1 (full), so the wall holds
    // through the prompt band right to the edge. Seam-free at/beyond {@link #FOG_START}: every function returns
    // the caller's own vanilla value unchanged, so there is no visible onset ring.

    /** Cylindrical render-distance fog END (blocks) AT the edge: a near-opaque wall leaving ~1 chunk of sight.
     *  A touch more open than the pole's 16 ({@code PolarHazardWindow.POLAR_FOG_END_NEAR}) -- the edge is a
     *  passable weather advisory, not the lethal polar cap, so you can still see the wall you are walking into. */
    public static final float APPROACH_FOG_END_NEAR = 20.0f;
    /** Cylindrical render-distance fog START (blocks) at the edge: fog begins this close, so a wall 2-3 blocks
     *  away is still under START (unfogged) while everything past it fades to haze. */
    public static final float APPROACH_FOG_START_NEAR = 6.0f;
    /** END-curve exponent over the approach progress. {@code > 1} = ease-in (gathering front): thin far out,
     *  thickening steeply toward the edge. */
    public static final double APPROACH_FOG_END_CURVE = 1.6;
    /** START-curve exponent. SMALLER than {@link #APPROACH_FOG_END_CURVE} so START pulls in a little faster than
     *  END and the fog BAND widens as you approach (a deepening haze over distance, not a hard single-range wall). */
    public static final double APPROACH_FOG_START_CURVE = 1.1;

    /** Continuous approach progress in {@code [0,1]}: 0 at/beyond {@link #FOG_START} (500), 1 at/inside
     *  {@link #PROMPT_AT} (100). NaN is treated as "far" (0) so a bad read never fogs the screen. */
    public static double approachProgress(double distToEdge) {
        if (Double.isNaN(distToEdge)) {
            return 0.0;
        }
        double t = (FOG_START - distToEdge) / (FOG_START - PROMPT_AT);
        return t <= 0.0 ? 0.0 : (t >= 1.0 ? 1.0 : t);
    }

    /** Eased END fraction in {@code [0,1]}: 0 at/beyond {@link #FOG_START} (seam-free), 1 at the edge. */
    public static float approachFogEndFraction(double distToEdge) {
        double p = approachProgress(distToEdge);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, APPROACH_FOG_END_CURVE);
    }

    /** Eased START fraction in {@code [0,1]} (faster curve than END so the fog band deepens on approach). */
    public static float approachFogStartFraction(double distToEdge) {
        double p = approachProgress(distToEdge);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, APPROACH_FOG_START_CURVE);
    }

    /** Tightened cylindrical fog END: eases the caller's CURRENT vanilla end toward {@link #APPROACH_FOG_END_NEAR}
     *  as the edge nears. Returns {@code vanillaEnd} unchanged at/beyond {@link #FOG_START} (seam-free) and never
     *  loosens (NEAR is far closer than any real view distance). Easing from the caller's own end keeps the onset
     *  seam-free at every video render distance. */
    public static float approachFogEnd(float vanillaEnd, double distToEdge) {
        float f = approachFogEndFraction(distToEdge);
        if (f <= 0.0f) {
            return vanillaEnd;
        }
        return vanillaEnd + (APPROACH_FOG_END_NEAR - vanillaEnd) * f;
    }

    /** Tightened cylindrical fog START: eases the caller's vanilla start toward {@link #APPROACH_FOG_START_NEAR}
     *  on the faster START curve, then clamps just below the (already tightened) end so {@code START < END} always
     *  holds. Seam-free at/beyond {@link #FOG_START}. */
    public static float approachFogStart(float vanillaStart, float tightenedEnd, double distToEdge) {
        float f = approachFogStartFraction(distToEdge);
        float start = f <= 0.0f ? vanillaStart : vanillaStart + (APPROACH_FOG_START_NEAR - vanillaStart) * f;
        return Math.min(start, tightenedEnd - 1.0f);
    }

    /** Outcome of {@link #evaluate}: open the prompt this tick, and the next arm state to persist. */
    public record Decision(boolean openPrompt, boolean nextArmed) {
    }
}
