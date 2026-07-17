package com.example.globe.client.create;

/**
 * Shared half-open rectangle policy for widgets rendered through a viewport scissor.
 *
 * <p>A partially visible widget remains selectable, but only where its own bounds and the
 * viewport's visible bounds overlap. Keeping this policy independent from Minecraft makes the
 * exact input geometry executable in a headless regression test.</p>
 */
final class ViewportClipPolicy {
    private ViewportClipPolicy() {
    }

    static boolean intersects(int top, int bottom, int clipTop, int clipBottom) {
        return bottom > clipTop && top < clipBottom;
    }

    static boolean containsPoint(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    static boolean acceptsClippedWidgetClick(
            double x,
            double y,
            int widgetLeft,
            int widgetTop,
            int widgetRight,
            int widgetBottom,
            int clipLeft,
            int clipTop,
            int clipRight,
            int clipBottom
    ) {
        if (widgetRight <= widgetLeft
                || widgetBottom <= widgetTop
                || clipRight <= clipLeft
                || clipBottom <= clipTop) {
            return false;
        }
        return containsPoint(x, y, widgetLeft, widgetTop, widgetRight, widgetBottom)
                && containsPoint(x, y, clipLeft, clipTop, clipRight, clipBottom);
    }
}
