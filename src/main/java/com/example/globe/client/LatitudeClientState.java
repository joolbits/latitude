package com.example.globe.client;

public final class LatitudeClientState {
    private LatitudeClientState() {
    }

    public enum AutoCreateWorldProbePhase {
        IDLE,
        WAITING_FOR_CREATE_SCREEN,
        WAITING_FOR_CONFIRM,
        WAITING_FOR_WORLD_OR_BLOCKER,
        WAITING_FOR_POST_ENTRY_CAPTURE,
        TIMED_OUT,
        COMPLETE
    }

    /** Timestamp (System.currentTimeMillis) when beginExpedition was called. */
    public static long expeditionStartMs = 0L;
    /** Last observed vanilla loading progress (0..1), used across loading-screen handoff. */
    public static volatile float latitudeLoadingProgress = 0f;
    /** Single source of truth for bespoke loading overlay lifecycle. */
    private static volatile boolean latitudeWorldLoading = false;
    /** Latches first client-ready observation to avoid log spam across ticks. */
    private static volatile boolean clientReadyObserved = false;
    /** Last elapsed value captured during a lifecycle clear, for post-clear logging. */
    private static volatile long lastLifecycleClearElapsedMs = -1L;
    /** Dev-only create-world route probe phase. */
    private static volatile AutoCreateWorldProbePhase autoCreateWorldProbePhase = AutoCreateWorldProbePhase.IDLE;
    /** Dev-only create-world route probe start time. */
    private static volatile long autoCreateWorldProbeStartMs = 0L;
    /** Dev-only create-world route probe timeout budget. */
    private static volatile long autoCreateWorldProbeTimeoutMs = 45_000L;
    /** Dev-only create-world route probe: open request has been fired this launch. */
    private static volatile boolean autoCreateWorldProbeOpened = false;
    /** Dev-only create-world route probe: confirm has been triggered this launch. */
    private static volatile boolean autoCreateWorldProbeConfirmed = false;
    /** Dev-only create-world route probe: world entry has been observed this launch. */
    private static volatile boolean autoCreateWorldProbeWorldEntered = false;
    /** Dev-only create-world route probe: game time when world entry was first observed. */
    private static volatile long autoCreateWorldProbeWorldEnteredGameTime = -1L;
    /** Dev-only create-world route probe: timeout has been reached. */
    private static volatile boolean autoCreateWorldProbeTimedOut = false;
    /** Dev-only create-world route probe: post-open screen log has been emitted. */
    private static volatile boolean autoCreateWorldProbeLogged = false;
    /** Dev-only create-world route probe: create-world screen detection has been emitted. */
    private static volatile boolean autoCreateWorldProbeScreenDetectedLogged = false;
    /** Dev-only create-world route probe: creative mode override has been applied. */
    private static volatile boolean autoCreateWorldProbeCreativeApplied = false;
    /** Dev-only create-world route probe: post-entry diagnostics have been captured. */
    private static volatile boolean autoCreateWorldProbeDiagnosticsCaptured = false;

    public static long elapsedSinceExpeditionMs() {
        return expeditionStartMs > 0L ? System.currentTimeMillis() - expeditionStartMs : -1L;
    }

    public static synchronized void beginExpedition(long startMs) {
        expeditionStartMs = startMs;
        latitudeLoadingProgress = 0f;
        latitudeWorldLoading = false;
        clientReadyObserved = false;
        lastLifecycleClearElapsedMs = -1L;
    }

    public static synchronized void activateLatitudeLoading() {
        if (expeditionStartMs <= 0L) {
            expeditionStartMs = System.currentTimeMillis();
        }
        latitudeWorldLoading = true;
        latitudeLoadingProgress = 0f;
        clientReadyObserved = false;
    }

    public static boolean isLatitudeWorldLoading() {
        return latitudeWorldLoading;
    }

    public static synchronized boolean markClientReadyObserved() {
        if (!latitudeWorldLoading || clientReadyObserved) {
            return false;
        }
        clientReadyObserved = true;
        return true;
    }

    public static long lastLifecycleClearElapsedMs() {
        return lastLifecycleClearElapsedMs;
    }

    public static synchronized long clearLatitudeLoadingState() {
        long sinceExpedition = elapsedSinceExpeditionMs();
        latitudeWorldLoading = false;
        clientReadyObserved = false;
        lastLifecycleClearElapsedMs = sinceExpedition;
        expeditionStartMs = 0L;
        latitudeLoadingProgress = 0f;
        return sinceExpedition;
    }

    public static synchronized void resetAutoCreateWorldProbe(long timeoutMs) {
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_CREATE_SCREEN;
        autoCreateWorldProbeStartMs = System.currentTimeMillis();
        autoCreateWorldProbeTimeoutMs = timeoutMs > 0L ? timeoutMs : 45_000L;
        autoCreateWorldProbeOpened = false;
        autoCreateWorldProbeConfirmed = false;
        autoCreateWorldProbeWorldEntered = false;
        autoCreateWorldProbeTimedOut = false;
        autoCreateWorldProbeLogged = false;
        autoCreateWorldProbeScreenDetectedLogged = false;
        autoCreateWorldProbeCreativeApplied = false;
        autoCreateWorldProbeWorldEnteredGameTime = -1L;
        autoCreateWorldProbeDiagnosticsCaptured = false;
    }

    public static synchronized AutoCreateWorldProbePhase getAutoCreateWorldProbePhase() {
        return autoCreateWorldProbePhase;
    }

    public static synchronized void setAutoCreateWorldProbePhase(AutoCreateWorldProbePhase phase) {
        autoCreateWorldProbePhase = phase;
    }

    public static synchronized long getAutoCreateWorldProbeStartMs() {
        return autoCreateWorldProbeStartMs;
    }

    public static synchronized long getAutoCreateWorldProbeTimeoutMs() {
        return autoCreateWorldProbeTimeoutMs;
    }

    public static synchronized boolean isAutoCreateWorldProbeOpened() {
        return autoCreateWorldProbeOpened;
    }

    public static synchronized void markAutoCreateWorldProbeOpened() {
        autoCreateWorldProbeOpened = true;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_CREATE_SCREEN;
    }

    public static synchronized boolean isAutoCreateWorldProbeConfirmed() {
        return autoCreateWorldProbeConfirmed;
    }

    public static synchronized void markAutoCreateWorldProbeConfirmed() {
        autoCreateWorldProbeConfirmed = true;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_WORLD_OR_BLOCKER;
    }

    public static synchronized boolean isAutoCreateWorldProbeWorldEntered() {
        return autoCreateWorldProbeWorldEntered;
    }

    public static synchronized void markAutoCreateWorldProbeWorldEntered() {
        autoCreateWorldProbeWorldEntered = true;
        autoCreateWorldProbeWorldEnteredGameTime = -1L;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_POST_ENTRY_CAPTURE;
    }

    public static synchronized void markAutoCreateWorldProbeWorldEntered(long gameTime) {
        autoCreateWorldProbeWorldEntered = true;
        autoCreateWorldProbeWorldEnteredGameTime = gameTime;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_POST_ENTRY_CAPTURE;
    }

    public static synchronized boolean isAutoCreateWorldProbeTimedOut() {
        return autoCreateWorldProbeTimedOut;
    }

    public static synchronized void markAutoCreateWorldProbeTimedOut() {
        autoCreateWorldProbeTimedOut = true;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.TIMED_OUT;
    }

    public static synchronized boolean isAutoCreateWorldProbeLogged() {
        return autoCreateWorldProbeLogged;
    }

    public static synchronized void markAutoCreateWorldProbeLogged() {
        autoCreateWorldProbeLogged = true;
    }

    public static synchronized boolean isAutoCreateWorldProbeScreenDetectedLogged() {
        return autoCreateWorldProbeScreenDetectedLogged;
    }

    public static synchronized void markAutoCreateWorldProbeScreenDetectedLogged() {
        autoCreateWorldProbeScreenDetectedLogged = true;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.WAITING_FOR_CONFIRM;
    }

    public static synchronized boolean isAutoCreateWorldProbeCreativeApplied() {
        return autoCreateWorldProbeCreativeApplied;
    }

    public static synchronized void markAutoCreateWorldProbeCreativeApplied() {
        autoCreateWorldProbeCreativeApplied = true;
    }

    public static synchronized long getAutoCreateWorldProbeWorldEnteredGameTime() {
        return autoCreateWorldProbeWorldEnteredGameTime;
    }

    public static synchronized boolean isAutoCreateWorldProbeDiagnosticsCaptured() {
        return autoCreateWorldProbeDiagnosticsCaptured;
    }

    public static synchronized void markAutoCreateWorldProbeDiagnosticsCaptured() {
        autoCreateWorldProbeDiagnosticsCaptured = true;
        autoCreateWorldProbePhase = AutoCreateWorldProbePhase.COMPLETE;
    }
}
