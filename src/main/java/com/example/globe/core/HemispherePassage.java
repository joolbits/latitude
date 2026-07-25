package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage) -- pure re-arm / band state logic + approach-fog curves for the
 * E/W-edge crossing prompt. Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). Sibling
 * of {@link HemisphereCrossing}, but a DIFFERENT shape: {@code HemisphereCrossing} models crossing the CENTER
 * line (equator / prime meridian) to fire a title; this models arming a one-shot PROMPT as the player
 * approaches the OUTER X edge (the antimeridian, {@code dist -> 0}). There is no "crossing" here, only
 * distance-to-edge with asymmetric hysteresis, so {@code HemisphereCrossing.evaluateBanded} does not fit.
 *
 * <p><b>Degree-anchored, intended-radius geometry (redesign 2026-07-12).</b> The trigger distances
 * ({@code promptAt}, {@code rearmAt}, {@code rampStart}, {@code climax}) are NO LONGER hardcoded blocks here.
 * They are resolved PER WORLD from longitude degrees against the mod's INTENDED X radius by
 * {@link EdgeGeometry#resolve}, and passed IN to every method below. This does two things Peetsa's TEST-86
 * flight recorder demanded: (1) the lines are anchored to the intended radius, so a lerping/vandalized live
 * border can never slide them; (2) the whole experience begins at the ~176-deg advisory (edge-flow rework)
 * leading a ~177.5-deg fog onset instead of everything starting near ~170, so a crossing lands you in the
 * thinning fog edge (178 deg). See {@link EdgeGeometry} for the anchors, floors and ordering chain.
 *
 * <p><b>The distance axis.</b> {@code distToEdge} is blocks from the player to the nearest E/W world-border
 * edge, a non-negative value that shrinks to 0 AT the edge. On both client and server it is now
 * {@code EdgeGeometry.distanceToEdge(xRadiusIntended, centerX, x)} -- the SAME quantity, computed the SAME
 * way, so the client prompt and the server's {@link #serverAcceptsCross} re-validation cannot disagree.
 *
 * <p><b>Asymmetric hysteresis (why it can't oscillate).</b> The anti-machine-gun guarantee needs only ONE
 * thing: a re-arm distance ({@code rearmAt}) far enough past the prompt line ({@code promptAt}) that a player
 * cannot cross it by positional jitter. {@link EdgeGeometry} floors {@code rearmAt - promptAt} at the 64-block
 * DEAD_ZONE, ~30x per-tick jitter, on every world size. Between {@code promptAt} and {@code rearmAt} the arm
 * is STICKY; only a deliberate walk-out-and-back re-offers the prompt (the TEST 83 fix: a MODEST walk-back,
 * not the old whole-fog-band trek).
 *
 * <p><b>Spawned/arrived contract (edge-flow rework: arrival lands ON the prompt line).</b> The crossing drops
 * the arriving player at {@code arrivalDist}, which is now EXACTLY {@code promptDist} ({@code ARRIVAL_DEG ==
 * PROMPT_DEG == 178}), 2 deg from the wall and 0.5 deg inside the fog onset. The S2C arrival ALWAYS seeds the
 * arm DISARMED, and the prompt only ever opens when ARMED, so a player standing on the prompt line disarmed is
 * NEVER self-prompted. Re-arm requires a STRICT walk-out {@code distToEdge > rearmAt} (the 177-deg line, 3 deg
 * out), so an in-band arrival HOLDS disarmed; a player who then walks toward the wall is not re-prompted by the
 * ordinary machine.
 *
 * <p><b>The post-arrival EDGE auto-re-prompt (edge-flow rework, Peetsa's confirmed flow).</b> A just-arrived
 * player is in a DISTINCT state -- {@link Phase#SEEDED_DISARMED} -- not ordinary disarmed. While they remain in
 * the sticky band, walking ALL THE WAY to the wall ({@code distToEdge <= edgeRepromptAt}, ~179.6 deg) re-offers
 * the crossing ONCE, automatically -- no click, no walk-out. Declining consumes the one-shot
 * ({@link Phase#EDGE_PROMPTED}, which behaves as ordinary disarmed) and it does not re-open until a FRESH
 * arrival or the normal walk-out/re-arm cycle. Leaving past the re-arm line from ANY disarmed phase returns to
 * {@link Phase#ARMED} and the ordinary approach prompt fires again at 178. The full machine is
 * {@link #evaluatePhase}; the ordinary boolean {@link #evaluate}/{@link #evaluateGated} are the ARMED/DISARMED
 * sub-cycle it is built to be consistent with.
 */
public final class HemispherePassage {

    /** Server anti-exploit slack (blocks) added to the prompt distance when re-validating a C2S cross: the
     *  player may drift a little between the client opening the prompt and the answer reaching the server, but
     *  a spoofed answer from deep inland (dist >> promptAt + this) is rejected. */
    public static final double SERVER_CROSS_SLACK = 32.0;

    private HemispherePassage() {
    }

    /**
     * Pure per-tick transition of the passage arm off the current distance-to-edge.
     *
     * @param armed       previous arm state (true = a prompt may open when the player reaches the edge)
     * @param distToEdge  blocks to the nearest E/W border edge ({@code >= 0}); NaN is treated as "far"
     *                    (re-arm/no-prompt) so a transient bad read can never trap the player disarmed.
     * @param promptAt    the resolved prompt distance ({@code EdgeGeometry.Resolved.promptDist}).
     * @param rearmAt     the resolved re-arm distance ({@code EdgeGeometry.Resolved.rearmDist}).
     * @return the decision: whether to open the prompt this tick, and the next arm state to persist.
     */
    public static Decision evaluate(boolean armed, double distToEdge, double promptAt, double rearmAt) {
        // NaN guard: an unknown distance behaves like "well outside" -- re-arm, never prompt.
        if (Double.isNaN(distToEdge)) {
            return new Decision(false, true);
        }
        boolean nextArmed = armed;
        // Re-arm only after clearing the sticky band (asymmetric: promptAt < rearmAt).
        if (!nextArmed && distToEdge > rearmAt) {
            nextArmed = true;
        }
        boolean openPrompt = nextArmed && distToEdge <= promptAt;
        if (openPrompt) {
            nextArmed = false; // disarm on open -- one prompt per approach.
        }
        return new Decision(openPrompt, nextArmed);
    }

    /**
     * FULL per-tick resolution including the caller's open-gate, so the client just persists {@code nextArmed}
     * unconditionally and opens the screen iff {@code openPrompt}. This folds the whole arm/prompt control flow
     * ({@link #evaluate} + the two "can I actually open right now?" conditions) into ONE pure, unit-testable
     * function, closing the integration gap that let a live one-shot-forever bug hide behind green {@link #evaluate}
     * tests (the gate used to live only in the Minecraft-coupled caller and was never exercised end-to-end).
     *
     * <p><b>Why re-arm must NOT be gated on terrain.</b> Re-arm is a pure function of <i>distance</i> -- has the
     * player left the {@code rearmAt} sticky band? -- and nothing else. The earlier caller froze the ENTIRE
     * machine (skipped {@code evaluate} AND its commit) whenever the player read as deep-underground, which
     * coupled the distance-based re-arm to a per-tick, terrain-dependent classification. Here the arm ALWAYS
     * tracks distance; only the OPEN is gated.
     *
     * @param armed         previous arm state.
     * @param distToEdge    blocks to the nearest E/W edge ({@code >= 0}; NaN treated as "far").
     * @param canOpenPrompt whether the prompt may ACTUALLY open this tick -- true only when the player is on the
     *                      surface AND no other screen is up. When false, an armed in-band player STAYS armed
     *                      (the one-shot is held, not burned) while the re-arm still runs off distance as usual.
     * @param promptAt      the resolved prompt distance.
     * @param rearmAt       the resolved re-arm distance.
     * @return {@code openPrompt} = open the screen now; {@code nextArmed} = the arm state to persist
     *         UNCONDITIONALLY (the caller no longer branches its commit).
     */
    public static Decision evaluateGated(boolean armed, double distToEdge, boolean canOpenPrompt,
                                         double promptAt, double rearmAt) {
        Decision base = evaluate(armed, distToEdge, promptAt, rearmAt);
        if (base.openPrompt() && !canOpenPrompt) {
            // Blocked (underground, or a screen is up): do NOT open and do NOT disarm -- hold the armed one-shot
            // so it fires the instant the block clears. Re-arm cannot apply on this branch (openPrompt implies
            // distToEdge <= promptAt, which is nowhere near rearmAt), so keeping the INPUT armed (true) is exact.
            return new Decision(false, armed);
        }
        // A real open (disarm committed) or any non-open tick (sticky/re-arm committed): persist base as-is.
        return base;
    }

    // ---- edge-flow rework: the arrival-aware PHASE state machine (post-arrival EDGE auto-re-prompt) ----

    /**
     * The passage arm's full state -- a superset of the boolean {@code armed}/{@code disarmed} the ordinary
     * {@link #evaluate} models, extended with the two post-arrival phases the EDGE auto-re-prompt needs.
     * <ul>
     *   <li>{@link #ARMED} -- a normal approach opens the prompt at the prompt line (== {@code armed==true}).</li>
     *   <li>{@link #DISARMED} -- ordinary disarmed after a normal prompt/decline (== {@code armed==false}); no
     *       prompt anywhere in the band; re-arms only on a walk-out past the re-arm line.</li>
     *   <li>{@link #SEEDED_DISARMED} -- the post-arrival state: like DISARMED for the ordinary approach, but it
     *       ALSO carries the one-shot EDGE auto-re-prompt (walking to the wall re-offers the crossing once).</li>
     *   <li>{@link #EDGE_PROMPTED} -- the EDGE one-shot has been consumed; behaves exactly as ordinary DISARMED
     *       (kept distinct only so the transition is legible in the debug log).</li>
     * </ul>
     */
    public enum Phase {
        ARMED, DISARMED, SEEDED_DISARMED, EDGE_PROMPTED
    }

    /** Outcome of {@link #evaluatePhase}: open the prompt this tick, and the next {@link Phase} to persist. */
    public record PhaseDecision(boolean openPrompt, Phase nextPhase) {
    }

    /**
     * FULL arrival-aware per-tick resolution -- the client persists {@code nextPhase} unconditionally and opens
     * the screen iff {@code openPrompt}. A strict superset of {@link #evaluateGated}: the {@link Phase#ARMED} and
     * {@link Phase#DISARMED} phases reproduce {@code evaluateGated(true,..)} / {@code evaluateGated(false,..)}
     * exactly, and the two post-arrival phases add the EDGE auto-re-prompt.
     *
     * <p><b>Re-arm is distance-only and terrain-independent</b> (same contract as {@link #evaluateGated}): a
     * clean walk-out past {@code rearmAt} returns to {@link Phase#ARMED} from ANY phase, and is the ONLY way to
     * leave the sticky band. NaN behaves like "well outside" -> re-arm, never prompt.
     *
     * <p><b>HOLD-not-burn</b> (same contract): when a prompt WOULD open but {@code canOpenPrompt} is false
     * (underground / a screen is up), the phase is HELD (the one-shot is not consumed) so it fires the instant
     * the block clears -- for BOTH the ARMED approach prompt and the SEEDED_DISARMED edge prompt.
     *
     * @param phase          previous phase.
     * @param distToEdge     blocks to the nearest E/W edge ({@code >= 0}; NaN treated as "far").
     * @param canOpenPrompt  whether the prompt may ACTUALLY open this tick (surface AND no other screen up).
     * @param promptAt       the resolved ordinary-approach prompt distance ({@code Resolved.promptDist}).
     * @param rearmAt        the resolved re-arm distance ({@code Resolved.rearmDist}).
     * @param edgeRepromptAt the resolved EDGE auto-re-prompt distance ({@code Resolved.edgeRepromptDist}), hard
     *                       by the wall; only consulted in {@link Phase#SEEDED_DISARMED}.
     */
    public static PhaseDecision evaluatePhase(Phase phase, double distToEdge, boolean canOpenPrompt,
                                              double promptAt, double rearmAt, double edgeRepromptAt) {
        // NaN / bad read: behave like "well outside" -- re-arm, never prompt (parity with evaluate()).
        if (Double.isNaN(distToEdge)) {
            return new PhaseDecision(false, Phase.ARMED);
        }
        // The ONE transition that leaves the sticky band: a clean walk-out past the re-arm line re-arms from ANY
        // phase (and keeps ARMED armed). Distance-only, never gated on terrain.
        if (distToEdge > rearmAt) {
            return new PhaseDecision(false, Phase.ARMED);
        }
        // Inside the sticky band (distToEdge <= rearmAt): the prompt behaviour is per phase.
        switch (phase) {
            case ARMED -> {
                if (distToEdge <= promptAt) {
                    if (canOpenPrompt) {
                        return new PhaseDecision(true, Phase.DISARMED); // open + disarm (one prompt per approach)
                    }
                    return new PhaseDecision(false, Phase.ARMED);       // blocked at the prompt line: HOLD armed
                }
                return new PhaseDecision(false, Phase.ARMED);           // in band, not yet at the prompt line
            }
            case SEEDED_DISARMED -> {
                if (distToEdge <= edgeRepromptAt) {
                    if (canOpenPrompt) {
                        return new PhaseDecision(true, Phase.EDGE_PROMPTED); // the post-arrival edge one-shot
                    }
                    return new PhaseDecision(false, Phase.SEEDED_DISARMED); // blocked at the wall: HOLD the seed
                }
                return new PhaseDecision(false, Phase.SEEDED_DISARMED);     // arrived, not yet at the wall
            }
            default -> {
                // DISARMED / EDGE_PROMPTED: ordinary disarmed -- no prompt anywhere; only the walk-out above
                // re-arms.
                return new PhaseDecision(false, phase);
            }
        }
    }

    /**
     * Server-side anti-exploit gate for a C2S {@code cross=true}: is the sender actually near enough to the
     * edge that a legitimate client prompt could have opened? The server NEVER trusts the client's own
     * arm/prompt state -- it re-derives distance from the player's authoritative position (against the SAME
     * intended X radius the client uses) and accepts only within {@code promptAt} + {@link #SERVER_CROSS_SLACK}.
     *
     * @param distToEdge server-computed blocks to the nearest E/W edge; NaN or negative -> reject.
     * @param promptAt   the resolved prompt distance for this world (same value the client armed on).
     */
    public static boolean serverAcceptsCross(double distToEdge, double promptAt) {
        if (Double.isNaN(distToEdge) || distToEdge < 0.0) {
            return false;
        }
        return distToEdge <= promptAt + SERVER_CROSS_SLACK;
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

    // ---- TEST 92 right-click-at-the-wall re-prompt (Peetsa): re-summon the crossing prompt without the ----
    // ---- walk-out. A disarmed player standing in the prompt band who presses USE while FACING the border ----
    // ---- re-arms the passage so the prompt opens next tick. Pure predicates here; the input read (use key, ----
    // ---- yaw, rate limit, "no screen up") is client glue in HemispherePassageClient. ----

    /** Facing cone half-angle as a cosine: the USE-key gesture only counts when the player's horizontal look
     *  points within 60 deg of straight-OUT toward the nearer E/W wall ({@code cos 60 deg = 0.5}). Tight enough
     *  that a normal right-click aimed at a block/item to the side or inward never hijacks into a re-prompt. */
    public static final double REARM_GESTURE_FACING_MIN_COS = 0.5;

    /** The horizontal look-direction X component for a Minecraft yaw (deg): {@code -sin(yaw)}. +1 = due east
     *  (+X, yaw -90), -1 = due west (-X, yaw +90), 0 = due north/south. Pure; pitch-independent by design (we
     *  only care about the horizontal bearing toward the wall). */
    public static double lookDirX(float yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    /**
     * True when the player's horizontal facing points OUTWARD toward the nearer E/W edge within the given cone.
     * Outward is +X on the east half ({@code x > centerX}) and -X on the west half; on the exact center there is
     * no outward side, so this is false. {@code minCos} is the cosine of the cone half-angle
     * ({@link #REARM_GESTURE_FACING_MIN_COS} = cos 60 deg).
     */
    public static boolean facingOutwardX(double playerX, double centerX, float yawDeg, double minCos) {
        double d = playerX - centerX;
        if (d == 0.0) {
            return false;
        }
        double outwardSign = d > 0.0 ? 1.0 : -1.0;
        return outwardSign * lookDirX(yawDeg) >= minCos;
    }

    /** The horizontal look-direction Z component for a Minecraft yaw (deg): {@code +cos(yaw)} (MC yaw 0 faces
     *  +Z / south). +1 = due south (+Z, yaw 0), -1 = due north (-Z, yaw 180), 0 = due east/west. The Z sibling
     *  of {@link #lookDirX} for the B-7 pole re-prompt gesture; pure, pitch-independent. */
    public static double lookDirZ(float yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }

    /**
     * True when the player's horizontal facing points POLEWARD (toward the nearer N/S pole) within the given
     * cone -- the Z sibling of {@link #facingOutwardX} for the B-7 pole re-prompt gesture. Poleward is -Z on the
     * northern half ({@code z < centerZ}, since {@code N = -Z}) and +Z on the southern half; on the exact
     * equator ({@code z == centerZ}) there is no poleward side, so this is false. {@code minCos} is the cosine of
     * the cone half-angle ({@link #REARM_GESTURE_FACING_MIN_COS} = cos 60 deg), shared with the EW gesture.
     */
    public static boolean facingPolewardZ(double playerZ, double centerZ, float yawDeg, double minCos) {
        double d = playerZ - centerZ;
        if (d == 0.0) {
            return false;
        }
        double polewardSign = d > 0.0 ? 1.0 : -1.0; // toward the nearer pole (outward on Z)
        return polewardSign * lookDirZ(yawDeg) >= minCos;
    }

    /**
     * The TEST 92 re-prompt gesture predicate: should a USE-key press RE-ARM the passage this tick? True only
     * when the arm is currently DISARMED, the player is within the prompt band ({@code distToEdge <= promptAt}),
     * and they are {@code facingOutward} toward the wall. The caller owns the input read, the "no screen / on
     * the surface" gate, and the rate limit; this is the pure decision. Re-arming lets the normal
     * {@link #evaluateGated} open the prompt on the same/next tick (subject to that gate).
     */
    public static boolean rearmGestureArms(boolean armed, double distToEdge, boolean facingOutward,
                                           double promptAt) {
        if (armed || !facingOutward || Double.isNaN(distToEdge)) {
            return false;
        }
        return distToEdge <= promptAt;
    }

    // ---- B-7 P3 gesture AIM GATE (owner, TEST 97): "I was trying to break ice and kept having the prompt ----
    // ---- pop up because I was clicking." A click that TARGETS something (a block being mined, an entity ----
    // ---- being hit) is an ordinary interaction, never a border ask -- only a MISS (air) click may re-arm. ----

    /** Crosshair hit kinds for {@link #rearmGestureArms(boolean, double, boolean, double, int)} -- mirrors
     *  {@code net.minecraft.world.phys.HitResult.Type} (MISS/BLOCK/ENTITY) without an MC import so the aim
     *  gate stays pure/unit-testable. The client shim maps {@code Minecraft.hitResult} onto these. */
    public static final int GESTURE_HIT_MISS = 0;
    /** The crosshair rests on a block (the player is mining/placing/using it) -- gesture suppressed. */
    public static final int GESTURE_HIT_BLOCK = 1;
    /** The crosshair rests on an entity (the player is attacking/using it) -- gesture suppressed. */
    public static final int GESTURE_HIT_ENTITY = 2;

    /**
     * The aim-gated re-prompt gesture predicate (B-7 P3, both axes): identical to
     * {@link #rearmGestureArms(boolean, double, boolean, double)} plus the crosshair AIM GATE -- the gesture
     * only counts when the click targets NOTHING ({@link #GESTURE_HIT_MISS}: air/out-of-reach). A click whose
     * crosshair rests on a block or an entity is an ordinary mine/attack/use and is suppressed, so breaking
     * ice at the wall can never re-summon the prompt. The 4-arg base predicate is untouched (its semantics are
     * pinned by the existing tests); this wrapper is what the client arms (EW and POLE) call.
     */
    public static boolean rearmGestureArms(boolean armed, double distToEdge, boolean facingOutward,
                                           double promptAt, int crosshairHitKind) {
        if (crosshairHitKind != GESTURE_HIT_MISS) {
            return false; // aiming at a block/entity: an ordinary click, never a border ask.
        }
        return rearmGestureArms(armed, distToEdge, facingOutward, promptAt);
    }

    // ---- B-7 P3 arrival legibility (owner, TEST 97): name the far meridian under the pole arrival title ----

    /**
     * The arrival meridian label for the pole-crossing subtitle, e.g. {@code "167°W"} (or {@code "0°"} at the
     * prime meridian). Pure twin of {@code LatitudeMath.formatLongitudeDeg} (same conventions: longitude =
     * {@code |x - centerX| / xRadius * 180} clamped to {@code [0,180]} and rounded; {@code W} = west of center,
     * matching vanilla F3's "Towards negative X (West)" and the HUD) taking primitives so it is unit-testable
     * without a {@code WorldBorder}. A degenerate radius or NaN {@code x} degrades to {@code "0°"}.
     */
    public static String farMeridianLabel(double x, double centerX, double xRadiusIntended) {
        if (!(xRadiusIntended > 0.0) || Double.isNaN(x)) {
            return "0°";
        }
        double norm = Math.min(1.0, Math.abs(x - centerX) / xRadiusIntended);
        int deg = (int) Math.round(norm * 180.0);
        if (deg == 0) {
            return "0°";
        }
        return deg + "°" + (x < centerX ? 'W' : 'E');
    }

    // ---- B-5-P2 approach fog (A2): pure distance->fog curves for the REAL DEPTH FOG mixin ----
    //
    // The approach to the E/W edge is NOT a flat screen tint (Peetsa vetoed that): it drives Minecraft's OWN
    // render-distance fog exactly like {@code FogRendererPolarSetupMixin} does for the pole, so it is depth-
    // correct and wall-aware. These are the pure, testable curves; the client mixin (FogRendererPassageSetupMixin)
    // does the GL-side FogData mutation + haze-palette tint. The intensity axis is the SAME distance-to-edge the
    // prompt arms on, and the onset/climax are now the PER-WORLD {@code rampStart}/{@code climax} from
    // EdgeGeometry (the banner cap is advisoryDist (176 deg) since the staged flow; climax (179 deg) sits one degree poleward of the 178-deg prompt).
    //
    // Curve choice -- "a weather front rolling in": ease-IN (exponent > 1) over the band. At {@code rampStart}
    // the fog is barely-there and distant (a haze on the horizon); it thickens STEEPLY into a near-opaque wall
    // as the player closes on {@code climax}, so the edge reads as a storm gathering ahead rather than a fog that
    // snaps on. At/below {@code climax} the fraction clamps to 1 (full), so the wall holds through the prompt
    // band right to the edge. Seam-free at/beyond {@code rampStart}: every function returns the caller's own
    // vanilla value unchanged, so there is no visible onset ring.

    /** Cylindrical render-distance fog END (blocks) AT the edge: a near-opaque wall leaving ~1 chunk of sight.
     *  A touch more open than the pole's 16 -- the edge is a passable weather advisory, not the lethal polar
     *  cap, so you can still see the wall you are walking into. */
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

    /** Continuous approach progress in {@code [0,1]}: 0 at/beyond {@code rampStart}, 1 at/inside {@code climax}.
     *  NaN is treated as "far" (0) so a bad read never fogs the screen. Degenerate {@code rampStart <= climax}
     *  yields 0 (no band). */
    public static double approachProgress(double distToEdge, double rampStart, double climax) {
        if (Double.isNaN(distToEdge)) {
            return 0.0;
        }
        double span = rampStart - climax;
        if (!(span > 0.0)) {
            return 0.0;
        }
        double t = (rampStart - distToEdge) / span;
        return t <= 0.0 ? 0.0 : (t >= 1.0 ? 1.0 : t);
    }

    /** Eased END fraction in {@code [0,1]}: 0 at/beyond {@code rampStart} (seam-free), 1 at the climax/edge. */
    public static float approachFogEndFraction(double distToEdge, double rampStart, double climax) {
        double p = approachProgress(distToEdge, rampStart, climax);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, APPROACH_FOG_END_CURVE);
    }

    /** Eased START fraction in {@code [0,1]} (faster curve than END so the fog band deepens on approach). */
    public static float approachFogStartFraction(double distToEdge, double rampStart, double climax) {
        double p = approachProgress(distToEdge, rampStart, climax);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, APPROACH_FOG_START_CURVE);
    }

    /** Tightened cylindrical fog END: eases the caller's CURRENT vanilla end toward {@link #APPROACH_FOG_END_NEAR}
     *  as the edge nears. Returns {@code vanillaEnd} unchanged at/beyond {@code rampStart} (seam-free) and never
     *  loosens. Easing from the caller's own end keeps the onset seam-free at every video render distance. */
    public static float approachFogEnd(float vanillaEnd, double distToEdge, double rampStart, double climax) {
        float f = approachFogEndFraction(distToEdge, rampStart, climax);
        if (f <= 0.0f) {
            return vanillaEnd;
        }
        return vanillaEnd + (APPROACH_FOG_END_NEAR - vanillaEnd) * f;
    }

    /** Tightened cylindrical fog START: eases the caller's vanilla start toward {@link #APPROACH_FOG_START_NEAR}
     *  on the faster START curve, then clamps just below the (already tightened) end so {@code START < END} always
     *  holds. Seam-free at/beyond {@code rampStart}. */
    public static float approachFogStart(float vanillaStart, float tightenedEnd, double distToEdge,
                                         double rampStart, double climax) {
        float f = approachFogStartFraction(distToEdge, rampStart, climax);
        float start = f <= 0.0f ? vanillaStart : vanillaStart + (APPROACH_FOG_START_NEAR - vanillaStart) * f;
        return Math.min(start, tightenedEnd - 1.0f);
    }

    /** Outcome of {@link #evaluate}: open the prompt this tick, and the next arm state to persist. */
    public record Decision(boolean openPrompt, boolean nextArmed) {
    }
}
