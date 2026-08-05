package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.ClipboardImageWriter;
import com.example.globe.client.ClipboardImageWriter.ClipboardCopyResult;
import com.example.globe.client.LatitudeConfig;
import com.example.globe.util.LatitudeMath;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DevCaptureKeybind {
    private static final long DEBOUNCE_MS = 300L;
    private static final String CAPTURE_CSV_HEADER =
            "timestamp,file,screenshot_sha256,case_id,session_id,session_sequence,world_tick,"
                    + "dimension,seed,biome,signed_latitude_degrees,zone,x,y,z,yaw,pitch,"
                    + "gui_scale,artifact_role,test_sequence,mod_version,git_commit,git_branch,"
                    + "build_dirty,build_time\n";
    private static final String CAPTURE_CSV_FILE = "captures-v3.csv";
    private static final String CAPTURE_DIR_HINT = "run/Latitude/captures/";

    private static KeyMapping captureKey;
    private static KeyMapping explainKey;
    private static boolean initialized;
    private static long lastCaptureMillis;

    private DevCaptureKeybind() {
    }

    public static void init() {
        if (initialized || !LatitudeDevRuntime.isToolingEnabled()) {
            return;
        }

        captureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.globe.dev_capture_overlay",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_NUMPAD0,
                ClientKeybinds.CATEGORY
        ));

        explainKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.globe.dev_explain_here",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_NUMPAD3,
                ClientKeybinds.CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(DevCaptureKeybind::onEndClientTick);
        initialized = true;
    }

    private static void onEndClientTick(Minecraft client) {
        DevPresentationTrace.clientTick(client);
        if (captureKey == null) {
            return;
        }

        while (captureKey.consumeClick()) {
            long now = System.currentTimeMillis();
            if ((now - lastCaptureMillis) < DEBOUNCE_MS) {
                continue;
            }
            lastCaptureMillis = now;
            capture(client, false);
        }

        if (explainKey != null) {
            while (explainKey.consumeClick()) {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand("latdev explainHere");
                }
            }
        }
    }

    public static void requestCaseCapture() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            throw new IllegalStateException("integrated Minecraft client is unavailable");
        }
        client.execute(() -> capture(client, true));
    }

    private static void capture(Minecraft client, boolean requestAlreadyRecorded) {
        boolean requestOwned = requestAlreadyRecorded;
        if (client.player == null || client.level == null) {
            if (requestOwned) {
                recordCaptureFailure(null, "client player or level unavailable");
            }
            return;
        }

        CaptureSnapshot snapshot = null;
        try {
            if (!requestAlreadyRecorded && DevTestSession.active().isPresent()) {
                DevTestSession.requestCaptureActive(
                        "keybind",
                        true,
                        client.level.getGameTime(),
                        Map.of("source", "dev_capture_keybind"));
                requestOwned = true;
            }
            snapshot = freezeSnapshot(client);
            RenderTarget framebuffer = client.getMainRenderTarget();
            CaptureSnapshot frozenSnapshot = snapshot;
            Screenshot.takeScreenshot(
                    framebuffer,
                    image -> client.execute(() -> handleCapturedImage(client, image, frozenSnapshot)));
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture pipeline failed", e);
            if (requestOwned) {
                recordCaptureFailure(snapshot, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            sendStatus(client, "[latdev] Capture failed: " + e.getMessage());
        }
    }

    private static void handleCapturedImage(
            Minecraft client,
            NativeImage image,
            CaptureSnapshot snapshot
    ) {
        try {
            boolean clipboardEnabled = LatitudeConfig.screenshotClipboardEnabled;
            boolean saveEnabled = LatitudeConfig.screenshotAlsoSaveToDisk;
            boolean csvEnabled = LatitudeConfig.captureWriteCsv;
            boolean caseLinked = snapshot.sessionId() != null;
            File savedFile = null;

            if (saveEnabled || csvEnabled || caseLinked || (clipboardEnabled && usePowerShellClipboard())) {
                savedFile = ClipboardImageWriter.saveToDisk(client, image);
            }

            if (!clipboardEnabled) {
                if (savedFile != null) {
                    sendStatus(client, "[latdev] Saved to " + CAPTURE_DIR_HINT + savedFile.getName());
                    appendCaptureCsvIfEnabled(snapshot, savedFile.toPath());
                    recordCaptureCompleted(snapshot, savedFile.toPath());
                } else {
                    sendStatus(client, "[latdev] Capture completed (clipboard disabled, disk save disabled)");
                }
                return;
            }

            if (usePowerShellClipboard()) {
                if (savedFile == null) {
                    savedFile = ClipboardImageWriter.saveToDisk(client, image);
                }
                boolean keepOnSuccess = saveEnabled || csvEnabled || caseLinked;
                handlePowerShellClipboardAsync(
                        client,
                        savedFile,
                        keepOnSuccess,
                        csvEnabled,
                        snapshot);
                return;
            }

            ClipboardCopyResult clipboardResult = ClipboardImageWriter.copyToClipboard(image);
            if (clipboardResult == ClipboardCopyResult.SUCCESS) {
                if (savedFile != null) {
                    sendStatus(client, "[latdev] Copied screenshot to clipboard; saved to " + CAPTURE_DIR_HINT + savedFile.getName());
                    appendCaptureCsvIfEnabled(snapshot, savedFile.toPath());
                    recordCaptureCompleted(snapshot, savedFile.toPath());
                } else {
                    sendStatus(client, "[latdev] Copied screenshot to clipboard");
                }
                return;
            }

            if (savedFile == null) {
                savedFile = ClipboardImageWriter.saveToDisk(client, image);
            }
            if (clipboardResult == ClipboardCopyResult.HEADLESS) {
                sendStatus(client, "[latdev] Clipboard unavailable; saved to " + CAPTURE_DIR_HINT + savedFile.getName());
            } else {
                sendStatus(client, "[latdev] Clipboard copy failed; saved to " + CAPTURE_DIR_HINT + savedFile.getName());
            }
            appendCaptureCsvIfEnabled(snapshot, savedFile.toPath());
            recordCaptureCompleted(snapshot, savedFile.toPath());
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture output failed", e);
            recordCaptureFailure(snapshot, e.getClass().getSimpleName() + ": " + e.getMessage());
            sendStatus(client, "[latdev] Capture output failed: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private static void handlePowerShellClipboardAsync(
            Minecraft client,
            File captureFile,
            boolean keepOnSuccess,
            boolean csvEnabled,
            CaptureSnapshot snapshot
    ) {
        CompletableFuture
                .supplyAsync(() -> ClipboardImageWriter.copyPngFileToClipboardWindowsPowerShell(captureFile.toPath()))
                .thenAccept(copied -> client.execute(() -> finalizePowerShellClipboard(
                        client,
                        copied,
                        captureFile,
                        keepOnSuccess,
                        csvEnabled,
                        snapshot)));
    }

    private static void finalizePowerShellClipboard(
            Minecraft client,
            boolean copied,
            File captureFile,
            boolean keepOnSuccess,
            boolean csvEnabled,
            CaptureSnapshot snapshot
    ) {
        try {
            if (copied) {
                if (!keepOnSuccess) {
                    ClipboardImageWriter.deleteQuietly(captureFile);
                    sendStatus(client, "[latdev] Copied screenshot to clipboard");
                } else {
                    appendCaptureCsvIfEnabled(snapshot, captureFile.toPath());
                    recordCaptureCompleted(snapshot, captureFile.toPath());
                    sendStatus(client, "[latdev] Copied screenshot to clipboard; saved to " + CAPTURE_DIR_HINT + captureFile.getName());
                }
                return;
            }

            sendStatus(client, "[latdev] Clipboard copy failed; saved to " + CAPTURE_DIR_HINT + captureFile.getName());
            if (csvEnabled) {
                appendCaptureCsv(snapshot, captureFile.toPath());
            }
            recordCaptureCompleted(snapshot, captureFile.toPath());
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture output failed", e);
            recordCaptureFailure(snapshot, e.getClass().getSimpleName() + ": " + e.getMessage());
            sendStatus(client, "[latdev] Capture output failed: " + e.getMessage());
        }
    }

    private static void appendCaptureCsvIfEnabled(
            CaptureSnapshot snapshot,
            Path capturePath
    ) throws IOException {
        if (!LatitudeConfig.captureWriteCsv) {
            return;
        }
        appendCaptureCsv(snapshot, capturePath);
    }

    private static void appendCaptureCsv(
            CaptureSnapshot snapshot,
            Path capturePath
    ) throws IOException {
        Path latdevDir = snapshot.gameDirectory().resolve("latdev");
        Files.createDirectories(latdevDir);
        // Keep the legacy five-column captures.csv immutable. The richer provenance contract has
        // a versioned filename so old evidence can never acquire rows under the wrong header.
        Path csvPath = latdevDir.resolve(CAPTURE_CSV_FILE);

        if (Files.notExists(csvPath)) {
            Files.writeString(csvPath, CAPTURE_CSV_HEADER, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        String row = escapeCsv(snapshot.timestamp())
                + "," + escapeCsv(capturePath.getFileName().toString())
                + "," + escapeCsv(sha256(capturePath))
                + "," + escapeCsv(orUnknown(snapshot.caseId()))
                + "," + escapeCsv(orUnknown(snapshot.sessionId()))
                + "," + snapshot.sessionSequence()
                + "," + snapshot.worldTick()
                + "," + escapeCsv(snapshot.dimension())
                + "," + escapeCsv(snapshot.seed())
                + "," + escapeCsv(snapshot.biome())
                + "," + snapshot.signedLatitudeDegrees()
                + "," + escapeCsv(snapshot.zone())
                + "," + snapshot.x()
                + "," + snapshot.y()
                + "," + snapshot.z()
                + "," + snapshot.yaw()
                + "," + snapshot.pitch()
                + "," + snapshot.guiScale()
                + "," + escapeCsv(snapshot.build().role())
                + "," + snapshot.build().sequence()
                + "," + escapeCsv(snapshot.build().version())
                + "," + escapeCsv(snapshot.build().commit())
                + "," + escapeCsv(snapshot.build().branch())
                + "," + escapeCsv(snapshot.build().dirty())
                + "," + escapeCsv(snapshot.build().time())
                + "\n";
        Files.writeString(csvPath, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static CaptureSnapshot freezeSnapshot(Minecraft client) {
        var player = client.player;
        var world = client.level;
        if (player == null || world == null) {
            throw new IllegalStateException("client player or level unavailable");
        }

        Optional<DevTestSession> session = DevTestSession.active();
        String caseId = session.map(DevTestSession::caseId).orElse(null);
        String sessionId = session.map(DevTestSession::sessionId).orElse(null);
        long sessionSequence = session.map(DevTestSession::sequence).orElse(0L);
        long worldTick = world.getGameTime();
        var border = world.getWorldBorder();
        double signedLatitude = DevToolPolicy.signedLatitudeDegrees(
                player.getZ(),
                border.getCenterZ(),
                LatitudeMath.worldRadiusBlocks(border));
        String biome = world.getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("unknown");
        String seed = "unknown";
        var integratedServer = client.getSingleplayerServer();
        if (integratedServer != null) {
            seed = Long.toString(integratedServer.getWorldGenSettings().options().seed());
        }

        return new CaptureSnapshot(
                client.gameDirectory.toPath().toAbsolutePath().normalize(),
                Util.getFilenameFormattedDateTime(),
                caseId,
                sessionId,
                sessionSequence,
                worldTick,
                world.dimension().identifier().toString(),
                seed,
                biome,
                String.format(Locale.ROOT, "%.6f", signedLatitude),
                LatitudeMath.zoneKey(border, player.getZ()),
                String.format(Locale.ROOT, "%.3f", player.getX()),
                String.format(Locale.ROOT, "%.3f", player.getY()),
                String.format(Locale.ROOT, "%.3f", player.getZ()),
                String.format(Locale.ROOT, "%.3f", player.getYRot()),
                String.format(Locale.ROOT, "%.3f", player.getXRot()),
                client.getWindow().getGuiScale(),
                buildIdentity());
    }

    private static void recordCaptureCompleted(CaptureSnapshot snapshot, Path capturePath) {
        if (snapshot == null || snapshot.sessionId() == null) {
            return;
        }
        try {
            Optional<DevTestSession> active = DevTestSession.active();
            if (active.isEmpty() || !snapshot.sessionId().equals(active.get().sessionId())) {
                GlobeMod.LOGGER.warn(
                        "[latdev] capture completion session mismatch frozen={} active={}",
                        snapshot.sessionId(),
                        active.map(DevTestSession::sessionId).orElse("none"));
                return;
            }
            String digest = sha256(capturePath);
            Path absolute = capturePath.toAbsolutePath().normalize();
            String relative = absolute.startsWith(snapshot.gameDirectory())
                    ? snapshot.gameDirectory().relativize(absolute).toString()
                    : capturePath.getFileName().toString();
            LinkedHashMap<String, String> fields = snapshot.metadata();
            fields.put("capture_status", "saved");
            DevTestSession.recordScreenshotActive(
                    relative,
                    digest,
                    snapshot.worldTick(),
                    fields);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] could not append capture completion to active case", e);
        }
    }

    private static void recordCaptureFailure(CaptureSnapshot snapshot, String reason) {
        String sessionId = snapshot == null ? null : snapshot.sessionId();
        if (sessionId == null && DevTestSession.active().isEmpty()) {
            return;
        }
        try {
            Optional<DevTestSession> active = DevTestSession.active();
            if (active.isEmpty()) {
                return;
            }
            if (sessionId != null && !sessionId.equals(active.get().sessionId())) {
                GlobeMod.LOGGER.warn(
                        "[latdev] capture failure session mismatch frozen={} active={}",
                        sessionId,
                        active.get().sessionId());
                return;
            }
            long tick = snapshot == null ? -1L : snapshot.worldTick();
            Map<String, String> fields = snapshot == null ? Map.of() : snapshot.metadata();
            DevTestSession.recordCaptureFailedActive(reason, tick, fields);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] could not append capture failure to active case", e);
        }
    }

    private static LatitudeDevRuntime.BuildIdentity buildIdentity() {
        return LatitudeDevRuntime.identity();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String orUnknown(String value) {
        return value == null ? "none" : value;
    }

    private static String escapeCsv(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static boolean usePowerShellClipboard() {
        return LatitudeConfig.screenshotClipboardWindowsPowerShell && ClipboardImageWriter.isWindows();
    }

    private static void sendStatus(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private record CaptureSnapshot(
            Path gameDirectory,
            String timestamp,
            String caseId,
            String sessionId,
            long sessionSequence,
            long worldTick,
            String dimension,
            String seed,
            String biome,
            String signedLatitudeDegrees,
            String zone,
            String x,
            String y,
            String z,
            String yaw,
            String pitch,
            int guiScale,
            LatitudeDevRuntime.BuildIdentity build
    ) {
        private LinkedHashMap<String, String> metadata() {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put("artifact_role", build.role());
            values.put("biome", biome);
            values.put("build_dirty", build.dirty());
            values.put("build_time", build.time());
            values.put("case_id", orUnknown(caseId));
            values.put("dimension", dimension);
            values.put("git_branch", build.branch());
            values.put("git_commit", build.commit());
            values.put("gui_scale", Integer.toString(guiScale));
            values.put("mod_version", build.version());
            values.put("pitch", pitch);
            values.put("request_sequence", Long.toString(sessionSequence));
            values.put("seed", seed);
            values.put("session_id", orUnknown(sessionId));
            values.put("signed_latitude_degrees", signedLatitudeDegrees);
            values.put("test_sequence", Integer.toString(build.sequence()));
            values.put("x", x);
            values.put("y", y);
            values.put("yaw", yaw);
            values.put("z", z);
            values.put("zone", zone);
            return values;
        }
    }
}
