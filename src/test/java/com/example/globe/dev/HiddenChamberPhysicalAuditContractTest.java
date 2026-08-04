package com.example.globe.dev;

import com.example.globe.core.HiddenChamberScan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM contracts for the default-off hidden-chamber physical audit: the window arithmetic every
 * invocation is validated against, the property surface the architect drives it with, and the wiring that
 * decides whether a launch argument ever reaches the forked server JVM at all.
 */
class HiddenChamberPhysicalAuditContractTest {

    private static final String PREFIX = "latdev.chamberAudit";

    private static final List<String> PROPERTY_SUFFIXES = List.of(
            "", ".targetChunkMinX", ".targetChunkMinZ", ".targetChunkSpan",
            ".scanChunkMinX", ".scanChunkMinZ", ".scanChunkSpan",
            ".centerX", ".centerZ", ".radiusChunks", ".out", ".settleTicks",
            ".mobAssay", ".locatorCheck", ".expectTheme", ".minCompleted", ".expectGallerySpires");

    /** Every assertion the report is allowed to turn green on. A silent drop here is a silent weakening. */
    private static final List<String> REPORT_ASSERTIONS = List.of(
            "allRequestedChunksFull",
            "atLeastMinCompletedInTarget",
            "everyChamberAboveY0",
            "everyChamberAllGlacialQuarts",
            "mobAssayPass",
            "locatorConsistent",
            "zeroFailedWriteBatches",
            "zeroRolledBackWriteBatches",
            "magmaResidualZero",
            "noOpenAirSpires",
            "gallerySpiresBeyond32",
            "expectedThemeMatched");

    @TempDir
    Path temporaryDirectory;

    @Test
    void historicalTargetAndScanWindowsRemainExactly256And289Chunks() {
        HiddenChamberPhysicalAudit.Config config = config(-8, -8, 16, -8, -8, 17, 0, 0, 8);

        assertTrue(config.errors().isEmpty(), config.errors().toString());
        assertEquals(256, config.targetChunkCount());
        assertEquals(289, config.scanChunkCount());
        assertEquals(256, config.toJson().get("targetChunkCount").getAsInt());
        assertEquals(289, config.toJson().get("scanChunkCount").getAsInt());

        HiddenChamberPhysicalAudit.Config wrongHalo = config(-8, -8, 16, -8, -8, 16, 0, 0, 8);
        assertTrue(wrongHalo.errors().stream().anyMatch(error ->
                        error.contains("historical target span 16 requires scan span 17")),
                wrongHalo.errors().toString());
    }

    @Test
    void windowArithmeticRejectsEveryBrokenSpanContainmentAndRadiusReconciliation() {
        assertRejects(config(-8, -8, 0, -8, -8, 17, 0, 0, 8), "targetChunkSpan must be 1..64");
        assertRejects(config(-8, -8, 65, -8, -8, 17, 0, 0, 8), "targetChunkSpan must be 1..64");
        assertRejects(config(-8, -8, 1, -8, -8, 66, 0, 0, 8), "scanChunkSpan must be 1..65");
        assertRejects(config(-8, -8, 1, -8, -8, 17, 0, 0, 33), "radiusChunks must be 0..32");
        assertRejects(config(-8, -8, 1, -8, -8, 17, 0, 0, -1), "radiusChunks must be 0..32");

        // scanSpan and radius are one number written twice; they must reconcile.
        assertRejects(config(-8, -8, 1, -8, -8, 15, 0, 0, 8),
                "scanChunkSpan must equal 2*radiusChunks+1");
        // ...and the scan corner is fixed by the centre block and that radius, never declared freely.
        assertRejects(config(-8, -8, 1, -7, -8, 17, 0, 0, 8),
                "scan chunk minimum must match centerX/centerZ and radiusChunks");
        assertRejects(config(-8, -8, 1, -8, -7, 17, 0, 0, 8),
                "scan chunk minimum must match centerX/centerZ and radiusChunks");
        // The centre is a BLOCK coordinate: floorDiv by 16 is the law on the negative side too.
        assertTrue(config(-16, -16, 4, -17, -17, 17, -129, -129, 8).errors().isEmpty(),
                "floorDiv(-129,16)-8 == -17 must reconcile");

        // A target square that leans out of the scan square is half-read by construction.
        assertRejects(config(-9, -8, 16, -8, -8, 17, 0, 0, 8),
                "target chunk square must be contained by the scan square");
        assertRejects(config(-8, -8, 18, -8, -8, 17, 0, 0, 8),
                "target chunk square must be contained by the scan square");

        assertRejects(HiddenChamberPhysicalAudit.Config.validated(
                        -8, -8, 16, -8, -8, 17, 0, 0, 8, out(), -1, true, true, null, 1, false),
                "settleTicks must be 0..24000");
        assertRejects(HiddenChamberPhysicalAudit.Config.validated(
                        -8, -8, 16, -8, -8, 17, 0, 0, 8, out(), 24_001, true, true, null, 1, false),
                "settleTicks must be 0..24000");
        assertRejects(HiddenChamberPhysicalAudit.Config.validated(
                        -8, -8, 16, -8, -8, 17, 0, 0, 8, out(), 100, true, true, null, -1, false),
                "minCompleted must be non-negative");
    }

    @Test
    void everyDocumentedPropertyNameParsesIntoTheConfigItNames() {
        Path fallback = temporaryDirectory.resolve("fallback.json");
        Path declared = temporaryDirectory.resolve("declared.json");
        withProperties(Map.ofEntries(
                Map.entry(PREFIX + ".targetChunkMinX", "-8"),
                Map.entry(PREFIX + ".targetChunkMinZ", "-8"),
                Map.entry(PREFIX + ".targetChunkSpan", "16"),
                Map.entry(PREFIX + ".scanChunkMinX", "-8"),
                Map.entry(PREFIX + ".scanChunkMinZ", "-8"),
                Map.entry(PREFIX + ".scanChunkSpan", "17"),
                Map.entry(PREFIX + ".centerX", "0"),
                Map.entry(PREFIX + ".centerZ", "0"),
                Map.entry(PREFIX + ".radiusChunks", "8"),
                Map.entry(PREFIX + ".out", declared.toString()),
                Map.entry(PREFIX + ".settleTicks", "240"),
                Map.entry(PREFIX + ".mobAssay", "false"),
                Map.entry(PREFIX + ".locatorCheck", "false"),
                Map.entry(PREFIX + ".expectTheme", "frigid_lake"),
                Map.entry(PREFIX + ".minCompleted", "3"),
                Map.entry(PREFIX + ".expectGallerySpires", "true")), () -> {
                    HiddenChamberPhysicalAudit.Config config =
                            HiddenChamberPhysicalAudit.Config.read(fallback);
                    assertTrue(config.errors().isEmpty(), config.errors().toString());
                    assertEquals(-8, config.targetChunkMinX());
                    assertEquals(-8, config.targetChunkMinZ());
                    assertEquals(16, config.targetChunkSpan());
                    assertEquals(-8, config.scanChunkMinX());
                    assertEquals(-8, config.scanChunkMinZ());
                    assertEquals(17, config.scanChunkSpan());
                    assertEquals(0, config.centerX());
                    assertEquals(0, config.centerZ());
                    assertEquals(8, config.radiusChunks());
                    assertEquals(declared.toAbsolutePath().normalize(), config.out());
                    assertEquals(240, config.settleTicks());
                    assertFalse(config.mobAssay());
                    assertFalse(config.locatorCheck());
                    assertEquals("frigid_lake", config.expectTheme());
                    assertEquals(HiddenChamberScan.Theme.FRIGID_LAKE, config.expectedTheme());
                    assertEquals(3, config.minCompleted());
                    assertTrue(config.expectGallerySpires());
                });
    }

    @Test
    void aBlankForwardIsTheDefaultAndNeverASilentlyDisarmedKnob() {
        Path fallback = temporaryDirectory.resolve("fallback.json");
        Path declared = temporaryDirectory.resolve("declared.json");
        // build.gradle forwards every optional knob with an EMPTY value when the caller omits it, and
        // Boolean.parseBoolean("") is false -- so a naive read would silently disarm the mob assay and the
        // locator cross-check on every ordinary invocation.
        Map<String, String> blanks = Map.ofEntries(
                Map.entry(PREFIX + ".targetChunkMinX", "-8"),
                Map.entry(PREFIX + ".targetChunkMinZ", "-8"),
                Map.entry(PREFIX + ".targetChunkSpan", "16"),
                Map.entry(PREFIX + ".scanChunkMinX", "-8"),
                Map.entry(PREFIX + ".scanChunkMinZ", "-8"),
                Map.entry(PREFIX + ".scanChunkSpan", "17"),
                Map.entry(PREFIX + ".centerX", "0"),
                Map.entry(PREFIX + ".centerZ", "0"),
                Map.entry(PREFIX + ".radiusChunks", "8"),
                Map.entry(PREFIX + ".out", declared.toString()),
                Map.entry(PREFIX + ".settleTicks", ""),
                Map.entry(PREFIX + ".mobAssay", ""),
                Map.entry(PREFIX + ".locatorCheck", ""),
                Map.entry(PREFIX + ".expectTheme", ""),
                Map.entry(PREFIX + ".minCompleted", ""),
                Map.entry(PREFIX + ".expectGallerySpires", ""));
        withProperties(blanks, () -> {
            HiddenChamberPhysicalAudit.Config config = HiddenChamberPhysicalAudit.Config.read(fallback);
            assertTrue(config.errors().isEmpty(), config.errors().toString());
            assertEquals(100, config.settleTicks());
            assertTrue(config.mobAssay(), "a blank forward must leave the mob assay ARMED");
            assertTrue(config.locatorCheck(), "a blank forward must leave the locator check ARMED");
            assertFalse(config.expectGallerySpires());
            assertEquals(1, config.minCompleted());
            assertNull(config.expectTheme());
            assertNull(config.expectedTheme());
        });
    }

    @Test
    void requiredPropertiesAndTheThemeVocabularyFailClosed() {
        Path fallback = temporaryDirectory.resolve("fallback.json");
        withProperties(Map.of(), () -> {
            HiddenChamberPhysicalAudit.Config config = HiddenChamberPhysicalAudit.Config.read(fallback);
            for (String required : List.of("targetChunkMinX", "targetChunkMinZ", "targetChunkSpan",
                    "scanChunkMinX", "scanChunkMinZ", "scanChunkSpan",
                    "centerX", "centerZ", "radiusChunks")) {
                assertTrue(config.errors().contains(PREFIX + "." + required + " is required"),
                        "missing required-property error for " + required + ": " + config.errors());
            }
            assertTrue(config.errors().contains(PREFIX + ".out is required"), config.errors().toString());
            assertEquals(fallback.toAbsolutePath().normalize(), config.out(),
                    "a config-error report must still have somewhere to be written");
        });

        withProperties(Map.of(
                PREFIX + ".targetChunkMinX", "-8", PREFIX + ".targetChunkMinZ", "-8",
                PREFIX + ".targetChunkSpan", "16", PREFIX + ".scanChunkMinX", "-8",
                PREFIX + ".scanChunkMinZ", "-8", PREFIX + ".scanChunkSpan", "17",
                PREFIX + ".centerX", "0", PREFIX + ".centerZ", "0",
                PREFIX + ".radiusChunks", "8", PREFIX + ".out", "out.json"), () -> {
                    System.setProperty(PREFIX + ".expectTheme", "cathedral");
                    System.setProperty(PREFIX + ".mobAssay", "yes");
                    HiddenChamberPhysicalAudit.Config config =
                            HiddenChamberPhysicalAudit.Config.read(temporaryDirectory.resolve("f.json"));
                    assertTrue(config.errors().stream().anyMatch(error ->
                                    error.contains("expectTheme must be ice_cathedral")),
                            config.errors().toString());
                    assertTrue(config.errors().stream().anyMatch(error ->
                            error.contains("mobAssay must be true or false")), config.errors().toString());
                    System.clearProperty(PREFIX + ".expectTheme");
                    System.clearProperty(PREFIX + ".mobAssay");
                });

        /*
         * The same blank-versus-garbage split for the INT knobs, which the boolean and theme cases above
         * left untested. Pass 4 proved a blank forward is the DEFAULT (settleTicks -> 100, minCompleted ->
         * 1); this is the other half, and the two together are what stop a typo from being read as a
         * deliberate value. A malformed int is a CONFIG ERROR, never a silent fall back to the default and
         * never a zero -- an audit that quietly ran with settleTicks=0 because someone wrote "1oo" would
         * report on an unsettled world and call it evidence.
         */
        withProperties(Map.of(
                PREFIX + ".targetChunkMinX", "-8", PREFIX + ".targetChunkMinZ", "-8",
                PREFIX + ".targetChunkSpan", "16", PREFIX + ".scanChunkMinX", "-8",
                PREFIX + ".scanChunkMinZ", "-8", PREFIX + ".scanChunkSpan", "17",
                PREFIX + ".centerX", "0", PREFIX + ".centerZ", "0",
                PREFIX + ".radiusChunks", "8", PREFIX + ".out", "out.json"), () -> {
                    System.setProperty(PREFIX + ".settleTicks", "1oo");
                    System.setProperty(PREFIX + ".minCompleted", "many");
                    HiddenChamberPhysicalAudit.Config config =
                            HiddenChamberPhysicalAudit.Config.read(temporaryDirectory.resolve("f.json"));
                    assertTrue(config.errors().contains(PREFIX + ".settleTicks must be an integer"),
                            config.errors().toString());
                    assertTrue(config.errors().contains(PREFIX + ".minCompleted must be an integer"),
                            config.errors().toString());
                    System.clearProperty(PREFIX + ".settleTicks");
                    System.clearProperty(PREFIX + ".minCompleted");
                });

        // A malformed REQUIRED int is a config error too, not a silently-zero window bound.
        withProperties(Map.of(
                PREFIX + ".targetChunkMinX", "-8", PREFIX + ".targetChunkMinZ", "-8",
                PREFIX + ".targetChunkSpan", "sixteen", PREFIX + ".scanChunkMinX", "-8",
                PREFIX + ".scanChunkMinZ", "-8", PREFIX + ".scanChunkSpan", "17",
                PREFIX + ".centerX", "0", PREFIX + ".centerZ", "0",
                PREFIX + ".radiusChunks", "8", PREFIX + ".out", "out.json"), () -> {
                    HiddenChamberPhysicalAudit.Config config =
                            HiddenChamberPhysicalAudit.Config.read(temporaryDirectory.resolve("f.json"));
                    assertTrue(config.errors().contains(PREFIX + ".targetChunkSpan must be an integer"),
                            config.errors().toString());
                });

        for (Map.Entry<String, HiddenChamberScan.Theme> named : Map.of(
                "ice_cathedral", HiddenChamberScan.Theme.ICE_CATHEDRAL,
                "frigid_lake", HiddenChamberScan.Theme.FRIGID_LAKE,
                "lost_expedition", HiddenChamberScan.Theme.LOST_EXPEDITION).entrySet()) {
            assertEquals(named.getValue(), HiddenChamberPhysicalAudit.Config.validated(
                            -8, -8, 16, -8, -8, 17, 0, 0, 8, out(), 100, true, true,
                            named.getKey(), 1, false)
                    .expectedTheme());
        }
    }

    @Test
    void fullChunkRequestsUseDeterministicZMajorThenXMinorOrder() {
        List<HiddenChamberPhysicalAudit.ChunkCoordinate> chunks =
                HiddenChamberPhysicalAudit.requestedChunks(-8, -8, 17);

        assertEquals(289, chunks.size());
        assertEquals(new HiddenChamberPhysicalAudit.ChunkCoordinate(-8, -8), chunks.get(0));
        assertEquals(new HiddenChamberPhysicalAudit.ChunkCoordinate(-7, -8), chunks.get(1));
        assertEquals(new HiddenChamberPhysicalAudit.ChunkCoordinate(8, -8), chunks.get(16));
        assertEquals(new HiddenChamberPhysicalAudit.ChunkCoordinate(-8, -7), chunks.get(17));
        assertEquals(new HiddenChamberPhysicalAudit.ChunkCoordinate(8, 8), chunks.getLast());
    }

    @Test
    void everyReportAssertionAndFailClosedLawIsPinnedInSource() throws Exception {
        String audit = Files.readString(auditSource());

        for (String assertion : REPORT_ASSERTIONS) {
            assertTrue(audit.contains("\"" + assertion + "\""),
                    "the report lost its " + assertion + " assertion");
        }
        assertTrue(audit.contains("hidden-chamber-physical-audit-v1"), "the schema name is the report's id");

        // Physical, not claimed: the reconstruction is the shared pure scanner over real blocks.
        assertTrue(audit.contains("HiddenChamberScan.scan("),
                "the verdict must come from the shared block reconstruction");
        assertTrue(audit.contains("HiddenChamberScan.classifyPatch("),
                "the locator cross-check must re-run the shipped classification");
        assertTrue(audit.contains("ChunkStatus.FULL, true"),
                "the proof must request its own FULL generation window");
        assertTrue(audit.contains("getChunkNow"),
                "reconstruction reads resident chunks and must never regenerate mid-sweep");
        assertFalse(audit.contains("setBlock"), "the proof must never repair blocks by hand");

        // Counters and telemetry are evidence, never the verdict -- but the window must be opened and closed.
        assertTrue(audit.contains("HiddenGlacialChamberFeature.resetCounters()"));
        assertTrue(audit.contains("WriteTelemetry.beginSession"));
        assertTrue(audit.contains("WriteTelemetry.endSession"));

        // The stored-quart idiom: LevelReader#getBiome's fuzzy sampler can answer for a neighbouring quart.
        assertTrue(audit.contains("getNoiseBiome("), "biome containment must read the stored quart");
        assertFalse(audit.contains("getBiome("),
                "LevelReader#getBiome's eight-quart sampler must never decide containment");

        // The assay is a REAL server mob on a REAL path, watched down the shaft.
        assertTrue(audit.contains("EntitySpawnReason.COMMAND"));
        assertTrue(audit.contains("setOnGround(true)"),
                "an unticked entity reports airborne and the navigator refuses to path for it");
        assertTrue(audit.contains("setChunkForced"), "the descent watch needs its window pinned");
        assertTrue(audit.contains("releaseForcedChunks()"), "every forced chunk must be released");

        // Fail closed, and always leave a report behind.
        assertTrue(audit.contains("server.halt(false)"), "the report write must be followed by a clean halt");
        assertTrue(audit.contains("catch (Throwable writeFailure)"),
                "a primary failure must still make a best-effort JSON write");
        assertTrue(audit.contains("report.addProperty(\"status\", \"failed\")"),
                "the report is born failed and is only promoted by evidence");
    }

    @Test
    void runnerDispatchOrderAndBuildForwardingArePinned() throws Exception {
        String runner = Files.readString(
                Path.of("src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java"));
        String gradle = Files.readString(Path.of("build.gradle"));

        int chamber = runner.indexOf("HiddenChamberPhysicalAudit.isEnabled()");
        int surfaceTrap = runner.indexOf("SurfaceTrapPhysicalAudit.isEnabled()");
        assertTrue(surfaceTrap >= 0 && chamber > surfaceTrap,
                "the chamber audit dispatches immediately after the surface-trap physical proof");
        for (String laterGeneratingMode : List.of(
                "TerrainProofHarness.isTriggered()",
                "System.getProperty(PROBE_PROP_KEY",
                "CaveDropTrapFullChunkAudit.isEnabled()",
                "runAlpineAuditAndStop(server)",
                "runExportAndStop(server, config)")) {
            int later = runner.indexOf(laterGeneratingMode);
            assertTrue(later >= 0, "missing later dispatch entry " + laterGeneratingMode);
            assertTrue(chamber < later, "the chamber audit must dispatch before " + laterGeneratingMode);
        }

        for (String suffix : PROPERTY_SUFFIXES) {
            assertTrue(gradle.contains("-Dlatdev.chamberAudit" + suffix + "="),
                    "biomePreview must forward chamber-audit property '" + suffix + "'");
        }
        // The feature reads this one at class-init IN THE FORKED JVM; unforwarded, theme forcing is a no-op.
        assertTrue(gradle.contains("-Dlatitude.chamber.forceTheme="),
                "biomePreview must forward latitude.chamber.forceTheme into the forked server JVM");
    }

    /* ---------------------------------------------------------------------------------------------- */

    private HiddenChamberPhysicalAudit.Config config(
            int targetMinX, int targetMinZ, int targetSpan,
            int scanMinX, int scanMinZ, int scanSpan,
            int centerX, int centerZ, int radius) {
        return HiddenChamberPhysicalAudit.Config.validated(
                targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                centerX, centerZ, radius, out(), 100, true, true, null, 1, false);
    }

    private Path out() {
        return temporaryDirectory.resolve("hidden-chamber-physical-audit.json");
    }

    private static Path auditSource() {
        return Path.of("src/main/java/com/example/globe/dev/HiddenChamberPhysicalAudit.java");
    }

    private static void assertRejects(HiddenChamberPhysicalAudit.Config config, String expectedError) {
        assertTrue(config.errors().contains(expectedError),
                "expected rejection '" + expectedError + "' but saw " + config.errors());
    }

    /** System properties are process-global; every knob this test touches is restored afterwards. */
    private static void withProperties(Map<String, String> properties, Runnable body) {
        List<String> keys = new ArrayList<>();
        for (String suffix : PROPERTY_SUFFIXES) {
            keys.add(PREFIX + suffix);
        }
        Map<String, String> saved = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            String previous = System.getProperty(key);
            if (previous != null) {
                saved.put(key, previous);
            }
            System.clearProperty(key);
        }
        try {
            properties.forEach(System::setProperty);
            body.run();
        } finally {
            for (String key : keys) {
                System.clearProperty(key);
            }
            saved.forEach(System::setProperty);
        }
    }
}
