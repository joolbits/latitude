package com.example.globe.client;

public final class LatitudeClientState {
    private LatitudeClientState() {
    }

    public static boolean firstWorldLoad = false;
    public static long firstWorldLoadStartMs = 0L;
    /** Timestamp (System.currentTimeMillis) when beginExpedition was called. */
    public static long expeditionStartMs = 0L;
    /** Last observed vanilla loading progress (0..1), used across loading-screen handoff. */
    public static volatile float latitudeLoadingProgress = 0f;
    /** Single source of truth for bespoke loading overlay lifecycle. */
    private static volatile boolean latitudeWorldLoading = false;
    /** Latches first client-ready observation to avoid log spam across ticks. */
    private static volatile boolean clientReadyObserved = false;
    /** Monotonic timestamp when terrain/player readiness first became stable. */
    private static volatile long worldReadySinceMs = 0L;
    /** Last elapsed value captured during a lifecycle clear, for post-clear logging. */
    private static volatile long lastLifecycleClearElapsedMs = -1L;

    public static long elapsedSinceExpeditionMs() {
        long startMs = expeditionStartMs > 0L ? expeditionStartMs : firstWorldLoadStartMs;
        return startMs > 0L ? System.currentTimeMillis() - startMs : -1L;
    }

    public static synchronized void beginExpedition(long startMs) {
        expeditionStartMs = startMs;
        latitudeLoadingProgress = 0f;
        latitudeWorldLoading = false;
        clientReadyObserved = false;
        worldReadySinceMs = 0L;
        lastLifecycleClearElapsedMs = -1L;
    }

    public static synchronized void activateLatitudeLoading() {
        latitudeWorldLoading = true;
        latitudeLoadingProgress = 0f;
        clientReadyObserved = false;
        worldReadySinceMs = 0L;
    }

    public static boolean isLatitudeWorldLoading() {
        return latitudeWorldLoading || firstWorldLoad;
    }

    public static synchronized boolean markClientReadyObserved() {
        if (!latitudeWorldLoading || clientReadyObserved) {
            return false;
        }
        clientReadyObserved = true;
        return true;
    }

    public static synchronized long markWorldReadyCandidate(long nowMs) {
        if (!isLatitudeWorldLoading()) {
            worldReadySinceMs = 0L;
            return 0L;
        }
        if (worldReadySinceMs == 0L) {
            worldReadySinceMs = nowMs;
        }
        return nowMs - worldReadySinceMs;
    }

    public static synchronized void resetWorldReadyCandidate() {
        worldReadySinceMs = 0L;
    }

    public static long lastLifecycleClearElapsedMs() {
        return lastLifecycleClearElapsedMs;
    }

    public static synchronized long clearLatitudeLoadingState() {
        long sinceExpedition = elapsedSinceExpeditionMs();
        latitudeWorldLoading = false;
        clientReadyObserved = false;
        worldReadySinceMs = 0L;
        lastLifecycleClearElapsedMs = sinceExpedition;
        expeditionStartMs = 0L;
        latitudeLoadingProgress = 0f;
        firstWorldLoad = false;
        firstWorldLoadStartMs = 0L;
        return sinceExpedition;
    }
}
