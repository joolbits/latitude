package com.example.globe;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.CompassHud;
import com.example.globe.client.CompassHudConfig;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeSettingsScreen;
import com.example.globe.client.SpawnZoneScreen;
import com.example.globe.client.EwSandstormOverlayRenderer;
import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.dev.DevCaptureKeybind;
import com.example.globe.dev.client.SeamAuditClientBridge;
import com.example.globe.dev.client.audit.SeamAuditHarness;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

public class GlobeModClient implements ClientModInitializer {
    private static boolean pendingSpawnPickerOpen;

    @Override
    public void onInitializeClient() {
        GlobeNet.registerPayloads();
        GlobeMod.LOGGER.info("Globe client init OK");
        if (GlobeClientState.DEBUG_EW_FOG) {
            GlobeMod.LOGGER.info("[Latitude] debugEwFog=true");
        }

        LatitudeConfig.get();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobeClientState.setGlobeWorld(false);
            pendingSpawnPickerOpen = false;
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            if (payload.isGlobe()) {
                // Flip the bespoke loading flag as soon as the handshake packet arrives (network thread).
                LatitudeClientState.activateLatitudeLoading();
            }
            context.client().execute(() -> {
                GlobeClientState.setGlobeWorld(payload.isGlobe());
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={}", payload.isGlobe());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.OpenSpawnPickerPayload.ID, (payload, context) -> {
            // Legacy spawn picker is no longer part of the first-load flow; ignore any stale payloads.
            if (!payload.open()) {
                return;
            }

            context.client().execute(() -> {
                pendingSpawnPickerOpen = false;
                GlobeMod.LOGGER.info("Ignoring legacy open spawn picker payload");
            });
        });

        GlobeWarningOverlay.init();
        CompassHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::polarCapClientTick);
        ClientKeybinds.init();
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            DevCaptureKeybind.init();
            SeamAuditClientBridge.init();
            SeamAuditHarness.init();
        }
        ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::clientKeybindTick);
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            com.example.globe.dev.AutoCreateWorldProbe.maybeRegister();
        }

        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (!GlobeClientState.DEBUG_EW_WALL) return;
            // Wall/overlay rendering happens in the HUD pass now to avoid POV seams.
            return;
        });
    }

    private static void clientKeybindTick(Minecraft client) {
        while (ClientKeybinds.TOGGLE_COMPASS.consumeClick()) {
            var cfg = CompassHudConfig.get();
            cfg.enabled = !cfg.enabled;
            CompassHudConfig.saveCurrent();
        }

        while (ClientKeybinds.OPEN_SETTINGS.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new LatitudeSettingsScreen(null));
            } else {
                client.setScreen(new LatitudeSettingsScreen(client.screen));
            }
        }
    }

    private static void polarCapClientTick(Minecraft client) {
        if (pendingSpawnPickerOpen && client.player != null && client.level != null && client.screen == null) {
            pendingSpawnPickerOpen = false;
            client.setScreen(new SpawnZoneScreen());
            GlobeMod.LOGGER.info("Opened SpawnZoneScreen");
        }

        if (client.player == null || client.level == null) {
            return;
        }

        // Clamp client-side view distance for EW storms (Sodium-proof fog wall).
        GlobeClientState.clampEwViewDistance(client);

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

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.level, client.player);
        GlobeClientState.EwStormStage ewStage = GlobeClientState.computeEwStormStage(client.level, client.player);

        boolean polarActive = polarStage != GlobeClientState.PolarStage.NONE;
        boolean ewActive = ewStage != GlobeClientState.EwStormStage.NONE;

        if (!polarActive && !ewActive) {
            return;
        }

        if ((client.level.getGameTime() & 3) != 0) {
            return;
        }

        if (ewActive) {
            ewSandstormClientTick(client, ewStage);
        }

        if (polarActive) {
            float intensity = switch (polarStage) {
                case WARN_1 -> 0.12f;
                case WARN_2 -> 0.22f;
                case DANGER, LETHAL -> Math.max(0.4f, GlobeClientState.computePoleWhiteoutFactor(client.player.getZ()));
                default -> 0.0f;
            };

            intensity = Math.max(0.0f, Math.min(1.0f, intensity));
            if (intensity <= 0.001f) {
                return;
            }

            int count = 2 + (int) Math.round(intensity * 26.0);
            if (count > 6) count = 6;
            RandomSource random = client.player.getRandom();

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
                client.particleEngine.createParticle(ParticleTypes.SNOWFLAKE, px + ox, py + 1.5 + oy, pz + oz, vHoriz, vy, vz);
            }
        }
    }

    private static void ewSandstormClientTick(Minecraft client, GlobeClientState.EwStormStage stage) {
        int base = switch (stage) {
            case LEVEL_1 -> 6;
            case LEVEL_2 -> 20;
            default -> 0;
        };
        if (base <= 0) {
            return;
        }

        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        double vx = client.player.getX() >= 0.0 ? -0.10 : 0.10;

        // Use falling sand dust for a visible sandstorm wall, plus some haze.
        int sandCount = base;
        int hazeCount = Math.max(1, base / 3);
        BlockParticleOption sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
        spawnCloudRing(client, sand, sandCount, random, px, py, pz, vx);
        spawnCloudRing(client, ParticleTypes.CLOUD, hazeCount, random, px, py, pz, vx * 0.6);
    }

    private static void spawnCloudRing(Minecraft client, ParticleOptions particle, int count, RandomSource random,
                                       double px, double py, double pz, double vx) {
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * 16.0;
            double oy = 1.0 + random.nextDouble() * 6.0;
            double oz = (random.nextDouble() - 0.5) * 16.0;
            client.particleEngine.createParticle(particle, px + ox, py + oy, pz + oz, vx, 0.01, 0.0);
        }
    }

    private static boolean isWarningParticleActive(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.level, client.player);
        GlobeClientState.EwStormStage ewStage = GlobeClientState.computeEwStormStage(client.level, client.player);
        return polarStage != GlobeClientState.PolarStage.NONE || ewStage != GlobeClientState.EwStormStage.NONE;
    }
}
