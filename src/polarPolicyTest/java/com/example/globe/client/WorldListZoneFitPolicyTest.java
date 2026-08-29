package com.example.globe.client;

import com.example.globe.client.WorldListZoneFitPolicy.WidthMeasure;

/**
 * The world-list row must never sacrifice the climate zone, and must never damage a row that
 * already fits.
 */
public final class WorldListZoneFitPolicyTest {
    private static final String ELLIPSIS = "...";

    /** Every glyph six wide — a plain, unstyled row. */
    private static final WidthMeasure FLAT = text -> text.length() * 6;

    /**
     * Every glyph seven wide. This is bold's exact shape: a per-glyph surcharge, so the error
     * against a plain measurement grows with the length of the cut rather than being a constant.
     * A cut chosen under FLAT and rendered under this is the live failure mode being guarded.
     */
    private static final WidthMeasure SURCHARGED = text -> text.length() * 7;

    private static int assertions;

    private WorldListZoneFitPolicyTest() {
    }

    public static void runAll() {
        rowThatFitsComesBackUntouched();
        rowThatFitsExactlyOnTheLimitComesBackUntouched();
        overflowingRowReservesTheZone();
        reservationHoldsUnderAWidthAffectingStyle();
        naiveFlatCutWouldOverflowUnderThatStyle();
        surrenderTheHeadWhenEvenTheEllipsisCannotFit();
        cutNeverSplitsASurrogatePair();
        System.out.println("PASS WorldListZoneFitPolicyTest assertions=" + assertions);
    }

    /**
     * The guard that keeps this feature from damaging what it was meant to leave alone. Clipping
     * helpers append their ellipsis unconditionally, so dropping the fits-check puts a bogus "..."
     * on every short row. Identity, not just equality — nothing may be rebuilt on this path.
     */
    private static void rowThatFitsComesBackUntouched() {
        String original = "wafer (8/23/26, 5:23 PM)";
        String head = WorldListZoneFitPolicy.headText(original, FLAT, ELLIPSIS, 60, 1000);
        assertSame(original, head, "a row with room to spare is returned unchanged");
        assertTrue(!head.endsWith(ELLIPSIS), "a row that fits never gains an ellipsis");
    }

    /** Vanilla's overflow test is strictly greater-than, so landing exactly on the limit fits. */
    private static void rowThatFitsExactlyOnTheLimitComesBackUntouched() {
        String original = "abcdefghij";                 // 10 glyphs, 60 wide under FLAT
        String head = WorldListZoneFitPolicy.headText(original, FLAT, ELLIPSIS, 40, 100);
        assertSame(original, head, "combined width landing exactly on the allotment is not clipped");
    }

    private static void overflowingRowReservesTheZone() {
        String original = "a-very-long-world-folder-name (8/24/26, 10:12 PM)";
        int suffixWidth = 66;                            // " Subtropical" at six wide
        int allotted = 240;
        String head = WorldListZoneFitPolicy.headText(original, FLAT, ELLIPSIS, suffixWidth, allotted);

        assertTrue(head.length() < original.length(), "an overflowing row is shortened");
        assertTrue(head.endsWith(ELLIPSIS), "a shortened row is marked with an ellipsis");
        assertTrue(original.startsWith(head.substring(0, head.length() - ELLIPSIS.length())),
                "the kept text is a prefix of the original");
        assertFits(head, FLAT, suffixWidth, allotted, "reserved zone still fits beside the head");

        // And it is the LONGEST such prefix -- a lazy implementation that over-trims would pass the
        // fit assertion while throwing away readable characters.
        String oneMore = original.substring(0, head.length() - ELLIPSIS.length() + 1) + ELLIPSIS;
        assertTrue(FLAT.widthOf(oneMore) + suffixWidth > allotted,
                "one more character would not have fit; the kept prefix is maximal");
    }

    /**
     * The whole point of injecting the measurement. Under a width-affecting style the policy must
     * still leave room for the zone — not document that the style must stay width-neutral.
     */
    private static void reservationHoldsUnderAWidthAffectingStyle() {
        String original = "a-very-long-world-folder-name (8/24/26, 10:12 PM)";
        int suffixWidth = 77;                            // the same suffix, surcharged
        int allotted = 240;
        String head = WorldListZoneFitPolicy.headText(original, SURCHARGED, ELLIPSIS, suffixWidth, allotted);

        assertFits(head, SURCHARGED, suffixWidth, allotted,
                "reservation holds when the row's style costs extra per glyph");
        assertTrue(head.endsWith(ELLIPSIS), "the surcharged row is still marked as shortened");
    }

    /**
     * The control that gives the test above teeth. Measuring the cut with a plain width function
     * and rendering it styled is the defect this design exists to prevent; prove that combination
     * really does overflow, so the assertion above is not passing vacuously.
     */
    private static void naiveFlatCutWouldOverflowUnderThatStyle() {
        String original = "a-very-long-world-folder-name (8/24/26, 10:12 PM)";
        int allotted = 240;
        String naive = WorldListZoneFitPolicy.headText(original, FLAT, ELLIPSIS, 66, allotted);

        assertTrue(SURCHARGED.widthOf(naive) + 77 > allotted,
                "a cut measured plain and rendered styled overflows -- which is why the measurement "
                        + "is injected rather than taken from a plain-text helper");

        String correct = WorldListZoneFitPolicy.headText(original, SURCHARGED, ELLIPSIS, 77, allotted);
        assertTrue(correct.length() < naive.length(),
                "the style-aware cut is strictly shorter than the plain one");
    }

    private static void surrenderTheHeadWhenEvenTheEllipsisCannotFit() {
        String head = WorldListZoneFitPolicy.headText("anything at all", FLAT, ELLIPSIS, 95, 100);
        assertEquals("", head, "the zone outranks an ellipsis with no text attached to it");
    }

    private static void cutNeverSplitsASurrogatePair() {
        String original = "world 🌍🌍🌍🌍 (8/24/26)";
        for (int allotted = 20; allotted <= 200; allotted += 2) {
            String head = WorldListZoneFitPolicy.headText(original, FLAT, ELLIPSIS, 18, allotted);
            String kept = head.endsWith(ELLIPSIS)
                    ? head.substring(0, head.length() - ELLIPSIS.length())
                    : head;
            assertions++;
            if (!kept.isEmpty() && Character.isHighSurrogate(kept.charAt(kept.length() - 1))) {
                throw new AssertionError("cut split a surrogate pair at allotment " + allotted
                        + " (kept=\"" + kept + "\")");
            }
        }
    }

    private static void assertFits(String head, WidthMeasure measure, int suffixWidth,
                                   int allotted, String message) {
        assertions++;
        int total = measure.widthOf(head) + suffixWidth;
        if (total > allotted) {
            throw new AssertionError(message + ": rendered width " + total
                    + " exceeds allotment " + allotted + " (head=\"" + head + "\")");
        }
    }

    private static void assertSame(String expected, String actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected the original instance, got \"" + actual + "\"");
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected \"" + expected + "\" but was \"" + actual + "\"");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
