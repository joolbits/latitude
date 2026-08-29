package com.example.globe.client;

/**
 * Regression for the Select World row's zone suffix surviving a narrow row.
 *
 * <p>Widths here are plain integers, so the arithmetic is checked without a font or a client.</p>
 */
public final class WorldListZoneFitPolicyTest {
    private static int assertions;

    private WorldListZoneFitPolicyTest() {
    }

    public static void runAll() {
        shortRowsAreLeftCompletelyUntouched();
        aRowLandingExactlyOnTheLimitIsNotClipped();
        anOverflowingRowIsClipped();
        theClippedRowStillFitsOnceSuffixAndEllipsisGoBackOn();
        anUncappedWidgetIsNeverClipped();
        aRowTooNarrowForTheZoneDegradesInsteadOfGoingNegative();
        System.out.println("WORLD_LIST_ZONE_FIT_POLICY_PASS assertions=" + assertions);
    }

    /**
     * The failure this guards against is not "the zone is missing" but "the fix damaged rows that
     * were already fine": clipping unconditionally would put an ellipsis on every short row.
     */
    private static void shortRowsAreLeftCompletelyUntouched() {
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(40, 30, 200),
                "a row with room to spare is not clipped");
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(0, 30, 200),
                "an empty head with a suffix is not clipped");
    }

    /**
     * Vanilla's own overflow test is strictly greater-than, so equality must fit. If this policy
     * used {@code >=} the row would be clipped needlessly -- and a combined width landing exactly on
     * the limit would then be re-clipped by vanilla and eat the zone anyway, invisibly.
     */
    private static void aRowLandingExactlyOnTheLimitIsNotClipped() {
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(170, 30, 200),
                "head plus suffix landing exactly on the allotment fits, as vanilla's own test does");
        expectTrue(!WorldListZoneFitPolicy.fitsWithoutClipping(171, 30, 200),
                "one pixel over the allotment does not fit");
    }

    private static void anOverflowingRowIsClipped() {
        expectTrue(!WorldListZoneFitPolicy.fitsWithoutClipping(300, 30, 200),
                "a long row overflows and must be clipped");
    }

    /**
     * The property that actually matters in game: after clipping the head to its budget, putting the
     * ellipsis and the zone back on must still land within the allotment -- otherwise vanilla clips
     * the result a second time and the zone is lost exactly as before.
     */
    private static void theClippedRowStillFitsOnceSuffixAndEllipsisGoBackOn() {
        int allotted = 200;
        int suffixWidth = 45;
        int ellipsisWidth = 8;
        int budget = WorldListZoneFitPolicy.headBudget(suffixWidth, ellipsisWidth, allotted);

        expectEquals(147, budget, "budget reserves both the suffix and the ellipsis");
        expectTrue(budget + ellipsisWidth + suffixWidth <= allotted,
                "the rebuilt row must fit, or vanilla clips it again and the zone is lost anyway");
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(budget + ellipsisWidth, suffixWidth, allotted),
                "and the policy itself agrees the rebuilt row fits");
    }

    private static void anUncappedWidgetIsNeverClipped() {
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(9999, 30, 0),
                "a widget with no width cap cannot overflow");
        expectTrue(WorldListZoneFitPolicy.fitsWithoutClipping(9999, 30, -1),
                "a negative cap is treated as no cap rather than clipping everything away");
    }

    private static void aRowTooNarrowForTheZoneDegradesInsteadOfGoingNegative() {
        int budget = WorldListZoneFitPolicy.headBudget(80, 8, 40);
        expectEquals(0, budget, "an impossible row yields an empty head, not a negative budget");
    }

    private static void expectEquals(int expected, int actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectTrue(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
