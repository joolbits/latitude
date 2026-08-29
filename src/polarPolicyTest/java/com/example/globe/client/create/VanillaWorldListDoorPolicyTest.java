package com.example.globe.client.create;

/**
 * All that is left of this file's original geometry testing after the redesign: the door's own
 * dimming, since placement now comes entirely from {@link VanillaFooterLayoutPolicy}, already
 * exercised by {@code VanillaFooterLayoutPolicyTest}. See the mixin javadoc for why: an earlier
 * appended-with-refuse design was reported live as absent at a window that looked perfectly
 * ordinary on screen, because a manually-set high GUI Scale shrinks the game's own notion of
 * screen width independently of the window's real on-screen size.
 */
public final class VanillaWorldListDoorPolicyTest {
    private static int assertions;

    private VanillaWorldListDoorPolicyTest() {
    }

    public static void run() {
        alphaIsPartialTransparency();
        System.out.println("PASS VanillaWorldListDoorPolicyTest assertions=" + assertions);
    }

    /**
     * A value of exactly 1.0 (or above) would not dim the button at all -- the whole point of the
     * constant. A value of exactly 0.0 (or below) would make it invisible, defeating the door
     * entirely. Both ends of that range are worth pinning explicitly, not just "some float".
     */
    private static void alphaIsPartialTransparency() {
        assertTrue(VanillaWorldListDoorPolicy.ALPHA > 0.0f,
                "alpha must be strictly positive, or the door would be invisible rather than dimmed");
        assertTrue(VanillaWorldListDoorPolicy.ALPHA < 1.0f,
                "alpha must be strictly less than opaque, or the door would not read as secondary");
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
