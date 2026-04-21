package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.ClipboardImageWriter;
import com.example.globe.client.ClipboardImageWriter.ClipboardCopyResult;
import com.example.globe.client.LatitudeConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

public final class DevCaptureKeybind {
    private static final long DEBOUNCE_MS = 300L;
    private static final String CAPTURE_CSV_HEADER = "timestamp,file,x,y,z\n";
    private static final String CAPTURE_DIR_HINT = "run/Latitude/captures/";

    private static KeyMapping captureKey;
    private static KeyMapping explainKey;
    private static boolean initialized;
    private static long lastCaptureMillis;

    private DevCaptureKeybind() {
    }

    public static void init() {
        if (initialized || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
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
        if (captureKey == null) {
            return;
        }

        while (captureKey.consumeClick()) {
            long now = System.currentTimeMillis();
            if ((now - lastCaptureMillis) < DEBOUNCE_MS) {
                continue;
            }
            lastCaptureMillis = now;
            capture(client);
        }

        if (explainKey != null) {
            while (explainKey.consumeClick()) {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand("latdev explainHere");
                }
            }
        }
    }

    private static void capture(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        try {
            RenderTarget framebuffer = client.getMainRenderTarget();
            Screenshot.takeScreenshot(framebuffer, image -> client.execute(() -> handleCapturedImage(client, image)));
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture pipeline failed", e);
            sendStatus(client, "[latdev] Capture failed: " + e.getMessage());
        }
    }

    private static void handleCapturedImage(Minecraft client, NativeImage image) {
        try {
            boolean clipboardEnabled = LatitudeConfig.screenshotClipboardEnabled;
            boolean saveEnabled = LatitudeConfig.screenshotAlsoSaveToDisk;
            boolean csvEnabled = LatitudeConfig.captureWriteCsv;
            File savedFile = null;

            if (saveEnabled || csvEnabled || (clipboardEnabled && usePowerShellClipboard())) {
                savedFile = ClipboardImageWriter.saveToDisk(client, image);
            }

            if (!clipboardEnabled) {
                if (savedFile != null) {
                    sendStatus(client, "[latdev] Saved to " + CAPTURE_DIR_HINT + savedFile.getName());
                    appendCaptureCsvIfEnabled(client, savedFile.toPath());
                } else {
                    sendStatus(client, "[latdev] Capture completed (clipboard disabled, disk save disabled)");
                }
                return;
            }

            if (usePowerShellClipboard()) {
                if (savedFile == null) {
                    savedFile = ClipboardImageWriter.saveToDisk(client, image);
                }
                boolean keepOnSuccess = saveEnabled || csvEnabled;
                handlePowerShellClipboardAsync(client, savedFile, keepOnSuccess, csvEnabled);
                return;
            }

            ClipboardCopyResult clipboardResult = ClipboardImageWriter.copyToClipboard(image);
            if (clipboardResult == ClipboardCopyResult.SUCCESS) {
                if (savedFile != null) {
                    sendStatus(client, "[latdev] Copied screenshot to clipboard; saved to " + CAPTURE_DIR_HINT + savedFile.getName());
                    appendCaptureCsvIfEnabled(client, savedFile.toPath());
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
            appendCaptureCsvIfEnabled(client, savedFile.toPath());
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture output failed", e);
            sendStatus(client, "[latdev] Capture output failed: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private static void handlePowerShellClipboardAsync(
            Minecraft client,
            File captureFile,
            boolean keepOnSuccess,
            boolean csvEnabled
    ) {
        CompletableFuture
                .supplyAsync(() -> ClipboardImageWriter.copyPngFileToClipboardWindowsPowerShell(captureFile.toPath()))
                .thenAccept(copied -> client.execute(() -> finalizePowerShellClipboard(client, copied, captureFile, keepOnSuccess, csvEnabled)));
    }

    private static void finalizePowerShellClipboard(
            Minecraft client,
            boolean copied,
            File captureFile,
            boolean keepOnSuccess,
            boolean csvEnabled
    ) {
        try {
            if (copied) {
                if (!keepOnSuccess) {
                    ClipboardImageWriter.deleteQuietly(captureFile);
                    sendStatus(client, "[latdev] Copied screenshot to clipboard");
                } else {
                    appendCaptureCsvIfEnabled(client, captureFile.toPath());
                    sendStatus(client, "[latdev] Copied screenshot to clipboard; saved to " + CAPTURE_DIR_HINT + captureFile.getName());
                }
                return;
            }

            sendStatus(client, "[latdev] Clipboard copy failed; saved to " + CAPTURE_DIR_HINT + captureFile.getName());
            if (csvEnabled) {
                appendCaptureCsv(client, captureFile.toPath());
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture output failed", e);
            sendStatus(client, "[latdev] Capture output failed: " + e.getMessage());
        }
    }

    private static void appendCaptureCsvIfEnabled(Minecraft client, Path capturePath) throws IOException {
        if (!LatitudeConfig.captureWriteCsv) {
            return;
        }
        appendCaptureCsv(client, capturePath);
    }

    private static void appendCaptureCsv(Minecraft client, Path capturePath) throws IOException {
        Path latdevDir = client.gameDirectory.toPath().resolve("latdev");
        Files.createDirectories(latdevDir);
        Path csvPath = latdevDir.resolve("captures.csv");

        if (Files.notExists(csvPath)) {
            Files.writeString(csvPath, CAPTURE_CSV_HEADER, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        String x = "?";
        String y = "?";
        String z = "?";
        var player = client.player;
        if (player != null) {
            x = Integer.toString(player.getBlockX());
            y = Integer.toString(player.getBlockY());
            z = Integer.toString(player.getBlockZ());
        }

        String row = escapeCsv(Util.getFilenameFormattedDateTime())
                + "," + escapeCsv(capturePath.getFileName().toString())
                + "," + x
                + "," + y
                + "," + z
                + "\n";
        Files.writeString(csvPath, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
}
