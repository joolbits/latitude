package com.example.globe.dev;

import com.example.globe.util.LatitudeMath;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DevCaptureKeybind {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT);
    private static final DateTimeFormatter CSV_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT);

    private static final String CSV_HEADER = "timestamp,seed,dimension,diameter,radius,x,y,z,lat_degrees,biome\n";
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
                "key.globe.latdev_capture",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_KP_0,
                KeyBinding.Category.create(Identifier.of("globe", "latdev"))
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

        BlockPos pos = client.player.getBlockPos();
        String timestamp = CSV_STAMP.format(LocalDateTime.now());
        String fileStamp = FILE_STAMP.format(LocalDateTime.now());

        String seed = "unknown";
        if (client.getServer() != null && client.getServer().getOverworld() != null) {
            seed = String.valueOf(client.getServer().getOverworld().getSeed());
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        double diameter = client.world.getWorldBorder().getSize();
        double radius = diameter * 0.5;
        double latDegrees = LatitudeMath.absLatDegExact(client.world.getWorldBorder(), pos.getZ());
        String biome = client.world.getBiome(pos)
                .getKey()
                .map(k -> k.getValue().toString())
                .orElse("unknown");

        String screenshotName = "latdev_" + fileStamp + ".png";
        ScreenshotRecorder.saveScreenshot(
                client.runDirectory,
                screenshotName,
                client.getFramebuffer(),
                1,
                msg -> client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(msg, false);
                    }
                })
        );

        try {
            writeCsv(client, timestamp, seed, dimension, diameter, radius, pos, latDegrees, biome);
        } catch (IOException ioe) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[latdev] Capture CSV write failed: " + ioe.getMessage()), false);
            }
            return;
        }

        if (client.player != null) {
            client.player.sendMessage(Text.literal("[latdev] Captured: lat="
                    + String.format(Locale.ROOT, "%.2f", latDegrees)
                    + "°, biome=" + biome
                    + ", xyz=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + ", seed=" + seed), false);
        }
    }

    private static void writeCsv(MinecraftClient client,
                                 String timestamp,
                                 String seed,
                                 String dimension,
                                 double diameter,
                                 double radius,
                                 BlockPos pos,
                                 double latDegrees,
                                 String biome) throws IOException {
        Path dir = client.runDirectory.toPath().resolve("latdev");
        Files.createDirectories(dir);
        Path csv = dir.resolve("captures.csv");

        if (Files.notExists(csv)) {
            Files.writeString(csv, CSV_HEADER, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        String row = escapeCsv(timestamp) + ","
                + escapeCsv(seed) + ","
                + escapeCsv(dimension) + ","
                + String.format(Locale.ROOT, "%.2f", diameter) + ","
                + String.format(Locale.ROOT, "%.2f", radius) + ","
                + pos.getX() + ","
                + pos.getY() + ","
                + pos.getZ() + ","
                + String.format(Locale.ROOT, "%.4f", latDegrees) + ","
                + escapeCsv(biome)
                + "\n";

        Files.writeString(csv, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
