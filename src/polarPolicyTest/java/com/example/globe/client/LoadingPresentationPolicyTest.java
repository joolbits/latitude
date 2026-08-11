package com.example.globe.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guards the Latitude loading pane's coverage of the whole world-open screen chain, and the flag
 * that decides whether the pane draws at all.
 *
 * <p>The chain a resumed world passes through, enumerated from the remapped 1.21.11 jar rather than
 * from source: three {@code GenericMessageScreen}s ({@code WorldOpenFlows.openWorld},
 * {@code openWorldLoadLevelData}, {@code openWorldLoadLevelStem}), one {@code ProgressScreen}
 * ({@code Minecraft.doWorldLoad}'s opening {@code disconnectWithProgressScreen}), then
 * {@code LevelLoadingScreen} twice ({@code doWorldLoad}, then {@code ClientPacketListener.handleLogin}).
 * Every one of those is shown through {@code setScreenAndShow} or {@code setScreen}, and every one
 * must carry the pane. The chain's remaining screens are all interactive error/confirm screens and
 * must stay vanilla.
 *
 * <p>Two distinct defects have hidden in here, and this file guards both:
 * <ul>
 *   <li><b>Uncovered screens.</b> The overlay originally painted only {@code LevelLoadingScreen}.
 *   <li><b>A flag switched off mid-chain.</b> The hook at {@code doWorldLoad} second-guessed the
 *       early activation with {@code NoiseBasedChunkGenerator.stable(...)}, which cannot match a
 *       resumed save (its {@code level.dat} inlines the overworld noise settings, so the holder has
 *       no registry key), and cleared the flag for the entire server-start and spawn-load phase.
 *       Full coverage of the chain is worth nothing while the flag says "not a Latitude world".
 * </ul>
 *
 * <p>Every overlay hook in this lifecycle is deliberately {@code require = 0} (a missed target must
 * degrade to vanilla, never crash), which also means a hook that stops matching goes <b>silent</b>.
 * The mixin-target verifier catches a target that moved; only this catches a screen in the chain
 * that nothing covers, or a verdict that turns the pane off behind its back.
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
        theResumedWorldVerdictIsNotDecidedByTheStemCheckAlone();
        releaseBuildCarriesNoLoadingTraceHook();
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
        assertTrue(config.contains("\"client.ProgressScreenLatitudeOverlayMixin\""),
                "the ProgressScreen overlay must be registered — doWorldLoad opens with "
                        + "disconnectWithProgressScreen(), whose setScreenAndShow forces a rendered "
                        + "frame of it on every single world open");

        String generic = read(
                "src/main/java/com/example/globe/mixin/client/GenericMessageScreenLatitudeOverlayMixin.java");
        // GenericMessageScreen inherits render() from Screen and only declares renderBackground,
        // so that is the only hook Mixin can resolve on this target.
        assertTrue(generic.contains("method = \"renderBackground\""),
                "GenericMessageScreen declares renderBackground, not render — the hook must target it");
        assertTrue(generic.contains("LatitudeLoadingPane.render("),
                "the message screens must paint the same pane as the loading screen");

        String progress = read(
                "src/main/java/com/example/globe/mixin/client/ProgressScreenLatitudeOverlayMixin.java");
        // ProgressScreen, unlike GenericMessageScreen, does declare render().
        assertTrue(progress.contains("method = \"render\""),
                "ProgressScreen declares render — the hook must target it");
        assertTrue(progress.contains("LatitudeLoadingPane.render("),
                "the progress screen must paint the same pane as the rest of the chain");
        assertTrue(progress.contains("require = 0"),
                "the overlay hook fails soft, per the GitHub #7 rule");
    }

    /**
     * The pane is only ever drawn while the loading flag is set, so a verdict that switches the flag
     * off mid-chain blanks a fully-covered chain just as effectively as a missing hook.
     *
     * <p>{@code NoiseBasedChunkGenerator.stable(key)} is {@code Holder.is(ResourceKey)}, and a
     * resumed save's {@code level.dat} stores its overworld generator settings as an inline compound
     * rather than a registry-key string — so the decoded holder has no key and the check returns
     * false for every Latitude world that was ever saved and reopened. Consulting the save's own
     * Latitude marker (the same evidence the early hook pre-activates on) is what makes the two
     * hooks agree; the stale-flag clear must never fire on the stem check alone again.
     */
    private static void theResumedWorldVerdictIsNotDecidedByTheStemCheckAlone() throws IOException {
        String source = read(
                "src/main/java/com/example/globe/mixin/client/MinecraftClientStartIntegratedMixin.java");
        assertTrue(source.contains("RecreatedWorldMetadata.latitudePresetId("),
                "doWorldLoad must confirm against the save's own Latitude marker, not only the "
                        + "world stem — the stem check structurally cannot see a resumed Latitude world");
        assertTrue(source.contains("stemDetected || diskDetected"),
                "the verdict must be the OR of the stem check and the save marker; either alone has "
                        + "a blind spot");
        int verdict = source.indexOf("stemDetected || diskDetected");
        int clear = source.indexOf("LatitudeClientState.clearLatitudeLoadingState()");
        assertTrue(verdict > 0 && clear > verdict,
                "the stale-flag clear must sit behind the combined verdict — clearing on the stem "
                        + "check alone turns the pane off for the whole server-start phase of every "
                        + "resumed Latitude world");
    }

    /** Release builds keep the loading behavior but omit the screen-by-screen diagnostic probe. */
    private static void releaseBuildCarriesNoLoadingTraceHook() throws IOException {
        String source = read(
                "src/main/java/com/example/globe/mixin/client/LevelLoadingScreenLatitudeOverlayMixin.java");
        assertFalse(source.contains("method = \"setScreen\""),
                "the release mixin must not retain the screen-enumeration diagnostic hook");
        assertFalse(source.contains("[LAT][LOADUI]") || source.contains("[Latitude lifecycle]"),
                "the release loading path must not emit development lifecycle traces");
        assertFalse(source.contains("globe$lastReadinessWaitLogTick"),
                "the release loading path must not retain the log-only wait counter");
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
                "src/main/java/com/example/globe/mixin/client/ProgressScreenLatitudeOverlayMixin.java",
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
