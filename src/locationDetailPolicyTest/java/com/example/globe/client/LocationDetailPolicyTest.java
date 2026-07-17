package com.example.globe.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocationDetailPolicyTest {
    public static void main(String[] args) throws Exception {
        modeCycleIsExact();
        persistedFlagsCoverAllModesAndLegacyValues();
        compositionCoversAllModesInBiomeThenZoneOrder();
        biomeIdsBecomePlayerFacingTitleCase();
        staticIntegrationProofsHold();
        System.out.println("LOCATION_DETAIL_POLICY_TEST_PASS");
    }

    private static void modeCycleIsExact() {
        var modes = LocationDetailPolicy.Mode.values();
        assertEquals(4, modes.length, "mode cycle has exactly four entries");
        assertEquals(LocationDetailPolicy.Mode.OFF, modes[0], "Off is first and default");
        assertEquals("Off", modes[0].label(), "Off label");
        assertEquals(LocationDetailPolicy.Mode.BIOME, modes[1], "Biome is second");
        assertEquals("Biome", modes[1].label(), "Biome label");
        assertEquals(LocationDetailPolicy.Mode.ZONE, modes[2], "Zone is third");
        assertEquals("Zone", modes[2].label(), "Zone label");
        assertEquals(LocationDetailPolicy.Mode.BIOME_AND_ZONE, modes[3], "combined is fourth");
        assertEquals("Biome + Zone", modes[3].label(), "combined label");
        assertEquals(LocationDetailPolicy.Mode.OFF, LocationDetailPolicy.DEFAULT_MODE, "default is Off");
    }

    private static void persistedFlagsCoverAllModesAndLegacyValues() {
        assertEquals(
                LocationDetailPolicy.Mode.OFF,
                LocationDetailPolicy.fromPersistedFlags(false, false),
                "both flags false maps to Off");
        assertEquals(
                LocationDetailPolicy.Mode.BIOME,
                LocationDetailPolicy.fromPersistedFlags(true, false),
                "biome-only flags map to Biome");
        assertEquals(
                LocationDetailPolicy.Mode.ZONE,
                LocationDetailPolicy.fromPersistedFlags(false, true),
                "zone-only flags map to Zone");
        assertEquals(
                LocationDetailPolicy.Mode.BIOME_AND_ZONE,
                LocationDetailPolicy.fromPersistedFlags(true, true),
                "both flags true map to Biome + Zone");

        assertEquals(
                LocationDetailPolicy.Mode.OFF,
                LocationDetailPolicy.fromPersistedFlags(false, false),
                "legacy displayZoneInHud=false maps to Off when new biome flag is absent/default false");
        assertEquals(
                LocationDetailPolicy.Mode.ZONE,
                LocationDetailPolicy.fromPersistedFlags(false, true),
                "legacy displayZoneInHud=true maps to Zone when new biome flag is absent/default false");
    }

    private static void compositionCoversAllModesInBiomeThenZoneOrder() {
        assertEquals(
                null,
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.OFF, "Plains", "Tropics"),
                "Off composes no location detail");
        assertEquals(
                "Plains",
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.BIOME, "Plains", "Tropics"),
                "Biome composes only biome");
        assertEquals(
                "Tropics",
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.ZONE, "Plains", "Tropics"),
                "Zone composes only zone");
        assertEquals(
                "Plains \u00b7 Tropics",
                LocationDetailPolicy.compose(
                        LocationDetailPolicy.Mode.BIOME_AND_ZONE,
                        "Plains",
                        "Tropics"),
                "combined detail composes biome before zone as one string");
    }

    private static void biomeIdsBecomePlayerFacingTitleCase() {
        assertEquals(
                "Plains",
                LocationDetailPolicy.titleCaseBiomeId("minecraft:plains"),
                "vanilla namespace is removed");
        assertEquals(
                "Pasture",
                LocationDetailPolicy.titleCaseBiomeId("biomesoplenty:pasture"),
                "mod namespace is removed");
        assertEquals(
                "Old Growth Birch Forest",
                LocationDetailPolicy.titleCaseBiomeId("minecraft:old_growth_birch_forest"),
                "multi-word biome path is title-cased");
    }

    private static void staticIntegrationProofsHold() throws IOException {
        String config = normalize(read("src/main/java/com/example/globe/client/CompassHudConfig.java"));
        assertTrue(
                config.contains("public boolean displayBiomeInHud = false;")
                        && config.contains("public boolean displayZoneInHud = false;"),
                "config persists two default-false booleans");
        assertTrue(
                config.contains("LocationDetailPolicy.fromPersistedFlags(displayBiomeInHud, displayZoneInHud)"),
                "config derives the four-state mode from persisted booleans");
        assertTrue(
                config.contains("displayBiomeInHud = selected.includesBiome();")
                        && config.contains("displayZoneInHud = selected.includesZone();"),
                "mode selection writes both persisted booleans");

        String hud = normalize(read("src/main/java/com/example/globe/client/CompassHud.java"));
        assertTrue(
                occurrences(hud, "locationDetailLabel(client, cfg, true)") >= 2,
                "analog and digital runtime paths consume the same location-detail policy");
        assertTrue(
                hud.contains("latitudeText(client, cfg), locationDetailLabel(client, cfg, true)")
                        && hud.indexOf("drawText(ctx, client, cfg, latText")
                        < hud.indexOf("drawText(ctx, client, cfg, locationDetailText"),
                "digital and analog layouts both place location detail after latitude");
        assertTrue(
                hud.contains("LocationDetailPolicy.compose( cfg.locationDetailMode(), biomeLabel(client), displayZoneName(zoneKey))"),
                "live biome and zone labels compose through the shared policy");
        assertTrue(
                hud.contains("LocationDetailPolicy.titleCaseBiomeId(key.identifier().toString())"),
                "runtime biome id uses the deterministic title-case helper");
        assertTrue(
                occurrences(hud, "sampleLocationDetail(cfg, true)") >= 5
                        && hud.contains("computeAnalogBounds")
                        && hud.contains("sampleLines(cfg)"),
                "analog and digital preview/bounds consume the combined sample unit");
        assertTrue(
                occurrences(hud, "analogLocationGap(cfg, latText)") == 3,
                "analog render, base position, and bounds use one gap calculation");
        assertTrue(
                hud.contains("renderDetachedLocationDetail")
                        && hud.contains("computeLocationDetailBounds")
                        && hud.contains("cfg.zoneOffsetX")
                        && hud.contains("cfg.zoneOffsetY"),
                "the whole selected unit reuses legacy detach anchors and offsets");
        assertTrue(
                !hud.contains("renderDetachedZone") && !hud.contains("computeZoneBounds"),
                "no parallel zone-only detached render/bounds path remains");

        String studio = normalize(read("src/main/java/com/example/globe/client/LatitudeHudStudioScreen.java"));
        assertTrue(
                studio.contains("CycleButton.<LocationDetailPolicy.Mode>builder")
                        && studio.contains(".withValues(LocationDetailPolicy.Mode.values())")
                        && studio.contains("Component.literal(\"Location Detail\")"),
                "HUD Studio exposes the exact policy cycle");
        assertTrue(
                studio.contains("cfg.setLocationDetailMode(value)")
                        && studio.contains("CompassHud.computeLocationDetailBounds(mc, cfg)")
                        && studio.contains("cfg.hasLocationDetail()")
                        && studio.contains("setVisible(wLocationFollow, showCompassControls && CompassHudConfig.get().hasLocationDetail())"),
                "Studio selection, visibility, and detached dragging use the combined mode");
        assertTrue(
                studio.contains("Shows the current biome, latitude zone, both together, or neither beside the compass.")
                        && studio.contains("detach the whole unit for dragging."),
                "Studio tooltips describe all modes and one combined drag unit");
        assertTrue(
                studio.contains("cfg.setLocationDetailMode(LocationDetailPolicy.DEFAULT_MODE)"),
                "HUD Studio reset returns location detail to Off");

        String settings = normalize(read("src/main/java/com/example/globe/client/LatitudeSettingsScreen.java"));
        assertTrue(
                settings.contains("CycleButton.<LocationDetailPolicy.Mode>builder")
                        && settings.contains("cfg.setLocationDetailMode(value)"),
                "simple settings cannot overwrite the four-state mode with a Boolean zone control");
        assertTrue(
                settings.contains("cfg.setLocationDetailMode(LocationDetailPolicy.DEFAULT_MODE)"),
                "simple settings reset also returns location detail to Off");

        String build = normalize(read("build.gradle"));
        assertTrue(
                build.contains("tasks.register('latitudeLocationDetailPolicyTest', JavaExec)")
                        && build.contains("dependsOn tasks.named('latitudeLocationDetailPolicyTest')"),
                "location-detail policy proof is automatically wired into Gradle check/build");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
