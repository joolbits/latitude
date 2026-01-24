package com.example.globe;

import net.minecraft.server.network.ServerPlayerEntity;

public final class StartCompass {
    private StartCompass() {
    }

    private static final String TAG = "latitude_given_compass";

    public static boolean hasReceived(ServerPlayerEntity p) {
        if (p == null) return false;
        return p.getCommandTags().contains(TAG);
    }

    public static void markReceived(ServerPlayerEntity p) {
        if (p == null) return;
        p.addCommandTag(TAG);
    }
}
