package com.example.globe.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class InvariantScan {
    private InvariantScan() {}

    public static void main(String[] args) throws Exception {
        List<String> errors = new ArrayList<>();

        String mixins = readFile("src/main/resources/globe.mixins.json");
        requireContains(mixins, "client.CreateWorldScreenMixin", errors, "globe.mixins.json missing client.CreateWorldScreenMixin");
        requireContains(mixins, "client.LevelLoadingScreenLatitudeOverlayMixin", errors, "globe.mixins.json missing client.LevelLoadingScreenLatitudeOverlayMixin");
        requireContains(mixins, "client.DownloadingTerrainScreenFirstLoadMessageMixin", errors, "globe.mixins.json missing client.DownloadingTerrainScreenFirstLoadMessageMixin");
        requireContains(mixins, "client.LatitudeLoadingClientTickMixin", errors, "globe.mixins.json missing client.LatitudeLoadingClientTickMixin");

        byte[] overlayClass = readClassBytes("/com/example/globe/mixin/client/LevelLoadingScreenLatitudeOverlayMixin.class");
        String overlayText = new String(overlayClass, StandardCharsets.ISO_8859_1);
        requireContains(overlayText, "LATITUDE", errors, "loading overlay title missing in mixin class");
        requireContains(overlayText, "Press F9 in-game for HUD options", errors, "loading overlay HUD hint missing in mixin class");
        requireContains(overlayText, "bespoke overlay cleared as level loading screen closed", errors, "loading overlay clear log missing in mixin class");

        byte[] downloadingClass = readClassBytes("/com/example/globe/mixin/client/DownloadingTerrainScreenFirstLoadMessageMixin.class");
        String downloadingText = new String(downloadingClass, StandardCharsets.ISO_8859_1);
        requireContains(downloadingText, "firstWorldLoad", errors, "downloading-terrain first-load flag clear missing in mixin class");

        byte[] tickClass = readClassBytes("/com/example/globe/mixin/client/LatitudeLoadingClientTickMixin.class");
        String tickText = new String(tickClass, StandardCharsets.ISO_8859_1);
        requireContains(tickText, "bespoke overlay state cleared at terrain handoff", errors, "loading tick handoff clear log missing in mixin class");

        if (!errors.isEmpty()) {
            System.err.println("[Latitude invariant scan] FAIL");
            for (String e : errors) {
                System.err.println(" - " + e);
            }
            System.exit(1);
        }

        System.out.println("[Latitude invariant scan] PASS");
    }

    private static String readFile(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    private static byte[] readClassBytes(String resourcePath) throws IOException {
        try (InputStream in = InvariantScan.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }

    private static void requireContains(String haystack, String needle, List<String> errors, String message) {
        if (haystack == null || !haystack.contains(needle)) {
            errors.add(message);
        }
    }
}
