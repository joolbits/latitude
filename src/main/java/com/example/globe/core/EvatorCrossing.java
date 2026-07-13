package com.example.globe.core;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P2 -- the SILENT crossing's pure per-tick state machine. Zero Minecraft
 * imports (Core Logic layer, unit-testable in a plain JVM). The {@link HemispherePassage} shape discipline:
 * distance-to-edge in, one-shot decisions + next state out; the Minecraft-coupled caller (the
 * {@code GlobeMod.borderUxTick} per-player loop) persists the returned {@link State} verbatim and performs the
 * side effects ({@code loadSpawnTargetChunkRing} pre-warm / {@code crossHemisphereMomentum} teleport).
 *
 * <p><b>No prompt, no curtain, no title -- SILENT is the feature.</b> Where B-5's machine arms a consensual
 * PROMPT, this machine arms two silent one-shots per approach, both driven off the SAME degree-anchored
 * {@link MirrorGeometry} lines the mirror band itself is built from (so the trigger can never fire outside
 * mirrored terrain):
 * <ul>
 *   <li><b>PRE-WARM</b> ({@link MirrorGeometry#PRE_WARM_DEG} 177.0): fires ONCE per approach as the player
 *       crosses the pre-warm line inbound -- the caller force-loads the destination 3x3 ring at the mirrored
 *       target so the teleport's far side is hot. Never per-tick: the one-shot disarms on fire and re-arms only
 *       on a genuine walk-out.</li>
 *   <li><b>TRIGGER</b> ({@link MirrorGeometry#TRIGGER_DEG} 179.5): fires ONCE per approach as the player meets
 *       the wall -- the caller performs the momentum-preserving mirror teleport. The REAL can't-outrun-it
 *       guarantee (design amendment 10) is the HELD one-shot itself: an armed trigger stays fire-eligible at
 *       EVERY distance from {@code triggerAt} down to 0, so a tick skipped at elytra speed just fires a few
 *       blocks deeper in the band, still inside mirrored terrain. The vanilla border at 180 backs this up by
 *       halting further progress -- but it is a damage boundary, not solid collision, so the border alone is
 *       never what catches a fast player; the held one-shot is.</li>
 * </ul>
 *
 * <p><b>Re-arm hysteresis (the anti-bounce guarantee).</b> Both one-shots re-arm ONLY when the player leaves
 * past the pre-warm line ({@code distToEdge > preWarmAt}). The sticky band between the trigger line and the
 * pre-warm line is therefore {@code preWarmAt - triggerDist >=} {@link MirrorGeometry#PRE_WARM_MIN_GAP_BLOCKS}
 * (32) blocks on EVERY world size including Itty-Bitty (where both lines floor) -- far beyond positional
 * jitter. The crossing ARRIVAL is what makes this natural: the mirror preserves border distance, so the player
 * lands inside their own trigger band moving INWARD (momentum preserved); the caller seeds the arrived player
 * {@link State#DISARMED}, their natural walk inward crosses the pre-warm line and re-arms both one-shots, and
 * only a deliberate turn-around-and-return re-crosses. A 60-tick server cooldown (the B-5
 * {@code PASSAGE_CROSS_COOLDOWN_TICKS} pattern, held by the caller) is belt-and-suspenders on top.
 *
 * <p><b>Gated fire, held one-shot (the {@code evaluateGated} lesson).</b> {@code canCross} folds the caller's
 * per-tick eligibility (surface-only, alive, not spectator, cooldown clear) into the machine: when the trigger
 * WOULD fire but {@code canCross} is false, the one-shot is HELD (not burned) so it fires the instant the
 * block clears -- and re-arm still runs purely off distance, never gated on terrain (the B-5 TEST-83 class of
 * bug: freezing the whole machine on a terrain classification strands the player).
 *
 * <p><b>Fresh-tracking contract: {@link State#DISARMED}.</b> A player the server has no state for (fresh join,
 * or just crossed) starts DISARMED. For everyone outside the pre-warm line this is invisible -- the first
 * evaluated tick re-arms them. Only a player who logs in ALREADY inside the band stays disarmed until they walk
 * out past the pre-warm line and return -- deliberately: arming a fresh join deep in the trigger band would
 * teleport them INSTANTLY at login, before their client has even settled (accepted trade-off; the walk-out is
 * 32+ blocks on Itty, ~2.5 deg elsewhere).
 *
 * <p>NaN-safe: an unknown distance reads as "far" (re-arm, never fire), same as {@link HemispherePassage}.
 */
public final class EvatorCrossing {

    private EvatorCrossing() {
    }

    /**
     * The per-player arm state the caller persists between ticks. Immutable record; the two booleans are the
     * two independent one-shots of the CURRENT approach episode.
     *
     * @param triggerArmed the silent-teleport one-shot is live
     * @param preWarmArmed the destination-ring pre-load one-shot is live
     */
    public record State(boolean triggerArmed, boolean preWarmArmed) {

        /** Both one-shots live -- the state a re-armed (out-of-band) player carries. */
        public static final State ARMED = new State(true, true);

        /** Both one-shots spent/withheld -- the seed for a fresh tracking entry AND for a player who just
         *  arrived from a crossing (the mirror lands them in-band; walking inward past the pre-warm line
         *  re-arms them naturally). */
        public static final State DISARMED = new State(false, false);
    }

    /**
     * The tick's decisions: fire the pre-warm and/or the crossing NOW, and the state to persist. The caller
     * commits {@code next} UNCONDITIONALLY (all hold/burn/re-arm logic is folded in here), pre-loads the
     * mirrored 3x3 ring iff {@code firePreWarm}, and performs the momentum teleport iff {@code fireCross}.
     */
    public record Outcome(boolean firePreWarm, boolean fireCross, State next) {
    }

    /**
     * Pure per-tick transition off the current distance-to-edge.
     *
     * @param state      the persisted arm state ({@link State#DISARMED} for a fresh tracking entry).
     * @param distToEdge blocks to the nearest E/W border edge ({@code >= 0}), measured against the INTENDED X
     *                   radius (the same {@code EdgeGeometry.distanceToEdge} quantity B-5 uses); NaN reads as
     *                   "far" (re-arm, never fire) so a transient bad read can never fire a teleport.
     * @param canCross   whether the crossing may ACTUALLY fire this tick (surface, alive, not spectator,
     *                   cooldown clear). False HOLDS an armed trigger rather than burning it. Does NOT gate the
     *                   pre-warm (a chunk pre-load is harmless from any terrain) and never gates re-arm.
     * @param triggerAt  the resolved trigger distance ({@link MirrorGeometry.Resolved#triggerDist()}).
     * @param preWarmAt  the resolved pre-warm distance ({@link MirrorGeometry.Resolved#preWarmDist()}), which
     *                   is ALSO the re-arm line (leave past it to re-arm both one-shots).
     */
    public static Outcome evaluate(State state, double distToEdge, boolean canCross,
                                   double triggerAt, double preWarmAt) {
        // NaN guard: an unknown distance behaves like "well outside" -- re-arm, never fire anything.
        if (Double.isNaN(distToEdge)) {
            return new Outcome(false, false, State.ARMED);
        }
        boolean triggerArmed = state.triggerArmed();
        boolean preWarmArmed = state.preWarmArmed();

        // Re-arm: purely off distance, never gated. Leaving past the pre-warm line resets the approach episode.
        if (distToEdge > preWarmAt) {
            return new Outcome(false, false, State.ARMED);
        }

        // Inside the pre-warm line: an armed pre-warm fires exactly once per approach episode.
        boolean firePreWarm = preWarmArmed; // distToEdge <= preWarmAt on this path
        if (firePreWarm) {
            preWarmArmed = false;
        }

        // At/inside the trigger line: an armed trigger fires the silent crossing -- unless the caller's gate is
        // closed, in which case the one-shot is HELD (fires the instant the gate clears; never burned unfired).
        boolean fireCross = false;
        if (triggerArmed && distToEdge <= triggerAt) {
            if (canCross) {
                fireCross = true;
                triggerArmed = false;
            }
            // !canCross: hold triggerArmed as-is (true). Re-arm can't apply here (distToEdge <= triggerAt
            // is nowhere near > preWarmAt), so holding the input state is exact.
        }
        return new Outcome(firePreWarm, fireCross, new State(triggerArmed, preWarmArmed));
    }

    /**
     * P2 sweep refinement -- the pure arrival-Y decision for the silent crossing. The caller (the
     * Minecraft-coupled {@code HemispherePassageService.crossHemisphereMomentum}) PROBES the destination:
     * {@code playerYFreeAtMirror} is true iff the mirror column's blocks at the player's own Y (feet AND head)
     * are loaded air. Then:
     * <ul>
     *   <li><b>Free -&gt; the player's exact Y.</b> Covers BOTH the airborne flyer (keeps altitude -- snapping
     *       an elytra flight to the ground would be a visible slam) AND the common grounded case (the mirror
     *       makes matching heights the norm, so a walker's feet/head at their own Y are air above the mirrored
     *       surface and they arrive mid-stride at their own height).</li>
     *   <li><b>Not free (or unloaded) -&gt; the probed safe surface Y.</b> The rare solid-overhang case: a
     *       player under a non-leaf arch would otherwise pop onto its mirrored roof, and a flyer at the height
     *       of a mirrored floating island would suffocate inside it -- both resolve to the validated surface
     *       instead (a visible-but-SAFE pop, only in these rare cases).</li>
     * </ul>
     * No search, no nudge (amendment 6 unchanged): if even the safe surface probe failed, the caller already
     * no-oped before reaching this decision.
     */
    public static double chooseArrivalY(boolean playerYFreeAtMirror, double safeY, double playerY) {
        return playerYFreeAtMirror ? playerY : safeY;
    }
}
