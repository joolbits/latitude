package com.example.globe.core.ui;

import com.example.globe.core.config.LatitudeConfigData.AccessibilityMode;
import com.example.globe.core.ui.AccessibilityPalette.SignalRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math probes for {@link AccessibilityPalette}. No Minecraft. Verifies STANDARD is a strict identity,
 * HIGH_CONTRAST floors alpha and lifts dim luminance (idempotently), and COLORBLIND_FRIENDLY remaps only the
 * red/green-reliant signals while leaving thematic colors alone.
 */
class AccessibilityPaletteTest {

    private static final AccessibilityMode STD = AccessibilityMode.STANDARD;
    private static final AccessibilityMode HC = AccessibilityMode.HIGH_CONTRAST;
    private static final AccessibilityMode CB = AccessibilityMode.COLORBLIND_FRIENDLY;

    private static int lum(int argb) {
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return Math.round(0.299f * r + 0.587f * g + 0.114f * b);
    }

    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }

    // ── STANDARD is a strict identity everywhere ──

    @Test
    void standardIsIdentity() {
        int muted = 0xC08C8078;
        assertEquals(120, AccessibilityPalette.textAlpha(STD, 120));
        assertEquals(80, AccessibilityPalette.backgroundAlpha(STD, 80));
        assertEquals(0, AccessibilityPalette.outlineStrength(STD));
        assertEquals(muted, AccessibilityPalette.adjustPanelText(STD, muted));
        assertEquals(muted, AccessibilityPalette.adjustMuted(STD, muted));
        assertEquals(0xFFCC3333, AccessibilityPalette.adjustSignalColor(STD, 0xFFCC3333, SignalRole.NEEDLE_NORTH));
        assertEquals(0xFF223344, AccessibilityPalette.clampDecorativeBrightness(STD, 0xFF223344, 200));
    }

    // ── Alpha floors ──

    @Test
    void highContrastFloorsTextAlphaToOpaque() {
        assertEquals(255, AccessibilityPalette.textAlpha(HC, 20));
        assertEquals(255, AccessibilityPalette.textAlpha(HC, 255));
        // colorblind leaves the slider alone
        assertEquals(20, AccessibilityPalette.textAlpha(CB, 20));
    }

    @Test
    void highContrastFloorsBackgroundAlphaButNeverLowersIt() {
        int floored = AccessibilityPalette.backgroundAlpha(HC, 10);
        assertTrue(floored >= 232, "background must be near-opaque under high contrast");
        // an already-solid background is not reduced
        assertEquals(255, AccessibilityPalette.backgroundAlpha(HC, 255));
        assertEquals(10, AccessibilityPalette.backgroundAlpha(CB, 10));
    }

    @Test
    void outlineStrengthOnlyUnderHighContrast() {
        assertTrue(AccessibilityPalette.outlineStrength(HC) > 0);
        assertEquals(0, AccessibilityPalette.outlineStrength(CB));
        assertEquals(0, AccessibilityPalette.outlineStrength(STD));
    }

    // ── Luminance lifts ──

    @Test
    void adjustPanelTextLiftsDimGreyAndForcesOpaque() {
        int dim = 0x60605850; // low alpha, dark grey
        int out = AccessibilityPalette.adjustPanelText(HC, dim);
        assertEquals(255, alpha(out), "text forced opaque");
        assertTrue(lum(out) >= 190, "dim text lifted to a legible luminance, was " + lum(dim));
    }

    @Test
    void adjustPanelTextLeavesBrightTextHueIntact() {
        int warmWhite = 0xFFEDE0D0; // already bright
        int out = AccessibilityPalette.adjustPanelText(HC, warmWhite);
        assertEquals(255, alpha(out));
        // bright already above floor => RGB unchanged
        assertEquals(warmWhite & 0xFFFFFF, out & 0xFFFFFF);
    }

    @Test
    void liftIsIdempotent() {
        int dim = 0xFF605850;
        int once = AccessibilityPalette.adjustPanelText(HC, dim);
        int twice = AccessibilityPalette.adjustPanelText(HC, once);
        assertEquals(once, twice, "applying the lift twice must equal once");

        int m1 = AccessibilityPalette.adjustMuted(HC, dim);
        int m2 = AccessibilityPalette.adjustMuted(HC, m1);
        assertEquals(m1, m2);
    }

    @Test
    void adjustMutedIsGentlerThanBodyTextButStillLifts() {
        int muted = 0xFF8C8078; // lum ~ 132
        int out = AccessibilityPalette.adjustMuted(HC, muted);
        assertTrue(lum(out) >= 170, "muted lifted to its floor");
        assertEquals(muted, AccessibilityPalette.adjustMuted(CB, muted), "muted untouched outside HC");
    }

    @Test
    void clampDecorativeKeepsBrightColorsButRaisesDarkOnes() {
        int darkHue = 0xFF102040;
        assertTrue(lum(AccessibilityPalette.clampDecorativeBrightness(HC, darkHue, 200)) >= 200);
        int bright = 0xFFFFF0C0;
        assertEquals(bright, AccessibilityPalette.clampDecorativeBrightness(HC, bright, 200));
    }

    // ── Signal remaps ──

    @Test
    void colorblindRemapsRedNorthNeedleAwayFromRed() {
        int red = 0xFFCC3333;
        int out = AccessibilityPalette.adjustSignalColor(CB, red, SignalRole.NEEDLE_NORTH);
        assertNotEquals(red, out, "red needle must be remapped");
        // remapped color must NOT be red-dominant
        int r = (out >>> 16) & 0xFF, g = (out >>> 8) & 0xFF, b = out & 0xFF;
        assertFalse(r > g && r > b, "remapped needle must not be red-dominant");
    }

    @Test
    void colorblindLeavesNonRedNeedleAlone() {
        int cyan = 0xFF4FC3FF; // arctic-blue theme needle, already CVD-safe
        assertEquals(cyan, AccessibilityPalette.adjustSignalColor(CB, cyan, SignalRole.NEEDLE_NORTH));
    }

    @Test
    void colorblindRemapsGreenPositiveToGold() {
        int green = 0xFF33CC33;
        int out = AccessibilityPalette.adjustSignalColor(CB, green, SignalRole.POSITIVE);
        assertNotEquals(green, out);
        // gold-ish: red & green high, blue low
        int r = (out >>> 16) & 0xFF, g = (out >>> 8) & 0xFF, b = out & 0xFF;
        assertTrue(r > 180 && g > 120 && b < g, "positive remapped to a warm gold");
    }

    @Test
    void colorblindRedWarningAndNegativeRemap() {
        int red = 0xFFE2402E;
        assertNotEquals(red, AccessibilityPalette.adjustSignalColor(CB, red, SignalRole.WARNING));
        assertNotEquals(red, AccessibilityPalette.adjustSignalColor(CB, red, SignalRole.NEGATIVE));
    }

    @Test
    void highContrastSignalIsOpaqueAndBright() {
        int dimRed = 0x40801010;
        int out = AccessibilityPalette.adjustSignalColor(HC, dimRed, SignalRole.NEEDLE_NORTH);
        assertEquals(255, alpha(out));
        assertTrue(lum(out) >= 200, "signal pushed bright under high contrast");
    }

    @Test
    void redRelianceDetection() {
        assertTrue(AccessibilityPalette.isRedReliant(0xFFCC3333));
        assertTrue(AccessibilityPalette.isRedReliant(0xFFE2402E));
        assertFalse(AccessibilityPalette.isRedReliant(0xFF4FC3FF)); // cyan
        assertFalse(AccessibilityPalette.isRedReliant(0xFF7BE0A0)); // emerald
        assertFalse(AccessibilityPalette.isRedReliant(0xFFE5C07B)); // gold — red not dominant enough
    }
}
