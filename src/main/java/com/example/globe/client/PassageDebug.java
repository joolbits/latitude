package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.core.HemispherePassage;
import com.example.globe.core.PassageAxis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Phase 5 Slice B-5-P3 / B-7-P2 -- opt-in diagnostic instrumentation for the Hemisphere Passage arm/prompt
 * state machine(s), behind {@code -Dlatitude.debugPassage=true} (default off, zero overhead when off: every
 * entry point returns immediately after the flag check). This class NEVER changes passage behavior -- it only
 * READS the machine's state and logs it (the "instrument, don't fix" flight recorder that solved B-5's re-arm
 * mystery). B-7 P2 makes it PER-AXIS: every line carries an {@code axis=EW}/{@code axis=POLE} tag and each arm
 * keeps its OWN transition ring + SNAP throttle, so an EW and a POLE arm never smear into one log.
 *
 * <p><b>What a log reader gets</b> ({@code [LatPassage]} prefix): {@code SNAP} once per second per arm (the
 * complete state vector + last 3 transitions for that axis) and {@code EVENT} as they happen (every phase
 * transition, prompt open, answer, arrival, curtain raise/fade, pole clamp contact). All timestamps are
 * {@code System.currentTimeMillis()} (the wall clock the state machine runs on).
 */
final class PassageDebug {

    private static final String PREFIX = "[LatPassage]";

    /** Ring buffer of the last 3 phase transitions PER AXIS (index cycles 0..2). */
    private static final String[][] ARM_RING = new String[PassageAxis.values().length][3];
    private static final int[] armRingCount = new int[PassageAxis.values().length];
    private static final int[] armRingHead = new int[PassageAxis.values().length];

    /** 1 Hz throttle for the SNAP line PER AXIS (wall-clock ms of the last snapshot printed). */
    private static final long[] lastSnapshotMs = new long[PassageAxis.values().length];

    static {
        java.util.Arrays.fill(lastSnapshotMs, Long.MIN_VALUE);
    }

    private PassageDebug() {
    }

    /** Fresh read of the toggle each call (per the debugPolarSnow pattern) so it can be flipped without caring
     *  about static-init order; when false, all public entry points below no-op immediately. */
    static boolean enabled() {
        return Boolean.getBoolean("latitude.debugPassage");
    }

    /** World-switch hygiene: clear every axis's transition ring + throttle so a new world's log starts clean. */
    static void reset() {
        for (int a = 0; a < ARM_RING.length; a++) {
            armRingCount[a] = 0;
            armRingHead[a] = 0;
            for (int i = 0; i < ARM_RING[a].length; i++) {
                ARM_RING[a][i] = null;
            }
            lastSnapshotMs[a] = Long.MIN_VALUE;
        }
    }

    /**
     * Normal-path per-tick observation for one arm (curtain down, arm evaluated this tick): record any phase
     * transition immediately, then emit the throttled SNAP line. {@code deepUnder} / {@code openScreen} are the
     * exact two components that formed {@code canOpenPrompt} at the call site.
     */
    static void snapshot(PassageAxis axis, Minecraft mc, double distToEdge, HemispherePassage.Phase prevPhase,
                         HemispherePassage.Phase phase, HemispherePassage.PhaseDecision d, boolean deepUnder,
                         Screen openScreen, String curtainState) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (prevPhase != phase) {
            recordArm(axis, prevPhase, phase, distToEdge, "eval", now);
        }
        int a = axis.ordinal();
        if (now - lastSnapshotMs[a] < 1000L) {
            return;
        }
        lastSnapshotMs[a] = now;
        boolean canOpen = !deepUnder && openScreen == null;
        GlobeMod.LOGGER.info(
                "{} SNAP axis={} dist={} phase={} openPrompt={} nextPhase={} canOpen={} deepUnder={} screen={} "
                        + "curtain={} pendingArrival={} | {}",
                PREFIX, axis, fmt(distToEdge), phase, d.openPrompt(), d.nextPhase(), canOpen, deepUnder,
                screenName(openScreen), curtainState,
                HemispherePassageClientState.peekPendingArrival(), renderRing(axis));
    }

    /**
     * Curtain-up per-tick observation for one arm (a crossing is mid-flight, so the arm is NOT evaluated this
     * tick): emit the throttled SNAP line with the eval fields marked N/A.
     */
    static void snapshotCurtainUp(PassageAxis axis, Minecraft mc, HemispherePassage.Phase phase,
                                  double distToEdge, String curtainState) {
        if (!enabled() || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int a = axis.ordinal();
        if (now - lastSnapshotMs[a] < 1000L) {
            return;
        }
        lastSnapshotMs[a] = now;
        Screen openScreen = mc.gui != null ? mc.gui.screen() : null;
        GlobeMod.LOGGER.info(
                "{} SNAP axis={} dist={} phase={} openPrompt=NA nextPhase=NA canOpen=NA deepUnder=NA screen={} "
                        + "curtain={} pendingArrival={} | {} (curtain-up: arm not evaluated this tick)",
                PREFIX, axis, fmt(distToEdge), phase, screenName(openScreen), curtainState,
                HemispherePassageClientState.peekPendingArrival(), renderRing(axis));
    }

    /** A phase transition that happens OUTSIDE the clientTick eval (arrival seed, curtain-timeout disarm,
     *  re-arm gesture): log it + push it into the axis's ring, so the SNAP transition history is complete. */
    static void armTransition(PassageAxis axis, HemispherePassage.Phase from, HemispherePassage.Phase to,
                              double distToEdge, String reason) {
        if (!enabled() || from == to) {
            return;
        }
        recordArm(axis, from, to, distToEdge, reason, System.currentTimeMillis());
    }

    static void onPromptOpen(PassageAxis axis, double distToEdge, String beyond) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT promptOpen axis={} dist={} beyond={} t={}",
                PREFIX, axis, fmt(distToEdge), beyond, System.currentTimeMillis());
    }

    static void onAnswer(PassageAxis axis, boolean cross) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT answer axis={} cross={} t={}", PREFIX, axis, cross, System.currentTimeMillis());
    }

    static void onArrivalConsumed(PassageAxis axis, int arrivalX, int arrivalZ, String beyond) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT arrivalConsumed axis={} arrivalX={} arrivalZ={} beyond={} t={}",
                PREFIX, axis, arrivalX, arrivalZ, beyond, System.currentTimeMillis());
    }

    static void onStaleArrivalDrained() {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT staleArrivalDrained (pre-crossing) t={}", PREFIX, System.currentTimeMillis());
    }

    static void onCurtainRaised(PassageAxis axis) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT curtainRaised axis={} t={}", PREFIX, axis, System.currentTimeMillis());
    }

    static void onCurtainFadeOut(PassageAxis axis, String why) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT curtainFadeOut axis={} why={} t={}", PREFIX, axis, why, System.currentTimeMillis());
    }

    /** B-7 S2: the client detected it is pressed against the pole clamp line and spawned the frost cue. */
    static void onPoleClampContact(double distToPole) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT poleClampContact axis={} distToPole={} t={}",
                PREFIX, PassageAxis.POLE, fmt(distToPole), System.currentTimeMillis());
    }

    // ---- internals -------------------------------------------------------------------------------

    private static void recordArm(PassageAxis axis, HemispherePassage.Phase from, HemispherePassage.Phase to,
                                  double distToEdge, String reason, long now) {
        int a = axis.ordinal();
        String entry = "phase " + from + "->" + to + " @dist " + fmt(distToEdge) + " t=" + now + " (" + reason + ")";
        ARM_RING[a][armRingHead[a]] = entry;
        armRingHead[a] = (armRingHead[a] + 1) % ARM_RING[a].length;
        if (armRingCount[a] < ARM_RING[a].length) {
            armRingCount[a]++;
        }
        GlobeMod.LOGGER.info("{} EVENT axis={} {}", PREFIX, axis, entry);
    }

    /** Render the axis's last up-to-3 phase transitions oldest-first. */
    private static String renderRing(PassageAxis axis) {
        int a = axis.ordinal();
        if (armRingCount[a] == 0) {
            return "transitions:[]";
        }
        StringBuilder sb = new StringBuilder("transitions:[");
        int start = (armRingHead[a] - armRingCount[a] + ARM_RING[a].length) % ARM_RING[a].length;
        for (int i = 0; i < armRingCount[a]; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(ARM_RING[a][(start + i) % ARM_RING[a].length]);
        }
        return sb.append(']').toString();
    }

    private static String screenName(Screen s) {
        return s == null ? "null" : s.getClass().getSimpleName();
    }

    private static String fmt(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }
}
