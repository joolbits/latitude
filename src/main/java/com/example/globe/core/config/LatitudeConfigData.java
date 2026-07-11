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

    // RAINBOW = a STATIC left-to-right ROYGBIV gradient across the title letters (no time drift). AURORA =
    // the SAME flowing/drifting gradient the analog compass's "Aurora" scheme uses. AURORA is APPENDED last
    // on purpose: TitleColorPreset is persisted by NAME (GSON) and read by ordinal in the HUD Studio picker,
    // so appending never shifts an existing saved value (RAINBOW stays index 6 / name "RAINBOW").
    public enum TitleColorPreset { WHITE, GOLD, RED, CYAN, GREEN, CUSTOM, RAINBOW, AURORA }

    // NORMAL briefly removed 2026-07-08, then RESTORED same day: Peetsa's "normal and uppercase are the
    // same thing" report was correctly diagnosed but wrongly treated as a request to delete the option --
    // he wants "Tropical" (natural case) kept. The real bug was narrower: the HUD Studio's no-world SAMPLE
    // title was hardcoded ALL-CAPS ("TROPICS 12°S"), so NORMAL's no-op transform was indistinguishable
    // from UPPERCASE only in that one no-world preview context. Fixed at the source (studioPreviewTitle's
    // fallback is natural-case now) instead of removing the option. See LESSONS L20 in the main worktree.
    public enum TitleCaseMode { NORMAL, UPPERCASE, LOWERCASE, MOCKING }

    /**
     * Accessibility presets that HUD surfaces (compass, labels, overlays) consult to bias their rendering
     * toward legibility. THIS config pass wires the setting itself and the HUD Studio's own response; the
     * in-world HUD application is a deliberate follow-up in CompassHud. Persisted BY NAME (GSON) and read by
     * ORDINAL in the Studio picker, so any new mode MUST be appended -- {@code STANDARD} stays index 0 / name
     * "STANDARD"; never reorder (same discipline as {@link TitleColorPreset}'s AURORA note above).
     *
     * <p><b>Intended semantics -- the spec the follow-up HUD pass must honor:</b>
     * <ul>
     *   <li>{@code STANDARD} -- today's look; the mode applies no overrides.</li>
     *   <li>{@code HIGH_CONTRAST} -- maximize legibility. Force HUD text to FULL opacity regardless of the
     *       user's Text Opacity slider, draw a strong dark outline/backing plate behind text and glyphs, and
     *       clamp any panel background/translucency to a hard opaque floor so nothing is barely-there. Prefer
     *       solid backing plates over see-through ones. (Text Opacity below that floor is ignored while on.)</li>
     *   <li>{@code COLORBLIND_FRIENDLY} -- never carry meaning by COLOR ALONE, and never by a red-vs-green
     *       pairing specifically. Every color-coded signal must also carry a shape / text / position cue, and
     *       status colors should come from a blue / gold / white palette (unambiguous across the common
     *       color-vision deficiencies) rather than a red/green pair.</li>
     * </ul>
     */
    public enum AccessibilityMode { STANDARD, HIGH_CONTRAST, COLORBLIND_FRIENDLY }

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
    public TitleCaseMode zoneEnterTitleCase = TitleCaseMode.NORMAL;

    /** Extra pixels between characters; negative = tighter. */
    @SerializedName(value = "zoneEnterTitleLetterSpacing", alternate = {"zoneEnterTitleLetterSpacingValue"})
    public int zoneEnterTitleLetterSpacing = 0;

    @SerializedName(value = "zoneEnterTitleOffsetX", alternate = {"zoneEnterTitleOffsetXValue"})
    public int zoneEnterTitleOffsetX = 0;

    @SerializedName(value = "zoneEnterTitleOffsetY", alternate = {"zoneEnterTitleOffsetYValue"})
    public int zoneEnterTitleOffsetY = -40;

    @SerializedName(value = "zoneEnterTitleDraggable", alternate = {"zoneEnterTitleDraggableValue"})
    public boolean zoneEnterTitleDraggable = true;

    /** Accessibility: when true, the HUD Studio skips its gentle roll-out/roll-in row transitions (and any
     *  other motion-flourish a consumer chooses to gate on it) and snaps instantly instead. Default OFF so
     *  the animated behavior is the out-of-box experience; motion-sensitive players opt in. */
    @SerializedName(value = "reduceMotion", alternate = {"reduceMotionValue"})
    public boolean reduceMotion = false;

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

    @SerializedName(value = "accessibilityMode", alternate = {"accessibilityModeValue"})
    public AccessibilityMode accessibilityMode = AccessibilityMode.STANDARD;

    /** Defaults + current version — the fresh-install state. */
    public static LatitudeConfigData fresh() {
        LatitudeConfigData d = new LatitudeConfigData();
        d.configVersion = CURRENT_CONFIG_VERSION;
        return d;
    }

    /** Null-guard enums (Gson leaves unknown enum constants null) and clamp ranges to the UI's bounds. */
    public void sanitize() {
        if (zoneEnterTitleColorPreset == null) zoneEnterTitleColorPreset = TitleColorPreset.WHITE;
        if (zoneEnterTitleCase == null) zoneEnterTitleCase = TitleCaseMode.NORMAL;
        if (accessibilityMode == null) accessibilityMode = AccessibilityMode.STANDARD;

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
