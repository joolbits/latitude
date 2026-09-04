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
        atlasBandCatalogNamesDappledAndPreservesExemptions();
        sanitizationIsStableAndTraversalSafe();
        packagedTestIdentityAndProbePolicyFailClosed();
        latitudeMappingUsesCenterBoundsAndBlockCenters();
        productionBorderRadiusOwnsCommandAndEvidenceLatitude();
        movementAndTransitionSamplingAreDeterministic();
        traceClockPreservesSameDimensionContinuity();
        caseSessionIsAppendOnlyOrderedAndExplicitlyClosed();
        recorderLiteBindsPrivateIdentityAndRouteWithoutLeakingSummary();
        System.out.println("DEV_TOOL_POLICY_TEST_PASS assertions=" + assertions);
    }

    private static void atlasBandCatalogNamesDappledAndPreservesExemptions() {
        expectTrue(BiomeBandPolicy.canonicalBandIdsFor("minecraft:dappled_forest")
                        .equals(List.of("temperate")),
                "Dappled must be audited against its temperate placement band");
        expectTrue(BiomeBandPolicy.policy().containsKey("minecraft:ocean")
                        && BiomeBandPolicy.canonicalBandIdsFor("minecraft:ocean").isEmpty(),
                "an explicitly exempt ocean must remain distinguishable from missing policy");
        expectTrue(!BiomeBandPolicy.policy().containsKey("example:unreviewed"),
                "unknown biome policy must not be guessed from its name");
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
                                + "\"a_field\":\"first\","
                                + "\"owed_checkpoint_count\":\"0\","
                                + "\"recorder_plan\":\"unplanned\","
                                + "\"world_class\":\"unknown\","
                                + "\"z_field\":\"last\""),
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

    private static void recorderLiteBindsPrivateIdentityAndRouteWithoutLeakingSummary()
            throws Exception {
        Path root = Files.createTempDirectory("latitude-recorder-lite");
        Clock fixed = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
        Path planPath = root.resolve("route.properties");
        Files.writeString(planPath, String.join("\n",
                "schema=latitude-recorder-plan-v1",
                "case_id=private-world",
                "world_class=fresh-control",
                "checkpoint.registry-load=pass",
                "checkpoint.atlas-bundle=complete",
                "atlas.radius=7500",
                "atlas.step=128",
                ""));
        RecorderLitePlan plan = RecorderLitePlan.load(planPath, "private-world");

        Map<String, String> privateIdentity = Map.ofEntries(
                Map.entry("artifact_sha256", "abc123"),
                Map.entry("source_commit", "deadbeef"),
                Map.entry("minecraft_version", "test-minecraft"),
                Map.entry("latitude_version", "test-latitude"),
                Map.entry("loader_version", "test-loader"),
                Map.entry("provider_stack", "minecraft,test-provider"),
                Map.entry("config_fingerprint", "config123"),
                Map.entry("datapack_fingerprint", "packs123"));
        Map<String, String> privateContext = Map.of(
                "seed", "private-seed",
                "player", "private-player",
                "x", "private-x",
                "machine_path", "/private/machine/path");
        DevTestSession session = DevTestSession.startActive(
                root,
                "private-world",
                300L,
                privateContext,
                plan,
                privateIdentity,
                fixed);

        String manifest = Files.readString(session.directory().resolve("manifest.private.json"));
        for (String required : List.of(
                "latitude-recorder-private-v1",
                "artifact_sha256",
                "source_commit",
                "minecraft_version",
                "latitude_version",
                "loader_version",
                "provider_stack",
                "config_fingerprint",
                "datapack_fingerprint",
                "fresh-control",
                "registry-load",
                "atlas-bundle",
                "private-seed",
                "private-player",
                "/private/machine/path")) {
            expectTrue(manifest.contains(required),
                    "private manifest binds required identity/route field " + required);
        }

        DevTestSession.markActive("registry-load=pass", 301L, Map.of());
        DevTestSession.requestCaptureActive("overview", true, 302L, Map.of());
        DevTestSession.recordScreenshotActive(
                "private/path/screenshot.png", "screen123", 302L, Map.of());
        DevTestSession.markActive("atlas-bundle=complete", 303L, Map.of());
        DevTestSession.finishActive(DevToolPolicy.CloseState.PASS, 304L, Map.of());

        String summary = Files.readString(session.directory().resolve("summary.json"));
        for (String required : List.of(
                "latitude-recorder-summary-v1",
                "case_label",
                "session_label",
                "registry-load",
                "atlas-bundle",
                "\"expected\":\"pass\"",
                "\"observed\":\"pass\"",
                "\"expected\":\"complete\"",
                "\"observed\":\"complete\"",
                "\"screenshot_count\":1",
                "screenshot-001",
                "screen123")) {
            expectTrue(summary.contains(required),
                    "shareable summary contains sanitized result field " + required);
        }
        for (String forbidden : List.of(
                "private-world",
                "private-seed",
                "private-player",
                "/private/machine/path",
                "private/path/screenshot.png")) {
            expectTrue(!summary.contains(forbidden),
                    "shareable summary excludes private field " + forbidden);
        }

        Path missingPlanPath = root.resolve("missing-route.properties");
        Files.writeString(missingPlanPath, String.join("\n",
                "schema=latitude-recorder-plan-v1",
                "case_id=missing-route",
                "world_class=legacy-new-chunks",
                "checkpoint.registry-load=pass",
                "checkpoint.boundary=visible",
                ""));
        RecorderLitePlan missingPlan = RecorderLitePlan.load(missingPlanPath, "missing-route");
        DevTestSession missing = DevTestSession.startActive(
                root, "missing-route", 400L, Map.of(), missingPlan, Map.of(), fixed);
        DevTestSession.markActive("registry-load=pass", 401L, Map.of());
        expectThrows(
                () -> DevTestSession.finishActive(
                        DevToolPolicy.CloseState.PASS, 402L, Map.of()),
                "Recorder Lite rejects PASS while an owed checkpoint is missing");
        DevTestSession.finishActive(DevToolPolicy.CloseState.HOLD, 403L, Map.of());
        expectTrue(
                Files.readString(missing.directory().resolve("summary.json"))
                        .contains("\"missing_checkpoint_count\":1"),
                "hold summary reports the missing checkpoint");

        Path mismatchPlanPath = root.resolve("mismatch-route.properties");
        Files.writeString(mismatchPlanPath, String.join("\n",
                "schema=latitude-recorder-plan-v1",
                "case_id=mismatch-route",
                "world_class=ordinary-control",
                "checkpoint.registry-load=pass",
                ""));
        RecorderLitePlan mismatchPlan = RecorderLitePlan.load(
                mismatchPlanPath, "mismatch-route");
        DevTestSession mismatch = DevTestSession.startActive(
                root, "mismatch-route", 500L, Map.of(), mismatchPlan, Map.of(), fixed);
        DevTestSession.markActive("registry-load=fail", 501L, Map.of());
        expectThrows(
                () -> DevTestSession.finishActive(
                        DevToolPolicy.CloseState.PASS, 502L, Map.of()),
                "Recorder Lite rejects PASS when observed evidence disagrees with expected");
        DevTestSession.finishActive(DevToolPolicy.CloseState.FAIL, 503L, Map.of());
        expectTrue(
                Files.readString(mismatch.directory().resolve("summary.json"))
                        .contains("\"mismatch_count\":1"),
                "fail summary reports the mismatched checkpoint");
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
