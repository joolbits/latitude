package com.example.globe.dev;

import com.example.globe.client.PolarPresentationPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public final class DevToolPolicyTest {
    private static int assertions;

    private DevToolPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        sanitizationIsStableAndTraversalSafe();
        packagedTestIdentityAndProbePolicyFailClosed();
        latitudeMappingUsesCenterBoundsAndBlockCenters();
        productionBorderRadiusOwnsCommandAndEvidenceLatitude();
        movementAndTransitionSamplingAreDeterministic();
        traceClockPreservesSameDimensionContinuity();
        caseSessionIsAppendOnlyOrderedAndExplicitlyClosed();
        structureAtlasExporterStaysDevOnlyAndSharesTheLocateEvaluators();
        System.out.println("DEV_TOOL_POLICY_TEST_PASS assertions=" + assertions);
    }

    /**
     * The structure atlas exporter must stay dev-only and must judge candidates through the same
     * evaluators the locate command and placement guard use — a second evaluator would drift, and
     * a dot on the atlas would stop meaning what a locate answer means.
     */
    private static void structureAtlasExporterStaysDevOnlyAndSharesTheLocateEvaluators()
            throws Exception {
        String exporter = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/globe/dev/StructureAtlasExporter.java"))
                .replaceAll("\\s+", " ");
        expectTrue(exporter.contains("package com.example.globe.dev;"),
                "exporter lives in the dev package, which release artifacts exclude");
        expectTrue(exporter.contains("System.getProperty(PROP_KEY) == null"),
                "exporter registers nothing unless its property is set");
        expectTrue(exporter.contains(
                "LatitudeStructureLocateService.sweepStructureCandidatesForAtlas("),
                "exporter judges candidates through the shared locate admission sweep");

        String census = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/globe/dev/DistributionCensusExporter.java"))
                .replaceAll("\\s+", " ");
        expectTrue(census.contains("package com.example.globe.dev;"),
                "the distribution census lives in the release-excluded dev package");
        expectTrue(census.contains("System.getProperty(PROP_KEY) == null"),
                "the distribution census registers nothing unless its property is set");
        expectTrue(census.contains("distribution census REFUSED: Latitude does not own"),
                "a census on a non-Latitude world refuses with a reason rather than reporting "
                        + "vanilla's distribution as Latitude's");
        expectTrue(census.contains("bandProvinceBiome"),
                "the census emits per-band per-province counts, not just global shares — the "
                        + "global figure alone cannot say WHERE a biome fails to appear");
        expectTrue(census.contains("hotspotByBand") && census.contains("hotspotBiomes"),
                "the census measures arid-hotspot area against what that area actually became");

        String globeMod = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/globe/GlobeMod.java")).replaceAll("\\s+", " ");
        expectTrue(globeMod.contains(
                "invokeDevRegister(\"com.example.globe.dev.DistributionCensusExporter\")"),
                "the distribution census registers only through the dev-only reflective path");
        expectTrue(globeMod.contains(
                "invokeDevRegister(\"com.example.globe.dev.StructureAtlasExporter\")"),
                "exporter registers only through the dev-environment-only reflective path");

        String build = java.nio.file.Files.readString(java.nio.file.Path.of("build.gradle"))
                .replaceAll("\\s+", " ");
        expectTrue(build.contains("path == 'com/example/globe/dev'")
                        && build.contains("path.startsWith('com/example/globe/dev/')"),
                "release artifact exclusion still covers the whole dev package");

        String locate = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/globe/world/LatitudeStructureLocateService.java"))
                .replaceAll("\\s+", " ");
        expectTrue(locate.contains("public static AtlasSweep sweepStructureCandidatesForAtlas(")
                        && locate.contains("applyVerdictTally(tally, verdict)"),
                "locate and the sweep share one verdict vocabulary and one tally mapping");
        expectTrue(locate.contains("Latitude does not own worldgen in this overworld"),
                "a sweep on a non-Latitude world refuses with a reason rather than reporting "
                        + "vanilla's placement as Latitude's");
        expectTrue(exporter.contains("sweep.refusalReason() != null")
                        && exporter.contains("structure atlas REFUSED"),
                "a refused sweep is logged loudly, so an empty artifact is never read as an "
                        + "empty world");
        expectTrue(exporter.contains("MAX_CANDIDATE_ROWS")
                        && exporter.contains("rejectedSampleStride")
                        && exporter.contains("candidatesTotal"),
                "the artifact is bounded, and records the total and the sampling stride so a "
                        + "thinned layer cannot be mistaken for a complete one");
        expectTrue(exporter.contains("Files.createTempDirectory(outputRoot, runScope + \"_\")")
                        && exporter.contains("\"_R\" + radius"),
                "every sweep gets a unique world/radius-prefixed directory — world name and "
                        + "radius alone still collide across seeds and concurrent reruns");
        expectTrue(exporter.contains("System.getProperty(\"latdev.biomePng\") != null")
                        && exporter.contains("System.getProperty(\"latdev.biomeSearch\") != null")
                        && exporter.contains("structure atlas REFUSED: run it without"),
                "the structure sweep refuses the biome exporters whose shutdown and radius "
                        + "ownership made combined evidence invalid");
        expectTrue(exporter.contains("can take tens of minutes")
                        && exporter.contains("structure-incidence counter"),
                "the dev-tool contract states the measured runtime and candidate-level evidence "
                        + "boundary instead of promising a seconds-long incidence measurement");
    }

    private static void packagedTestIdentityAndProbePolicyFailClosed() {
        String version = "1.5.0+26.2-test.2";
        String commit = "f9add4b9ac569faf0424fa08c1f5f5d4ecd99aae";
        String branch = "codex/1.5-mini-launch-26.2";
        String time = "2026-07-19T21:30:00Z";
        expectTrue(
                DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, version,
                        commit, branch, "false", time),
                "matching packaged TEST identity is accepted");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        false, version, "TEST", 2, version, version,
                        commit, branch, "false", time),
                "missing Fabric TEST marker is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "PUBLIC", 2, version, version,
                        commit, branch, "false", time),
                "forged artifact role is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 3, version, version,
                        commit, branch, "false", time),
                "version and sequence disagreement is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, "1.5.0+26.2-test.3",
                        commit, branch, "false", time),
                "manifest artifact-version disagreement is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, version,
                        "unknown", branch, "false", time),
                "unknown commit is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, version,
                        commit, "unknown", "false", time),
                "unknown branch is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, version,
                        commit, branch, "maybe", time),
                "non-boolean dirty state is rejected");
        expectTrue(
                !DevToolPolicy.packagedTestIdentityValid(
                        true, version, "TEST", 2, version, version,
                        commit, branch, "false", "not-a-time"),
                "non-ISO build time is rejected");

        expectTrue(DevToolPolicy.devToolingAllowed(true, false),
                "Loom development enables tooling");
        expectTrue(DevToolPolicy.devToolingAllowed(false, true),
                "valid packaged TEST identity enables tooling");
        expectTrue(!DevToolPolicy.devToolingAllowed(false, false),
                "ordinary packaged runtime fails closed");

        expectTrue(
                DevToolPolicy.autoCreateWorldProbeEnabled(true, false, null, false),
                "Loom development preserves default-on auto-create behavior");
        expectTrue(
                !DevToolPolicy.autoCreateWorldProbeEnabled(true, false, null, true),
                "Loom disable switch remains effective");
        expectTrue(
                !DevToolPolicy.autoCreateWorldProbeEnabled(true, false, "false", false),
                "Loom explicit false remains effective");
        expectTrue(
                !DevToolPolicy.autoCreateWorldProbeEnabled(false, true, null, false),
                "packaged TEST auto-create defaults off");
        expectTrue(
                DevToolPolicy.autoCreateWorldProbeEnabled(false, true, "true", false),
                "packaged TEST auto-create requires explicit opt-in");
        expectTrue(
                !DevToolPolicy.autoCreateWorldProbeEnabled(false, false, "true", false),
                "invalid packaged identity cannot activate auto-create");
    }

    private static void sanitizationIsStableAndTraversalSafe() throws Exception {
        expectEquals(
                "polar-fog-north",
                DevToolPolicy.sanitizeToken("  Polar Fog / NORTH  ", "case"),
                "case names normalize predictably");
        expectEquals(
                "case",
                DevToolPolicy.sanitizeToken("../../", "case"),
                "separator-only names use the safe fallback");
        expectTrue(
                DevToolPolicy.sanitizeToken("a".repeat(100), "case").length()
                        == DevToolPolicy.MAX_TOKEN_LENGTH,
                "sanitized names have a stable maximum length");

        Path root = Files.createTempDirectory("latitude-dev-policy-root");
        Path child = DevToolPolicy.resolveContained(root, "case", "events.jsonl");
        expectTrue(child.startsWith(root.toAbsolutePath()), "ordinary evidence path stays contained");
        expectThrows(
                () -> DevToolPolicy.resolveContained(root, "..", "escape"),
                "traversal outside evidence root is rejected");
    }

    private static void latitudeMappingUsesCenterBoundsAndBlockCenters() {
        DevToolPolicy.LatitudeTarget equator = DevToolPolicy.latitudeTarget(
                0.0,
                100.0,
                1000.0,
                -900.0,
                1100.0,
                1.0);
        expectEquals(100, equator.blockZ(), "zero degrees maps to nonzero border center");
        expectNear(0.045, equator.actualDegrees(), "actual latitude uses placed block center");

        DevToolPolicy.LatitudeTarget south = DevToolPolicy.latitudeTarget(
                45.0,
                100.0,
                1000.0,
                -900.0,
                1100.0,
                1.0);
        expectEquals(600, south.blockZ(), "+45 maps south of the centered equator");
        expectNear(45.045, south.actualDegrees(), "+45 reports achieved block-center latitude");

        DevToolPolicy.LatitudeTarget north = DevToolPolicy.latitudeTarget(
                -45.0,
                100.0,
                1000.0,
                -900.0,
                1100.0,
                1.0);
        expectEquals(-400, north.blockZ(), "-45 maps north of the centered equator");
        expectNear(-44.955, north.actualDegrees(), "-45 reports achieved block-center latitude");

        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        90.0, 100.0, 1000.0, -900.0, 1100.0, 1.0),
                "+90 is rejected because the safe block center cannot be inside the border");
        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        -90.0, 100.0, 1000.0, -900.0, 1100.0, 1.0),
                "-90 is rejected because the safe block center cannot be inside the border");
        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        90.01, 100.0, 1000.0, -900.0, 1100.0, 1.0),
                "out-of-range latitude is rejected instead of clamped");
        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        Double.NaN, 100.0, 1000.0, -900.0, 1100.0, 1.0),
                "non-finite latitude is rejected");

        expectEquals(
                1098,
                DevToolPolicy.safeHorizontalBlock(1098.9, -900.0, 1100.0, 1.0),
                "optional X floors to a safely contained block");
        expectThrows(
                () -> DevToolPolicy.safeHorizontalBlock(1099.5, -900.0, 1100.0, 1.0),
                "optional X inside the safety margin is rejected");
    }

    private static void movementAndTransitionSamplingAreDeterministic() {
        expectEquals(
                DevToolPolicy.MovementDirection.INITIAL,
                DevToolPolicy.movementDirection(Double.NaN, 84.0),
                "first movement sample is explicit");
        expectEquals(
                DevToolPolicy.MovementDirection.POLEWARD,
                DevToolPolicy.movementDirection(84.0, 85.0),
                "increasing absolute latitude is poleward");
        expectEquals(
                DevToolPolicy.MovementDirection.EQUATORWARD,
                DevToolPolicy.movementDirection(85.0, 84.0),
                "decreasing absolute latitude is equatorward");
        expectEquals(
                DevToolPolicy.MovementDirection.STATIONARY,
                DevToolPolicy.movementDirection(85.0, 85.0),
                "unchanged absolute latitude is stationary");

        DevToolPolicy.TraceTransition first = DevToolPolicy.traceTransition(
                Double.NaN,
                85.0,
                null,
                0,
                1,
                -1,
                PolarPresentationPolicy.fogIntensity(85.0));
        expectTrue(first.shouldRecord(), "initial rendered-policy sample is recorded");
        expectEquals(0, first.fogBucket(), "85 degrees begins at zero fog intensity");

        DevToolPolicy.TraceTransition fogStep = DevToolPolicy.traceTransition(
                85.0,
                87.5,
                DevToolPolicy.MovementDirection.INITIAL,
                1,
                2,
                0,
                PolarPresentationPolicy.fogIntensity(87.5));
        expectTrue(fogStep.shouldRecord(), "stage/direction/fog change is recorded");
        expectEquals(5, fogStep.fogBucket(), "production smoothstep midpoint is bucket five");

        DevToolPolicy.TraceTransition noChange = DevToolPolicy.traceTransition(
                87.5,
                87.5,
                DevToolPolicy.MovementDirection.STATIONARY,
                2,
                2,
                5,
                PolarPresentationPolicy.fogIntensity(87.5));
        expectTrue(!noChange.shouldRecord(), "unchanged stationary sample does not spam the trace");
    }

    private static void productionBorderRadiusOwnsCommandAndEvidenceLatitude() {
        expectEquals(
                10_000,
                DevToolPolicy.productionLatitudeRadius(10_000, 10_000.0),
                "active worldgen radius owns developer classification");
        expectEquals(
                10_000,
                DevToolPolicy.productionLatitudeRadius(0, 10_000.0),
                "world-border half-size is the developer-classification fallback");
        expectEquals(
                10_000,
                DevToolPolicy.productionLatitudeRadius(10_000, 9_984.0),
                "padded traversal radius cannot shrink active latitude authority");

        int resolvedRadius = DevToolPolicy.productionLatitudeRadius(10_000, 10_000.0);
        double northBelowBoundary = Math.abs(
                DevToolPolicy.signedLatitudeDegrees(-5_555, 0.0, resolvedRadius));
        double northAtBoundary = Math.abs(
                DevToolPolicy.signedLatitudeDegrees(-5_556, 0.0, resolvedRadius));
        double southBelowBoundary = Math.abs(
                DevToolPolicy.signedLatitudeDegrees(5_555, 0.0, resolvedRadius));
        double southAtBoundary = Math.abs(
                DevToolPolicy.signedLatitudeDegrees(5_556, 0.0, resolvedRadius));
        expectNear(49.995, northBelowBoundary,
                "north O3 below-boundary block uses production radius");
        expectNear(50.004, northAtBoundary,
                "north O3 boundary block uses production radius");
        expectNear(49.995, southBelowBoundary,
                "south O3 below-boundary block uses production radius");
        expectNear(50.004, southAtBoundary,
                "south O3 boundary block uses production radius");
        expectTrue(northBelowBoundary < 50.0,
                "north O3 below-boundary block stays temperate");
        expectTrue(northAtBoundary >= 50.0,
                "north O3 boundary block enters subpolar");
        expectTrue(southBelowBoundary < 50.0,
                "south O3 below-boundary block stays temperate");
        expectTrue(southAtBoundary >= 50.0,
                "south O3 boundary block enters subpolar");

        DevToolPolicy.LatitudeTarget target = DevToolPolicy.latitudeTarget(
                89.0,
                0.0,
                10_000.0,
                -10_000.0,
                10_000.0,
                1.0);
        expectEquals(9_889, target.blockZ(),
                "89 degrees uses the production border half-size, not the padded audit radius");
        expectNear(89.0055, target.actualDegrees(),
                "command and client evidence agree at the placed block center");
        expectNear(
                target.actualDegrees(),
                DevToolPolicy.signedLatitudeDegrees(
                        target.blockZ() + 0.5,
                        0.0,
                        10_000.0),
                "case context uses the same production radius as target placement");

        DevToolPolicy.LatitudeTarget centered = DevToolPolicy.latitudeTarget(
                -89.0,
                250.0,
                10_000.0,
                -9_750.0,
                10_250.0,
                1.0);
        expectEquals(-9_639, centered.blockZ(),
                "nonzero border center is included in production-radius placement");
        expectNear(-88.9965, centered.actualDegrees(),
                "nonzero-center achieved latitude uses block-center placement");

        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        90.0, 0.0, 10_000.0, -10_000.0, 10_000.0, 1.0),
                "production-radius +90 target is rejected at the safety margin");
        expectThrows(
                () -> DevToolPolicy.latitudeTarget(
                        -90.0, 0.0, 10_000.0, -10_000.0, 10_000.0, 1.0),
                "production-radius -90 target is rejected at the safety margin");

        double staleRadiusClaim = DevToolPolicy.signedLatitudeDegrees(
                9_873.5,
                0.0,
                9_984.0);
        double productionClaim = DevToolPolicy.signedLatitudeDegrees(
                9_873.5,
                0.0,
                10_000.0);
        expectNear(89.00390625, staleRadiusClaim,
                "preserved Phase 7 command-side RED is reproducible");
        expectNear(88.8615, productionClaim,
                "preserved Phase 7 production-client value is reproducible");
        expectTrue(Math.abs(staleRadiusClaim - productionClaim) > 0.1,
                "the stale radius disagreement is materially visible");
    }

    private static void traceClockPreservesSameDimensionContinuity() {
        DevToolPolicy.TraceClock clock = new DevToolPolicy.TraceClock();
        DevToolPolicy.TraceClock.Update initial = clock.update("minecraft:overworld", 4_179L);
        expectEquals(DevToolPolicy.TraceContextAction.INITIAL, initial.action(),
                "first trace clock sample establishes context");
        expectEquals(4_179L, initial.policyTick(),
                "first policy tick starts from raw game time");

        DevToolPolicy.TraceClock.Update forward = clock.update("minecraft:overworld", 4_180L);
        expectEquals(DevToolPolicy.TraceContextAction.CONTINUE, forward.action(),
                "forward same-dimension time continues normally");
        expectEquals(4_180L, forward.policyTick(),
                "forward raw tick advances policy time");

        PolarPresentationPolicy.PolarWarningEpisode warning =
                new PolarPresentationPolicy.PolarWarningEpisode();
        warning.update(3, 89.0, initial.policyTick());
        warning.update(4, 89.8, forward.policyTick());
        expectEquals(4, warning.highestTriggeredStageRank(),
                "poleward warning episode triggers before rollback");

        DevToolPolicy.TraceClock.Update rollback = clock.update("minecraft:overworld", 4_134L);
        expectEquals(DevToolPolicy.TraceContextAction.CLOCK_RESYNC, rollback.action(),
                "same-dimension raw rollback is a clock resync, not a context reset");
        expectEquals(4_181L, rollback.policyTick(),
                "rollback advances monotonic warning policy time by one client tick");
        warning.update(4, 89.8, rollback.policyTick());
        expectEquals(4, warning.highestTriggeredStageRank(),
                "clock resync preserves warning episode rank");
        float alphaAfterRollback = warning.alpha(rollback.policyTick());
        expectTrue(alphaAfterRollback > 0.0f,
                "clock resync does not make warning age negative or invisible");

        DevToolPolicy.TraceClock.Update afterRollback = clock.update("minecraft:overworld", 4_135L);
        expectEquals(4_182L, afterRollback.policyTick(),
                "post-resync raw progress continues monotonic policy time");
        warning.update(4, 89.8, afterRollback.policyTick());
        expectTrue(warning.alpha(afterRollback.policyTick()) >= alphaAfterRollback,
                "warning timing progresses monotonically after rollback");

        DevToolPolicy.TraceTransition movement = DevToolPolicy.traceTransition(
                89.8,
                89.9,
                DevToolPolicy.MovementDirection.STATIONARY,
                4,
                4,
                10,
                PolarPresentationPolicy.fogIntensity(89.9));
        expectEquals(DevToolPolicy.MovementDirection.POLEWARD, movement.direction(),
                "same-dimension clock resync does not force movement back to initial");

        DevToolPolicy.TraceClock.Update dimensionChange =
                clock.update("minecraft:the_nether", 50L);
        expectEquals(DevToolPolicy.TraceContextAction.DIMENSION_RESET, dimensionChange.action(),
                "real dimension change explicitly requests sample and warning reset");
        expectEquals(50L, dimensionChange.policyTick(),
                "new dimension receives a fresh policy clock epoch");
    }

    private static void caseSessionIsAppendOnlyOrderedAndExplicitlyClosed() throws Exception {
        Path root = Files.createTempDirectory("latitude-dev-session");
        Clock fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
        DevTestSession session = DevTestSession.startActive(
                root,
                "North Fog",
                100L,
                Map.of("z_field", "last", "a_field", "first"),
                fixed);
        expectEquals("north-fog", session.caseId(), "case id is sanitized");
        expectEquals("north-fog", session.sessionId(), "first session uses the base id");

        expectEquals(
                2L,
                DevTestSession.markActive("Warning One", 101L, Map.of("note", "visible")),
                "mark receives the second monotonic sequence");

        List<String> beforeFailedRequest = Files.readAllLines(session.eventsPath());
        Files.delete(session.eventsPath());
        Files.createDirectory(session.eventsPath());
        expectThrows(
                () -> DevTestSession.requestCaptureActive(
                        "Failed Frame",
                        true,
                        102L,
                        Map.of()),
                "failed request append does not create a phantom pending capture");
        Files.delete(session.eventsPath());
        Files.write(session.eventsPath(), beforeFailedRequest);
        expectEquals(2L, session.sequence(), "failed request append does not consume a sequence");

        expectEquals(
                3L,
                DevTestSession.requestCaptureActive(
                        "Frame One",
                        true,
                        102L,
                        Map.of("source", "test")),
                "capture request receives the third monotonic sequence");
        expectThrows(
                () -> DevTestSession.requestCaptureActive(
                        "Frame Two",
                        true,
                        102L,
                        Map.of()),
                "a second request cannot overwrite a pending capture");
        expectThrows(
                () -> DevTestSession.finishActive(
                        DevToolPolicy.CloseState.PASS,
                        102L,
                        Map.of()),
                "case cannot finish while an asynchronous capture is pending");

        List<String> beforeFailedCompletion = Files.readAllLines(session.eventsPath());
        Files.delete(session.eventsPath());
        Files.createDirectory(session.eventsPath());
        expectThrows(
                () -> DevTestSession.recordScreenshotActive(
                        "Latitude/captures/failed.png",
                        "bad",
                        102L,
                        Map.of()),
                "failed completion append retains the pending capture");
        Files.delete(session.eventsPath());
        Files.write(session.eventsPath(), beforeFailedCompletion);
        expectThrows(
                () -> DevTestSession.finishActive(
                        DevToolPolicy.CloseState.PASS,
                        102L,
                        Map.of()),
                "failed completion cannot make the session finishable");

        expectEquals(
                4L,
                DevTestSession.recordScreenshotActive(
                        "Latitude/captures/frame.png",
                        "abc123",
                        102L,
                        Map.of("capture_status", "saved")),
                "capture completion is distinct from capture request");
        DevTestSession finished = DevTestSession.finishActive(
                DevToolPolicy.CloseState.HOLD,
                103L,
                Map.of("reason", "human_visual_decision"));
        expectEquals(5L, finished.sequence(), "finish receives the fifth monotonic sequence");
        expectTrue(DevTestSession.active().isEmpty(), "finished session is no longer active");

        List<String> lines = Files.readAllLines(session.eventsPath());
        expectEquals(5, lines.size(), "append-only event log contains every lifecycle event");
        expectTrue(
                lines.getFirst().startsWith(
                        "{\"schema\":\"latitude-dev-case-v1\",\"sequence\":1,\"world_tick\":100,"
                                + "\"event\":\"start\",\"case_id\":\"north-fog\","
                                + "\"session_id\":\"north-fog\","
                                + "\"timestamp_utc\":\"2026-07-19T12:00:00Z\","
                                + "\"a_field\":\"first\",\"z_field\":\"last\""),
                "base and extension field ordering is stable");
        expectTrue(lines.get(2).contains("\"event\":\"capture_requested\""),
                "request event is explicitly named");
        expectTrue(lines.get(3).contains("\"event\":\"capture_completed\""),
                "completion event is explicitly named");
        expectTrue(lines.get(4).contains("\"result\":\"hold\""),
                "finish records explicit pass/fail/hold result");
        expectTrue(
                Files.readString(session.directory().resolve("summary.json"))
                        .contains("\"result\":\"hold\""),
                "summary repeats the explicit close result");

        DevTestSession second = DevTestSession.startActive(
                root,
                "North Fog",
                200L,
                Map.of(),
                fixed);
        expectEquals("north-fog-002", second.sessionId(),
                "same case id receives deterministic collision suffix");
        Path blockedSummary = second.directory().resolve("summary.json");
        Files.createDirectory(blockedSummary);
        expectThrows(
                () -> DevTestSession.finishActive(
                        DevToolPolicy.CloseState.PASS,
                        201L,
                        Map.of()),
                "summary write failure leaves one recoverable finish event");
        expectEquals(2L, second.sequence(), "failed summary write records finish exactly once");
        expectThrows(
                () -> DevTestSession.markActive(
                        "Too Late",
                        202L,
                        Map.of()),
                "no event may follow an already-recorded finish");
        expectEquals(2L, second.sequence(), "rejected post-finish event cannot consume a sequence");
        expectEquals(
                2,
                Files.readAllLines(second.eventsPath()).size(),
                "rejected post-finish event cannot extend the event log");
        Files.delete(blockedSummary);
        DevTestSession recovered = DevTestSession.finishActive(
                DevToolPolicy.CloseState.PASS,
                202L,
                Map.of());
        expectEquals(2L, recovered.sequence(), "summary retry does not duplicate the finish event");
        expectEquals(
                2,
                Files.readAllLines(second.eventsPath()).size(),
                "recovered session keeps a single finish row");

        expectEquals(DevToolPolicy.CloseState.PASS, DevToolPolicy.CloseState.parse("PASS"),
                "close-state parsing is case-insensitive");
        expectThrows(
                () -> DevToolPolicy.CloseState.parse("maybe"),
                "unknown close state is rejected");
    }

    private static void expectTrue(boolean actual, String label) {
        assertions++;
        if (!actual) {
            throw new AssertionError(label);
        }
    }

    private static void expectNear(double expected, double actual, String label) {
        assertions++;
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectEquals(Object expected, Object actual, String label) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectThrows(ThrowingAction action, String label) {
        assertions++;
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(label + ": expected exception");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
