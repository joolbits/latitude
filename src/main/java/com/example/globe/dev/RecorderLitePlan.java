package com.example.globe.dev;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Private, local route prepared for one Recorder Lite case session. */
public final class RecorderLitePlan {
    public static final String SCHEMA = "latitude-recorder-plan-v1";
    public static final String PLAN_PROPERTY = "latitude.recorder.plan";
    private static final String SCHEMA_KEY = "schema";
    private static final String CASE_KEY = "case_id";
    private static final String WORLD_CLASS_KEY = "world_class";
    private static final String CHECKPOINT_PREFIX = "checkpoint.";
    private static final String ATLAS_PREFIX = "atlas.";

    private static final java.util.Set<String> WORLD_CLASSES = java.util.Set.of(
            "fresh-control",
            "legacy-existing-chunks",
            "legacy-new-chunks",
            "ordinary-control",
            "unknown");

    private final String caseId;
    private final String worldClass;
    private final Map<String, String> checkpoints;
    private final Map<String, String> atlasSettings;
    private final boolean configured;

    private RecorderLitePlan(
            String caseId,
            String worldClass,
            Map<String, String> checkpoints,
            Map<String, String> atlasSettings,
            boolean configured
    ) {
        this.caseId = caseId;
        this.worldClass = worldClass;
        this.checkpoints = immutableSorted(checkpoints);
        this.atlasSettings = immutableSorted(atlasSettings);
        this.configured = configured;
    }

    public static RecorderLitePlan configuredOrEmpty(String rawCaseId) throws IOException {
        String configuredPath = System.getProperty(PLAN_PROPERTY, "").trim();
        if (configuredPath.isEmpty()) {
            return empty(rawCaseId);
        }
        return load(Path.of(configuredPath), rawCaseId);
    }

    static RecorderLitePlan load(Path path, String rawCaseId) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Recorder Lite plan is not a readable file");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!SCHEMA.equals(properties.getProperty(SCHEMA_KEY))) {
            throw new IllegalArgumentException("Recorder Lite plan schema must be " + SCHEMA);
        }

        String caseId = DevToolPolicy.sanitizeToken(rawCaseId, "case");
        String plannedCase = DevToolPolicy.sanitizeToken(properties.getProperty(CASE_KEY), "case");
        if (!caseId.equals(plannedCase)) {
            throw new IllegalArgumentException(
                    "Recorder Lite plan case does not match /latdev case start");
        }
        String worldClass = DevToolPolicy.sanitizeToken(
                properties.getProperty(WORLD_CLASS_KEY, "unknown"), "unknown");
        if (!WORLD_CLASSES.contains(worldClass)) {
            throw new IllegalArgumentException("unsupported Recorder Lite world class: " + worldClass);
        }

        Map<String, String> checkpoints = new TreeMap<>();
        Map<String, String> atlasSettings = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (SCHEMA_KEY.equals(key) || CASE_KEY.equals(key) || WORLD_CLASS_KEY.equals(key)) {
                continue;
            }
            if (key.startsWith(CHECKPOINT_PREFIX)) {
                String id = strictToken(key.substring(CHECKPOINT_PREFIX.length()), "checkpoint");
                String expected = strictToken(properties.getProperty(key), "expected");
                if (checkpoints.put(id, expected) != null) {
                    throw new IllegalArgumentException("duplicate Recorder Lite checkpoint: " + id);
                }
                continue;
            }
            if (key.startsWith(ATLAS_PREFIX)) {
                String id = strictToken(key.substring(ATLAS_PREFIX.length()), "atlas");
                atlasSettings.put(id, boundedValue(properties.getProperty(key), "atlas setting"));
                continue;
            }
            throw new IllegalArgumentException("unknown Recorder Lite plan field: " + key);
        }
        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("Recorder Lite plan needs at least one checkpoint");
        }
        return new RecorderLitePlan(caseId, worldClass, checkpoints, atlasSettings, true);
    }

    static RecorderLitePlan empty(String rawCaseId) {
        return new RecorderLitePlan(
                DevToolPolicy.sanitizeToken(rawCaseId, "case"),
                "unknown",
                Map.of(),
                Map.of(),
                false);
    }

    public String caseId() {
        return caseId;
    }

    public String worldClass() {
        return worldClass;
    }

    public Map<String, String> checkpoints() {
        return checkpoints;
    }

    public Map<String, String> atlasSettings() {
        return atlasSettings;
    }

    public boolean configured() {
        return configured;
    }

    private static String strictToken(String value, String label) {
        String sanitized = DevToolPolicy.sanitizeToken(value, label);
        if (value == null || !sanitized.equals(value.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(label + " must be a lowercase safe token");
        }
        return sanitized;
    }

    private static String boundedValue(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 256
                || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(label + " must be one non-empty line up to 256 characters");
        }
        return normalized;
    }

    private static Map<String, String> immutableSorted(Map<String, String> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
