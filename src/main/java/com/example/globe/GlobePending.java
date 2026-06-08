package com.example.globe;

public final class GlobePending {
    private GlobePending() {
    }

    private static volatile String pendingSpawnZone;

    public static volatile boolean startWithCompass = true;

    /**
     * Border radius (blocks) the bespoke create-world launcher selected for the world being
     * created. Read once by the server at the new world's first {@code ServerWorldEvents.LOAD}
     * and persisted into {@link com.example.globe.world.LatitudeWorldState}, because the globe
     * ChunkGeneratorSettings key does NOT survive level.dat serialization on this MC line
     * (it deserializes as an unkeyed/inline entry), so the world's Globe-ness and size cannot
     * be recovered from the generator at runtime. 0 = not a Latitude/Globe world.
     */
    public static volatile int pendingGlobeRadius = 0;

    public static void set(String zoneId) {
        pendingSpawnZone = zoneId;
    }

    public static String consume() {
        String v = pendingSpawnZone;
        pendingSpawnZone = null;
        return v;
    }
}
