package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompassHudConfig {
    public enum ShowMode { ALWAYS, COMPASS_PRESENT, HOLDING_COMPASS }
    public enum DirectionMode { CARDINAL_4, CARDINAL_8, DEGREES }
    public enum HAnchor { LEFT, CENTER, RIGHT }
    public enum VAnchor { TOP, CENTER, BOTTOM }

    // Master toggle
    public boolean enabled = true;

    public ShowMode showMode = ShowMode.COMPASS_PRESENT;
    public DirectionMode directionMode = DirectionMode.CARDINAL_8;

    // Positioning (screen-space)
    public HAnchor hAnchor = HAnchor.CENTER;
    public VAnchor vAnchor = VAnchor.TOP;
    public int offsetX = 0;
    public int offsetY = 6; // push it down a bit from top

    // Sizing
    public float scale = 1.0f; // 0.5 .. 3.0 recommended
    public int padding = 3;

    // Styling
    public boolean showBackground = true;
    public int backgroundRgb = 0x000000;
    public int backgroundAlpha = 90; // 0..255 (lower = less dark)
    public int textRgb = 0xFFFFFF;
    public int textAlpha = 255; // 0..255
    public boolean shadow = true;

    // Hotbar attach
    public boolean attachToHotbarCompass = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("globe_compass_hud.json");

    private static CompassHudConfig INSTANCE;

    private CompassHudConfig() {}

    public static CompassHudConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    public static void saveCurrent() { save(get()); }

    public static void setEnabledAndSave(boolean value) { get().enabled = value; saveCurrent(); }

    public int textArgb() {
        return ((textAlpha & 0xFF) << 24) | (textRgb & 0xFFFFFF);
    }

    public int backgroundArgb() {
        return ((backgroundAlpha & 0xFF) << 24) | (backgroundRgb & 0xFFFFFF);
    }

    private static CompassHudConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    CompassHudConfig cfg = GSON.fromJson(r, CompassHudConfig.class);
                    if (cfg != null) {
                        cfg.sanitize();
                        return cfg;
                    }
                }
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to read compass HUD config; using defaults", e);
        }

        CompassHudConfig fresh = new CompassHudConfig();
        save(fresh);
        return fresh;
    }

    private static void save(CompassHudConfig cfg) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(cfg, w);
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to write compass HUD config", e);
        }
    }

    private void sanitize() {
        if (showMode == null) showMode = ShowMode.COMPASS_PRESENT;
        if (directionMode == null) directionMode = DirectionMode.CARDINAL_8;
        if (hAnchor == null) hAnchor = HAnchor.CENTER;
        if (vAnchor == null) vAnchor = VAnchor.TOP;
        if (scale < 0.25f) scale = 0.25f;
        if (scale > 4.0f) scale = 4.0f;
        if (padding < 0) padding = 0;
        if (backgroundAlpha < 0) backgroundAlpha = 0;
        if (backgroundAlpha > 255) backgroundAlpha = 255;
        if (textAlpha < 0) textAlpha = 0;
        if (textAlpha > 255) textAlpha = 255;
    }
}
