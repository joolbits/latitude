package com.example.globe.client;

public final class LatitudeClientState {
    private LatitudeClientState() {
    }

    public static boolean firstWorldLoad = false;
    public static long firstWorldLoadStartMs = 0L;
    /** Set by LatitudeWorldLauncher before launch, cleared when loading screen closes. */
    public static volatile boolean latitudeWorldLoading = false;
}
