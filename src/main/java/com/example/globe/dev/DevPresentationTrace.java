package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeClientState;
import com.example.globe.client.LatitudeConfig;
import com.example.globe.client.PolarPresentationPolicy;
import com.example.globe.util.LatitudeMath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Integrated-client trace of the actual production polar presentation policy.
 */
public final class DevPresentationTrace {
    private static Trace active;

    private DevPresentationTrace() {
    }

    /**
     * Called reflectively by the server-side dev command only after it has proved this is an
     * integrated client. Keeping the call reflective prevents a dedicated server from resolving
     * client-only classes.
     */
    public static synchronized String startFromIntegratedCommand(
            Path runDirectory,
            UUID playerId,
            String playerName,
            long worldTick
    ) throws IOException {
        if (active != null) {
            throw new IllegalStateException("presentation trace already active for " + active.playerName);
        }
        Path traceRoot = runDirectory.resolve("latdev").resolve("presentation-traces")
                .toAbsolutePath().normalize();
        Files.createDirectories(traceRoot);
        String stem = DevToolPolicy.sanitizeToken(playerName, "player") + "-presentation";
        Path path = nextPath(traceRoot, stem);
        Trace candidate = new Trace(
                playerId,
                DevToolPolicy.sanitizeToken(playerName, "player"),
                runDirectory.toAbsolutePath().normalize(),
                path,
                worldTick);
        candidate.append("trace_start", worldTick, Map.of(
                "mode", "integrated_client_computed_presentation_policy",
                "player", candidate.playerName));
        active = candidate;
        appendCaseEvent("presentation_trace_start", worldTick, Map.of(
                "mode", "integrated_client_computed_presentation_policy",
                "trace_file", active.relativePath()));
        return active.relativePath();
    }

    public static synchronized String stopFromIntegratedCommand(UUID playerId) throws IOException {
        if (active == null) {
            throw new IllegalStateException("no active presentation trace");
        }
        if (!active.playerId.equals(playerId)) {
            throw new IllegalStateException("presentation trace belongs to another player");
        }
        long tick = active.lastWorldTick;
        active.append("trace_stop", tick, Map.of(
                "samples", Long.toString(active.sampleCount)));
        appendCaseEvent("presentation_trace_stop", tick, Map.of(
                "samples", Long.toString(active.sampleCount),
                "trace_file", active.relativePath()));
        String path = active.relativePath();
        active = null;
        return path;
    }

    public static synchronized void clientTick(Minecraft client) {
        if (active == null || client == null || client.player == null || client.level == null) {
            return;
        }
        if (!active.playerId.equals(client.player.getUUID())) {
            return;
        }

        try {
            WorldBorder border = client.level.getWorldBorder();
            double signedDegrees = DevToolPolicy.signedLatitudeDegrees(
                    client.player.getZ(),
                    border.getCenterZ(),
                    LatitudeMath.worldRadiusBlocks(border));
            double absoluteDegrees = Math.abs(signedDegrees);
            GlobeClientState.PolarStage polarStage = GlobeClientState.computePolarStage(client.level, client.player);
            GlobeClientState.Eval productionEvaluation = GlobeClientState.evaluate(client);
            int stageRank = stageRank(polarStage);
            float fogIntensity = PolarPresentationPolicy.fogIntensity(absoluteDegrees);
            long worldTick = client.level.getGameTime();
            String dimension = client.level.dimension().location().toString();
            DevToolPolicy.TraceClock.Update clockUpdate =
                    active.traceClock.update(dimension, worldTick);
            long policyTick = clockUpdate.policyTick();

            if (clockUpdate.action() == DevToolPolicy.TraceContextAction.DIMENSION_RESET) {
                active.append("context_reset", worldTick, Map.of(
                        "current_dimension", dimension,
                        "policy_tick", Long.toString(policyTick),
                        "previous_dimension", clockUpdate.previousDimension(),
                        "previous_world_tick", Long.toString(clockUpdate.previousRawTick()),
                        "reason", "dimension_change"));
                active.resetSampleState();
            } else if (clockUpdate.action() == DevToolPolicy.TraceContextAction.CLOCK_RESYNC) {
                active.append("clock_resync", worldTick, Map.of(
                        "dimension", dimension,
                        "policy_tick", Long.toString(policyTick),
                        "previous_world_tick", Long.toString(clockUpdate.previousRawTick()),
                        "reason", "same_dimension_world_tick_rollback"));
            }

            active.warningEpisode.update(stageRank, absoluteDegrees, policyTick);
            int warningActiveRank = active.warningEpisode.activeStageRank(policyTick);
            int warningHighestRank = active.warningEpisode.highestTriggeredStageRank();
            DevToolPolicy.TraceTransition transition = DevToolPolicy.traceTransition(
                    active.previousAbsoluteDegrees,
                    absoluteDegrees,
                    active.previousDirection,
                    active.previousStageRank,
                    stageRank,
                    active.previousFogBucket,
                    fogIntensity);
            boolean warningChanged = warningActiveRank != active.previousWarningActiveRank
                    || warningHighestRank != active.previousWarningHighestRank;

            if (transition.shouldRecord() || warningChanged) {
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                values.put("absolute_latitude_degrees", format(absoluteDegrees));
                values.put("direction", transition.direction().name().toLowerCase(Locale.ROOT));
                values.put("dimension", dimension);
                values.put("fog_bucket", Integer.toString(transition.fogBucket()));
                values.put("fog_intensity", format(fogIntensity));
                values.put("fog_render_applicable", Boolean.toString(
                        productionEvaluation.active() && productionEvaluation.surfaceOk()));
                values.put("latitude_world_active", Boolean.toString(productionEvaluation.active()));
                values.put("policy_tick", Long.toString(policyTick));
                values.put("polar_stage", polarStage.name().toLowerCase(Locale.ROOT));
                values.put("signed_latitude_degrees", format(signedDegrees));
                values.put("surface_ok", Boolean.toString(productionEvaluation.surfaceOk()));
                values.put("warning_active_rank", Integer.toString(warningActiveRank));
                values.put("warning_messages_enabled", Boolean.toString(
                        LatitudeConfig.showWarningMessages));
                values.put("warning_highest_triggered_rank", Integer.toString(warningHighestRank));
                values.put("warning_render_applicable", Boolean.toString(
                        productionEvaluation.active()
                                && productionEvaluation.surfaceOk()
                                && LatitudeConfig.showWarningMessages));
                values.put("x", format(client.player.getX()));
                values.put("z", format(client.player.getZ()));
                active.append("transition", worldTick, values);
                appendCaseEvent("presentation_transition", worldTick, values);
                active.sampleCount++;
            }

            active.previousAbsoluteDegrees = absoluteDegrees;
            active.previousDirection = transition.direction();
            active.previousStageRank = stageRank;
            active.previousFogBucket = transition.fogBucket();
            active.previousWarningActiveRank = warningActiveRank;
            active.previousWarningHighestRank = warningHighestRank;
            active.lastWorldTick = worldTick;
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[latdev] presentation trace sample failed", e);
        }
    }

    private static int stageRank(GlobeClientState.PolarStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case WARN_1 -> 1;
            case WARN_2 -> 2;
            case DANGER -> 3;
            case LETHAL -> 4;
        };
    }

    private static Path nextPath(Path root, String stem) {
        for (int index = 1; index <= 9999; index++) {
            String suffix = index == 1 ? "" : "-" + String.format("%03d", index);
            Path candidate = DevToolPolicy.resolveContained(root, stem + suffix + ".jsonl");
            if (Files.notExists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("too many presentation traces for " + stem);
    }

    private static void appendCaseEvent(String event, long worldTick, Map<String, String> fields) {
        if (DevTestSession.active().isEmpty()) {
            return;
        }
        try {
            DevTestSession.appendActive(event, worldTick, fields);
        } catch (IOException | IllegalStateException e) {
            GlobeMod.LOGGER.warn("[latdev] could not append presentation trace event to active case", e);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static final class Trace {
        private final UUID playerId;
        private final String playerName;
        private final Path runDirectory;
        private final Path path;
        private final DevToolPolicy.TraceClock traceClock = new DevToolPolicy.TraceClock();
        private PolarPresentationPolicy.PolarWarningEpisode warningEpisode =
                new PolarPresentationPolicy.PolarWarningEpisode();
        private long sequence;
        private long sampleCount;
        private long lastWorldTick;
        private double previousAbsoluteDegrees = Double.NaN;
        private DevToolPolicy.MovementDirection previousDirection;
        private int previousStageRank;
        private int previousFogBucket = -1;
        private int previousWarningActiveRank;
        private int previousWarningHighestRank;

        private Trace(
                UUID playerId,
                String playerName,
                Path runDirectory,
                Path path,
                long startWorldTick
        ) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.runDirectory = runDirectory;
            this.path = path;
            this.lastWorldTick = startWorldTick;
        }

        private String relativePath() {
            return runDirectory.relativize(path).toString();
        }

        private void resetSampleState() {
            warningEpisode = new PolarPresentationPolicy.PolarWarningEpisode();
            previousAbsoluteDegrees = Double.NaN;
            previousDirection = null;
            previousStageRank = 0;
            previousFogBucket = -1;
            previousWarningActiveRank = 0;
            previousWarningHighestRank = 0;
        }

        private void append(String event, long worldTick, Map<String, String> fields) throws IOException {
            sequence++;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("schema", "latitude-presentation-trace-v1");
            row.put("sequence", sequence);
            row.put("world_tick", worldTick);
            row.put("event", event);
            row.put("timestamp_utc", Instant.now().toString());
            row.putAll(new java.util.TreeMap<>(fields));
            Files.writeString(
                    path,
                    DevTestSession.json(row) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
    }
}
