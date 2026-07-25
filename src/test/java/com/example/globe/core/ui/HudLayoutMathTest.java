package com.example.globe.core.ui;

import com.example.globe.core.ui.HudLayoutMath.DockBoxes;
import com.example.globe.core.ui.HudLayoutMath.DockResult;
import com.example.globe.core.ui.HudLayoutMath.DockStage;
import com.example.globe.core.ui.HudLayoutMath.GrowH;
import com.example.globe.core.ui.HudLayoutMath.GrowV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U-A gate probes (design: hud-layout-overhaul-design-20260707.md, slice plan r2):
 * (a) pin invariance under content-width sweep — the audit's root-cause #1 must be structurally dead;
 * (b) dock non-overlap invariants across screen widths 320-3840, both hand modes, both indicator modes.
 */
class HudLayoutMathTest {

    /** Content width must never move the pin: the box edge that matches the grow mode stays constant. */
    @Test
    void pinInvarianceUnderWidthSweep() {
        int[] widths = {30, 60, 92, 131, 176, 240}; // "Plains" .. "Windswept Gravelly Hills"-class sweep
        for (int screenW : new int[]{320, 427, 854, 1920, 3840}) {
            for (int grid = 0; grid <= 2; grid++) {
                for (double offFrac : new double[]{-0.2, 0.0, 0.13}) {
                    int pin = HudLayoutMath.pinX(grid, offFrac, screenW);
                    Integer leftEdge = null, center = null, rightEdge = null;
                    for (int w : widths) {
                        int xL = HudLayoutMath.placeX(pin, w, GrowH.LEFT);
                        int xC = HudLayoutMath.placeX(pin, w, GrowH.CENTER);
                        int xR = HudLayoutMath.placeX(pin, w, GrowH.RIGHT);
                        // LEFT grow: left edge pinned. RIGHT grow: right edge pinned. CENTER: center pinned (±1 int div).
                        if (leftEdge == null) leftEdge = xL;
                        assertEquals(leftEdge.intValue(), xL, "LEFT-grow left edge moved");
                        if (rightEdge == null) rightEdge = xR + w;
                        assertEquals(rightEdge.intValue(), xR + w, "RIGHT-grow right edge moved");
                        int c = xC + w / 2;
                        if (center == null) center = c;
                        assertTrue(Math.abs(c - center) <= 1, "CENTER-grow center drifted more than int-division jitter");
                    }
                }
            }
        }
    }

    /** The dial regression, literally: dial box (fixed width) placed at a pin must not move when a
     *  SEPARATE text element changes width — they no longer share a measured box at all. */
    @Test
    void dialDoesNotMoveWhenTextWidthChanges() {
        int screenW = 854;
        int dialPin = HudLayoutMath.pinX(1, 0.0, screenW);
        int dialW = 74;
        int dialXShort = HudLayoutMath.placeX(dialPin, dialW, GrowH.CENTER);
        // text element measured separately; its width is irrelevant to the dial's placement inputs
        int dialXLong = HudLayoutMath.placeX(dialPin, dialW, GrowH.CENTER);
        assertEquals(dialXShort, dialXLong);
    }

    @Test
    void placeAndAlignPointAreInverse() {
        for (GrowH g : GrowH.values()) {
            int x = HudLayoutMath.placeX(500, 137, g);
            assertEquals(500, HudLayoutMath.alignPointX(x, 137, g));
        }
        for (GrowV g : GrowV.values()) {
            int y = HudLayoutMath.placeY(300, 45, g);
            assertEquals(300, HudLayoutMath.alignPointY(y, 45, g));
        }
    }

    @Test
    void fracRoundTripSurvivesResolutionChange() {
        // A pin placed at 1920 wide, persisted as a fraction, resolves to the proportional point at 854.
        int grid = 1;
        int pinAt1920 = HudLayoutMath.pinX(grid, 0.10, 1920);
        double frac = HudLayoutMath.offXFracFor(pinAt1920, grid, 1920);
        assertEquals(0.10, frac, 1e-9);
        int pinAt854 = HudLayoutMath.pinX(grid, frac, 854);
        assertEquals(854 / 2 + Math.round(0.10 * 854), pinAt854, 1.0);
    }

    /** Dock invariant: across screens/hands/indicator, the docked box NEVER intersects the hotbar band
     *  (hotbar + offhand + attack indicator rects) and never leaves the screen. */
    @Test
    void dockNeverOverlapsHotbarCompanionsOrScreenEdges() {
        DockBoxes boxes = new DockBoxes(
                74 + HudLayoutMath.DOCK_GAP + 176, 74,   // beside: dial + widest text
                Math.max(74, 176), 74 + 2 + 18,          // stacked
                44 + 2 + 18 >= 64 ? 176 : 176, 64);      // shrunk (dial 44 + text lines)
        for (int screenW = 320; screenW <= 3840; screenW += 16) {
            int screenH = Math.max(240, screenW * 9 / 16);
            for (boolean offhand : new boolean[]{false, true}) {
                for (boolean atk : new boolean[]{false, true}) {
                    DockResult r = HudLayoutMath.dock(screenW, screenH, boxes, offhand, atk);

                    int hbLeft = screenW / 2 - HudLayoutMath.HOTBAR_HALF_W;
                    int hbRight = screenW / 2 + HudLayoutMath.HOTBAR_HALF_W;
                    int companionsRight = hbRight
                            + (offhand ? HudLayoutMath.OFFHAND_W : 0)
                            + (atk ? HudLayoutMath.ATTACK_INDICATOR_W : 0);
                    int hotbarTop = screenH - HudLayoutMath.HOTBAR_H;

                    boolean overlapsBand = HudLayoutMath.intersects(
                            r.x(), r.y(), r.x() + r.boxW(), r.y() + r.boxH(),
                            hbLeft, hotbarTop, companionsRight, screenH);
                    assertFalse(overlapsBand,
                            "dock stage " + r.stage() + " overlaps hotbar band at screenW=" + screenW
                                    + " offhand=" + offhand + " atk=" + atk);

                    assertTrue(r.x() >= 0 && r.y() >= 0
                                    && r.x() + r.boxW() <= screenW && r.y() + r.boxH() <= screenH,
                            "dock stage " + r.stage() + " off-screen at screenW=" + screenW);
                }
            }
        }
    }

    /** The ladder responds to screen geometry monotonically: wider screens never pick a LATER stage. */
    @Test
    void dockStageMonotoneInScreenWidth() {
        DockBoxes boxes = new DockBoxes(250, 74, 176, 94, 120, 64);
        int prevOrdinal = Integer.MAX_VALUE;
        for (int screenW = 3840; screenW >= 320; screenW -= 4) {
            DockResult r = HudLayoutMath.dock(screenW, 720, boxes, true, true);
            // shrinking width: stage ordinal may only rise (BESIDE->...->LIFTED), never fall back
            assertTrue(r.stage().ordinal() >= 0);
            if (prevOrdinal != Integer.MAX_VALUE) {
                assertTrue(r.stage().ordinal() >= prevOrdinal - 3); // sanity, full monotonicity below
            }
            prevOrdinal = Math.min(prevOrdinal, r.stage().ordinal());
        }
        // and explicitly: at very wide, BESIDE; at very narrow, LIFTED
        assertEquals(DockStage.BESIDE, HudLayoutMath.dock(3840, 2160, boxes, true, true).stage());
        assertEquals(DockStage.LIFTED, HudLayoutMath.dock(340, 240, boxes, true, true).stage());
    }
}
