package com.example.globe.core.ui;

/**
 * The naming rules for the 8 HUD preset slots (HUD Studio round 10 item j) -- a pure Java helper (ZERO Minecraft
 * imports) so the "custom name vs. auto-summary" and "blank reverts to auto" logic is plain-JVM unit-testable,
 * independent of the MC-coupled {@code CompassHudPresetSlots} that reads it. One home for both the STORE rule
 * ({@link #normalize}: what a rename field's raw text becomes on disk) and the SHOW rule ({@link #resolve}: what a
 * slot's button label is), so a saved name and the label it produces can never drift.
 */
public final class PresetName {

    private PresetName() {
    }

    /** True iff {@code name} is a real, shown custom name (non-null and not all-whitespace). */
    public static boolean isSet(String name) {
        return name != null && !name.isBlank();
    }

    /** Normalize a raw rename-field value to what should be STORED: trimmed, or null when blank (a blank name is
     *  not persisted -- it reverts the slot to its automatic description). */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The slot's effective label: the custom {@code name} when it {@link #isSet}, else the {@code autoSummary}
     *  (the look's style/theme description) -- the "default name = current behavior" rule. */
    public static String resolve(String name, String autoSummary) {
        return isSet(name) ? name : autoSummary;
    }
}
