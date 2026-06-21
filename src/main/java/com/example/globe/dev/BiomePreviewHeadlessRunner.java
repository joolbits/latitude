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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import java.io.IOException;
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
    private static final String EMIT_HEIGHT_PROP_KEY = "latitude.emitHeight";
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
            Collection<Holder<Biome>> sourcePool = baseSource.possibleBiomes();
            Holder<Biome> wrappedSource = new LatitudeBiomeSource(baseSource, sourcePool, radius)
                    .getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
            Holder<Biome> sourceEquivalent = LatitudeBiomes.pick(
                    LatitudeBiomes.expandSourceCandidatePool(sourcePool),
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
        Collection<Holder<Biome>> sourcePool = baseSource.possibleBiomes();
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
