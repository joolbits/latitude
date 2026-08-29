package com.example.globe;

import java.util.ArrayList;
import java.util.List;

import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.CompassHud;
import com.example.globe.client.CompassHudConfig;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.GlobeWarningOverlay;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.LatitudeHudStudioScreen;
import com.example.globe.client.EwPresentationPolicy;
import com.example.globe.dev.DevCaptureKeybind;
import com.example.globe.dev.client.SeamAuditClientBridge;
import com.example.globe.dev.client.audit.SeamAuditHarness;
import com.example.globe.util.LatitudeBands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class GlobeModClient implements ClientModInitializer {
    private static final int PROMENADE_PALM_LEAVES_OPAQUE_TINT = 0xFF7DB22E;
    private static final String[] PROMENADE_PALM_TINT_BLOCKS = {
            "promenade:palm_leaves",
            "promenade:snowy_palm_leaves",
            "promenade:palm_hanging_leaves",
            "promenade:palm_leaf_pile"
    };

    @Override
    public void onInitializeClient() {
        GlobeNet.registerPayloads();
        GlobeMod.LOGGER.info("Globe client init OK");

        LatitudeConfig.get();
        ClientLifecycleEvents.CLIENT_STARTED.register(GlobeModClient::registerPromenadePalmTintCompat);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobeWarningOverlay.resetForDisconnect();
            GlobeClientState.resetForDisconnect();
            // A failed/cancelled integrated-world load may disconnect before the normal client-ready tick.
            // Never let that stale flag keep the next loading screen under Latitude's close hold.
            LatitudeClientState.clearLatitudeLoadingState();
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            if (payload.isGlobe()) {
                // Flip the bespoke loading flag as soon as the handshake packet arrives (network thread).
                LatitudeClientState.activateLatitudeLoading();
                // LatitudeWorldLauncher (fresh creation) and the resumed-world mixins run ONLY on the
                // client hosting its own IntegratedServer -- hasSingleplayerServer() is exactly that
                // check (true from the moment doWorldLoad spins the server; unlike isSingleplayer(),
                // it stays true after opening to LAN, so the host keeps ignoring this payload even
                // then). On that one client, applying this id could race or overwrite a label -- or a
                // deliberate null for a random spawn zone -- those local mechanisms already set. Every
                // other client (a remote dedicated-server join, or a friend joining an opened-to-LAN
                // world) never had any of those three mechanisms touch it, so there's nothing to
                // clobber: apply freely there.
                if (!context.client().hasSingleplayerServer()) {
                    LatitudeBands.Band band = LatitudeBands.fromCanonicalId(payload.loadingBandId());
                    if (band != null) {
                        LatitudeClientState.setLoadingZoneLabel(band.displayName());
                    }
                }
            } else if (LatitudeClientState.isLatitudeWorldLoading()) {
                LatitudeClientState.clearLatitudeLoadingState();
            }
            context.client().execute(() -> GlobeClientState.setGlobeWorld(payload.isGlobe()));
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.OpenSpawnPickerPayload.ID, (payload, context) -> {
            // Legacy spawn picker is no longer part of the first-load flow; ignore any stale payloads.
            if (!payload.open()) {
                return;
            }

            context.client().execute(() -> {
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

    }

    private static void registerPromenadePalmTintCompat(Minecraft client) {
        if (!FabricLoader.getInstance().isModLoaded("promenade")) {
            return;
        }

        List<Block> blocks = new ArrayList<>();
        for (String blockId : PROMENADE_PALM_TINT_BLOCKS) {
            Identifier id = Identifier.parse(blockId);
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                blocks.add(BuiltInRegistries.BLOCK.getValue(id));
            }
        }

        if (blocks.isEmpty()) {
            GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat skipped; no palm leaf blocks found");
            return;
        }

        if (client.getBlockColors() == null) {
            GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat deferred; block colors not ready");
            return;
        }

        // 26.2 registers a List<BlockTintSource>; 1.21.11 has no BlockTintSources at all and
        // takes a single BlockColor functional interface instead. Same constant tint either way.
        client.getBlockColors().register(
                (state, level, pos, tintIndex) -> PROMENADE_PALM_LEAVES_OPAQUE_TINT,
                blocks.toArray(Block[]::new)
        );
        GlobeMod.LOGGER.info("[Latitude] Promenade palm tint compat applied to {} block(s)", blocks.size());
    }

    private static void clientKeybindTick(Minecraft client) {
        while (ClientKeybinds.TOGGLE_COMPASS.consumeClick()) {
            var cfg = CompassHudConfig.get();
            cfg.enabled = !cfg.enabled;
            CompassHudConfig.saveCurrent();
        }

        while (ClientKeybinds.OPEN_SETTINGS.consumeClick()) {
            if (client.screen instanceof LatitudeHudStudioScreen) {
                continue;
            }
            if (client.screen == null) {
                client.setScreen(new LatitudeHudStudioScreen(null));
            } else {
                client.setScreen(new LatitudeHudStudioScreen(client.screen));
            }
        }
    }

    private static void polarCapClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        // Trust GlobeClientState (server-synced)
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        var eval = GlobeClientState.evaluate(client);
        if (!eval.active()) {
            
            return;
        }

        if (!LatitudeConfig.enableWarningParticles) {
            return;
        }

        GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.level, client.player);
        double ewDistanceToBorder = GlobeClientState.distanceToEwBorderBlocks(client.player.getX());
        float ewParticleIntensity = EwPresentationPolicy.particleIntensity(
                ewDistanceToBorder,
                GlobeClientState.ewPresentationVisibility());

        boolean polarActive = polarStage != GlobeClientState.PolarStage.NONE;
        boolean ewActive = ewParticleIntensity > 0.0f;

        if (!polarActive && !ewActive) {
            return;
        }

        if ((client.level.getGameTime() & 3) != 0) {
            return;
        }

        if (ewActive) {
            ewSandstormClientTick(
                    client,
                    ewDistanceToBorder,
                    GlobeClientState.ewPresentationVisibility());
        }

        if (polarActive && eval.surfaceOk()) {
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

    private static void ewSandstormClientTick(
            Minecraft client,
            double distanceToBorder,
            float presentationVisibility) {
        RandomSource random = client.player.getRandom();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        var border = client.level.getWorldBorder();
        double vx = EwPresentationPolicy.windTowardInterior(
                border.getMinX(),
                border.getMaxX(),
                px,
                0.10);

        // Use falling sand dust for a visible sandstorm wall, plus some haze.
        int sandCount = EwPresentationPolicy.leadingSandParticleBudget(20, distanceToBorder, presentationVisibility);
        int hazeCount = EwPresentationPolicy.particleBudget(7, distanceToBorder, presentationVisibility);
        if (sandCount <= 0 && hazeCount <= 0) {
            return;
        }
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

}
