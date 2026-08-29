package com.example.globe.client.create;

/**
 * Minecraft-free geometry for re-laying the vanilla create-world footer when that screen was
 * reached through Latitude's escape hatch (issue #19).
 *
 * <p>Vanilla builds that footer as {@code LinearLayout.horizontal().spacing(8)} holding two
 * default-width (150px) buttons, so the row occupies {@link #VANILLA_ROW_WIDTH}px. Latitude adds a
 * third button there, and three default-width buttons would overflow the 320px narrowest scaled GUI.
 * Rather than widen the row, every button narrows so the three together never exceed the width
 * vanilla already used -- the row keeps its original footprint and nothing can push off-screen.</p>
 *
 * <p>Integer division deliberately rounds the width DOWN, so the arranged row is at most
 * {@code VANILLA_ROW_WIDTH} and never a pixel more.</p>
 */
public final class VanillaFooterLayoutPolicy {
    /** Vanilla's own footer spacing, from {@code CreateWorldScreen.init}. */
    public static final int SPACING = 8;

    /** Vanilla's two default-width buttons plus one gap: {@code 150 + 8 + 150}. */
    public static final int VANILLA_ROW_WIDTH = 308;

    private VanillaFooterLayoutPolicy() {
    }

    /**
     * Equal width for {@code count} buttons sharing {@code envelopeWidth}, gaps included.
     *
     * @throws IllegalArgumentException if {@code count} is not positive
     */
    public static int buttonWidth(int count, int envelopeWidth) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        int gaps = (count - 1) * SPACING;
        return Math.max(1, (envelopeWidth - gaps) / count);
    }

    /**
     * Left edge of button {@code index} in a row of {@code count} equal-width buttons centred on
     * {@code centreX}.
     *
     * @throws IllegalArgumentException if {@code count} is not positive, or {@code index} is outside
     *         {@code [0, count)}
     */
    public static int buttonX(int centreX, int count, int width, int index) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        int total = count * width + (count - 1) * SPACING;
        int left = centreX - total / 2;
        return left + index * (width + SPACING);
    }

    /**
     * Width for three buttons sharing a row's EXISTING footprint, with two independent gaps.
     *
     * <p>Replaces the earlier append-and-refuse approach, which asked for space beyond what vanilla's
     * row already claimed and gave up when there was none. That produced a button which simply was
     * not there on ordinary windows -- reported live on the sibling line and rejected outright by the
     * maintainer, who was right: a control that vanishes is worse than a slightly narrower one.</p>
     *
     * <p>Nothing here is ever requested beyond {@code envelopeWidth}, so there is no width at which
     * this can fail. The two gaps are separate deliberately: the door keeps a wider separation from
     * its neighbour than the neighbours keep from each other, because that gap is the mis-click
     * defence the design was chosen for, not spacing.</p>
     *
     * @throws IllegalArgumentException if any gap is negative
     */
    public static int sharedWidthForThree(int envelopeWidth, int innerGap, int doorGap) {
        if (innerGap < 0 || doorGap < 0) {
            throw new IllegalArgumentException("gaps must not be negative");
        }
        return Math.max(1, (envelopeWidth - innerGap - doorGap) / 3);
    }

    /**
     * Left edges of the three buttons, laid out from {@code rowLeft} within the same footprint.
     *
     * <p>Returns exactly three values: left, middle, and the door.</p>
     */
    public static int[] threeButtonXs(int rowLeft, int width, int innerGap, int doorGap) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (innerGap < 0 || doorGap < 0) {
            throw new IllegalArgumentException("gaps must not be negative");
        }
        int first = rowLeft;
        int second = first + width + innerGap;
        int third = second + width + doorGap;
        return new int[] {first, second, third};
    }

    /** Keeps a row clear of the screen edges. */
    public static final int EDGE_MARGIN = 4;

    /**
     * The row's natural width, clamped to what the screen can actually show.
     *
     * <p>CRASHED THE CLIENT before this existed -- but not for the reason first recorded here,
     * which was wrong and is corrected in place rather than quietly replaced.</p>
     *
     * <p>{@code Window.calculateScale} is, in effect:</p>
     *
     * <pre>  while (scale != guiScale &amp;&amp; scale &lt; fbW &amp;&amp; scale &lt; fbH
     *         &amp;&amp; fbW/(scale+1) &gt;= 320 &amp;&amp; fbH/(scale+1) &gt;= 240) scale++;</pre>
     *
     * <p>The 320 test sits INSIDE the increment guard, so it only ever stops the scale climbing. It
     * is not a minimum, and a manually set GUI scale cannot push the width below it -- that setting
     * is an upper bound and can only make the width LARGER. The earlier claim that a manual scale
     * bypasses the floor was false (peer correction, verified against 26.2 bytecode).</p>
     *
     * <p>What actually happens needs no unusual setting: when the WINDOW itself is narrower than
     * 320, the loop never runs, the scale stays 1, and the scaled width is simply the window width.
     * Her crash report states it outright -- "Window size: 146x116" -- and reproducing the algorithm
     * for 146x116 at guiScale 4 with forceUnicodeFont false gives scale 1 and width 146, matching
     * the reported {@code RenderArea width=146} exactly. Centring a 308-wide row there puts the left
     * edge at -81 and the first draw throws
     * {@code IllegalArgumentException: Scissor at -79 ... out of bounds}.</p>
     *
     * <p>Presence was guaranteed; staying on screen was not.</p>
     */
    public static int fittedEnvelope(int naturalEnvelope, int screenWidth, int edgeMargin) {
        if (naturalEnvelope <= 0) {
            throw new IllegalArgumentException("naturalEnvelope must be positive");
        }
        int available = Math.max(1, screenWidth - 2 * Math.max(0, edgeMargin));
        return Math.min(naturalEnvelope, available);
    }

    /** Left edge of a centred row, never left of the margin and never negative. */
    public static int clampedRowLeft(int screenWidth, int envelope, int edgeMargin) {
        int margin = Math.max(0, edgeMargin);
        return Math.max(margin, (screenWidth - envelope) / 2);
    }

    /** Total width the arranged row occupies, gaps included. */
    public static int rowWidth(int count, int width) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        return count * width + (count - 1) * SPACING;
    }
}
