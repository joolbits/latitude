package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the in-world overlay fit/clamp/wrap helpers (GUI-scale parity audit 2026-07-10,
 * Lane A: findings H1 / M1 / M2). Pins the "title drifts off-screen after a GUI-scale change" and
 * "long title/warning overflows a narrow screen" regressions forever.
 */
class OverlayLayoutTest {

    private static final double TOL = 1e-9;

    // A fixed-pitch stand-in for a font: every character is 6px wide. Deterministic, no MC dependency.
    private static final ToIntFunction<String> SIX_PX = s -> s.length() * 6;

    // ------------------------------------------------------------------ fitScale (M1)

    @Test
    void fitScaleLeavesFittingTextUntouched() {
        // 20px content at scale 2 = 40px, well under 100px available -> desired scale returned as-is.
        assertEquals(2.0, OverlayLayout.fitScale(2.0, 20, 100, 0.5), TOL);
    }

    @Test
    void fitScaleShrinksOverWideTextToFit() {
        // 100px content at scale 3 = 300px, but only 150px available -> shrink to exactly 150/100 = 1.5.
        assertEquals(1.5, OverlayLayout.fitScale(3.0, 100, 150, 0.5), TOL);
    }

    @Test
    void fitScaleNeverGoesBelowTheFloor() {
        // 1000px content into 100px would need 0.1, but the floor is 0.5 -> clamps to 0.5 (clampCenter
        // then keeps the still-too-wide box centered).
        assertEquals(0.5, OverlayLayout.fitScale(3.0, 1000, 100, 0.5), TOL);
    }

    @Test
    void fitScaleGuardsDegenerateInputs() {
        assertEquals(1.8, OverlayLayout.fitScale(1.8, 0, 100, 0.5), TOL, "zero content width -> unchanged");
        assertEquals(1.8, OverlayLayout.fitScale(1.8, 50, 0, 0.5), TOL, "zero available width -> unchanged");
    }

    // ------------------------------------------------------------------ clampCenter (H1)

    @Test
    void clampCenterKeepsAnInBoundsCenterPut() {
        // half=20 box on a 480 screen: a center at 240 fits [220,260] -> unchanged.
        assertEquals(240, OverlayLayout.clampCenter(240, 20, 480));
    }

    @Test
    void clampCenterPullsAnOffScreenCenterBackOn() {
        // The H1 failure: a title dragged far right at a large GUI canvas leaves a huge offset; a later
        // small canvas (480 wide) would place the center at 940 -> must clamp so the box stays fully on.
        int half = 60;
        int extent = 480;
        assertEquals(extent - half, OverlayLayout.clampCenter(940, half, extent), "right overflow pulled in");
        assertEquals(half, OverlayLayout.clampCenter(-300, half, extent), "left overflow pulled in");
    }

    @Test
    void clampCenterCentersABoxWiderThanTheScreen() {
        // Box half-extent 200 on a 320 screen can't fit (400 > 320) -> center it so the middle is visible
        // and both edges overflow equally, rather than pinning one edge on-screen and the other far off.
        assertEquals(160, OverlayLayout.clampCenter(50, 200, 320));
        assertEquals(160, OverlayLayout.clampCenter(999, 200, 320));
    }

    // ------------------------------------------------------------------ wrap (M2)

    @Test
    void wrapReturnsSingleLineWhenItFits() {
        List<String> lines = OverlayLayout.wrap("Turn back now.", 1000, SIX_PX);
        assertEquals(List.of("Turn back now."), lines, "a short warning stays one line (render path unchanged)");
    }

    @Test
    void wrapBreaksALongWarningIntoWidthBoundedLines() {
        String warning = "Snow begins to fall. The cold is setting in -- consider turning back.";
        int maxWidth = 180; // ~30 chars at 6px
        List<String> lines = OverlayLayout.wrap(warning, maxWidth, SIX_PX);

        assertTrue(lines.size() >= 2, "a long warning must wrap onto more than one line");
        for (String line : lines) {
            assertTrue(SIX_PX.applyAsInt(line) <= maxWidth,
                    "every wrapped line must fit within maxWidth: \"" + line + "\"");
        }
        // Lossless: the wrapped lines rejoin to the original (single spaces between words).
        assertEquals(warning, String.join(" ", lines), "wrapping must not drop or duplicate any word");
    }

    @Test
    void wrapNeverSplitsAWordMidWord() {
        // A single word wider than maxWidth gets its own over-wide line rather than being broken.
        List<String> lines = OverlayLayout.wrap("antidisestablishmentarianism", 30, SIX_PX);
        assertEquals(1, lines.size());
        assertEquals("antidisestablishmentarianism", lines.get(0));
    }

    @Test
    void wrapHandlesEmptyAndNull() {
        assertEquals(List.of(""), OverlayLayout.wrap("", 100, SIX_PX));
        assertEquals(List.of(""), OverlayLayout.wrap(null, 100, SIX_PX));
        assertFalse(OverlayLayout.wrap("x", 100, SIX_PX).isEmpty());
    }
}
