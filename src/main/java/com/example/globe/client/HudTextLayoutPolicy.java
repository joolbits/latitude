package com.example.globe.client;

/**
 * Dependency-free numeric policy shared by persisted HUD settings, layout, dragging, and focused tests.
 */
public final class HudTextLayoutPolicy {
    public static final float DEFAULT_LOCATION_TEXT_SCALE = 1.0f;
    public static final float LOCATION_TEXT_SCALE_MIN = 0.50f;
    public static final float LOCATION_TEXT_SCALE_MAX = 1.25f;

    private HudTextLayoutPolicy() {
    }

    public static float sanitizeLocationTextScale(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            value = DEFAULT_LOCATION_TEXT_SCALE;
        }
        value = Math.max(LOCATION_TEXT_SCALE_MIN, Math.min(LOCATION_TEXT_SCALE_MAX, value));
        return Math.round(value * 20.0f) / 20.0f;
    }

    public static int scaledPixels(int unscaledPixels, float scale) {
        return (int) Math.ceil(unscaledPixels * scale);
    }

    public static int combinedTextHeight(
            int unscaledFontHeight,
            float compassScale,
            float locationTextScale,
            boolean hasLocationText) {
        int compassHeight = scaledPixels(unscaledFontHeight, compassScale);
        int locationHeight = hasLocationText
                ? scaledPixels(unscaledFontHeight, locationTextScale)
                : 0;
        return Math.max(compassHeight, locationHeight);
    }

    public static int digitalBoxWidth(
            int unscaledPadding,
            float compassScale,
            int unscaledDirectionWidth,
            int unscaledLatitudeSegmentWidth,
            int unscaledDetailSegmentWidth,
            float locationTextScale) {
        int padding = scaledPixels(unscaledPadding, compassScale);
        return padding * 2
                + scaledPixels(unscaledDirectionWidth, compassScale)
                + scaledPixels(unscaledLatitudeSegmentWidth, locationTextScale)
                + scaledPixels(unscaledDetailSegmentWidth, locationTextScale);
    }

    public static int movePristineDetachedY(
            int detailX,
            int detailY,
            int detailWidth,
            int detailHeight,
            int compassX,
            int compassY,
            int compassWidth,
            int compassHeight,
            int screenHeight,
            int gap) {
        if (!intersects(
                detailX, detailY, detailWidth, detailHeight,
                compassX, compassY, compassWidth, compassHeight)) {
            return detailY;
        }

        int maxY = Math.max(0, screenHeight - detailHeight);
        int belowY = clamp(compassY + compassHeight + gap, 0, maxY);
        if (!intersects(
                detailX, belowY, detailWidth, detailHeight,
                compassX, compassY, compassWidth, compassHeight)) {
            return belowY;
        }

        int aboveY = clamp(compassY - detailHeight - gap, 0, maxY);
        return intersects(
                detailX, aboveY, detailWidth, detailHeight,
                compassX, compassY, compassWidth, compassHeight)
                ? detailY
                : aboveY;
    }

    public static double titleDragCoordinate(
            double rawOffset,
            boolean snapEnabled,
            int gridPixels) {
        if (!snapEnabled || gridPixels <= 1) {
            return rawOffset;
        }
        int rounded = (int) Math.round(rawOffset);
        return Math.round(rounded / (float) gridPixels) * gridPixels;
    }

    private static boolean intersects(
            int ax,
            int ay,
            int aw,
            int ah,
            int bx,
            int by,
            int bw,
            int bh) {
        return ax < bx + bw
                && ax + aw > bx
                && ay < by + bh
                && ay + ah > by;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
