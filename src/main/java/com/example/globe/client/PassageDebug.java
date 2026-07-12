package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.core.HemispherePassage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Phase 5 Slice B-5-P3 round 2 -- opt-in diagnostic instrumentation for the Hemisphere Passage arm/prompt
 * state machine, behind {@code -Dlatitude.debugPassage=true} (default off, zero overhead when off: every entry
 * point returns immediately after the flag check, exactly like the existing {@code latitude.debugPolarSnow}
 * pattern). This class NEVER changes passage behavior -- it only READS the machine's state and logs it; it is
 * the separate "instrument, don't fix" approach for the still-unfixed re-arm bug.
 *
 * <p><b>What a log reader gets.</b> One grep-able prefix, {@code [LatPassage]}:
 * <ul>
 *   <li>{@code SNAP} -- once per second while in a globe world: the COMPLETE state vector this tick
 *       (distToEdge, armed, this tick's {@link HemispherePassage#evaluateGated} openPrompt/nextArmed, the
 *       {@code canOpenPrompt} gate + its two components, curtain state, the pending-arrival flag) plus the last
 *       3 arm transitions with wall-clock timestamps.</li>
 *   <li>{@code EVENT} -- as they happen: every arm/disarm transition, every prompt open, every answer sent,
 *       every arrival consumed, every curtain raise/fade. Enough to reconstruct exactly why the machine did or
 *       did not prompt at every moment of a decline -&gt; walk-out -&gt; return session.</li>
 * </ul>
 * All timestamps are {@code System.currentTimeMillis()} (the same wall clock the state machine runs on).
 */
final class PassageDebug {

    private static final String PREFIX = "[LatPassage]";

    /** Ring buffer of the last 3 arm transitions (index cycles 0..2), rendered into every SNAP line. */
    private static final String[] ARM_RING = new String[3];
    private static int armRingCount;
    private static int armRingHead;

    /** 1 Hz throttle for the SNAP line (wall-clock ms of the last snapshot printed). */
    private static long lastSnapshotMs = Long.MIN_VALUE;

    private PassageDebug() {
    }

    /** Fresh read of the toggle each call (per the debugPolarSnow pattern) so it can be flipped without caring
     *  about static-init order; when false, all public entry points below no-op immediately. */
    static boolean enabled() {
        // P3 REPRO STAGING (branch-local): forced ON so the Modrinth-launched TEST jar records the
        // re-arm repro without JVM args (the profile carries none). REVISIT BEFORE MERGE -- restore
        // the property read (and keep the property as the shipped default-off switch).
        return true || Boolean.getBoolean("latitude.debugPassage");
    }

    /** World-switch hygiene: clear the transition ring + throttle so a new world's log starts clean. */
    static void reset() {
        armRingCount = 0;
        armRingHead = 0;
        for (int i = 0; i < ARM_RING.length; i++) {
            ARM_RING[i] = null;
        }
        lastSnapshotMs = Long.MIN_VALUE;
    }

    /**
     * Normal-path per-tick observation (curtain down, arm evaluated this tick): record any arm transition
     * immediately, then emit the throttled SNAP line. {@code deepUnder} / {@code openScreen} are the exact two
     * components that formed {@code canOpenPrompt} at the call site.
     */
    static void snapshot(Minecraft mc, double distToEdge, boolean prevArmed, boolean armed,
                         HemispherePassage.Decision d, boolean deepUnder, Screen openScreen, String curtainState) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (prevArmed != armed) {
            recordArm(prevArmed, armed, distToEdge, "eval", now);
        }
        if (now - lastSnapshotMs < 1000L) {
            return;
        }
        lastSnapshotMs = now;
        boolean canOpen = !deepUnder && openScreen == null;
        GlobeMod.LOGGER.info(
                "{} SNAP dist={} armed={} openPrompt={} nextArmed={} canOpen={} deepUnder={} screen={} "
                        + "curtain={} pendingArrival={} | {}",
                PREFIX, fmt(distToEdge), armed, d.openPrompt(), d.nextArmed(), canOpen, deepUnder,
                screenName(openScreen), curtainState,
                HemispherePassageClientState.peekPendingArrival(), renderRing());
    }

    /**
     * Curtain-up per-tick observation (a crossing is mid-flight, so the arm is NOT evaluated this tick): emit
     * the throttled SNAP line with the eval fields marked N/A. Computes distToEdge itself (debug-only) since the
     * caller returns before it would otherwise be read.
     */
    static void snapshotCurtainUp(Minecraft mc, boolean armed, String curtainState) {
        if (!enabled() || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSnapshotMs < 1000L) {
            return;
        }
        lastSnapshotMs = now;
        double distToEdge = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
        Screen openScreen = mc.gui != null ? mc.gui.screen() : null;
        GlobeMod.LOGGER.info(
                "{} SNAP dist={} armed={} openPrompt=NA nextArmed=NA canOpen=NA deepUnder=NA screen={} "
                        + "curtain={} pendingArrival={} | {} (curtain-up: arm not evaluated this tick)",
                PREFIX, fmt(distToEdge), armed, screenName(openScreen), curtainState,
                HemispherePassageClientState.peekPendingArrival(), renderRing());
    }

    /** An arm/disarm transition that happens OUTSIDE the clientTick eval (arrival disarm, curtain-timeout
     *  disarm): log it + push it into the ring, so the SNAP transition history is complete. */
    static void armTransition(boolean from, boolean to, double distToEdge, String reason) {
        if (!enabled() || from == to) {
            return;
        }
        recordArm(from, to, distToEdge, reason, System.currentTimeMillis());
    }

    static void onPromptOpen(double distToEdge, boolean beyondEast) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT promptOpen dist={} beyond={} t={}",
                PREFIX, fmt(distToEdge), beyondEast ? "East" : "West", System.currentTimeMillis());
    }

    static void onAnswer(boolean cross) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT answer cross={} t={}", PREFIX, cross, System.currentTimeMillis());
    }

    static void onArrivalConsumed(int arrivalX, boolean east) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT arrivalConsumed arrivalX={} east={} t={}",
                PREFIX, arrivalX, east, System.currentTimeMillis());
    }

    static void onStaleArrivalDrained() {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT staleArrivalDrained (pre-crossing) t={}", PREFIX, System.currentTimeMillis());
    }

    static void onCurtainRaised() {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT curtainRaised t={}", PREFIX, System.currentTimeMillis());
    }

    static void onCurtainFadeOut(String why) {
        if (!enabled()) {
            return;
        }
        GlobeMod.LOGGER.info("{} EVENT curtainFadeOut why={} t={}", PREFIX, why, System.currentTimeMillis());
    }

    // ---- internals -------------------------------------------------------------------------------

    private static void recordArm(boolean from, boolean to, double distToEdge, String reason, long now) {
        String entry = "armed " + from + "->" + to + " @dist " + fmt(distToEdge) + " t=" + now + " (" + reason + ")";
        ARM_RING[armRingHead] = entry;
        armRingHead = (armRingHead + 1) % ARM_RING.length;
        if (armRingCount < ARM_RING.length) {
            armRingCount++;
        }
        GlobeMod.LOGGER.info("{} EVENT {}", PREFIX, entry);
    }

    /** Render the last up-to-3 arm transitions oldest-first, e.g. {@code transitions:[armed false->true ...; ...]}. */
    private static String renderRing() {
        if (armRingCount == 0) {
            return "transitions:[]";
        }
        StringBuilder sb = new StringBuilder("transitions:[");
        int start = (armRingHead - armRingCount + ARM_RING.length) % ARM_RING.length;
        for (int i = 0; i < armRingCount; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(ARM_RING[(start + i) % ARM_RING.length]);
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
