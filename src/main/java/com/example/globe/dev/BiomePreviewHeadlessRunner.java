package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

public final class BiomePreviewHeadlessRunner {
    private static final AtomicBoolean TRIGGERED = new AtomicBoolean(false);
    private static final String ARG_FLAG = "latdevBiomePng";
    private static final String PROP_KEY = "latdev.biomePng";
    private static final Pattern PROP_PAIR = Pattern.compile(
            "(?i)([a-z][a-z0-9_]*)\\s*=\\s*([^;]+?)(?=(?:\\s*[;,]\\s*[a-z][a-z0-9_]*\\s*=)|$)");

    private BiomePreviewHeadlessRunner() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BiomePreviewHeadlessRunner::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        if (!TRIGGERED.compareAndSet(false, true)) {
            return;
        }

        Config config = parseConfig();
        if (!config.enabled) {
            return;
        }

        server.execute(() -> runExportAndStop(server, config));
    }

    private static void runExportAndStop(MinecraftServer server, Config config) {
        ServerWorld world = server.getOverworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][headless] no overworld available; stopping server");
            server.stop(false);
            return;
        }

        try {
            int y = MathHelper.clamp(config.y, 0, 320);
            int radius = config.radiusBlocks != null
                    ? Math.max(1, config.radiusBlocks)
                    : radiusFromSizeOrWorld(config.sizePreset, world);
            long worldSeed = world.getSeed();
            long effectiveSeed = config.seedOverride != null ? config.seedOverride : worldSeed;
            Path outputDir = config.outDir != null ? config.outDir : defaultOutDir(server.getRunDirectory());

            LatitudeBiomes.setWorldSeed(effectiveSeed);
            String startMessage = String.format(
                    Locale.ROOT,
                    "[latdev][headless] starting export seed=%d worldSeed=%d radius=%d steps=%s y=%d out=%s",
                    effectiveSeed,
                    worldSeed,
                    radius,
                    config.steps,
                    y,
                    outputDir);
            GlobeMod.LOGGER.info(startMessage);
            System.out.println(startMessage);

            for (int step : config.steps) {
                BiomePreviewExporter.ExportResult result = BiomePreviewExporter.export(
                        world, radius, step, y, server.getRunDirectory());
                BiomePreviewExporter.ExportResult finalized = finalizeOutput(result, worldSeed, effectiveSeed, outputDir);

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
                System.out.println(finishMessage);
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][headless] export failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][headless] stopping server");
            server.stop(false);
        }
    }

    private static Path defaultOutDir(Path runDir) {
        Path normalized = runDir.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (fileName != null && "run-headless".equalsIgnoreCase(fileName.toString())) {
            Path parent = normalized.getParent();
            if (parent != null) {
                return parent.resolve("run").resolve("latdev").resolve("biome-previews").toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static BiomePreviewExporter.ExportResult finalizeOutput(BiomePreviewExporter.ExportResult result,
                                                                    long worldSeed,
                                                                    long effectiveSeed,
                                                                    Path outDir) throws IOException {
        Path targetDir = outDir != null ? outDir : result.pngPath().getParent();
        if (targetDir == null) {
            return result;
        }
        Files.createDirectories(targetDir);

        String seedSource = String.valueOf(worldSeed);
        String seedTarget = String.valueOf(effectiveSeed);
        String pngName = result.pngPath().getFileName().toString().replace(seedSource, seedTarget);
        String txtName = result.txtPath().getFileName().toString().replace(seedSource, seedTarget);
        Path targetPng = targetDir.resolve(pngName);
        Path targetTxt = targetDir.resolve(txtName);

        moveIfDifferent(result.pngPath(), targetPng);
        moveIfDifferent(result.txtPath(), targetTxt);

        if (Files.exists(targetTxt)) {
            List<String> lines = Files.readAllLines(targetTxt);
            if (!lines.isEmpty() && lines.get(0).startsWith("seed=")) {
                lines.set(0, "seed=" + effectiveSeed);
            }
            Files.write(targetTxt, lines);
        }

        return new BiomePreviewExporter.ExportResult(
                targetPng,
                targetTxt,
                result.width(),
                result.height(),
                result.totalSamples(),
                result.durationMs());
    }

    private static void moveIfDifferent(Path src, Path dst) throws IOException {
        if (src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
            return;
        }
        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private static int radiusFromSizeOrWorld(String sizePreset, ServerWorld world) {
        if (sizePreset != null) {
            String normalized = sizePreset.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "itty", "xsmall" -> 3750;
                case "small" -> 5000;
                case "regular" -> 7500;
                case "large" -> 10000;
                case "massive" -> 20000;
                default -> authoritativeRadius(world);
            };
        }
        return authoritativeRadius(world);
    }

    private static int authoritativeRadius(ServerWorld world) {
        int borderRadius = Math.max(0, (int) Math.floor(world.getWorldBorder().getSize() * 0.5) - 16);
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) {
            return MathHelper.clamp(activeRadius, 1, Math.max(1, borderRadius));
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
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;

        return new Config(enabled, seed, radius, size, steps, y, out);
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
        List<String> latdevKeys = List.of("seed", "radius", "size", "step", "steps", "y", "out");
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
                out.put(key, value);
                if (latdevKeys.contains(key)) {
                    enabled = true;
                }
                continue;
            }

            String key = token.toLowerCase(Locale.ROOT);
            if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                out.put(key, args.get(i + 1));
                i++;
                if (latdevKeys.contains(key)) {
                    enabled = true;
                }
            } else {
                out.put(key, "true");
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
                out.put(key, value);
            }
        }
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
            deduped.add(MathHelper.clamp(parsed, 8, 512));
        }
        return new ArrayList<>(deduped);
    }

    private record Config(boolean enabled,
                          Long seedOverride,
                          Integer radiusBlocks,
                          String sizePreset,
                          List<Integer> steps,
                          int y,
                          Path outDir) {
    }
}
