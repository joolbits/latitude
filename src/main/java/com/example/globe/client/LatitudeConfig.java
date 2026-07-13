package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.example.globe.core.config.LatitudeConfigData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Static facade over {@link LatitudeConfigData} (globe_latitude.json). The statics are the runtime API the
 * client code reads every frame; the data class is the single source of defaults, the persisted shape, and
 * the pure-JVM-testable half (U-C config hygiene — this replaced a triple-mirrored layout where every field
 * existed as a static, an instance {@code ...Value} twin, and three hand-maintained copy blocks, plus ten
 * fields nothing read: the band-blend trio, storm wall, debug blend, two compass-degree flags, clipboard
 * fallback, and the ZoneEntryNotifier pair).
 */
public final class LatitudeConfig {

    // ---- runtime API (assigned from LatitudeConfigData; defaults live THERE, not here) ----
    public static boolean enableWarningParticles;
    public static boolean showWarningMessages;

    public static boolean zoneEnterTitleEnabled;
    public static double zoneEnterTitleSeconds;
    public static double zoneEnterTitleScale;
    public static LatitudeConfigData.TitleColorPreset zoneEnterTitleColorPreset;
    public static int zoneEnterTitleRgb;
    public static LatitudeConfigData.TitleCaseMode zoneEnterTitleCase;
    public static int zoneEnterTitleLetterSpacing;

    public static boolean zoneEnterTitleOutline;
    public static int zoneEnterTitleOutlineRgb;
    public static int zoneEnterTitleOutlineThickness;
    public static boolean zoneEnterTitleDropShadow;
    public static boolean zoneEnterTitleGlow;
    public static double zoneEnterTitleGlowIntensity;
    public static boolean zoneEnterTitleGlimmer;
    public static double zoneEnterTitleGlimmerIntensity;

    public static int zoneEnterTitleOffsetX;
    public static int zoneEnterTitleOffsetY;
    public static boolean zoneEnterTitleDraggable;

    public static boolean reduceMotion;

    public static boolean hudSnapEnabled;
    public static int hudSnapPixels;

    public static boolean showZoneBaseDegreesOnTitle;

    public static boolean screenshotClipboardEnabled;
    public static boolean screenshotAlsoSaveToDisk;
    public static boolean screenshotClipboardWindowsPowerShell;
    public static boolean captureWriteCsv;

    public static LatitudeConfigData.AccessibilityMode accessibilityMode;

    static {
        applyFrom(LatitudeConfigData.fresh()); // sane values even before load() runs
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("globe_latitude.json");

    private static boolean loaded;

    private LatitudeConfig() {
    }

    /** Idempotent load-once; called at client init and lazily from saveCurrent()/reload(). */
    public static void get() {
        if (!loaded) {
            load();
        }
    }

    public static void reload() {
        load();
    }

    public static void saveCurrent() {
        get();
        save(captureTo());
    }

    /** Clamp the LIVE static fields to their valid ranges, using {@link LatitudeConfigData#sanitize()} as
     *  the single source of truth (captureTo() sanitizes then applyFrom() writes back). saveCurrent() only
     *  sanitizes the on-disk copy, so any caller that sets live fields from an untrusted source -- a preset
     *  import / hand-edited slot -- must call this to clamp what's actually on screen, not just what's saved. */
    public static void sanitizeLive() {
        get();
        applyFrom(captureTo());
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    LatitudeConfigData data = GSON.fromJson(r, LatitudeConfigData.class);
                    if (data != null) {
                        data.sanitize();
                        boolean legacy = data.configVersion < LatitudeConfigData.CURRENT_CONFIG_VERSION;
                        data.configVersion = LatitudeConfigData.CURRENT_CONFIG_VERSION;
                        applyFrom(data);
                        if (legacy) {
                            save(data); // one-time rewrite: clean key names, dead keys dropped, version stamped
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to read latitude config; using defaults", e);
        }

        LatitudeConfigData fresh = LatitudeConfigData.fresh();
        applyFrom(fresh);
        save(fresh);
    }

    private static void applyFrom(LatitudeConfigData d) {
        enableWarningParticles = d.enableWarningParticles;
        showWarningMessages = d.showWarningMessages;

        zoneEnterTitleEnabled = d.zoneEnterTitleEnabled;
        zoneEnterTitleSeconds = d.zoneEnterTitleSeconds;
        zoneEnterTitleScale = d.zoneEnterTitleScale;
        zoneEnterTitleColorPreset = d.zoneEnterTitleColorPreset;
        zoneEnterTitleRgb = d.zoneEnterTitleRgb;
        zoneEnterTitleCase = d.zoneEnterTitleCase;
        zoneEnterTitleLetterSpacing = d.zoneEnterTitleLetterSpacing;

        zoneEnterTitleOutline = d.zoneEnterTitleOutline;
        zoneEnterTitleOutlineRgb = d.zoneEnterTitleOutlineRgb;
        zoneEnterTitleOutlineThickness = d.zoneEnterTitleOutlineThickness;
        zoneEnterTitleDropShadow = d.zoneEnterTitleDropShadow;
        zoneEnterTitleGlow = d.zoneEnterTitleGlow;
        zoneEnterTitleGlowIntensity = d.zoneEnterTitleGlowIntensity;
        zoneEnterTitleGlimmer = d.zoneEnterTitleGlimmer;
        zoneEnterTitleGlimmerIntensity = d.zoneEnterTitleGlimmerIntensity;

        zoneEnterTitleOffsetX = d.zoneEnterTitleOffsetX;
        zoneEnterTitleOffsetY = d.zoneEnterTitleOffsetY;
        zoneEnterTitleDraggable = d.zoneEnterTitleDraggable;

        reduceMotion = d.reduceMotion;

        hudSnapEnabled = d.hudSnapEnabled;
        hudSnapPixels = d.hudSnapPixels;

        showZoneBaseDegreesOnTitle = d.showZoneBaseDegreesOnTitle;

        screenshotClipboardEnabled = d.screenshotClipboardEnabled;
        screenshotAlsoSaveToDisk = d.screenshotAlsoSaveToDisk;
        screenshotClipboardWindowsPowerShell = d.screenshotClipboardWindowsPowerShell;
        captureWriteCsv = d.captureWriteCsv;

        accessibilityMode = d.accessibilityMode;
    }

    /** Detached snapshot of the live static fields, for snapshot/restore (HUD Studio Cancel). Returns a
     *  fresh, sanitized {@link LatitudeConfigData} the caller can hold and later hand back to
     *  {@link #restore(LatitudeConfigData)}. */
    public static LatitudeConfigData snapshot() {
        get();
        return captureTo();
    }

    /** Writes a previously-taken {@link #snapshot()} back onto the live static fields. Does not persist to
     *  disk -- the caller decides whether to follow with {@link #saveCurrent()}. */
    public static void restore(LatitudeConfigData snap) {
        applyFrom(snap);
    }

    private static LatitudeConfigData captureTo() {
        LatitudeConfigData d = LatitudeConfigData.fresh();
        d.enableWarningParticles = enableWarningParticles;
        d.showWarningMessages = showWarningMessages;

        d.zoneEnterTitleEnabled = zoneEnterTitleEnabled;
        d.zoneEnterTitleSeconds = zoneEnterTitleSeconds;
        d.zoneEnterTitleScale = zoneEnterTitleScale;
        d.zoneEnterTitleColorPreset = zoneEnterTitleColorPreset;
        d.zoneEnterTitleRgb = zoneEnterTitleRgb;
        d.zoneEnterTitleCase = zoneEnterTitleCase;
        d.zoneEnterTitleLetterSpacing = zoneEnterTitleLetterSpacing;

        d.zoneEnterTitleOutline = zoneEnterTitleOutline;
        d.zoneEnterTitleOutlineRgb = zoneEnterTitleOutlineRgb;
        d.zoneEnterTitleOutlineThickness = zoneEnterTitleOutlineThickness;
        d.zoneEnterTitleDropShadow = zoneEnterTitleDropShadow;
        d.zoneEnterTitleGlow = zoneEnterTitleGlow;
        d.zoneEnterTitleGlowIntensity = zoneEnterTitleGlowIntensity;
        d.zoneEnterTitleGlimmer = zoneEnterTitleGlimmer;
        d.zoneEnterTitleGlimmerIntensity = zoneEnterTitleGlimmerIntensity;

        d.zoneEnterTitleOffsetX = zoneEnterTitleOffsetX;
        d.zoneEnterTitleOffsetY = zoneEnterTitleOffsetY;
        d.zoneEnterTitleDraggable = zoneEnterTitleDraggable;

        d.reduceMotion = reduceMotion;

        d.hudSnapEnabled = hudSnapEnabled;
        d.hudSnapPixels = hudSnapPixels;

        d.showZoneBaseDegreesOnTitle = showZoneBaseDegreesOnTitle;

        d.screenshotClipboardEnabled = screenshotClipboardEnabled;
        d.screenshotAlsoSaveToDisk = screenshotAlsoSaveToDisk;
        d.screenshotClipboardWindowsPowerShell = screenshotClipboardWindowsPowerShell;
        d.captureWriteCsv = captureWriteCsv;

        d.accessibilityMode = accessibilityMode;
        d.sanitize();
        return d;
    }

    private static void save(LatitudeConfigData d) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(d, w);
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to write latitude config", e);
        }
    }
}
