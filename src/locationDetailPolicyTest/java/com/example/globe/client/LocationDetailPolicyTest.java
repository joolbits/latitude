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
        customBiomeSourcesAreOptionalAndPlayerFacing();
        defaultDetachedBoundsStayReachableAtAcceptedGuiSize();
        locationTextScalePolicyIsBoundedAndIndependent();
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
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.OFF, "Plains", "Tropical"),
                "Off composes no location detail");
        assertEquals(
                "Plains",
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.BIOME, "Plains", "Tropical"),
                "Biome composes only biome");
        assertEquals(
                "Tropical",
                LocationDetailPolicy.compose(LocationDetailPolicy.Mode.ZONE, "Plains", "Tropical"),
                "Zone composes only zone");
        assertEquals(
                "Plains \u00b7 Tropical",
                LocationDetailPolicy.compose(
                        LocationDetailPolicy.Mode.BIOME_AND_ZONE,
                        "Plains",
                        "Tropical"),
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

    private static void customBiomeSourcesAreOptionalAndPlayerFacing() {
        assertEquals(
                "Snowy Shield",
                LocationDetailPolicy.biomeLabel("biomesoplenty:snowy_shield", false),
                "source toggle off preserves the compact biome-only label");
        assertEquals(
                "Snowy Shield \u00b7 BIOMES O' PLENTY",
                LocationDetailPolicy.biomeLabel("biomesoplenty:snowy_shield", true),
                "known custom provider is named when the toggle is on");
        assertEquals(
                "Plains",
                LocationDetailPolicy.biomeLabel("minecraft:plains", true),
                "vanilla biomes stay unlabelled");
        assertEquals(
                "Pale Garden \u00b7 REGIONS UNEXPLORED",
                LocationDetailPolicy.biomeLabel("regions_unexplored:pale_garden", true),
                "generic custom-provider formatting is deterministic");
        assertEquals(
                "Plains \u00b7 VANILLA",
                LocationDetailPolicy.studioPreviewBiomeLabel("minecraft:plains", true),
                "Studio demonstrates the source toggle even when its coherent sample biome is vanilla");
        assertEquals(
                "Plains",
                LocationDetailPolicy.studioPreviewBiomeLabel("minecraft:plains", false),
                "Studio source example disappears when the toggle is off");
    }

    private static void defaultDetachedBoundsStayReachableAtAcceptedGuiSize() {
        int screenW = 427;
        int screenH = 240;
        var detail = centeredTop(screenW, 29, 9);
        var digital = centeredTop(screenW, 52, 15);
        var analog = centeredTop(screenW, 68, 48);

        var separatedDigital = moveDetachedDetail(detail, digital, screenH, true);
        var separatedAnalog = moveDetachedDetail(detail, analog, screenH, true);
        assertTrue(!intersects(digital, separatedDigital),
                "default Digital Detach detail does not overlap at accepted 427x240 GUI size");
        assertTrue(!intersects(analog, separatedAnalog),
                "default Analog Detach detail does not overlap at accepted 427x240 GUI size");
        assertEquals(digital.y + digital.h + 4, separatedDigital.y,
                "default Digital Detach detail uses the fixed four-pixel gap");
        assertEquals(analog.y + analog.h + 4, separatedAnalog.y,
                "default Analog Detach detail uses the fixed four-pixel gap");
        assertEquals(detail, moveDetachedDetail(detail, digital, screenH, false),
                "Follow or an explicit detached placement remains byte-for-byte unchanged");

        var alreadyClear = new Rect(detail.x, 80, detail.w, detail.h);
        assertEquals(alreadyClear, moveDetachedDetail(alreadyClear, digital, screenH, true),
                "a non-intersecting pristine default remains unchanged");

        var nearBottomCompass = new Rect(180, 220, 68, 15);
        var nearBottomDetail = new Rect(199, 226, 29, 9);
        var clampedAbove = moveDetachedDetail(
                nearBottomDetail,
                nearBottomCompass,
                screenH,
                true);
        assertTrue(!intersects(nearBottomCompass, clampedAbove),
                "surface clamp falls back above instead of reintroducing an overlap");
        assertEquals(207, clampedAbove.y,
                "surface-clamped fallback keeps the fixed four-pixel gap");
    }

    private static void staticIntegrationProofsHold() throws IOException {
        String config = normalize(read("src/main/java/com/example/globe/client/CompassHudConfig.java"));
        String numericPolicy = normalize(read("src/main/java/com/example/globe/client/HudTextLayoutPolicy.java"));
        assertTrue(
                config.contains("public boolean displayBiomeInHud = false;")
                        && config.contains("public boolean displayZoneInHud = false;")
                        && config.contains("public boolean showCustomBiomeSource = false;"),
                "config persists two default-false booleans");
        assertTrue(
                config.contains("LocationDetailPolicy.fromPersistedFlags(displayBiomeInHud, displayZoneInHud)"),
                "config derives the four-state mode from persisted booleans");
        assertTrue(
                config.contains("displayBiomeInHud = selected.includesBiome();")
                        && config.contains("displayZoneInHud = selected.includesZone();"),
                "mode selection writes both persisted booleans");
        assertTrue(
                numericPolicy.contains("DEFAULT_LOCATION_TEXT_SCALE = 1.0f")
                        && numericPolicy.contains("LOCATION_TEXT_SCALE_MIN = 0.50f")
                        && numericPolicy.contains("LOCATION_TEXT_SCALE_MAX = 1.25f")
                        && config.contains("public float locationTextScale = DEFAULT_LOCATION_TEXT_SCALE;"),
                "location text scale has a backward-safe 100% default and compact Studio range");
        assertTrue(
                occurrences(config, "locationTextScale = DEFAULT_LOCATION_TEXT_SCALE;") >= 2
                        && config.contains("HudTextLayoutPolicy.sanitizeLocationTextScale(locationTextScale)")
                        && numericPolicy.contains("Float.isFinite(value)")
                        && numericPolicy.contains("Math.round(value * 20.0f) / 20.0f"),
                "missing, reset, non-finite, and non-step-aligned values sanitize safely");

        String hud = normalize(read("src/main/java/com/example/globe/client/CompassHud.java"));
        String currentDigitalContentBody = slice(
                hud,
                "private static DigitalContent currentDigitalContent(",
                "private static String currentDirectionText(");
        String computeBoundsBody = slice(
                hud,
                "public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg)",
                "public static HudPoint computeBasePosition(");
        assertTrue(
                occurrences(hud, "locationDetailLabel(client, cfg, true)") >= 2,
                "analog and digital runtime paths consume the same location-detail policy");
        assertTrue(
                hud.contains("latitudeText(client, cfg), locationDetailLabel(client, cfg, true)")
                        && hud.indexOf("content.latitudeSegment()")
                        < hud.indexOf("content.detailSegment()")
                        && hud.indexOf("drawScaledText(ctx, client, cfg, latText")
                        < hud.indexOf("drawScaledText(ctx, client, cfg, locationDetailText"),
                "digital and analog layouts both place location detail after latitude");
        assertTrue(
                hud.contains("LocationDetailPolicy.compose( cfg.locationDetailMode(), biomeLabel(client, cfg), displayZoneName(zoneKey))"),
                "live biome and zone labels compose through the shared policy");
        assertTrue(
                hud.contains("case \"EQUATOR\", \"TROPICAL\" -> \"Tropical\";")
                        && hud.contains("case \"SUBTROPICAL\" -> \"Subtropical\";")
                        && !hud.contains("\"Tropics\"")
                        && !hud.contains("\"Subtropics\""),
                "runtime and preview HUD use the canonical Tropical and Subtropical labels");
        assertTrue(
                hud.contains("LocationDetailPolicy.biomeLabel(")
                        && hud.contains("cfg.showCustomBiomeSource"),
                "runtime biome id uses the optional provider-aware label helper");
        assertTrue(
                occurrences(hud, "sampleLocationDetail(cfg, true)") >= 3
                        && hud.contains("computeAnalogBounds")
                        && hud.contains("sampleDigitalContent(cfg)"),
                "analog and digital preview/bounds consume the combined sample unit");
        assertTrue(
                hud.contains("scaledTextWidth(client, content.direction(), cfg.scale)")
                        && hud.contains("scaledTextWidth(client, content.latitudeSegment(), cfg.locationTextScale)")
                        && hud.contains("scaledTextWidth(client, content.detailSegment(), cfg.locationTextScale)")
                        && hud.contains("content.latitudeSegment() != null || content.detailSegment() != null")
                        && hud.contains("HudTextLayoutPolicy.combinedTextHeight(")
                        && hud.contains("drawScaledText(ctx, client, cfg, content.direction()")
                        && occurrences(hud, "cfg.locationTextScale") >= 20,
                "digital direction retains compass scale while latitude/detail share an independent scale");
        assertTrue(
                occurrences(hud, "scaledTextWidth(client, latText, cfg.locationTextScale)") >= 3
                        && occurrences(hud, "scaledTextWidth(client, locationDetailText, cfg.locationTextScale)") >= 3
                        && occurrences(hud, "drawScaledText(") >= 7
                        && hud.contains("detailBounds.x,")
                        && hud.contains("detailBounds.y,"),
                "analog FOLLOW and detached detail render/bounds use the same independent scale");
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
                currentDigitalContentBody.contains("currentDirectionText(client, cfg)")
                        && currentDigitalContentBody.contains("latitudeText(client, cfg)")
                        && currentDigitalContentBody.contains("locationDetailLabel(client, cfg, true)")
                        && !currentDigitalContentBody.contains("sampleLocationDetail")
                        && !currentDigitalContentBody.contains("sampleDigitalContent"),
                "runtime digital content is locally wired to live provider-aware content with no sample substitution");
        assertTrue(
                computeBoundsBody.contains(
                        "studioPreview ? sampleDigitalContent(cfg) : currentDigitalContent(client, cfg)")
                        && computeBoundsBody.indexOf("sampleDigitalContent(cfg)")
                        < computeBoundsBody.indexOf("currentDigitalContent(client, cfg)")
                        && hud.contains("HudBounds compassBounds = computeBounds(client, cfg);"),
                "computeBounds permits samples only on the Studio branch and live content on runtime");
        assertTrue(
                hud.contains("HudTextLayoutPolicy.digitalBoxWidth(")
                        && hud.contains("HudTextLayoutPolicy.movePristineDetachedY("),
                "rendered width and pristine overlap use the production numeric policy");
        assertTrue(
                hud.contains("DEFAULT_DETACHED_DETAIL_GAP = 4")
                        && hud.contains("isPristineDefaultDetachedPlacement(cfg)")
                        && hud.contains("moveDefaultDetachedDetailOutsideCompass(")
                        && hud.contains("HudBounds compassBounds = computeBounds(client, cfg);"),
                "only pristine default detached placement resolves an actual compass intersection");
        assertTrue(
                hud.contains("cfg.zoneHAnchor == CompassHudConfig.HAnchor.CENTER")
                        && hud.contains("cfg.zoneVAnchor == CompassHudConfig.VAnchor.TOP")
                        && hud.contains("cfg.zoneOffsetX == 0")
                        && hud.contains("cfg.zoneOffsetY == 0")
                        && hud.contains("return !cfg.zoneFollowsCompass"),
                "Follow and custom detached anchors or offsets bypass the default-only correction");
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
                studio.contains("Component.literal(\"Show Biome Source\")")
                        && studio.contains("cfg.showCustomBiomeSource = value")
                        && studio.contains("locationDetailMode().includesBiome()"),
                "HUD Studio exposes the optional custom-biome provider label only for biome modes");
        assertTrue(
                studio.contains("Component.literal(\"HUD Placement\")")
                        && studio.contains("LatitudeConfig.hudSnapEnabled = value")
                        && studio.contains("v ? \"SNAP\" : \"FREE\""),
                "HUD Studio exposes the existing grid/free placement policy");
        assertTrue(
                studio.contains("case \"EQUATOR\", \"TROPICAL\" -> \"Tropical\";")
                        && studio.contains("case \"SUBTROPICAL\" -> \"Subtropical\";")
                        && !studio.contains("\"Tropics\"")
                        && !studio.contains("\"Subtropics\""),
                "HUD Studio uses the canonical Tropical and Subtropical labels");
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
        assertTrue(
                studio.contains("Component.literal(\"Location Text Size\")")
                        && studio.contains("CompassHudConfig.LOCATION_TEXT_SCALE_MIN * 100.0f")
                        && studio.contains("CompassHudConfig.LOCATION_TEXT_SCALE_MAX * 100.0f")
                        && studio.contains("Math.round(cfg.locationTextScale * 100.0f)")
                        && studio.contains("\"%\", 5, v -> cfg.locationTextScale = v / 100.0f"),
                "Compass tab exposes one 50%-125% location-text slider in five-percent steps");
        assertTrue(
                occurrences(studio, "HudTextLayoutPolicy.titleDragCoordinate(") == 2
                        && studio.contains("LatitudeConfig.hudSnapEnabled")
                        && studio.contains("LatitudeConfig.hudSnapPixels"),
                "title drag preview quantizes in SNAP while the unguarded assignments preserve FREE movement");

        assertTrue(
                studio.contains("TAB_NAMES = {\"Compass\", \"Title\", \"Settings\"}")
                        && occurrences(studio, "CycleButton.<LocationDetailPolicy.Mode>builder") == 1,
                "the consolidated Studio keeps one authoritative four-state location-detail control");
        assertTrue(
                !Files.exists(Path.of("src/main/java/com/example/globe/client/LatitudeSettingsScreen.java")),
                "the retired standalone settings screen cannot retain a parallel Boolean or reset path");

        String build = normalize(read("build.gradle"));
        assertTrue(
                build.contains("tasks.register('latitudeLocationDetailPolicyTest', JavaExec)")
                        && build.contains("dependsOn tasks.named('latitudeLocationDetailPolicyTest')"),
                "location-detail policy proof is automatically wired into Gradle check/build");
    }

    private static void locationTextScalePolicyIsBoundedAndIndependent() {
        assertEquals(1.0f, HudTextLayoutPolicy.sanitizeLocationTextScale(Float.NaN), "NaN restores default");
        assertEquals(1.0f, HudTextLayoutPolicy.sanitizeLocationTextScale(Float.POSITIVE_INFINITY), "infinity restores default");
        assertEquals(1.0f, HudTextLayoutPolicy.sanitizeLocationTextScale(0.0f), "missing primitive JSON value restores default");
        assertEquals(0.50f, HudTextLayoutPolicy.sanitizeLocationTextScale(0.49f), "low values clamp to 50%");
        assertEquals(1.25f, HudTextLayoutPolicy.sanitizeLocationTextScale(1.26f), "high values clamp to 125%");
        assertEquals(1.05f, HudTextLayoutPolicy.sanitizeLocationTextScale(1.03f), "saved values quantize to five-percent steps");

        int directionWidth = HudTextLayoutPolicy.scaledPixels(20, 1.25f);
        int smallLocationWidth = HudTextLayoutPolicy.scaledPixels(30, 0.50f);
        int largeLocationWidth = HudTextLayoutPolicy.scaledPixels(30, 1.25f);
        assertEquals(25, directionWidth, "digital direction uses compass scale");
        assertEquals(15, smallLocationWidth, "small location text uses its own scale");
        assertEquals(38, largeLocationWidth, "large location text uses its own scale");
        assertEquals(directionWidth, HudTextLayoutPolicy.scaledPixels(20, 1.25f),
                "changing location text size cannot change direction or compass size");

        int directionOnlySmall = HudTextLayoutPolicy.combinedTextHeight(9, 1.0f, 0.50f, false);
        int directionOnlyLarge = HudTextLayoutPolicy.combinedTextHeight(9, 1.0f, 1.25f, false);
        assertEquals(directionOnlySmall, directionOnlyLarge,
                "direction-only digital bounds ignore location text size");
        assertEquals(12, HudTextLayoutPolicy.combinedTextHeight(9, 1.0f, 1.25f, true),
                "location text contributes height only when it is present");

        int longProviderWidth = HudTextLayoutPolicy.digitalBoxWidth(
                3, 1.0f, 8, 20, 150, 1.25f);
        int sampleSubstitutionWidth = HudTextLayoutPolicy.digitalBoxWidth(
                3, 1.0f, 8, 20, 20, 1.25f);
        assertEquals(227, longProviderWidth,
                "125% custom-biome/provider content produces exact rendered compass width");
        assertTrue(longProviderWidth > sampleSubstitutionWidth,
                "live long provider content cannot be substituted by the short Studio sample");

        var longCompass = centeredTop(427, longProviderWidth, 24);
        var longDetail = centeredTop(427, 300, 18);
        var movedLongDetail = moveDetachedDetail(longDetail, longCompass, 240, true);
        assertEquals(32, movedLongDetail.y,
                "pristine detached detail moves below the exact long rendered compass");
        assertTrue(!intersects(longCompass, movedLongDetail),
                "long provider detail cannot overlap its exact runtime compass bounds");

        assertEquals(16.0, HudTextLayoutPolicy.titleDragCoordinate(13.0, true, 8),
                "SNAP title coordinate rounds to the grid");
        assertEquals(13.25, HudTextLayoutPolicy.titleDragCoordinate(13.25, false, 8),
                "FREE title coordinate remains unquantized");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static String slice(String value, String start, String end) {
        int from = value.indexOf(start);
        int to = from < 0 ? -1 : value.indexOf(end, from + start.length());
        if (from < 0 || to < 0) {
            throw new AssertionError("missing source slice: " + start + " ... " + end);
        }
        return value.substring(from, to);
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

    private static Rect centeredTop(int screenW, int w, int h) {
        return new Rect((screenW - w) / 2, 4, w, h);
    }

    private static boolean intersects(Rect a, Rect b) {
        return a.x < b.x + b.w
                && a.x + a.w > b.x
                && a.y < b.y + b.h
                && a.y + a.h > b.y;
    }

    private static Rect moveDetachedDetail(
            Rect detail,
            Rect compass,
            int screenH,
            boolean pristineDefaultPlacement) {
        if (!pristineDefaultPlacement || !intersects(detail, compass)) {
            return detail;
        }
        int movedY = HudTextLayoutPolicy.movePristineDetachedY(
                detail.x,
                detail.y,
                detail.w,
                detail.h,
                compass.x,
                compass.y,
                compass.w,
                compass.h,
                screenH,
                4);
        return movedY == detail.y
                ? detail
                : new Rect(detail.x, movedY, detail.w, detail.h);
    }

    private record Rect(int x, int y, int w, int h) {
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
