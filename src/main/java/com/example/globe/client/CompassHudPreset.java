package com.example.globe.client;

import com.example.globe.core.config.LatitudeConfigData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * A portable snapshot of "the HUD's look": the full Compass+Labels config plus the Title tab's own
 * fields. Deliberately does NOT include the General tab's warnings/screenshot settings -- those are
 * player preferences about the game, not part of a compass "look" someone would want to save or share.
 *
 * <p>Backs BOTH the 8-slot Presets tab and the Export/Import-to-clipboard feature (2026-07-08,
 * Peetsa's request) -- they are the exact same shape, just persisted to a different place (a numbered
 * slot file vs. the system clipboard), so this one class serves both.
 */
public final class CompassHudPreset {

    /** Bumped only if this shape changes incompatibly; unknown/missing fields degrade via Gson's normal
     *  null-then-sanitize behavior, not a parse failure (same discipline as every other config here). */
    public int presetFormatVersion = 1;

    public CompassHudConfig compass;
    public TitleFields title = new TitleFields();

    public static final class TitleFields {
        public boolean enabled = true;
        public double seconds = 6.0;
        public double scale = 1.8;
        public LatitudeConfigData.TitleColorPreset colorPreset = LatitudeConfigData.TitleColorPreset.OFF_WHITE;
        public int customRgb = 0xFFFFFF;
        public LatitudeConfigData.TitleCaseMode caseMode = LatitudeConfigData.TitleCaseMode.UPPERCASE;
        public int letterSpacing = 1;
        // Outline / shadow / glow (NEW 2026-07-11, title-styling overhaul). Defaults mirror the fresh-config
        // look so a preset saved without these keys imports as the out-of-box style. Depth cue flipped to the
        // FADED drop shadow (dropShadow false->true, glow true->false) 2026-07-11: "change the default glow to
        // a faded drop shadow"; glowIntensity 0.75 stays for when glow is toggled on; outlineThickness 1 = the
        // classic crisp 1px ring.
        public boolean outline = false;
        public int outlineRgb = 0x000000;
        public int outlineThickness = 1;
        public boolean dropShadow = true;
        public boolean glow = false;
        public double glowIntensity = 0.75;
        // One-shot color-aware glimmer sweep (NEW 2026-07-11). Default ON so a preset saved without this key
        // imports with the glimmer on, matching the fresh-config look.
        public boolean glimmer = true;
        public boolean showBaseDegrees = true;
        public int offsetX = 0;
        public int offsetY = -40;
        public boolean draggable = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // One-level undo/redo for the last Load/Import (2026-07-09, Peetsa's request: recover from an accidental
    // load, and re-apply it if the undo itself was the accident). In-memory only (a mistaken load is a
    // this-session accident, not something worth persisting to disk like the numbered slots) -- plain static
    // fields, same pattern as every other singleton here. Classic single-step toggle: a fresh Load/Import
    // arms Undo (and clears any Redo branch); Undo swaps the live state with the undo point and arms Redo;
    // Redo swaps back and re-arms Undo.
    private static CompassHudPreset undoSnapshot;
    private static CompassHudPreset redoSnapshot;
    // Set while undo/redo drive applyToLive() themselves, so the auto-capture at its top doesn't clobber the
    // snapshots those operations are carefully managing.
    private static boolean suppressHistoryCapture = false;

    public static boolean hasUndo() {
        return undoSnapshot != null;
    }

    public static boolean hasRedo() {
        return redoSnapshot != null;
    }

    /** Discards any pending Undo/Redo point without applying either. For a cancelled Studio session: the
     *  snapshots are static (they outlive screen close), so leaving them set would let a later reopen's Undo
     *  resurrect state the player just discarded via Cancel. */
    public static void clearUndoRedo() {
        undoSnapshot = null;
        redoSnapshot = null;
    }

    /** Restores the live HUD to whatever it was immediately before the last Load/Import, and arms Redo so the
     *  step can be re-applied. One level only -- matches what "undo my last preset load" actually means here. */
    public static void undoLastLoad() {
        if (undoSnapshot == null) return;
        CompassHudPreset target = undoSnapshot;
        undoSnapshot = null;
        CompassHudPreset current = captureCurrent();   // what we're leaving becomes the redo target
        applyWithoutHistory(target);
        redoSnapshot = current;
    }

    /** Re-applies the Load/Import that Undo just reverted, and re-arms Undo. */
    public static void redoLastLoad() {
        if (redoSnapshot == null) return;
        CompassHudPreset target = redoSnapshot;
        redoSnapshot = null;
        CompassHudPreset current = captureCurrent();
        applyWithoutHistory(target);
        undoSnapshot = current;
    }

    private static void applyWithoutHistory(CompassHudPreset p) {
        suppressHistoryCapture = true;
        try {
            p.applyToLive();
        } finally {
            suppressHistoryCapture = false;
        }
    }

    /** Snapshots the CURRENTLY LIVE compass config + title fields. */
    public static CompassHudPreset captureCurrent() {
        CompassHudPreset p = new CompassHudPreset();
        // Deep-copy via a JSON round trip so this snapshot can never alias the live singleton -- editing
        // a saved preset later must never retroactively change what's on screen right now.
        p.compass = GSON.fromJson(GSON.toJson(CompassHudConfig.get()), CompassHudConfig.class);
        p.title.enabled = LatitudeConfig.zoneEnterTitleEnabled;
        p.title.seconds = LatitudeConfig.zoneEnterTitleSeconds;
        p.title.scale = LatitudeConfig.zoneEnterTitleScale;
        p.title.colorPreset = LatitudeConfig.zoneEnterTitleColorPreset;
        p.title.customRgb = LatitudeConfig.zoneEnterTitleRgb;
        p.title.caseMode = LatitudeConfig.zoneEnterTitleCase;
        p.title.letterSpacing = LatitudeConfig.zoneEnterTitleLetterSpacing;
        p.title.outline = LatitudeConfig.zoneEnterTitleOutline;
        p.title.outlineRgb = LatitudeConfig.zoneEnterTitleOutlineRgb;
        p.title.outlineThickness = LatitudeConfig.zoneEnterTitleOutlineThickness;
        p.title.dropShadow = LatitudeConfig.zoneEnterTitleDropShadow;
        p.title.glow = LatitudeConfig.zoneEnterTitleGlow;
        p.title.glowIntensity = LatitudeConfig.zoneEnterTitleGlowIntensity;
        p.title.glimmer = LatitudeConfig.zoneEnterTitleGlimmer;
        p.title.showBaseDegrees = LatitudeConfig.showZoneBaseDegreesOnTitle;
        p.title.offsetX = LatitudeConfig.zoneEnterTitleOffsetX;
        p.title.offsetY = LatitudeConfig.zoneEnterTitleOffsetY;
        p.title.draggable = LatitudeConfig.zoneEnterTitleDraggable;
        return p;
    }

    /**
     * Applies this snapshot onto the LIVE singletons and saves both to disk. Copies field-by-field INTO
     * the existing {@code CompassHudConfig.get()} instance (via reflection) rather than replacing it --
     * other code (open Studio widgets) holds a reference to that exact instance, same reasoning as why
     * "Reset HUD"/"Reset Compass" mutate fields in place instead of swapping the singleton. Reflection
     * (not a hand-written field list) means this can never silently miss a newly-added field the way a
     * manual copy function could. Caller must refresh the Studio ({@code this.init()}) afterward, same as
     * every other config-cascading action in that screen.
     */
    public void applyToLive() {
        if (!suppressHistoryCapture) {
            // A fresh Load/Import: snapshot whatever is LIVE right now (before it's overwritten below) as the
            // undo point, and drop any pending Redo branch. This is the one choke point every Load-a-slot and
            // Import-from-clipboard call already passes through, so undo/redo can't be forgotten at some
            // future call site the way a per-button capture could be (same single-source-of-truth reasoning
            // as copyAllInstanceFields below).
            undoSnapshot = captureCurrent();
            redoSnapshot = null;
        }
        if (compass != null) {
            compass.sanitize();
            copyAllInstanceFields(compass, CompassHudConfig.get());
            CompassHudConfig.saveCurrent();
        }
        if (title != null) {
            LatitudeConfig.zoneEnterTitleEnabled = title.enabled;
            LatitudeConfig.zoneEnterTitleSeconds = title.seconds;
            LatitudeConfig.zoneEnterTitleScale = title.scale;
            LatitudeConfig.zoneEnterTitleColorPreset = title.colorPreset != null ? title.colorPreset : LatitudeConfigData.TitleColorPreset.WHITE;
            LatitudeConfig.zoneEnterTitleRgb = title.customRgb;
            LatitudeConfig.zoneEnterTitleCase = title.caseMode != null ? title.caseMode : LatitudeConfigData.TitleCaseMode.NORMAL;
            LatitudeConfig.zoneEnterTitleLetterSpacing = title.letterSpacing;
            LatitudeConfig.zoneEnterTitleOutline = title.outline;
            LatitudeConfig.zoneEnterTitleOutlineRgb = title.outlineRgb;
            LatitudeConfig.zoneEnterTitleOutlineThickness = title.outlineThickness;
            LatitudeConfig.zoneEnterTitleDropShadow = title.dropShadow;
            LatitudeConfig.zoneEnterTitleGlow = title.glow;
            LatitudeConfig.zoneEnterTitleGlowIntensity = title.glowIntensity;
            LatitudeConfig.zoneEnterTitleGlimmer = title.glimmer;
            LatitudeConfig.showZoneBaseDegreesOnTitle = title.showBaseDegrees;
            LatitudeConfig.zoneEnterTitleOffsetX = title.offsetX;
            LatitudeConfig.zoneEnterTitleOffsetY = title.offsetY;
            LatitudeConfig.zoneEnterTitleDraggable = title.draggable;
            // An imported/hand-edited preset can carry out-of-range title numerics (scale/seconds/letter
            // spacing) the Studio sliders could never produce -- clamp the LIVE fields (not just the saved
            // copy) so a bad paste can't leave the on-screen title broken, mirroring compass.sanitize() above.
            LatitudeConfig.sanitizeLive();
            LatitudeConfig.saveCurrent();
        }
    }

    private static void copyAllInstanceFields(Object src, Object dst) {
        for (Field f : src.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                f.set(dst, f.get(src));
            } catch (ReflectiveOperationException ignored) {
                // A field that can't be copied is left at dst's current value rather than aborting the
                // whole apply -- one odd field shouldn't block restoring the rest of a preset.
            }
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Returns null (never throws) on unparseable text, so callers can show "that didn't look right"
     *  instead of crashing the Studio on a bad paste. */
    public static CompassHudPreset fromJson(String json) {
        try {
            CompassHudPreset p = GSON.fromJson(json, CompassHudPreset.class);
            if (p == null || p.compass == null) return null;
            if (p.title == null) p.title = new TitleFields();
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
