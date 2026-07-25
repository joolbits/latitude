package com.example.globe.core.ui;

/**
 * Pure math for HUD Studio's narrow-screen adaptive layout (GUI-scale parity audit finding H2).
 *
 * <p>The Studio's settings sidebar was a hard-coded 208 gui-px with no fallback: at Minecraft's 320x240
 * effective-resolution floor (GUI Scale 4 on a small monitor) the {@code sidebarWidth + 4 = 212}px card ate
 * 66% of the width and the screen-centered Done|Cancel row underlapped it. This class derives, from the
 * current screen width, a shrunk sidebar and a Done|Cancel group re-anchored clear of that sidebar -- but
 * only below a threshold, so wider screens keep the established layout <b>pixel-identical</b>.
 *
 * <p>Extracted out of {@code LatitudeHudStudioScreen} (an MC {@code Screen} subclass, not unit-testable) so
 * the geometry can be pinned with plain JUnit, mirroring {@link GroupRowLayout}. The screen owns rendering,
 * widget identity, and input; this class only turns {@code (screenW)} into layout numbers.
 *
 * <p><b>Threshold derivation.</b> The card's right edge at the full 208px sidebar is {@code 6 + 208 + 4 =
 * 218}. The screen-centered 200px Done|Cancel group's left edge is {@code (screenW - 200) / 2}; it first
 * clears the card (left edge &ge; 218) at {@code screenW &ge; 2*218 + 200 = 636}. So at and above
 * {@link #NARROW_THRESHOLD} the centered group already clears the sidebar and nothing needs to change; below
 * it, the group is re-anchored to the right of the sidebar instead. Independently, the sidebar itself only
 * begins to shrink once the reserved canvas would otherwise be squeezed (around 358px), so every width that
 * keeps the full 208px sidebar also keeps the tab strip and its label scale byte-identical.
 */
public final class HudStudioLayout {
    private HudStudioLayout() {
    }

    /** The established full sidebar width (unchanged above the threshold). */
    public static final int FULL_SIDEBAR_W = 208;
    /** Never shrink the sidebar below this -- narrower and the sliders/dropdowns stop being usable. */
    public static final int MIN_SIDEBAR_W = 150;
    /** Canvas (gui-px) kept to the RIGHT of the sidebar card for the live preview + the action buttons. The
     *  sidebar shrinks only enough to preserve this once the screen gets too narrow to afford both. */
    public static final int RESERVED_CANVAS_W = 140;
    /** Below this width the centered Done|Cancel group would underlap the full-width sidebar card, so the
     *  adaptive layout activates. At/above it the layout is pixel-identical to the pre-fix screen. */
    public static final int NARROW_THRESHOLD = 636;

    /** Card left edge (matches the {@code sidebarX = 6} used in the screen's draw). */
    public static final int CARD_LEFT = 6;
    /** Card is {@code sidebarWidth + 4} wide, so its right edge is {@code CARD_LEFT + sidebarW + 4}. */
    public static final int CARD_PAD = 4;

    /** The established Done|Cancel group width and inner gap (kept identical above the threshold). */
    public static final int GROUP_FULL_W = 200;
    public static final int GROUP_MIN_W = 110;
    public static final int GROUP_GAP = 4;
    /** Right-edge margin the group keeps from the screen edge, and the gap it keeps from the sidebar card. */
    public static final int EDGE_MARGIN = 8;
    public static final int GROUP_CANVAS_GAP = 6;

    /** True when the adaptive (narrow-screen) layout is active. Above this the layout is unchanged. */
    public static boolean isNarrow(int screenW) {
        return screenW < NARROW_THRESHOLD;
    }

    /** Right edge of the sidebar card, given its (possibly shrunk) width. */
    public static int cardRight(int sidebarW) {
        return CARD_LEFT + sidebarW + CARD_PAD;
    }

    /**
     * Effective sidebar width for the current screen. Returns the full 208 above the threshold; below it,
     * keeps 208 while there is room for the reserved canvas and only then shrinks, clamped to
     * {@link #MIN_SIDEBAR_W}. So every width that keeps the full sidebar keeps the tab strip identical.
     */
    public static int sidebarWidth(int screenW) {
        if (!isNarrow(screenW)) {
            return FULL_SIDEBAR_W;
        }
        // Leave RESERVED_CANVAS_W to the right of the card (card right edge = CARD_LEFT + sidebarW + CARD_PAD).
        int fit = screenW - (CARD_LEFT + CARD_PAD) - RESERVED_CANVAS_W;
        return clamp(fit, MIN_SIDEBAR_W, FULL_SIDEBAR_W);
    }

    /** Geometry for the two bottom action buttons: group origin/width and each button's width. */
    public record ActionButtons(int groupX, int groupW, int doneW, int cancelW, int gap) {
    }

    /**
     * Places the Done|Cancel group. Above the threshold this is the established screen-centered 200px group
     * (pixel-identical). Below it the group is right-aligned in the canvas to the right of the sidebar card,
     * shrinking (down to {@link #GROUP_MIN_W}) to fit, so it never underlaps the sidebar in either the
     * panel-shown or panel-hidden state (the buttons keep their init-time bounds when {@code L} hides the
     * sidebar, so anchoring them clear of the card is correct for both).
     */
    public static ActionButtons actionButtons(int screenW, int sidebarW) {
        if (!isNarrow(screenW)) {
            int groupW = GROUP_FULL_W;
            int groupX = (screenW - groupW) / 2;
            int doneW = (groupW - GROUP_GAP) / 2;
            int cancelW = groupW - doneW - GROUP_GAP;
            return new ActionButtons(groupX, groupW, doneW, cancelW, GROUP_GAP);
        }
        int cardRight = cardRight(sidebarW);
        int avail = screenW - cardRight - GROUP_CANVAS_GAP - EDGE_MARGIN;
        int groupW = clamp(avail, GROUP_MIN_W, GROUP_FULL_W);
        int groupX = screenW - EDGE_MARGIN - groupW;
        // Never let the group slip left of the card, even at extreme (sub-floor) widths.
        int minX = cardRight + GROUP_CANVAS_GAP;
        if (groupX < minX) {
            groupX = minX;
        }
        int doneW = (groupW - GROUP_GAP) / 2;
        int cancelW = groupW - doneW - GROUP_GAP;
        return new ActionButtons(groupX, groupW, doneW, cancelW, GROUP_GAP);
    }

    /**
     * Tab-label draw scale. Returns {@code baseScale} whenever the sidebar is at its full width (i.e. every
     * width above the shrink point, including the whole wide range) so the established look is untouched;
     * only once the sidebar actually shrinks does it reduce the scale so the longest label still fits inside
     * its (now narrower) tab.
     *
     * @param sidebarW    current sidebar width
     * @param gap         inter-tab gap ({@code TAB_GAP})
     * @param tabCount    number of tabs
     * @param maxLabelW   pixel width of the widest tab label at scale 1.0
     * @param baseScale   the established tab-label scale ({@code TAB_LABEL_SCALE})
     */
    public static float tabLabelScale(int sidebarW, int gap, int tabCount, int maxLabelW, float baseScale) {
        if (sidebarW >= FULL_SIDEBAR_W || maxLabelW <= 0 || tabCount <= 0) {
            return baseScale;
        }
        int totalW = sidebarW + CARD_PAD;
        int tabW = (totalW - gap * (tabCount - 1)) / tabCount;
        int inner = tabW - 6; // ~3px padding inside each tab's borders
        if (inner <= 0) {
            return baseScale;
        }
        return Math.min(baseScale, inner / (float) maxLabelW);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
