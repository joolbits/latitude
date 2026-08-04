package com.example.globe.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofedCavernPlacementTest {

    @Test
    void returnsExactRoofDistanceFromOneBlockThroughTheInclusiveBoundary() {
        int originY = 10;
        int oneBlock = RoofedCavernColumnGate.findRoof(originY,
                originY + RoofedCavernPlacement.MAX_ROOF_RISE,
                y -> y == originY + 1 ? RoofedCavernColumnGate.STURDY_UNDERSIDE
                        : RoofedCavernColumnGate.AIR_OR_WATER);
        assertTrue(RoofedCavernColumnGate.isRoofed(oneBlock));
        assertEquals(RoofedCavernColumnGate.Outcome.ACCEPT, RoofedCavernColumnGate.outcome(oneBlock));
        assertEquals(1, RoofedCavernColumnGate.roofDistance(oneBlock),
                "the closest roof is one block above the candidate");

        int tallGallery = RoofedCavernColumnGate.findRoof(originY,
                originY + 64,
                y -> y == originY + 64 ? RoofedCavernColumnGate.STURDY_UNDERSIDE
                        : RoofedCavernColumnGate.AIR_OR_WATER);
        assertTrue(RoofedCavernColumnGate.isRoofed(tallGallery),
                "a 64-block roofed gallery must survive the old 24/32-block limit");
        assertEquals(64, RoofedCavernColumnGate.roofDistance(tallGallery));

        int boundary = RoofedCavernColumnGate.findRoof(originY,
                originY + RoofedCavernPlacement.MAX_ROOF_RISE,
                y -> y == originY + RoofedCavernPlacement.MAX_ROOF_RISE
                        ? RoofedCavernColumnGate.STURDY_UNDERSIDE : RoofedCavernColumnGate.AIR_OR_WATER);
        assertTrue(RoofedCavernColumnGate.isRoofed(boundary),
                "the bounded 128-block roof is still a valid roof witness");
        assertEquals(RoofedCavernPlacement.MAX_ROOF_RISE, RoofedCavernColumnGate.roofDistance(boundary));
    }

    @Test
    void rejectsOpenSkyAndSolidInterruptions() {
        int originY = 10;
        int openSky = RoofedCavernColumnGate.findRoof(originY,
                originY + RoofedCavernPlacement.MAX_ROOF_RISE,
                y -> RoofedCavernColumnGate.AIR_OR_WATER);
        assertEquals(RoofedCavernColumnGate.REJECT_NO_ROOF_WITHIN_BOUND, openSky);
        assertEquals(RoofedCavernColumnGate.Outcome.REJECT_NO_ROOF_WITHIN_BOUND,
                RoofedCavernColumnGate.outcome(openSky));
        assertEquals(RoofedCavernColumnGate.NO_ROOF, RoofedCavernColumnGate.roofDistance(openSky),
                "an all-air column is open sky, not a roofed cavern");
        int outOfBound = RoofedCavernColumnGate.findRoof(originY,
                originY + RoofedCavernPlacement.MAX_ROOF_RISE,
                y -> y == originY + 129 ? RoofedCavernColumnGate.STURDY_UNDERSIDE
                        : RoofedCavernColumnGate.AIR_OR_WATER);
        assertEquals(RoofedCavernColumnGate.REJECT_NO_ROOF_WITHIN_BOUND, outOfBound);
        assertEquals(RoofedCavernColumnGate.Outcome.REJECT_NO_ROOF_WITHIN_BOUND,
                RoofedCavernColumnGate.outcome(outOfBound));
        assertEquals(RoofedCavernColumnGate.NO_ROOF, RoofedCavernColumnGate.roofDistance(outOfBound),
                "a roof beyond the 128-block bound cannot qualify");
        int obstructed = RoofedCavernColumnGate.findRoof(originY, originY + 64,
                y -> y == originY + 20 ? 0
                        : y == originY + 40 ? RoofedCavernColumnGate.STURDY_UNDERSIDE
                        : RoofedCavernColumnGate.AIR_OR_WATER);
        assertEquals(RoofedCavernColumnGate.REJECT_NO_ROOF_WITHIN_BOUND, obstructed);
        assertEquals(RoofedCavernColumnGate.Outcome.REJECT_NO_ROOF_WITHIN_BOUND,
                RoofedCavernColumnGate.outcome(obstructed));
        assertEquals(RoofedCavernColumnGate.NO_ROOF, RoofedCavernColumnGate.roofDistance(obstructed),
                "a solid interruption before the roof is not a valid open cavern column");
        int originBlocked = RoofedCavernColumnGate.findRoof(originY, originY + 64,
                y -> y == originY ? 0 : RoofedCavernColumnGate.AIR_OR_WATER);
        assertEquals(RoofedCavernColumnGate.REJECT_ORIGIN_NOT_AIR_WATER, originBlocked);
        assertEquals(RoofedCavernColumnGate.Outcome.REJECT_ORIGIN_NOT_AIR_WATER,
                RoofedCavernColumnGate.outcome(originBlocked));
        assertEquals(RoofedCavernColumnGate.NO_ROOF, RoofedCavernColumnGate.roofDistance(originBlocked),
                "the candidate itself must begin in air or water");
    }

    @Test
    void debugLogContractPinsTagAndOrderedFields() {
        assertEquals("[LAT][ROOFED_CAVERN] outcome={} originX={} originY={} originZ={} biome={} "
                        + "skyVisible={} roofDistance={}",
                RoofedCavernPlacement.DEBUG_LOG_TEMPLATE);
    }

    @Test
    void biomePreviewForwardsRoofDebugExactlyOnceAndDefaultsOff() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));
        int blockStart = buildScript.indexOf("        biomePreview {");
        int blockEnd = buildScript.indexOf("        biomeSeedSearch {", blockStart);
        assertTrue(blockStart >= 0 && blockEnd > blockStart, "biomePreview run block must be discoverable");
        String biomePreviewBlock = buildScript.substring(blockStart, blockEnd);
        String forwarding =
                "vmArg \"-Dlatitude.debugRoofedCavern=${System.getProperty('latitude.debugRoofedCavern', 'false')}\"";
        long occurrences = biomePreviewBlock.lines().filter(line -> line.contains(forwarding)).count();
        assertEquals(1, occurrences, "biomePreview must forward the roof diagnostic exactly once, default false");
    }
}
