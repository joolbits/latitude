package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeWorldSize;
import com.example.globe.client.LatitudeClientState;
import com.example.globe.client.create.LatitudeCreateWorldScreen;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.GlobeWorldSizeRuntime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Dev-only auto-create-world probe harness. This class lives in the
 * {@code com.example.globe.dev} package which is excluded from the release jar,
 * so none of this code ships to users. It MUST only be referenced from inside an
 * {@code isDevelopmentEnvironment()} guard in production code.
 */
public final class AutoCreateWorldProbe {
    private AutoCreateWorldProbe() {
    }

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
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_VERIFY_EXTRA_WAIT_TICKS = 0;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_RADIUS = 1024;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_STEP = 4;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_MAX_SAMPLES = 50000;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_MAX_CHUNKS = 2048;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_CHUNKS_PER_BATCH = 4;
    private static final int DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_PROGRESS_CHUNKS = 64;

    private static boolean autoCreateWorldProbeLatdevCommandsSent;
    private static boolean autoCreateWorldProbeLatdevCustomTeleportAttempted;
    private static boolean autoCreateWorldProbeLatdevTargetVerified;
    private static boolean autoCreateWorldProbeLatdevTargetPendingLogged;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetSetupPending;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetSetupComplete;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved;
    private static volatile boolean autoCreateWorldProbeLatdevServerTargetSetupFailed;
    private static volatile boolean autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued;
    private static volatile LiveTargetSearchState autoCreateWorldProbeLatdevLiveTargetSearchState;
    private static long autoCreateWorldProbeLatdevCommandsSentGameTime = Long.MIN_VALUE;
    private static long autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
    private static long autoCreateWorldProbeLatdevCommandsSentWallTimeMs = Long.MIN_VALUE;
    private static long autoCreateWorldProbeLatdevCustomTeleportWallTimeMs = Long.MIN_VALUE;
    private static final boolean AUTO_CREATE_WORLD_PROBE_CREATIVE = isAutoCreateWorldProbeCreativeEnabled();

    public static void maybeRegister() {
        if (!isAutoCreateWorldProbeEnabled()) {
            return;
        }
        long timeoutMs = getAutoCreateWorldProbeTimeoutMs();
        autoCreateWorldProbeLatdevCommandsSent = false;
        autoCreateWorldProbeLatdevCustomTeleportAttempted = false;
        autoCreateWorldProbeLatdevTargetVerified = false;
        autoCreateWorldProbeLatdevTargetPendingLogged = false;
        autoCreateWorldProbeLatdevServerTargetSetupPending = false;
        autoCreateWorldProbeLatdevServerTargetSetupComplete = false;
        autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved = false;
        autoCreateWorldProbeLatdevServerTargetSetupFailed = false;
        autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued = false;
        autoCreateWorldProbeLatdevLiveTargetSearchState = null;
        autoCreateWorldProbeLatdevCommandsSentGameTime = Long.MIN_VALUE;
        autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
        autoCreateWorldProbeLatdevCommandsSentWallTimeMs = Long.MIN_VALUE;
        autoCreateWorldProbeLatdevCustomTeleportWallTimeMs = Long.MIN_VALUE;
        LatitudeClientState.resetAutoCreateWorldProbe(timeoutMs);
        GlobeMod.LOGGER.info("[LAT][CWPATH] autoCreateWorldProbe enabled timeout={}s creative={}",
                timeoutMs / 1000L, AUTO_CREATE_WORLD_PROBE_CREATIVE);
        ClientTickEvents.END_CLIENT_TICK.register(AutoCreateWorldProbe::autoCreateWorldProbeTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            autoCreateWorldProbeLatdevLiveTargetSearchState = null;
            autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued = false;
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
        });
    }

    private static void autoCreateWorldProbeTick(Minecraft client) {
        if (client == null) {
            return;
        }

        long timeoutMs = LatitudeClientState.getAutoCreateWorldProbeTimeoutMs();
        long startMs = LatitudeClientState.getAutoCreateWorldProbeStartMs();
        if (startMs > 0L && System.currentTimeMillis() - startMs >= timeoutMs) {
            if (!LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
                emitAutoCreateWorldProbeTimeoutDiagnostics(client, startMs, timeoutMs);
                LatitudeClientState.markAutoCreateWorldProbeTimedOut();
                client.stop();
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

        screen = clearAutoCreateWorldProbePauseScreen(client, screen);

        if (LatitudeClientState.isAutoCreateWorldProbeWorldEntered()
                && LatitudeClientState.isLatitudeWorldLoading()) {
            return;
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
                        if (!hasAutoCreateWorldProbeWaitElapsed(client,
                                autoCreateWorldProbeLatdevCustomTeleportGameTime,
                                autoCreateWorldProbeLatdevCustomTeleportWallTimeMs,
                                teleportWaitTicks)) {
                            return;
                        }
                        long targetVerifyExtraWaitTicks = getAutoCreateWorldProbeLatdevTargetVerifyExtraWaitTicks();
                        boolean finalTargetVerifyAttempt = targetVerifyExtraWaitTicks <= 0
                                || hasAutoCreateWorldProbeWaitElapsed(client,
                                autoCreateWorldProbeLatdevCustomTeleportGameTime,
                                autoCreateWorldProbeLatdevCustomTeleportWallTimeMs,
                                teleportWaitTicks + targetVerifyExtraWaitTicks);
                        if (!verifyAutoCreateWorldProbeTargetBiome(client, finalTargetVerifyAttempt)) {
                            if (!finalTargetVerifyAttempt) {
                                return;
                            }
                            LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
                            client.stop();
                            return;
                        }
                    }
                    if (!autoCreateWorldProbeLatdevCommandsSent) {
                        sendAutoCreateWorldProbeLatdevCommands(client);
                        return;
                    }
                    long commandWaitTicks = getAutoCreateWorldProbeLatdevCommandWaitTicks();
                    if (!hasAutoCreateWorldProbeWaitElapsed(client,
                            autoCreateWorldProbeLatdevCommandsSentGameTime,
                            autoCreateWorldProbeLatdevCommandsSentWallTimeMs,
                            commandWaitTicks)) {
                        return;
                    }
                }
                captureSpawnProbeDiagnostics(client);
                LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
                GlobeMod.LOGGER.info("[LAT][CWPATH] spawn diagnostics captured; stopping client");
                client.stop();
            }
        }

        if (LatitudeClientState.isAutoCreateWorldProbeTimedOut()) {
            return;
        }
    }

    private static Screen clearAutoCreateWorldProbePauseScreen(Minecraft client, Screen screen) {
        if (!(screen instanceof PauseScreen)
                || !LatitudeClientState.isAutoCreateWorldProbeWorldEntered()
                || LatitudeClientState.isAutoCreateWorldProbeDiagnosticsCaptured()
                || LatitudeClientState.isAutoCreateWorldProbeTimedOut()
                || client.level == null
                || client.player == null) {
            return screen;
        }

        GlobeMod.LOGGER.info("[LAT][CWPATH] clearing pause screen during autoCreateWorldProbe phase={} worldTime={}",
                LatitudeClientState.getAutoCreateWorldProbePhase(),
                client.level.getGameTime());
        client.setScreen(null);
        return client.screen;
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

    private static String getAutoCreateWorldProbeLatdevTargetBandRaw() {
        String explicit = trimmedProperty("latitude.debug.autoCreateWorldProbe.latdevTargetBand");
        if (explicit == null) {
            explicit = trimmedProperty("latitude.debug.autoCreateWorldProbe.latdevRequiredBand");
        }
        return explicit;
    }

    private static LatitudeBands.Band getAutoCreateWorldProbeLatdevTargetBand() {
        return LatitudeBands.fromCanonicalId(getAutoCreateWorldProbeLatdevTargetBandRaw());
    }

    private static String bandProofLabel(LatitudeBands.Band band) {
        return band != null ? band.id() : "<any>";
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

    private static boolean isAutoCreateWorldProbeLatdevLiveTargetSearchEnabled() {
        return Boolean.getBoolean("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearch");
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchRadius() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchRadius",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_RADIUS, 16, 7500);
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchStep() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchStep",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_STEP, 4, 128);
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchMaxSamples() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchMaxSamples",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_MAX_SAMPLES, 100, 250000);
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchMaxChunks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchMaxChunks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_MAX_CHUNKS, 1, 20000);
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchChunksPerBatch() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchChunksPerBatch",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_CHUNKS_PER_BATCH, 1, 64);
    }

    private static int getAutoCreateWorldProbeLatdevLiveTargetSearchProgressChunks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchProgressChunks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_SEARCH_PROGRESS_CHUNKS, 1, 4096);
    }

    private static long getAutoCreateWorldProbeLatdevTeleportWaitTicks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevTeleportWaitTicks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TELEPORT_WAIT_TICKS, 1, 600);
    }

    private static long getAutoCreateWorldProbeLatdevTargetVerifyExtraWaitTicks() {
        return getIntProperty("latitude.debug.autoCreateWorldProbe.latdevTargetVerifyExtraWaitTicks",
                DEFAULT_AUTO_CREATE_WORLD_PROBE_LATDEV_TARGET_VERIFY_EXTRA_WAIT_TICKS, 0, 1200);
    }

    private static boolean isAutoCreateWorldProbeLatdevForceLoadTargetEnabled() {
        return Boolean.getBoolean("latitude.debug.autoCreateWorldProbe.latdevForceLoadTarget");
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
        String targetBiomeId = getAutoCreateWorldProbeLatdevTargetBiomeId();
        if (targetBiomeId != null && sendAutoCreateWorldProbeServerLiveTargetSearch(client, targetBiomeId)) {
            return true;
        }
        if (targetBiomeId != null) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] target biome was requested but configured target teleport was unavailable; not falling back to loaded-chunk scan");
            client.stop();
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
        autoCreateWorldProbeLatdevCustomTeleportWallTimeMs = System.currentTimeMillis();
        return true;
    }

    private static boolean sendAutoCreateWorldProbeServerLiveTargetSearch(Minecraft client, String expectedBiome) {
        if (!isAutoCreateWorldProbeLatdevLiveTargetSearchEnabled() || expectedBiome == null) {
            return false;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null || client.level == null) {
            return false;
        }

        UUID playerId = client.player.getUUID();
        int centerX = liveTargetSearchCenterBlockX(client);
        int centerZ = liveTargetSearchCenterBlockZ(client);
        int sampleY = liveTargetSearchSampleY(client);
        int radius = getAutoCreateWorldProbeLatdevLiveTargetSearchRadius();
        int step = getAutoCreateWorldProbeLatdevLiveTargetSearchStep();
        int maxSamples = getAutoCreateWorldProbeLatdevLiveTargetSearchMaxSamples();
        int maxChunks = getAutoCreateWorldProbeLatdevLiveTargetSearchMaxChunks();
        int chunksPerBatch = getAutoCreateWorldProbeLatdevLiveTargetSearchChunksPerBatch();
        int progressChunks = getAutoCreateWorldProbeLatdevLiveTargetSearchProgressChunks();
        boolean forceLoad = isAutoCreateWorldProbeLatdevForceLoadTargetEnabled();
        String requiredBandRaw = getAutoCreateWorldProbeLatdevTargetBandRaw();
        LatitudeBands.Band requiredBand = getAutoCreateWorldProbeLatdevTargetBand();
        if (requiredBandRaw != null && requiredBand == null) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] invalid live target search band='{}' validBands={}",
                    requiredBandRaw, LatitudeBands.canonicalIds());
            client.stop();
            return true;
        }
        int localStep = liveTargetSearchLocalStep(step);
        ArrayDeque<Long> chunksToSearch = planLiveTargetSearchChunks(centerX, centerZ, radius, maxChunks);
        if (chunksToSearch.isEmpty()) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] [LATDEV_LIVE_TARGET_SEARCH] no chunks planned target={} centerX={} centerZ={} radius={} maxChunks={}",
                    expectedBiome, centerX, centerZ, radius, maxChunks);
            client.stop();
            return true;
        }
        autoCreateWorldProbeLatdevServerTargetSetupPending = true;
        autoCreateWorldProbeLatdevServerTargetSetupComplete = false;
        autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved = false;
        autoCreateWorldProbeLatdevServerTargetSetupFailed = false;
        autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued = false;
        autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
        LiveTargetSearchState searchState = new LiveTargetSearchState(
                playerId,
                expectedBiome,
                centerX,
                centerZ,
                sampleY,
                radius,
                step,
                localStep,
                maxSamples,
                maxChunks,
                chunksPerBatch,
                progressChunks,
                forceLoad,
                requiredBand,
                chunksToSearch);
        autoCreateWorldProbeLatdevLiveTargetSearchState = searchState;
        GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] queued target={} targetBand={} centerX={} centerZ={} y={} radius={} step={} localStep={} maxSamples={} maxChunks={} plannedChunks={} chunksPerBatch={} progressChunks={}",
                expectedBiome, bandProofLabel(requiredBand), centerX, centerZ, sampleY, radius, step, localStep, maxSamples, maxChunks,
                chunksToSearch.size(), chunksPerBatch, progressChunks);
        scheduleAutoCreateWorldProbeLiveTargetSearchBatch(server, searchState);
        return true;
    }

    private static void scheduleAutoCreateWorldProbeLiveTargetSearchBatch(IntegratedServer server,
                                                                          LiveTargetSearchState state) {
        if (server == null || state == null || autoCreateWorldProbeLatdevLiveTargetSearchState != state
                || autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued) {
            return;
        }
        autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued = true;
        server.execute(() -> runAutoCreateWorldProbeLiveTargetSearchBatch(server, state));
    }

    private static void runAutoCreateWorldProbeLiveTargetSearchBatch(IntegratedServer server,
                                                                     LiveTargetSearchState state) {
        try {
            if (autoCreateWorldProbeLatdevLiveTargetSearchState != state) {
                return;
            }
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(state.playerId);
            if (serverPlayer == null) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                autoCreateWorldProbeLatdevServerTargetSetupPending = false;
                autoCreateWorldProbeLatdevLiveTargetSearchState = null;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target search could not find server player expectedBiome={}",
                        state.expectedBiome);
                return;
            }

            ServerLevel level = serverPlayer.level();
            if (tryCompletePendingLiveTargetSearchHit(level, serverPlayer, state)) {
                return;
            }
            if (!state.startedLogged) {
                state.startedLogged = true;
                GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] started target={} targetBand={} centerX={} centerZ={} y={} radius={} step={} localStep={} maxSamples={} maxChunks={} plannedChunks={} chunksPerBatch={} level={}",
                        state.expectedBiome, bandProofLabel(state.requiredBand), state.centerX, state.centerZ, state.sampleY, state.radius, state.step,
                        state.localStep, state.maxSamples, state.maxChunks, state.plannedChunks,
                        state.chunksPerBatch, level.dimension().identifier());
            }

            LiveTargetSearchHit hit = null;
            int batchChunks = 0;
            while (batchChunks < state.chunksPerBatch
                    && !state.chunksToSearch.isEmpty()
                    && state.samples < state.maxSamples
                    && hit == null) {
                long packedChunk = state.chunksToSearch.removeFirst();
                int chunkX = unpackChunkX(packedChunk);
                int chunkZ = unpackChunkZ(packedChunk);
                hit = searchLiveTargetChunk(level, state, chunkX, chunkZ);
                batchChunks++;
            }

            if (hit != null) {
                if (beginOrCompleteLiveTargetSearchHit(level, serverPlayer, state, hit)) {
                    return;
                }
                return;
            }

            maybeLogLiveTargetSearchProgress(state);
            if (state.chunksToSearch.isEmpty() || state.samples >= state.maxSamples) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                autoCreateWorldProbeLatdevServerTargetSetupPending = false;
                autoCreateWorldProbeLatdevLiveTargetSearchState = null;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] [LATDEV_LIVE_TARGET_SEARCH] miss target={} targetBand={} centerX={} centerZ={} y={} radius={} step={} localStep={} samples={} chunksLoaded={} plannedChunks={} remainingChunks={} maxChunks={} bandRejectedHits={} elapsedMs={}",
                        state.expectedBiome, bandProofLabel(state.requiredBand), state.centerX, state.centerZ, state.sampleY, state.radius, state.step,
                        state.localStep, state.samples, state.chunksSearched, state.plannedChunks,
                        state.chunksToSearch.size(), state.maxChunks, state.bandRejectedHits, state.elapsedMs());
            }
        } catch (RuntimeException e) {
            autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
            autoCreateWorldProbeLatdevLiveTargetSearchState = null;
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target search failed target={} targetBand={} centerX={} centerZ={} y={} radius={} step={} samples={} chunksLoaded={} remainingChunks={} maxChunks={} bandRejectedHits={}",
                    state.expectedBiome, bandProofLabel(state.requiredBand), state.centerX, state.centerZ, state.sampleY, state.radius, state.step,
                    state.samples, state.chunksSearched, state.chunksToSearch.size(), state.maxChunks, state.bandRejectedHits, e);
        } finally {
            autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued = false;
        }
    }

    private static void completeLiveTargetSearchHit(ServerLevel level,
                                                    ServerPlayer serverPlayer,
                                                    LiveTargetSearchState state,
                                                    LiveTargetSearchHit hit) {
        int hitChunkX = Math.floorDiv(hit.x(), 16);
        int hitChunkZ = Math.floorDiv(hit.z(), 16);
        if (!state.pendingHitFullChunkRequested) {
            state.pendingHitFullChunkRequested = true;
            GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] request full chunk async chunk={},{} target={} targetBand={} forceLoadRequested={} action=await_full_chunk_future",
                    hitChunkX, hitChunkZ, state.expectedBiome, bandProofLabel(state.requiredBand), state.forceLoad);
            CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource()
                    .getChunkFuture(hitChunkX, hitChunkZ, ChunkStatus.FULL, true);
            future.whenComplete((result, throwable) -> level.getServer().execute(() ->
                    finishLiveTargetSearchHitAfterFullChunk(level, state, hit, result, throwable)));
            if (!state.pendingHitWaitingLogged) {
                state.pendingHitWaitingLogged = true;
                GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] hit pending full chunk target={} x={} z={} action=retry_next_tick",
                        state.expectedBiome, hit.x(), hit.z());
            }
            return;
        }

        if (!state.pendingHitWaitingLogged) {
            state.pendingHitWaitingLogged = true;
            GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] hit pending full chunk target={} x={} z={} action=await_existing_future",
                    state.expectedBiome, hit.x(), hit.z());
        }
    }

    private static void finishLiveTargetSearchHitAfterFullChunk(ServerLevel level,
                                                                LiveTargetSearchState state,
                                                                LiveTargetSearchHit hit,
                                                                ChunkResult<ChunkAccess> result,
                                                                Throwable throwable) {
        if (autoCreateWorldProbeLatdevLiveTargetSearchState != state || state.pendingHit != hit) {
            return;
        }
        if (throwable != null) {
            autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
            autoCreateWorldProbeLatdevLiveTargetSearchState = null;
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target async full chunk failed target={} x={} z={}",
                    state.expectedBiome, hit.x(), hit.z(), throwable);
            return;
        }
        ChunkAccess chunk = result != null ? result.orElse(null) : null;
        if (chunk == null) {
            autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
            autoCreateWorldProbeLatdevLiveTargetSearchState = null;
            String error = result != null ? result.getError() : "missing chunk result";
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target async full chunk unavailable target={} x={} z={} error={}",
                    state.expectedBiome, hit.x(), hit.z(), error);
            return;
        }
        ServerPlayer serverPlayer = level.getServer().getPlayerList().getPlayer(state.playerId);
        if (serverPlayer == null) {
            autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
            autoCreateWorldProbeLatdevLiveTargetSearchState = null;
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target async full chunk could not find server player expectedBiome={}",
                    state.expectedBiome);
            return;
        }
        finishLiveTargetSearchHit(level, serverPlayer, state, hit);
    }

    private static void finishLiveTargetSearchHit(ServerLevel level,
                                                  ServerPlayer serverPlayer,
                                                  LiveTargetSearchState state,
                                                  LiveTargetSearchHit hit) {
        SurfaceTeleportTarget surfaceTarget = prepareAutoCreateWorldProbeSurfaceTarget(level, hit.x(), hit.z());
        String verifyBiome = biomeId(level.getBiome(new BlockPos(hit.x(), surfaceTarget.safeY(), hit.z())));
        GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] hit target={} biome={} x={} sampleY={} safeY={} z={} latDeg={} band={} targetBand={} dist={} samples={} chunksLoaded={} loadedTeleportChunks={} plannedChunks={} bandRejectedHits={} elapsedMs={}",
                state.expectedBiome,
                verifyBiome,
                hit.x(),
                state.sampleY,
                surfaceTarget.safeY(),
                hit.z(),
                String.format(Locale.ROOT, "%.2f", hit.latDeg()),
                hit.band().id(),
                bandProofLabel(state.requiredBand),
                String.format(Locale.ROOT, "%.1f", hit.distanceBlocks()),
                state.samples,
                state.chunksSearched,
                surfaceTarget.loadedChunks(),
                state.plannedChunks,
                state.bandRejectedHits,
                state.elapsedMs());
        teleportAutoCreateWorldProbePlayerToSurface(level, serverPlayer, hit.x() + 0.5, surfaceTarget.safeY(), hit.z() + 0.5);
        autoCreateWorldProbeLatdevLiveTargetSearchState = null;
        autoCreateWorldProbeLatdevServerTargetSetupComplete = true;
        autoCreateWorldProbeLatdevServerTargetSetupPending = false;
        GlobeMod.LOGGER.info("[LAT][CWPATH] server teleported player to live target search hit: x={} sampleY={} safeY={} z={} expectedBiome={} expectedBand={}",
                String.format(Locale.ROOT, "%.1f", hit.x() + 0.5),
                state.sampleY,
                surfaceTarget.safeY(),
                String.format(Locale.ROOT, "%.1f", hit.z() + 0.5),
                state.expectedBiome,
                bandProofLabel(state.requiredBand));
    }

    private static boolean beginOrCompleteLiveTargetSearchHit(ServerLevel level,
                                                              ServerPlayer serverPlayer,
                                                              LiveTargetSearchState state,
                                                              LiveTargetSearchHit hit) {
        state.pendingHit = hit;
        completeLiveTargetSearchHit(level, serverPlayer, state, hit);
        return autoCreateWorldProbeLatdevLiveTargetSearchState == null;
    }

    private static boolean tryCompletePendingLiveTargetSearchHit(ServerLevel level,
                                                                 ServerPlayer serverPlayer,
                                                                 LiveTargetSearchState state) {
        if (state.pendingHit == null) {
            return false;
        }
        completeLiveTargetSearchHit(level, serverPlayer, state, state.pendingHit);
        return true;
    }

    private static void maybeLogLiveTargetSearchProgress(LiveTargetSearchState state) {
        if (state.chunksSearched - state.lastProgressChunks < state.progressChunks
                && !state.chunksToSearch.isEmpty()
                && state.samples < state.maxSamples) {
            return;
        }
        state.lastProgressChunks = state.chunksSearched;
        GlobeMod.LOGGER.info("[LATDEV_LIVE_TARGET_SEARCH] progress target={} targetBand={} samples={} chunksLoaded={} plannedChunks={} remainingChunks={} bandRejectedHits={} elapsedMs={}",
                state.expectedBiome,
                bandProofLabel(state.requiredBand),
                state.samples,
                state.chunksSearched,
                state.plannedChunks,
                state.chunksToSearch.size(),
                state.bandRejectedHits,
                state.elapsedMs());
    }

    private static ArrayDeque<Long> planLiveTargetSearchChunks(int centerX, int centerZ, int radius, int maxChunks) {
        ArrayDeque<Long> chunks = new ArrayDeque<>();
        int centerChunkX = Math.floorDiv(centerX, 16);
        int centerChunkZ = Math.floorDiv(centerZ, 16);
        int chunkRadius = Math.max(0, Math.floorDiv(radius + 15, 16));
        addLiveTargetSearchChunk(chunks, centerX, centerZ, radius, maxChunks, centerChunkX, centerChunkZ);
        for (int ring = 1; ring <= chunkRadius && chunks.size() < maxChunks; ring++) {
            for (int offset = -ring; offset <= ring && chunks.size() < maxChunks; offset++) {
                addLiveTargetSearchChunk(chunks, centerX, centerZ, radius, maxChunks,
                        centerChunkX + offset, centerChunkZ - ring);
                addLiveTargetSearchChunk(chunks, centerX, centerZ, radius, maxChunks,
                        centerChunkX + offset, centerChunkZ + ring);
            }
            for (int offset = -ring + 1; offset <= ring - 1 && chunks.size() < maxChunks; offset++) {
                addLiveTargetSearchChunk(chunks, centerX, centerZ, radius, maxChunks,
                        centerChunkX - ring, centerChunkZ + offset);
                addLiveTargetSearchChunk(chunks, centerX, centerZ, radius, maxChunks,
                        centerChunkX + ring, centerChunkZ + offset);
            }
        }
        return chunks;
    }

    private static void addLiveTargetSearchChunk(ArrayDeque<Long> chunks,
                                                 int centerX,
                                                 int centerZ,
                                                 int radius,
                                                 int maxChunks,
                                                 int chunkX,
                                                 int chunkZ) {
        if (chunks.size() >= maxChunks || !liveTargetSearchChunkIntersectsRadius(centerX, centerZ, radius, chunkX, chunkZ)) {
            return;
        }
        chunks.add(packChunk(chunkX, chunkZ));
    }

    private static boolean liveTargetSearchChunkIntersectsRadius(int centerX, int centerZ, int radius, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        long dx = centerX < minX ? (long) minX - centerX : (centerX > maxX ? (long) centerX - maxX : 0L);
        long dz = centerZ < minZ ? (long) minZ - centerZ : (centerZ > maxZ ? (long) centerZ - maxZ : 0L);
        long radiusLong = radius;
        return dx * dx + dz * dz <= radiusLong * radiusLong;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }

    private static int liveTargetSearchCenterBlockX(Minecraft client) {
        Double explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchCenterX");
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetSearchCenterX");
        }
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetX");
        }
        return explicit != null ? (int) Math.floor(explicit) : client.player.blockPosition().getX();
    }

    private static int liveTargetSearchCenterBlockZ(Minecraft client) {
        Double explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchCenterZ");
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetSearchCenterZ");
        }
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetZ");
        }
        return explicit != null ? (int) Math.floor(explicit) : client.player.blockPosition().getZ();
    }

    private static int liveTargetSearchSampleY(Minecraft client) {
        Double explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevLiveTargetSearchY");
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetSearchY");
        }
        if (explicit == null) {
            explicit = getDoubleProperty("latitude.debug.autoCreateWorldProbe.latdevTargetY");
        }
        return explicit != null ? (int) Math.floor(explicit) : client.player.blockPosition().getY();
    }

    private static int liveTargetSearchLocalStep(int configuredStep) {
        if (configuredStep <= 4) {
            return 4;
        }
        if (configuredStep <= 8) {
            return 8;
        }
        return 16;
    }

    private static LiveTargetSearchHit searchLiveTargetChunk(ServerLevel level,
                                                            LiveTargetSearchState state,
                                                            int chunkX,
                                                            int chunkZ) {
        state.chunksSearched++;
        level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, true);

        int firstLocal = state.localStep >= 16 ? 8 : (state.localStep >= 8 ? 4 : 2);
        for (int localZ = firstLocal; localZ < 16 && state.samples < state.maxSamples; localZ += state.localStep) {
            for (int localX = firstLocal; localX < 16 && state.samples < state.maxSamples; localX += state.localStep) {
                state.samples++;
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                String biome = biomeId(level.getBiome(new BlockPos(x, state.sampleY, z)));
                if (!state.expectedBiome.equals(biome)) {
                    continue;
                }
                double latDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), z);
                LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(latDeg);
                if (state.requiredBand != null && state.requiredBand != band) {
                    state.bandRejectedHits++;
                    continue;
                }
                double dist = Math.hypot(x - state.centerX, z - state.centerZ);
                return new LiveTargetSearchHit(x, z, dist, latDeg, band);
            }
        }
        return null;
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
        String command = String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", x, targetY, z);
        if (sendAutoCreateWorldProbeServerTargetSetup(client, x, targetY, z, expectedBiome)) {
            return true;
        }
        GlobeMod.LOGGER.info("[LAT][CWPATH] teleporting player to configured latdev proof target: x={} y={} z={} expectedBiome={}",
                String.format(Locale.ROOT, "%.1f", x),
                String.format(Locale.ROOT, "%.1f", targetY),
                String.format(Locale.ROOT, "%.1f", z),
                expectedBiome != null ? expectedBiome : "<unspecified>");
        client.player.connection.sendCommand(command);
        autoCreateWorldProbeLatdevCustomTeleportGameTime = client.level.getGameTime();
        autoCreateWorldProbeLatdevCustomTeleportWallTimeMs = System.currentTimeMillis();
        return true;
    }

    private static boolean sendAutoCreateWorldProbeServerTargetSetup(Minecraft client, double x, double y, double z,
                                                                     String expectedBiome) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null || client.level == null) {
            return false;
        }

        UUID playerId = client.player.getUUID();
        int targetBlockX = (int) Math.floor(x);
        int targetBlockZ = (int) Math.floor(z);
        int chunkX = Math.floorDiv(targetBlockX, 16);
        int chunkZ = Math.floorDiv(targetBlockZ, 16);
        boolean forceLoad = isAutoCreateWorldProbeLatdevForceLoadTargetEnabled();
        autoCreateWorldProbeLatdevServerTargetSetupPending = true;
        autoCreateWorldProbeLatdevServerTargetSetupComplete = false;
        autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved = false;
        autoCreateWorldProbeLatdevServerTargetSetupFailed = false;
        autoCreateWorldProbeLatdevCustomTeleportGameTime = Long.MIN_VALUE;
        GlobeMod.LOGGER.info("[LAT][CWPATH] server target setup queued chunk={},{} blockX={} blockZ={} expectedBiome={}",
                chunkX,
                chunkZ,
                targetBlockX,
                targetBlockZ,
                expectedBiome != null ? expectedBiome : "<unspecified>");
        server.execute(() -> {
            try {
                GlobeMod.LOGGER.info("[LAT][CWPATH] server target setup started chunk={},{} blockX={} blockZ={} expectedBiome={}",
                        chunkX,
                        chunkZ,
                        targetBlockX,
                        targetBlockZ,
                        expectedBiome != null ? expectedBiome : "<unspecified>");
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer == null) {
                    autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                    GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target setup could not find server player expectedBiome={}",
                            expectedBiome != null ? expectedBiome : "<unspecified>");
                    return;
                }
                ServerLevel level = serverPlayer.level();
                GlobeMod.LOGGER.info("[LAT][CWPATH] server requesting configured latdev proof target full chunk async chunk={},{} blockX={} blockZ={} forceLoadRequested={}",
                        chunkX, chunkZ, targetBlockX, targetBlockZ, forceLoad);
                CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource()
                        .getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true);
                future.whenComplete((result, throwable) -> server.execute(() ->
                        finishAutoCreateWorldProbeServerTargetSetup(level, playerId, x, y, z,
                                expectedBiome, targetBlockX, targetBlockZ, chunkX, chunkZ, result, throwable)));
            } catch (RuntimeException e) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                autoCreateWorldProbeLatdevServerTargetSetupPending = false;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target setup failed chunk={},{} expectedBiome={}",
                        chunkX,
                        chunkZ,
                        expectedBiome != null ? expectedBiome : "<unspecified>",
                        e);
            }
        });
        return true;
    }

    private static void finishAutoCreateWorldProbeServerTargetSetup(ServerLevel level,
                                                                    UUID playerId,
                                                                    double x,
                                                                    double y,
                                                                    double z,
                                                                    String expectedBiome,
                                                                    int targetBlockX,
                                                                    int targetBlockZ,
                                                                    int chunkX,
                                                                    int chunkZ,
                                                                    ChunkResult<ChunkAccess> result,
                                                                    Throwable throwable) {
        try {
            if (throwable != null) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target async full chunk failed chunk={},{} expectedBiome={}",
                        chunkX,
                        chunkZ,
                        expectedBiome != null ? expectedBiome : "<unspecified>",
                        throwable);
                return;
            }
            ChunkAccess chunk = result != null ? result.orElse(null) : null;
            if (chunk == null) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                String error = result != null ? result.getError() : "missing chunk result";
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target async full chunk unavailable chunk={},{} expectedBiome={} error={}",
                        chunkX,
                        chunkZ,
                        expectedBiome != null ? expectedBiome : "<unspecified>",
                        error);
                return;
            }
            ServerPlayer serverPlayer = level.getServer().getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target setup could not find server player expectedBiome={}",
                        expectedBiome != null ? expectedBiome : "<unspecified>");
                return;
            }
            SurfaceTeleportTarget surfaceTarget = prepareAutoCreateWorldProbeSurfaceTarget(level, targetBlockX, targetBlockZ);
            String serverBiome = biomeId(level.getBiome(new BlockPos(targetBlockX, surfaceTarget.safeY(), targetBlockZ)));
            GlobeMod.LOGGER.info("[LAT][CWPATH] server loaded configured latdev proof target async chunk={},{} biome={} blockX={} requestedY={} safeY={} blockZ={} loadedTeleportChunks={}",
                    chunkX,
                    chunkZ,
                    serverBiome,
                    targetBlockX,
                    (int) Math.floor(y),
                    surfaceTarget.safeY(),
                    targetBlockZ,
                    surfaceTarget.loadedChunks());
            teleportAutoCreateWorldProbePlayerToSurface(level, serverPlayer, x, surfaceTarget.safeY(), z);
            autoCreateWorldProbeLatdevServerTargetSetupComplete = true;
            GlobeMod.LOGGER.info("[LAT][CWPATH] server teleported player to configured latdev proof target: x={} requestedY={} safeY={} z={} expectedBiome={}",
                    String.format(Locale.ROOT, "%.1f", x),
                    String.format(Locale.ROOT, "%.1f", y),
                    surfaceTarget.safeY(),
                    String.format(Locale.ROOT, "%.1f", z),
                    expectedBiome != null ? expectedBiome : "<unspecified>");
        } finally {
            autoCreateWorldProbeLatdevServerTargetSetupPending = false;
        }
    }

    private static SurfaceTeleportTarget prepareAutoCreateWorldProbeSurfaceTarget(ServerLevel level, int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        int loadedChunks = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
                loadedChunks++;
            }
        }
        BlockPos ground = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(blockX, level.getMinY(), blockZ));
        int worldMaxY = level.getMinY() + level.getHeight() - 1;
        int safeY = Math.max(level.getMinY() + 1, Math.min(ground.getY() + 1, worldMaxY));
        return new SurfaceTeleportTarget(safeY, loadedChunks);
    }

    private static void teleportAutoCreateWorldProbePlayerToSurface(ServerLevel level,
                                                                    ServerPlayer serverPlayer,
                                                                    double x,
                                                                    int safeY,
                                                                    double z) {
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
    }

    private static boolean waitForAutoCreateWorldProbeServerTargetSetup(Minecraft client) {
        if (autoCreateWorldProbeLatdevServerTargetSetupFailed) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target setup failed before biome verification");
            LatitudeClientState.markAutoCreateWorldProbeDiagnosticsCaptured();
            client.stop();
            return true;
        }
        LiveTargetSearchState liveTargetSearchState = autoCreateWorldProbeLatdevLiveTargetSearchState;
        if (liveTargetSearchState != null
                && autoCreateWorldProbeLatdevServerTargetSetupPending
                && !autoCreateWorldProbeLatdevLiveTargetSearchBatchQueued) {
            IntegratedServer server = client.getSingleplayerServer();
            if (server == null) {
                autoCreateWorldProbeLatdevServerTargetSetupFailed = true;
                autoCreateWorldProbeLatdevServerTargetSetupPending = false;
                autoCreateWorldProbeLatdevLiveTargetSearchState = null;
                GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] live target search lost integrated server target={}",
                        liveTargetSearchState.expectedBiome);
                return true;
            }
            scheduleAutoCreateWorldProbeLiveTargetSearchBatch(server, liveTargetSearchState);
        }
        if (autoCreateWorldProbeLatdevServerTargetSetupPending) {
            return true;
        }
        if (autoCreateWorldProbeLatdevServerTargetSetupComplete
                && !autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved) {
            if (client.level == null) {
                return true;
            }
            autoCreateWorldProbeLatdevServerTargetSetupCompleteObserved = true;
            autoCreateWorldProbeLatdevTargetPendingLogged = false;
            autoCreateWorldProbeLatdevCustomTeleportGameTime = client.level.getGameTime();
            autoCreateWorldProbeLatdevCustomTeleportWallTimeMs = System.currentTimeMillis();
            GlobeMod.LOGGER.info("[LAT][CWPATH] client observed server target setup complete; waiting {} ticks before target biome verification",
                    getAutoCreateWorldProbeLatdevTeleportWaitTicks());
            return true;
        }
        return false;
    }

    private static boolean verifyAutoCreateWorldProbeTargetBiome(Minecraft client, boolean finalAttempt) {
        String expectedBiome = getAutoCreateWorldProbeLatdevTargetBiomeId();
        if (expectedBiome == null || !isAutoCreateWorldProbeLatdevTargetRequired()) {
            return true;
        }
        if (autoCreateWorldProbeLatdevTargetVerified) {
            return true;
        }
        if (client.level == null || client.player == null) {
            if (!finalAttempt) {
                return false;
            }
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] cannot verify target biome because client world/player is unavailable expected={}",
                    expectedBiome);
            return false;
        }
        String actualBiome = biomeId(client.level.getBiome(client.player.blockPosition()));
        String expectedBandRaw = getAutoCreateWorldProbeLatdevTargetBandRaw();
        LatitudeBands.Band expectedBand = getAutoCreateWorldProbeLatdevTargetBand();
        if (expectedBandRaw != null && expectedBand == null) {
            GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] invalid configured latdev proof target band='{}' validBands={}",
                    expectedBandRaw, LatitudeBands.canonicalIds());
            return false;
        }
        double actualLatDeg = LatitudeMath.absLatDegExact(client.level.getWorldBorder(), client.player.getZ());
        LatitudeBands.Band actualBand = LatitudeBands.fromAbsoluteLatitudeDeg(actualLatDeg);
        boolean bandMatches = expectedBand == null || expectedBand == actualBand;
        if (expectedBiome.equals(actualBiome) && bandMatches) {
            autoCreateWorldProbeLatdevTargetVerified = true;
            GlobeMod.LOGGER.info("[LAT][CWPATH] configured latdev proof target verified: biome={} latDeg={} band={} targetBand={} x={} y={} z={}",
                    actualBiome,
                    String.format(Locale.ROOT, "%.2f", actualLatDeg),
                    actualBand.id(),
                    bandProofLabel(expectedBand),
                    String.format(Locale.ROOT, "%.1f", client.player.getX()),
                    String.format(Locale.ROOT, "%.1f", client.player.getY()),
                    String.format(Locale.ROOT, "%.1f", client.player.getZ()));
            return true;
        }
        if (!finalAttempt) {
            if (!autoCreateWorldProbeLatdevTargetPendingLogged) {
                autoCreateWorldProbeLatdevTargetPendingLogged = true;
                GlobeMod.LOGGER.info("[LAT][CWPATH] configured latdev proof target pending expected={} actual={} expectedBand={} actualBand={} latDeg={} x={} y={} z={} action=retry",
                        expectedBiome,
                        actualBiome,
                        bandProofLabel(expectedBand),
                        actualBand.id(),
                        String.format(Locale.ROOT, "%.2f", actualLatDeg),
                        String.format(Locale.ROOT, "%.1f", client.player.getX()),
                        String.format(Locale.ROOT, "%.1f", client.player.getY()),
                        String.format(Locale.ROOT, "%.1f", client.player.getZ()));
            }
            return false;
        }
        GlobeMod.LOGGER.error("[LAT][CWPATH][PROOF_FAIL] configured latdev proof target mismatch expectedBiome={} actualBiome={} expectedBand={} actualBand={} latDeg={} x={} y={} z={}",
                expectedBiome,
                actualBiome,
                bandProofLabel(expectedBand),
                actualBand.id(),
                String.format(Locale.ROOT, "%.2f", actualLatDeg),
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
        autoCreateWorldProbeLatdevCommandsSentWallTimeMs = System.currentTimeMillis();
    }

    private static boolean hasAutoCreateWorldProbeWaitElapsed(Minecraft client,
                                                              long startGameTime,
                                                              long startWallTimeMs,
                                                              long waitTicks) {
        if (waitTicks <= 0L) {
            return true;
        }
        if (client.level != null && startGameTime != Long.MIN_VALUE) {
            long currentGameTime = client.level.getGameTime();
            if (currentGameTime >= startGameTime && currentGameTime - startGameTime >= waitTicks) {
                return true;
            }
        }
        return startWallTimeMs != Long.MIN_VALUE
                && System.currentTimeMillis() - startWallTimeMs >= waitTicks * 50L;
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

    private static final class LiveTargetSearchState {
        private final UUID playerId;
        private final String expectedBiome;
        private final int centerX;
        private final int centerZ;
        private final int sampleY;
        private final int radius;
        private final int step;
        private final int localStep;
        private final int maxSamples;
        private final int maxChunks;
        private final int chunksPerBatch;
        private final int progressChunks;
        private final boolean forceLoad;
        private final LatitudeBands.Band requiredBand;
        private final ArrayDeque<Long> chunksToSearch;
        private final int plannedChunks;
        private final long startedMs;
        private boolean startedLogged;
        private int samples;
        private int chunksSearched;
        private int lastProgressChunks;
        private int bandRejectedHits;
        private LiveTargetSearchHit pendingHit;
        private boolean pendingHitFullChunkRequested;
        private boolean pendingHitWaitingLogged;

        private LiveTargetSearchState(UUID playerId,
                                      String expectedBiome,
                                      int centerX,
                                      int centerZ,
                                      int sampleY,
                                      int radius,
                                      int step,
                                      int localStep,
                                      int maxSamples,
                                      int maxChunks,
                                      int chunksPerBatch,
                                      int progressChunks,
                                      boolean forceLoad,
                                      LatitudeBands.Band requiredBand,
                                      ArrayDeque<Long> chunksToSearch) {
            this.playerId = playerId;
            this.expectedBiome = expectedBiome;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.sampleY = sampleY;
            this.radius = radius;
            this.step = step;
            this.localStep = localStep;
            this.maxSamples = maxSamples;
            this.maxChunks = maxChunks;
            this.chunksPerBatch = chunksPerBatch;
            this.progressChunks = progressChunks;
            this.forceLoad = forceLoad;
            this.requiredBand = requiredBand;
            this.chunksToSearch = chunksToSearch;
            this.plannedChunks = chunksToSearch.size();
            this.startedMs = System.currentTimeMillis();
        }

        private long elapsedMs() {
            return Math.max(0L, System.currentTimeMillis() - startedMs);
        }
    }

    private record LiveTargetSearchHit(int x, int z, double distanceBlocks, double latDeg, LatitudeBands.Band band) {
    }

    private record SurfaceTeleportTarget(int safeY, int loadedChunks) {
    }
}
