package com.example.globe.world;

import com.example.globe.core.HiddenChamberPlan;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM contract tripwires for the {@code globe:hidden_glacial_chamber} world layer -- registration
 * wiring, both JSON halves, the biome swap it performs, the resources it must NOT have deleted, and the
 * source-level laws the feature itself has to keep (one rarity call site, live debug rows, a total role
 * vocabulary). No server boot: a parse or wiring failure here is cheaper than one at world creation.
 */
class HiddenGlacialChamberFeatureContractTest {

    private static final String ID = "globe:hidden_glacial_chamber";

    private static JsonObject load(String resourcePath) {
        InputStream stream = HiddenGlacialChamberFeatureContractTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> featureStep(JsonObject biome, int step) {
        JsonArray features = biome.getAsJsonArray("features");
        List<String> ids = new ArrayList<>();
        features.get(step).getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
        return ids;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int cursor = haystack.indexOf(needle);
        while (cursor >= 0) {
            count++;
            cursor = haystack.indexOf(needle, cursor + needle.length());
        }
        return count;
    }

    private static String featureSource() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/example/globe/world/HiddenGlacialChamberFeature.java"));
    }

    @Test
    void modInitRegistersTheFeatureBeforeRegistryFreeze() throws IOException {
        String globeMod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(globeMod.contains("com.example.globe.world.HiddenGlacialChamberFeature.register();"),
                "a datapack configured_feature naming an unregistered type hard-fails at load: "
                        + "GlobeMod must register the chamber feature unconditionally at init");
        assertTrue(globeMod.indexOf("com.example.globe.world.RoofedCavernPlacement.register();")
                        < globeMod.indexOf("com.example.globe.world.HiddenGlacialChamberFeature.register();"),
                "the chamber registration sits with the other worldgen registrations, after the placement "
                        + "modifier type");
    }

    @Test
    void bothJsonHalvesParseAndThePlacementIsDeliberatelyEmpty() {
        JsonObject configured = load("/data/globe/worldgen/configured_feature/hidden_glacial_chamber.json");
        assertEquals(ID, configured.get("type").getAsString(),
                "must declare the mod's custom feature type (registered into BuiltInRegistries.FEATURE)");
        assertTrue(configured.has("config"), "a NoneFeatureConfiguration feature still needs an (empty) config");
        assertTrue(configured.getAsJsonObject("config").isEmpty(),
                "config is empty -- NoneFeatureConfiguration.CODEC accepts {}");

        JsonObject placed = load("/data/globe/worldgen/placed_feature/hidden_glacial_chamber.json");
        assertEquals(ID, placed.get("feature").getAsString(),
                "placed feature must reference the configured feature by id");
        assertTrue(placed.getAsJsonArray("placement").isEmpty(),
                "the chamber owns its whole chunk and scans it deterministically from the origin: an "
                        + "in_square would randomise the origin and break that determinism");
    }

    @Test
    void theBiomeSwapPutsTheChamberInTheTrapsSlotAndUnwiresTheBarrensRoof() {
        JsonObject caves = load("/data/globe/worldgen/biome/glacial_caves.json");
        String cavesRaw = caves.toString();
        assertEquals(1, occurrences(cavesRaw, "\"" + ID + "\""),
                "glacial_caves lists the chamber exactly once");
        assertEquals(0, occurrences(cavesRaw, "\"globe:cave_drop_trap\""),
                "the drop trap's biome membership ended with this pass");

        JsonObject barrens = load("/data/globe/worldgen/biome/polar_barrens.json");
        assertEquals(0, occurrences(barrens.toString(), "\"globe:powder_crevasse_roof\""),
                "the surface powder roof is unwired from polar_barrens");
        List<String> barrensTopLayer = featureStep(barrens, 10);
        assertEquals("minecraft:freeze_top_layer", barrensTopLayer.get(0),
                "the barrens top-layer step still leads with the freeze pass");
        assertEquals("globe:magma_quench_sweep", barrensTopLayer.get(barrensTopLayer.size() - 1),
                "the magma quench is still the final top-layer writer on the barrens");
    }

    @Test
    void theUnwiredFeaturesKeepEveryShippedResourceForExistingWorlds() {
        // Unwiring ends only biome MEMBERSHIP. Deleting the resources would orphan every chunk that
        // already generated one, so both retired features keep both JSON halves on disk.
        for (String name : List.of("cave_drop_trap", "powder_crevasse_roof")) {
            assertNotNull(HiddenGlacialChamberFeatureContractTest.class.getResourceAsStream(
                            "/data/globe/worldgen/configured_feature/" + name + ".json"),
                    name + ": the configured_feature JSON must stay shipped");
            assertNotNull(HiddenGlacialChamberFeatureContractTest.class.getResourceAsStream(
                            "/data/globe/worldgen/placed_feature/" + name + ".json"),
                    name + ": the placed_feature JSON must stay shipped");
        }
    }

    @Test
    void rarityHasExactlyOneCallSiteAndTheDebugRowsAreOnTheShippingPath() throws IOException {
        String source = featureSource();
        assertEquals(1, occurrences(source, "occurrenceGate"),
                "the occurrence gate is THE single rarity and must be consulted exactly once, after the "
                        + "plan is accepted -- a second call site would let rarity change eligibility");
        assertTrue(source.contains("[LAT][CHAMBER]"),
                "the census rows carry the [LAT][CHAMBER] prefix the audit greps for");
        assertTrue(source.contains("latitude.debugGlacialDressing") && source.contains("latitude.debugCollapse"),
                "both the dedicated and the backward-compatible debug switches must arm the rows");
        for (String counter : List.of("noEntrance", "unsafeVolume", "noExit",
                "rarityRejected", "completedChamber", "abortedRolledBack")) {
            assertTrue(source.contains(counter),
                    "the process-cumulative counter " + counter + " must exist and be reported");
        }
        assertTrue(source.contains("countersSnapshot"),
                "the audit needs a public snapshot accessor for the counters");
        // The old drop-trap counters were dead code behind an early return. Every outcome the row can
        // report must actually be constructed on a live path, and every exit from place() must emit.
        for (HiddenGlacialChamberFeature.Outcome outcome
                : HiddenGlacialChamberFeature.Outcome.values()) {
            assertTrue(source.contains("Outcome." + outcome.name()),
                    "no live path ever reports outcome " + outcome.name());
        }
        assertTrue(occurrences(source, "emit(chunkX, chunkZ,") >= 6,
                "every exit from place() -- both gate-offs, the rejection, rarity, success, and rollback -- "
                        + "needs its own live census row on the shipping path");
        assertFalse(source.contains("private static final boolean DEBUG = false"),
                "the debug switch must be read from the system properties, never hard-disabled");
    }

    @Test
    void theRoleVocabularyIsTotalOverEveryPlannedRole() throws IOException {
        String source = featureSource();
        for (HiddenChamberPlan.CellRole role : HiddenChamberPlan.CellRole.values()) {
            assertTrue(source.contains("CellRole." + role.name()),
                    "the role-to-block map must cover " + role.name()
                            + " -- an unmapped role would silently write nothing");
        }
    }
}
