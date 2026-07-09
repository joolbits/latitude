package com.example.globe.core.config;

import com.google.gson.annotations.SerializedName;

/**
 * The persisted shape of the client latitude config (globe_latitude.json) — a pure data class with ZERO
 * Minecraft/Fabric imports so the pure-JVM suite can round-trip it (design:
 * docs/design/hud-layout-overhaul-design-20260707.md, Pillar 3 config hygiene).
 *
 * <p>This class is the SINGLE source of config defaults: every field's initializer is the default, used
 * both for fresh installs and for keys absent from an existing file (Gson keeps constructor-assigned
 * values for missing keys). The legacy file format serialized instance fields named {@code <field>Value};
 * {@code @SerializedName(alternate = ...)} reads those old keys while writing clean names going forward.
 *
 * <p>{@code configVersion} intentionally defaults to 0 so a legacy file (no version key) is recognizable;
 * {@link #fresh()} stamps {@link #CURRENT_CONFIG_VERSION}, and the loader re-stamps + saves after a
 * successful legacy read so the migration happens exactly once.
 */
public final class LatitudeConfigData {

    public static final int CURRENT_CONFIG_VERSION = 1;

    /** 0 = legacy file (or pre-version fresh state); stamped to CURRENT_CONFIG_VERSION on load/fresh. */
    public int configVersion = 0;

    @SerializedName(value = "enableWarningParticles", alternate = {"enableWarningParticlesValue"})
    public boolean enableWarningParticles = true;

    @SerializedName(value = "showWarningMessages", alternate = {"showWarningMessagesValue"})
    public boolean showWarningMessages = true;

    public enum TitleColorPreset { WHITE, GOLD, RED, CYAN, GREEN, CUSTOM, RAINBOW }

    // NORMAL was removed 2026-07-08 (Peetsa: "normal and uppercase are the same thing" -- in the one
    // place that mattered, the HUD Studio's no-world sample title, both the sample AND the fallback were
    // hardcoded ALL-CAPS, so NORMAL's no-op case transform was indistinguishable from UPPERCASE's). Gson
    // maps an unrecognized saved constant name to null (see sanitize() below), not a parse failure, so an
    // existing config with the old "NORMAL" value degrades safely to the new default on next load.
    public enum TitleCaseMode { UPPERCASE, LOWERCASE, MOCKING }

    @SerializedName(value = "zoneEnterTitleEnabled", alternate = {"zoneEnterTitleEnabledValue"})
    public boolean zoneEnterTitleEnabled = true;

    @SerializedName(value = "zoneEnterTitleSeconds", alternate = {"zoneEnterTitleSecondsValue"})
    public double zoneEnterTitleSeconds = 6.0;

    @SerializedName(value = "zoneEnterTitleScale", alternate = {"zoneEnterTitleScaleValue"})
    public double zoneEnterTitleScale = 1.8;

    @SerializedName(value = "zoneEnterTitleColorPreset", alternate = {"zoneEnterTitleColorPresetValue"})
    public TitleColorPreset zoneEnterTitleColorPreset = TitleColorPreset.WHITE;

    @SerializedName(value = "zoneEnterTitleRgb", alternate = {"zoneEnterTitleRgbValue"})
    public int zoneEnterTitleRgb = 0xFFFFFF;

    @SerializedName(value = "zoneEnterTitleCase", alternate = {"zoneEnterTitleCaseValue"})
    public TitleCaseMode zoneEnterTitleCase = TitleCaseMode.UPPERCASE;

    /** Extra pixels between characters; negative = tighter. */
    @SerializedName(value = "zoneEnterTitleLetterSpacing", alternate = {"zoneEnterTitleLetterSpacingValue"})
    public int zoneEnterTitleLetterSpacing = 0;

    @SerializedName(value = "zoneEnterTitleOffsetX", alternate = {"zoneEnterTitleOffsetXValue"})
    public int zoneEnterTitleOffsetX = 0;

    @SerializedName(value = "zoneEnterTitleOffsetY", alternate = {"zoneEnterTitleOffsetYValue"})
    public int zoneEnterTitleOffsetY = -40;

    @SerializedName(value = "zoneEnterTitleDraggable", alternate = {"zoneEnterTitleDraggableValue"})
    public boolean zoneEnterTitleDraggable = true;

    @SerializedName(value = "hudSnapEnabled", alternate = {"hudSnapEnabledValue"})
    public boolean hudSnapEnabled = true;

    @SerializedName(value = "hudSnapPixels", alternate = {"hudSnapPixelsValue"})
    public int hudSnapPixels = 8;

    @SerializedName(value = "showZoneBaseDegreesOnTitle", alternate = {"showZoneBaseDegreesOnTitleValue"})
    public boolean showZoneBaseDegreesOnTitle = true;

    @SerializedName(value = "screenshotClipboardEnabled", alternate = {"screenshotClipboardEnabledValue"})
    public boolean screenshotClipboardEnabled = true;

    @SerializedName(value = "screenshotAlsoSaveToDisk", alternate = {"screenshotAlsoSaveToDiskValue"})
    public boolean screenshotAlsoSaveToDisk = true;

    @SerializedName(value = "screenshotClipboardWindowsPowerShell", alternate = {"screenshotClipboardWindowsPowerShellValue"})
    public boolean screenshotClipboardWindowsPowerShell = defaultWindowsClipboardEnabled();

    @SerializedName(value = "captureWriteCsv", alternate = {"captureWriteCsvValue"})
    public boolean captureWriteCsv = false;

    /** Defaults + current version — the fresh-install state. */
    public static LatitudeConfigData fresh() {
        LatitudeConfigData d = new LatitudeConfigData();
        d.configVersion = CURRENT_CONFIG_VERSION;
        return d;
    }

    /** Null-guard enums (Gson leaves unknown enum constants null) and clamp ranges to the UI's bounds. */
    public void sanitize() {
        if (zoneEnterTitleColorPreset == null) zoneEnterTitleColorPreset = TitleColorPreset.WHITE;
        if (zoneEnterTitleCase == null) zoneEnterTitleCase = TitleCaseMode.UPPERCASE;

        zoneEnterTitleSeconds = clamp(zoneEnterTitleSeconds, 2.0, 10.0);
        zoneEnterTitleScale = clamp(zoneEnterTitleScale, 1.0, 3.0);
        zoneEnterTitleLetterSpacing = clampInt(zoneEnterTitleLetterSpacing, -4, 16);

        hudSnapPixels = clampInt(hudSnapPixels, 1, 64);

        configVersion = clampInt(configVersion, 0, CURRENT_CONFIG_VERSION);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean defaultWindowsClipboardEnabled() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
