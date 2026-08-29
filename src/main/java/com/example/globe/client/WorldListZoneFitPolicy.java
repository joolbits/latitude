package com.example.globe.client;

/**
 * Minecraft-free arithmetic for fitting the last-known-zone suffix onto a Select World row without
 * the zone being the thing that gets cut off (maintainer report, 2026-08-26).
 *
 * <p>Vanilla gives each row's text widget an allotted width and, when the text exceeds it, clips
 * with a trailing ellipsis. Latitude appends the zone at the END of that line, so it was by
 * construction the first thing sacrificed: longer rows rendered "... 10:12 PM) S" with the zone
 * shorn away. The fix reserves the suffix's width up front and clips the HEAD instead, so what gets
 * sacrificed is the level id -- which on that line is near-duplicate of the world name already shown
 * directly above it, and so the least informative text in the row.</p>
 *
 * <p><b>The comparison is deliberately non-strict.</b> Vanilla's own overflow test is strictly
 * greater-than ({@code if_icmple} in {@code StringWidget.visitLines}), so text landing exactly on the
 * allotted width renders untouched. Using {@code >=} here would clip a row vanilla would have left
 * alone -- and, worse, a combined width landing exactly on the limit would be re-clipped by vanilla
 * afterwards and eat the zone anyway, silently, while the code still looked correct.</p>
 */
public final class WorldListZoneFitPolicy {
    private WorldListZoneFitPolicy() {
    }

    /**
     * Whether head plus suffix already fit, leaving the text untouched.
     *
     * <p>A non-positive {@code allottedWidth} means the widget has no width cap, so nothing can
     * overflow and nothing should be clipped.</p>
     */
    public static boolean fitsWithoutClipping(int headWidth, int suffixWidth, int allottedWidth) {
        if (allottedWidth <= 0) {
            return true;
        }
        return headWidth + suffixWidth <= allottedWidth;
    }

    /**
     * Width the head text may occupy once the suffix and its ellipsis are reserved.
     *
     * <p>Clamped at zero: a row too narrow to hold even the zone yields an empty head rather than a
     * negative budget, leaving "... <zone>" rather than throwing.</p>
     */
    public static int headBudget(int suffixWidth, int ellipsisWidth, int allottedWidth) {
        return Math.max(0, allottedWidth - suffixWidth - ellipsisWidth);
    }
}
