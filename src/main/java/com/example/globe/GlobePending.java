package com.example.globe;

public final class GlobePending {
    private GlobePending() {
    }

    private static volatile String pendingSpawnZone;

    public static void set(String zoneId) {
        pendingSpawnZone = zoneId;
    }

    public static String consume() {
        String v = pendingSpawnZone;
        pendingSpawnZone = null;
        return v;
    }
}
