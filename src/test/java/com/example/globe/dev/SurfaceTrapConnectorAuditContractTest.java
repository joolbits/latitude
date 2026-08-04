package com.example.globe.dev;

import com.example.globe.core.SubterraneanTrapPlan;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the no-generation dispatch and deterministic saved-window input contract. */
class SurfaceTrapConnectorAuditContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void targetChunkInputIsStrictlyUniqueAndDeterministicallySorted() throws Exception {
        Path chunks = temporaryDirectory.resolve("targets.txt");
        Files.writeString(chunks, "# historical calls\n5,-2\n-3,8\n0,0\n", StandardCharsets.UTF_8);

        assertEquals(List.of(
                        new SurfaceTrapConnectorAudit.ChunkCoordinate(-3, 8),
                        new SurfaceTrapConnectorAudit.ChunkCoordinate(0, 0),
                        new SurfaceTrapConnectorAudit.ChunkCoordinate(5, -2)),
                SurfaceTrapConnectorAudit.readTargetChunks(chunks));
    }

    @Test
    void targetChunkInputRejectsDuplicateOrMalformedRowsRatherThanSilentlyChangingThePopulation() throws Exception {
        Path duplicate = temporaryDirectory.resolve("duplicate.txt");
        Files.writeString(duplicate, "1,2\n1,2\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> SurfaceTrapConnectorAudit.readTargetChunks(duplicate));

        Path malformed = temporaryDirectory.resolve("malformed.txt");
        Files.writeString(malformed, "1,2,3\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> SurfaceTrapConnectorAudit.readTargetChunks(malformed));
    }

    @Test
    void auditIsGatedBeforeGeneratingModesAndEveryForkedPropertyIsForwarded() throws Exception {
        String runner = Files.readString(Path.of("src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java"));
        String gradle = Files.readString(Path.of("build.gradle"));
        String audit = Files.readString(Path.of("src/main/java/com/example/globe/dev/SurfaceTrapConnectorAudit.java"));

        assertTrue(runner.indexOf("SurfaceTrapConnectorAudit.isEnabled()")
                        < runner.indexOf("CaveDropTrapFullChunkAudit.isEnabled()"),
                "saved-world audit must dispatch before the feature-generating cave audit");
        for (String property : List.of("latdev.surfaceConnectorAudit=", "latdev.surfaceConnectorAudit.chunks=",
                "latdev.surfaceConnectorAudit.maxHorizontal=", "latdev.surfaceConnectorAudit.maxVerticalDrop=",
                "latdev.surfaceConnectorAudit.out=")) {
            assertTrue(gradle.contains(property), "biomePreview must forward " + property);
        }
        assertTrue(audit.contains("getChunk(x, z, ChunkStatus.FULL, false)"),
                "missing saved chunks must be detected without requesting generation");
        assertTrue(!audit.contains("ensureCanWrite") && !audit.contains("setBlock"),
                "this diagnostic must not cross a world-write boundary");
        assertTrue(gradle.contains("path.startsWith('com/example/globe/dev/')"),
                "dev-only audit classes must remain excluded from the shipped jar");
    }

    @Test
    void connectedComponentLargerThanProductionCapIsEmittedOnceWithCanonicalIdentity() {
        Set<SubterraneanTrapPlan.RouteCell> cells = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            cells.add(new SubterraneanTrapPlan.RouteCell(x, 20, 0));
        }

        List<SurfaceTrapConnectorAudit.Volume> volumes =
                SurfaceTrapConnectorAudit.connectedComponents(cells);

        assertEquals(1, volumes.size());
        assertEquals(40, volumes.getFirst().cells().size());
        assertEquals(new SubterraneanTrapPlan.RouteCell(0, 20, 0), volumes.getFirst().id());
        assertEquals(40, new HashSet<>(volumes.getFirst().cells()).size(),
                "a component must not contain overlap/fragment duplicates after cell 32");
    }

    @Test
    void nearestMeasurementSelectsARealAnchorAndSeparatesCellFromComponentOwnerSemantics() {
        List<SubterraneanTrapPlan.RouteCell> realAnchors = List.of(
                new SubterraneanTrapPlan.RouteCell(0, 40, 10),
                new SubterraneanTrapPlan.RouteCell(10, 40, 0));
        SubterraneanTrapPlan.RouteCell caveCell = new SubterraneanTrapPlan.RouteCell(0, 20, 0);
        SurfaceTrapConnectorAudit.Volume volume = new SurfaceTrapConnectorAudit.Volume(
                caveCell, List.of(caveCell), false);

        SurfaceTrapConnectorAudit.Nearest nearest = SurfaceTrapConnectorAudit.nearestVolume(
                realAnchors, new SurfaceTrapConnectorAudit.ChunkCoordinate(0, 0),
                List.of(volume), 64, 96);

        assertTrue(realAnchors.contains(nearest.anchor()));
        assertEquals(10, nearest.horizontalManhattan(),
                "independent x/z minima would invent a zero-distance mouth here");

        SubterraneanTrapPlan.RouteCell outside = new SubterraneanTrapPlan.RouteCell(16, 20, 8);
        SubterraneanTrapPlan.RouteCell ownerMember = new SubterraneanTrapPlan.RouteCell(0, 20, 0);
        SurfaceTrapConnectorAudit.Nearest relation = SurfaceTrapConnectorAudit.nearestVolume(
                List.of(new SubterraneanTrapPlan.RouteCell(15, 40, 8)),
                new SurfaceTrapConnectorAudit.ChunkCoordinate(0, 0),
                List.of(new SurfaceTrapConnectorAudit.Volume(ownerMember,
                        List.of(ownerMember, outside), false)), 64, 96);
        assertFalse(relation.nearestCellSameOwner());
        assertTrue(relation.componentIntersectsOwner());
    }

    @Test
    void requestedRadiusIsNeverCalledGlobalWhenItEscapesLoadedTargetChunks() {
        Set<SurfaceTrapConnectorAudit.ChunkCoordinate> loaded =
                Set.of(new SurfaceTrapConnectorAudit.ChunkCoordinate(0, 0));
        assertFalse(SurfaceTrapConnectorAudit.requestedRadiusClipped(
                List.of(new SubterraneanTrapPlan.RouteCell(8, 40, 8)), 7, loaded));
        assertTrue(SurfaceTrapConnectorAudit.requestedRadiusClipped(
                List.of(new SubterraneanTrapPlan.RouteCell(8, 40, 8)), 8, loaded));
        assertTrue(SurfaceTrapConnectorAudit.requestedRadiusClipped(
                List.of(new SubterraneanTrapPlan.RouteCell(15, 40, 8)), 1, loaded));
    }

    @Test
    void historicalPopulationReconcilesOnlyAsOneExactJointContract() {
        List<SurfaceTrapConnectorAudit.ChunkCoordinate> calls = IntStream.range(
                        0, SurfaceTrapConnectorAudit.EXPECTED_HISTORICAL_CALLS)
                .mapToObj(i -> new SurfaceTrapConnectorAudit.ChunkCoordinate(i, -i)).toList();
        SurfaceTrapConnectorAudit.InputPopulation exact = new SurfaceTrapConnectorAudit.InputPopulation(
                calls, SurfaceTrapConnectorAudit.EXPECTED_INPUT_SHA256);
        assertTrue(SurfaceTrapConnectorAudit.reconciliation(exact, 106, 6_784, true, 665).passes());
        assertFalse(SurfaceTrapConnectorAudit.reconciliation(exact, 105, 6_720, true, 665).passes());
        assertFalse(SurfaceTrapConnectorAudit.reconciliation(
                new SurfaceTrapConnectorAudit.InputPopulation(calls, "wrong"),
                106, 6_784, true, 665).passes());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SurfaceTrapConnectorAudit.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingInputStillProducesMachineReadableFailedReport() throws Exception {
        Path reportPath = temporaryDirectory.resolve("reports/audit.json");
        SurfaceTrapConnectorAudit.writeInputPreflightReport(
                temporaryDirectory.resolve("missing.txt"), reportPath);

        JsonObject report = JsonParser.parseString(Files.readString(reportPath)).getAsJsonObject();
        assertEquals("failed", report.get("status").getAsString());
        assertEquals("input-read", report.get("failureStage").getAsString());
        assertTrue(report.getAsJsonArray("errors").size() > 0);
    }
}
