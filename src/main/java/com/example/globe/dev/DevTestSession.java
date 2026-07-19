package com.example.globe.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Small append-only evidence session for development-only human tests.
 */
public final class DevTestSession {
    public static final String SCHEMA = "latitude-dev-case-v1";
    private static final String EVENTS_FILE = "events.jsonl";
    private static final String SUMMARY_FILE = "summary.json";

    private static DevTestSession active;

    private final String caseId;
    private final String sessionId;
    private final Path directory;
    private final Path eventsPath;
    private final Clock clock;
    private long sequence;
    private boolean closed;
    private String pendingCaptureLabel;
    private DevToolPolicy.CloseState pendingFinishState;
    private long pendingFinishSequence;

    private DevTestSession(
            String caseId,
            String sessionId,
            Path directory,
            Clock clock
    ) {
        this.caseId = caseId;
        this.sessionId = sessionId;
        this.directory = directory;
        this.eventsPath = directory.resolve(EVENTS_FILE);
        this.clock = clock;
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
        if (active != null && !active.closed) {
            throw new IllegalStateException("case session already active: " + active.sessionId);
        }
        String caseId = DevToolPolicy.sanitizeToken(rawCaseId, "case");
        Path normalizedRoot = casesRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        String sessionId = nextSessionId(normalizedRoot, caseId);
        Path directory = DevToolPolicy.resolveContained(normalizedRoot, sessionId);
        Files.createDirectory(directory);

        DevTestSession session = new DevTestSession(caseId, sessionId, directory, clock);
        session.append("start", worldTick, context);
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
        return session.append("mark", worldTick, values);
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
        summary.put("schema", SCHEMA);
        summary.put("case_id", caseId);
        summary.put("session_id", sessionId);
        summary.put("result", state.id());
        summary.put("event_count", finishSequence);
        summary.put("events_file", EVENTS_FILE);
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
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(jsonEscape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else if (value == null) {
                out.append("null");
            } else {
                out.append('"').append(jsonEscape(value.toString())).append('"');
            }
        }
        return out.append('}').toString();
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
