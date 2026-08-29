package com.example.globe.client.create;

/**
 * Narrowing the escape-hatch footer must reproduce vanilla's own geometry, never exceed it, and
 * still fit at the narrowest GUI scale.
 */
public final class VanillaFooterLayoutPolicyTest {
    private static int assertions;

    private VanillaFooterLayoutPolicyTest() {
    }

    public static void run() {
        twoButtonsReproduceVanillasOwnWidth();
        threeButtonsFitWithinVanillasEnvelope();
        unnarrowedThreeButtonsWouldOverflowAnOrdinaryWindow();
        the320RuleGatesIncrementsAndIsNotAFloor();
        fittedEnvelopeKeepsTheRowOnScreenBelow320();
        rowLeftAgreesWithTheEnvelopeClampAndStillHoldsWithoutIt();
        rowIsCenteredAroundTheGivenPoint();
        rejectsNonPositiveCount();
        System.out.println("PASS VanillaFooterLayoutPolicyTest assertions=" + assertions);
    }

    /**
     * The anchor. Vanilla's own two-button footer must fall out of this formula unchanged, or the
     * "narrowing" would be visibly narrowing vanilla's own two buttons too.
     */
    private static void twoButtonsReproduceVanillasOwnWidth() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(2, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        assertEquals(150, width, "two buttons at vanilla's envelope reproduce vanilla's own 150px width");
        int rowWidth = width * 2 + VanillaFooterLayoutPolicy.SPACING;
        assertEquals(VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH, rowWidth,
                "two-button row exactly fills vanilla's envelope");
    }

    private static void threeButtonsFitWithinVanillasEnvelope() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        assertEquals(97, width, "three buttons narrow to 97px each");
        int rowWidth = width * 3 + VanillaFooterLayoutPolicy.SPACING * 2;
        assertTrue(rowWidth <= VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH,
                "narrowed three-button row (" + rowWidth + ") must not exceed vanilla's own envelope ("
                        + VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH + ")");
    }

    /**
     * The reason narrowing is mandatory, not cosmetic: three un-narrowed 150px buttons need 466px,
     * wider than any ordinary window's scaled width.
     */
    private static void unnarrowedThreeButtonsWouldOverflowAnOrdinaryWindow() {
        int unnarrowed = 150 * 3 + VanillaFooterLayoutPolicy.SPACING * 2;
        assertTrue(unnarrowed > 320,
                "un-narrowed three-button row (" + unnarrowed + ") must provably exceed a 320px "
                        + "scaled width -- this is WHY narrowing is required, not a style choice");
    }

    /**
     * THE PREMISE, MADE EXECUTABLE. Both footer features were once proved correct against the claim
     * that "Minecraft floors its GUI scale so the scaled width never drops below 320". That claim
     * was false, it lived only in prose, and prose is never consumed by anything -- so no review and
     * no mutation test could falsify it. It cost two live failures and three separate wrong
     * explanations of one crash before anyone checked it.
     *
     * <p>This reproduces {@code Window.calculateScale(int, boolean)} exactly as disassembled: the
     * {@code >= 320} / {@code >= 240} tests sit INSIDE the guard on incrementing the scale, so they
     * only ever stop the scale climbing. They create no floor. A window already narrower than 320
     * never enters the loop, the scale stays 1, and the scaled width is simply the window width.</p>
     */
    private static void the320RuleGatesIncrementsAndIsNotAFloor() {
        // A 146x116 window with GUI Scale set to 4 -- the configuration behind a real crash report.
        assertEquals(1, calculateScale(146, 116, 4, false),
                "the loop never runs at all when the window starts below 320: 146/2 = 73, which "
                        + "fails the increment guard on the very first evaluation");
        assertEquals(146, 146 / calculateScale(146, 116, 4, false),
                "so the scaled width is just the window width -- 320 is NOT a minimum");

        assertTrue(320 <= 1280 / calculateScale(1280, 960, 0, false),
                "at Auto scale the guard does hold the width at or above 320, which is the "
                        + "observation the false floor claim was over-generalised from");

        // Force Unicode Font bumps an odd scale by one AFTER the loop, outside the guard -- a second,
        // independent route below 320 that does not need a narrow window at all.
        int guarded = calculateScale(1000, 800, 0, false);
        int bumped = calculateScale(1000, 800, 0, true);
        assertEquals(3, guarded, "the guarded loop stops at 3 for a 1000px-wide window");
        assertEquals(4, bumped, "Force Unicode Font pushes it to 4, past what the guard permitted");
        assertTrue(1000 / bumped < 320,
                "leaving a scaled width of " + (1000 / bumped) + ", below 320, with a window that "
                        + "is not narrow at all");
    }

    /**
     * {@code rowLeft}'s clamp is BELT-AND-BRACES, and this pins it as such rather than pretending
     * otherwise. Mutation-tested: deleting the clamp did not redden any guard, because
     * {@link VanillaFooterLayoutPolicy#fittedEnvelope(int)} already guarantees the row fits, so the
     * centred left edge is never below the margin anyway. The envelope clamp is the load-bearing
     * guard; this one is the second line.
     *
     * <p>A redundant guard is only harmless if it AGREES with the guard that makes it redundant --
     * so that is checked across the range rather than assumed. And it is still worth keeping,
     * because a future caller that computes an envelope without the clamp gets caught here instead
     * of rendering at a negative x.</p>
     */
    private static void rowLeftAgreesWithTheEnvelopeClampAndStillHoldsWithoutIt() {
        for (int screenWidth : new int[] {146, 200, 320, 640, 1000}) {
            int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(screenWidth);
            int width = VanillaFooterLayoutPolicy.buttonWidth(3, envelope);
            int rowWidth = width * 3 + VanillaFooterLayoutPolicy.SPACING * 2;
            int clamped = VanillaFooterLayoutPolicy.rowLeft(3, width, screenWidth);
            assertEquals((screenWidth - rowWidth) / 2, clamped,
                    "clamped and unclamped agree at screen width " + screenWidth + " -- the clamp is "
                            + "redundant here, and a redundant guard that DISAGREED would be a bug");
            assertTrue(clamped >= 0 && clamped + rowWidth <= screenWidth,
                    "row stays on a " + screenWidth + "px screen");
        }

        // The case that makes it worth keeping: an envelope that skipped the clamp. Three
        // un-narrowed 150px buttons need 466px, which centred on a 146px screen starts at -160.
        assertEquals(VanillaFooterLayoutPolicy.SCREEN_MARGIN,
                VanillaFooterLayoutPolicy.rowLeft(3, 150, 146),
                "an unclamped envelope would start the row at -160; rowLeft refuses to go below the "
                        + "margin, and a negative x is what the renderer rejects outright");
    }

    /** Faithful transcription of {@code Window.calculateScale}, offsets 0-71. */
    private static int calculateScale(int framebufferWidth, int framebufferHeight, int guiScale, boolean forceUnicode) {
        int scale = 1;
        while (scale != guiScale
                && scale < framebufferWidth
                && scale < framebufferHeight
                && framebufferWidth / (scale + 1) >= 320
                && framebufferHeight / (scale + 1) >= 240) {
            scale++;
        }
        if (forceUnicode && scale % 2 != 0) {
            scale++;
        }
        return scale;
    }

    /**
     * Given that no floor exists, the envelope must be clamped to the screen. At the 146px width
     * from the crash report the row has to fit between the margins, and its left edge must stay
     * non-negative -- the renderer refuses a scissor at a negative x outright rather than clipping,
     * and a narrowed button whose label no longer fits is exactly what enables that scissor.
     */
    private static void fittedEnvelopeKeepsTheRowOnScreenBelow320() {
        int screenWidth = 146;
        int envelope = VanillaFooterLayoutPolicy.fittedEnvelope(screenWidth);
        assertEquals(138, envelope, "envelope shrinks to the screen minus a margin each side");

        int width = VanillaFooterLayoutPolicy.buttonWidth(3, envelope);
        int rowWidth = width * 3 + VanillaFooterLayoutPolicy.SPACING * 2;
        int left = Math.max(VanillaFooterLayoutPolicy.SCREEN_MARGIN, (screenWidth - rowWidth) / 2);
        assertTrue(left >= 0, "left edge is not negative (" + left + ")");
        assertTrue(left + rowWidth <= screenWidth,
                "row (" + rowWidth + " from " + left + ") stays within the " + screenWidth + "px screen");

        assertEquals(VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH,
                VanillaFooterLayoutPolicy.fittedEnvelope(1000),
                "and an ordinary screen is unaffected -- the clamp only ever binds when it must");
    }

    private static void rowIsCenteredAroundTheGivenPoint() {
        int width = VanillaFooterLayoutPolicy.buttonWidth(3, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        int centreX = 500;
        int left = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 0);
        int right = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, 2);
        int rowWidth = width * 3 + VanillaFooterLayoutPolicy.SPACING * 2;
        assertEquals(centreX - rowWidth / 2, left, "leftmost button starts at the row's left edge");
        assertEquals(left + rowWidth - width, right, "rightmost button ends at the row's right edge");
        for (int i = 1; i < 3; i++) {
            int prevRight = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, i - 1) + width;
            int thisLeft = VanillaFooterLayoutPolicy.buttonX(centreX, 3, width, i);
            assertEquals(VanillaFooterLayoutPolicy.SPACING, thisLeft - prevRight,
                    "adjacent buttons are separated by exactly SPACING");
        }
    }

    private static void rejectsNonPositiveCount() {
        boolean threw = false;
        try {
            VanillaFooterLayoutPolicy.buttonWidth(0, VanillaFooterLayoutPolicy.VANILLA_ROW_WIDTH);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "zero button count must be rejected");
    }

    private static void assertEquals(int expected, int actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
