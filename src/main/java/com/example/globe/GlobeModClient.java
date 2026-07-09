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
import com.example.globe.client.SpawnZoneScreen;
import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.dev.DevCaptureKeybind;
import com.example.globe.dev.client.SeamAuditClientBridge;
import com.example.globe.dev.client.audit.SeamAuditHarness;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class GlobeModClient implements ClientModInitializer {
    private static boolean pendingSpawnPickerOpen;

    /**
     * Slice B (audit P1-2 / Lane 8): the Sodium E-W section-culling compat mixin
     * ({@code RenderSectionManagerVisibilityMixin}) targets Sodium's internal
     * {@code RenderSectionManager.isSectionVisible(III)Z}, which Sodium 0.9.0+mc26.2 removed; the injection
     * uses {@code require = 0} so a missing target degrades silently instead of crashing the client — but
     * "silently" meant NOTHING anywhere said the optimization was off (a future "why is E-W culling not
     * working" mystery). A mixin cannot log its own non-application, so this client-init reflection check
     * carries the warn: if Sodium is loaded and the target method is absent, say so once.
     */
    private static void warnIfSodiumCullHookInactive() {
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                return;
            }
            Class<?> rsm = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager");
            rsm.getDeclaredMethod("isSectionVisible", int.class, int.class, int.class);
            // Method present -> the compat injection applied; the E-W culling optimization is active.
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] Sodium is installed but RenderSectionManager.isSectionVisible(III)Z is absent "
                            + "on this Sodium version -- Latitude's E-W section-culling render-distance "
                            + "optimization is INACTIVE (safely skipped; everything else unaffected). "
                            + "Expected on Sodium >= 0.9.0.");
        } catch (Throwable ignored) {
            // Reflection failure here must never affect client init; the check is purely informational.
        }
    }
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
        if (GlobeClientState.DEBUG_EW_FOG) {
            GlobeMod.LOGGER.info("[Latitude] debugEwFog=true");
        }
        warnIfSodiumCullHookInactive();

        LatitudeConfig.get();
        ClientLifecycleEvents.CLIENT_STARTED.register(GlobeModClient::registerPromenadePalmTintCompat);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GlobeClientState.setGlobeWorld(false);
            com.example.globe.util.LatitudeMath.setLatitudeZRadius(0);
            pendingSpawnPickerOpen = false;
            // U-D world-switch hygiene: per-world caches (HUD strings, compass presence, dial-texture
            // presence) and any in-flight zone title must not leak across worlds.
            com.example.globe.client.CompassHud.onWorldSwitch();
            com.example.globe.client.ZoneEnterTitleOverlay.reset();
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            if (payload.isGlobe()) {
                // Flip the bespoke loading flag as soon as the handshake packet arrives (network thread).
                LatitudeClientState.activateLatitudeLoading();
            } else if (LatitudeClientState.isLatitudeWorldLoading()) {
                LatitudeClientState.clearLatitudeLoadingState();
            }
            context.client().execute(() -> {
                GlobeClientState.setGlobeWorld(payload.isGlobe());
                // Mercator: latitude (Z) radius differs from the X-sized border half; drive HUD/zone/pole off it.
                com.example.globe.util.LatitudeMath.setLatitudeZRadius(payload.isGlobe() ? payload.latitudeZRadius() : 0);
                GlobeMod.LOGGER.info("S2C globe state: isGlobe={} latitudeZRadius={}", payload.isGlobe(), payload.latitudeZRadius());
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

        client.getBlockColors().register(
                List.of(BlockTintSources.constant(PROMENADE_PALM_LEAVES_OPAQUE_TINT)),
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
            // U-B one-front-door: F9 opens the HUD Studio directly (the legacy LatitudeSettingsScreen was a
            // duplicated subset with different save semantics; its two unique fields moved to the Studio's
            // General tab).
            if (client.gui.screen() == null) {
                client.setScreenAndShow(new LatitudeHudStudioScreen(null));
            } else {
                client.setScreenAndShow(new LatitudeHudStudioScreen(client.gui.screen()));
            }
        }
    }

    private static void polarCapClientTick(Minecraft client) {
        if (pendingSpawnPickerOpen && client.player != null && client.level != null && client.gui.screen() == null) {
            pendingSpawnPickerOpen = false;
            client.setScreenAndShow(new SpawnZoneScreen());
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

        // B-3b anti-backlog HARD REQUIREMENT: while the game is paused the client tick keeps firing but the
        // particle engine does not step existing particles -- spawning here would pile them at the spawn
        // point to all animate in a burst on unpause. Paused => spawn NOTHING; resume is clean. Guards ALL
        // particle spawning below (ambient snow AND the EW storm), so no path can accrue a paused backlog.
        if (client.isPaused()) {
            return;
        }

        // Throttle: spawn on every 4th tick (shared by ambient snow + EW storm). Fixed per-tick BUDGET on
        // each spawn tick -- never a "how many do I owe since last spawn" accumulator.
        boolean spawnTick = (client.level.getGameTime() & 3) == 0;

        // B-3b: AMBIENT polar snow + fog. Always-on for globe worlds in the polar approach band (85 deg+),
        // NOT gated by enableWarningParticles -- atmosphere, like the EW screen haze (which is also not
        // config-gated). Density is a FIXED per-tick budget from the 85->90 progress ramp (very heavy near
        // the pole). The matching FOG is rendered by PolarWhiteoutOverlayHud (a HUD screen fill reading
        // computePoleWhiteoutFactor) on the SAME 85->90 ramp; volumetric fog-renderer wiring is a B-4 decision.
        if (spawnTick) {
            double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                    client.level.getWorldBorder(), client.player.getZ());
            int snowCount = com.example.globe.core.PolarHazardWindow.snowCount(absLatDeg);
            if (snowCount > 0) {
                spawnAmbientPolarSnow(client, snowCount);
            }
        }

        // WARNING-intensity particles (the EW border storm) stay behind enableWarningParticles -- its
        // meaning is unchanged (it governs only the warning particles it already governed).
        if (!LatitudeConfig.enableWarningParticles) {
            return;
        }

        GlobeClientState.EwStormStage ewStage = GlobeClientState.computeEwStormStage(client.level, client.player);
        if (ewStage != GlobeClientState.EwStormStage.NONE && spawnTick) {
            ewStormClientTick(client, ewStage);
        }
    }

    private static void spawnAmbientPolarSnow(Minecraft client, int count) {
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

    private static void ewStormClientTick(Minecraft client, GlobeClientState.EwStormStage stage) {
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

        int mainCount = base;
        int hazeCount = Math.max(1, base / 3);

        // Climate-aware E/W storm (TEST 1 E1): in the cold bands (subpolar/polar) the border storm is a SNOW
        // blizzard — snowflakes + pale haze building toward whiteout — instead of a desert sandstorm.
        var border = client.level.getWorldBorder();
        double absDeg = Math.abs(com.example.globe.util.LatitudeMath.degreesFromZ(border, pz));
        com.example.globe.util.LatitudeBands.Band band =
                com.example.globe.util.LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        boolean cold = band == com.example.globe.util.LatitudeBands.Band.SUBPOLAR
                || band == com.example.globe.util.LatitudeBands.Band.POLAR;

        if (cold) {
            spawnCloudRing(client, ParticleTypes.SNOWFLAKE, mainCount, random, px, py, pz, vx);
        } else {
            BlockParticleOption sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
            spawnCloudRing(client, sand, mainCount, random, px, py, pz, vx);
        }
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
