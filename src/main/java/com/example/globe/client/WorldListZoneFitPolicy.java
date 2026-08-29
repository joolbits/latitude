package com.example.globe.client;

import java.util.Arrays;

/**
 * Fits the world list's "id (last played)" line so Latitude's climate-zone suffix always survives.
 *
 * <p>Vanilla gives that row a fixed allotment and clips anything past it with an ellipsis. Latitude
 * appends the zone at the END of that line, so the zone is by construction the first thing
 * sacrificed — which is exactly what was reported live: long rows showed "… 10:12 PM) S…" where
 * "Subtropical" belonged. This policy reserves the suffix's width first and spends what is left on
 * the id and timestamp, so the text that shortens is the level id — which duplicates the world name
 * already shown on the line above it, and is therefore the least informative text in the row.</p>
 *
 * <p><b>Why this measures rendered width rather than cutting with a font helper.</b>
 * {@code Font.plainSubstrByWidth} passes {@code Style.EMPTY} to the splitter unconditionally, so it
 * measures the cut as unstyled no matter what the caller does — nothing at the call site can
 * influence it. If the row's style ever became width-affecting (bold adds a per-glyph offset), a cut
 * chosen that way would over-fill, the combined line would tip past the allotment, vanilla's own
 * clip would fire on the whole line, and the zone would be eaten again — silently, with the
 * arithmetic here provably correct. Searching on the width of the string as it will actually render
 * has no such coupling, and costs a handful of measurements on overflowing rows only.</p>
 *
 * <p>Deliberately free of every Minecraft type: the measurement arrives as a function so the policy
 * suites (which compile against Latitude's own classes only) can exercise it with a stub, including
 * a stub that charges a per-character surcharge the way bold does.</p>
 */
public final class WorldListZoneFitPolicy {

    /**
     * Measures a candidate head string as it will actually be rendered — i.e. carrying the same
     * style the finished component will carry, not the bare characters.
     */
    @FunctionalInterface
    public interface WidthMeasure {
        int widthOf(String text);
    }

    private WorldListZoneFitPolicy() {
    }

    /**
     * Returns the text to render before the zone suffix.
     *
     * <p>Returns {@code original} <b>unchanged, and by identity</b> when it already fits. That is
     * load-bearing rather than an optimisation: every clipping helper in this area appends its
     * ellipsis with no fits-already check, so a missing guard here would put a bogus "…" on every
     * short row — visibly damaging the rows this feature was meant to leave alone.</p>
     *
     * <p>When it does not fit, returns the longest code-point-aligned prefix whose rendered width,
     * <em>with the ellipsis already attached</em>, leaves room for the suffix.</p>
     */
    public static String headText(String original, WidthMeasure measure, String ellipsis,
                                  int suffixWidth, int allottedWidth) {
        if (original == null) throw new IllegalArgumentException("original text is required");
        if (measure == null) throw new IllegalArgumentException("width measure is required");
        if (ellipsis == null) throw new IllegalArgumentException("ellipsis text is required");

        int budget = allottedWidth - suffixWidth;
        if (measure.widthOf(original) <= budget) {
            return original;
        }
        if (measure.widthOf(ellipsis) > budget) {
            // Not even the ellipsis fits beside the zone. The zone is the more useful of the two,
            // so surrender the head entirely rather than push the row past its allotment.
            return "";
        }

        // Rendered width is non-decreasing in prefix length, so the longest fitting prefix is found
        // by bisection. Candidates are code-point boundaries, never raw char offsets, so a cut can
        // never land between the halves of a surrogate pair.
        int[] boundaries = codePointBoundaries(original);
        int low = 0;                        // boundaries[0] is "", proven to fit just above
        int high = boundaries.length - 1;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (measure.widthOf(original.substring(0, boundaries[mid]) + ellipsis) <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return original.substring(0, boundaries[low]) + ellipsis;
    }

    /** Ascending char offsets at which {@code text} may be split without breaking a code point. */
    private static int[] codePointBoundaries(String text) {
        int length = text.length();
        int[] offsets = new int[length + 1];
        int count = 0;
        int index = 0;
        while (true) {
            offsets[count++] = index;
            if (index >= length) break;
            index += Character.charCount(text.codePointAt(index));
        }
        return Arrays.copyOf(offsets, count);
    }
}
