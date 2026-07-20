package com.example.globe.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class PolarPresentationPolicyTest {
    private static final float EPSILON = 0.0001f;

    public static void main(String[] args) throws Exception {
        fogEnvelopeIsContinuousAndMonotonic();
        fogColorReachesCoolOffWhite();
        warningEpisodeIsPolewardFiniteAndRearmable();
        directLethalEntryArmsWithoutChangingNormalApproach();
        warningArbitrationFallsThroughToCanonicalEwText();
        outlineIsAnExplicitOnePixelRing();
        staticIntegrationProofsHold();
        EwPresentationPolicyTest.runAll();
        System.out.println("POLAR_PRESENTATION_POLICY_TEST_PASS");
    }

    private static void fogEnvelopeIsContinuousAndMonotonic() {
        assertNear(0.0f, PolarPresentationPolicy.fogIntensity(84.9), "84.9 has no polar fog");
        assertNear(0.0f, PolarPresentationPolicy.fogIntensity(85.0), "85 starts continuously at zero");
        assertNear(0.5f, PolarPresentationPolicy.fogIntensity(87.5), "87.5 is smoothstep midpoint");
        assertNear(1.0f, PolarPresentationPolicy.fogIntensity(90.0), "90 reaches full whiteout intensity");

        assertNear(-1.0f, PolarPresentationPolicy.fogEndDistance(85.0), "85 does not tighten fog distance yet");
        assertNear(49.0f, PolarPresentationPolicy.fogEndDistance(87.5), "87.5 has midpoint fog distance");
        assertNear(2.0f, PolarPresentationPolicy.fogEndDistance(90.0), "90 has near-whiteout fog distance");
        assertNear(-1.0f, PolarPresentationPolicy.fogEndDistance(84.9, 256.0f),
                "below 85 leaves the live baseline untouched");
        assertNear(-1.0f, PolarPresentationPolicy.fogEndDistance(85.0, 256.0f),
                "85 joins the live baseline continuously");
        assertNear(129.0f, PolarPresentationPolicy.fogEndDistance(87.5, 256.0f),
                "midpoint blends from the live fog baseline");
        assertNear(2.0f, PolarPresentationPolicy.fogEndDistance(90.0, 256.0f),
                "90 converges to the same near-whiteout end");

        assertNear(240.0f, PolarPresentationPolicy.polarFogStart(240.0f, 0.0f),
                "fog start is continuous at zero intensity");
        assertNear(PolarPresentationPolicy.FOG_NEAR_START_BLOCKS,
                PolarPresentationPolicy.polarFogStart(240.0f, 1.0f),
                "fog start converges near the camera at 90");

        float liveBaseline = 256.0f;
        float previousLiveEnd = liveBaseline;
        for (int step = 0; step <= 100; step++) {
            double latitude = 85.0 + step * 0.05;
            float candidate = PolarPresentationPolicy.fogEndDistance(latitude, liveBaseline);
            float effectiveEnd = candidate < 0.0f ? liveBaseline : candidate;
            assertTrue(effectiveEnd <= previousLiveEnd + EPSILON,
                    "live-baseline fog end must tighten monotonically at " + latitude);
            previousLiveEnd = effectiveEnd;
        }

        float previousIntensity = PolarPresentationPolicy.fogIntensity(84.0);
        float previousEnd = Float.MAX_VALUE;
        for (int step = 1; step <= 140; step++) {
            double latitude = 84.0 + step * 0.05;
            float intensity = PolarPresentationPolicy.fogIntensity(latitude);
            assertTrue(intensity + EPSILON >= previousIntensity,
                    "fog intensity must be monotonic at " + latitude);
            previousIntensity = intensity;

            float end = PolarPresentationPolicy.fogEndDistance(latitude);
            if (end >= 0.0f) {
                assertTrue(end <= previousEnd + EPSILON,
                        "fog end must tighten monotonically at " + latitude);
                previousEnd = end;
            }
        }
    }

    private static void fogColorReachesCoolOffWhite() {
        assertNear(0.25f,
                PolarPresentationPolicy.blendFogColorChannel(0.25f, 0.92f, 0.0f),
                "zero intensity preserves original color");
        assertNear(PolarPresentationPolicy.FOG_TARGET_RED,
                PolarPresentationPolicy.blendFogColorChannel(0.25f, PolarPresentationPolicy.FOG_TARGET_RED, 1.0f),
                "full intensity reaches target red");
        assertNear(PolarPresentationPolicy.FOG_TARGET_GREEN,
                PolarPresentationPolicy.blendFogColorChannel(0.25f, PolarPresentationPolicy.FOG_TARGET_GREEN, 1.0f),
                "full intensity reaches target green");
        assertNear(PolarPresentationPolicy.FOG_TARGET_BLUE,
                PolarPresentationPolicy.blendFogColorChannel(0.25f, PolarPresentationPolicy.FOG_TARGET_BLUE, 1.0f),
                "full intensity reaches target blue");
    }

    private static void warningEpisodeIsPolewardFiniteAndRearmable() {
        assertEquals(10, PolarPresentationPolicy.WARNING_FADE_IN_TICKS,
                "warning fades in over half a second");
        assertEquals(70, PolarPresentationPolicy.WARNING_HOLD_TICKS,
                "warning holds fully readable for three and a half seconds");
        assertEquals(20, PolarPresentationPolicy.WARNING_FADE_OUT_TICKS,
                "warning fades out over one second");
        assertEquals(100, PolarPresentationPolicy.WARNING_TOTAL_TICKS,
                "warning remains available for exactly five seconds");

        var episode = new PolarPresentationPolicy.PolarWarningEpisode();
        episode.update(0, 84.9, 0L);
        episode.update(1, 85.0, 1L);
        assertEquals(1, episode.highestTriggeredStageRank(), "first poleward stage entry triggers once");
        assertNear(0.0f, episode.alpha(1L), "episode starts at zero alpha");
        assertNear(1.0f, episode.alpha(11L), "episode reaches full alpha after fade-in");

        episode.update(1, 85.5, 11L);
        assertNear(1.0f, episode.alpha(12L), "same-stage movement does not restart fade-in");

        episode.update(2, 85.4, 13L);
        assertEquals(1, episode.highestTriggeredStageRank(), "equatorward escalation is suppressed");
        episode.update(2, 86.0, 14L);
        assertEquals(1, episode.highestTriggeredStageRank(), "suppressed stage does not retrigger without a new escalation");

        episode.update(3, 89.2, 15L);
        assertEquals(3, episode.highestTriggeredStageRank(), "new higher poleward stage triggers");
        episode.update(2, 88.0, 17L);
        episode.update(3, 89.2, 18L);
        assertEquals(3, episode.highestTriggeredStageRank(), "retreat and re-entry do not replay a seen stage");
        assertNear(1.0f, episode.alpha(25L), "retreat re-entry did not restart the episode");

        episode.update(0, 84.9, 30L);
        assertEquals(0, episode.highestTriggeredStageRank(), "below 85 rearms the warning family");
        assertNear(0.0f, episode.alpha(30L), "below 85 cancels the prior episode");
        episode.update(1, 85.0, 31L);
        assertEquals(1, episode.highestTriggeredStageRank(), "rearmed family triggers on next poleward entry");

        var finite = new PolarPresentationPolicy.PolarWarningEpisode();
        finite.update(0, 84.9, 100L);
        finite.update(1, 85.0, 101L);
        assertNear(1.0f, finite.alpha(101L + PolarPresentationPolicy.WARNING_FADE_IN_TICKS),
                "fade-in boundary is fully opaque");
        assertNear(1.0f,
                finite.alpha(101L + PolarPresentationPolicy.WARNING_FADE_IN_TICKS
                        + PolarPresentationPolicy.WARNING_HOLD_TICKS),
                "fade-out starts from fully opaque");
        assertTrue(finite.alpha(101L + PolarPresentationPolicy.WARNING_TOTAL_TICKS - 1L) > 0.0f,
                "final fade tick remains visible");
        assertNear(0.0f, finite.alpha(101L + PolarPresentationPolicy.WARNING_TOTAL_TICKS),
                "episode reaches exact zero after five seconds");
        assertEquals(0, finite.activeStageRank(101L + PolarPresentationPolicy.WARNING_TOTAL_TICKS),
                "expired episode has no active warning stage");
    }

    private static void directLethalEntryArmsWithoutChangingNormalApproach() {
        var directLethal = new PolarPresentationPolicy.PolarWarningEpisode();
        directLethal.update(4, 89.55, 500L);
        assertEquals(4, directLethal.highestTriggeredStageRank(),
                "direct load at any stage-four latitude arms the lethal warning");

        var directWarning = new PolarPresentationPolicy.PolarWarningEpisode();
        directWarning.update(1, 85.2, 600L);
        assertEquals(0, directWarning.highestTriggeredStageRank(),
                "ordinary direct load does not bypass approach-only warning semantics");
        directWarning.update(1, 85.3, 601L);
        assertEquals(0, directWarning.highestTriggeredStageRank(),
                "movement within the same stage still does not synthesize an entry");
    }

    private static void outlineIsAnExplicitOnePixelRing() {
        int[][] offsets = PolarPresentationPolicy.outlineOffsets();
        assertEquals(8, offsets.length, "outline has all eight neighboring pixels");

        Set<String> unique = new HashSet<>();
        for (int[] offset : offsets) {
            assertEquals(2, offset.length, "outline offset has x and y");
            assertTrue(Math.abs(offset[0]) <= 1 && Math.abs(offset[1]) <= 1,
                    "outline stays one pixel from the text");
            assertTrue(offset[0] != 0 || offset[1] != 0,
                    "outline does not overwrite the center glyph");
            unique.add(offset[0] + "," + offset[1]);
        }
        assertEquals(8, unique.size(), "outline offsets are unique");
    }

    private static void warningArbitrationFallsThroughToCanonicalEwText() throws IOException {
        assertEquals(0, EwPresentationPolicy.warningStageRank(500.0001),
                "east/west text is inactive just outside 500 blocks");
        assertEquals(1, EwPresentationPolicy.warningStageRank(500.0),
                "east/west level 1 begins at exactly 500 blocks");
        assertEquals(1, EwPresentationPolicy.warningStageRank(100.0001),
                "east/west level 1 remains active just outside 100 blocks");
        assertEquals(2, EwPresentationPolicy.warningStageRank(100.0),
                "east/west level 2 begins at exactly 100 blocks");

        var polarLethal = PolarPresentationPolicy.arbitrateWarning(4, 2);
        assertTrue(polarLethal.polar() && polarLethal.stageRank() == 4,
                "active polar lethal outranks east/west level 2");

        var ewCritical = PolarPresentationPolicy.arbitrateWarning(3, 2);
        assertTrue(!ewCritical.polar() && ewCritical.stageRank() == 2,
                "east/west level 2 outranks active nonlethal polar");

        var polarActive = PolarPresentationPolicy.arbitrateWarning(1, 1);
        assertTrue(polarActive.polar() && polarActive.stageRank() == 1,
                "active nonlethal polar outranks east/west level 1");

        var expiredPolar = PolarPresentationPolicy.arbitrateWarning(0, 1);
        assertTrue(!expiredPolar.polar() && expiredPolar.stageRank() == 1,
                "expired polar episode falls through to east/west level 1");

        String state = normalize(read("src/main/java/com/example/globe/client/GlobeClientState.java"));
        String overlay = read("src/main/java/com/example/globe/client/GlobeWarningOverlay.java");

        assertTrue(state.contains("public static EwStormStage ewTextStageForDistance(double distanceToBorder)"),
                "fixed-distance east/west text stage has one exposed canonical owner");
        assertTrue(state.contains("public static WarningState arbitrateWarning(PolarStage activePolar, EwStormStage ewStage)"),
                "warning precedence has one exposed canonical arbitration policy");
        assertTrue(state.contains("EwPresentationPolicy.warningStageRank(distanceToBorder)")
                        && state.contains("PolarPresentationPolicy.arbitrateWarning(polarRank(polar), ewRank(ew))"),
                "Minecraft-facing warning state maps the executable dependency-free policy");
        assertTrue(overlay.contains("GlobeClientState.arbitrateWarning(activePolarStage, activeEwStage)"),
                "overlay arbitrates from the finite active polar episode and canonical east/west text stage");
        assertTrue(overlay.contains("Sandstorm on the horizon, consider turning back.")
                        && overlay.contains("Zero visibility ahead. Turn around."),
                "east/west warning copy is exact");
        assertTrue(!overlay.contains("EW_SAND_WARN_TEMPLATE")
                        && !overlay.contains("EW_SAND_DANGER_TEMPLATE")
                        && !overlay.contains("String.format(base.getString()"),
                "east/west warning rendering has no unresolved direction placeholders");
    }

    private static void staticIntegrationProofsHold() throws IOException {
        String globeMod = read("src/main/java/com/example/globe/GlobeMod.java");
        assertTrue(!globeMod.contains("MobEffects.BLINDNESS"),
                "server polar effects no longer apply blindness");

        String state = normalize(read("src/main/java/com/example/globe/client/GlobeClientState.java"));
        String ewPolicy = normalize(read("src/main/java/com/example/globe/client/EwPresentationPolicy.java"));
        assertTrue(ewPolicy.contains("FOG_START_DISTANCE_BLOCKS = 400.0")
                        && ewPolicy.contains("FOG_FULL_DISTANCE_BLOCKS = 50.0"),
                "east/west fog now uses the approved 400-to-50 envelope");
        assertTrue(ewPolicy.contains("t * t * (3.0 - 2.0 * t)"),
                "east/west intensity uses the approved smoothstep");
        assertTrue(ewPolicy.contains("FOG_NEAR_START_BLOCKS = 0.5f")
                        && ewPolicy.contains("FOG_NEAR_END_BLOCKS = 12.0f"),
                "east/west fog converges on the approved near-whiteout distances");

        String mixin = read("src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java");
        assertEquals(1, countOccurrences(mixin, "@Inject(method = \"setupFog\""),
                "exactly one 26.2 fog hook targets the mapped setupFog method");
        assertTrue(mixin.contains("computeEwFogEnd") && mixin.contains("computePoleFogEnd"),
                "legacy east/west path and isolated polar path both remain present");
        assertTrue(mixin.contains("latitude$polarEnd") && mixin.contains("latitude$blendPolarFogColor"),
                "polar hook tightens only its own distance and blends the mapped fog color field");

        String injectedHandler = methodSection(mixin, "private void latitude$applyFog(");
        int ewCall = injectedHandler.indexOf("latitude$applyEwFog(");
        int polarCall = injectedHandler.indexOf("latitude$applyPolarFog(");
        assertTrue(ewCall >= 0 && polarCall > ewCall,
                "single setupFog handler applies east/west fog before polar fog");
        assertTrue(injectedHandler.contains("client == null || client.level == null || client.player == null")
                        && injectedHandler.contains("camera.getFluidInCamera() != FogType.NONE"),
                "single setupFog handler validates the client and atmospheric fog once");

        String ewSection = methodSection(mixin, "private static void latitude$applyEwFog(")
                + methodSection(mixin, "private static float latitude$tightenStart(")
                + methodSection(mixin, "private static float latitude$tightenEnd(");
        assertTrue(ewSection.contains("computeEwFogEnd")
                        && ewSection.contains("computeEwFogStart"),
                "east/west helper consumes the approved start and end tightening policy");
        assertTrue(!ewSection.contains("computePoleFogEnd")
                        && !ewSection.contains("latitude$polarEnd")
                        && !ewSection.contains("latitude$blendPolarFogColor"),
                "east/west helper cannot activate or alter polar fog");

        String polarSection = methodSection(mixin, "private static void latitude$applyPolarFog(")
                + methodSection(mixin, "private static void latitude$tightenPolarFogDistances(")
                + methodSection(mixin, "private static float latitude$polarEnd(")
                + methodSection(mixin, "private static void latitude$blendPolarFogColor(");
        assertTrue(polarSection.contains("computePoleFogEnd")
                        && polarSection.contains("latitude$polarEnd")
                        && polarSection.contains("latitude$blendPolarFogColor"),
                "polar helper retains its distance and color policy");
        assertTrue(!polarSection.contains("computeEwFogEnd")
                        && !polarSection.contains("ewIntensity01"),
                "polar helper cannot activate or alter east/west fog");
        assertTrue(injectedHandler.contains("GlobeClientState.evaluate(client)")
                        && injectedHandler.contains("if (!eval.active())")
                        && polarSection.contains("GlobeClientState.Eval eval")
                        && polarSection.contains("if (!eval.surfaceOk())"),
                "one shared evaluation gates Latitude fog before the polar surface check");

        String overlay = read("src/main/java/com/example/globe/client/GlobeWarningOverlay.java");
        assertTrue(overlay.contains("POLAR_WARNING_EPISODE.update"),
                "overlay updates the polar episode gate");
        assertTrue(overlay.contains("drawCenteredPolarWarning") && overlay.contains("outlineOffsets()"),
                "polar text uses explicit outline draws");
        String poleTextSection = methodSection(overlay, "private static Component poleTextForStage(");
        assertTrue(!poleTextSection.contains("ChatFormatting.BOLD"),
                "danger and lethal polar warnings use a non-bold red fill");
        String polarDrawSection = methodSection(overlay, "private static void drawCenteredPolarWarning(");
        assertTrue(polarDrawSection.contains("Component.literal(text.getString())")
                        && polarDrawSection.contains("POLAR_KEYLINE_RGB")
                        && polarDrawSection.contains("keylineText"),
                "polar warning keyline is a styleless dark component");
        assertTrue(countOccurrences(polarDrawSection, ", false);") >= 2,
                "polar keyline and fill both render without drop shadows");
        assertTrue(overlay.contains("EW_WARNING_EPISODE.alpha")
                        && overlay.contains("drawCenteredEwWarning"),
                "east/west warning uses its finite fade and explicit keyline path");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String methodSection(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method is present: " + signature);
        int openBrace = source.indexOf('{', start);
        assertTrue(openBrace >= 0, "method body is present: " + signature);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("method body is balanced: " + signature);
    }

    private static void assertNear(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
