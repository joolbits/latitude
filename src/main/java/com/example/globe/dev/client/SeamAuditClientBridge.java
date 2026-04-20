package com.example.globe.dev.client;

import com.example.globe.GlobeMod;
import com.example.globe.dev.SeamAuditCoordinator;
import com.example.globe.dev.audit.AutonomousSeamAuditJob;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only client bridge that wires {@link SeamAuditCoordinator} to the client
 * {@link ScreenshotRecorder}. The server-side coordinator owns the audit folder
 * and pipeline; this bridge's single responsibility is to save a framebuffer
 * screenshot to the requested audit PNG path.
 *
 * <p>Guarded by {@link FabricLoader#isDevelopmentEnvironment()} so it is a no-op
 * in shipped builds.
 */
public final class SeamAuditClientBridge {
    private static boolean initialized;

    private SeamAuditClientBridge() {
    }

    public static void init() {
        if (initialized) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        SeamAuditCoordinator.setScreenshotHandler(SeamAuditClientBridge::captureToPath);
        AutonomousSeamAuditJob.setScreenshotHandler(SeamAuditClientBridge::captureToPath);
        initialized = true;
        GlobeMod.LOGGER.info("[seamAudit] client screenshot bridge initialized (chat coordinator + autonomous harness)");
    }

    private static void captureToPath(Path pngPath) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            GlobeMod.LOGGER.warn("[seamAudit] no MinecraftClient; cannot capture screenshot to {}", pngPath);
            return;
        }
        client.execute(() -> {
            try {
                Framebuffer framebuffer = client.getFramebuffer();
                if (framebuffer == null) {
                    GlobeMod.LOGGER.warn("[seamAudit] no framebuffer; skipping screenshot");
                    return;
                }
                ScreenshotRecorder.takeScreenshot(framebuffer, image -> client.execute(() -> writeImage(image, pngPath)));
            } catch (Exception e) {
                GlobeMod.LOGGER.warn("[seamAudit] screenshot capture failed", e);
            }
        });
    }

    private static void writeImage(NativeImage image, Path pngPath) {
        try {
            if (pngPath.getParent() != null) {
                Files.createDirectories(pngPath.getParent());
            }
            File outFile = pngPath.toFile();
            image.writeTo(outFile);
            GlobeMod.LOGGER.info("[seamAudit] screenshot written to {}", pngPath);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[seamAudit] screenshot write failed", e);
        } finally {
            image.close();
        }
    }
}
