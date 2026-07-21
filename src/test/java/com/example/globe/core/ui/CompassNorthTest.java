package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S33 north-glyph law (Peetsa 2026-07-21: "the top point of the compass rose should just be shorter, pointing
 * to a red N outlined in a shade-darker color. Apply the same rule to the in-game compass"). Both hand-drawn
 * roses -- the live HUD dial and the loading-screen card -- consume these, so pinning them here is what keeps
 * the two from drifting apart again.
 */
class CompassNorthTest {

    @Test
    void northArmStopsShortOfTheGlyphWithAGap() {
        // Glyph bottom 12 px above centre, gap 2 -> the arm ends at 10, pointing at the letter.
        assertEquals(12 - CompassNorth.ARM_GLYPH_GAP, CompassNorth.northArmLength(20, 12));
        // The shortened arm is genuinely shorter than its three siblings (the whole point of the change).
        assertTrue(CompassNorth.northArmLength(20, 12) < 20);
    }

    @Test
    void northArmNeverExceedsTheOtherArmsOrVanishes() {
        // A glyph drawn far out (small tick/large radius) must not make north LONGER than the other arms.
        assertEquals(20, CompassNorth.northArmLength(20, 40));
        // A tiny dial (glyph nearly at centre) still keeps a visible point rather than collapsing to nothing.
        assertEquals(2, CompassNorth.northArmLength(6, 0));
        assertEquals(2, CompassNorth.northArmLength(6, -5));
    }

    @Test
    void outlineIsTheSameRedOnlyDarker() {
        int outline = CompassNorth.outlineColor();
        assertEquals(0xFF, (outline >>> 24) & 0xFF, "alpha preserved -- the outline is opaque like the glyph");
        for (int shift : new int[]{16, 8, 0}) {
            int glyphChannel = (CompassNorth.RED >> shift) & 0xFF;
            int outlineChannel = (outline >> shift) & 0xFF;
            assertTrue(outlineChannel <= glyphChannel, "every channel darkens, none brightens");
        }
        assertTrue(((outline >> 16) & 0xFF) < ((CompassNorth.RED >> 16) & 0xFF),
                "the dominant red channel must visibly darken (a real outline, not a no-op)");
    }

    @Test
    void outlineOnlyAtScalesWhereTheGlyphCanCarryIt() {
        // TEST 119's complaint was outlines blobbing on small glyphs; the rule keeps them off down there.
        assertTrue(CompassNorth.shouldOutline(1.0f));
        assertTrue(CompassNorth.shouldOutline(CompassNorth.OUTLINE_MIN_SCALE));
        assertFalse(CompassNorth.shouldOutline(CompassNorth.OUTLINE_MIN_SCALE - 0.01f));
        assertFalse(CompassNorth.shouldOutline(0.4f), "the smallest dial falls back to a drop shadow");
    }

    @Test
    void darkenIsClampedAndAlphaPreserving() {
        assertEquals(0xFF000000, CompassNorth.darken(0xFFFFFFFF, 1.0f));
        assertEquals(0xFFFFFFFF, CompassNorth.darken(0xFFFFFFFF, 0.0f));
        assertEquals(0xFF000000, CompassNorth.darken(0xFFFFFFFF, 5.0f), "over-range clamps to full dark");
        assertEquals(0xFFFFFFFF, CompassNorth.darken(0xFFFFFFFF, -1.0f), "under-range clamps to unchanged");
        assertEquals(0x80, (CompassNorth.darken(0x80FF0000, 0.5f) >>> 24) & 0xFF, "alpha survives darkening");
    }
}
