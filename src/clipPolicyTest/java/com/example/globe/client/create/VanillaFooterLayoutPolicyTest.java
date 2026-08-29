package com.example.globe.client.create;

final class VanillaFooterLayoutPolicyTest {
    /** Minecraft picks a GUI scale that keeps the scaled width at or above this. */
    private static final int NARROWEST_SCALED_GUI = 320;

    private static int assertions;

    private VanillaFooterLayoutPolicyTest() {
    }

    static int run() {
        assertions = 0;
        twoButtonsReproduceVanillasOwnGeometry();
        threeButtonsNeverOutgrowTheRowVanillaAlreadyUsed();
        threeButtonsFitTheNarrowestGui();
        buttonsAreOrderedGapPreservingAndCentred();
        degenerateArgumentsAreRejected();
        theDoorFitsAtEveryWidthBecauseItSharesTheRow();
        subThreeTwentyWidthsAreGenuinelyReachable();
        nothingIsEverPlacedOffTheLeftEdge();
        return assertions;
    }

    /**
     * The policy is only trustworthy for three if it agrees with vanilla for two: vanilla's footer
     * is two 150px buttons with an 8px gap, so the same envelope must give back exactly 150.
     */
    private static void twoButtonsReproduceVanillasOwnGeometry() {
        expectEquals(150, VanillaFooterLayoutPolicy.buttonWidth(2, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH),
                "two buttons in vanilla's envelope are vanilla's own 150px");
        expectEquals(VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH,
                VanillaFooterLayoutPolicy.rowWidth(2, 150),
                "and they occupy exactly the row width vanilla used");
    }

    /**
     * The whole reason the buttons narrow: three at vanilla's default 150px would be 466px, which
     * overflows. Widening the row instead would push a button off-screen at small GUI sizes.
     */
    private static void threeButtonsNeverOutgrowTheRowVanillaAlreadyUsed() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        int row = VanillaFooterLayoutPolicy.rowWidth(3, width);
        expectTrue(row <= VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH,
                "three buttons must fit inside vanilla's own footprint, was " + row);
        expectTrue(VanillaFooterLayoutPolicy.rowWidth(3, 150) > VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH,
                "and the unnarrowed row genuinely would not have fitted");
    }

    private static void threeButtonsFitTheNarrowestGui() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        int centreX = NARROWEST_SCALED_GUI / 2;
        int leftEdge = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 0);
        int rightEdge = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 2) + width;
        expectTrue(leftEdge >= 0, "the row must not start off the left edge, was " + leftEdge);
        expectTrue(rightEdge <= NARROWEST_SCALED_GUI,
                "the row must not run past the right edge, was " + rightEdge);
    }

    private static void buttonsAreOrderedGapPreservingAndCentred() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        int centreX = 200;
        int first = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 0);
        int second = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 1);
        int third = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 2);

        expectEquals(width + VanillaFooterLayoutPolicy.SPACING, second - first,
                "adjacent buttons are one button plus one gap apart");
        expectEquals(width + VanillaFooterLayoutPolicy.SPACING, third - second,
                "and the gap is uniform across the row");
        expectEquals(centreX, (first + third + width) / 2, "the row is centred on the given centre");
    }

    private static void degenerateArgumentsAreRejected() {
        expectThrows(() -> VanillaFooterLayoutPolicy.buttonWidth(0, 308), "a zero-button row");
        expectThrows(() -> VanillaFooterLayoutPolicy.buttonX(160, 0, 98, 0), "a zero-button placement");
        expectThrows(() -> VanillaFooterLayoutPolicy.buttonX(160, 3, 98, 3), "an out-of-range index");
        expectThrows(() -> VanillaFooterLayoutPolicy.buttonX(160, 3, 98, -1), "a negative index");
        expectTrue(VanillaFooterLayoutPolicy.buttonWidth(3, 4) >= 1,
                "an absurdly small envelope still yields a positive width rather than zero or negative");
    }

    /**
     * The door must be present at EVERY width -- the guarantee that replaced refuse-when-no-room.
     *
     * <p>The earlier design appended past the row's edge and gave up when there was no spare width,
     * so the button was simply absent on ordinary windows at a manually-raised GUI scale (Minecraft's
     * internal width shrinks with GUI scale independently of how large the window looks). That was
     * reported live and rejected: a control that vanishes is worse than a slightly narrower one.</p>
     *
     * <p>Sharing the row's own footprint makes absence structurally impossible, so this asserts the
     * property directly across the whole reachable range rather than pinning one measured case.</p>
     */
    private static void theDoorFitsAtEveryWidthBecauseItSharesTheRow() {
        int inner = 8, doorGap = 10;
        // 308 is vanilla's own two-button row; the envelope never grows beyond what it already had.
        for (int envelope : new int[] {308, 300, 280, 240, 200, 160, 120, 80, 40}) {
            int w = VanillaFooterLayoutPolicy.sharedWidthForThree(envelope, inner, doorGap);
            expectTrue(w >= 1, "width must stay positive at envelope " + envelope + ", was " + w);
            int[] xs = VanillaFooterLayoutPolicy.threeButtonXs(100, w, inner, doorGap);
            int right = xs[2] + w;
            expectTrue(right <= 100 + envelope,
                    "three buttons must stay inside the row's own footprint at envelope "
                            + envelope + "; right edge " + right + " vs " + (100 + envelope));
            expectTrue(xs[0] < xs[1] && xs[1] < xs[2], "buttons must stay in order");
        }
        // The door's separation must exceed the neighbours' own spacing -- that gap is the
        // mis-click defence, and equalising it would silently discard the reason for the design.
        int w = VanillaFooterLayoutPolicy.sharedWidthForThree(308, inner, doorGap);
        int[] xs = VanillaFooterLayoutPolicy.threeButtonXs(0, w, inner, doorGap);
        expectEquals(inner, xs[1] - (xs[0] + w), "neighbours keep vanilla's own spacing");
        expectEquals(doorGap, xs[2] - (xs[1] + w), "the door keeps its wider separation");
        expectTrue(doorGap > inner, "the door's gap must remain the larger of the two");

        expectThrows(() -> VanillaFooterLayoutPolicy.sharedWidthForThree(308, -1, 10), "a negative gap");
        expectThrows(() -> VanillaFooterLayoutPolicy.threeButtonXs(0, 0, 8, 10), "a zero width");
    }

    /**
     * No coordinate may be negative at ANY screen width. This crashed the client.
     *
     * <p>Both lines reasoned about 320 as a minimum scaled width. It is not a minimum: in
     * {@code Window.calculateScale} the 320 test sits inside the increment guard, so it only stops
     * the scale climbing. A manually set GUI scale cannot push the width below it -- that was the
     * first explanation recorded here and it was wrong. When the WINDOW is narrower than 320 the
     * loop never runs at all, the scale stays 1, and the scaled width is just the window width.
     * Her crash: window 146x116, guiScale 4, forceUnicodeFont false, scale 1, scaled width 146 --
     * matching {@code RenderArea width=146} in the report exactly. Centring a 308-wide row there
     * gives a left edge of -81 and the next draw throws {@code Scissor at -79 ... out of bounds}.
     * Guaranteeing the button is PRESENT is not the same as guaranteeing it is ON SCREEN, and the
     * earlier fix only did the former.</p>
     */
    private static void nothingIsEverPlacedOffTheLeftEdge() {
        int inner = 8, doorGap = 10, margin = VanillaFooterLayoutPolicy.EDGE_MARGIN;
        // 146 is the width from the live crash; the rest bracket it well below any assumed floor.
        for (int screen : new int[] {146, 80, 60, 40, 20, 320, 427, 640}) {
            int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(308, screen, margin);
            int left = VanillaFooterLayoutPolicy.clampedRowLeft(screen, envelope, margin);
            int width = VanillaFooterLayoutPolicy.sharedWidthForThree(envelope, inner, doorGap);
            int[] xs = VanillaFooterLayoutPolicy.threeButtonXs(left, width, inner, doorGap);

            expectTrue(left >= 0, "row left must never be negative at screen " + screen + ", was " + left);
            expectTrue(xs[0] >= 0, "first button must not be off the left edge at screen " + screen);
            expectTrue(envelope <= screen,
                    "envelope must never exceed the screen at " + screen + ", was " + envelope);
            expectTrue(width >= 1, "width must stay positive at screen " + screen);
        }
        // The clamp must not fire when there is room -- otherwise it would silently shrink normal rows.
        expectEquals(308, VanillaFooterLayoutPolicy.fittedEnvelope(308, 640, margin),
                "a roomy screen keeps the row's natural width");
    }

    /**
     * Makes the PREMISE executable instead of leaving it in a comment.
     *
     * <p>Three different causes were proposed for the sub-320 crash and all three were wrong, while
     * the arithmetic built on each was right every time -- because each lived in a javadoc or a
     * commit message, where nothing consumes it. A claim is only tested when something downstream
     * reads it, and no mutation test can catch a false comment because a mutated comment changes no
     * behaviour. So the premise behind the widths chosen below is asserted here rather than
     * narrated: if someone comes to believe 320 is a floor and prunes those cases, this fails first
     * and says why.</p>
     *
     * <p>HONEST LIMIT: this reproduces {@code Window.calculateScale}'s shape rather than calling it
     * -- the policy suite is deliberately Minecraft-free. It therefore pins our MODEL of vanilla,
     * which could drift from vanilla itself. It cannot replace reading the bytecode; it can stop the
     * model being quietly wrong in the direction that matters here.</p>
     */
    private static void subThreeTwentyWidthsAreGenuinelyReachable() {
        // while (scale != guiScale && scale < fbW && scale < fbH
        //        && fbW/(scale+1) >= 320 && fbH/(scale+1) >= 240) scale++;
        // if (forceUnicode && scale % 2 != 0) scale++;
        // Verified against 26.2 bytecode, Window.calculateScale offsets 0..71.
        expectEquals(146, modelScaledWidth(146, 116, 4, false),
                "a window narrower than 320 leaves the scale at 1, so the scaled width IS the "
                        + "window width -- this is her live crash: Window size 146x116");
        expectTrue(modelScaledWidth(146, 116, 4, false) < 320,
                "sub-320 is reachable with entirely default settings, so the cases below are real");

        // The claim that was wrong, now falsifiable: a manual scale cannot make the width smaller.
        int auto = modelScaledWidth(1920, 1080, 0, false);
        for (int gui : new int[] {1, 2, 3, 4, 5}) {
            expectTrue(modelScaledWidth(1920, 1080, gui, false) >= auto,
                    "a manual guiScale is an upper bound on the CLIMB, so it can only make the "
                            + "scaled width larger, never smaller -- guiScale " + gui);
        }
        // The second genuine route, kept because it is real even though it is not her case.
        expectTrue(modelScaledWidth(1000, 1000, 0, true) < 320,
                "forceUnicodeFont bumps the scale outside the guard and can also go sub-320");
    }

    /** Model of vanilla's scale rule; see the caveat on the caller. */
    private static int modelScaledWidth(int fbW, int fbH, int guiScale, boolean forceUnicode) {
        int scale = 1;
        while (scale != guiScale && scale < fbW && scale < fbH
                && fbW / (scale + 1) >= 320 && fbH / (scale + 1) >= 240) {
            scale++;
        }
        if (forceUnicode && scale % 2 != 0) {
            scale++;
        }
        return fbW / scale;
    }

    private static void expectThrows(Runnable action, String label) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + " must be rejected");
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

    private static void expectFalse(boolean condition, String label) {
        expectTrue(!condition, label);
    }
}
