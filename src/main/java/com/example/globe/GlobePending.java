package com.example.globe;

public final class GlobePending {
    private GlobePending() {
    }

    private static volatile String pendingSpawnZone;

    public static volatile boolean startWithCompass = true;

    /**
     * Border radius selected by the bespoke create-world screen. Consumed once
     * by the server on first overworld load and persisted into LatitudeWorldState.
     */
    public static volatile int pendingGlobeRadius = 0;

    /**
     * World shape ("mercator"/"classic") chosen on the create-world screen. Consumed once by the server on
     * first overworld load (GlobeMod.initLatitudeBiomesForWorld) and persisted into LatitudeWorldState. Null
     * means no explicit choice reached the server (legacy launch path / launch failed before this was set),
     * in which case the existing mercator-default behavior applies.
     */
    public static volatile String pendingGlobeShape = null;

    // Rapid sequential world creation (dev automation, a stuck/slow first load, a fast double-invocation) can
    // race: a second world's first-overworld-load could consume/clear a first world's still-pending
    // pendingGlobeShape/pendingGlobeRadius before the first world's own load has processed them, silently
    // falling back to the Mercator default instead of an explicitly-chosen shape. There's no natural world/
    // session identity available to fence pendingGlobeShape/pendingGlobeRadius directly (they're set on the
    // client thread before the corresponding server exists), so instead this claims a single "a world
    // creation is in flight" slot for the WHOLE launch (any world type, not just Latitude, since a plain
    // vanilla world's load can just as easily steal a still-pending Latitude shape) -- a second launch attempt
    // is refused rather than silently racing the first. Released once the resulting world's first overworld
    // load actually runs (GlobeMod.initLatitudeBiomesForWorld), or the launch fails/rolls back. Time-bounded
    // as a safety net so a genuinely stuck load can't permanently lock out new-world creation.
    private static volatile long worldCreationClaimedAtMs = 0L;
    private static final long WORLD_CREATION_CLAIM_TIMEOUT_MS = 30_000L;

    public static synchronized boolean tryClaimWorldCreationInFlight(long nowMs) {
        if (worldCreationClaimedAtMs != 0L && (nowMs - worldCreationClaimedAtMs) < WORLD_CREATION_CLAIM_TIMEOUT_MS) {
            return false;
        }
        worldCreationClaimedAtMs = nowMs;
        return true;
    }

    public static synchronized void clearWorldCreationInFlight() {
        worldCreationClaimedAtMs = 0L;
    }

    public static void set(String zoneId) {
        pendingSpawnZone = zoneId;
    }

    public static String peek() {
        return pendingSpawnZone;
    }

    public static String consume() {
        String v = pendingSpawnZone;
        pendingSpawnZone = null;
        return v;
    }
}
