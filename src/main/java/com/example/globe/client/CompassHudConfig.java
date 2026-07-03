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
        OBSIDIAN_RED, ARCTIC_BLUE, EMERALD, ROYAL_PURPLE, SUNSET, MONOCHROME, CUSTOM
    }
    public enum HAnchor { LEFT, CENTER, RIGHT }
    public enum VAnchor { TOP, CENTER, BOTTOM }

    // Master toggle
    public boolean enabled = true;

    public ShowMode showMode = ShowMode.COMPASS_PRESENT;
    // Default to the analog compass for the 2.0 release (Peetsa's chosen default layout: analog @ size 32,
    // inner alpha 0.50, lat+long readout on). Existing users keep whatever their saved config has.
    public CompassStyle style = CompassStyle.ANALOG;
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
    public float analogSize = 32.0f; // pixels

    // Analog styling. Lower = more transparent inner disc.
    public float analogInnerAlpha = 0.50f; // 0..1

    // Custom analog theme colors (only used when analogTheme == CUSTOM). Defaults mirror CLASSIC_GOLD so a
    // fresh Custom theme doesn't look black/broken before the player touches a slider. face is bare 0xRRGGBB
    // (alpha comes from analogInnerAlpha above, same as every other theme); ring/muted/needle are full ARGB
    // with alpha pinned to 0xFF, matching the packing convention CompassHud.analogColors() relies on.
    public int customFaceRgb = 0x1A1410;
    public int customRingArgb = 0xFFD4A74A;
    public int customMutedArgb = 0xFF8C8078;
    public int customNeedleArgb = 0xFFEDE0D0;

    // Zone (band) label
    public boolean displayZoneInHud = false;
    public boolean zoneFollowsCompass = true;
    public HAnchor zoneHAnchor = HAnchor.CENTER;
    public VAnchor zoneVAnchor = VAnchor.TOP;
    public int zoneOffsetX = 0;
    public int zoneOffsetY = 0;

    // Biome label -- same follow/detach/anchor/offset shape as the zone label above, so it can independently
    // ride with the compass or be dragged to its own spot in the HUD Studio.
    public boolean displayBiomeInHud = false;
    public boolean biomeFollowsCompass = true;
    public HAnchor biomeHAnchor = HAnchor.CENTER;
    public VAnchor biomeVAnchor = VAnchor.TOP;
    public int biomeOffsetX = 0;
    public int biomeOffsetY = 0;
    // When zone and biome are BOTH attached to the compass line, which comes first. false = "Zone, Biome"
    // (band before biome); true = "Biome, Zone".
    public boolean biomeBeforeZone = false;

    // Coordinates (lat/lon) detachability. Previously always fused to the compass; now can ride with it
    // (default, unchanged behavior) or detach to its own anchor+offset like zone/biome. The actual lat/lon
    // digits still come from showLatitude/showLongitude (digital) or analogShowLatitude/analogShowLongitude
    // (analog) -- this only controls WHERE that text renders, not whether it exists.
    public boolean coordsFollowsCompass = true;
    public HAnchor coordsHAnchor = HAnchor.CENTER;
    public VAnchor coordsVAnchor = VAnchor.TOP;
    public int coordsOffsetX = 0;
    public int coordsOffsetY = 0;

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

    // Longitude display (2.0 "Longitude" release)
    public Boolean showLongitude = true; // digital mode
    public Boolean analogShowLongitude = true;

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

    /** A brand-new config with only the field-initializer defaults above (no disk read/write). Single source of
     *  truth for "what are the defaults" -- used by both first-load and any reset-to-defaults action, so they
     *  can never drift apart. */
    public static CompassHudConfig fresh() {
        return new CompassHudConfig();
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
        if (style == null) style = CompassStyle.DIGITAL;
        if (analogTheme == null) analogTheme = AnalogCompassTheme.CLASSIC_GOLD;
        if (directionMode == null) directionMode = DirectionMode.CARDINAL_8;
        if (hAnchor == null) hAnchor = HAnchor.CENTER;
        if (vAnchor == null) vAnchor = VAnchor.TOP;
        if (showLatitude == null) showLatitude = true;
        if (analogShowLatitude == null) analogShowLatitude = true;
        if (latitudeDecimals == null) latitudeDecimals = 0;
        if (latitudeDecimals < 0) latitudeDecimals = 0;
        if (showLongitude == null) showLongitude = true;
        if (analogShowLongitude == null) analogShowLongitude = true;
        if (latitudeDecimals > 3) latitudeDecimals = 3;
        if (scale < 0.25f) scale = 0.25f;
        if (scale > 4.0f) scale = 4.0f;
        // Floor lowered to match the HUD Studio slider's new minimum (16) so that value isn't clamped back up;
        // ceiling left generous as a backstop for hand-edited configs even though the slider itself now tops
        // out at 72.
        if (analogSize < 16.0f) analogSize = 16.0f;
        if (analogSize > 128.0f) analogSize = 128.0f;
        if (analogInnerAlpha < 0.0f) analogInnerAlpha = 0.0f;
        if (analogInnerAlpha > 1.0f) analogInnerAlpha = 1.0f;
        if (zoneHAnchor == null) zoneHAnchor = HAnchor.CENTER;
        if (zoneVAnchor == null) zoneVAnchor = VAnchor.TOP;
        if (biomeHAnchor == null) biomeHAnchor = HAnchor.CENTER;
        if (biomeVAnchor == null) biomeVAnchor = VAnchor.TOP;
        if (coordsHAnchor == null) coordsHAnchor = HAnchor.CENTER;
        if (coordsVAnchor == null) coordsVAnchor = VAnchor.TOP;
        if (padding < 0) padding = 0;
        if (backgroundAlpha < 0) backgroundAlpha = 0;
        if (backgroundAlpha > 255) backgroundAlpha = 255;
        if (textAlpha < 0) textAlpha = 0;
        if (textAlpha > 255) textAlpha = 255;
    }
}
