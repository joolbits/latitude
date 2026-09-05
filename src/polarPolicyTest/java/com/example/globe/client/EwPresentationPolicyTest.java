package com.example.globe.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class EwPresentationPolicyTest {
    private static final float EPSILON = 0.0001f;

    private EwPresentationPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("EW_PRESENTATION_POLICY_TEST_PASS");
    }

    public static void runAll() throws Exception {
        fogEnvelopeUsesThe400To50Smoothstep();
        warmSandHazeStopsAtTheSubpolarBoundary();
        borderDistanceIsSymmetricThroughTheCanonicalMinFormula();
        warningCopyNamesTheActualWorldEdge();
        particlesUseTheFixedFogEnvelopeAndActualBorder();
        warningEpisodesAreFiniteDirectionalAndRearmable();
        pausedCrossingAndDirectEntryFollowStrictApproachSemantics();
        outlineIsAnExplicitOnePixelRing();
        shelterRequiresDeepContinuousZeroSky();
        confirmedShelterPausesEpisodeTime();
        warningClockResyncPreservesEpisodeAge();
        staticIntegrationProofsHold();
    }

    private static void warmSandHazeStopsAtTheSubpolarBoundary() {
        assertTrue(EwPresentationPolicy.usesWarmSandHaze(0.0),
                "tropical storms use the warm sand haze");
        assertTrue(EwPresentationPolicy.usesWarmSandHaze(49.999),
                "temperate storms retain the warm sand haze below 50 degrees");
        assertTrue(!EwPresentationPolicy.usesWarmSandHaze(50.0),
                "subpolar storms do not inherit the warm sand color at 50 degrees");
        assertTrue(!EwPresentationPolicy.usesWarmSandHaze(85.0),
                "polar presentation retains its independent fog color");
        assertTrue(!EwPresentationPolicy.usesWarmSandHaze(Double.NaN),
                "unknown latitude fails open to the live fog color");

        assertNear(0.0f, EwPresentationPolicy.sandHazeColorIntensity(400.0, 1.0f, 25.0),
                "the haze color joins the live baseline continuously at 400 blocks");
        assertNear(1.0f, EwPresentationPolicy.sandHazeColorIntensity(50.0, 1.0f, 49.999),
                "full temperate storm reaches the brown target color");
        assertNear(0.0f, EwPresentationPolicy.sandHazeColorIntensity(50.0, 1.0f, 50.0),
                "subpolar latitude disables the brown color even at full EW fog");
        assertNear(0.0f, EwPresentationPolicy.sandHazeColorIntensity(50.0, 0.0f, 25.0),
                "confirmed shelter removes the brown haze with the shared presentation visibility");
        assertNear(EwPresentationPolicy.SAND_HAZE_TARGET_RED,
                EwPresentationPolicy.blendFogColorChannel(
                        0.2f, EwPresentationPolicy.SAND_HAZE_TARGET_RED, 1.0f),
                "full intensity reaches the brown target red");
        assertNear(0.2f,
                EwPresentationPolicy.blendFogColorChannel(
                        0.2f, EwPresentationPolicy.SAND_HAZE_TARGET_BLUE, 0.0f),
                "zero intensity preserves the live fog color");
    }

    private static void borderDistanceIsSymmetricThroughTheCanonicalMinFormula() {
        assertNear(500.0f, (float) EwPresentationPolicy.distanceToNearestBorder(-3750.0, 3750.0, -3250.0),
                "west approach uses the nearest-border distance");
        assertNear(500.0f, (float) EwPresentationPolicy.distanceToNearestBorder(-3750.0, 3750.0, 3250.0),
                "east approach mirrors west exactly");
        assertNear(100.0f, (float) EwPresentationPolicy.distanceToNearestBorder(1250.0, 8750.0, 1350.0),
                "shifted west border uses the canonical min formula");
        assertNear(100.0f, (float) EwPresentationPolicy.distanceToNearestBorder(1250.0, 8750.0, 8650.0),
                "shifted east border remains symmetric");
    }

    private static void fogEnvelopeUsesThe400To50Smoothstep() {
        assertNear(0.0f, EwPresentationPolicy.fogIntensity(400.0001),
                "fog is untouched outside 400 blocks");
        assertNear(0.0f, EwPresentationPolicy.fogIntensity(400.0),
                "fog joins the live baseline continuously at 400");
        assertNear(0.5f, EwPresentationPolicy.fogIntensity(225.0),
                "fog uses smoothstep at the 400-to-50 midpoint");
        assertTrue(EwPresentationPolicy.fogIntensity(100.0) > 0.5f,
                "fog is already dense at the level-two warning");
        assertNear(1.0f, EwPresentationPolicy.fogIntensity(50.0),
                "fog reaches full intensity at 50");
        assertNear(1.0f, EwPresentationPolicy.fogIntensity(0.0),
                "fog remains full inside 50");

        assertNear(-1.0f, EwPresentationPolicy.fogEndDistance(400.0, 256.0f),
                "400 leaves the live fog end untouched");
        assertNear(134.0f, EwPresentationPolicy.fogEndDistance(225.0, 256.0f),
                "midpoint blends from the live fog end to 12");
        assertNear(12.0f, EwPresentationPolicy.fogEndDistance(50.0, 256.0f),
                "full fog tightens end to 12");
        assertNear(240.0f, EwPresentationPolicy.fogStartDistance(400.0, 240.0f),
                "400 leaves the live fog start untouched");
        assertNear(120.25f, EwPresentationPolicy.fogStartDistance(225.0, 240.0f),
                "midpoint blends start toward 0.5");
        assertNear(0.5f, EwPresentationPolicy.fogStartDistance(50.0, 240.0f),
                "full fog tightens start to 0.5");
        assertNear(-1.0f, EwPresentationPolicy.fogEndDistance(50.0, 256.0f, 0.0f),
                "confirmed fully hidden shelter restores the live fog end");
        assertNear(240.0f, EwPresentationPolicy.fogStartDistance(50.0, 240.0f, 0.0f),
                "confirmed fully hidden shelter restores the live fog start");

        float previous = 0.0f;
        for (int distance = 400; distance >= 0; distance--) {
            float intensity = EwPresentationPolicy.fogIntensity(distance);
            assertTrue(intensity + EPSILON >= previous,
                    "fog intensity is monotonic toward the border at " + distance);
            previous = intensity;
        }
    }

    private static void particlesUseTheFixedFogEnvelopeAndActualBorder() {
        assertNear(0.0f, EwPresentationPolicy.particleIntensity(400.0, 1.0f),
                "particles begin strictly inside the fixed 400-block envelope");
        assertNear(0.0f, EwPresentationPolicy.particleIntensity(500.0, 1.0f),
                "warning distance does not create particles outside the fog envelope");
        assertNear(0.0f, EwPresentationPolicy.particleIntensity(50.0, 0.0f),
                "shared shelter visibility suppresses particles");
        assertEquals(0, EwPresentationPolicy.particleBudget(20, 400.0, 1.0f),
                "particle budget is zero at the exact continuous envelope endpoint");
        assertEquals(1, EwPresentationPolicy.leadingSandParticleBudget(20, 399.999, 1.0f),
                "the first in-envelope sample emits one sparse sand particle");
        assertEquals(0, EwPresentationPolicy.particleBudget(7, 399.999, 1.0f),
                "haze does not double the sparse leading particle");
        assertEquals(20, EwPresentationPolicy.particleBudget(20, 50.0, 1.0f),
                "particle budget reaches its maximum at full fog intensity");

        float previousIntensity = 0.0f;
        int previousBudget = 0;
        for (int distance = 400; distance >= 0; distance--) {
            float intensity = EwPresentationPolicy.particleIntensity(distance, 1.0f);
            int budget = EwPresentationPolicy.particleBudget(20, distance, 1.0f);
            assertTrue(intensity + EPSILON >= previousIntensity,
                    "particle intensity is monotonic toward the border at " + distance);
            assertTrue(budget >= previousBudget,
                    "particle budget is monotonic toward the border at " + distance);
            previousIntensity = intensity;
            previousBudget = budget;
        }

        assertNear(0.1f, (float) EwPresentationPolicy.windTowardInterior(-3750.0, 3750.0, -3700.0, 0.1),
                "west-border wind blows east toward the interior");
        assertNear(-0.1f, (float) EwPresentationPolicy.windTowardInterior(-3750.0, 3750.0, 3700.0, 0.1),
                "east-border wind blows west toward the interior");
        assertNear(0.1f, (float) EwPresentationPolicy.windTowardInterior(1250.0, 8750.0, 1300.0, 0.1),
                "shifted west border retains inward wind");
        assertNear(-0.1f, (float) EwPresentationPolicy.windTowardInterior(1250.0, 8750.0, 8700.0, 0.1),
                "shifted east border retains inward wind");
        assertNear(0.0f, (float) EwPresentationPolicy.windTowardInterior(1250.0, 8750.0, 5000.0, 0.1),
                "border-center tie has no arbitrary sign-biased wind");
    }

    private static void warningEpisodesAreFiniteDirectionalAndRearmable() {
        assertEquals(20, EwPresentationPolicy.WARNING_FADE_IN_TICKS,
                "warning fades in for one second");
        assertEquals(100, EwPresentationPolicy.WARNING_HOLD_TICKS,
                "warning holds for five seconds");
        assertEquals(20, EwPresentationPolicy.WARNING_FADE_OUT_TICKS,
                "warning fades out for one second");
        assertEquals(140, EwPresentationPolicy.WARNING_TOTAL_TICKS,
                "warning episode lasts exactly seven seconds");

        assertEquals(0, EwPresentationPolicy.warningStageRank(400.0),
                "advisory is inactive before particles begin");
        assertEquals(1, EwPresentationPolicy.warningStageRank(399.999),
                "advisory begins with the first storm particles");
        assertEquals(1, EwPresentationPolicy.warningStageRank(100.0001),
                "advisory remains selected just outside 100");
        assertEquals(2, EwPresentationPolicy.warningStageRank(100.0),
                "danger begins at 100");

        var episode = new EwPresentationPolicy.WarningEpisode();
        episode.update(0, 400.0, 0L, false);
        episode.update(1, 399.999, 1L, false);
        assertEquals(1, episode.highestTriggeredStageRank(),
                "borderward entry triggers the advisory once");
        assertNear(0.0f, episode.alpha(1L), "episode begins at zero alpha");
        assertNear(1.0f, episode.alpha(21L), "episode reaches full alpha");

        episode.update(1, 350.0, 22L, false);
        episode.update(1, 375.0, 23L, false);
        episode.update(1, 350.0, 24L, false);
        assertEquals(1, episode.highestTriggeredStageRank(),
                "retreat and same-stage re-entry do not retrigger");
        assertNear(1.0f, episode.alpha(24L),
                "retreat did not restart the advisory clock");

        episode.update(2, 99.0, 25L, false);
        assertEquals(2, episode.highestTriggeredStageRank(),
                "borderward escalation triggers danger");
        assertEquals(2, episode.activeStageRank(26L),
                "danger replaces the advisory episode");
        episode.update(1, 101.0, 26L, false);
        episode.update(2, 99.0, 27L, false);
        assertNear(0.1f, episode.alpha(27L),
                "retreat and danger re-entry do not restart danger");

        episode.update(0, 400.0, 28L, false);
        assertEquals(0, episode.highestTriggeredStageRank(),
                "moving beyond the particle threshold rearms the warning family");
        episode.update(1, 399.999, 29L, false);
        assertEquals(1, episode.highestTriggeredStageRank(),
                "rearmed advisory triggers on the next borderward entry");

        assertTrue(episode.alpha(29L + EwPresentationPolicy.WARNING_TOTAL_TICKS - 1L) > 0.0f,
                "last fade tick remains visible");
        assertNear(0.0f, episode.alpha(29L + EwPresentationPolicy.WARNING_TOTAL_TICKS),
                "episode ends exactly after seven seconds");
    }

    private static void pausedCrossingAndDirectEntryFollowStrictApproachSemantics() {
        var shelteredCrossing = new EwPresentationPolicy.WarningEpisode();
        shelteredCrossing.update(0, 400.0, 0L, false);
        shelteredCrossing.update(1, 399.999, 1L, true);
        assertEquals(0, shelteredCrossing.highestTriggeredStageRank(),
                "a crossing while confirmed hidden does not burn the warning");
        shelteredCrossing.update(1, 399.0, 2L, false);
        assertEquals(1, shelteredCrossing.highestTriggeredStageRank(),
                "surface return resumes and consumes the pending borderward crossing");

        var directDanger = new EwPresentationPolicy.WarningEpisode();
        directDanger.update(2, 99.0, 100L, false);
        assertEquals(0, directDanger.highestTriggeredStageRank(),
                "direct initial level-two entry is intentionally silent");
        directDanger.update(2, 98.0, 101L, false);
        assertEquals(0, directDanger.highestTriggeredStageRank(),
                "same-stage motion cannot synthesize a missed approach crossing");
    }

    private static void outlineIsAnExplicitOnePixelRing() {
        int[][] offsets = EwPresentationPolicy.outlineOffsets();
        assertEquals(8, offsets.length, "outline has all eight neighbors");
        Set<String> unique = new HashSet<>();
        for (int[] offset : offsets) {
            assertEquals(2, offset.length, "outline entry has x and y");
            assertTrue(Math.abs(offset[0]) <= 1 && Math.abs(offset[1]) <= 1,
                    "outline remains one pixel from the glyph");
            assertTrue(offset[0] != 0 || offset[1] != 0,
                    "outline does not replace the glyph");
            unique.add(offset[0] + "," + offset[1]);
        }
        assertEquals(8, unique.size(), "outline offsets are unique");
    }

    private static void shelterRequiresDeepContinuousZeroSky() {
        assertNear(10.0f / 13.0f, EwPresentationPolicy.exposureFraction(10, 13),
                "13-sample exposure preserves the committed concept");
        assertTrue(!EwPresentationPolicy.isHiddenCandidate(62, 63, 0),
                "surface tree or arch remains fully presented even with a blocked center");
        assertTrue(!EwPresentationPolicy.isHiddenCandidate(60, 63, 1),
                "deep space with any sky sample is not sealed");
        assertTrue(EwPresentationPolicy.isHiddenCandidate(60, 63, 0),
                "only deep zero-sky space is a hidden candidate");

        var shelter = new EwPresentationPolicy.ShelterState();
        for (int tick = 1; tick < EwPresentationPolicy.HIDDEN_CONFIRM_TICKS; tick++) {
            shelter.update(60, 63, 0);
            assertNear(1.0f, shelter.visibility(),
                    "candidate shelter does not fade before 20 continuous ticks");
            assertTrue(!shelter.pauseEpisode(),
                    "candidate shelter does not pause warnings before confirmation");
        }

        shelter.update(60, 63, 0);
        assertTrue(shelter.pauseEpisode(), "twentieth hidden tick confirms shelter");
        assertNear(1.0f, shelter.visibility(), "fade begins from full visibility");
        for (int tick = 0; tick < EwPresentationPolicy.HIDDEN_FADE_TICKS; tick++) {
            shelter.update(60, 63, 0);
        }
        assertNear(0.0f, shelter.visibility(), "confirmed shelter fades fully over 20 ticks");
        assertEquals(0, EwPresentationPolicy.particleBudget(20, shelter.visibility()),
                "particle budget uses the same hidden state");

        for (int tick = 0; tick < EwPresentationPolicy.VISIBLE_RESTORE_TICKS; tick++) {
            shelter.update(62, 63, 0);
        }
        assertNear(1.0f, shelter.visibility(), "presentation restores fully within five ticks");
        assertTrue(!shelter.pauseEpisode(), "leaving confirmed shelter resumes the episode");
        assertEquals(20, EwPresentationPolicy.particleBudget(20, shelter.visibility()),
                "particles restore from the same state");
    }

    private static void confirmedShelterPausesEpisodeTime() {
        var episode = new EwPresentationPolicy.WarningEpisode();
        episode.update(0, 400.0, 0L, false);
        episode.update(1, 399.999, 1L, false);
        episode.update(1, 399.0, 21L, false);
        assertNear(1.0f, episode.alpha(21L), "episode is fully visible before shelter");

        episode.update(1, 399.0, 121L, true);
        episode.update(1, 399.0, 221L, true);
        assertNear(1.0f, episode.alpha(221L),
                "confirmed shelter pauses rather than burns the episode");
        episode.update(1, 399.0, 222L, false);
        assertNear(1.0f, episode.alpha(222L), "episode resumes after shelter");
    }

    private static void warningClockResyncPreservesEpisodeAge() {
        var episode = new EwPresentationPolicy.WarningEpisode();
        episode.update(0, 400.0, 100L, false);
        episode.update(1, 399.999, 101L, false);
        float beforeRollback = episode.alpha(121L);
        episode.shiftClock(-80L);
        assertNear(beforeRollback, episode.alpha(41L),
                "same-level clock rollback preserves the active warning age");
        assertEquals(1, episode.highestTriggeredStageRank(),
                "clock resync does not rearm a warning episode");
    }

    private static void warningCopyNamesTheActualWorldEdge() {
        // Shifted borders catch implementations that mistake the sign of X for the edge.
        for (double[] borders : new double[][]{{-10000, 10000}, {1000, 3000}, {-3000, -1000}}) {
            for (boolean east : new boolean[]{false, true}) {
                String direction = east ? "east" : "west";
                double edge = east ? borders[1] : borders[0];
                for (double inward : new double[]{300, 50, 0, -10}) {
                    double x = edge + (east ? -inward : inward);
                    assertTrue(("Storms to the " + direction + ". Low visibility; consider turning back.")
                                    .equals(EwPresentationPolicy.warningText(1, borders[0], borders[1], x)),
                            "advisory names the nearby " + direction + " edge");
                    assertTrue(("Zero visibility to the " + direction + ". Turn around.")
                                    .equals(EwPresentationPolicy.warningText(2, borders[0], borders[1], x)),
                            "danger names the same " + direction + " edge, including at or beyond it");
                    assertTrue(EwPresentationPolicy.warningText(0, borders[0], borders[1], x) == null,
                            "no warning stage remains silent");
                }
            }
        }
    }

    private static void staticIntegrationProofsHold() throws IOException {
        String state = normalize(read("src/main/java/com/example/globe/client/GlobeClientState.java"));
        String overlay = read("src/main/java/com/example/globe/client/GlobeWarningOverlay.java");
        String zoneTitle = read("src/main/java/com/example/globe/client/ZoneEnterTitleOverlay.java");
        String haze = read("src/main/java/com/example/globe/client/EwSandstormOverlayHud.java");
        // The fog logic itself no longer lives in the mixin on this target: setupFog uploads its
        // colour before returning, so the work is split across two injections and shared here.
        String fog = read("src/main/java/com/example/globe/client/LatitudeFogPresentation.java");
        String client = read("src/main/java/com/example/globe/GlobeModClient.java");
        String mixins = read("src/main/resources/globe.mixins.json");

        assertTrue(state.contains("EwPresentationPolicy.fogIntensity")
                        && state.contains("EwPresentationPolicy.ShelterState"),
                "Minecraft state delegates fog and shelter policy to the pure owner");
        assertTrue(state.contains("EXPOSURE_OFFSETS")
                        && state.contains("EwPresentationPolicy.SKY_SAMPLE_COUNT")
                        && state.contains("EXPOSURE_RECOMPUTE_TICKS = 5"),
                "live exposure shim uses the exact 13-sample five-tick cache policy");
        assertTrue(overlay.contains("EwPresentationPolicy.warningText(")
                        && overlay.contains("ewRank(stage), border.getMinX(), border.getMaxX(), client.player.getX()")
                        && overlay.contains("ewTextForStage(stage, client)"),
                "warning copy receives the actual world borders and player position");
        assertTrue(!overlay.contains("Sandstorm on the horizon"),
                "warning copy remains truthful outside sandstorm climates");
        assertTrue(!overlay.contains("ChatFormatting.BOLD"),
                "east/west warnings are explicitly non-bold");
        assertTrue(overlay.contains("EW_WARNING_EPISODE.update")
                        && overlay.contains("ewPresentationVisibility"),
                "overlay consumes the finite episode and shared shelter state");
        assertTrue(overlay.contains("drawCenteredEwWarning")
                        && overlay.contains("EwPresentationPolicy.outlineOffsets()"),
                "east/west warnings use the explicit keyline path");
        assertTrue(client.contains("leadingSandParticleBudget(20, distanceToBorder, presentationVisibility)")
                        && client.contains("particleBudget(7, distanceToBorder, presentationVisibility)"),
                "the live path emits one leading sand particle without matching haze");
        String draw = methodSection(overlay, "private static void drawCenteredEwWarning(");
        assertTrue(countOccurrences(draw, ", false);") >= 2,
                "keyline and fill both render without shadows");

        assertTrue(!haze.contains(".fill("),
                "flat tan full-screen haze owner is neutralized");
        assertTrue(fog.contains("computeEwFogStart")
                        && fog.contains("computeEwFogEnd")
                        && fog.contains("sandHazeColorIntensity")
                        && fog.contains("SAND_HAZE_TARGET_RED"),
                "depth fog tightens from the live baseline and uses the bounded brown-color policy");
        String fogHandler = methodSection(fog, "private static Gate gate(");
        assertEquals(1, countOccurrences(fog, "GlobeClientState.evaluate(client)"),
                "fog evaluates Latitude identity exactly once, in the shared gate");
        assertTrue(fogHandler.contains("if (!GlobeClientState.isGlobeWorld())")
                        && fogHandler.indexOf("if (!GlobeClientState.isGlobeWorld())")
                        < fogHandler.indexOf("GlobeClientState.evaluate(client)"),
                "fog requires authoritative Latitude world identity before evaluation");
        assertTrue(fogHandler.contains("if (!eval.active())"),
                "the shared gate closes on inactive worlds");
        String distancePass = methodSection(fog, "public static void applyDistances(");
        assertTrue(distancePass.indexOf("if (gate == null)")
                        < distancePass.indexOf("applyEwDistances("),
                "non-Latitude worlds fail open before east/west fog is applied");
        String colorPass = methodSection(fog, "public static void applyColor(");
        assertTrue(colorPass.indexOf("if (gate == null") < colorPass.indexOf("blendSandHazeColor("),
                "non-Latitude worlds fail open before east/west fog colour is blended");
        String polarFog = methodSection(fog, "private static void applyPolarDistances(");
        assertTrue(!polarFog.contains("GlobeClientState.evaluate(client)")
                        && polarFog.contains("GlobeClientState.Eval eval"),
                "polar fog consumes the same evaluation without a contradictory second gate");
        assertTrue(client.contains("ewPresentationVisibility")
                        && client.contains("EwPresentationPolicy.particleBudget"),
                "storm particles consume the same shelter visibility");
        String particleTick = methodSection(client, "private static void polarCapClientTick(");
        String ewParticleTick = methodSection(client, "private static void ewSandstormClientTick(");
        assertTrue(!particleTick.contains("computeEwStormStage")
                        && !ewParticleTick.contains("computeEwStormStage")
                        && particleTick.contains("EwPresentationPolicy.particleIntensity")
                        && ewParticleTick.contains("EwPresentationPolicy.particleBudget")
                        && ewParticleTick.contains("border.getMinX()")
                        && ewParticleTick.contains("border.getMaxX()")
                        && ewParticleTick.contains("EwPresentationPolicy.windTowardInterior"),
                "EW particles use the fixed fog envelope, shared shelter, and actual-border wind");

        int updateIndex = overlay.indexOf("EW_WARNING_EPISODE.update(");
        int visibilityConfigIndex = overlay.indexOf("if (!LatitudeConfig.showWarningMessages)");
        assertTrue(updateIndex >= 0 && visibilityConfigIndex > updateIndex,
                "warning config off still advances and consumes the episode before suppressing drawing");
        assertTrue(overlay.contains("lastWarningLevel != client.level")
                        && overlay.contains("worldTime < lastWarningWorldTime")
                        && overlay.contains("resyncWorldClock(worldTime)"),
                "same-level clock rollback uses a timeline resync instead of a world-entry reset");
        String overlayRender = methodSection(overlay, "public static void render(");
        String resyncWorldClock = methodSection(overlay, "private static void resyncWorldClock(");
        String resetWorldEntry = methodSection(overlay, "private static void resetWorldEntryState(");
        String clearWarningState = methodSection(overlay, "private static void clearWarningWorldState(");
        String disconnectReset = methodSection(overlay, "public static void resetForDisconnect(");
        assertEquals(2, countOccurrences(overlayRender, "clearWarningWorldState();"),
                "null client and disconnected player or level both clear warning state");
        assertTrue(!overlayRender.contains("DEBUG_DISABLE_WARNINGS")
                        && !overlayRender.contains("Boolean.getBoolean("),
                "release warning rendering has no debug switch that can bypass disconnect cleanup");
        assertTrue(clearWarningState.contains("resetForDisconnect()"),
                "render-time null cleanup delegates to the explicit lifecycle reset");
        assertTrue(disconnectReset.contains("lastWarningLevel = null")
                        && disconnectReset.contains("lastWarningWorldTime = Long.MIN_VALUE")
                        && disconnectReset.contains("resetWorldEntryState(-1L)"),
                "explicit disconnect reset clears static level identity, clock, episodes, and zone state");
        assertTrue(resyncWorldClock.contains("POLAR_WARNING_EPISODE.shiftClock")
                        && resyncWorldClock.contains("EW_WARNING_EPISODE.shiftClock")
                        && resyncWorldClock.contains("ZoneEnterTitleOverlay.shiftClock")
                        && !resyncWorldClock.contains("lastZoneKey = null")
                        && !resyncWorldClock.contains("resetWorldEntryState"),
                "clock resync shifts presentation timelines without forgetting the active zone");
        assertTrue(resetWorldEntry.contains("lastZoneKey = null")
                        && resetWorldEntry.contains("POLAR_WARNING_EPISODE.reset()")
                        && resetWorldEntry.contains("EW_WARNING_EPISODE.reset()")
                        && resetWorldEntry.contains("ZoneEnterTitleOverlay.reset()"),
                "true world-entry reset clears the zone, warning families, and active title");
        assertTrue(zoneTitle.contains("public static void shiftClock(long deltaTicks)")
                        && zoneTitle.contains("startWorldTime += deltaTicks")
                        && zoneTitle.contains("endWorldTime += deltaTicks"),
                "active zone-title timing follows the resynchronized world clock");
        String resetTitle = methodSection(zoneTitle, "public static void reset(");
        assertTrue(resetTitle.contains("title = null")
                        && resetTitle.contains("startWorldTime = Long.MIN_VALUE")
                        && resetTitle.contains("endWorldTime = Long.MIN_VALUE"),
                "true world changes and disconnects discard any title from the prior level");
        String disconnectEvent = methodSection(client,
                "ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->");
        assertTrue(disconnectEvent.contains("GlobeWarningOverlay.resetForDisconnect()")
                        && disconnectEvent.contains("GlobeClientState.resetForDisconnect()")
                        && disconnectEvent.indexOf("GlobeWarningOverlay.resetForDisconnect()")
                        < disconnectEvent.indexOf("GlobeClientState.resetForDisconnect()")
                        && !disconnectEvent.contains("GlobeWarningOverlay.render("),
                "disconnect event directly resets overlay and shared state without requiring render");

        assertEquals(0, countOccurrences(
                        mixins,
                        "client.compat.sodium.RenderSectionManagerVisibilityMixin"),
                "dead Sodium isSectionVisible compatibility mixin is not registered");
        assertTrue(Files.notExists(Path.of(
                        "src/main/java/com/example/globe/mixin/client/compat/sodium/RenderSectionManagerVisibilityMixin.java")),
                "dead Sodium isSectionVisible compatibility source is removed");
        // Both halves of the fog design now live in one mixin on FogRenderer, which must keep the
        // sub-default priority, or Sodium's default-priority mixins snapshot the fog state before
        // Latitude has touched it.
        assertTrue(read("src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java")
                        .contains("@Mixin(value = FogRenderer.class, priority = 900)"),
                "Latitude blends fog colour and tightens distances before Sodium's "
                        + "default-priority snapshot");
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
