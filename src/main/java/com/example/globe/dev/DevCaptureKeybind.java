package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.ClientKeybinds;
import com.example.globe.client.ClipboardImageWriter;
import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.UlOverlayTextBuilder;
import com.example.globe.mixin.client.GameRendererAccessor;
import com.example.globe.mixin.client.MinecraftClientFramebufferAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import java.io.File;

public final class DevCaptureKeybind {
    private static final long DEBOUNCE_MS = 300L;

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

        SimpleFramebuffer compositeFramebuffer = null;
        boolean handedOff = false;
        try {
            Framebuffer mainFramebuffer = client.getFramebuffer();
            compositeFramebuffer = new SimpleFramebuffer(
                    "Latitude Capture Composite",
                    mainFramebuffer.textureWidth,
                    mainFramebuffer.textureHeight,
                    mainFramebuffer.useDepthAttachment
            );

            copyFramebuffer(mainFramebuffer, compositeFramebuffer);
            renderMetadataOverlayOffscreen(client, compositeFramebuffer);
            final SimpleFramebuffer compositeForCallback = compositeFramebuffer;

            ScreenshotRecorder.takeScreenshot(compositeForCallback, image ->
                    client.execute(() -> {
                        try {
                            handleCapturedImage(client, image);
                        } finally {
                            compositeForCallback.delete();
                        }
                    })
            );
            handedOff = true;
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture pipeline failed", e);
            sendStatus(client, "[latdev] Capture failed: " + e.getMessage());
        } finally {
            if (!handedOff && compositeFramebuffer != null) {
                compositeFramebuffer.delete();
            }
        }
    }

    private static void copyFramebuffer(Framebuffer source, Framebuffer target) {
        GpuTexture sourceColor = source.getColorAttachment();
        GpuTexture targetColor = target.getColorAttachment();
        if (sourceColor == null || targetColor == null) {
            throw new IllegalStateException("Missing color attachment during capture compositing");
        }

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                sourceColor,
                targetColor,
                0,
                0,
                0,
                0,
                0,
                source.textureWidth,
                source.textureHeight
        );
    }

    private static void renderMetadataOverlayOffscreen(MinecraftClient client, Framebuffer targetFramebuffer) {
        if (!(client.gameRenderer instanceof GameRendererAccessor gameRendererAccessor)) {
            throw new IllegalStateException("GameRenderer accessor not available");
        }
        if (!(client instanceof MinecraftClientFramebufferAccessor clientFramebufferAccessor)) {
            throw new IllegalStateException("MinecraftClient framebuffer accessor not available");
        }

        GpuBufferSlice fogBuffer = RenderSystem.getShaderFog();
        if (fogBuffer == null) {
            throw new IllegalStateException("Fog buffer unavailable for capture overlay render");
        }

        Framebuffer originalFramebuffer = clientFramebufferAccessor.globe$getFramebuffer();
        clientFramebufferAccessor.globe$setFramebuffer(targetFramebuffer);
        try {
            gameRendererAccessor.globe$getGuiState().clear();
            DrawContext drawContext = new DrawContext(client, gameRendererAccessor.globe$getGuiState(), 0, 0);
            drawUlMetadata(drawContext, client);
            drawContext.drawDeferredElements();
            gameRendererAccessor.globe$getGuiRenderer().render(fogBuffer);
            gameRendererAccessor.globe$getGuiRenderer().incrementFrame();
        } finally {
            gameRendererAccessor.globe$getGuiState().clear();
            clientFramebufferAccessor.globe$setFramebuffer(originalFramebuffer);
        }
    }

    private static void drawUlMetadata(DrawContext ctx, MinecraftClient client) {
        int x = 4;
        int y = 4;
        for (String line : UlOverlayTextBuilder.buildLines(client)) {
            ctx.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFFF);
            y += 10;
        }
    }

    private static void handleCapturedImage(MinecraftClient client, NativeImage image) {
        try {
            boolean copied = false;
            boolean clipboardEnabled = LatitudeConfig.screenshotClipboardEnabled;

            if (clipboardEnabled) {
                copied = ClipboardImageWriter.copyToClipboard(image);
            }

            if (copied) {
                if (LatitudeConfig.screenshotAlsoSaveToDisk) {
                    File file = ClipboardImageWriter.saveToDisk(client, image);
                    sendStatus(client, "[latdev] Copied capture to clipboard and saved " + file.getName());
                } else {
                    sendStatus(client, "[latdev] Copied capture to clipboard");
                }
                return;
            }

            if (LatitudeConfig.screenshotClipboardFallbackToDisk) {
                File file = ClipboardImageWriter.saveToDisk(client, image);
                if (clipboardEnabled) {
                    sendStatus(client, "[latdev] Clipboard copy failed; saved " + file.getName());
                } else {
                    sendStatus(client, "[latdev] Clipboard disabled; saved " + file.getName());
                }
                return;
            }

            if (clipboardEnabled) {
                sendStatus(client, "[latdev] Clipboard copy failed (fallback disabled)");
            } else {
                sendStatus(client, "[latdev] Clipboard disabled and fallback disabled");
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] Capture output failed", e);
            sendStatus(client, "[latdev] Capture output failed: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private static void sendStatus(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
