package com.example.globe.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Small append-only evidence session for development-only human tests.
 */
public final class DevTestSession {
    public static final String SCHEMA = "latitude-dev-case-v1";
    public static final String PRIVATE_MANIFEST_SCHEMA = "latitude-recorder-private-v1";
    public static final String SUMMARY_SCHEMA = "latitude-recorder-summary-v1";
    private static final String EVENTS_FILE = "events.jsonl";
    private static final String PRIVATE_MANIFEST_FILE = "manifest.private.json";
    private static final String SUMMARY_FILE = "summary.json";

    private static DevTestSession active;

    private final String caseId;
    private final String sessionId;
    private final Path directory;
    private final Path eventsPath;
    private final Clock clock;
    private final RecorderLitePlan recorderPlan;
    private final Map<String, String> checkpointObservations = new TreeMap<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private long sequence;
    private boolean closed;
    private String pendingCaptureLabel;
    private DevToolPolicy.CloseState pendingFinishState;
    private long pendingFinishSequence;

    private DevTestSession(
            String caseId,
            String sessionId,
            Path directory,
            Clock clock,
            RecorderLitePlan recorderPlan
    ) {
        this.caseId = caseId;
        this.sessionId = sessionId;
        this.directory = directory;
        this.eventsPath = directory.resolve(EVENTS_FILE);
        this.clock = clock;
        this.recorderPlan = recorderPlan;
    }

    public static synchronized DevTestSession startActive(
            Path casesRoot,
            String rawCaseId,
            long worldTick,
            Map<String, String> context
    ) throws IOException {
        return startActive(casesRoot, rawCaseId, worldTick, context, Clock.systemUTC());
    }

    static synchronized DevTestSession startActive(
            Path casesRoot,
            String rawCaseId,
            long worldTick,
            Map<String, String> context,
            Clock clock
    ) throws IOException {
        return startActive(
                casesRoot,
                rawCaseId,
                worldTick,
                context,
                RecorderLitePlan.empty(rawCaseId),
                context,
                clock);
    }

    public static synchronized DevTestSession startRecorderActive(
            Path casesRoot,
            String rawCaseId,
            long worldTick,
            Map<String, String> eventContext,
            RecorderLitePlan recorderPlan,
            Map<String, String> privateIdentity
    ) throws IOException {
        return startActive(
                casesRoot,
                rawCaseId,
                worldTick,
                eventContext,
                recorderPlan,
                privateIdentity,
                Clock.systemUTC());
    }

    static synchronized DevTestSession startActive(
            Path casesRoot,
            String rawCaseId,
            long worldTick,
            Map<String, String> eventContext,
            RecorderLitePlan recorderPlan,
            Map<String, String> privateIdentity,
            Clock clock
    ) throws IOException {
        if (active != null && !active.closed) {
            throw new IllegalStateException("case session already active: " + active.sessionId);
        }
        String caseId = DevToolPolicy.sanitizeToken(rawCaseId, "case");
        Path normalizedRoot = casesRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        String sessionId = nextSessionId(normalizedRoot, caseId);
        Path directory = DevToolPolicy.resolveContained(normalizedRoot, sessionId);
        Files.createDirectory(directory);

        RecorderLitePlan plan = recorderPlan == null
                ? RecorderLitePlan.empty(rawCaseId)
                : recorderPlan;
        if (!caseId.equals(plan.caseId())) {
            throw new IllegalArgumentException("Recorder Lite route does not match case id");
        }
        DevTestSession session = new DevTestSession(caseId, sessionId, directory, clock, plan);
        session.writePrivateManifest(eventContext, privateIdentity);
        Map<String, String> startContext = new LinkedHashMap<>();
        if (eventContext != null) {
            startContext.putAll(eventContext);
        }
        startContext.put("recorder_plan", plan.configured() ? "configured" : "unplanned");
        startContext.put("world_class", plan.worldClass());
        startContext.put("owed_checkpoint_count", Integer.toString(plan.checkpoints().size()));
        session.append("start", worldTick, startContext);
        active = session;
        return session;
    }

    public static synchronized Optional<DevTestSession> active() {
        if (active == null || active.closed) {
            return Optional.empty();
        }
        return Optional.of(active);
    }

    public static synchronized long markActive(
            String rawLabel,
            long worldTick,
            Map<String, String> fields
    ) throws IOException {
        DevTestSession session = requireActive();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("label", DevToolPolicy.sanitizeToken(rawLabel, "mark"));
        if (fields != null) {
            values.putAll(fields);
        }
        CheckpointObservation observation = session.parseObservation(rawLabel);
        if (observation != null) {
            if (session.checkpointObservations.containsKey(observation.checkpoint())) {
                throw new IllegalStateException(
                        "checkpoint already observed: " + observation.checkpoint());
            }
            values.put("checkpoint", observation.checkpoint());
            values.put("expected", observation.expected());
            values.put("observed", observation.observed());
            values.put("checkpoint_result", observation.matches() ? "pass" : "fail");
        }
        long eventSequence = session.append("mark", worldTick, values);
        if (observation != null) {
            session.checkpointObservations.put(observation.checkpoint(), observation.observed());
        }
        return eventSequence;
    }

    public static synchronized long requestCaptureActive(
            String rawLabel,
            boolean integratedClient,
            long worldTick,
            Map<String, String> fields
    ) throws IOException {
        DevTestSession session = requireActive();
        if (session.pendingCaptureLabel != null) {
            throw new IllegalStateException(
                    "capture '" + session.pendingCaptureLabel
                            + "' is still pending; wait for completion or failure before requesting another");
        }
        String captureLabel = DevToolPolicy.sanitizeToken(rawLabel, "capture");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("label", captureLabel);
        values.put("capture_mode", integratedClient ? "integrated_client_auto" : "marker_only");
        if (fields != null) {
            values.putAll(fields);
        }
        long requestSequence = session.append("capture_requested", worldTick, values);
        session.pendingCaptureLabel = captureLabel;
        return requestSequence;
    }

    public static synchronized long recordScreenshotActive(
            String relativePath,
            String sha256,
            long requestWorldTick,
            Map<String, String> metadata
    ) throws IOException {
        DevTestSession session = requireActive();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("label", session.pendingCaptureLabel == null ? "keybind" : session.pendingCaptureLabel);
        values.put("screenshot_path", relativePath == null ? "unsaved" : relativePath);
        values.put("screenshot_sha256", sha256 == null ? "unavailable" : sha256);
        if (metadata != null) {
            values.putAll(metadata);
        }
        long completionSequence = session.append("capture_completed", requestWorldTick, values);
        Map<String, Object> screenshot = new LinkedHashMap<>();
        screenshot.put("label", String.format("screenshot-%03d", session.screenshots.size() + 1));
        screenshot.put("sha256", sha256 == null ? "unavailable" : sha256);
        session.screenshots.add(screenshot);
        session.pendingCaptureLabel = null;
        return completionSequence;
    }

    public static synchronized long recordCaptureFailedActive(
            String reason,
            long requestWorldTick,
            Map<String, String> metadata
    ) throws IOException {
        DevTestSession session = requireActive();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("label", session.pendingCaptureLabel == null ? "capture" : session.pendingCaptureLabel);
        values.put("reason", reason == null || reason.isBlank() ? "unknown" : reason);
        if (metadata != null) {
            values.putAll(metadata);
        }
        long failureSequence = session.append("capture_failed", requestWorldTick, values);
        session.pendingCaptureLabel = null;
        return failureSequence;
    }

    public static synchronized long appendActive(
            String event,
            long worldTick,
            Map<String, String> fields
    ) throws IOException {
        return requireActive().append(event, worldTick, fields);
    }

    public static synchronized DevTestSession finishActive(
            DevToolPolicy.CloseState state,
            long worldTick,
            Map<String, String> fields
    ) throws IOException {
        DevTestSession session = requireActive();
        if (session.pendingCaptureLabel != null) {
            throw new IllegalStateException(
                    "capture '" + session.pendingCaptureLabel
                            + "' is still pending; wait for completion or failure before finishing");
        }
        if (session.pendingFinishState != null && session.pendingFinishState != state) {
            throw new IllegalStateException(
                    "finish result was already recorded as " + session.pendingFinishState.id());
        }
        if (state == DevToolPolicy.CloseState.PASS && session.recorderPlan.configured()) {
            List<String> missing = session.missingCheckpoints();
            List<String> mismatched = session.mismatchedCheckpoints();
            if (!missing.isEmpty() || !mismatched.isEmpty()) {
                throw new IllegalStateException(
                        "Recorder Lite pass requires every checkpoint to match; missing="
                                + missing + " mismatched=" + mismatched);
            }
        }
        if (session.pendingFinishState == null) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("result", state.id());
            if (fields != null) {
                values.putAll(fields);
            }
            session.pendingFinishSequence = session.append("finish", worldTick, values);
            session.pendingFinishState = state;
        }
        session.writeSummary(state, session.pendingFinishSequence);
        session.closed = true;
        active = null;
        return session;
    }

    public String caseId() {
        return caseId;
    }

    public String sessionId() {
        return sessionId;
    }

    public Path directory() {
        return directory;
    }

    public Path eventsPath() {
        return eventsPath;
    }

    public synchronized long sequence() {
        return sequence;
    }

    private synchronized long append(String event, long worldTick, Map<String, String> fields) throws IOException {
        if (closed) {
            throw new IllegalStateException("case session is closed");
        }
        if (pendingFinishState != null) {
            throw new IllegalStateException(
                    "case finish is already recorded; only summary recovery may continue");
        }
        long nextSequence = sequence + 1L;
        LinkedHashMap<String, Object> row = baseRow(nextSequence, event, worldTick);
        if (fields != null) {
            for (Map.Entry<String, String> entry : new TreeMap<>(fields).entrySet()) {
                if (!row.containsKey(entry.getKey())) {
                    row.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Files.writeString(
                eventsPath,
                json(row) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        sequence = nextSequence;
        return nextSequence;
    }

    private void writeSummary(DevToolPolicy.CloseState state, long finishSequence) throws IOException {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema", SUMMARY_SCHEMA);
        summary.put("case_label", opaqueLabel("case", caseId));
        summary.put("session_label", opaqueLabel("session", sessionId));
        summary.put("result", state.id());
        summary.put("event_count", finishSequence);
        summary.put("recorder_plan", recorderPlan.configured() ? "configured" : "unplanned");
        summary.put("owed_checkpoint_count", recorderPlan.checkpoints().size());
        summary.put("observed_checkpoint_count", checkpointObservations.size());
        summary.put("missing_checkpoint_count", missingCheckpoints().size());
        summary.put("mismatch_count", mismatchedCheckpoints().size());
        List<Map<String, Object>> checkpoints = new ArrayList<>();
        for (Map.Entry<String, String> checkpoint : recorderPlan.checkpoints().entrySet()) {
            String observed = checkpointObservations.get(checkpoint.getKey());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("checkpoint", checkpoint.getKey());
            row.put("expected", checkpoint.getValue());
            row.put("observed", observed == null ? "missing" : observed);
            row.put("result", observed == null
                    ? "missing"
                    : checkpoint.getValue().equals(observed) ? "pass" : "fail");
            checkpoints.add(row);
        }
        summary.put("checkpoints", checkpoints);
        summary.put("screenshot_count", screenshots.size());
        summary.put("screenshots", List.copyOf(screenshots));
        Path summaryPath = directory.resolve(SUMMARY_FILE);
        String expected = json(summary) + "\n";
        try {
            Files.writeString(
                    summaryPath,
                    expected,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isRegularFile(summaryPath)
                    || !Files.readString(summaryPath, StandardCharsets.UTF_8).equals(expected)) {
                throw exists;
            }
        }
    }

    private void writePrivateManifest(
            Map<String, String> eventContext,
            Map<String, String> privateIdentity
    ) throws IOException {
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema", PRIVATE_MANIFEST_SCHEMA);
        manifest.put("case_id", caseId);
        manifest.put("session_id", sessionId);
        manifest.put("world_class", recorderPlan.worldClass());
        manifest.put("identity", sortedCopy(privateIdentity));
        manifest.put("initial_context", sortedCopy(eventContext));
        manifest.put("atlas_settings", recorderPlan.atlasSettings());
        manifest.put("owed_checkpoints", recorderPlan.checkpoints());
        manifest.put("events_file", EVENTS_FILE);
        Files.writeString(
                directory.resolve(PRIVATE_MANIFEST_FILE),
                json(manifest) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    private CheckpointObservation parseObservation(String rawLabel) {
        if (!recorderPlan.configured() || rawLabel == null || !rawLabel.contains("=")) {
            return null;
        }
        String[] parts = rawLabel.split("=", 2);
        String checkpoint = strictObservationToken(parts[0], "checkpoint");
        String observed = strictObservationToken(parts[1], "observed");
        String expected = recorderPlan.checkpoints().get(checkpoint);
        if (expected == null) {
            throw new IllegalArgumentException("checkpoint is not owed by this route: " + checkpoint);
        }
        return new CheckpointObservation(
                checkpoint,
                expected,
                observed,
                expected.equals(observed));
    }

    private static String strictObservationToken(String raw, String label) {
        String sanitized = DevToolPolicy.sanitizeToken(raw, label);
        if (raw == null || !sanitized.equals(raw.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(label + " must be a lowercase safe token");
        }
        return sanitized;
    }

    private List<String> missingCheckpoints() {
        return recorderPlan.checkpoints().keySet().stream()
                .filter(checkpoint -> !checkpointObservations.containsKey(checkpoint))
                .toList();
    }

    private List<String> mismatchedCheckpoints() {
        return recorderPlan.checkpoints().entrySet().stream()
                .filter(checkpoint -> checkpointObservations.containsKey(checkpoint.getKey()))
                .filter(checkpoint -> !checkpoint.getValue().equals(
                        checkpointObservations.get(checkpoint.getKey())))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, String> sortedCopy(Map<String, String> values) {
        return values == null ? Map.of() : new TreeMap<>(values);
    }

    private static String opaqueLabel(String kind, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return kind + "-" + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record CheckpointObservation(
            String checkpoint,
            String expected,
            String observed,
            boolean matches
    ) {
    }

    private LinkedHashMap<String, Object> baseRow(long eventSequence, String event, long worldTick) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("schema", SCHEMA);
        row.put("sequence", eventSequence);
        row.put("world_tick", worldTick);
        row.put("event", event != null && event.matches("[a-z0-9_]+")
                ? event
                : DevToolPolicy.sanitizeToken(event, "event"));
        row.put("case_id", caseId);
        row.put("session_id", sessionId);
        row.put("timestamp_utc", Instant.now(clock).toString());
        return row;
    }

    private static DevTestSession requireActive() {
        if (active == null || active.closed) {
            throw new IllegalStateException("no active case session; use /latdev case start <name>");
        }
        return active;
    }

    private static String nextSessionId(Path root, String caseId) {
        if (Files.notExists(root.resolve(caseId))) {
            return caseId;
        }
        for (int index = 2; index <= 9999; index++) {
            String candidate = caseId + "-" + String.format("%03d", index);
            if (Files.notExists(root.resolve(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("too many sessions for case " + caseId);
    }

    static String json(Map<String, ?> values) {
        StringBuilder out = new StringBuilder();
        appendJsonValue(out, values);
        return out.toString();
    }

    private static void appendJsonValue(StringBuilder out, Object value) {
        if (value instanceof Map<?, ?> map) {
            appendJsonMap(out, map);
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object entry : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendJsonValue(out, entry);
            }
            out.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value == null) {
            out.append("null");
        } else {
            out.append('"').append(jsonEscape(value.toString())).append('"');
        }
    }

    private static void appendJsonMap(StringBuilder out, Map<?, ?> values) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(jsonEscape(entry.getKey().toString())).append('"').append(':');
            appendJsonValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
