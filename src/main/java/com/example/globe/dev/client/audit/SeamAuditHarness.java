package com.example.globe.dev.client.audit;

import com.example.globe.GlobeMod;
import com.example.globe.dev.audit.AutonomousSeamAuditJob;
import com.example.globe.dev.audit.SeamAuditMode;
import com.example.globe.dev.client.SeamAuditClientBridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.server.IntegratedServer;

/**
 * Client-side startup owner of the autonomous seam-audit harness.
 *
 * <p>On the first {@link ClientPlayConnectionEvents#JOIN JOIN} event after
 * audit mode is enabled, this harness:
 * <ol>
 *   <li>Installs the screenshot bridge (idempotent).</li>
 *   <li>Clamps the client view distance to {@link SeamAuditMode#RENDER_DISTANCE}
 *       (saving the original for restore).</li>
 *   <li>Schedules {@link AutonomousSeamAuditJob#start} on the integrated-server
 *       thread via {@link IntegratedServer#execute}.</li>
 *   <li>On completion, restores the view distance and optionally calls
 *       {@link Minecraft#stop()}.</li>
 * </ol>
 *
 * <p>All behavior is audit-mode-only. In non-audit dev runs or shipped builds,
 * this class is a no-op.
 */
public final class SeamAuditHarness {
    private static boolean initialized;
    private static volatile boolean fired;
    private static int savedViewDistance = -1;

    private SeamAuditHarness() {
    }

    public static void init() {
        if (initialized) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        if (!SeamAuditMode.ENABLED) return;
        initialized = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));

        GlobeMod.LOGGER.info("[auditMode] seam audit harness armed (bandPair={}, edge={}, samples={}, renderDistance={}, exitWhenDone={})",
                SeamAuditMode.BAND_PAIR,
                SeamAuditMode.EDGE,
                SeamAuditMode.SAMPLES,
                SeamAuditMode.RENDER_DISTANCE,
                SeamAuditMode.EXIT_WHEN_DONE);
    }

    private static void onJoin(Minecraft client) {
        if (fired) return;
        if (client == null) return;
        fired = true;

        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            GlobeMod.LOGGER.warn("[auditMode] JOIN without integrated server; autonomous audit requires single-player dev world");
            return;
        }

        // Make sure the screenshot bridge is installed (it is normally installed
        // earlier in onInitializeClient, but we want to be robust if audit mode
        // runs without that path).
        SeamAuditClientBridge.init();

        applyClientSettings(client);

        server.execute(() ->
                AutonomousSeamAuditJob.start(
                        server,
                        SeamAuditMode.BAND_PAIR,
                        SeamAuditMode.EDGE,
                        SeamAuditMode.SAMPLES,
                        SeamAuditMode.ALONG_SEAM_SPAN,
                        SeamAuditMode.CROSS_SEAM_HALF_WIDTH,
                        SeamAuditMode.LOADED_RATIO,
                        SeamAuditMode.SETTLE_TICKS,
                        SeamAuditMode.CHUNKS_PER_TICK,
                        SeamAuditMode.READINESS_TIMEOUT_TICKS,
                        SeamAuditHarness::onJobComplete));
    }

    private static void onJobComplete() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            restoreClientSettings(client);
            if (SeamAuditMode.EXIT_WHEN_DONE) {
                GlobeMod.LOGGER.info("[auditMode] audit done; scheduling client stop (exitWhenDone=true)");
                client.stop();
            } else {
                GlobeMod.LOGGER.info("[auditMode] audit done; exitWhenDone=false — client remains open");
            }
        });
    }

    private static void applyClientSettings(Minecraft client) {
        Options options = client.options;
        if (options == null) return;
        try {
            savedViewDistance = options.renderDistance().get();
            int target = Math.max(2, SeamAuditMode.RENDER_DISTANCE);
            options.renderDistance().set(target);
            GlobeMod.LOGGER.info("[auditMode] view distance saved={} applied={}", savedViewDistance, target);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[auditMode] failed to apply audit client settings", e);
        }
    }

    private static void restoreClientSettings(Minecraft client) {
        Options options = client.options;
        if (options == null || savedViewDistance < 0) return;
        try {
            options.renderDistance().set(savedViewDistance);
            GlobeMod.LOGGER.info("[auditMode] view distance restored to {}", savedViewDistance);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[auditMode] failed to restore view distance", e);
        } finally {
            savedViewDistance = -1;
        }
    }
}
