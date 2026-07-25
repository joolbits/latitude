package com.example.globe.client;

import com.example.globe.GlobeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The 8 numbered HUD-look preset slots (2026-07-08, Peetsa's request), persisted separately from the
 * live compass config so switching presets can never corrupt the file the game reads every frame.
 */
public final class CompassHudPresetSlots {

    public static final int SLOT_COUNT = 8;

    /** Null = empty slot. Gson serializes null array entries fine. */
    public CompassHudPreset[] slots = new CompassHudPreset[SLOT_COUNT];

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("globe_compass_hud_presets.json");

    private static CompassHudPresetSlots INSTANCE;

    private CompassHudPresetSlots() {}

    public static CompassHudPresetSlots get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    /** Saves the CURRENT live HUD look into a slot (0-based), overwriting whatever was there. A custom NAME the
     *  slot already carried is preserved (re-saving a look into "My Cold-Weather HUD" keeps that name -- the slot
     *  is the same slot, only its look changed). An empty slot saves nameless (auto-summary). */
    public static void saveCurrentInto(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        CompassHudPresetSlots s = get();
        String keepName = s.slots[slot] != null ? s.slots[slot].name : null;
        CompassHudPreset p = CompassHudPreset.captureCurrent();
        p.name = keepName;
        s.slots[slot] = p;
        save(s);
    }

    /** The slot's raw custom name, or "" if unnamed/empty (never null -- callers pre-fill a rename field). */
    public static String getName(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return "";
        CompassHudPreset p = get().slots[slot];
        return (p != null && p.name != null) ? p.name : "";
    }

    /** Sets (or clears, on blank) a slot's custom name and persists. No-op on an empty slot -- a name needs a
     *  look to belong to. A blank name reverts the slot to the auto-summary (the default behavior). The store rule
     *  lives in {@link com.example.globe.core.ui.PresetName#normalize} (pure, tested). */
    public static void setName(int slot, String name) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        CompassHudPresetSlots s = get();
        CompassHudPreset p = s.slots[slot];
        if (p == null) return;
        p.name = com.example.globe.core.ui.PresetName.normalize(name);
        save(s);
    }

    /** Applies a slot's preset onto the live HUD, or does nothing if the slot is empty. Caller must
     *  still refresh the Studio ({@code this.init()}). Returns true if a preset was applied. */
    public static boolean loadFrom(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        CompassHudPreset p = get().slots[slot];
        if (p == null) return false;
        p.applyToLive();
        return true;
    }

    public static void clear(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        CompassHudPresetSlots s = get();
        s.slots[slot] = null;
        save(s);
    }

    public static boolean isOccupied(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        CompassHudPreset p = get().slots[slot];
        // Require a non-null compass too, matching what summarize() calls "(empty)" and what loadFrom()
        // can actually apply -- a corrupt/hand-edited slot deserialized to {compass:null} must not enable
        // the Load/Clear buttons for a look that would apply nothing.
        return p != null && p.compass != null;
    }

    /** A short, human summary for the slot's button label -- the owner's custom NAME when one is set (HUD Studio
     *  round 10), else the look's style/theme auto-summary, so slots are distinguishable at a glance instead of
     *  all reading identical unnamed rows. */
    public static String summarize(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return "";
        CompassHudPreset p = get().slots[slot];
        if (p == null || p.compass == null) return "(empty)";
        if (com.example.globe.core.ui.PresetName.isSet(p.name)) return p.name;
        CompassHudConfig c = p.compass;
        if (c.style == CompassHudConfig.CompassStyle.DIGITAL) {
            return "Digital";
        }
        String look = c.analogLook != null ? c.analogLook.name() : "DISC";
        String theme = c.analogTheme != null ? c.analogTheme.name() : "";
        String lookNice = look.charAt(0) + look.substring(1).toLowerCase(java.util.Locale.ROOT);
        return lookNice + (theme.isEmpty() ? "" : " / " + themeShort(theme));
    }

    private static String themeShort(String theme) {
        return switch (theme) {
            case "CLASSIC_GOLD" -> "Gold";
            case "PALE_GOLD" -> "Pale Gold";
            case "RED_IVORY" -> "Red Ivory";
            case "CYAN_STEEL" -> "Cyan Steel";
            case "MINT_BRASS" -> "Mint Brass";
            case "OBSIDIAN_RED" -> "Obsidian Red";
            case "ARCTIC_BLUE" -> "Arctic Blue";
            case "EMERALD" -> "Emerald";
            case "ROYAL_PURPLE" -> "Royal Purple";
            case "SUNSET" -> "Sunset";
            case "MONOCHROME" -> "Monochrome";
            case "RAINBOW" -> "Aurora";
            case "CUSTOM" -> "Custom";
            default -> theme;
        };
    }

    private static CompassHudPresetSlots load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    CompassHudPresetSlots s = GSON.fromJson(r, CompassHudPresetSlots.class);
                    if (s != null) {
                        if (s.slots == null || s.slots.length != SLOT_COUNT) {
                            CompassHudPreset[] fixed = new CompassHudPreset[SLOT_COUNT];
                            if (s.slots != null) {
                                System.arraycopy(s.slots, 0, fixed, 0, Math.min(s.slots.length, SLOT_COUNT));
                            }
                            s.slots = fixed;
                        }
                        return s;
                    }
                }
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to read HUD presets; starting with 8 empty slots", e);
        }
        return new CompassHudPresetSlots();
    }

    private static void save(CompassHudPresetSlots s) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(s, w);
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("Failed to write HUD presets", e);
        }
    }
}
