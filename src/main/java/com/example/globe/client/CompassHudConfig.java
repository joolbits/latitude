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
    public enum CompassStyle { DIGITAL, ANALOG }
    // Append-only: keep CLASSIC_GOLD first (default) and never reorder — themes persist by name
    // and the cycle button uses declaration order.
    public enum AnalogCompassTheme {
        CLASSIC_GOLD, PALE_GOLD, RED_IVORY, CYAN_STEEL, MINT_BRASS,
        OBSIDIAN_RED, ARCTIC_BLUE, EMERALD, ROYAL_PURPLE, SUNSET, MONOCHROME
    }
    public enum HAnchor { LEFT, CENTER, RIGHT }
    public enum VAnchor { TOP, CENTER, BOTTOM }

    public static final CompassStyle DEFAULT_COMPASS_STYLE = CompassStyle.ANALOG;
    public static final float DEFAULT_ANALOG_SIZE = 32.0f;
    public static final int ANALOG_SIZE_STUDIO_MIN = 16;
    public static final int ANALOG_SIZE_STUDIO_MAX = 72;
    public static final float ANALOG_SIZE_SAVED_MAX = 128.0f;

    // Master toggle
    public boolean enabled = true;

    public ShowMode showMode = ShowMode.COMPASS_PRESENT;
    public CompassStyle style = DEFAULT_COMPASS_STYLE;
    public AnalogCompassTheme analogTheme = AnalogCompassTheme.CLASSIC_GOLD;
    public DirectionMode directionMode = DirectionMode.CARDINAL_8;

    // Positioning (screen-space)
    public HAnchor hAnchor = HAnchor.CENTER;
    public VAnchor vAnchor = VAnchor.TOP;
    public int offsetX = 0;
    public int offsetY = 0;

    // Sizing (digital text)
    public float scale = 1.0f; // 0.5 .. 3.0 recommended
    public int padding = 3;

    // Sizing (analog disc diameter, unscaled)
    public float analogSize = DEFAULT_ANALOG_SIZE; // pixels

    // Analog styling
    public float analogInnerAlpha = 0.65f; // 0..1

    // Location detail. Two booleans preserve the legacy displayZoneInHud JSON field while
    // representing Off / Biome / Zone / Biome + Zone without a config migration.
    public boolean displayBiomeInHud = false;
    public boolean displayZoneInHud = false;
    public boolean zoneFollowsCompass = true;
    public HAnchor zoneHAnchor = HAnchor.CENTER;
    public VAnchor zoneVAnchor = VAnchor.TOP;
    public int zoneOffsetX = 0;
    public int zoneOffsetY = 0;

    // Styling
    public boolean showBackground = true;
    public int backgroundRgb = 0x000000;
    public int backgroundAlpha = 64; // 0..255 (lower = less dark)
    public int textRgb = 0xFFFFFF;
    public int textAlpha = 255; // 0..255
    public boolean shadow = true;

    // Latitude display
    public Boolean showLatitude = true; // digital mode
    public Boolean analogShowLatitude = true;
    public Integer latitudeDecimals = 0;

    // Inline formatting
    public boolean compactHud = false;

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

    public LocationDetailPolicy.Mode locationDetailMode() {
        return LocationDetailPolicy.fromPersistedFlags(displayBiomeInHud, displayZoneInHud);
    }

    public void setLocationDetailMode(LocationDetailPolicy.Mode mode) {
        LocationDetailPolicy.Mode selected =
                mode == null ? LocationDetailPolicy.DEFAULT_MODE : mode;
        displayBiomeInHud = selected.includesBiome();
        displayZoneInHud = selected.includesZone();
    }

    public boolean hasLocationDetail() {
        return locationDetailMode() != LocationDetailPolicy.Mode.OFF;
    }

    public void resetToDefaults() {
        enabled = true;
        showMode = ShowMode.COMPASS_PRESENT;
        style = DEFAULT_COMPASS_STYLE;
        analogTheme = AnalogCompassTheme.CLASSIC_GOLD;
        directionMode = DirectionMode.CARDINAL_8;
        hAnchor = HAnchor.CENTER;
        vAnchor = VAnchor.TOP;
        offsetX = 0;
        offsetY = 0;
        scale = 1.0f;
        padding = 3;
        analogSize = DEFAULT_ANALOG_SIZE;
        analogInnerAlpha = 0.65f;
        setLocationDetailMode(LocationDetailPolicy.DEFAULT_MODE);
        zoneFollowsCompass = true;
        zoneHAnchor = HAnchor.CENTER;
        zoneVAnchor = VAnchor.TOP;
        zoneOffsetX = 0;
        zoneOffsetY = 0;
        showBackground = true;
        backgroundRgb = 0x000000;
        backgroundAlpha = 64;
        textRgb = 0xFFFFFF;
        textAlpha = 255;
        shadow = true;
        showLatitude = true;
        analogShowLatitude = true;
        latitudeDecimals = 0;
        compactHud = false;
        attachToHotbarCompass = false;
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
        if (style == null) style = CompassStyle.DIGITAL;
        if (analogTheme == null) analogTheme = AnalogCompassTheme.CLASSIC_GOLD;
        if (directionMode == null) directionMode = DirectionMode.CARDINAL_8;
        if (hAnchor == null) hAnchor = HAnchor.CENTER;
        if (vAnchor == null) vAnchor = VAnchor.TOP;
        if (showLatitude == null) showLatitude = true;
        if (analogShowLatitude == null) analogShowLatitude = true;
        if (latitudeDecimals == null) latitudeDecimals = 0;
        if (latitudeDecimals < 0) latitudeDecimals = 0;
        if (latitudeDecimals > 3) latitudeDecimals = 3;
        if (scale < 0.25f) scale = 0.25f;
        if (scale > 4.0f) scale = 4.0f;
        if (analogSize < ANALOG_SIZE_STUDIO_MIN) analogSize = ANALOG_SIZE_STUDIO_MIN;
        if (analogSize > ANALOG_SIZE_SAVED_MAX) analogSize = ANALOG_SIZE_SAVED_MAX;
        if (analogInnerAlpha < 0.0f) analogInnerAlpha = 0.0f;
        if (analogInnerAlpha > 1.0f) analogInnerAlpha = 1.0f;
        if (zoneHAnchor == null) zoneHAnchor = HAnchor.CENTER;
        if (zoneVAnchor == null) zoneVAnchor = VAnchor.TOP;
        if (padding < 0) padding = 0;
        if (backgroundAlpha < 0) backgroundAlpha = 0;
        if (backgroundAlpha > 255) backgroundAlpha = 255;
        if (textAlpha < 0) textAlpha = 0;
        if (textAlpha > 255) textAlpha = 255;
    }
}
