package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for HUD Studio's narrow-screen adaptive layout (GUI-scale parity audit finding H2).
 *
 * <p>Pins two things forever: (1) at and above {@link HudStudioLayout#NARROW_THRESHOLD} the layout is
 * <b>pixel-identical</b> to the pre-fix screen (full 208px sidebar, screen-centered 200px Done|Cancel,
 * unchanged tab-label scale); (2) at the 320x240 floor the sidebar shrinks and the Done|Cancel group is
 * re-anchored so it never underlaps the sidebar card.
 */
class HudStudioLayoutTest {

    private static final float TOL = 1e-4f;

    // ---- Above the threshold: everything must be byte-identical to the old hard-coded layout. ----

    @Test
    void wideKeepsFullSidebar() {
        for (int w : new int[]{HudStudioLayout.NARROW_THRESHOLD, 640, 960, 1920, 3840}) {
            assertEquals(HudStudioLayout.FULL_SIDEBAR_W, HudStudioLayout.sidebarWidth(w),
                    "sidebar must stay 208 at width " + w);
            assertFalse(HudStudioLayout.isNarrow(w), "width " + w + " is not narrow");
        }
    }

    @Test
    void wideDoneCancelIsScreenCenteredAndMatchesOldGeometry() {
        // Old code: bw=200, groupX=(width-200)/2, halfW=(bw-4)/2=98, Done=[groupX,98], Cancel=[.., 200-98-4=98].
        for (int w : new int[]{636, 700, 960, 1280}) {
            int sidebar = HudStudioLayout.sidebarWidth(w);
            HudStudioLayout.ActionButtons ab = HudStudioLayout.actionButtons(w, sidebar);
            assertEquals((w - 200) / 2, ab.groupX(), "groupX centered at width " + w);
            assertEquals(200, ab.groupW(), "group full width at " + w);
            assertEquals(98, ab.doneW(), "Done width unchanged at " + w);
            assertEquals(98, ab.cancelW(), "Cancel width unchanged at " + w);
            assertEquals(4, ab.gap());
            assertEquals(200, ab.doneW() + ab.gap() + ab.cancelW(), "group parts sum to 200");
        }
    }

    @Test
    void fullSidebarKeepsBaseTabLabelScale() {
        // At any width holding the full 208 sidebar, the tab-label scale must be the established value exactly.
        float base = 0.85f;
        int maxLabelW = 40; // a realistic widest-tab-label pixel width
        for (int w : new int[]{360, 480, 512, 636, 960}) {
            int sidebar = HudStudioLayout.sidebarWidth(w);
            if (sidebar != HudStudioLayout.FULL_SIDEBAR_W) continue;
            assertEquals(base, HudStudioLayout.tabLabelScale(sidebar, 4, 5, maxLabelW, base), TOL,
                    "full sidebar keeps base tab-label scale at width " + w);
        }
    }

    // ---- Below the threshold: the 480 headline case and the 320x240 floor. ----

    @Test
    void narrowActivatesBelowThreshold() {
        assertTrue(HudStudioLayout.isNarrow(HudStudioLayout.NARROW_THRESHOLD - 1));
        assertTrue(HudStudioLayout.isNarrow(480));
        assertTrue(HudStudioLayout.isNarrow(320));
    }

    /** The named GUI-Scale-4 @ 1080p case: 480 gui-px. The full sidebar still fits (canvas is roomy), so the
     *  sidebar stays 208, but the Done|Cancel group must be re-anchored clear of the card instead of
     *  centered-and-overlapping (old: centered 140..340 over the 6..218 card). */
    @Test
    void case480KeepsSidebarButClearsDoneCancel() {
        int w = 480;
        int sidebar = HudStudioLayout.sidebarWidth(w);
        assertEquals(HudStudioLayout.FULL_SIDEBAR_W, sidebar, "480 still affords the full sidebar");
        HudStudioLayout.ActionButtons ab = HudStudioLayout.actionButtons(w, sidebar);
        int cardRight = HudStudioLayout.cardRight(sidebar);
        assertTrue(ab.groupX() >= cardRight, "Done|Cancel must start at/after the card right edge (" + cardRight
                + "), was " + ab.groupX());
        assertTrue(ab.groupX() + ab.groupW() <= w, "group must stay on-screen");
    }

    /** The 320x240 hard floor: the sidebar shrinks (frees canvas) and Done|Cancel is re-anchored clear of it,
     *  fully on-screen. */
    @Test
    void case320FloorShrinksSidebarAndClearsButtons() {
        int w = 320;
        int sidebar = HudStudioLayout.sidebarWidth(w);
        assertTrue(sidebar < HudStudioLayout.FULL_SIDEBAR_W, "sidebar shrinks at the 320 floor");
        assertTrue(sidebar >= HudStudioLayout.MIN_SIDEBAR_W, "sidebar never below the usable floor");

        int cardRight = HudStudioLayout.cardRight(sidebar);
        assertTrue(w - cardRight >= HudStudioLayout.GROUP_MIN_W,
                "a usable canvas remains right of the sidebar at the floor");

        HudStudioLayout.ActionButtons ab = HudStudioLayout.actionButtons(w, sidebar);
        assertTrue(ab.groupX() >= cardRight, "Done|Cancel clears the sidebar card at the floor");
        assertTrue(ab.groupX() + ab.groupW() <= w - 0, "group stays on-screen at the floor");
        assertTrue(ab.groupW() >= HudStudioLayout.GROUP_MIN_W, "buttons stay at least minimally wide");
        assertEquals(ab.groupW(), ab.doneW() + ab.gap() + ab.cancelW(), "group parts sum to group width");
    }

    /** Sidebar shrink is monotonic and clamped: never above 208, never below the floor, smaller as the
     *  screen narrows through the shrink band. */
    @Test
    void sidebarShrinkIsClampedAndMonotonic() {
        int prev = HudStudioLayout.sidebarWidth(300);
        assertTrue(prev >= HudStudioLayout.MIN_SIDEBAR_W && prev <= HudStudioLayout.FULL_SIDEBAR_W);
        for (int w = 301; w <= 640; w++) {
            int cur = HudStudioLayout.sidebarWidth(w);
            assertTrue(cur >= HudStudioLayout.MIN_SIDEBAR_W, "never below floor at " + w);
            assertTrue(cur <= HudStudioLayout.FULL_SIDEBAR_W, "never above full at " + w);
            assertTrue(cur >= prev, "sidebar is non-decreasing as width grows (" + w + ")");
            prev = cur;
        }
    }

    /** When the sidebar has actually shrunk, the tab-label scale must reduce so the widest label still fits
     *  inside its narrower tab. */
    @Test
    void shrunkSidebarReducesTabLabelScale() {
        float base = 0.85f;
        int maxLabelW = 40;
        int sidebar = HudStudioLayout.sidebarWidth(320);
        float scale = HudStudioLayout.tabLabelScale(sidebar, 4, 5, maxLabelW, base);
        assertTrue(scale <= base, "scale never exceeds base");
        // The widest label at this scale must fit the tab's inner width.
        int totalW = sidebar + HudStudioLayout.CARD_PAD;
        int tabW = (totalW - 4 * 4) / 5;
        assertTrue(maxLabelW * scale <= (tabW - 6) + 0.5f, "widest label fits the shrunk tab");
    }
}
