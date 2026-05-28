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
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.EwSandstormOverlayRenderer;
import com.example.globe.client.EwStormWallRenderer;
import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.GlobeWorldSizeRuntime;
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
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

public class GlobeModClient implements ClientModInitializer {
    private static final Identifier MANGROVE_SWAMP_ID = Identifier.fromNamespaceAndPath("minecraft", "mangrove_swamp");
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_POST_ENTRY_WAIT_TICKS = 60;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_SPAWN_SCAN_RADIUS = 768;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_SPAWN_SCAN_STEP = 32;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_WAIT_TICKS = 40;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_RADIUS = 512;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_SAMPLES = 1000;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_CUSTOM_SCAN_RADIUS = 1024;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_CUSTOM_SCAN_STEP = 32;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TELEPORT_WAIT_TICKS = 40;
    private static boolean pendingSpawnPickerOpen;
    private static boolean autoCreateWorldProbeLatdevCommandsSent;
    private static boolean autoCreateWorldProbeLatdevCustomTeleportAttempted;
    private static boolean autoCreateWorldProbeLatdevTargetVerified;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetPending;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetComplete;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetFailed;
    private static boolean autoCreateWorldProbePauseOnLostFocusOriginalValue = true;
    private static boolean autoCreateWorldProbePauseOnLostFocusCaptured;
    private static boolean autoCreateWorldProbePauseOnLostFocusDisabledLogged;
    private static long autoCreateWorldProbeLatdevCommandsSentGameTime = Long.MIN_VALUE;
    private static long autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
    private static final boolean AUTO_CREATE_WORLD_PROBE_CREATIVE = isAutoCreateWorldProbeCreativeEnabled();

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
            autoCreateWorldProbeLatdevServerTargetPending = false;
            autoCreateWorldProbeLatdevServerTargetComplete = false;
            autoCreateWorldProbeLatdevServerTargetFailed = false;
            autoCreateWorldProbePauseOnLostFocusCaptured = false;
            autoCreateWorldProbePauseOnLostFocusDisabledLogged = false;
        });

        ClientPlayNetworking.registerGlobalReceiver(GlobeNet.GlobeStatePayload.ID, (payload, context) -> {
            if (payload.isGlobe()) {
                // Flip the bespoke loading flag as soon as the handshake packet arrives (network thread).
                LatitudeClientState.activateLatitudeLoading();
                LatitudeClientState.firstWorldLoad = false;
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
        if (isAutoCreateWorldProbeEnabled()) {
            long timeoutMs = getAutoCreateWorldProbeTimeoutMs();
            autoCreateWorldProbeLatdevCommandsSent = false;
            autoCreateWorldProbeLatdevCustomTeleportAttempted = false;
            autoCreateWorldProbeLatdevTargetVerified = false;
            autoCreateWorldProbeLatdevServerTargetPending = false;
            autoCreateWorldProbeLatdevServerTargetComplete = false;
            autoCreateWorldProbeLatdevServerTargetFailed = false;
            autoCreateWorldProbePauseOnLostFocusCaptured = false;
            autoCreateWorldProbePauseOnLostFocusDisabledLogged = false;
            autoCreateWorldProbeLatdevCommandsSentGameTime = Long.MIN_VALUE;
            autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
            LatitudeClientState.resetAutoCreateWorldProbe(timeoutMs);
            GlobeMod.LOGGER.info("[LAT][CWPATH] autoCreateWorldProbe enabled timeout={}s creative={}",
                    timeoutMs / 1000L, AUTO_CREATE_WORLD_PROBE_CREATIVE);
            ClientTickEvents.END_CLIENT_TICK.register(GlobeModClient::autoCreateWorldProbeTick);
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

    private static void autoCreateWorldProbeTick(Minecraft client) {
        if (client == null) {
            return;
        }

        disableAutoCreateWorldProbePauseOnLostFocus(client);

        long timeoutMs = LatitudeClientState.getAutoCreateWorldProbeTimeoutMs();
        long startMs = LatitudeClientState.getAutoCreateWorldProbeStartMs();
        if (startMs > 0L && System.currentTimeMillis() - startMs >= timeoutMs) {
            if (!LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
                emitAutoCreateWorldProbeTimeoutDiagnostics(client, startMs, timeoutMs);
                LatitudeClientState.markAutoCreateWorldProbeTimedOut();
                stopAutoCreateWorldProbeClient(client);
            }
            return;
        }

        Screen screen = client.screen;

        if (screen == null && client.level != null && client.player != null) {
            if (!LatitudeClientState.isAutoCreateWorldProbeWorldEntered()) {
                GlobeMod.LOGGER.info("[LAT][CWPATH] world entry detected: level={} screen=null",
                        client.level.getClass().getName());
                LatitudeClientState.markAutoCreateWorldProbeWorldEntered(client.level.getGameTime());
            }
        }

        if (screen == null && (client.level == null || client.player == null)) {
            return;
        }

        if (!LatitudeClientState.isAutoCreateWorldProbeOpened()) {
            if (screen instanceof TitleScreen) {
                GlobeMod.LOGGER.info("[LAT][CWPATH] title screen detected: {}", screen.getClass().getName());
                LatitudeClientState.markAutoCreateWorldProbeOpened();
                client.execute(() -> {
                    if (LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
                        return;
                    }
                    GlobeMod.LOGGER.info("[LAT][CWPATH] opening create-world probe");
                    CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
                    Screen afterOpen = client.screen;
                    GlobeMod.LOGGER.info("[LAT][CWPATH] current screen after open: {}",
                            afterOpen == null ? "null" : afterOpen.getClass().getName());
                });
            }
            return;
        }

        if ((screen instanceof CreateWorldScreen || screen instanceof LatitudeCreateWorldScreen)
                && !LatitudeClientState.isAutoCreateWorldProbeScreenDetectedLogged()) {
            GlobeMod.LOGGER.info("[LAT][CWPATH] create-world screen detected: {}", screen.getClass().getName());
            LatitudeClientState.markAutoCreateWorldProbeScreenDetectedLogged();
        }

        if (screen instanceof LatitudeCreateWorldScreen latitudeCreateWorldScreen) {
            if (!LatitudeClientState.isAutoCreateWorldProbeConfirmed()) {
                LatitudeClientState.markAutoCreateWorldProbeConfirmed();
                client.execute(() -> {
                    if (LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
                        return;
                    }
                    Screen active = client.screen;
                    if (!(active instanceof LatitudeCreateWorldScreen currentLatitudeScreen)) {
                        GlobeMod.LOGGER.info("[LAT][CWPATH] auto-confirm skipped; current screen is {}",
                                active == null ? "null" : active.getClass().getName());
                        return;
                    }

                    applyAutoCreateWorldProbeInputs(currentLatitudeScreen);
                    if (AUTO_CREATE_WORLD_PROBE_CREATIVE && !LatitudeClientState.isAutoCreateWorldProbeCreativeApplied()) {
                        currentLatitudeScreen.probeSetCreativeMode();
                        LatitudeClientState.markAutoCreateWorldProbeCreativeApplied();
                    }
                    GlobeMod.LOGGER.info("[LAT][CWPATH] auto-confirming world creation");
                    currentLatitudeScreen.probeAutoConfirmWorldCreation();
                    Screen afterConfirm = client.screen;
                    GlobeMod.LOGGER.info("[LAT][CWPATH] current screen after confirm: {}",
                            afterConfirm == null ? "null" : afterConfirm.getClass().getName());
                });
            }
        }

        if (LatitudeClientState.isAutoCreateWorldProbeConfirmed()
                && !LatitudeClientState.isAutoCreateWorldProbeWorldEntered()
                && client.level != null
                && client.player != null) {
            GlobeMod.LOGGER.info("[LAT][CWPATH] world entry detected: level={} screen={}",
                    client.level.getClass().getName(),
                    client.screen == null ? "null" : client.screen.getClass().getName());
            LatitudeClientState.markAutoCreateWorldProbeWorldEntered(client.level.getGameTime());
        }

        if (LatitudeClientState.isAutoCreateWorldProbeWorldEntered()
                && !LatitudeClientState.isAutoCreateWorldProbeDiagnosticsCaptured()
                && client.level != null
                && client.player != null
                && !LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
            long enteredGameTime = LatitudeClientState.getAutoCreateWorldProbeWorldEnteredGameTime();
            if (enteredGameTime < 0L) {
                enteredGameTime = client.level.getGameTime();
                LatitudeClientState.markAutoCreateWorldProbeWorldEntered(enteredGameTime);
            }

            long waitTicks = getAutoCreateWorldProbePostEntryWaitTicks();
            if (client.level.getGameTime() - enteredGameTime >= waitTicks) {
                if (waitForAutoCreateWorldProbePlayableClient(client)) {
                    return;
                }
                if (isAutoCreateWorldProbeLatdevHereProbeEnabled()) {
                    if (isAutoCreateWorldProbeLatdevPreferCustomHereEnabled()
                            && !autoCreateWorldProbeLatdevCustomTeleportAttempted) {
                        autoCreateWorldProbeLatdevCustomTeleportAttempted = true;
                        if (sendAutoCreateWorldProbeCustomTeleport(client)) {
                            return;
                        }
                    }
                    if (waitForAutoCreateWorldProbeServerTargetSetup(client)) {
                        return;
                    }
                    if (autoCreateWorldProbeLatdevCustomTeleportGameTime != Long.MIN_VALUE) {
                        long teleportWaitTicks = getAutoCreateWorldProbeLatdevTeleportWaitTicks();
                        if (client.level.getGameTime() - autoCreateWorldProbeLatdevCustomTeleportGameTime < teleportWaitTicks) {
                            return;
                        }
                        if (!verifyAutoCreateWorldProbeTargetBiome(client)) {
                            LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
                            stopAutoCreateWorldProbeClient(client);
                            return;
                        }
                    }
                    if (!autoCreateWorldProbeLatdevCommandsSent) {
                        sendAutoCreateWorldProbeLatdevCommands(client);
                        return;
                    }
                    long commandWaitTicks = getAutoCreateWorldProbeLatdevCommandWaitTicks();
                    if (client.level.getGameTime() - autoCreateWorldProbeLatdevCommandsSentGameTime < commandWaitTicks) {
                        return;
                    }
                }
                captureSpawnProbeDiagnostics(client);
                LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
                GlobeMod.LOGGER.info("[LAT][CWPATH] spawn diagnostics captured; stopping client");
                stopAutoCreateWorldProbeClient(client);
            }
        }

        if (LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
            return;
        }
    }

    private static void disableAutoCreateWorldProbePauseOnLostFocus(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }
        if (!autoCreateWorldProbePauseOnLostFocusCaptured) {
            autoCreateWorldProbePauseOnLostFocusOriginalValue = client.options.pauseOnLostFocus;
            autoCreateWorldProbePauseOnLostFocusCaptured = true;
        }
        if (!client.options.pauseOnLostFocus) {
            return;
        }
        client.options.pauseOnLostFocus = false;
        if (!autoCreateWorldProbePauseOnLostFocusDisabledLogged) {
            autoCreateWorldProbePauseOnLostFocusDisabledLogged = true;
            GlobeMod.LOGGER.info("[LAT][CWPATH] disabled pause-on-lost-focus for unattended proof harness");
        }
    }

    private static void stopAutoCreateWorldProbeClient(Minecraft client) {
        restoreAutoCreateWorldProbePauseOnLostFocus(client);
        client.stop();
    }

    private static void restoreAutoCreateWorldProbePauseOnLostFocus(Minecraft client) {
        if (!autoCreateWorldProbePauseOnLostFocusCaptured || client == null || client.options == null) {
            return;
        }
        client.options.pauseOnLostFocus = autoCreateWorldProbePauseOnLostFocusOriginalValue;
        autoCreateWorldProbePauseOnLostFocusCaptured = false;
    }

    private static void emitAutoCreateWorldProbeTimeoutDiagnostics(Minecraft client, long startMs, long timeoutMs) {
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(0L, now - startMs);
        long phaseTicks = LatitudeClientState.getAutoCreateWorldProbeWorldEnteredGameTime() >= 0L
                ? Math.max(0L, client.level != null ? client.level.getGameTime() - LatitudeClientState.getAutoCreateWorldProbeWorldEnteredGameTime() : 0L)
                : Math.max(0L, elapsedMs / 50L);

        Screen current = client.screen;
        boolean hasWorld = client.level != null;
        boolean hasPlayer = client.player != null;

        GlobeMod.LOGGER.info(
                "[LAT][CWPATH] timeout phase={} ticksInPhase={} elapsedMs={} timeoutMs={} screen={} world={} player={}",
                LatitudeClientState.getAutoCreateWorldProbePhase(),
                phaseTicks,
                elapsedMs,
                timeoutMs,
                current == null ? "null" : current.getClass().getName(),
                hasWorld,
                hasPlayer);

        if (hasWorld && hasPlayer) {
            GlobeMod.LOGGER.info(
                    "[LAT][CWPATH] timeout details playerPos=x={} y={} z={} worldTime={} gameMode={}",
                    client.player.getX(),
                    client.player.getY(),
                    client.player.getZ(),
                    client.level.getGameTime(),
                    client.gameMode == null ? "null" : client.gameMode.getClass().getName());
        }
    }

    private static boolean isAutoCreateWorldProbeEnabled() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return false;
        }

        String explicit = System.getProperty("latitude.debug.autoCreateWorldProbe");
        if (explicit != null) {
            return Boolean.parseBoolean(explicit);
        }

        return !Boolean.getBoolean("latitude.debug.autoCreateWorldProbe.disable");
    }

    private static long getAutoCreateWorldProbeTimeoutMs() {
        String explicit = System.getProperty("latitude.debug.autoCreateWorldProbe.timeoutSeconds");
        if (explicit == null || explicit.isBlank()) {
            return 45_000L;
        }

        try {
            long seconds = Long.parseLong(explicit.trim());
            if (seconds <= 0L) {
                return 45_000L;
            }
            return seconds * 1000L;
        } catch (NumberFormatException e) {
            GlobeMod.LOGGER.warn("[LAT][CWPATH] invalid autoCreateWorldProbe timeoutSeconds='{}'; using default", explicit);
            return 45_000L;
        }
    }

    private static long getAutoCreateWorldProbePostEntryWaitTicks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.postEntryWaitTicks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_POST_ENTRY_WAIT_TICKS, 20, 600);
    }

    private static int getAutoCreateWorldProbeSpawnScanRadius() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.spawnScanRadius",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_SPAWN_SCAN_RADIUS, 128, 4096);
    }

    private static int getAutoCreateWorldProbeSpawnScanStep() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.spawnScanStep",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_SPAWN_SCAN_STEP, 8, 128);
    }

    private static boolean isAutoCreateWorldProbeCreativeEnabled() {
        String explicit = System.getProperty("latitude.debug.autoCreateWorldProbe.creative");
        if (explicit == null || explicit.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(explicit);
    }

    private static boolean isAutoCreateWorldProbeLatdevHereProbeEnabled() {
        return Boolean.getBoolean("latitude.debug.autoCreateWorldProbe.latdevHereProbe");
    }

    private static boolean isAutoCreateWorldProbeLatdevPreferCustomHereEnabled() {
        return Boolean.getBoolean("latitude.debug.autoCreateWorldProbe.latdevPreferCustomHere");
    }

    private static String getAutoCreateWorldProbeWorldName() {
        return trimmedProperty("latitude.debug.autoCreateWorldProbe.worldName");
    }

    private static String getAutoCreateWorldProbeSeed() {
        return trimmedProperty("latitude.debug.autoCreateWorldProbe.seed");
    }

    private static GlobeWorldSize getAutoCreateWorldProbeWorldSize() {
        String explicit = trimmedProperty("latitude.debug.autoCreateWorldProbe.worldSize");
        if (explicit == null) {
            return null;
        }
        String normalized = explicit.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("ITTY".equals(normalized) || "XSMALL".equals(normalized)) {
            return GlobeWorldSize.ITTY_BITTY;
        }
        if ("GINORMOUS".equals(normalized)) {
            return GlobeWorldSize.MASSIVE;
        }
        try {
            return GlobeWorldSize.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            GlobeMod.LOGGER.warn("[LAT][CWPATH] invalid autoCreateWorldProbe worldSize='{}'; ignoring", explicit);
            return null;
        }
    }

    private static String getAutoCreateWorldProbeLatdevTargetBiomeId() {
        String explicit = trimmedProperty("latitude.debug.autoCreateWorldProbe.latdevTargetBiomeId");
        if (explicit != null) {
            return explicit;
        }
        return trimmedProperty("latitude.debug.autoCreateWorldProbe.latdevTargetBiome");
    }

    private static boolean isAutoCreateWorldProbeLatdevTargetRequired() {
        String explicit = trimmedProperty("latitude.debug.autoCreateWorldProbe.latdevRequireTargetBiome");
        if (explicit != null) {
            return Boolean.parseBoolean(explicit);
        }
        return getAutoCreateWorldProbeLatdevTargetBiomeId() != null;
    }

    private static long getAutoCreateWorldProbeLatdevCommandWaitTicks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevCommandWaitTicks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_WAIT_TICKS, 1, 600);
    }

    private static int getAutoCreateWorldProbeLatdevProbeRadius() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevProbeRadius",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_RADIUS, 32, 8192);
    }

    private static int getAutoCreateWorldProbeLatdevProbeSamples() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevProbeSamples",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_SAMPLES, 10, 5000);
    }

    private static int getAutoCreateWorldProbeLatdevCustomScanRadius() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevCustomScanRadius",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_CUSTOM_SCAN_RADIUS, 32, 4096);
    }

    private static int getAutoCreateWorldProbeLatdevCustomScanStep() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevCustomScanStep",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_CUSTOM_SCAN_STEP, 4, 128);
    }

    private static long getAutoCreateWorldProbeLatdevTeleportWaitTicks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevTeleportWaitTicks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TELEPORT_WAIT_TICKS, 1, 600);
    }

    private static void applyAutoCreateWorldProbeInputs(LatitudeCreateWorldScreen screen) {
        String worldName = getAutoCreateWorldProbeWorldName();
        String seed = getAutoCreateWorldProbeSeed();
        GlobeWorldSize size = getAutoCreateWorldProbeWorldSize();
        if (worldName == null && seed == null && size == null) {
            return;
        }
        screen.probeSetWorldInputs(worldName, seed, size);
    }

    private static String trimmedProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static int getIntProperty(String name, int defaultValue, int minValue, int maxValue) {
        String explicit = System.getProperty(name);
        if (explicit == null || explicit.isBlank()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(explicit.trim());
            if (value < minValue) {
                return minValue;
            }
            if (value > maxValue) {
                return maxValue;
            }
            return value;
        } catch (NumberFormatException e) {
            GlobeMod.LOGGER.warn("[LAT][CWPATH] invalid {}='{}'; using default {}", name, explicit, defaultValue);
            return defaultValue;
        }
    }

    private static Double getDoubleProperty(String name) {
        String explicit = System.getProperty(name);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(explicit.trim());
        } catch (NumberFormatException e) {
            GlobeMod.LOGGER.warn("[LAT][CWPATH] invalid {}='{}'; ignoring configured target", name, explicit);
            return null;
        }
    }

    private static boolean sendAutoCreateWorldProbeCustomTeleport(Minecraft client) {
        if (client.level == null || client.player == null || client.player.connection == null) {
            return false;
        }
        if (sendAutoCreateWorldProbeConfiguredTeleport(client)) {
            return true;
        }
        if (getAutoCreateWorldProbeLatdevTargetBiomeId() != null) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] target biome was requested but configured target teleport was unavailable; not falling back to loaded-chunk scan");
            stopAutoCreateWorldProbeClient(client);
            return true;
        }
        int radius = getAutoCreateWorldProbeLatdevCustomScanRadius();
        int step = getAutoCreateWorldProbeLatdevCustomScanStep();
        CustomBiomeHit hit = scanForNearestCustomBiome(client.level, client.player.blockPosition(), radius, step);
        if (hit == null) {
            GlobeMod.LOGGER.info("[LAT][CWPATH] no loaded custom biome found before latdev proof commands radius={} step={}",
                    radius, step);
            return false;
        }
        String command = String.format(Locale.ROOT, "tp @s %.1f ~ %.1f", hit.x + 0.5, hit.z + 0.5);
        GlobeMod.LOGGER.info("[LAT][CWPATH] teleporting player to loaded custom biome before latdev proof: biome={} x={} z={} dist={}",
                hit.biomeId, hit.x, hit.z, String.format(Locale.ROOT, "%.1f", hit.distanceBlocks));
        client.player.connection.sendCommand(command);
        autoCreateWorldProbeLatdevCustomTeleportGameTime = client.level.getGameTime();
        return true;
    }

    private static boolean sendAutoCreateWorldProbeConfiguredTeleport(Minecraft client) {
        Double x = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetX");
        Double z = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetZ");
        if (x == null || z == null) {
            return false;
        }
        Double y = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetY");
        double targetY = y != null ? y : client.player.getY();
        String expectedBiome = getAutoCreateWorldProbeLatdevTargetBiomeId();
        if (sendAutoCreateWorldProbeServerSurfaceTeleport(client, x, y, z, expectedBiome)) {
            return true;
        }
        String command = String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", x, targetY, z);
        GlobeMod.LOGGER.info("[LAT][CWPATH] teleporting player to configured latdev proof target: x={} y={} z={} expectedBiome={}",
                String.format(Locale.ROOT, "%.1f", x),
                String.format(Locale.ROOT, "%.1f", targetY),
                String.format(Locale.ROOT, "%.1f", z),
                expectedBiome != null ? expectedBiome : "<unspecified>");
        client.player.connection.sendCommand(command);
        autoCreateWorldProbeLatdevCustomTeleportGameTime = client.level.getGameTime();
        return true;
    }

    private static boolean waitForAutoCreateWorldProbePlayableClient(Minecraft client) {
        return client.screen != null;
    }

    private static boolean sendAutoCreateWorldProbeServerSurfaceTeleport(Minecraft client,
                                                                         double x,
                                                                         Double requestedY,
                                                                         double z,
                                                                         String expectedBiome) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return false;
        }

        UUID playerId = client.player.getUUID();
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        autoCreateWorldProbeLatdevServerTargetPending = true;
        autoCreateWorldProbeLatdevServerTargetComplete = false;
        autoCreateWorldProbeLatdevServerTargetFailed = false;
        autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
        GlobeMod.LOGGER.info("[LAT][CWPATH] configured proof target queued for server-safe surface teleport chunk={},{} blockX={} requestedY={} blockZ={} expectedBiome={}",
                chunkX,
                chunkZ,
                blockX,
                requestedY != null ? String.format(Locale.ROOT, "%.1f", requestedY) : "<surface>",
                blockZ,
                expectedBiome != null ? expectedBiome : "<unspecified>");

        server.execute(() -> {
            try {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer == null) {
                    autoCreateWorldProbeLatdevServerTargetFailed = true;
                    GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured proof target surface teleport could not find server player expectedBiome={}",
                            expectedBiome != null ? expectedBiome : "<unspecified>");
                    return;
                }

                ServerLevel level = serverPlayer.level();
                ChunkGenerator generator = level.getChunkSource().getGenerator();
                if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
                    autoCreateWorldProbeLatdevServerTargetFailed = true;
                    GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured proof target surface teleport requires a noise chunk generator expectedBiome={}",
                            expectedBiome != null ? expectedBiome : "<unspecified>");
                    return;
                }

                RandomState randomState = level.getChunkSource().randomState();
                int loadedChunks = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
                        loadedChunks++;
                    }
                }
                int topY = noiseGenerator.getBaseHeight(
                        blockX, blockZ, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, randomState);
                int worldMaxY = level.getMinY() + level.getHeight() - 1;
                int safeY = Math.max(level.getMinY() + 1, Math.min(topY + 1, worldMaxY));
                serverPlayer.teleportTo(level,
                        x,
                        safeY,
                        z,
                        EnumSet.noneOf(Relative.class),
                        serverPlayer.getYRot(),
                        serverPlayer.getXRot(),
                        true);
                serverPlayer.setDeltaMovement(0.0, 0.0, 0.0);
                serverPlayer.fallDistance = 0.0F;
                autoCreateWorldProbeLatdevServerTargetComplete = true;
                GlobeMod.LOGGER.info("[LAT][CWPATH] server-safe configured proof target teleport complete chunk={},{} loadedChunks={} x={} requestedY={} safeY={} z={} biome={} expectedBiome={}",
                        chunkX,
                        chunkZ,
                        loadedChunks,
                        String.format(Locale.ROOT, "%.1f", x),
                        requestedY != null ? String.format(Locale.ROOT, "%.1f", requestedY) : "<surface>",
                        safeY,
                        String.format(Locale.ROOT, "%.1f", z),
                        "<deferred>",
                        expectedBiome != null ? expectedBiome : "<unspecified>");
            } catch (RuntimeException e) {
                autoCreateWorldProbeLatdevServerTargetFailed = true;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured proof target surface teleport failed chunk={},{} expectedBiome={}",
                        chunkX,
                        chunkZ,
                        expectedBiome != null ? expectedBiome : "<unspecified>",
                        e);
            } finally {
                autoCreateWorldProbeLatdevServerTargetPending = false;
            }
        });
        return true;
    }

    private static boolean waitForAutoCreateWorldProbeServerTargetSetup(Minecraft client) {
        if (autoCreateWorldProbeLatdevServerTargetFailed) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured proof target surface teleport failed before biome verification");
            LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
            stopAutoCreateWorldProbeClient(client);
            return true;
        }
        if (autoCreateWorldProbeLatdevServerTargetPending) {
            return true;
        }
        if (autoCreateWorldProbeLatdevServerTargetComplete) {
            if (client.level == null) {
                return true;
            }
            autoCreateWorldProbeLatdevServerTargetComplete = false;
            autoCreateWorldProbeLatdevCustomTeleportGameTime = client.level.getGameTime();
            GlobeMod.LOGGER.info("[LAT][CWPATH] client observed server-safe proof target teleport complete; waiting {} ticks before target biome verification",
                    getAutoCreateWorldProbeLatdevTeleportWaitTicks());
            return true;
        }
        return false;
    }

    private static boolean verifyAutoCreateWorldProbeTargetBiome(Minecraft client) {
        String expectedBiome = getAutoCreateWorldProbeLatdevTargetBiomeId();
        if (expectedBiome == null || !isAutoCreateWorldProbeLatdevTargetRequired()) {
            return true;
        }
        if (autoCreateWorldProbeLatdevTargetVerified) {
            return true;
        }
        if (client.level == null || client.player == null) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] cannot verify target biome because client world/player is unavailable expected={}",
                    expectedBiome);
            return false;
        }
        String actualBiome = biomeId(client.level.getBiome(client.player.blockPosition()));
        if (expectedBiome.equals(actualBiome)) {
            autoCreateWorldProbeLatdevTargetVerified = true;
            GlobeMod.LOGGER.info("[LAT][CWPATH] configured latdev proof target verified: biome={} x={} y={} z={}",
                    actualBiome,
                    String.format(Locale.ROOT, "%.1f", client.player.getX()),
                    String.format(Locale.ROOT, "%.1f", client.player.getY()),
                    String.format(Locale.ROOT, "%.1f", client.player.getZ()));
            return true;
        }
        GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target biome mismatch expected={} actual={} x={} y={} z={}",
                expectedBiome,
                actualBiome,
                String.format(Locale.ROOT, "%.1f", client.player.getX()),
                String.format(Locale.ROOT, "%.1f", client.player.getY()),
                String.format(Locale.ROOT, "%.1f", client.player.getZ()));
        return false;
    }

    private static void sendAutoCreateWorldProbeLatdevCommands(Minecraft client) {
        if (client.level == null || client.player == null || client.player.connection == null) {
            return;
        }
        int radius = getAutoCreateWorldProbeLatdevProbeRadius();
        int samples = getAutoCreateWorldProbeLatdevProbeSamples();
        String probeCommand = "latdev probe " + radius + " " + samples;
        GlobeMod.LOGGER.info("[LAT][CWPATH] sending player latdev proof commands: latdev here; {}", probeCommand);
        client.player.connection.sendCommand("latdev here");
        client.player.connection.sendCommand(probeCommand);
        autoCreateWorldProbeLatdevCommandsSent = true;
        autoCreateWorldProbeLatdevCommandsSentGameTime = client.level.getGameTime();
    }

    private static CustomBiomeHit scanForNearestCustomBiome(net.minecraft.world.level.Level world, BlockPos centerPos, int radiusBlocks, int stepBlocks) {
        if (world == null || centerPos == null) {
            return null;
        }
        int radius = Math.max(1, radiusBlocks);
        int step = Math.max(1, stepBlocks);
        int centerX = centerPos.getX();
        int centerY = centerPos.getY();
        int centerZ = centerPos.getZ();
        CustomBiomeHit best = null;

        for (int dz = -radius; dz <= radius; dz += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if (!world.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                BlockPos samplePos = new BlockPos(x, centerY, z);
                String id = biomeId(world.getBiome(samplePos));
                if (id.equals("unknown") || id.startsWith("minecraft:")) {
                    continue;
                }

                double dist = Math.hypot(dx, dz);
                if (best == null || dist < best.distanceBlocks) {
                    best = new CustomBiomeHit(x, z, dist, id);
                }
            }
        }

        return best;
    }

    private static void captureSpawnProbeDiagnostics(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }

        var world = client.level;
        var player = client.player;
        BlockPos playerPos = player.blockPosition();
        WorldBorder border = world.getWorldBorder();
        IntegratedServer integratedServer = client.getSingleplayerServer();
        long worldSeed = integratedServer != null
                ? integratedServer.getWorldGenSettings().options().seed()
                : 0L;
        int effectiveRadius = GlobeWorldSizeRuntime.borderRadiusBlocks(world, DEFAULT_AUTO_CREATE_WORLD_PROBE_SPAWN_SCAN_RADIUS);

        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] seed={}", worldSeed);
        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] effectiveRadius={}", effectiveRadius);

        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] player x={} y={} z={}",
                player.getX(), player.getY(), player.getZ());

        LevelData.RespawnData respawnData = world.getRespawnData();
        if (respawnData != null && respawnData.pos() != null) {
            BlockPos spawnPos = respawnData.pos();
            GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] spawn x={} y={} z={} dimension={}",
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), respawnData.dimension());
        } else {
            GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] spawn unavailable");
        }

        Holder<Biome> playerBiome = world.getBiome(playerPos);
        String playerBiomeId = biomeId(playerBiome);
        double absLatDeg = LatitudeMath.absLatDegExact(border, player.getZ());
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg);
        char hemisphere = LatitudeMath.hemisphere(border, player.getZ());

        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] biome={} at player", playerBiomeId);
        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] {}",
                String.format(Locale.ROOT, "latDeg=%.2f hemi=%s band=%s", absLatDeg, hemisphere, band));

        int scanRadius = effectiveRadius;
        int scanStep = getAutoCreateWorldProbeSpawnScanStep();
        GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] scanRadius={} step={}", scanRadius, scanStep);

        MangroveHit nearestMangrove = scanForMangrove(world, playerPos, scanRadius, scanStep);
        if (nearestMangrove != null) {
            GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] {}",
                    String.format(Locale.ROOT,
                            "nearestMangrove x=%d z=%d dist=%.1f biome=%s latDeg=%.2f band=%s %s",
                            nearestMangrove.x,
                            nearestMangrove.z,
                            nearestMangrove.distanceBlocks,
                            nearestMangrove.biomeId,
                            nearestMangrove.latDeg,
                            nearestMangrove.band,
                            nearestMangrove.oceanContext));
        } else {
            GlobeMod.LOGGER.info("[LAT][SPAWNPROBE] nearestMangrove not found within radius={}", scanRadius);
        }
    }

    private static MangroveHit scanForMangrove(net.minecraft.world.level.Level world, BlockPos centerPos, int radiusBlocks, int stepBlocks) {
        if (world == null || centerPos == null) {
            return null;
        }
        int radius = Math.max(1, radiusBlocks);
        int step = Math.max(1, stepBlocks);
        int centerX = centerPos.getX();
        int centerY = centerPos.getY();
        int centerZ = centerPos.getZ();
        MangroveHit best = null;

        for (int dz = -radius; dz <= radius; dz += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if (!world.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                BlockPos samplePos = new BlockPos(x, centerY, z);
                Holder<Biome> biome = world.getBiome(samplePos);
                if (!biome.is(MANGROVE_SWAMP_ID)) {
                    continue;
                }

                double dist = Math.hypot(dx, dz);
                double latDeg = LatitudeMath.absLatDegExact(world.getWorldBorder(), z);
                LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(latDeg);
                MangroveHit hit = new MangroveHit(x, z, dist, biomeId(biome), latDeg, band,
                        scanOceanContext(world, samplePos, Math.min(256, radius / 2), Math.max(16, step)));
                if (best == null || hit.distanceBlocks < best.distanceBlocks) {
                    best = hit;
                }
            }
        }

        return best;
    }

    private static String scanOceanContext(net.minecraft.world.level.Level world, BlockPos centerPos, int radiusBlocks, int stepBlocks) {
        if (world == null || centerPos == null) {
            return "unavailable";
        }

        int radius = Math.max(1, radiusBlocks);
        int step = Math.max(1, stepBlocks);
        int centerX = centerPos.getX();
        int centerY = centerPos.getY();
        int centerZ = centerPos.getZ();
        double bestDist = Double.POSITIVE_INFINITY;
        String bestBiome = null;

        for (int dz = -radius; dz <= radius; dz += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if (!world.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                BlockPos samplePos = new BlockPos(x, centerY, z);
                Holder<Biome> biome = world.getBiome(samplePos);
                if (!biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) {
                    continue;
                }

                double dist = Math.hypot(dx, dz);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestBiome = biomeId(biome);
                }
            }
        }

        if (!Double.isFinite(bestDist)) {
            return "oceanNearby=false";
        }
        return String.format(Locale.ROOT, "oceanNearby=true dist=%.1f biome=%s", bestDist, bestBiome);
    }

    private static String biomeId(Holder<Biome> biome) {
        if (biome == null) {
            return "unknown";
        }
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
    }

    private record MangroveHit(int x, int z, double distanceBlocks, String biomeId, double latDeg, LatitudeBands.Band band, String oceanContext) {
    }

    private record CustomBiomeHit(int x, int z, double distanceBlocks, String biomeId) {
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
