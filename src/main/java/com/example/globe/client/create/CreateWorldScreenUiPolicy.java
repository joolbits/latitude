package com.example.globe.client.create;

/**
 * Minecraft-free navigation rules for the create-world screen.
 *
 * <p>Upstream's version of this class also carries an {@code isInsideClip} rectangle test. This
 * line does not adopt it: {@link ViewportClipPolicy#acceptsClippedWidgetClick} already answers
 * that exact question here, rejects degenerate rectangles that {@code isInsideClip} would accept,
 * and is pinned by its own regression suite. Two policies deciding one thing is how they drift.</p>
 */
final class CreateWorldScreenUiPolicy {
    static final int EDGE_MARGIN = 4;
    static final int HEADER_GAP = 2;
    static final int PANEL_BOTTOM_MARGIN = 24;
    static final int BUTTON_ROW_TOP_FROM_BOTTOM = 20;
    static final int PANE_GAP = 2;
    static final int TAB_GAP = 1;
    static final int BESPOKE_BACKGROUND_OPACITY_PERCENT = 80;

    private CreateWorldScreenUiPolicy() {
    }

    /**
     * Next panel index when cycling, wrapping in both directions. {@code Math.floorMod} rather
     * than {@code %} so a reverse step off panel zero lands on the last panel instead of -1.
     */
    static int cyclePanel(int activePanel, int panelCount, boolean reverse) {
        if (panelCount <= 0) {
            throw new IllegalArgumentException("panelCount must be positive");
        }
        return Math.floorMod(activePanel + (reverse ? -1 : 1), panelCount);
    }

    static int bespokeBackground(int rgb) {
        int alpha = Math.round(255 * BESPOKE_BACKGROUND_OPACITY_PERCENT / 100.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    static boolean accessibilityControlsNeedOwnRow(int buttonRowStartX, int controlsWidth) {
        return EDGE_MARGIN + controlsWidth + PANE_GAP > buttonRowStartX;
    }

    static boolean shouldRetainButtonFocus(boolean lastInputWasMouse, boolean hovered) {
        return !lastInputWasMouse || hovered;
    }
}
