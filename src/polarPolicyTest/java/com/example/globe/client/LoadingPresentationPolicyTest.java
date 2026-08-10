package com.example.globe.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guards the Latitude loading pane's coverage of the whole world-open screen chain.
 *
 * <p>Resuming a save was branded only at the very end: {@code WorldOpenFlows.openWorld} shows three
 * sequential {@code GenericMessageScreen}s before {@code LevelLoadingScreen} exists, and the
 * overlay painted only the last of them. The flag was set correctly and early — nothing painted it.
 * Every overlay hook in this lifecycle is deliberately {@code require = 0} (a missed target must
 * degrade to vanilla, never crash), which also means a hook that stops matching goes <b>silent</b>.
 * The mixin-target verifier catches a target that moved; only this catches a screen in the chain
 * that nothing covers.
 */
public final class LoadingPresentationPolicyTest {

    private LoadingPresentationPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("LOADING_PRESENTATION_POLICY_TEST_PASS");
    }

    public static void runAll() throws Exception {
        everyScreenInTheResumeChainPaintsTheSharedPane();
        theSharedPaneOwnsTheDrawingSoTheTwoScreensCannotDrift();
        thePaneClockIsSharedSoTheHandoffDoesNotRestartIt();
        theVanillaMessageWidgetIsRestoredWheneverLatitudeIsNotLoading();
    }

    /**
     * The reload path's plain message screens must be covered, not just the final loading screen.
     */
    private static void everyScreenInTheResumeChainPaintsTheSharedPane() throws IOException {
        String config = read("src/main/resources/globe.mixins.json");
        assertTrue(config.contains("\"client.GenericMessageScreenLatitudeOverlayMixin\""),
                "the GenericMessageScreen overlay must be registered — an unregistered mixin never "
                        + "applies and the reload path silently reverts to vanilla-then-bespoke");
        assertTrue(config.contains("\"client.LevelLoadingScreenLatitudeOverlayMixin\""),
                "the LevelLoadingScreen overlay stays registered");

        String generic = read(
                "src/main/java/com/example/globe/mixin/client/GenericMessageScreenLatitudeOverlayMixin.java");
        // GenericMessageScreen inherits render() from Screen and only declares renderBackground,
        // so that is the only hook Mixin can resolve on this target.
        assertTrue(generic.contains("method = \"renderBackground\""),
                "GenericMessageScreen declares renderBackground, not render — the hook must target it");
        assertTrue(generic.contains("LatitudeLoadingPane.render("),
                "the message screens must paint the same pane as the loading screen");
    }

    /** Neither screen may carry its own copy of the pane's drawing. */
    private static void theSharedPaneOwnsTheDrawingSoTheTwoScreensCannotDrift() throws IOException {
        String pane = read("src/main/java/com/example/globe/client/LatitudeLoadingPane.java");
        assertTrue(pane.contains("PANE_BG") && pane.contains("drawCompass(")
                        && pane.contains("drawPhrase("),
                "LatitudeLoadingPane owns the pane background, compass and phrase drawing");

        for (String mixin : new String[] {
                "src/main/java/com/example/globe/mixin/client/LevelLoadingScreenLatitudeOverlayMixin.java",
                "src/main/java/com/example/globe/mixin/client/GenericMessageScreenLatitudeOverlayMixin.java",
        }) {
            String source = read(mixin);
            assertTrue(source.contains("LatitudeLoadingPane"),
                    mixin + " must delegate to the shared pane");
            assertFalse(source.contains("drawCompass(") || source.contains("drawPhrase("),
                    mixin + " must not re-implement pane drawing — two copies drift, and a fix "
                            + "applied to one screen would silently miss the other");
        }
    }

    /**
     * Vanilla builds a fresh screen instance at each step of the chain, so per-instance animation
     * state would restart the phrase cycle and snap the needle at exactly the handoffs this pane
     * exists to smooth.
     */
    private static void thePaneClockIsSharedSoTheHandoffDoesNotRestartIt() throws IOException {
        String pane = read("src/main/java/com/example/globe/client/LatitudeLoadingPane.java");
        assertTrue(pane.contains("private static long overlayStartMs"),
                "the pane clock is static so it survives the screen handoff");
        assertTrue(pane.contains("private static double needleAngle"),
                "the compass needle angle is shared across the screen handoff");
        assertTrue(pane.contains("if (overlayStartMs != 0L) {"),
                "start() is idempotent — a second screen adopting the pane must not restart its clock");
    }

    /**
     * Fail-open: the hook runs for every message screen in the game, so it must never leave an
     * unrelated one blank.
     */
    private static void theVanillaMessageWidgetIsRestoredWheneverLatitudeIsNotLoading() throws IOException {
        String generic = read(
                "src/main/java/com/example/globe/mixin/client/GenericMessageScreenLatitudeOverlayMixin.java");
        assertTrue(generic.contains("textWidget.visible = !loading"),
                "the vanilla message text is suppressed only while Latitude owns the screen, and "
                        + "restored on every other frame");
        int restore = generic.indexOf("textWidget.visible = !loading");
        int earlyReturn = generic.indexOf("if (!loading) {");
        assertTrue(restore > 0 && earlyReturn > restore,
                "the widget must be restored BEFORE the not-loading early return, or an aborted "
                        + "load leaves every later message screen permanently blank");
        assertTrue(generic.contains("require = 0"),
                "the overlay hook fails soft, per the GitHub #7 rule");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
