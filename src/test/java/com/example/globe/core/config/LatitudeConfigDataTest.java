package com.example.globe.core.config;

import com.example.globe.core.config.LatitudeConfigData.AccessibilityMode;
import com.example.globe.core.config.LatitudeConfigData.TitleCaseMode;
import com.example.globe.core.config.LatitudeConfigData.TitleColorPreset;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U-C gate probes (design: hud-layout-overhaul-design-20260707.md, Pillar 3 config hygiene):
 * the persisted config shape must (a) read the LEGACY on-disk key names (`<field>Value`, written by the
 * pre-U-C triple-mirrored class) via alternate names, (b) keep defaults for absent keys, (c) survive a
 * clean write→read round-trip under the NEW key names, and (d) sanitize hostile values.
 */
class LatitudeConfigDataTest {

    private static final Gson GSON = new Gson();

    @Test
    void freshIsCurrentVersionWithDocumentedDefaults() {
        LatitudeConfigData d = LatitudeConfigData.fresh();
        assertEquals(LatitudeConfigData.CURRENT_CONFIG_VERSION, d.configVersion);
        assertTrue(d.showWarningMessages);
        assertTrue(d.zoneEnterTitleEnabled);
        assertEquals(6.0, d.zoneEnterTitleSeconds);
        assertEquals(1.8, d.zoneEnterTitleScale);
        // FRESH default refreshed 2026-07-11 (title-styling overhaul, refined same day): warm OFF_WHITE fill,
        // no outline (1px thickness when enabled), FADED drop shadow ON, glow OFF (intensity 0.75 when on),
        // ALL CAPS, letter spacing 1. Depth cue is now the faded drop shadow, not the glow halo (Peetsa:
        // "change the default glow to a faded drop shadow").
        assertEquals(TitleColorPreset.OFF_WHITE, d.zoneEnterTitleColorPreset);
        assertFalse(d.zoneEnterTitleOutline);
        assertEquals(0x000000, d.zoneEnterTitleOutlineRgb);
        assertEquals(1, d.zoneEnterTitleOutlineThickness);
        assertTrue(d.zoneEnterTitleDropShadow, "fresh default is the faded drop shadow (2026-07-11)");
        assertFalse(d.zoneEnterTitleGlow, "glow moved off-by-default; drop shadow is the depth cue now");
        assertEquals(0.75, d.zoneEnterTitleGlowIntensity);
        assertEquals(1, d.zoneEnterTitleLetterSpacing, "fresh letter spacing +1 (2026-07-11)");
        assertEquals(TitleCaseMode.UPPERCASE, d.zoneEnterTitleCase);
        assertEquals(-40, d.zoneEnterTitleOffsetY);
        assertTrue(d.hudSnapEnabled);
        assertEquals(8, d.hudSnapPixels);
    }

    /** A real pre-U-C file: legacy `...Value` keys, dead fields included, no version key. */
    @Test
    void legacyFileLoadsThroughAlternateNamesAndIgnoresDeadKeys() {
        String legacy = """
                {
                  "enableWarningParticlesValue": false,
                  "showWarningMessagesValue": false,
                  "enableEwStormWallValue": true,
                  "zoneEntryNotifyModeValue": "TOAST",
                  "showLatitudeDegreesValue": true,
                  "latitudeDegreesOnCompassValue": true,
                  "zoneEnterTitleEnabledValue": true,
                  "zoneEnterTitleSecondsValue": 4.5,
                  "zoneEnterTitleScaleValue": 2.2,
                  "zoneEnterTitleColorPresetValue": "GOLD",
                  "zoneEnterTitleRgbValue": 43690,
                  "zoneEnterTitleCaseValue": "UPPERCASE",
                  "zoneEnterTitleLetterSpacingValue": 3,
                  "zoneEnterTitleOffsetXValue": 17,
                  "zoneEnterTitleOffsetYValue": -55,
                  "zoneEnterTitleDraggableValue": false,
                  "hudSnapEnabledValue": false,
                  "hudSnapPixelsValue": 16,
                  "showLatitudeDegreesOnCompassValue": false,
                  "showZoneBaseDegreesOnTitleValue": false,
                  "latitudeBandBlendingEnabledValue": true,
                  "latitudeBandBlendWidthFracValue": 0.08,
                  "latitudeBandBoundaryWarpFracValue": 0.06,
                  "debugLatitudeBlendValue": false,
                  "screenshotClipboardEnabledValue": false,
                  "screenshotClipboardFallbackToDiskValue": true,
                  "screenshotAlsoSaveToDiskValue": false,
                  "screenshotClipboardWindowsPowerShellValue": false,
                  "captureWriteCsvValue": true
                }
                """;
        LatitudeConfigData d = GSON.fromJson(legacy, LatitudeConfigData.class);
        d.sanitize();

        assertEquals(0, d.configVersion, "legacy file must read as version 0");
        assertFalse(d.enableWarningParticles);
        assertFalse(d.showWarningMessages);
        assertEquals(4.5, d.zoneEnterTitleSeconds);
        assertEquals(2.2, d.zoneEnterTitleScale);
        assertEquals(TitleColorPreset.GOLD, d.zoneEnterTitleColorPreset);
        assertEquals(0xAAAA, d.zoneEnterTitleRgb);
        assertEquals(TitleCaseMode.UPPERCASE, d.zoneEnterTitleCase);
        assertEquals(3, d.zoneEnterTitleLetterSpacing);
        assertEquals(17, d.zoneEnterTitleOffsetX);
        assertEquals(-55, d.zoneEnterTitleOffsetY);
        assertFalse(d.zoneEnterTitleDraggable);
        assertFalse(d.hudSnapEnabled);
        assertEquals(16, d.hudSnapPixels);
        assertFalse(d.showZoneBaseDegreesOnTitle);
        assertFalse(d.screenshotClipboardEnabled);
        assertFalse(d.screenshotAlsoSaveToDisk);
        assertTrue(d.captureWriteCsv);
    }

    @Test
    void absentKeysKeepDefaults() {
        LatitudeConfigData d = GSON.fromJson("{\"hudSnapPixelsValue\": 4}", LatitudeConfigData.class);
        d.sanitize();
        assertEquals(4, d.hudSnapPixels);
        assertEquals(6.0, d.zoneEnterTitleSeconds, "absent key must keep the default");
        // Absent keys adopt the Java initializer (Gson) -- so an existing pre-release file with none of the
        // new title-style keys picks up the new default look. This is the disclosed pre-release migration.
        assertEquals(TitleColorPreset.OFF_WHITE, d.zoneEnterTitleColorPreset);
        assertFalse(d.zoneEnterTitleOutline);
        assertEquals(1, d.zoneEnterTitleOutlineThickness, "absent thickness key adopts the 1px default");
        assertEquals(1, d.zoneEnterTitleLetterSpacing, "absent letter-spacing key adopts the new +1 default");
        // NOTE the key-presence asymmetry: the dropShadow/glow BOOLEANs existed before this pass, so a real
        // saved file carries its own value; but a file that genuinely lacks the keys (as here) adopts the new
        // faded-drop-shadow-on / glow-off defaults.
        assertTrue(d.zoneEnterTitleDropShadow, "absent drop-shadow key adopts the new faded-drop-shadow default");
        assertFalse(d.zoneEnterTitleGlow, "absent glow key adopts the new glow-off default");
        assertEquals(0.75, d.zoneEnterTitleGlowIntensity, "absent intensity key adopts the gentle 0.75 default");
        assertTrue(d.zoneEnterTitleEnabled);
    }

    @Test
    void roundTripWritesCleanNamesAndPreservesValues() {
        LatitudeConfigData out = LatitudeConfigData.fresh();
        out.zoneEnterTitleSeconds = 9.0;
        out.zoneEnterTitleColorPreset = TitleColorPreset.RAINBOW;
        out.zoneEnterTitleCase = TitleCaseMode.MOCKING;
        out.zoneEnterTitleOffsetX = -12;
        out.zoneEnterTitleOutline = false;
        out.zoneEnterTitleOutlineRgb = 0x123456;
        out.zoneEnterTitleDropShadow = true;
        out.zoneEnterTitleGlow = true;
        out.hudSnapPixels = 32;
        out.captureWriteCsv = true;

        String json = GSON.toJson(out);
        assertFalse(json.contains("Value\""), "new files must use clean key names, not legacy ...Value");
        assertTrue(json.contains("\"configVersion\""));

        LatitudeConfigData in = GSON.fromJson(json, LatitudeConfigData.class);
        in.sanitize();
        assertEquals(LatitudeConfigData.CURRENT_CONFIG_VERSION, in.configVersion);
        assertEquals(9.0, in.zoneEnterTitleSeconds);
        assertEquals(TitleColorPreset.RAINBOW, in.zoneEnterTitleColorPreset);
        assertEquals(TitleCaseMode.MOCKING, in.zoneEnterTitleCase);
        assertEquals(-12, in.zoneEnterTitleOffsetX);
        assertFalse(in.zoneEnterTitleOutline);
        assertEquals(0x123456, in.zoneEnterTitleOutlineRgb);
        assertTrue(in.zoneEnterTitleDropShadow);
        assertTrue(in.zoneEnterTitleGlow);
        assertEquals(32, in.hudSnapPixels);
        assertTrue(in.captureWriteCsv);
    }

    @Test
    void sanitizeClampsHostileValuesAndNullEnums() {
        LatitudeConfigData d = GSON.fromJson("""
                {
                  "zoneEnterTitleSeconds": 999.0,
                  "zoneEnterTitleScale": 0.01,
                  "zoneEnterTitleLetterSpacing": -100,
                  "zoneEnterTitleOutlineThickness": 99,
                  "zoneEnterTitleGlowIntensity": 50.0,
                  "hudSnapPixels": 100000,
                  "zoneEnterTitleColorPreset": "NOT_A_REAL_PRESET",
                  "zoneEnterTitleCase": null,
                  "configVersion": 999
                }
                """, LatitudeConfigData.class);
        d.sanitize();
        assertEquals(10.0, d.zoneEnterTitleSeconds);
        assertEquals(1.0, d.zoneEnterTitleScale);
        assertEquals(-4, d.zoneEnterTitleLetterSpacing);
        assertEquals(4, d.zoneEnterTitleOutlineThickness, "outline thickness clamps to MAX (4)");
        assertEquals(2.0, d.zoneEnterTitleGlowIntensity, "glow intensity clamps to 2.0");
        assertEquals(64, d.hudSnapPixels);
        assertEquals(TitleColorPreset.WHITE, d.zoneEnterTitleColorPreset, "unknown enum constant -> default");
        assertEquals(TitleCaseMode.NORMAL, d.zoneEnterTitleCase);
        assertEquals(LatitudeConfigData.CURRENT_CONFIG_VERSION, d.configVersion, "future version clamped");
    }

    /** The appended Accessibility setting: defaults to Standard, round-trips by name under the clean key, keeps
     *  its default when the key is absent, and falls back to Standard for a null/unknown enum constant. */
    @Test
    void accessibilityModeDefaultsRoundTripsAndSanitizes() {
        assertEquals(AccessibilityMode.STANDARD, LatitudeConfigData.fresh().accessibilityMode);

        LatitudeConfigData out = LatitudeConfigData.fresh();
        out.accessibilityMode = AccessibilityMode.HIGH_CONTRAST;
        String json = GSON.toJson(out);
        assertTrue(json.contains("HIGH_CONTRAST"), "persists by NAME (append-safe), not ordinal");
        LatitudeConfigData in = GSON.fromJson(json, LatitudeConfigData.class);
        in.sanitize();
        assertEquals(AccessibilityMode.HIGH_CONTRAST, in.accessibilityMode);

        LatitudeConfigData absent = GSON.fromJson("{\"hudSnapPixels\": 8}", LatitudeConfigData.class);
        absent.sanitize();
        assertEquals(AccessibilityMode.STANDARD, absent.accessibilityMode, "absent key keeps the default");

        LatitudeConfigData bad = GSON.fromJson("{\"accessibilityMode\": \"NOPE\"}", LatitudeConfigData.class);
        bad.sanitize();
        assertEquals(AccessibilityMode.STANDARD, bad.accessibilityMode, "unknown enum constant -> default");
    }
}
