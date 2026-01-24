package com.example.globe;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.CompassHud;
import com.example.globe.client.CompassHudConfig;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeSettingsScreen;
import com.example.globe.client.SpawnZoneScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

public class GlobeModClient implements ClientModInitializer {
    private static boolean pendingSpawnPickerOpen;

    @Override
    public void onInitializeClient() {
        GlobeNet.registerPayloads();
        GlobeMod.LOGGER.info("Globe client init OK");

        LatitudeConfig.get();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobeClientState.setGlobeWorld(false);
            pendingSpawnPickerOpen = false;
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                GlobeClientState.setGlobeWorld(payload.isGlobe());
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={}", payload.isGlobe());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.OpenSpawnPickerPayload.ID, (payload, context) -> {
            if (!payload.open()) {
                return;
            }

            context.client().execute(() -> {
                pendingSpawnPickerOpen = true;
                GlobeMod.LOGGER.info("S2C open spawn picker received (pending=true)");
            });
        });

        GlobeWarningOverlay.init();
        CompassHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::polarCapClientTick);
        ClientKeybinds.init();
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::clientKeybindTick);
    }

    private static void clientKeybindTick(MinecraftClient client) {
        while (ClientKeybinds.TOGGLE_COMPASS.wasPressed()) {
            var cfg = CompassHudConfig.get();
            cfg.enabled = !cfg.enabled;
            CompassHudConfig.saveCurrent();
        }

        while (ClientKeybinds.OPEN_SETTINGS.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new LatitudeSettingsScreen(null));
            } else {
                client.setScreen(new LatitudeSettingsScreen(client.currentScreen));
            }
        }
    }

    private static void polarCapClientTick(MinecraftClient client) {
        if (pendingSpawnPickerOpen && client.player != null && client.world != null && client.currentScreen == null) {
            pendingSpawnPickerOpen = false;
            client.setScreen(new SpawnZoneScreen());
            GlobeMod.LOGGER.info("Opened SpawnZoneScreen");
        }

        if (client.player == null || client.world == null) {
            return;
        }

        // Trust GlobeClientState (server-synced)
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        if (GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }

        var eval = GlobeClientState.evaluate(client);
        if (!eval.active()) {
            
            return;
        }

        if (!eval.surfaceOk()) {
            return;
        }

        if (!LatitudeConfig.enableWarningParticles) {
            return;
        }

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.world, client.player);
        GlobeClientState.StormStage stormStage = GlobeClientState.computeStormStage(client.world, client.player);

        boolean polarActive = polarStage != GlobeClientState.PolarStage.NONE;
        boolean stormActive = stormStage != GlobeClientState.StormStage.NONE;

        if (!polarActive && !stormActive) {
            return;
        }

        if ((client.world.getTime() & 3) != 0) {
            return;
        }


        if (stormActive && stormStage != GlobeClientState.StormStage.NONE) {
            stormWallClientTick(client, stormStage);
        }

        if (polarActive) {
            float intensity = switch (polarStage) {
                case UNEASE -> 0.12f;
                case EFFECTS_I -> 0.22f;
                case EFFECTS_II -> 0.35f;
                case WHITEOUT_APPROACH, LETHAL, HOPELESS -> Math.max(0.4f, GlobeClientState.computePoleWhiteoutFactor(client.player.getZ()));
                default -> 0.0f;
            };

            intensity = Math.max(0.0f, Math.min(1.0f, intensity));
            if (intensity <= 0.001f) {
                return;
            }

            int count = 2 + (int) Math.round(intensity * 26.0);
            if (count > 6) count = 6;
            Random random = client.player.getRandom();

            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();

            for (int i = 0; i < count; i++) {
                double ox = (random.nextDouble() - 0.5) * 10.0;
                double oy = random.nextDouble() * 4.0;
                double oz = (random.nextDouble() - 0.5) * 10.0;

                double vx = (random.nextDouble() - 0.5) * 0.06;
                double vy = -0.02 - random.nextDouble() * 0.03;
                double vz = (random.nextDouble() - 0.5) * 0.06;

                double vHoriz = (vx + vz) * 0.5;
                client.particleManager.addParticle(ParticleTypes.SNOWFLAKE, px + ox, py + 1.5 + oy, pz + oz, vHoriz, vy, vz);
            }
        }
    }

    private static void stormWallClientTick(MinecraftClient client, GlobeClientState.StormStage stage) {
        float storm;
        float severe;
        switch (stage) {
            case WARNING -> {
                storm = 0.35f;
                severe = 0.0f;
            }
            case DANGER -> {
                storm = 0.85f;
                severe = 0.35f;
            }
            case EDGE_ABSOLUTE -> {
                storm = 1.0f;
                severe = 1.0f;
            }
            default -> {
                return;
            }
        }

        int cloudCount = 1 + (int) Math.round(storm * 10.0 + severe * 14.0);
        if (cloudCount > 6) cloudCount = 6;
        int ashCount = (int) Math.round(storm * 6.0 + severe * 16.0);
        if (ashCount > 6) ashCount = 6;

        Random random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        double vx = client.player.getX() >= 0.0 ? -0.06 : 0.06;

        spawnCloudRing(client, ParticleTypes.CLOUD, cloudCount, random, px, py, pz, vx);
        if (ashCount > 0) {
            spawnCloudRing(client, ParticleTypes.ASH, ashCount, random, px, py, pz, vx * 0.6);
        }
    }

    private static void spawnCloudRing(MinecraftClient client, ParticleEffect particle, int count, Random random,
                                       double px, double py, double pz, double vx) {
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * 16.0;
            double oy = 1.0 + random.nextDouble() * 6.0;
            double oz = (random.nextDouble() - 0.5) * 16.0;
            client.particleManager.addParticle(particle, px + ox, py + oy, pz + oz, vx, 0.01, 0.0);
        }
    }

    private static boolean isWarningParticleActive(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return false;
        }

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.world, client.player);
        GlobeClientState.StormStage stormStage = GlobeClientState.computeStormStage(client.world, client.player);
        return polarStage != GlobeClientState.PolarStage.NONE || stormStage != GlobeClientState.StormStage.NONE;
    }
}
