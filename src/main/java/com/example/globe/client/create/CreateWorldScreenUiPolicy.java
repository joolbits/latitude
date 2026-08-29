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
}
