package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.ClipboardImageWriter;
import com.example.globe.client.ClipboardImageWriter.ClipboardCopyResult;
import com.example.globe.client.LatitudeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
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

    private static KeyBinding captureKey;
    private static boolean initialized;
    private static long lastCaptureMillis;

    private DevCaptureKeybind() {
    }

    public static void init() {
        if (initialized || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.globe.dev_capture_overlay",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_KP_0,
                ClientKeybinds.CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(DevCaptureKeybind::onEndClientTick);
        initialized = true;
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (captureKey == null) {
            return;
        }

        while (captureKey.wasPressed()) {
            long now = System.currentTimeMillis();
            if ((now - lastCaptureMillis) < DEBOUNCE_MS) {
                continue;
            }
            lastCaptureMillis = now;
            capture(client);
        }
    }

    private static void capture(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        try {
            Framebuffer framebuffer = client.getFramebuffer();
            NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer);
            client.execute(() -> handleCapturedImage(client, image));
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture pipeline failed", e);
            sendStatus(client, "[latdev] Capture failed: " + e.getMessage());
        }
    }

    private static void handleCapturedImage(MinecraftClient client, NativeImage image) {
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
            MinecraftClient client,
            File captureFile,
            boolean keepOnSuccess,
            boolean csvEnabled
    ) {
        CompletableFuture
                .supplyAsync(() -> ClipboardImageWriter.copyPngFileToClipboardWindowsPowerShell(captureFile.toPath()))
                .thenAccept(copied -> client.execute(() -> finalizePowerShellClipboard(client, copied, captureFile, keepOnSuccess, csvEnabled)));
    }

    private static void finalizePowerShellClipboard(
            MinecraftClient client,
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

    private static void appendCaptureCsvIfEnabled(MinecraftClient client, Path capturePath) throws IOException {
        if (!LatitudeConfig.captureWriteCsv) {
            return;
        }
        appendCaptureCsv(client, capturePath);
    }

    private static void appendCaptureCsv(MinecraftClient client, Path capturePath) throws IOException {
        Path latdevDir = client.runDirectory.toPath().resolve("latdev");
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

        String row = escapeCsv(Util.getFormattedCurrentTime())
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

    private static void sendStatus(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
