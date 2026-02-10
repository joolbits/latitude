package com.example.globe.client;

import com.example.globe.client.gui.LatitudeSpawnWaitScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkStatus;

public final class FirstWorldLoadSpawnWaitEnforcer {
    private FirstWorldLoadSpawnWaitEnforcer() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!LatitudeClientState.firstWorldLoad) {
                return;
            }

            ClientWorld world = client.world;
            if (world == null || client.player == null) {
                return;
            }

            if (latitude$ready(world, client)) {
                LatitudeClientState.firstWorldLoad = false;
                LatitudeClientState.firstWorldLoadStartMs = 0L;
                return;
            }

            if (client.currentScreen == null) {
                client.setScreen(new LatitudeSpawnWaitScreen());
            }
        });
    }

    private static boolean latitude$ready(ClientWorld world, MinecraftClient client) {
        final int radius = 1;
        int cx = client.player.getBlockPos().getX() >> 4;
        int cz = client.player.getBlockPos().getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (world.getChunkManager().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
