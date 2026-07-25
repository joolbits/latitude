package com.example.globe.core.ui;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM probes for the HUD preset naming rules (HUD Studio round 10 item j): the store rule
 * ({@link PresetName#normalize}), the show rule ({@link PresetName#resolve} / {@link PresetName#isSet}), and that a
 * name persists across a Gson round trip exactly like it does on the real (MC-coupled) slot store -- proven here
 * on a tiny mirror DTO so the assertion needs no Minecraft classpath.
 */
class PresetNameTest {

    private static final Gson GSON = new Gson();

    @Test
    void isSetOnlyForRealNames() {
        assertFalse(PresetName.isSet(null));
        assertFalse(PresetName.isSet(""));
        assertFalse(PresetName.isSet("   "));
        assertTrue(PresetName.isSet("Cold HUD"));
    }

    @Test
    void normalizeTrimsAndBlanksToNull() {
        assertNull(PresetName.normalize(null));
        assertNull(PresetName.normalize(""));
        assertNull(PresetName.normalize("   "), "a blank name reverts the slot to its auto-summary");
        assertEquals("My HUD", PresetName.normalize("  My HUD  "), "trimmed");
        assertEquals("Rose", PresetName.normalize("Rose"));
    }

    @Test
    void resolvePrefersCustomNameElseAutoSummary() {
        assertEquals("Rose / Sunset", PresetName.resolve(null, "Rose / Sunset"), "unnamed => auto-summary");
        assertEquals("Rose / Sunset", PresetName.resolve("  ", "Rose / Sunset"), "blank => auto-summary");
        assertEquals("Night Owl HUD", PresetName.resolve("Night Owl HUD", "Rose / Sunset"), "custom name wins");
    }

    /** A slot's name is a plain Gson field (like every other preset field), so it survives a write->read round
     *  trip under the clean key "name". Mirrors the real {@code CompassHudPreset.name} persistence shape. */
    @Test
    void nameRoundTripsThroughGson() {
        NamedSlotMirror out = new NamedSlotMirror();
        out.name = "Frozen Expedition";
        String json = GSON.toJson(out);
        assertTrue(json.contains("\"name\""), "persists under the clean key name");

        NamedSlotMirror in = GSON.fromJson(json, NamedSlotMirror.class);
        assertEquals("Frozen Expedition", in.name, "the name survives the round trip");

        // An absent name key deserializes as null => resolve() falls back to the auto-summary.
        NamedSlotMirror absent = GSON.fromJson("{\"presetFormatVersion\":1}", NamedSlotMirror.class);
        assertNull(absent.name);
        assertEquals("(auto)", PresetName.resolve(absent.name, "(auto)"));
    }

    /** The persisted shape of a named preset slot -- {@code presetFormatVersion} + {@code name} -- matching the
     *  real {@code CompassHudPreset} field names, so this locks the wire format the MC-coupled class relies on. */
    private static final class NamedSlotMirror {
        int presetFormatVersion = 1;
        String name;
    }
}
