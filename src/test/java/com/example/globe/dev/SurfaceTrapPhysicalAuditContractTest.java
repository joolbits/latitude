package com.example.globe.dev;

import com.example.globe.core.GlacialMarkScan;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioral and wiring contracts for the default-off fresh-generation physical audit. */
class SurfaceTrapPhysicalAuditContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void historicalTargetAndScanWindowsRemainExactly256And289Chunks() {
        SurfaceTrapPhysicalAudit.Config config = SurfaceTrapPhysicalAudit.Config.validated(
                -8, -8, 16, -8, -8, 17, 0, 0, 8,
                temporaryDirectory.resolve("audit.json"));

        assertTrue(config.errors().isEmpty(), config.errors().toString());
        assertEquals(256, config.targetChunkCount());
        assertEquals(289, config.scanChunkCount());
        assertEquals(256, config.toJson().get("targetChunkCount").getAsInt());
        assertEquals(289, config.toJson().get("scanChunkCount").getAsInt());

        SurfaceTrapPhysicalAudit.Config wrongHalo = SurfaceTrapPhysicalAudit.Config.validated(
                -8, -8, 16, -8, -8, 16, 0, 0, 8,
                temporaryDirectory.resolve("wrong.json"));
        assertTrue(wrongHalo.errors().stream().anyMatch(error ->
                error.contains("historical target span 16 requires scan span 17")));
    }

    @Test
    void fullChunkRequestsUseDeterministicZMajorThenXMinorOrder() {
        List<SurfaceTrapPhysicalAudit.ChunkCoordinate> chunks =
                SurfaceTrapPhysicalAudit.requestedChunks(-8, -8, 17);

        assertEquals(289, chunks.size());
        assertEquals(new SurfaceTrapPhysicalAudit.ChunkCoordinate(-8, -8), chunks.get(0));
        assertEquals(new SurfaceTrapPhysicalAudit.ChunkCoordinate(-7, -8), chunks.get(1));
        assertEquals(new SurfaceTrapPhysicalAudit.ChunkCoordinate(8, -8), chunks.get(16));
        assertEquals(new SurfaceTrapPhysicalAudit.ChunkCoordinate(-8, -7), chunks.get(17));
        assertEquals(new SurfaceTrapPhysicalAudit.ChunkCoordinate(8, 8), chunks.getLast());
    }

    @Test
    void jsonReportIsGreenOnlyForARealEncounterDownwardRouteAndPassingCavernContract() {
        JsonObject green = new JsonObject();
        green.addProperty("schema", SurfaceTrapPhysicalAudit.SCHEMA);
        SurfaceTrapPhysicalAudit.finishReport(
                green, validScan(), 289, 289, 73_984L, 9_000_000L, List.of(),
                writeTelemetry(), -128, -64, -128);

        assertEquals("ok", green.get("status").getAsString());
        assertEquals(289, green.getAsJsonObject("counts").get("requestedChunks").getAsInt());
        assertEquals(0, green.getAsJsonObject("counts").get("writeFailures").getAsInt());
        assertEquals(1, green.getAsJsonObject("counts").get("authoredCavernCount").getAsInt());
        assertTrue(green.getAsJsonObject("assertions")
                .get("atLeastOneDownwardRoute").getAsBoolean());
        assertTrue(green.getAsJsonObject("assertions")
                .get("everyAuthoredCavernMeetsPhysicalContract").getAsBoolean());
        assertTrue(green.getAsJsonObject("writeFailureTelemetry").get("available").getAsBoolean());
        assertEquals("scan-local-block-index",
                green.getAsJsonObject("evidenceCoordinates").get("space").getAsString());
        assertEquals(-128, green.getAsJsonObject("evidenceCoordinates").get("originWorldX").getAsInt());

        JsonObject failed = new JsonObject();
        failed.addProperty("schema", SurfaceTrapPhysicalAudit.SCHEMA);
        SurfaceTrapPhysicalAudit.finishReport(failed,
                new GlacialMarkScan.PhysicalScanReport(
                        1, 0, 0, 0, 0, 0, 1, 0,
                        Map.of("AUTHORED_CAVERN_SIZE", 1), List.of()),
                289, 289, 73_984L, 9_000_000L, List.of(),
                writeTelemetry(), -128, -64, -128);
        assertEquals("failed", failed.get("status").getAsString());
        assertFalse(failed.getAsJsonObject("assertions").get("atLeastOneValidTrap").getAsBoolean());

        JsonObject naturalOnly = new JsonObject();
        GlacialMarkScan.PhysicalTrapEvidence authored = validScan().validTrapEvidence().getFirst();
        GlacialMarkScan.PhysicalTrapEvidence natural = new GlacialMarkScan.PhysicalTrapEvidence(
                authored.mouthX(), authored.mouthY(), authored.mouthZ(),
                GlacialMarkScan.EndpointKind.NATURAL_CAVE, authored.route(), null);
        SurfaceTrapPhysicalAudit.finishReport(naturalOnly,
                new GlacialMarkScan.PhysicalScanReport(
                        1, 1, 1, 27, 27, 1, 0, 0, Map.of(), List.of(natural)),
                289, 289, 73_984L, 9_000_000L, List.of(),
                writeTelemetry(), -128, -64, -128);
        assertEquals("failed", naturalOnly.get("status").getAsString());
        assertFalse(naturalOnly.getAsJsonObject("assertions")
                .get("atLeastOneAuthoredCavern").getAsBoolean());

        JsonObject unavailable = new JsonObject();
        SurfaceTrapPhysicalAudit.finishReport(unavailable, validScan(), 289, 289,
                73_984L, 9_000_000L, List.of(),
                new com.example.globe.world.PowderCrevasseRoofFeature.GenerationWriteTelemetry(
                        false, 9, 0, 0, 0, 0), -128, -64, -128);
        assertEquals("failed", unavailable.get("status").getAsString());
        assertTrue(unavailable.getAsJsonObject("counts").get("writeFailures").isJsonNull());
    }

    @Test
    void runnerDispatchBuildForwardingAndReadOnlyShutdownLawArePinned() throws Exception {
        String runner = Files.readString(
                Path.of("src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java"));
        String gradle = Files.readString(Path.of("build.gradle"));
        String audit = Files.readString(
                Path.of("src/main/java/com/example/globe/dev/SurfaceTrapPhysicalAudit.java"));

        int physical = runner.indexOf("SurfaceTrapPhysicalAudit.isEnabled()");
        assertTrue(physical >= 0);
        for (String laterGeneratingMode : List.of(
                "TerrainProofHarness.isTriggered()",
                "System.getProperty(PROBE_PROP_KEY",
                "CaveDropTrapFullChunkAudit.isEnabled()",
                "runAlpineAuditAndStop(server)",
                "runExportAndStop(server, config)")) {
            assertTrue(physical < runner.indexOf(laterGeneratingMode),
                    "physical audit must dispatch before " + laterGeneratingMode);
        }

        for (String suffix : List.of(
                "", ".targetChunkMinX", ".targetChunkMinZ", ".targetChunkSpan",
                ".scanChunkMinX", ".scanChunkMinZ", ".scanChunkSpan",
                ".centerX", ".centerZ", ".radiusChunks", ".out")) {
            assertTrue(gradle.contains("-Dlatdev.surfaceTrapPhysicalAudit" + suffix + "="),
                    "biomePreview must forward physical-audit property " + suffix);
        }
        assertTrue(audit.contains("ChunkStatus.FULL, true"),
                "fresh proof must request the exact FULL,true generation window");
        assertTrue(audit.contains("snapshot.worldMinY()"),
                "the shared scanner must receive real world Y for the above-Y0 law");
        assertFalse(audit.contains("setBlock"), "the proof must never repair blocks by hand");
        assertFalse(audit.contains("addRegionTicket") || audit.contains("addTicket"),
                "the proof must not leave chunk tickets behind");
        assertTrue(audit.contains("server.halt(false)"),
                "the report write must be followed by a clean server halt");
        assertTrue(audit.contains("catch (Throwable writeFailure)"),
                "a primary failure must still make a best-effort JSON write");
    }

    @Test
    void scannerAdapterClassifiesEveryForbiddenCavernResidueExplicitly() throws Exception {
        String audit = Files.readString(
                Path.of("src/main/java/com/example/globe/dev/SurfaceTrapPhysicalAudit.java"));
        for (String evidence : List.of(
                "PhysicalCellKind.FLUID", "PhysicalCellKind.BLOCK_ENTITY",
                "PhysicalCellKind.GRAVITY_SOLID", "PhysicalCellKind.ORE",
                "PhysicalCellKind.BEDROCK", "PhysicalCellKind.DEEPSLATE",
                "PhysicalCellKind.MAGMA")) {
            assertTrue(audit.contains(evidence), "missing explicit physical classification " + evidence);
        }
        assertTrue(audit.contains("world.getBiome(cursor)"),
                "floor and upper clear cells need measured saved-biome evidence");
    }

    private static GlacialMarkScan.PhysicalScanReport validScan() {
        GlacialMarkScan.PhysicalRouteEvidence route = new GlacialMarkScan.PhysicalRouteEvidence(
                List.of(
                        new GlacialMarkScan.PhysicalRouteStation(15, 10, 10, 10, 11),
                        new GlacialMarkScan.PhysicalRouteStation(10, 15, 10, 15, 11)),
                1, 1, 0, true, true);
        GlacialMarkScan.PhysicalCavernEvidence cavern =
                new GlacialMarkScan.PhysicalCavernEvidence(
                        10, 29, 2, 13, 8, 10, "EAST",
                        0, 0, 1, 0,
                        126, 680, 126, 126, 400, 806,
                        20, 240, 0.525,
                        10, 7, 8, 7, 3, 4, 2,
                        true, true, true);
        GlacialMarkScan.PhysicalTrapEvidence evidence =
                new GlacialMarkScan.PhysicalTrapEvidence(
                        5, 40, 5, GlacialMarkScan.EndpointKind.AUTHORED_CAVERN,
                        route, cavern);
        return new GlacialMarkScan.PhysicalScanReport(
                1, 1, 1, 27, 27, 1, 0, 0, Map.of(), List.of(evidence));
    }

    private static com.example.globe.world.PowderCrevasseRoofFeature.GenerationWriteTelemetry
            writeTelemetry() {
        return new com.example.globe.world.PowderCrevasseRoofFeature.GenerationWriteTelemetry(
                true, 7, 1, 1, 0, 0);
    }
}
