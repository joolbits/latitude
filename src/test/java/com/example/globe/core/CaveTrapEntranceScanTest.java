package com.example.globe.core;

import java.util.List;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveTrapEntranceScanTest {

    @Test
    void cardinalSamePlaneSignatureBlocksReportOneEntrancePatch() {
        var patches = CaveTrapEntranceScan.groupPatches(List.of(
                new CaveTrapEntranceScan.Cell(10, 32, 10),
                new CaveTrapEntranceScan.Cell(11, 32, 10),
                new CaveTrapEntranceScan.Cell(11, 32, 11),
                new CaveTrapEntranceScan.Cell(10, 32, 10)));

        assertEquals(1, patches.size());
        assertEquals(3, patches.getFirst().blockCount());
        assertEquals(32, patches.getFirst().y());
    }

    @Test
    void diagonalAndDifferentHeightSignaturesAreSeparateEntrances() {
        var patches = CaveTrapEntranceScan.groupPatches(List.of(
                new CaveTrapEntranceScan.Cell(0, 20, 0),
                new CaveTrapEntranceScan.Cell(1, 20, 1),
                new CaveTrapEntranceScan.Cell(0, 21, 1)));

        assertEquals(3, patches.size(), "only cardinal same-plane cells make one entrance carpet");
    }

    @Test
    void representativeIsARealCentralSignatureBlock() {
        var patch = CaveTrapEntranceScan.groupPatches(List.of(
                new CaveTrapEntranceScan.Cell(5, 40, 5),
                new CaveTrapEntranceScan.Cell(6, 40, 5),
                new CaveTrapEntranceScan.Cell(7, 40, 5))).getFirst();

        assertEquals(6, patch.x());
        assertEquals(40, patch.y());
        assertEquals(5, patch.z());
    }

    @Test
    void emptySectionPalettesSkipAll4096BlockReadsAndCandidateYsUseTheChunkMinimum() {
        boolean[] palettes = {false, false, true, false, true, false};

        assertEquals(List.of(2, 4), CaveTrapEntranceScan.candidateSectionIndices(palettes));
        assertEquals(-32, CaveTrapEntranceScan.sectionStartY(-64, 2));
        assertEquals(0, CaveTrapEntranceScan.sectionStartY(-64, 4));
        assertEquals(List.of(), CaveTrapEntranceScan.candidateSectionIndices(
                new boolean[] {false, false, false}),
                "a chunk with no candidate palettes must perform no block reads");
    }

    @Test
    void compiledCommandRegistersAndDocumentsTheSharedBetaAndDevRoots() throws IOException {
        byte[] bytes;
        try (var input = CaveTrapEntranceScanTest.class.getClassLoader().getResourceAsStream(
                "com/example/globe/LatitudeDevCommands.class")) {
            assertTrue(input != null, "the actual command adapter must be compiled for this contract");
            bytes = input.readAllBytes();
        }
        String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("markCaveTraps"));
        assertTrue(constants.contains("latdev2"), "development registers the shared command tree under /latdev2");
        assertTrue(constants.contains("cave_trap_powder_snow"), "the command reports the exact worldgen signature");
        assertTrue(constants.contains("FULL loaded chunks"), "unloaded chunks must be reported as skipped");
        assertTrue(constants.contains("/tp @s"), "teleports use an exact, underground Y coordinate");
        assertTrue(constants.contains("maybeHas"), "non-candidate section palettes skip their 4096 block reads");
    }
}
