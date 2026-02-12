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
        requireContains(mixins, "client.DownloadingTerrainScreenFirstLoadMessageMixin", errors, "globe.mixins.json missing client.DownloadingTerrainScreenFirstLoadMessageMixin");

        byte[] clazz = readClassBytes("/com/example/globe/mixin/client/DownloadingTerrainScreenFirstLoadMessageMixin.class");
        String clazzText = new String(clazz, StandardCharsets.ISO_8859_1);
        requireContains(clazzText, "Latitude is preparing your world for the first time.", errors, "first-load line 1 missing in mixin class");
        requireContains(clazzText, "Subsequent loads will be much faster.", errors, "first-load line 2 missing in mixin class");

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
