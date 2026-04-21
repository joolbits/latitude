package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BiomePreviewHeadlessRunner {
    private static final AtomicBoolean TRIGGERED = new AtomicBoolean(false);
    private static final String ARG_FLAG = "latdevBiomePng";
    private static final String PROP_KEY = "latdev.biomePng";
    private static final String SEARCH_ARG_FLAG = "latdevBiomeSearch";
    private static final String SEARCH_PROP_KEY = "latdev.biomeSearch";
    private static final String AUDIT_ARG_FLAG = "latdevBandAudit";
    private static final String AUDIT_PROP_KEY = "latdev.bandAudit";
    private static final String EMIT_HEIGHT_PROP_KEY = "latitude.emitHeight";
    private static final Pattern PROP_PAIR = Pattern.compile(
            "(?i)([a-z][a-z0-9_]*)\\s*=\\s*([^;]+?)(?=(?:\\s*[;,]\\s*[a-z][a-z0-9_]*\\s*=)|$)");
    private static final DateTimeFormatter SEARCH_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private BiomePreviewHeadlessRunner() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BiomePreviewHeadlessRunner::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        if (!TRIGGERED.compareAndSet(false, true)) {
            return;
        }

        AuditConfig auditConfig = parseAuditConfig();
        if (auditConfig.enabled) {
            server.execute(() -> runAuditAndStop(server, auditConfig));
            return;
        }

        SearchConfig searchConfig = parseSearchConfig();
        if (searchConfig.enabled) {
            server.execute(() -> runSearchAndStop(server, searchConfig));
            return;
        }

        Config config = parseConfig();
        if (!config.enabled) {
            return;
        }

        server.execute(() -> runExportAndStop(server, config));
    }

    private static void runExportAndStop(MinecraftServer server, Config config) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][headless] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        try {
            int y = Mth.clamp(config.y, 0, 320);
            int radius = config.radiusBlocks != null
                    ? Math.max(1, config.radiusBlocks)
                    : radiusFromSizeOrWorld(config.sizePreset, world);
            long worldSeed = world.getSeed();
            long effectiveSeed = config.seedOverride != null ? config.seedOverride : worldSeed;
            String runLabel = BiomePreviewExporter.resolveRunLabel(config.runLabel);
            Path outputDir = config.outDir != null ? config.outDir : defaultOutDir(server.getServerDirectory());

            LatitudeBiomes.setWorldSeed(effectiveSeed);
            LatitudeBiomes.setActiveRadiusBlocks(radius);
            String startMessage = String.format(
                    Locale.ROOT,
                    "[latdev][headless] starting export seed=%d worldSeed=%d radius=%d steps=%s y=%d layers=%s overlays=%s emitBiomeIndex=%s emitHeight=%s out=%s run=%s",
                    effectiveSeed,
                    worldSeed,
                    radius,
                    config.steps,
                    y,
                    config.exportOptions.layerCsv(),
                    config.exportOptions.overlayCsv(),
                    config.exportOptions.emitBiomeIndex(),
                    config.exportOptions.emitHeight(),
                    outputDir,
                    runLabel);
            GlobeMod.LOGGER.info(startMessage);

            for (int step : config.steps) {
                BiomePreviewExporter.ExportResult result = BiomePreviewExporter.export(
                        world, radius, step, y, server.getServerDirectory(), effectiveSeed, config.exportOptions, runLabel);
                BiomePreviewExporter.ExportResult finalized = finalizeOutput(result, effectiveSeed, outputDir, runLabel);

                String finishMessage = String.format(
                        Locale.ROOT,
                        "[latdev][headless] finished export step=%d file=%s sidecar=%s image=%dx%d samples=%d durationMs=%d",
                        step,
                        finalized.pngPath(),
                        finalized.txtPath(),
                        finalized.width(),
                        finalized.height(),
                        finalized.totalSamples(),
                        finalized.durationMs());
                GlobeMod.LOGGER.info(finishMessage);
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][headless] export failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][headless] stopping server");
            server.halt(false);
        }
    }

    private static void runSearchAndStop(MinecraftServer server, SearchConfig config) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][search] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        try {
            int y = Mth.clamp(config.y, 0, 320);
            int radius = config.radiusBlocks != null
                    ? Math.max(1, config.radiusBlocks)
                    : radiusFromSizeOrWorld(config.sizePreset, world);
            Instant generatedAt = Instant.now();
            Path outputRoot = config.outDir != null
                    ? config.outDir
                    : server.getServerDirectory().toAbsolutePath().normalize().resolve("seed-search");
            Path outputDir = outputRoot.resolve(SEARCH_TIMESTAMP.format(generatedAt));
            GitStamp gitStamp = currentGitStamp();
            BiomeSamplerTools.SearchOptions options = new BiomeSamplerTools.SearchOptions(
                    config.seedStart,
                    Math.max(1, config.seedCount),
                    config.targetBiomes,
                    config.requireAll,
                    radius,
                    Math.max(1, config.stepBlocks),
                    y,
                    Math.max(1, config.maxResults));

            String startMessage = String.format(
                    Locale.ROOT,
                    "[latdev][search] starting seed search seedStart=%d seedCount=%d targets=%s requireAll=%s radius=%d step=%d y=%d maxResults=%d out=%s",
                    options.seedStart(),
                    options.seedCount(),
                    options.targetBiomes(),
                    options.requireAll(),
                    options.radiusBlocks(),
                    options.stepBlocks(),
                    options.y(),
                    options.maxResults(),
                    outputDir);
            GlobeMod.LOGGER.info(startMessage);

            BiomeSamplerTools.SearchReport report = BiomeSamplerTools.searchSeeds(
                    world,
                    options,
                    gitStamp.branch(),
                    gitStamp.commit(),
                    generatedAt);
            BiomeSamplerTools.writeSearchReportFiles(outputDir, report);

            String finishMessage = String.format(
                    Locale.ROOT,
                    "[latdev][search] finished matches=%d results=%s",
                    report.results().size(),
                    outputDir.resolve("results.json"));
            GlobeMod.LOGGER.info(finishMessage);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][search] seed search failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][search] stopping server");
            server.halt(false);
        }
    }

    private static void runAuditAndStop(MinecraftServer server, AuditConfig config) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][audit] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        try {
            int y = Mth.clamp(config.y, 0, 320);
            int radius = config.radiusBlocks != null
                    ? Math.max(1, config.radiusBlocks)
                    : radiusFromSizeOrWorld(config.sizePreset, world);
            long worldSeed = world.getSeed();
            long effectiveSeed = config.seedOverride != null ? config.seedOverride : worldSeed;

            LatitudeBiomes.setWorldSeed(effectiveSeed);
            LatitudeBiomes.setActiveRadiusBlocks(radius);

            Path outputDir = config.outDir != null
                    ? config.outDir
                    : server.getServerDirectory().toAbsolutePath().normalize()
                            .getParent().resolve("run").resolve("latdev");
            Files.createDirectories(outputDir);

            java.util.Set<String> watched = new LinkedHashSet<>(config.watchedBiomes);
            java.util.Set<String> control = new LinkedHashSet<>(config.controlBiomes);

            for (double[] window : config.windows) {
                double minDeg = window[0];
                double maxDeg = window[1];
                String tag = String.format(Locale.ROOT, "%.0f-%.0f", minDeg, maxDeg);

                GlobeMod.LOGGER.info("[latdev][audit] scanning window {}° seed={} radius={} step={} y={}",
                        tag, effectiveSeed, radius, config.stepBlocks, y);

                BiomeSamplerTools.BandAuditReport report = BiomeSamplerTools.bandAudit(
                        world, effectiveSeed, radius, config.stepBlocks, y,
                        minDeg, maxDeg, watched, control);

                String fileName = String.format(Locale.ROOT, "band-audit_%s_seed%d_R%d.txt",
                        tag, effectiveSeed, radius);
                Path outFile = outputDir.resolve(fileName);
                BiomeSamplerTools.writeBandAuditReport(outFile, report);

                GlobeMod.LOGGER.info("[latdev][audit] window {}°: {} samples, report={}",
                        tag, report.totalSamplesInWindow(), outFile);
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][audit] audit failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][audit] stopping server");
            server.halt(false);
        }
    }

    private static Path defaultOutDir(Path runDir) {
        Path normalized = runDir.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (fileName != null && "run-headless".equalsIgnoreCase(fileName.toString())) {
            Path parent = normalized.getParent();
            if (parent != null) {
                return BiomePreviewExporter.defaultAtlasRoot(parent.resolve("run"));
            }
        }
        return BiomePreviewExporter.defaultAtlasRoot(normalized);
    }

    private static BiomePreviewExporter.ExportResult finalizeOutput(BiomePreviewExporter.ExportResult result,
                                                                    long effectiveSeed,
                                                                    Path outDir,
                                                                    String runLabel) throws IOException {
        if (result == null) {
            throw new IOException("Height-enabled atlas export did not produce an ExportResult before finalizeOutput");
        }
        Path sourceStepDir = result.pngPath().toAbsolutePath().normalize().getParent();
        Path targetRoot = outDir != null ? outDir : deriveAtlasRoot(sourceStepDir);
        Path targetDir = BiomePreviewExporter.atlasStepDirectory(
                targetRoot,
                effectiveSeed,
                runLabel,
                result.radiusBlocks(),
                result.stepBlocks());
        if (targetDir == null) {
            return result;
        }
        Files.createDirectories(targetDir);

        if (sourceStepDir != null && !sourceStepDir.toAbsolutePath().normalize().equals(targetDir.toAbsolutePath().normalize())) {
            moveDirectoryContents(sourceStepDir, targetDir);
            cleanupEmptyAncestors(sourceStepDir);
        }

        rewriteSeedMetadata(targetDir, effectiveSeed);

        Path targetPng = targetDir.resolve(result.pngPath().getFileName());
        Path targetTxt = targetDir.resolve(result.txtPath().getFileName());

        return new BiomePreviewExporter.ExportResult(
                targetPng,
                targetTxt,
                effectiveSeed,
                result.radiusBlocks(),
                result.stepBlocks(),
                result.width(),
                result.height(),
                result.totalSamples(),
                result.durationMs());
    }

    private static void moveDirectoryContents(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            return;
        }
        Files.createDirectories(targetDir);

        try (Stream<Path> stream = Files.list(sourceDir)) {
            List<Path> children = stream.toList();
            for (Path child : children) {
                Path target = targetDir.resolve(child.getFileName());
                Files.move(child, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void cleanupEmptyAncestors(Path start) throws IOException {
        Path cursor = start;
        while (cursor != null) {
            if (!Files.isDirectory(cursor)) {
                break;
            }
            try (Stream<Path> stream = Files.list(cursor)) {
                if (stream.findAny().isPresent()) {
                    break;
                }
            }
            Files.deleteIfExists(cursor);
            cursor = cursor.getParent();
            if (cursor == null || cursor.getFileName() == null) {
                break;
            }
            if ("atlas".equalsIgnoreCase(cursor.getFileName().toString())) {
                break;
            }
        }
    }

    private static void rewriteSeedMetadata(Path targetDir, long seed) throws IOException {
        if (targetDir == null || !Files.isDirectory(targetDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(targetDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".txt")) {
                    rewriteSeedInTextFile(file, seed);
                } else if (name.endsWith(".json")) {
                    rewriteSeedInJsonFile(file, seed);
                }
            }
        }
    }

    private static void rewriteSeedInTextFile(Path file, long seed) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("seed=")) {
                lines.set(i, "seed=" + seed);
                Files.write(file, lines);
                return;
            }
        }
    }

    private static void rewriteSeedInJsonFile(Path file, long seed) throws IOException {
        String content = Files.readString(file);
        String updated = content.replaceFirst("\"seed\"\\s*:\\s*-?\\d+", "\"seed\": " + seed);
        if (!updated.equals(content)) {
            Files.writeString(file, updated);
        }
    }

    private static Path deriveAtlasRoot(Path sourceStepDir) {
        if (sourceStepDir == null) {
            return null;
        }
        Path radiusDir = sourceStepDir.getParent();
        if (radiusDir == null) {
            return null;
        }
        Path maybeRunDir = radiusDir.getParent();
        if (maybeRunDir == null) {
            return null;
        }
        Path seedDir;
        if (maybeRunDir.getFileName() != null && maybeRunDir.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("run_")) {
            seedDir = maybeRunDir.getParent();
        } else {
            seedDir = maybeRunDir;
        }
        if (seedDir == null) {
            return null;
        }
        return seedDir.getParent();
    }

    private static int radiusFromSizeOrWorld(String sizePreset, ServerLevel world) {
        if (sizePreset != null) {
            String normalized = sizePreset.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "itty", "xsmall" -> 3750;
                case "tiny", "small" -> 5000;
                case "regular" -> 7500;
                case "large" -> 10000;
                case "massive" -> 20000;
                default -> authoritativeRadius(world);
            };
        }
        return authoritativeRadius(world);
    }

    private static int authoritativeRadius(ServerLevel world) {
        int borderRadius = Math.max(0, (int) Math.floor(world.getWorldBorder().getSize() * 0.5) - 16);
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) {
            return Mth.clamp(activeRadius, 1, Math.max(1, borderRadius));
        }
        return Math.max(1, borderRadius);
    }

    private static Config parseConfig() {
        Map<String, String> kv = new HashMap<>();
        List<String> launchArgs = launchArgs();
        boolean enabled = collectProgramArgs(launchArgs, kv);

        String prop = System.getProperty(PROP_KEY, "");
        if (!prop.isBlank()) {
            enabled = true;
            parsePropertyOptions(prop, kv);
        }

        Long seed = parseLong(kv.get("seed"));
        Integer radius = parseInt(kv.get("radius"));
        String size = kv.get("size");
        List<Integer> steps = parseSteps(kv.get("steps"));
        if (steps.isEmpty()) {
            steps = parseSteps(kv.get("step"));
        }
        if (steps.isEmpty()) {
            steps = List.of(64);
        }
        int y = parseInt(kv.get("y"), 64);
        boolean bundle = parseBoolean(kv.get("bundle"));
        ParsedLayers parsedLayers = parseLayers(kv.get("layers"));
        List<String> masks = coalesceMaskValues(parsedLayers.masks(), parseMaskValues(kv.get("mask")));
        List<BiomePreviewExporter.Overlay> overlays = parseOverlays(coalesceListValues(kv.get("overlay"), kv.get("overlays")));
        boolean emitHeightProp = parseBoolean(System.getProperty(EMIT_HEIGHT_PROP_KEY, ""));
        boolean emitBiomeIndex = parseBoolean(kv.get("emitbiomeindex"));
        boolean emitHeight = emitHeightProp || parseBoolean(kv.get("emitheight"));
        BiomePreviewExporter.ExportOptions baseOptions = BiomePreviewExporter.ExportOptions.from(bundle, parsedLayers.layers(), overlays, masks);
        // --ruggedness true: append RUGGEDNESS to whatever layer set was chosen
        boolean addRuggedness = parseBoolean(kv.get("ruggedness"));
        if (addRuggedness && !baseOptions.layers().contains(BiomePreviewExporter.Layer.RUGGEDNESS)) {
            java.util.EnumSet<BiomePreviewExporter.Layer> ext = java.util.EnumSet.copyOf(baseOptions.layers());
            ext.add(BiomePreviewExporter.Layer.RUGGEDNESS);
            baseOptions = new BiomePreviewExporter.ExportOptions(
                    ext, baseOptions.overlays(), baseOptions.maskLayers(), baseOptions.writeLegends(),
                    false, false);
        }
        // Emit legend metadata whenever biome index output is requested so atlas runs
        // keep the policy/legend authority alongside the sampled layer payload.
        BiomePreviewExporter.ExportOptions exportOptions = new BiomePreviewExporter.ExportOptions(
                baseOptions.layers(),
                baseOptions.overlays(),
                baseOptions.maskLayers(),
                baseOptions.writeLegends() || emitBiomeIndex,
                emitBiomeIndex,
                emitHeight);
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;

        String runLabel = null;
        for (String key : List.of("runlabel", "run", "label")) {
            if (kv.containsKey(key) && !kv.get(key).isBlank()) {
                runLabel = kv.get(key).trim();
                break;
            }
        }

        return new Config(enabled, seed, radius, size, steps, y, exportOptions, runLabel, out);
    }

    private static SearchConfig parseSearchConfig() {
        Map<String, String> kv = new HashMap<>();
        List<String> launchArgs = launchArgs();
        boolean enabled = collectSearchProgramArgs(launchArgs, kv);

        String prop = System.getProperty(SEARCH_PROP_KEY, "");
        if (!prop.isBlank()) {
            enabled = true;
            parsePropertyOptions(prop, kv);
        }

        List<String> targetBiomes = parseTargetBiomes(kv.get("targetbiome"));
        if (targetBiomes.isEmpty()) {
            enabled = false;
        }

        long seedStart = parseLong(kv.get("seedstart")) != null ? parseLong(kv.get("seedstart")) : 0L;
        int seedCount = parseInt(kv.get("seedcount"), 1);
        Integer radius = parseInt(kv.get("radius"));
        String size = kv.get("size");
        int step = parseInt(kv.get("step"), 128);
        int y = parseInt(kv.get("y"), 64);
        int maxResults = parseInt(kv.get("maxresults"), 10);
        boolean requireAll = parseBoolean(kv.get("requireall"));
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;

        return new SearchConfig(enabled, seedStart, seedCount, targetBiomes, requireAll, radius, size, step, y, maxResults, out);
    }

    private static AuditConfig parseAuditConfig() {
        Map<String, String> kv = new HashMap<>();
        List<String> launchArgs = launchArgs();
        boolean enabled = collectAuditProgramArgs(launchArgs, kv);

        String prop = System.getProperty(AUDIT_PROP_KEY, "");
        if (!prop.isBlank()) {
            enabled = true;
            parsePropertyOptions(prop, kv);
        }

        String windowsRaw = kv.get("windows");
        if (windowsRaw == null || windowsRaw.isBlank()) {
            enabled = false;
        }

        List<double[]> windows = parseWindows(windowsRaw);
        if (windows.isEmpty()) {
            enabled = false;
        }

        Long seed = parseLong(kv.get("seed"));
        Integer radius = parseInt(kv.get("radius"));
        String size = kv.get("size");
        int step = parseInt(kv.get("step"), 16);
        int y = parseInt(kv.get("y"), 64);
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;

        List<String> watched = parseTargetBiomes(kv.get("watched"));
        if (watched.isEmpty()) {
            watched = List.of(
                    "minecraft:snowy_plains",
                    "minecraft:snowy_taiga",
                    "minecraft:frozen_river");
        }
        List<String> control = parseTargetBiomes(kv.get("control"));
        if (control.isEmpty()) {
            control = List.of(
                    "minecraft:plains",
                    "minecraft:forest",
                    "minecraft:taiga",
                    "minecraft:river");
        }

        return new AuditConfig(enabled, seed, radius, size, step, y, windows, watched, control, out);
    }

    private static boolean collectAuditProgramArgs(List<String> args, Map<String, String> out) {
        boolean enabled = false;
        List<String> auditKeys = List.of("seed", "radius", "size", "step", "y", "out", "windows", "watched", "control");
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!arg.startsWith("--")) {
                continue;
            }

            String token = arg.substring(2);
            if (token.equalsIgnoreCase(AUDIT_ARG_FLAG)) {
                enabled = true;
                continue;
            }

            int eq = token.indexOf('=');
            if (eq >= 0) {
                String key = token.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = token.substring(eq + 1);
                putOption(out, key, value);
                if (auditKeys.contains(key)) {
                    enabled = true;
                }
                continue;
            }

            String key = token.toLowerCase(Locale.ROOT);
            if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                putOption(out, key, args.get(i + 1));
                i++;
                if (auditKeys.contains(key)) {
                    enabled = true;
                }
            } else {
                putOption(out, key, "true");
            }
        }
        return enabled;
    }

    private static List<double[]> parseWindows(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<double[]> windows = new ArrayList<>();
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            int dash = trimmed.indexOf('-');
            if (dash <= 0 || dash >= trimmed.length() - 1) {
                continue;
            }
            try {
                double min = Double.parseDouble(trimmed.substring(0, dash).trim());
                double max = Double.parseDouble(trimmed.substring(dash + 1).trim());
                if (min >= 0 && max > min && max <= 90) {
                    windows.add(new double[]{min, max});
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return windows;
    }

    private static List<String> launchArgs() {
        try {
            return Arrays.asList(FabricLoader.getInstance().getLaunchArguments(true));
        } catch (Throwable t) {
            GlobeMod.LOGGER.warn("[latdev][headless] failed to read launch arguments: {}", t.getMessage());
            return List.of();
        }
    }

    private static boolean collectProgramArgs(List<String> args, Map<String, String> out) {
        boolean enabled = false;
        List<String> latdevKeys = List.of("seed", "radius", "size", "step", "steps", "y", "out", "bundle", "layers", "mask", "overlay", "overlays", "emitbiomeindex", "emitheight", "ruggedness");
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!arg.startsWith("--")) {
                continue;
            }

            String token = arg.substring(2);
            if (token.equalsIgnoreCase(ARG_FLAG)) {
                enabled = true;
                continue;
            }

            int eq = token.indexOf('=');
            if (eq >= 0) {
                String key = token.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = token.substring(eq + 1);
                putOption(out, key, value);
                if (latdevKeys.contains(key)) {
                    enabled = true;
                }
                continue;
            }

            String key = token.toLowerCase(Locale.ROOT);
            if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                putOption(out, key, args.get(i + 1));
                i++;
                if (latdevKeys.contains(key)) {
                    enabled = true;
                }
            } else {
                putOption(out, key, "true");
            }
        }
        return enabled;
    }

    private static boolean collectSearchProgramArgs(List<String> args, Map<String, String> out) {
        boolean enabled = false;
        List<String> searchKeys = List.of("seedstart", "seedcount", "targetbiome", "requireall", "radius", "size", "step", "y", "maxresults", "out");
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!arg.startsWith("--")) {
                continue;
            }

            String token = arg.substring(2);
            if (token.equalsIgnoreCase(SEARCH_ARG_FLAG)) {
                enabled = true;
                continue;
            }

            int eq = token.indexOf('=');
            if (eq >= 0) {
                String key = token.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = token.substring(eq + 1);
                putOption(out, key, value);
                if (searchKeys.contains(key)) {
                    enabled = true;
                }
                continue;
            }

            String key = token.toLowerCase(Locale.ROOT);
            if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                putOption(out, key, args.get(i + 1));
                i++;
                if (searchKeys.contains(key)) {
                    enabled = true;
                }
            } else {
                putOption(out, key, "true");
            }
        }
        return enabled;
    }

    private static void parsePropertyOptions(String prop, Map<String, String> out) {
        Matcher matcher = PROP_PAIR.matcher(prop);
        while (matcher.find()) {
            String key = matcher.group(1).trim().toLowerCase(Locale.ROOT);
            String value = matcher.group(2).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                putOption(out, key, value);
            }
        }
    }

    private static void putOption(Map<String, String> out, String key, String value) {
        if (key == null || value == null) {
            return;
        }
        if (isMergeKey(key) && out.containsKey(key) && !out.get(key).isBlank()) {
            out.put(key, out.get(key) + "," + value);
            return;
        }
        out.put(key, value);
    }

    private static boolean isMergeKey(String key) {
        return "steps".equals(key)
                || "layers".equals(key)
                || "mask".equals(key)
                || "overlay".equals(key)
                || "overlays".equals(key)
                || "targetbiome".equals(key);
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        Integer value = parseInt(raw);
        return value != null ? value : fallback;
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("on")
                || normalized.equals("enabled")
                || normalized.equals("bundle");
    }

    private static ParsedLayers parseLayers(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedLayers(List.of(), List.of());
        }

        LinkedHashSet<BiomePreviewExporter.Layer> layers = new LinkedHashSet<>();
        LinkedHashSet<String> masks = new LinkedHashSet<>();
        boolean collectingMaskValues = false;
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("biomemask:")) {
                String maskToken = token.substring("biomemask:".length()).trim();
                if (!maskToken.isEmpty()) {
                    masks.add(maskToken);
                }
                collectingMaskValues = false;
                continue;
            }
            if (normalized.startsWith("mask=")) {
                String maskToken = token.substring("mask=".length()).trim();
                if (!maskToken.isEmpty()) {
                    masks.add(maskToken);
                }
                collectingMaskValues = true;
                continue;
            }
            BiomePreviewExporter.Layer layer = BiomePreviewExporter.Layer.fromToken(part);
            if (layer != null) {
                layers.add(layer);
                collectingMaskValues = false;
                continue;
            }
            if (collectingMaskValues) {
                masks.add(token);
            }
        }
        return new ParsedLayers(new ArrayList<>(layers), new ArrayList<>(masks));
    }

    private static List<String> parseMaskValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> masks = new LinkedHashSet<>();
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) {
                masks.add(token);
            }
        }
        return new ArrayList<>(masks);
    }

    private static List<String> coalesceMaskValues(List<String> first, List<String> second) {
        LinkedHashSet<String> masks = new LinkedHashSet<>();
        if (first != null) {
            masks.addAll(first);
        }
        if (second != null) {
            masks.addAll(second);
        }
        return new ArrayList<>(masks);
    }

    private static List<BiomePreviewExporter.Overlay> parseOverlays(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        LinkedHashSet<BiomePreviewExporter.Overlay> overlays = new LinkedHashSet<>();
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            BiomePreviewExporter.Overlay overlay = BiomePreviewExporter.Overlay.fromToken(part);
            if (overlay != null) {
                overlays.add(overlay);
            }
        }
        return new ArrayList<>(overlays);
    }

    private static String coalesceListValues(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + "," + second;
        }
        if (hasFirst) {
            return first;
        }
        if (hasSecond) {
            return second;
        }
        return "";
    }

    private static List<Integer> parseSteps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        LinkedHashSet<Integer> deduped = new LinkedHashSet<>();
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            Integer parsed = parseInt(part);
            if (parsed == null) {
                continue;
            }
            deduped.add(Mth.clamp(parsed, 8, 512));
        }
        return new ArrayList<>(deduped);
    }

    private static List<String> parseTargetBiomes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        String[] parts = raw.split("[,|]");
        for (String part : parts) {
            String token = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                deduped.add(token);
            }
        }
        return new ArrayList<>(deduped);
    }

    private static GitStamp currentGitStamp() {
        return new GitStamp(runGit("rev-parse", "--abbrev-ref", "HEAD"), runGit("rev-parse", "--short", "HEAD"));
    }

    private static String runGit(String... args) {
        try {
            Process process = new ProcessBuilder().command(commandWithGit(args)).start();
            byte[] out = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit == 0) {
                String text = new String(out).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static List<String> commandWithGit(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private record Config(boolean enabled,
                          Long seedOverride,
                          Integer radiusBlocks,
                          String sizePreset,
                          List<Integer> steps,
                          int y,
                          BiomePreviewExporter.ExportOptions exportOptions,
                          String runLabel,
                          Path outDir) {
    }

    private record SearchConfig(boolean enabled,
                                long seedStart,
                                int seedCount,
                                List<String> targetBiomes,
                                boolean requireAll,
                                Integer radiusBlocks,
                                String sizePreset,
                                int stepBlocks,
                                int y,
                                int maxResults,
                                Path outDir) {
    }

    private record ParsedLayers(List<BiomePreviewExporter.Layer> layers,
                                List<String> masks) {
    }

    private record GitStamp(String branch, String commit) {
    }

    private record AuditConfig(boolean enabled,
                                Long seedOverride,
                                Integer radiusBlocks,
                                String sizePreset,
                                int stepBlocks,
                                int y,
                                List<double[]> windows,
                                List<String> watchedBiomes,
                                List<String> controlBiomes,
                                Path outDir) {
    }
}
