package com.example.globe.dev;

import java.io.IOException;
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

        if (!errors.isEmpty()) {
            System.err.println("[Latitude invariant scan] FAIL");
            for (String e : errors) {
                System.err.println(" - " + e);
            }
            System.exit(1);
        }
    }

    private static String readFile(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    private static void requireContains(String haystack, String needle, List<String> errors, String message) {
        if (haystack == null || !haystack.contains(needle)) {
            errors.add(message);
        }
    }
}
