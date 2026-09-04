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
    static final int BESPOKE_BACKGROUND_ALPHA = Math.round(255 * 0.75f);

    private CreateWorldScreenUiPolicy() {
    }

    static int cyclePanel(int activePanel, int panelCount, boolean reverse) {
        if (panelCount <= 0) {
            throw new IllegalArgumentException("panelCount must be positive");
        }
        return Math.floorMod(activePanel + (reverse ? -1 : 1), panelCount);
    }

    static int bespokeBackground(int rgb) {
        return (BESPOKE_BACKGROUND_ALPHA << 24) | (rgb & 0x00FFFFFF);
    }
}
