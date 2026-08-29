package com.example.globe.client.create;

/** Minecraft-free geometry and navigation rules for the create-world screen. */
final class CreateWorldScreenUiPolicy {
    private CreateWorldScreenUiPolicy() {}

    static int cyclePanel(int activePanel, int panelCount, boolean reverse) {
        if (panelCount <= 0) {
            throw new IllegalArgumentException("panelCount must be positive");
        }
        return Math.floorMod(activePanel + (reverse ? -1 : 1), panelCount);
    }

    static boolean isInsideClip(double mouseX, double mouseY,
                                int widgetX, int widgetY, int widgetWidth, int widgetHeight,
                                int clipLeft, int clipTop, int clipRight, int clipBottom) {
        return mouseX >= Math.max(widgetX, clipLeft)
                && mouseX < Math.min(widgetX + widgetWidth, clipRight)
                && mouseY >= Math.max(widgetY, clipTop)
                && mouseY < Math.min(widgetY + widgetHeight, clipBottom);
    }
}
