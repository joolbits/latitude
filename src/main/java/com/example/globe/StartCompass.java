package com.example.globe;

import net.minecraft.server.level.ServerPlayer;

public final class StartCompass {
    private StartCompass() {
    }

    private static final String TAG = "latitude_given_compass";

    public static boolean hasReceived(ServerPlayer p) {
        if (p == null) return false;
        return p.entityTags().contains(TAG);
    }

    public static void markReceived(ServerPlayer p) {
        if (p == null) return;
        p.addTag(TAG);
    }
}
