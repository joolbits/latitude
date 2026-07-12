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
    // OFF_WHITE (appended 2026-07-11, title-styling overhaul) is the new FRESH-config default inner color: a
    // warm off-white that pairs with the black outline far better than a stark pure WHITE. Appended LAST for
    // the same ordinal-stability reason -- WHITE stays index 0, every prior name/index is untouched.
    public enum TitleColorPreset { WHITE, GOLD, RED, CYAN, GREEN, CUSTOM, RAINBOW, AURORA, OFF_WHITE }

    /** Warm off-white (ivory) used by {@link TitleColorPreset#OFF_WHITE} -- suits the mod's gold/brown palette
     *  and reads cleanly inside a black outline. */
    public static final int OFF_WHITE_RGB = 0xF3ECDD;

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

    // FRESH-CONFIG DEFAULT REFRESH (2026-07-11, title-styling overhaul): the new out-of-box title look is a
    // warm OFF_WHITE inner color (see the outline fields below). This initializer is the default for FRESH
    // configs only -- zoneEnterTitleColorPreset has existed in every prior 2.0 config, so a saved file
    // already carries its own value (typically WHITE) and Gson keeps it; only brand-new configs and "Reset
    // Title" adopt OFF_WHITE.
    @SerializedName(value = "zoneEnterTitleColorPreset", alternate = {"zoneEnterTitleColorPresetValue"})
    public TitleColorPreset zoneEnterTitleColorPreset = TitleColorPreset.OFF_WHITE;

    @SerializedName(value = "zoneEnterTitleRgb", alternate = {"zoneEnterTitleRgbValue"})
    public int zoneEnterTitleRgb = 0xFFFFFF;

    // ---- Title outline / shadow / glow (NEW 2026-07-11, title-styling overhaul; append-only) ----
    // MIGRATION NOTE (same policy as the compass fresh-default refresh in CompassHudConfig): these three
    // fields are NEW, so a config saved before this pass has no such keys and Gson leaves them at the Java
    // initializers below. Splitting "fresh gets the new look, existing keeps the old look" is impossible
    // with Gson's absent-key handling (the initializer serves both), so per the compass precedent we take
    // the new default for everyone and disclose it here. Accepted PRE-RELEASE because no public build has
    // ever written this file; once one has, changing a default needs a real stamped migration, not a silent
    // reinterpretation.

    /** Draw the title text with a crisp 1px outline (default black) behind the fill. Default = OFF (2026-07-11:
     *  Peetsa tried the outline and preferred the plain fill; the feature/toggle/RGB picker stay available). */
    @SerializedName(value = "zoneEnterTitleOutline", alternate = {"zoneEnterTitleOutlineValue"})
    public boolean zoneEnterTitleOutline = false;

    /** Outline color (0xRRGGBB). Default black; configurable via the Studio's RGB picker (like the custom
     *  fill color). */
    @SerializedName(value = "zoneEnterTitleOutlineRgb", alternate = {"zoneEnterTitleOutlineRgbValue"})
    public int zoneEnterTitleOutlineRgb = 0x000000;

    /** Outline thickness in SCREEN pixels (1..{@link com.example.globe.core.ui.TitleStyle#MAX_OUTLINE_THICKNESS}).
     *  Thickness 1 = the classic crisp 1px ring; higher values stamp a fuller neighbourhood for a bolder edge.
     *  Sanitize-clamped; only meaningful when {@link #zoneEnterTitleOutline} is on. NEW 2026-07-11. */
    @SerializedName(value = "zoneEnterTitleOutlineThickness", alternate = {"zoneEnterTitleOutlineThicknessValue"})
    public int zoneEnterTitleOutlineThickness = 1;

    /** The title's depth treatment: a FADED soft DIRECTIONAL drop shadow (two tapering low-alpha black stamps
     *  offset down-right -- see {@link com.example.globe.core.ui.TitleStyle#DROP_SHADOW_OFFSETS_PX}), NOT the
     *  stark hard vanilla single-pixel shadow. FRESH-config default flipped OFF -> ON 2026-07-11 (Peetsa:
     *  "change the default glow to a faded drop shadow") -- the out-of-box depth cue is now this soft shadow
     *  instead of the omnidirectional glow halo below.
     *  <p>KEY-PRESENCE ASYMMETRY (disclosed): this boolean key was ADDED EARLIER TODAY, so any config already
     *  saved this session carries an explicit {@code false} that Gson keeps -- those users (including Peetsa's
     *  own config) stay drop-shadow-OFF until they toggle it or Reset. Only a brand-new config file (no key on
     *  disk) adopts this {@code true} default. That is the correct always-present-key behavior, not a bug. */
    @SerializedName(value = "zoneEnterTitleDropShadow", alternate = {"zoneEnterTitleDropShadowValue"})
    public boolean zoneEnterTitleDropShadow = true;

    /** A soft dark halo radiating out behind the text (multi-ring low-alpha offsets), independent of the drop
     *  shadow. FRESH-config default flipped ON -> OFF 2026-07-11 (Peetsa: "change the default glow to a faded
     *  drop shadow" -- the depth cue moved to {@link #zoneEnterTitleDropShadow}); the glow stays fully
     *  available via this toggle + {@link #zoneEnterTitleGlowIntensity}.
     *  <p>KEY-PRESENCE ASYMMETRY (disclosed): this boolean key was ADDED EARLIER TODAY, so any config already
     *  saved this session carries an explicit value that Gson keeps -- those users (including Peetsa's own
     *  config) keep whatever glow state they saved until they toggle it or Reset. Only a brand-new config file
     *  (no key on disk) adopts this {@code false} default. That is the correct always-present-key behavior. */
    @SerializedName(value = "zoneEnterTitleGlow", alternate = {"zoneEnterTitleGlowValue"})
    public boolean zoneEnterTitleGlow = false;

    /** Glow halo intensity multiplier on the per-ring alphas (slider range 0.2..2.0). FRESH default 0.75 is
     *  deliberately GENTLE -- 0.75x the shipped ring alphas -- so the out-of-box glow is a soft whisper-halo,
     *  not a heavy shadow (Peetsa's "a *gentle* glow should be default"). Sanitize-clamped; the renderer caps
     *  each ring at {@link com.example.globe.core.ui.TitleStyle#GLOW_RING_ALPHA_CAP} so even 2.0 stays a glow.
     *  NEW 2026-07-11. */
    @SerializedName(value = "zoneEnterTitleGlowIntensity", alternate = {"zoneEnterTitleGlowIntensityValue"})
    public double zoneEnterTitleGlowIntensity = 0.75;

    /** A quick, single, color-aware glimmer wave that sweeps left->right across the title as it appears
     *  (brightens each letter's own color in one rapid crest, then done -- never loops). Default = ON; it's an
     *  ephemeral flourish tied to the title's entrance. Reduce Motion suppresses it (rendered static). */
    @SerializedName(value = "zoneEnterTitleGlimmer", alternate = {"zoneEnterTitleGlimmerValue"})
    public boolean zoneEnterTitleGlimmer = true;

    // Fresh-config default changed NORMAL -> UPPERCASE (2026-07-11, Peetsa). Existing saved configs keep
    // their own case untouched (the key has always existed, so it's always present on disk).
    @SerializedName(value = "zoneEnterTitleCase", alternate = {"zoneEnterTitleCaseValue"})
    public TitleCaseMode zoneEnterTitleCase = TitleCaseMode.UPPERCASE;

    /** Extra pixels between characters; negative = tighter. FRESH default 0 -> 1 (2026-07-11, Peetsa:
     *  "default letter spacing +1") for a touch more breathing room out of the box; existing saved configs
     *  keep their own value (key has always existed). Sanitize-clamped to -4..16. */
    @SerializedName(value = "zoneEnterTitleLetterSpacing", alternate = {"zoneEnterTitleLetterSpacingValue"})
    public int zoneEnterTitleLetterSpacing = 1;

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
        // Null only for an unknown/corrupt enum name (Gson leaves those null); fall to WHITE, the safe
        // long-standing solid, rather than reinterpreting a bad value as the new OFF_WHITE default.
        if (zoneEnterTitleColorPreset == null) zoneEnterTitleColorPreset = TitleColorPreset.WHITE;
        if (zoneEnterTitleCase == null) zoneEnterTitleCase = TitleCaseMode.NORMAL;
        if (accessibilityMode == null) accessibilityMode = AccessibilityMode.STANDARD;

        zoneEnterTitleSeconds = clamp(zoneEnterTitleSeconds, 2.0, 10.0);
        zoneEnterTitleScale = clamp(zoneEnterTitleScale, 1.0, 3.0);
        zoneEnterTitleLetterSpacing = clampInt(zoneEnterTitleLetterSpacing, -4, 16);
        zoneEnterTitleOutlineThickness = clampInt(zoneEnterTitleOutlineThickness, 1,
                com.example.globe.core.ui.TitleStyle.MAX_OUTLINE_THICKNESS);
        zoneEnterTitleGlowIntensity = clamp(zoneEnterTitleGlowIntensity, 0.2, 2.0);

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
