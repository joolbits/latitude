package com.example.globe.core.ui;

/**
 * Pure math for the HUD Studio sidebar's grouped-row cumulative layout (UI round 13 sweeper follow-up).
 *
 * <p>Widgets that share a base Y (an "undo + redo" pair, a preset slot's Load/Save/x triple, an RGB picker's
 * three sliders) are one logical row: the scroll cursor must advance ONCE per group, by the group's slot
 * height scaled by the group's reveal (the max reveal of its members) -- not once per widget. Advancing per
 * widget was exactly the pre-885b3da4 regression: every same-line widget consumed its own full row and the
 * panel exploded into a staircase. At rest (every reveal == 1.0), &Sigma; slot heights == baseY[i] for every
 * i, so {@code dispY[i] == baseY[i]} and the layout is byte-identical to the old fixed-baseY one.
 *
 * <p>Extracted out of {@code LatitudeHudStudioScreen} (an MC {@code Screen} subclass, not unit-testable) so
 * this walk can be pinned with a plain JUnit test independent of any GUI harness. The screen still owns
 * everything about widget identity, animation easing, and rendering; this class only turns
 * (baseY[], reveal[], widgetHeight[]) into (dispY[], slotH[]).
 */
public final class GroupRowLayout {
    private GroupRowLayout() {
    }

    /** Result of one layout pass: per-index displayed Y (cumulative, pre-scroll-offset) and slot height,
     *  plus the total content height measured from {@code viewportTop} to the bottom of the last slot. */
    public static final class Result {
        public final float[] dispY;
        public final float[] slotH;
        public final float contentBottom;

        Result(float[] dispY, float[] slotH, float contentBottom) {
            this.dispY = dispY;
            this.slotH = slotH;
            this.contentBottom = contentBottom;
        }
    }

    /**
     * Walks {@code baseY} in order, grouping consecutive entries that share the same base Y into one logical
     * row, and returns each entry's cumulative displayed Y and its group's slot height.
     *
     * @param viewportTop    starting cursor (top of the scroll viewport)
     * @param baseY          each tracked widget's fixed rest-position Y (ascending, same-Y runs are groups)
     * @param reveal         each tracked widget's current reveal in [0,1]; a group's reveal is the max of its
     *                       members
     * @param widgetHeights  each tracked widget's rendered height, used only as the fallback slot height for
     *                       the final group (which has no "next distinct baseY" to measure against); pass 0
     *                       for any null/absent widget
     */
    public static Result compute(float viewportTop, int[] baseY, float[] reveal, int[] widgetHeights) {
        int n = baseY.length;
        float[] dispY = new float[n];
        float[] slotH = new float[n];
        float cursor = viewportTop;
        int i = 0;
        while (i < n) {
            int base = baseY[i];
            int j = i;
            float groupReveal = 0f;
            while (j < n && baseY[j] == base) {
                groupReveal = Math.max(groupReveal, reveal[j]);
                j++;
            }
            int slot = groupSlotHeight(baseY, widgetHeights, i, j, base);
            for (int k = i; k < j; k++) {
                dispY[k] = cursor;
                slotH[k] = slot;
            }
            cursor += slot * groupReveal;
            i = j;
        }
        return new Result(dispY, slotH, cursor);
    }

    /** Slot height a same-baseY group [i0,i1) reserves = the distance to the next DISTINCT base Y (exactly
     *  the vertical advance the old fixed layout used after that row). Falls back to the tallest widget in
     *  the group + a 4px row gap for the final group, which has no "next" baseY to measure against. */
    private static int groupSlotHeight(int[] baseY, int[] widgetHeights, int i0, int i1, int base) {
        if (i1 < baseY.length) {
            int d = baseY[i1] - base;
            if (d > 0) return d;
        }
        int h = 20;
        for (int k = i0; k < i1 && k < widgetHeights.length; k++) {
            h = Math.max(h, widgetHeights[k]);
        }
        return h + 4; // + rowGap
    }
}
