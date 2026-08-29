package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeStructureLocateService;
import com.example.globe.world.LatitudeStructureLocateService.AtlasCandidate;
import com.example.globe.world.LatitudeStructureLocateService.AtlasSweep;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only structure atlas exporter. When {@code -Dlatdev.structureAtlas} is set, sweeps every
 * random-spread structure's placement grid through the exact locate admission evaluators and writes
 * {@code structures.json} for the atlas viewer's overlay, plus a per-structure verdict summary.
 *
 * <p>Launches through {@code runBiomePreview}, but must run by itself. The biome preview/search
 * exporters also own server shutdown and may override the active sampling radius, so combining
 * them with this sweep produces a race or a radius-mismatched artifact. The property may be
 * {@code true} (default output directory under the run dir) or a directory path. Registered only
 * in a development environment through the same reflective dev-register path as the biome runner,
 * and the whole {@code dev} package is excluded from release artifacts, so none of this can ship.
 *
 * <p>Verdicts are candidate-level (no {@code Structure.generate} preview), so an ACCEPTED dot is
 * a site the guards would admit, not a promise the jigsaw succeeded. That is the useful fidelity
 * for worldgen diagnosis — it answers "where MAY this structure live" border-wide. A regular
 * radius-10,000 sweep is intentionally exhaustive and can take tens of minutes; it is not a quick
 * structure-incidence counter.
 */
public final class StructureAtlasExporter {

    private static final String PROP_KEY = "latdev.structureAtlas";

    /**
     * Per-candidate rows written at most. A full sweep of a regular world is millions of rows —
     * dense placements like mineshafts dominate — and writing all of them produced a 531 MB
     * artifact no viewer will open. Accepted rows are always kept (they are the dots worth
     * looking at); rejected rows are evenly subsampled to fit, and the sampling stride is recorded
     * so a thinned layer can never be mistaken for a complete one.
     */
    private static final int MAX_CANDIDATE_ROWS = 150_000;

    private StructureAtlasExporter() {
    }

    public static void register() {
        if (System.getProperty(PROP_KEY) == null) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(StructureAtlasExporter::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        String property = System.getProperty(PROP_KEY);
        if (property == null) {
            return;
        }
        if (System.getProperty("latdev.biomePng") != null
                || System.getProperty("latdev.biomeSearch") != null) {
            GlobeMod.LOGGER.error(
                    "[Latitude] structure atlas REFUSED: run it without biomePng/biomeSearch; "
                            + "those exporters own server shutdown and sampling radius");
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            GlobeMod.LOGGER.error(
                    "[Latitude] structure atlas REFUSED: no overworld; nothing exported");
            server.halt(false);
            return;
        }
        // The sweep can take tens of minutes; on the server thread the watchdog counts that as
        // one tick and forcibly crashes the server at 60 s (observed live). The locate service
        // already established that these candidate evaluators are pure functions of per-world
        // configuration, safe off-thread — run the sweep on the shared background executor,
        // exactly like the biome runner's own export job, and halt when it finishes.
        net.minecraft.util.Util.backgroundExecutor().execute(() -> runSweep(server, overworld, property));
    }

    private static void runSweep(MinecraftServer server, ServerLevel overworld, String property) {
        try {
            // Every invocation gets its own directory. World name + radius alone is not unique:
            // separate seeds can reuse both, and two concurrent reruns of the same world can too.
            int radius = (int) Math.round(overworld.getWorldBorder().getSize() / 2.0);
            String runScope = sanitize(overworld.getServer().getWorldData().getLevelName())
                    + "_R" + radius;
            Path outputRoot = "true".equalsIgnoreCase(property) || property.isBlank()
                    ? server.getServerDirectory().resolve("latdev-structures")
                    : Path.of(property);
            Files.createDirectories(outputRoot);
            Path outputDir = Files.createTempDirectory(outputRoot, runScope + "_");
            long startNanos = System.nanoTime();
            AtlasSweep sweep =
                    LatitudeStructureLocateService.sweepStructureCandidatesForAtlas(overworld);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            Path json = outputDir.resolve("structures.json");
            Files.writeString(json, toJson(overworld, sweep, elapsedMs), StandardCharsets.UTF_8);
            if (sweep.refusalReason() != null) {
                // Never let an empty artifact read as "this world has no structures".
                GlobeMod.LOGGER.error("[Latitude] structure atlas REFUSED: {} -> {}",
                        sweep.refusalReason(), json.toAbsolutePath());
            } else {
                GlobeMod.LOGGER.info(
                        "[Latitude] structure atlas: {} candidates across {} structures in {} ms -> {}",
                        sweep.candidates().size(), sweep.structuresSwept(), elapsedMs,
                        json.toAbsolutePath());
            }
        } catch (IOException | RuntimeException failure) {
            GlobeMod.LOGGER.error("[Latitude] structure atlas export failed", failure);
        } finally {
            // Standalone operation was established above, so this exporter owns shutdown even
            // when the overworld is unavailable or the sweep fails.
            server.halt(false);
        }
    }

    /** Keeps a world name safe for a directory segment. */
    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "world";
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private static String toJson(ServerLevel level, AtlasSweep sweep, long elapsedMs) {
        List<AtlasCandidate> rows = sweep.candidates();
        Map<String, Map<String, Integer>> summary = new TreeMap<>();
        for (AtlasCandidate row : rows) {
            summary.computeIfAbsent(row.structureId(), key -> new LinkedHashMap<>())
                    .merge(row.verdict().name(), 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder(rows.size() * 64 + 4096);
        out.append("{\n");
        out.append("  \"radius\": ").append(level.getWorldBorder().getSize() / 2.0).append(",\n");
        out.append("  \"elapsedMs\": ").append(elapsedMs).append(",\n");
        out.append("  \"structuresSwept\": ").append(sweep.structuresSwept()).append(",\n");
        out.append("  \"refusal\": ").append(sweep.refusalReason() == null
                ? "null"
                : "\"" + sweep.refusalReason().replace("\"", "'") + "\"").append(",\n");
        out.append("  \"summary\": {\n");
        boolean firstStructure = true;
        for (Map.Entry<String, Map<String, Integer>> entry : summary.entrySet()) {
            if (!firstStructure) {
                out.append(",\n");
            }
            firstStructure = false;
            out.append("    \"").append(entry.getKey()).append("\": {");
            boolean firstVerdict = true;
            for (Map.Entry<String, Integer> verdict : entry.getValue().entrySet()) {
                if (!firstVerdict) {
                    out.append(", ");
                }
                firstVerdict = false;
                out.append('"').append(verdict.getKey().toLowerCase(Locale.ROOT)).append("\": ")
                        .append(verdict.getValue());
            }
            out.append('}');
        }
        out.append("\n  },\n");
        List<AtlasCandidate> accepted = new java.util.ArrayList<>();
        List<AtlasCandidate> rejected = new java.util.ArrayList<>();
        for (AtlasCandidate row : rows) {
            (row.verdict() == LatitudeStructureLocateService.CandidateVerdict.ACCEPTED
                    ? accepted : rejected).add(row);
        }
        int rejectedBudget = Math.max(0, MAX_CANDIDATE_ROWS - accepted.size());
        int stride = rejected.isEmpty() || rejected.size() <= rejectedBudget
                ? 1
                : (rejected.size() / Math.max(1, rejectedBudget)) + 1;
        List<AtlasCandidate> written = new java.util.ArrayList<>(accepted);
        for (int i = 0; i < rejected.size(); i += stride) {
            written.add(rejected.get(i));
        }
        out.append("  \"candidatesTotal\": ").append(rows.size()).append(",\n");
        out.append("  \"candidatesWritten\": ").append(written.size()).append(",\n");
        out.append("  \"acceptedWrittenInFull\": true,\n");
        out.append("  \"rejectedSampleStride\": ").append(stride).append(",\n");
        out.append("  \"candidates\": [\n");
        for (int i = 0; i < written.size(); i++) {
            AtlasCandidate row = written.get(i);
            out.append("    {\"id\": \"").append(row.structureId())
                    .append("\", \"x\": ").append(row.blockX())
                    .append(", \"z\": ").append(row.blockZ())
                    .append(", \"village\": ").append(row.village())
                    .append(", \"verdict\": \"")
                    .append(row.verdict().name().toLowerCase(Locale.ROOT)).append("\"}");
            out.append(i + 1 < written.size() ? ",\n" : "\n");
        }
        out.append("  ]\n}\n");
        return out.toString();
    }
}
