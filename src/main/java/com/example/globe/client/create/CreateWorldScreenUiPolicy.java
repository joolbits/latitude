package com.example.globe.client.create;

/**
 * Minecraft-free navigation rule for the create-world screen's tab strip.
 *
 * <p>Ported from upstream's {@code CreateWorldScreenUiPolicy} narrowed to this one method: its
 * companion {@code isInsideClip} answers the same question {@link ViewportClipPolicy} already
 * answers here (more defensively -- it rejects a degenerate rectangle {@code isInsideClip} would
 * accept), and that policy's own caller, a click-outside-clip fix for a custom {@code RulesIconRow}
 * widget, has no counterpart on this line -- the rules panel here uses plain {@code Button}s.</p>
 */
final class CreateWorldScreenUiPolicy {
    static final int EDGE_MARGIN = 4;
    static final int HEADER_GAP = 2;
    static final int PANEL_BOTTOM_MARGIN = 24;
    static final int BUTTON_ROW_TOP_FROM_BOTTOM = 20;
    static final int PANE_GAP = 2;
    static final int TAB_GAP = 1;
    static final int MIN_BACKGROUND_OPACITY_PERCENT = 75;
    static final int DEFAULT_BACKGROUND_OPACITY_PERCENT = 85;
    static final int MAX_BACKGROUND_OPACITY_PERCENT = 100;

    private CreateWorldScreenUiPolicy() {
    }

    static int cyclePanel(int activePanel, int panelCount, boolean reverse) {
        if (panelCount <= 0) {
            throw new IllegalArgumentException("panelCount must be positive");
        }
        return Math.floorMod(activePanel + (reverse ? -1 : 1), panelCount);
    }

    static int clampBackgroundOpacity(int opacityPercent) {
        return Math.max(MIN_BACKGROUND_OPACITY_PERCENT,
                Math.min(MAX_BACKGROUND_OPACITY_PERCENT, opacityPercent));
    }

    static int bespokeBackground(int rgb, int opacityPercent) {
        int alpha = Math.round(255 * clampBackgroundOpacity(opacityPercent) / 100.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    static boolean accessibilityControlsNeedOwnRow(int buttonRowStartX, int controlsWidth) {
        return EDGE_MARGIN + controlsWidth + PANE_GAP > buttonRowStartX;
    }
}
