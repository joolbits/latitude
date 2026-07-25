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
        OBSIDIAN_RED, ARCTIC_BLUE, EMERALD, ROYAL_PURPLE, SUNSET, MONOCHROME, RAINBOW, CUSTOM
    }
    public enum HAnchor { LEFT, CENTER, RIGHT }
    public enum VAnchor { TOP, CENTER, BOTTOM }
    /** Where the compass group docks. Replaces the old boolean attach (kept below for migration). */
    public enum DockMode { NONE, HOTBAR_RIGHT }
    // Append-only: keep DISC first (the pre-U-D dial, default) and never reorder — looks persist by name.
    public enum CompassLook { DISC, RING, ROSE, TAPE, MINIMAL }

    /**
     * Pin &amp; Grow layout version (design: hud-layout-overhaul-design-20260707.md). Field default is 0 so a
     * legacy JSON with no such key deserializes as LEGACY and gets a one-time migration on first render
     * (screen dims are needed to convert pixel offsets to fractions, so migration cannot happen at load);
     * {@link #fresh()} stamps {@link #CURRENT_LAYOUT_VERSION} so new configs are born migrated.
     */
    public static final int CURRENT_LAYOUT_VERSION = 1;
    public int layoutVersion = 0;

    // Master toggle
    public boolean enabled = true;

    public ShowMode showMode = ShowMode.COMPASS_PRESENT;
    // Default to the analog compass for the 2.0 release. FRESH-CONFIG DEFAULT REFRESH (2026-07-11): the old
    // default (Classic Gold + Disc + inner alpha 0.50) read as tired, and at that alpha the Disc's dark face
    // was nearly invisible so Disc and Ring looked identical. New default = a warm Sunset Rose with a solid
    // face: Rose look (the 8-point compass star), Sunset theme (amber ring / coral needle), inner alpha 0.85
    // (see analogInnerAlpha). These new initializers apply to fresh configs AND to any saved config that
    // predates a field's introduction -- Gson leaves a missing JSON key at the Java default, so an older file
    // silently adopts whatever we set here. Currently that's only analogLook (added 2026-07-07); analogTheme
    // was already present in every prior config, so its default only affects brand-new configs and "Reset
    // Compass". Accepted pre-release because no public build ever wrote this config file; once a public build
    // has, changing a field's default needs a real migration (stamp a version, don't silently reinterpret it).
    public CompassStyle style = CompassStyle.ANALOG;
    public AnalogCompassTheme analogTheme = AnalogCompassTheme.SUNSET;
    public CompassLook analogLook = CompassLook.ROSE;
    public DirectionMode directionMode = DirectionMode.CARDINAL_8;

    // Positioning (screen-space). LEGACY (layoutVersion 0): hAnchor/vAnchor anchored a CONTENT-MEASURED
    // box and offsetX/offsetY were absolute gui-scaled pixels — the audited root cause of the
    // biome-name-moves-the-dial bug and of placements not surviving GUI-scale changes.
    // V1 (Pin & Grow): hAnchor/vAnchor become the PIN GRID (a point), offXFrac/offYFrac are
    // screen-fraction offsets from that grid point, and growH/growV say how the content box extends from
    // the pin. The legacy pixel offsets are retained purely as migration input.
    public HAnchor hAnchor = HAnchor.CENTER;
    public VAnchor vAnchor = VAnchor.TOP;
    public int offsetX = 0;
    public int offsetY = 0;
    public double offXFrac = 0.0;
    public double offYFrac = 0.0;
    public com.example.globe.core.ui.HudLayoutMath.GrowH growH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
    public com.example.globe.core.ui.HudLayoutMath.GrowV growV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;

    // Hotbar dock (v1). Growth is structurally away from the hotbar; see HudLayoutMath.dock.
    public DockMode dockMode = DockMode.NONE;

    // When true, variable-width HUD text elements reserve the width of the longest biome name in the
    // current world's registry, so even the text box's edges are static (Pin & Grow makes the PIN stable
    // regardless; this stabilizes the box outline too).
    public boolean reservedTextWidth = false;

    // Sizing (digital text)
    public float scale = 1.0f; // 0.5 .. 3.0 recommended
    // Fresh-config default nudged 3 -> 5 (2026-07-11) so the beautified digital card has room to breathe
    // around its themed chrome (rounded corners + accent underline). Existing configs keep their saved value.
    public int padding = 5;

    // Sizing (analog disc diameter, unscaled)
    public float analogSize = 32.0f; // pixels

    // Analog styling. Lower = more transparent inner disc. Fresh-config default raised 0.50 -> 0.85
    // (2026-07-11): at 0.50 the Disc look's dark face was so translucent it read as an open Ring, so the two
    // looks were indistinguishable at defaults; 0.85 gives Disc a clearly filled face while Ring stays hollow
    // (Ring never fills the face regardless of this value). Existing configs keep their saved value.
    public float analogInnerAlpha = 0.85f; // 0..1

    // Only used when analogTheme == RAINBOW ("Aurora" in the UI). Seconds for one full color-wheel loop --
    // deliberately defaults slow and the slider's own range skews slow (2026-07-08, Peetsa's request: a
    // fast-cycling dial reads as strobing and can give people a headache). Range narrowed same day
    // (10-40, was 12-90): past ~40s the difference is imperceptible, so the wider range just wasted most
    // of the slider's length on settings that all looked the same.
    public float rainbowCycleSeconds = 24.0f;

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
    public double zoneOffXFrac = 0.0;
    public double zoneOffYFrac = 0.0;
    public com.example.globe.core.ui.HudLayoutMath.GrowH zoneGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
    public com.example.globe.core.ui.HudLayoutMath.GrowV zoneGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
    // Independent text size (2026-07-08, Peetsa's request) -- parity with the Title tab's own Title Size
    // slider. 1.0 = unchanged from every prior version, so old configs render identically after this field
    // is added.
    public float zoneTextScale = 1.0f;

    // Biome label -- same follow/detach/anchor/offset shape as the zone label above, so it can independently
    // ride with the compass or be dragged to its own spot in the HUD Studio.
    public boolean displayBiomeInHud = false;
    public boolean biomeFollowsCompass = true;
    public HAnchor biomeHAnchor = HAnchor.CENTER;
    public VAnchor biomeVAnchor = VAnchor.TOP;
    public int biomeOffsetX = 0;
    public int biomeOffsetY = 0;
    public double biomeOffXFrac = 0.0;
    public double biomeOffYFrac = 0.0;
    public com.example.globe.core.ui.HudLayoutMath.GrowH biomeGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
    public com.example.globe.core.ui.HudLayoutMath.GrowV biomeGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
    // When zone and biome are BOTH attached to the compass line, which comes first. false = "Zone, Biome"
    // (band before biome); true = "Biome, Zone".
    public boolean biomeBeforeZone = false;
    public float biomeTextScale = 1.0f;

    // Coordinates (lat/lon) detachability. Previously always fused to the compass; now can ride with it
    // (default, unchanged behavior) or detach to its own anchor+offset like zone/biome. The actual lat/lon
    // digits still come from showLatitude/showLongitude (digital) or analogShowLatitude/analogShowLongitude
    // (analog) -- this only controls WHERE that text renders, not whether it exists.
    public boolean coordsFollowsCompass = true;
    public HAnchor coordsHAnchor = HAnchor.CENTER;
    public VAnchor coordsVAnchor = VAnchor.TOP;
    public int coordsOffsetX = 0;
    public int coordsOffsetY = 0;
    public double coordsOffXFrac = 0.0;
    public double coordsOffYFrac = 0.0;
    public com.example.globe.core.ui.HudLayoutMath.GrowH coordsGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
    public com.example.globe.core.ui.HudLayoutMath.GrowV coordsGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
    public float coordsTextScale = 1.0f;

    // Clock solar readout (HUD Studio round 10 item l): a small line naming the solar state ("Midnight Sun" /
    // "Polar Night" / "Sun low in the north" ...) shown when the player holds/carries a vanilla clock AND Solar
    // Tilt (SOLAR_TILT_V2_ENABLED) is on. A DETACHED-ONLY element -- it never rides the compass line, so it has no
    // follow toggle; otherwise it mirrors the zone/biome/coords label shape (its own pin/anchor/offset/grow/scale),
    // draggable + resizable in the HUD Studio. Default OFF (opt-in, like the zone/biome labels).
    public boolean displayClockReadout = false;
    public HAnchor clockHAnchor = HAnchor.CENTER;
    public VAnchor clockVAnchor = VAnchor.TOP;
    public double clockOffXFrac = 0.0;
    public double clockOffYFrac = 0.0;
    public com.example.globe.core.ui.HudLayoutMath.GrowH clockGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
    public com.example.globe.core.ui.HudLayoutMath.GrowV clockGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
    public float clockTextScale = 1.0f;

    // Styling
    public boolean showBackground = true;
    public int backgroundRgb = 0x000000;
    // Fresh-config default raised 64 -> 150 (2026-07-11) so the beautified digital card reads as an
    // intentional chip behind the themed chrome instead of a barely-there wash. Digital-only field (analog
    // uses analogInnerAlpha); existing configs keep their saved value.
    public int backgroundAlpha = 150; // 0..255 (lower = less dark)
    // Fresh-config default changed from pure white (2026-07-11) to the same warm off-white/ivory used by
    // the zone-title's OFF_WHITE preset (LatitudeConfigData.OFF_WHITE_RGB = 0xF3ECDD), so the compass text
    // and the titles read as one consistent palette. Existing configs keep their saved textRgb.
    public int textRgb = 0xF3ECDD;
    public int textAlpha = 255; // 0..255
    // Overrides textRgb (and the Custom RGB sliders) with a per-letter rainbow cycle when true, on the compass
    // digital line and every zone/biome/coords label (attached or detached).
    public boolean textRainbow = false;
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
        CompassHudConfig cfg = new CompassHudConfig();
        cfg.layoutVersion = CURRENT_LAYOUT_VERSION; // born migrated; only disk-loaded legacies stay at 0
        // Default pin: classic top-center, tucked at the screen edge (y=EDGE_INSET). U-D shipped a 15%
        // downward "boss-bar nudge" here; TEST 28 live feedback read that as the compass floating
        // mid-screen after a Reset, which is worse than the transient boss-bar overlap it avoided
        // (boss bars only exist during boss fights, and the Studio makes moving the compass trivial).
        return cfg;
    }

    public static void reload() {
        INSTANCE = load();
    }

    /** A detached deep copy of {@code src}, for snapshot/restore (HUD Studio Cancel). Every config field is an
     *  immutable value (primitive, boxed, or enum), so a reflective field-by-field copy is a safe, drift-proof
     *  snapshot -- it can never fall out of sync with the field list the way a hand-maintained copy block can. */
    public static CompassHudConfig copyOf(CompassHudConfig src) {
        CompassHudConfig copy = new CompassHudConfig();
        copy.copyFrom(src);
        return copy;
    }

    /** Overwrites every non-static, non-final instance field of {@code this} from {@code src} (see
     *  {@link #copyOf}). Used to restore the live singleton from a snapshot without replacing the instance
     *  (so references held elsewhere stay valid). */
    public void copyFrom(CompassHudConfig src) {
        for (java.lang.reflect.Field f : CompassHudConfig.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) continue;
            try {
                f.set(this, f.get(src));
            } catch (IllegalAccessException ignored) {
                // All instance fields here are public; this cannot happen, but stay defensive.
            }
        }
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

        CompassHudConfig fresh = fresh();
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

    /** Pins live within one screen of their grid base; anything wilder is a corrupt/hand-edited value. */
    private static double clampFrac(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /** Package-private (not private) so CompassHudPreset can sanitize a snapshot deserialized from a
     *  preset slot or an imported clipboard blob -- the same defensive pass every on-disk load gets. */
    void sanitize() {
        if (showMode == null) showMode = ShowMode.COMPASS_PRESENT;
        if (style == null) style = CompassStyle.DIGITAL;
        if (analogTheme == null) analogTheme = AnalogCompassTheme.SUNSET;
        if (analogLook == null) analogLook = CompassLook.ROSE;
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
        if (clockHAnchor == null) clockHAnchor = HAnchor.CENTER;
        if (clockVAnchor == null) clockVAnchor = VAnchor.TOP;
        if (padding < 0) padding = 0;
        // Pin & Grow v1 fields (null-safe for hand-edited/legacy JSON; fracs clamped so a pin can never
        // be persisted off-screen — render-time clamping stays as the second belt).
        if (growH == null) growH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
        if (growV == null) growV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
        if (zoneGrowH == null) zoneGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
        if (zoneGrowV == null) zoneGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
        if (biomeGrowH == null) biomeGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
        if (biomeGrowV == null) biomeGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
        if (coordsGrowH == null) coordsGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
        if (coordsGrowV == null) coordsGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
        if (clockGrowH == null) clockGrowH = com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
        if (clockGrowV == null) clockGrowV = com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
        if (dockMode == null) dockMode = DockMode.NONE;
        offXFrac = clampFrac(offXFrac);
        offYFrac = clampFrac(offYFrac);
        zoneOffXFrac = clampFrac(zoneOffXFrac);
        zoneOffYFrac = clampFrac(zoneOffYFrac);
        biomeOffXFrac = clampFrac(biomeOffXFrac);
        biomeOffYFrac = clampFrac(biomeOffYFrac);
        coordsOffXFrac = clampFrac(coordsOffXFrac);
        coordsOffYFrac = clampFrac(coordsOffYFrac);
        clockOffXFrac = clampFrac(clockOffXFrac);
        clockOffYFrac = clampFrac(clockOffYFrac);
        if (layoutVersion < 0) layoutVersion = 0;
        if (layoutVersion > CURRENT_LAYOUT_VERSION) layoutVersion = CURRENT_LAYOUT_VERSION;
        // Legacy boolean attach migrates to the dock enum (position semantics live in HudLayoutMath now).
        if (attachToHotbarCompass && dockMode == DockMode.NONE) {
            dockMode = DockMode.HOTBAR_RIGHT;
        }
        if (backgroundAlpha < 0) backgroundAlpha = 0;
        if (backgroundAlpha > 255) backgroundAlpha = 255;
        if (textAlpha < 0) textAlpha = 0;
        if (textAlpha > 255) textAlpha = 255;
        if (rainbowCycleSeconds < 10.0f || rainbowCycleSeconds > 40.0f || Float.isNaN(rainbowCycleSeconds)) rainbowCycleSeconds = 24.0f;
        if (zoneTextScale < 0.5f || zoneTextScale > 3.0f || Float.isNaN(zoneTextScale)) zoneTextScale = 1.0f;
        if (biomeTextScale < 0.5f || biomeTextScale > 3.0f || Float.isNaN(biomeTextScale)) biomeTextScale = 1.0f;
        if (coordsTextScale < 0.5f || coordsTextScale > 3.0f || Float.isNaN(coordsTextScale)) coordsTextScale = 1.0f;
        if (clockTextScale < 0.5f || clockTextScale > 3.0f || Float.isNaN(clockTextScale)) clockTextScale = 1.0f;
    }
}
