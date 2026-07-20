package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.mixin.NoiseChunkGeneratorAccessor;
import com.example.globe.util.BiomeSamplerTools;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    private static final String LOCATE_BOUNDARY_PROP_KEY = "latdev.locateBoundary";
    private static final String ALPINE_AUDIT_PROP_KEY = "latdev.alpineAudit";
    private static final String EMIT_HEIGHT_PROP_KEY = "latitude.emitHeight";
    private static final String PROBE_PROP_KEY = "latdev.probe";
    private static final String PROBE_TARGET_PROP = "latdev.probe.target";
    private static final String PROBE_TYPES_PROP = "latdev.probe.types";
    private static final String PROBE_NAMES_PROP = "latdev.probe.names";
    private static final String PROBE_GRID_PROP = "latdev.probe.grid";
    private static final String PROBE_OUT_PROP = "latdev.probe.out";
    private static final String PROBE_RADIUS_PROP = "latdev.probe.radius";
    private static final String PROBE_MAX_COMBOS_PROP = "latdev.probe.maxCombos";
    private static final int PROBE_DEFAULT_MAX_COMBOS = 200_000;
    private static final Pattern PROP_PAIR = Pattern.compile(
            "(?i)([a-z][a-z0-9_]*)\\s*=\\s*([^;]+?)(?=(?:\\s*[;,]\\s*[a-z][a-z0-9_]*\\s*=)|$)");
    private static final DateTimeFormatter SEARCH_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final int PREVIEW_RADIUS_ITTY = 3750;
    private static final int PREVIEW_RADIUS_TINY = 5000;
    private static final int PREVIEW_RADIUS_SMALL = 7500;
    private static final int PREVIEW_RADIUS_REGULAR = 10000;
    private static final int PREVIEW_RADIUS_LARGE = 15000;
    private static final int PREVIEW_RADIUS_MASSIVE = 20000;
    private static final int PREVIEW_RADIUS_MAX = PREVIEW_RADIUS_MASSIVE;
    private static final ResourceKey<NoiseGeneratorSettings> GLOBE_REGULAR_SETTINGS_KEY =
            ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath("globe", "overworld_regular"));

    private BiomePreviewHeadlessRunner() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(BiomePreviewHeadlessRunner::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        if (!TRIGGERED.compareAndSet(false, true)) {
            return;
        }

        if (FreezeSweepProofHarness.isTriggered()) { // TEMP-DIAG (settled-sweep zero-claims round)
            FreezeSweepProofHarness.start(server);
            return;
        }

        if (TerrainProofHarness.isTriggered()) {
            server.execute(() -> TerrainProofHarness.runAndStop(server));
            return;
        }

        if (parseBoolean(System.getProperty(PROBE_PROP_KEY, ""))) {
            server.execute(() -> runFunctionProbeAndStop(server));
            return;
        }

        if (parseBoolean(System.getProperty(ALPINE_AUDIT_PROP_KEY, ""))) {
            server.execute(() -> runAlpineAuditAndStop(server));
            return;
        }

        LocateBoundaryConfig locateBoundaryConfig = parseLocateBoundaryConfig();
        if (locateBoundaryConfig.enabled) {
            server.execute(() -> runLocateBoundaryAndStop(server, locateBoundaryConfig));
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

        long worldSeed = world.getSeed();
        ExportJob job = null;
        try {
            int y = Mth.clamp(config.y, 0, 320);
            int radius = resolvePreviewRadius(world, config);
            int firstStep = !config.steps().isEmpty() ? config.steps().get(0) : 64;
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
            long widthForLog = Math.max(1L, (2L * (long) radius) / Math.max(1, firstStep) + 1L);
            GlobeMod.LOGGER.info(
                    "[latdev][headless] preview params radius={} step={} width={} height={} out={}",
                    radius,
                    firstStep,
                    widthForLog,
                    widthForLog,
                    outputDir);
            if (config.steps.isEmpty()) {
                GlobeMod.LOGGER.warn("[latdev][headless] no export steps configured; stopping server");
                LatitudeBiomes.setWorldSeed(worldSeed);
                server.halt(false);
                return;
            }

            // Drain the export synchronously on the server thread. END_SERVER_TICK
            // would otherwise stop firing once vanilla's pause-when-empty-seconds
            // (60s) elapses, freezing tick-driven sampling at full radius.
            job = new ExportJob(world, config, outputDir, effectiveSeed, worldSeed, radius, y, runLabel, server.getServerDirectory());
            while (!job.isDone()) {
                job.tick();
            }
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][headless] export failed", t);
        } finally {
            if (job != null) {
                job.close();
            } else {
                LatitudeBiomes.setWorldSeed(worldSeed);
            }
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

    // ------------------------------------------------------------------------------------------
    // Alpine snow-line audit (dev-only). Evaluates the ACTUAL compiled, deterministic
    // LatitudeBiomes.alpineSurfaceKind(x,y,z,radius) across a latitude x altitude grid against a
    // really-booted globe world, to prove the per-band snow onset behaves as designed (closes the
    // "live-eyeball-only" gap). Reads NOTHING but the public static function + the world seed/radius;
    // changes NO shipped worldgen logic.
    // ------------------------------------------------------------------------------------------

    private static final int ALPINE_AUDIT_KIND_SNOW = 2;
    private static final int[] ALPINE_AUDIT_REPORT_YS = {166, 170, 174, 178, 182, 186, 190};
    // (bandLabel, midLatDeg)
    private static final Object[][] ALPINE_AUDIT_BANDS = {
            {"TROPICAL", 10},
            {"SUBTROPICAL", 29},
            {"TEMPERATE", 42},
            {"SUBPOLAR", 58},
            {"POLAR", 78},
    };

    // --- Generic function probe ---------------------------------------------------------------
    // Reflectively sweeps ANY deterministic static method across a parameter grid and dumps a CSV.
    // This is the turnkey, no-new-Java-needed version of "call the actual compiled logic instead
    // of flying around looking at it" — see docs/binder/headless-verification-playbook.md. Boots a
    // real globe world so any FQCN#method reachable from this mod's classpath can be probed; if the
    // target class exposes static setWorldSeed(long)/setActiveRadiusBlocks(int) (as LatitudeBiomes
    // does), those are called first with the booted world's real seed so the probe matches a real
    // world rather than whatever static default the class boots with.
    //
    // Trigger: -Dlatdev.probe=true (checked before every other mode; paired with -Dlatdev.biomePng=disabled
    // to skip the atlas export on the same boot).
    // Config (all via -D system properties):
    //   latdev.probe.target = FQCN#methodName            e.g. com.example.globe.world.LatitudeBiomes#alpineSurfaceKind
    //   latdev.probe.types  = comma list, declared param order  (int|long|double|float|boolean)
    //                          e.g. int,int,int,int
    //   latdev.probe.names  = comma list, same length as types — cosmetic, used as grid keys + CSV columns
    //                          e.g. x,y,z,radius
    //   latdev.probe.grid   = ';'-separated "name=spec" entries, one per name in `names`. spec is one of:
    //                          - a single value:      556
    //                          - a comma list:         0,30,60,90
    //                          - an inclusive range:   150..200        (step 1)
    //                          - a stepped range:      0..3000:30
    //                          e.g. x=0..3000:30;y=150..200;z=556;radius=5000
    //   latdev.probe.out    = output CSV path (default run-headless/latdev/probe-report.csv)
    //   latdev.probe.radius = radius fed to setActiveRadiusBlocks() during init, if that method exists
    //                          on the target class (default 10000; independent of any grid dim named "radius")
    //   latdev.probe.maxCombos = safety cap on total grid combinations (default 200000)
    private static void runFunctionProbeAndStop(MinecraftServer server) {
        ServerLevel world = server.overworld();
        Path outPath = null;
        int rowCount = 0;
        try {
            if (world == null) {
                GlobeMod.LOGGER.error("[latdev][probe] no overworld available; stopping server");
                return;
            }
            String target = System.getProperty(PROBE_TARGET_PROP, "").trim();
            int hash = target.indexOf('#');
            if (hash <= 0 || hash == target.length() - 1) {
                GlobeMod.LOGGER.error("[latdev][probe] {} must be 'FQCN#methodName', got '{}'", PROBE_TARGET_PROP, target);
                return;
            }
            String className = target.substring(0, hash);
            String methodName = target.substring(hash + 1);

            String[] typeTokens = splitCsv(System.getProperty(PROBE_TYPES_PROP, ""));
            String[] names = splitCsv(System.getProperty(PROBE_NAMES_PROP, ""));
            if (typeTokens.length == 0 || typeTokens.length != names.length) {
                GlobeMod.LOGGER.error("[latdev][probe] {} and {} must be non-empty, equal-length comma lists (got types={}, names={})",
                        PROBE_TYPES_PROP, PROBE_NAMES_PROP, typeTokens.length, names.length);
                return;
            }
            Class<?>[] paramClasses = new Class<?>[typeTokens.length];
            for (int i = 0; i < typeTokens.length; i++) {
                paramClasses[i] = probeTypeClass(typeTokens[i]);
                if (paramClasses[i] == null) {
                    GlobeMod.LOGGER.error("[latdev][probe] unsupported type token '{}' (supported: int,long,double,float,boolean)", typeTokens[i]);
                    return;
                }
            }

            Class<?> targetClass;
            Method method;
            try {
                targetClass = Class.forName(className);
                method = targetClass.getDeclaredMethod(methodName, paramClasses);
                method.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                StringBuilder overloads = new StringBuilder();
                try {
                    Class<?> probedClass = Class.forName(className);
                    for (Method m : probedClass.getDeclaredMethods()) {
                        if (m.getName().equals(methodName)) {
                            overloads.append("\n  ").append(m);
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    overloads.append(" (class not found)");
                }
                GlobeMod.LOGGER.error("[latdev][probe] could not resolve {}#{}({}): {}. Candidates on that class:{}",
                        className, methodName, String.join(",", typeTokens), e.getMessage(), overloads);
                return;
            }
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                GlobeMod.LOGGER.error("[latdev][probe] {}#{} is not static — the probe only invokes static methods", className, methodName);
                return;
            }

            // Best-effort init: if the target class carries the usual LatitudeBiomes-style world
            // state setters, prime them with the REAL booted world so the probe reflects a real
            // world's seed/radius rather than a static default. No-op if absent.
            int initRadius = parseIntProperty(PROBE_RADIUS_PROP, 10000);
            try {
                Method seedSetter = targetClass.getDeclaredMethod("setWorldSeed", long.class);
                seedSetter.setAccessible(true);
                seedSetter.invoke(null, world.getSeed());
                GlobeMod.LOGGER.info("[latdev][probe] init: {}.setWorldSeed({})", className, world.getSeed());
            } catch (NoSuchMethodException ignored) {
                // target class has no such setter — fine, not every probe target needs world state.
            }
            try {
                Method radiusSetter = targetClass.getDeclaredMethod("setActiveRadiusBlocks", int.class);
                radiusSetter.setAccessible(true);
                radiusSetter.invoke(null, initRadius);
                GlobeMod.LOGGER.info("[latdev][probe] init: {}.setActiveRadiusBlocks({})", className, initRadius);
            } catch (NoSuchMethodException ignored) {
                // ditto.
            }

            Map<String, List<Object>> grid = parseProbeGrid(System.getProperty(PROBE_GRID_PROP, ""), names, paramClasses);
            if (grid == null) {
                return; // parseProbeGrid already logged the specific failure
            }

            long totalCombos = 1;
            for (String n : names) {
                totalCombos *= grid.get(n).size();
            }
            int maxCombos = parseIntProperty(PROBE_MAX_COMBOS_PROP, PROBE_DEFAULT_MAX_COMBOS);
            if (totalCombos > maxCombos) {
                GlobeMod.LOGGER.error("[latdev][probe] grid has {} combinations, exceeds {} (set {} to raise the cap)",
                        totalCombos, maxCombos, PROBE_MAX_COMBOS_PROP);
                return;
            }

            String outProp = System.getProperty(PROBE_OUT_PROP, "").trim();
            outPath = outProp.isEmpty()
                    ? server.getServerDirectory().resolve("latdev").resolve("probe-report.csv")
                    : Path.of(outProp);
            Files.createDirectories(outPath.getParent());

            GlobeMod.LOGGER.info("[latdev][probe] sweeping {}#{} over {} combination(s), writing {}",
                    className, methodName, totalCombos, outPath);

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", names)).append(",result\n");
            int[] indices = new int[names.length];
            Object[] args = new Object[names.length];
            boolean done = totalCombos == 0;
            while (!done) {
                for (int i = 0; i < names.length; i++) {
                    args[i] = grid.get(names[i]).get(indices[i]);
                }
                Object result;
                try {
                    result = method.invoke(null, args);
                } catch (Exception invokeEx) {
                    result = "ERROR:" + invokeEx.getCause();
                }
                for (Object a : args) {
                    csv.append(a).append(',');
                }
                csv.append(result).append('\n');
                rowCount++;

                int dim = names.length - 1;
                while (dim >= 0) {
                    indices[dim]++;
                    if (indices[dim] < grid.get(names[dim]).size()) {
                        break;
                    }
                    indices[dim] = 0;
                    dim--;
                }
                if (dim < 0) {
                    done = true;
                }
            }
            Files.write(outPath, csv.toString().getBytes(StandardCharsets.UTF_8));
            GlobeMod.LOGGER.info("[latdev][probe] wrote {} row(s) to {}", rowCount, outPath);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][probe] failed", t);
        } finally {
            server.halt(false);
        }
    }

    private static Class<?> probeTypeClass(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "boolean" -> boolean.class;
            default -> null;
        };
    }

    private static String[] splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private static int parseIntProperty(String key, int fallback) {
        String raw = System.getProperty(key, "").trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Parses {@code latdev.probe.grid} into {@code name -> boxed values matching that param's declared type}. */
    private static Map<String, List<Object>> parseProbeGrid(String raw, String[] names, Class<?>[] paramClasses) {
        Map<String, String> specByName = new HashMap<>();
        for (String entry : raw.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                GlobeMod.LOGGER.error("[latdev][probe] bad grid entry '{}' (expected name=spec)", entry);
                return null;
            }
            specByName.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        Map<String, List<Object>> grid = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String spec = specByName.get(name);
            if (spec == null) {
                GlobeMod.LOGGER.error("[latdev][probe] grid spec missing an entry for param '{}'", name);
                return null;
            }
            List<Double> raws = new ArrayList<>();
            if (spec.contains("..")) {
                int dotdot = spec.indexOf("..");
                double start = Double.parseDouble(spec.substring(0, dotdot));
                String rest = spec.substring(dotdot + 2);
                double step = 1.0;
                double end;
                int colon = rest.indexOf(':');
                if (colon >= 0) {
                    end = Double.parseDouble(rest.substring(0, colon));
                    step = Double.parseDouble(rest.substring(colon + 1));
                } else {
                    end = Double.parseDouble(rest);
                }
                if (step <= 0) {
                    GlobeMod.LOGGER.error("[latdev][probe] grid range for '{}' has non-positive step", name);
                    return null;
                }
                for (double v = start; v <= end + 1e-9; v += step) {
                    raws.add(v);
                }
            } else if (spec.contains(",")) {
                for (String tok : spec.split(",")) {
                    raws.add(Double.parseDouble(tok.trim()));
                }
            } else if (paramClasses[i] == boolean.class) {
                grid.put(name, List.of((Object) Boolean.parseBoolean(spec)));
                continue;
            } else {
                raws.add(Double.parseDouble(spec));
            }
            List<Object> boxed = new ArrayList<>(raws.size());
            for (double v : raws) {
                boxed.add(probeBox(paramClasses[i], v));
            }
            grid.put(name, boxed);
        }
        return grid;
    }

    private static Object probeBox(Class<?> paramClass, double v) {
        if (paramClass == int.class) {
            return (int) Math.round(v);
        }
        if (paramClass == long.class) {
            return Math.round(v);
        }
        if (paramClass == float.class) {
            return (float) v;
        }
        if (paramClass == boolean.class) {
            return v != 0.0;
        }
        return v; // double
    }

    private static void runAlpineAuditAndStop(MinecraftServer server) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][alpine-audit] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        long worldSeed = world.getSeed();
        try {
            int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
            int radius = activeRadius > 0 ? activeRadius : 10000;
            LatitudeBiomes.setWorldSeed(worldSeed);
            LatitudeBiomes.setActiveRadiusBlocks(radius);

            Path outputDir = server.getServerDirectory().toAbsolutePath().normalize()
                    .resolve("latdev");
            Files.createDirectories(outputDir);
            Path reportFile = outputDir.resolve("alpine-snow-audit.txt");

            int minY = 150;
            int maxY = 200;
            int xStart = 0;
            int xStop = 3000;
            int xStep = 30;
            int floorY = LatitudeBiomes.ALPINE_ROCK_Y; // 168

            List<String> lines = new ArrayList<>();
            lines.add("# Latitude alpine snow-line audit");
            lines.add("# Evaluates compiled LatitudeBiomes.alpineSurfaceKind(x,y,z,radius) (0=unchanged,1=stone,2=snow)");
            lines.add("seed=" + worldSeed);
            lines.add("radius=" + radius + " (active=" + activeRadius + ")");
            lines.add("alpine_rock_floor_y=" + floorY);
            lines.add("x_sweep=" + xStart + ".." + xStop + " step " + xStep
                    + " (" + (((xStop - xStart) / xStep) + 1) + " samples per Y)");
            lines.add("y_sweep=" + minY + ".." + maxY);
            lines.add("");

            String header = String.format(Locale.ROOT,
                    "%-12s %8s %10s | %s | below168",
                    "band", "repZ", "onsetY50", reportYHeader());
            lines.add(header);
            lines.add("-".repeat(header.length()));

            List<String> assertionResults = new ArrayList<>();
            boolean allPass = true;

            // per-band collected onset / below-168 facts for assertions
            for (Object[] bandSpec : ALPINE_AUDIT_BANDS) {
                String band = (String) bandSpec[0];
                int midLatDeg = (Integer) bandSpec[1];
                int repZ = (int) Math.round(midLatDeg / 90.0 * radius);

                // snow% at each Y in the full sweep
                int[] snowPctByY = new int[maxY - minY + 1];
                int onsetY50 = -1;
                boolean below168AnySnow = false;
                for (int y = minY; y <= maxY; y++) {
                    int samples = 0;
                    int snow = 0;
                    for (int x = xStart; x <= xStop; x += xStep) {
                        int kind = LatitudeBiomes.alpineSurfaceKind(x, y, repZ, radius);
                        samples++;
                        if (kind == ALPINE_AUDIT_KIND_SNOW) {
                            snow++;
                        }
                    }
                    int pct = samples == 0 ? 0 : (int) Math.round(100.0 * snow / samples);
                    snowPctByY[y - minY] = pct;
                    if (y < floorY && snow > 0) {
                        below168AnySnow = true;
                    }
                    if (onsetY50 < 0 && pct >= 50) {
                        onsetY50 = y;
                    }
                }

                StringBuilder reportYCols = new StringBuilder();
                for (int i = 0; i < ALPINE_AUDIT_REPORT_YS.length; i++) {
                    int ry = ALPINE_AUDIT_REPORT_YS[i];
                    int pct = (ry >= minY && ry <= maxY) ? snowPctByY[ry - minY] : -1;
                    reportYCols.append(String.format(Locale.ROOT, "%4d", pct));
                }

                lines.add(String.format(Locale.ROOT,
                        "%-12s %8d %10s | %s | %s",
                        band,
                        repZ,
                        onsetY50 < 0 ? "none" : Integer.toString(onsetY50),
                        reportYCols.toString(),
                        below168AnySnow ? "SNOW(!)" : "clean"));

                // ---- assertions ----
                int pctAt166 = snowPctByY[166 - minY];
                int pctAt170 = snowPctByY[170 - minY];
                int pctAt174 = snowPctByY[174 - minY];
                int pctAt178 = snowPctByY[178 - minY];
                int pctAt182 = snowPctByY[182 - minY];
                int pctAt186 = snowPctByY[186 - minY];
                int pctAt200 = snowPctByY[200 - minY];

                switch (band) {
                    case "TROPICAL" -> {
                        boolean ok = true;
                        for (int y = minY; y <= maxY; y++) {
                            if (snowPctByY[y - minY] != 0) {
                                ok = false;
                                break;
                            }
                        }
                        allPass &= ok;
                        assertionResults.add(assertLine("TROPICAL: snow% == 0 at ALL Y (warm-creep safety)",
                                ok, "maxSnow%=" + maxPct(snowPctByY) + " pctAtY200=" + pctAt200));
                    }
                    case "SUBTROPICAL" -> {
                        boolean ok = pctAt178 <= 5 && (pctAt182 >= 50 || pctAt186 >= 50);
                        allPass &= ok;
                        assertionResults.add(assertLine(
                                "SUBTROPICAL: ~0 below Y=178, majority by Y182-186",
                                ok, "pct@178=" + pctAt178 + " pct@182=" + pctAt182 + " pct@186=" + pctAt186));
                    }
                    case "TEMPERATE" -> {
                        boolean ok = pctAt170 <= 5 && (pctAt174 >= 50 || pctAt178 >= 50);
                        allPass &= ok;
                        assertionResults.add(assertLine(
                                "TEMPERATE (THE FIX): ~0 below Y=170, majority by Y174-178",
                                ok, "pct@170=" + pctAt170 + " pct@174=" + pctAt174 + " pct@178=" + pctAt178));
                    }
                    case "SUBPOLAR" -> {
                        boolean ok = pctAt170 >= 50 || pctAt174 >= 50;
                        allPass &= ok;
                        assertionResults.add(assertLine(
                                "SUBPOLAR: majority snow by Y170-174",
                                ok, "pct@170=" + pctAt170 + " pct@174=" + pctAt174));
                    }
                    case "POLAR" -> {
                        boolean okMajority = (snowPctByY[168 - minY] >= 50) || pctAt170 >= 50 || (snowPctByY[172 - minY] >= 50);
                        allPass &= okMajority;
                        assertionResults.add(assertLine(
                                "POLAR: majority snow by Y168-172",
                                okMajority,
                                "pct@168=" + snowPctByY[168 - minY] + " pct@170=" + pctAt170
                                        + " pct@172=" + snowPctByY[172 - minY]));
                    }
                    default -> { /* unreachable */ }
                }

                // floor assertion (per band): snow% == 0 for every Y < 168
                boolean floorOk = !below168AnySnow;
                allPass &= floorOk;
                assertionResults.add(assertLine(
                        band + ": snow% == 0 for every Y < 168 (ALPINE_ROCK_Y floor)",
                        floorOk, "pct@166=" + pctAt166));
            }

            lines.add("");
            lines.add("# Assertions");
            lines.addAll(assertionResults);
            lines.add("");
            lines.add("verdict=" + (allPass ? "PASS" : "FAIL"));

            // ---- SECONDARY: real generated-surface spot check (cheap, best-effort) ----
            List<String> surfaceLines = runAlpineRealSurfaceCheck(world, radius);
            lines.add("");
            lines.add("# Secondary: real generated-surface spot check");
            lines.addAll(surfaceLines);

            String body = String.join(System.lineSeparator(), lines) + System.lineSeparator();
            Files.writeString(reportFile, body, StandardCharsets.UTF_8);

            for (String l : lines) {
                GlobeMod.LOGGER.info("[latdev][alpine-audit] {}", l);
            }
            GlobeMod.LOGGER.info("[latdev][alpine-audit] report written: {} verdict={}",
                    reportFile, allPass ? "PASS" : "FAIL");
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][alpine-audit] audit failed", t);
        } finally {
            LatitudeBiomes.setWorldSeed(worldSeed);
            GlobeMod.LOGGER.info("[latdev][alpine-audit] stopping server");
            server.halt(false);
        }
    }

    private static String reportYHeader() {
        StringBuilder sb = new StringBuilder();
        for (int ry : ALPINE_AUDIT_REPORT_YS) {
            sb.append(String.format(Locale.ROOT, "%4d", ry));
        }
        return "snow% @Y[" + sb.toString().trim() + "]";
    }

    private static int maxPct(int[] pcts) {
        int m = 0;
        for (int p : pcts) {
            if (p > m) {
                m = p;
            }
        }
        return m;
    }

    private static String assertLine(String label, boolean pass, String detail) {
        return String.format(Locale.ROOT, "[%s] %s (%s)", pass ? "PASS" : "FAIL", label, detail);
    }

    /**
     * Best-effort confirmation that snow ACTUALLY paints on real generated terrain at a temperate
     * column and never at a tropical column. Forces a small number of chunks and reads the surface
     * heightmap + top block. Wrapped so a generation hiccup degrades to "deferred" rather than
     * hanging or aborting the primary result.
     */
    private static List<String> runAlpineRealSurfaceCheck(ServerLevel world, int radius) {
        List<String> out = new ArrayList<>();
        try {
            // Scan a band of real columns at the temperate and tropical representative latitudes,
            // find the HIGHEST generated surface in each, and report what alpineSurfaceKind() would
            // paint there. The temperate scan looks for genuine alpine terrain (>= ALPINE_ROCK_Y) so
            // it can confirm snow actually lands on a peak; the tropical scan confirms snow never does.
            // Capped to a handful of chunks so it never risks hanging.
            int temperateZ = (int) Math.round(42.0 / 90.0 * radius);
            int tropicalZ = (int) Math.round(10.0 / 90.0 * radius);
            out.add(scanRealBand(world, "TEMPERATE", temperateZ, radius));
            out.add(scanRealBand(world, "TROPICAL", tropicalZ, radius));
            out.add("note=spot check is a sanity cross-reference; the per-band table above is the authoritative proof");
        } catch (Throwable t) {
            out.clear();
            out.add("status=deferred (real-surface sampling raised " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()).replace('\n', ' ') + ")");
        }
        return out;
    }

    /**
     * Forces a small band of chunks at the given representative Z, finds the highest generated
     * surface column, and reports what alpineSurfaceKind() returns at that real surface Y. Bounded
     * to {@code chunkSpan} chunks so it cannot hang.
     */
    private static String scanRealBand(ServerLevel world, String label, int repZ, int radius) {
        int chunkSpan = 24; // up to 24 chunks (~384 blocks) of X scanned at this latitude
        int bestSurfaceY = Integer.MIN_VALUE;
        int bestX = 0;
        int bestZ = repZ;
        int firstSnowSurfaceY = Integer.MIN_VALUE;
        int firstSnowX = 0;
        boolean anyAlpine = false;
        int columnsScanned = 0;

        int baseChunkX = 0;
        int chunkZ = Math.floorDiv(repZ, 16);
        for (int cx = 0; cx < chunkSpan; cx++) {
            int chunkX = baseChunkX + cx;
            ChunkAccess chunk;
            try {
                chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.SURFACE, true);
            } catch (Throwable t) {
                continue;
            }
            if (chunk == null) {
                continue;
            }
            for (int lx = 0; lx < 16; lx += 4) {
                int blockX = (chunkX << 4) + lx;
                int localZ = repZ - (chunkZ << 4);
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, localZ);
                columnsScanned++;
                if (surfaceY > bestSurfaceY) {
                    bestSurfaceY = surfaceY;
                    bestX = blockX;
                    bestZ = repZ;
                }
                int kind = LatitudeBiomes.alpineSurfaceKind(blockX, surfaceY, repZ, radius);
                if (surfaceY >= LatitudeBiomes.ALPINE_ROCK_Y) {
                    anyAlpine = true;
                }
                if (kind == ALPINE_AUDIT_KIND_SNOW && firstSnowSurfaceY == Integer.MIN_VALUE) {
                    firstSnowSurfaceY = surfaceY;
                    firstSnowX = blockX;
                }
            }
        }

        double absLat = Math.abs((double) repZ) * 90.0 / Math.max(1, radius);
        String topId = "n/a";
        int bestKind = 0;
        if (bestSurfaceY != Integer.MIN_VALUE) {
            bestKind = LatitudeBiomes.alpineSurfaceKind(bestX, bestSurfaceY, repZ, radius);
            try {
                ChunkAccess bc = world.getChunk(Math.floorDiv(bestX, 16), chunkZ, ChunkStatus.SURFACE, true);
                if (bc != null) {
                    BlockState top = bc.getBlockState(new BlockPos(bestX, bestSurfaceY, repZ));
                    Identifier id = world.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(top.getBlock());
                    if (id != null) {
                        topId = id.toString();
                    }
                }
            } catch (Throwable ignored) {
                // best-effort block id only
            }
        }

        return String.format(Locale.ROOT,
                "%s: repZ=%d absLat=%.1f columns=%d highestSurfaceY=%d topBlock=%s alpineKind@peak=%d(%s) "
                        + "anyAlpineTerrain=%s firstSnowPaintedAt=%s",
                label, repZ, absLat, columnsScanned,
                bestSurfaceY == Integer.MIN_VALUE ? -1 : bestSurfaceY,
                topId, bestKind, kindName(bestKind),
                anyAlpine,
                firstSnowSurfaceY == Integer.MIN_VALUE
                        ? "none"
                        : ("x=" + firstSnowX + ",surfaceY=" + firstSnowSurfaceY));
    }

    private static String kindName(int kind) {
        return switch (kind) {
            case 1 -> "stone";
            case 2 -> "snow";
            default -> "unchanged";
        };
    }

    private static void runLocateBoundaryAndStop(MinecraftServer server, LocateBoundaryConfig config) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][locate-boundary] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        long worldSeed = world.getSeed();
        Path outputDir = config.outDir != null
                ? config.outDir
                : server.getServerDirectory().toAbsolutePath().normalize().resolve("locate-boundary");
        Path proofFile = outputDir.resolve("proof.txt");
        try {
            Files.createDirectories(outputDir);
            if (!config.valid) {
                writeLocateBoundaryProof(proofFile, List.of(
                        "status=fail",
                        "error=" + config.validationError,
                        "verdict=fail"));
                GlobeMod.LOGGER.error("[latdev][locate-boundary] invalid config: {}", config.validationError);
                return;
            }

            int y = Mth.clamp(config.y, world.getMinY(), world.getMaxY() - 1);
            int radius = config.radiusBlocks != null
                    ? Math.max(1, config.radiusBlocks)
                    : radiusFromSizeOrWorld(config.sizePreset, world);
            long effectiveSeed = config.seedOverride != null ? config.seedOverride : worldSeed;

            LatitudeBiomes.setWorldSeed(effectiveSeed);
            LatitudeBiomes.setActiveRadiusBlocks(radius);

            ChunkGenerator generator = world.getChunkSource().getGenerator();
            if (!(generator instanceof NoiseBasedChunkGenerator noiseGen)) {
                writeLocateBoundaryProof(proofFile, List.of(
                        "status=fail",
                        "seed=" + effectiveSeed,
                        "world_seed=" + worldSeed,
                        "radius=" + radius,
                        "x_y_z=" + config.x + "," + y + "," + config.z,
                        "error=generator_not_noise_based",
                        "generator_class=" + generator.getClass().getName(),
                        "verdict=fail"));
                GlobeMod.LOGGER.error("[latdev][locate-boundary] generator is not NoiseBasedChunkGenerator: {}",
                        generator.getClass().getName());
                return;
            }

            BiomeSource biomeSource = generator.getBiomeSource();
            BiomeSource baseSource = biomeSource instanceof LatitudeBiomeSource latitudeSource
                    ? latitudeSource.original()
                    : biomeSource;
            boolean settingsAccessorReady = ((NoiseChunkGeneratorAccessor) (Object) noiseGen).globe$getSettings() != null;
            boolean stableRegular = noiseGen.stable(GLOBE_REGULAR_SETTINGS_KEY);
            boolean shouldApplyLatitudeWorldgen = GlobeMod.shouldApplyLatitudeWorldgen(noiseGen);
            Registry<Biome> biomes = world.registryAccess().lookupOrThrow(Registries.BIOME);
            LatitudeBiomes.rememberSourcePolicyBiomeRegistry(biomes);
            RandomState noiseConfig = RandomState.create(
                    noiseGen.generatorSettings().value(),
                    world.registryAccess().lookupOrThrow(Registries.NOISE),
                    effectiveSeed);
            Climate.Sampler sampler = noiseConfig.sampler();

            int noiseX = Math.floorDiv(config.x, 4);
            int noiseY = Math.floorDiv(y, 4);
            int noiseZ = Math.floorDiv(config.z, 4);
            int sourceBlockX = noiseX << 2;
            int sourceBlockY = noiseY << 2;
            int sourceBlockZ = noiseZ << 2;
            int populateBlockX = sourceBlockX + 2;
            int populateBlockY = sourceBlockY + 2;
            int populateBlockZ = sourceBlockZ + 2;

            Holder<Biome> rawSource = baseSource.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
            Holder<Biome> base = baseSource.getNoiseBiome(noiseX, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, noiseZ, sampler);
            // Phase 5 Slice B-8: expand the source pool AT THE SOURCE so the atlas pool == live registry
            // reach (globe:polar_barrens included flag-on) -- the wrapped source, the pick candidate pool,
            // and possibleBiomes all agree. Byte-identical flag-off (expandSourceCandidatePool returns the
            // same possibleBiomes reference).
            Collection<Holder<Biome>> sourcePool = LatitudeBiomes.expandSourceCandidatePool(baseSource.possibleBiomes());
            Holder<Biome> wrappedSource = new LatitudeBiomeSource(baseSource, sourcePool, radius)
                    .getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
            Holder<Biome> sourceEquivalent = LatitudeBiomes.pick(
                    sourcePool,
                    base,
                    sourceBlockX,
                    sourceBlockZ,
                    sourceBlockY,
                    radius,
                    sampler,
                    "SOURCE",
                    null,
                    null,
                    null);

            int chunkX = Math.floorDiv(config.x, 16);
            int chunkZ = Math.floorDiv(config.z, 16);
            ChunkAccess chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            LevelHeightAccessor heightView = chunk != null ? chunk : world;
            Holder<Biome> populateEquivalent = LatitudeBiomes.pick(
                    biomes,
                    base,
                    populateBlockX,
                    populateBlockZ,
                    populateBlockY,
                    radius,
                    sampler,
                    "MIXIN",
                    noiseGen,
                    noiseConfig,
                    heightView);
            Holder<Biome> liveBiome = world.getBiome(new BlockPos(config.x, y, config.z));

            String rawSourceId = biomeId(biomes, rawSource);
            String baseId = biomeId(biomes, base);
            String wrappedSourceId = biomeId(biomes, wrappedSource);
            String sourceEquivalentId = biomeId(biomes, sourceEquivalent);
            String populateEquivalentId = biomeId(biomes, populateEquivalent);
            String liveBiomeId = biomeId(biomes, liveBiome);
            String savedProfileBiome = config.savedProfileBiome == null || config.savedProfileBiome.isBlank()
                    ? "unknown"
                    : config.savedProfileBiome;
            boolean liveProfileComparable = biomeSource instanceof LatitudeBiomeSource;
            boolean boundaryPassed = savedProfileBiome.equals(wrappedSourceId)
                    && savedProfileBiome.equals(sourceEquivalentId)
                    && savedProfileBiome.equals(populateEquivalentId)
                    && (!liveProfileComparable || savedProfileBiome.equals(liveBiomeId));
            String mismatch = locateBoundaryMismatch(
                    savedProfileBiome,
                    wrappedSourceId,
                    sourceEquivalentId,
                    populateEquivalentId,
                    liveBiomeId,
                    liveProfileComparable);

            LocateSearchProof locateSearchProof = runLocateSearchProof(
                    config,
                    world,
                    biomes,
                    baseSource,
                    radius,
                    sampler,
                    noiseGen,
                    noiseConfig);
            boolean passed = boundaryPassed && locateSearchProof.passed();

            List<String> proofLines = new ArrayList<>(List.of(
                    "status=ok",
                    "seed=" + effectiveSeed,
                    "world_seed=" + worldSeed,
                    "radius=" + radius,
                    "generator_class=" + generator.getClass().getName(),
                    "biome_source_class=" + biomeSource.getClass().getName(),
                    "base_source_class=" + baseSource.getClass().getName(),
                    "settings_accessor_ready=" + settingsAccessorReady,
                    "stable_globe_overworld_regular=" + stableRegular,
                    "active_radius=" + LatitudeBiomes.getActiveRadiusBlocks(),
                    "should_apply_latitude_worldgen=" + shouldApplyLatitudeWorldgen,
                    "x_y_z=" + config.x + "," + y + "," + config.z,
                    "noise_x_y_z=" + noiseX + "," + noiseY + "," + noiseZ,
                    "source_block_x_y_z=" + sourceBlockX + "," + sourceBlockY + "," + sourceBlockZ,
                    "populate_block_x_y_z=" + populateBlockX + "," + populateBlockY + "," + populateBlockZ,
                    "raw_source_biome=" + rawSourceId,
                    "source_surface_base_biome=" + baseId,
                    "wrapped_source_biome=" + wrappedSourceId,
                    "source_equivalent_biome=" + sourceEquivalentId,
                    "populate_equivalent_biome=" + populateEquivalentId,
                    "live_biome=" + liveBiomeId,
                    "live_biome_mode=" + (liveProfileComparable ? "latitude_biome_source" : "headless_unwrapped"),
                    "saved_profile_biome=" + savedProfileBiome,
                    "mismatch=" + mismatch));
            proofLines.addAll(locateSearchProof.lines());
            proofLines.add("verdict=" + (passed ? "pass" : "fail"));
            writeLocateBoundaryProof(proofFile, proofLines);
            GlobeMod.LOGGER.info("[latdev][locate-boundary] proof={} verdict={} mismatch={}",
                    proofFile, passed ? "pass" : "fail", mismatch);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][locate-boundary] proof failed", t);
            try {
                Files.createDirectories(outputDir);
                writeLocateBoundaryProof(proofFile, List.of(
                        "status=fail",
                        "error=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()).replace('\n', ' '),
                        "verdict=fail"));
            } catch (IOException io) {
                GlobeMod.LOGGER.error("[latdev][locate-boundary] failed to write failure proof", io);
            }
        } finally {
            LatitudeBiomes.setWorldSeed(worldSeed);
            GlobeMod.LOGGER.info("[latdev][locate-boundary] stopping server");
            server.halt(false);
        }
    }

    private static LocateSearchProof runLocateSearchProof(LocateBoundaryConfig config,
                                                          ServerLevel world,
                                                          Registry<Biome> biomes,
                                                          BiomeSource baseSource,
                                                          int radius,
                                                          Climate.Sampler sampler,
                                                          NoiseBasedChunkGenerator noiseGen,
                                                          RandomState noiseConfig) {
        if (config.targetBiome == null || config.targetBiome.isBlank()) {
            return new LocateSearchProof(true, List.of());
        }

        List<String> lines = new ArrayList<>();
        String targetBiome = config.targetBiome.trim().toLowerCase(Locale.ROOT);
        lines.add("locate_target_biome=" + targetBiome);
        lines.add("locate_start_x_y_z=" + config.startX + "," + config.startY + "," + config.startZ);
        lines.add("locate_search_radius=" + config.searchRadius);
        lines.add("locate_horizontal_step=" + config.horizontalStep);
        lines.add("locate_vertical_step=" + config.verticalStep);

        Identifier targetId = Identifier.tryParse(targetBiome);
        if (targetId == null) {
            lines.add("locate_result_error=invalid_target_identifier");
            lines.add("locate_result_final_matches_target=false");
            return new LocateSearchProof(false, lines);
        }
        Holder<Biome> targetHolder = biomes.get(targetId).orElse(null);
        if (targetHolder == null) {
            lines.add("locate_result_error=target_not_registered");
            lines.add("locate_result_final_matches_target=false");
            return new LocateSearchProof(false, lines);
        }

        int startY = Mth.clamp(config.startY, world.getMinY(), world.getMaxY() - 1);
        Pair<BlockPos, Holder<Biome>> result = world.findClosestBiome3d(
                candidate -> candidate.is(targetHolder),
                new BlockPos(config.startX, startY, config.startZ),
                Math.max(1, config.searchRadius),
                Math.max(1, config.horizontalStep),
                Math.max(1, config.verticalStep));
        if (result == null) {
            lines.add("locate_result_x_y_z=null");
            lines.add("locate_result_holder=null");
            lines.add("locate_result_final_matches_target=false");
            return new LocateSearchProof(false, lines);
        }

        BlockPos pos = result.getFirst();
        Holder<Biome> resultHolder = result.getSecond();
        int noiseX = Math.floorDiv(pos.getX(), 4);
        int noiseY = Math.floorDiv(pos.getY(), 4);
        int noiseZ = Math.floorDiv(pos.getZ(), 4);
        int sourceBlockX = noiseX << 2;
        int sourceBlockY = noiseY << 2;
        int sourceBlockZ = noiseZ << 2;
        int populateBlockX = sourceBlockX + 2;
        int populateBlockY = sourceBlockY + 2;
        int populateBlockZ = sourceBlockZ + 2;

        Holder<Biome> rawSource = baseSource.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
        Holder<Biome> base = baseSource.getNoiseBiome(noiseX, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, noiseZ, sampler);
        // Phase 5 Slice B-8: remember the registry + expand the source pool here too (locate-proof path)
        // so the wrapped source can emit globe:polar_barrens flag-on. Byte-identical flag-off.
        LatitudeBiomes.rememberSourcePolicyBiomeRegistry(biomes);
        Collection<Holder<Biome>> sourcePool = LatitudeBiomes.expandSourceCandidatePool(baseSource.possibleBiomes());
        Holder<Biome> wrappedSource = new LatitudeBiomeSource(baseSource, sourcePool, radius)
                .getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        ChunkAccess chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        LevelHeightAccessor heightView = chunk != null ? chunk : world;
        Holder<Biome> populateEquivalent = LatitudeBiomes.pick(
                biomes,
                base,
                populateBlockX,
                populateBlockZ,
                populateBlockY,
                radius,
                sampler,
                "LOCATE_PROOF",
                noiseGen,
                noiseConfig,
                heightView);
        Holder<Biome> liveBiome = world.getBiome(pos);

        String resultHolderId = biomeId(biomes, resultHolder);
        String rawSourceId = biomeId(biomes, rawSource);
        String wrappedSourceId = biomeId(biomes, wrappedSource);
        String populateEquivalentId = biomeId(biomes, populateEquivalent);
        String liveBiomeId = biomeId(biomes, liveBiome);
        boolean finalMatchesTarget = targetBiome.equals(liveBiomeId) && targetBiome.equals(populateEquivalentId);

        lines.add("locate_result_x_y_z=" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        lines.add("locate_result_holder=" + resultHolderId);
        lines.add("locate_result_noise_x_y_z=" + noiseX + "," + noiseY + "," + noiseZ);
        lines.add("locate_result_source_block_x_y_z=" + sourceBlockX + "," + sourceBlockY + "," + sourceBlockZ);
        lines.add("locate_result_populate_block_x_y_z=" + populateBlockX + "," + populateBlockY + "," + populateBlockZ);
        lines.add("locate_result_raw_source_biome=" + rawSourceId);
        lines.add("locate_result_wrapped_source_biome=" + wrappedSourceId);
        lines.add("locate_result_populate_equivalent_biome=" + populateEquivalentId);
        lines.add("locate_result_live_biome=" + liveBiomeId);
        lines.add("locate_result_final_matches_target=" + finalMatchesTarget);
        return new LocateSearchProof(finalMatchesTarget, lines);
    }

    private static String locateBoundaryMismatch(String expected,
                                                 String wrappedSource,
                                                 String sourceEquivalent,
                                                 String populateEquivalent,
                                                 String liveBiome,
                                                 boolean liveProfileComparable) {
        List<String> mismatches = new ArrayList<>();
        if (!expected.equals(wrappedSource)) {
            mismatches.add("wrapped_source=" + wrappedSource);
        }
        if (!expected.equals(sourceEquivalent)) {
            mismatches.add("source_equivalent=" + sourceEquivalent);
        }
        if (!expected.equals(populateEquivalent)) {
            mismatches.add("populate_equivalent=" + populateEquivalent);
        }
        if (liveProfileComparable && !expected.equals(liveBiome)) {
            mismatches.add("live=" + liveBiome);
        }
        return mismatches.isEmpty() ? "none" : String.join("|", mismatches);
    }

    private static void writeLocateBoundaryProof(Path proofFile, List<String> lines) throws IOException {
        Files.createDirectories(proofFile.getParent());
        Files.writeString(
                proofFile,
                String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static String biomeId(Registry<Biome> biomeRegistry, Holder<Biome> biome) {
        if (biome == null) {
            return "null";
        }
        Identifier id = biomeRegistry.getKey(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
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
                case "itty", "ittybitty", "itty_bitty", "xsmall" -> PREVIEW_RADIUS_ITTY;
                case "tiny" -> PREVIEW_RADIUS_TINY;
                case "small", "medium" -> PREVIEW_RADIUS_SMALL;
                case "regular" -> PREVIEW_RADIUS_REGULAR;
                case "large" -> PREVIEW_RADIUS_LARGE;
                case "ginormous", "massive" -> PREVIEW_RADIUS_MASSIVE;
                default -> authoritativeRadius(world);
            };
        }
        return authoritativeRadius(world);
    }

    private static int resolvePreviewRadius(ServerLevel world, Config config) {
        Integer configuredRadius = config != null ? config.radiusBlocks() : null;
        if (configuredRadius != null) {
            int radius = Math.max(1, configuredRadius);
            if (!isCanonicalPreviewRadius(radius)) {
                GlobeMod.LOGGER.warn(
                        "[latdev][headless] configured preview radius {} is outside canonical Latitude preview radii. "
                                + "using default radius {} to avoid invalid preview allocation.",
                        radius,
                        PREVIEW_RADIUS_REGULAR);
                return PREVIEW_RADIUS_REGULAR;
            }
            return radius;
        }

        int resolvedRadius = radiusFromSizeOrWorld(config != null ? config.sizePreset() : null, world);
        if (!isCanonicalPreviewRadius(resolvedRadius)) {
            if (resolvedRadius > PREVIEW_RADIUS_MAX) {
                GlobeMod.LOGGER.warn(
                        "[latdev][headless] resolved preview radius {} is outside canonical Latitude bounds. "
                                + "Using default radius {} instead of vanilla world-border radius.",
                        resolvedRadius,
                        PREVIEW_RADIUS_REGULAR);
            }
            return PREVIEW_RADIUS_REGULAR;
        }
        return resolvedRadius;
    }

    private static boolean isCanonicalPreviewRadius(int radius) {
        return radius == PREVIEW_RADIUS_ITTY
                || radius == PREVIEW_RADIUS_TINY
                || radius == PREVIEW_RADIUS_SMALL
                || radius == PREVIEW_RADIUS_REGULAR
                || radius == PREVIEW_RADIUS_LARGE
                || radius == PREVIEW_RADIUS_MASSIVE;
    }

    private static final class ExportJob {
        private final ServerLevel world;
        private final Config config;
        private final Path outputDir;
        private final long effectiveSeed;
        private final long originalSeed;
        private final int radius;
        private final int y;
        private final String runLabel;
        private final Path serverDirectory;
        private int stepIndex;
        private int currentStep;
        private BiomePreviewExporter.HeightStepProcessor processor;
        private boolean done;
        private boolean closed;

        private ExportJob(ServerLevel world,
                          Config config,
                          Path outputDir,
                          long effectiveSeed,
                          long originalSeed,
                          int radius,
                          int y,
                          String runLabel,
                          Path serverDirectory) {
            this.world = world;
            this.config = config;
            this.outputDir = outputDir;
            this.effectiveSeed = effectiveSeed;
            this.originalSeed = originalSeed;
            this.radius = radius;
            this.y = y;
            this.runLabel = runLabel;
            this.serverDirectory = serverDirectory;
        }

        private void tick() throws IOException {
            if (done) {
                return;
            }

            if (processor == null) {
                if (stepIndex >= config.steps.size()) {
                    done = true;
                    return;
                }
                currentStep = config.steps.get(stepIndex);
                processor = BiomePreviewExporter.HeightStepProcessor.create(
                        world,
                        radius,
                        currentStep,
                        y,
                        serverDirectory,
                        effectiveSeed,
                        config.exportOptions,
                        runLabel);
                GlobeMod.LOGGER.info("[latdev][headless] processing export step={} radius={} y={} out={}",
                        currentStep,
                        radius,
                        y,
                        outputDir);
            }

            long budgetMs = Math.max(1L, Long.getLong("latitude.atlas.exportBudgetMs", 250L));
            BiomePreviewExporter.ExportResult result = processor.processBudget(budgetMs);
            if (result == null) {
                return;
            }

            BiomePreviewExporter.ExportResult finalized = finalizeOutput(result, effectiveSeed, outputDir, runLabel);
            GlobeMod.LOGGER.info("[latdev][headless] finished export step={} file={} sidecar={} image={}x{} samples={} durationMs={}",
                    currentStep,
                    finalized.pngPath(),
                    finalized.txtPath(),
                    finalized.width(),
                    finalized.height(),
                    finalized.totalSamples(),
                    finalized.durationMs());

            stepIndex++;
            processor = null;
            if (stepIndex >= config.steps.size()) {
                done = true;
            }
        }

        private boolean isDone() {
            return done;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (processor != null) {
                processor.close();
                processor = null;
            }
            LatitudeBiomes.setWorldSeed(originalSeed);
        }
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
        BiomePreviewExporter.ExportOptions baseOptions = BiomePreviewExporter.ExportOptions.from(
                bundle,
                parsedLayers.layers(),
                overlays,
                masks,
                parsedLayers.includeBiomeAudit());
        // --ruggedness true: append RUGGEDNESS to whatever layer set was chosen
        boolean addRuggedness = parseBoolean(kv.get("ruggedness"));
        if (addRuggedness && !baseOptions.layers().contains(BiomePreviewExporter.Layer.RUGGEDNESS)) {
            java.util.EnumSet<BiomePreviewExporter.Layer> ext = java.util.EnumSet.copyOf(baseOptions.layers());
            ext.add(BiomePreviewExporter.Layer.RUGGEDNESS);
            baseOptions = new BiomePreviewExporter.ExportOptions(
                ext, baseOptions.overlays(), baseOptions.maskLayers(), baseOptions.writeLegends(),
                    false, false, parsedLayers.includeBiomeAudit());
        }
        // Emit legend metadata whenever biome index output is requested so atlas runs
        // keep the policy/legend authority alongside the sampled layer payload.
        BiomePreviewExporter.ExportOptions exportOptions = new BiomePreviewExporter.ExportOptions(
                baseOptions.layers(),
                baseOptions.overlays(),
                baseOptions.maskLayers(),
                baseOptions.writeLegends() || emitBiomeIndex,
                emitBiomeIndex,
                emitHeight,
                parsedLayers.includeBiomeAudit());
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

    private static LocateBoundaryConfig parseLocateBoundaryConfig() {
        Map<String, String> kv = new HashMap<>();
        String prop = System.getProperty(LOCATE_BOUNDARY_PROP_KEY, "");
        if (prop.isBlank()) {
            return new LocateBoundaryConfig(false, false, null, null, null, 0, 0, 0,
                    null, 0, 0, 0, 6400, 32, 64, null, null, "");
        }

        parsePropertyOptions(prop, kv);
        boolean enabled = parseBoolean(kv.get("enabled")) || kv.containsKey("x") || kv.containsKey("y") || kv.containsKey("z");
        Integer x = parseInt(kv.get("x"));
        Integer y = parseInt(kv.get("y"));
        Integer z = parseInt(kv.get("z"));
        String validationError = "";
        if (enabled && (x == null || y == null || z == null)) {
            validationError = "locate-boundary requires x, y, and z";
        }

        Long seed = parseLong(kv.get("seed"));
        Integer radius = parseInt(kv.get("radius"));
        String size = kv.get("size");
        String saved = kv.get("saved");
        String target = kv.get("target");
        if (target == null || target.isBlank()) {
            target = kv.get("targetbiome");
        }
        Integer startX = parseInt(kv.get("startx"));
        Integer startY = parseInt(kv.get("starty"));
        Integer startZ = parseInt(kv.get("startz"));
        Integer searchRadius = parseInt(kv.get("searchradius"));
        Integer horizontalStep = parseInt(kv.get("horizontalstep"));
        Integer verticalStep = parseInt(kv.get("verticalstep"));
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;

        return new LocateBoundaryConfig(
                enabled,
                validationError.isBlank(),
                seed,
                radius,
                size,
                x != null ? x : 0,
                y != null ? y : 0,
                z != null ? z : 0,
                target,
                startX != null ? startX : (x != null ? x : 0),
                startY != null ? startY : (y != null ? y : 0),
                startZ != null ? startZ : (z != null ? z : 0),
                searchRadius != null ? searchRadius : 6400,
                horizontalStep != null ? horizontalStep : 32,
                verticalStep != null ? verticalStep : 64,
                saved,
                out,
                validationError);
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
            return new ParsedLayers(List.of(), List.of(), false);
        }

        LinkedHashSet<BiomePreviewExporter.Layer> layers = new LinkedHashSet<>();
        LinkedHashSet<String> masks = new LinkedHashSet<>();
        boolean includeBiomeAudit = false;
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
            if (normalized.equals("stats") || normalized.equals("audit")) {
                includeBiomeAudit = true;
                collectingMaskValues = false;
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
        return new ParsedLayers(new ArrayList<>(layers), new ArrayList<>(masks), includeBiomeAudit);
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
                                List<String> masks,
                                boolean includeBiomeAudit) {
    }

    private record GitStamp(String branch, String commit) {
    }

    private record LocateBoundaryConfig(boolean enabled,
                                        boolean valid,
                                        Long seedOverride,
                                        Integer radiusBlocks,
                                        String sizePreset,
                                        int x,
                                        int y,
                                        int z,
                                        String targetBiome,
                                        int startX,
                                        int startY,
                                        int startZ,
                                        int searchRadius,
                                        int horizontalStep,
                                        int verticalStep,
                                        String savedProfileBiome,
                                        Path outDir,
                                        String validationError) {
    }

    private record LocateSearchProof(boolean passed, List<String> lines) {
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
