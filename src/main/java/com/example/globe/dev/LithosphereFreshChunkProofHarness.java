package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LithosphereFreshChunkProofHarness {
    private static final String MAP_CANDIDATES_PROP_KEY = "latdev.lithoFreshChunkProofMapCandidates";
    private static final String UNSAFE_SURFACE_SCOUT_PROP_KEY = "latdev.lithoFreshChunkProofUnsafeSurfaceScout";
    private static final Pattern MAP_CANDIDATE_OBJECT_PATTERN = Pattern.compile("\\{[^{}]*}");
    private static final Pattern MAP_CANDIDATE_X_PATTERN = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern MAP_CANDIDATE_Z_PATTERN = Pattern.compile("\"z\"\\s*:\\s*(-?\\d+)");
    private static final int CONTROL_SEARCH_MAX_CANDIDATES = 1024;
    private static final int CONTROL_SEARCH_TARGET_VALID = 2;
    private static final int CONTROL_SEARCH_MAX_FULL_VERIFICATIONS = 12;
    private static final int CONTROL_DISCOVERY_REJECTION_ROW_LIMIT = 80;
    private static final int CONTROL_SCOUT_MIN_SCORE = 3;
    private static final int CONTROL_SCOUT_HEIGHT_MARGIN = 6;
    private static final boolean DEDICATED_SERVER_SPAWN_REGION_PREP_BEFORE_CALLBACK = true;
    private static final boolean STRICT_TARGET_ONLY_GENERATION_GUARANTEED = false;
    private static final boolean CONCLUSIONS_DEPEND_ON_SPAWN_REGION_CHUNKS = false;

    private static final List<ProbePoint> FIXED_POINTS = List.of(
            new ProbePoint("raised-land-positive-a", "raised_land_positive", -804, 4705),
            new ProbePoint("raised-land-positive-b", "raised_land_positive", -1092, 4715),
            new ProbePoint("requested-control-invalid-a", "requested_control_invalid", 1568, -2328),
            new ProbePoint("requested-control-invalid-b", "requested_control_invalid", 1574, -2322)
    );

    private static final List<SearchArea> CONTROL_SEARCH_AREAS = List.of(
            new SearchArea("legacy_control_neighborhood", 1568, -2328, 256, 128),
            new SearchArea("polar_northwest", -7000, -8500, 1536, 384),
            new SearchArea("polar_northeast", 7000, -8500, 1536, 384),
            new SearchArea("warm_southwest", -7000, 8500, 1536, 384),
            new SearchArea("warm_southeast", 7000, 8500, 1536, 384),
            new SearchArea("cold_meridian", 0, -9000, 1536, 384),
            new SearchArea("warm_meridian", 0, 9000, 1536, 384),
            new SearchArea("west_ocean_band", -9000, 0, 1536, 384),
            new SearchArea("east_ocean_band", 9000, 0, 1536, 384),
            new SearchArea("mid_ring", 0, 0, 3072, 768)
    );
    private static final List<ProbePoint> RAISED_LAND_ANCHORS = List.of(
            new ProbePoint("raised-land-positive-a", "raised_land_positive", -804, 4705),
            new ProbePoint("raised-land-positive-b", "raised_land_positive", -1092, 4715)
    );

    private LithosphereFreshChunkProofHarness() {
    }

    public static Config parseConfig(String raw) {
        Map<String, String> values = parsePairs(raw);
        boolean enabled = parseBoolean(values.get("enabled"));
        Path outDir = Path.of(values.getOrDefault("out", "tmp/lithosphere-fresh-chunk-harness-97591ddd"));
        int radius = parseInt(values.get("radius"), 10_000);
        String mapCandidatesRaw = values.get("mapcandidates");
        if ((mapCandidatesRaw == null || mapCandidatesRaw.isBlank())) {
            mapCandidatesRaw = System.getProperty(MAP_CANDIDATES_PROP_KEY);
        }
        Path mapCandidatesPath = mapCandidatesRaw == null || mapCandidatesRaw.isBlank()
                ? null
                : Path.of(mapCandidatesRaw).toAbsolutePath().normalize();
        boolean unsafeSurfaceScoutEnabled = parseBoolean(values.get("unsafesurfacescout"));
        if (!unsafeSurfaceScoutEnabled) {
            unsafeSurfaceScoutEnabled = parseBoolean(System.getProperty(UNSAFE_SURFACE_SCOUT_PROP_KEY));
        }
        return new Config(enabled, outDir, Math.max(1, radius), mapCandidatesPath, unsafeSurfaceScoutEnabled);
    }

    public static void runAndStop(MinecraftServer server, Config config) {
        try {
            run(server, config);
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][litho-proof] fresh chunk proof failed", t);
        } finally {
            GlobeMod.LOGGER.info("[latdev][litho-proof] stopping server");
            server.stop(false);
        }
    }

    private static void run(MinecraftServer server, Config config) throws IOException {
        ServerWorld world = server.getOverworld();
        if (world == null) {
            throw new IllegalStateException("No overworld available");
        }

        Files.createDirectories(config.outDir());
        Instant startedAt = Instant.now();
        long startNs = System.nanoTime();
        long worldSeed = world.getSeed();
        LatitudeBiomes.setWorldSeed(worldSeed);

        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        int radius = activeRadius > 0 ? activeRadius : config.radiusBlocks();
        Registry<Biome> biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        BiomeSamplerTools.SamplerTemplate template = BiomeSamplerTools.createTemplate(world);
        NoiseConfig noiseConfig = NoiseConfig.create(template.settings().value(), template.noiseParameters(), worldSeed);
        MultiNoiseUtil.MultiNoiseSampler sampler = noiseConfig.getMultiNoiseSampler();
        boolean mrLithosphereLoaded = FabricLoader.getInstance().isModLoaded("mr_lithosphere");
        boolean legacyLithosphereLoaded = FabricLoader.getInstance().isModLoaded("lithosphere");
        boolean latitudeWrapperActive = world.getChunkManager().getChunkGenerator().getBiomeSource() instanceof LatitudeBiomeSource;

        Set<ChunkPos> fixedEvidenceChunks = new LinkedHashSet<>();
        for (ProbePoint point : FIXED_POINTS) {
            fixedEvidenceChunks.add(point.chunkPos());
        }
        for (ChunkPos chunkPos : fixedEvidenceChunks) {
            world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
        }

        List<ProbeResult> results = new ArrayList<>();
        for (ProbePoint point : FIXED_POINTS) {
            results.add(samplePoint(world, biomeRegistry, template, sampler, radius, point,
                    mrLithosphereLoaded || legacyLithosphereLoaded));
        }
        ControlSearchReport searchReport = deriveTrueOceanControls(world, biomeRegistry, template, noiseConfig, sampler, radius,
                mrLithosphereLoaded || legacyLithosphereLoaded, config.mapCandidatesPath(), config.unsafeSurfaceScoutEnabled());
        results.addAll(searchReport.derivedRows());

        Set<ChunkPos> explicitRequestedChunks = new LinkedHashSet<>(fixedEvidenceChunks);
        explicitRequestedChunks.addAll(searchReport.requestedChunks());
        Set<ChunkPos> sampledEvidenceChunks = new LinkedHashSet<>();
        for (ProbeResult result : results) {
            sampledEvidenceChunks.add(result.point().chunkPos());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        writeMarkdown(config.outDir().resolve("fresh-chunk-proof.md"), startedAt, worldSeed, radius,
                mrLithosphereLoaded, legacyLithosphereLoaded, latitudeWrapperActive, fixedEvidenceChunks,
                explicitRequestedChunks, sampledEvidenceChunks, searchReport, results, durationMs);
        writeJson(config.outDir().resolve("fresh-chunk-proof.json"), startedAt, worldSeed, radius,
                mrLithosphereLoaded, legacyLithosphereLoaded, latitudeWrapperActive, fixedEvidenceChunks,
                explicitRequestedChunks, sampledEvidenceChunks, searchReport, results, durationMs);
        writeVerdict(config.outDir().resolve("harness-verdict.md"), results, mrLithosphereLoaded || legacyLithosphereLoaded,
                latitudeWrapperActive, searchReport, durationMs);
        writeVerdict(config.outDir().resolve("hardened-harness-verdict.md"), results, mrLithosphereLoaded || legacyLithosphereLoaded,
                latitudeWrapperActive, searchReport, durationMs);
        writeControlDiscoveryReport(config.outDir().resolve("true-ocean-control-discovery.md"), searchReport);
        writeControlCandidatesJson(config.outDir().resolve("ocean-control-scout-candidates.json"), searchReport);

        GlobeMod.LOGGER.info("[latdev][litho-proof] wrote fresh chunk proof out={} points={} explicitRequestedChunks={} searchCandidates={} derivedControls={} durationMs={}",
                config.outDir().toAbsolutePath().normalize(), results.size(), explicitRequestedChunks.size(),
                searchReport.cheapSamplesEvaluated(), searchReport.validControlsFound(), durationMs);
    }

    private static ProbeResult samplePoint(ServerWorld world,
                                           Registry<Biome> biomeRegistry,
                                           BiomeSamplerTools.SamplerTemplate template,
                                           MultiNoiseUtil.MultiNoiseSampler sampler,
                                           int radius,
                                           ProbePoint point,
                                           boolean lithosphereLoaded) {
        int seaLevel = world.getSeaLevel();
        int worldSurfaceTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE, point.x(), point.z());
        int worldSurfaceWgTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, point.x(), point.z());
        int motionBlockingTopY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, point.x(), point.z());
        int oceanFloorTopY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, point.x(), point.z());
        int topBlockY = clampToWorld(world, worldSurfaceTopY - 1);
        int sampleY = clampToWorld(world, Math.max(topBlockY + 1, seaLevel + 1));

        BlockPos topPos = new BlockPos(point.x(), topBlockY, point.z());
        BlockState topState = world.getBlockState(topPos);
        FluidState topFluid = topState.getFluidState();
        BlockPos seaPos = new BlockPos(point.x(), clampToWorld(world, seaLevel), point.z());
        BlockState seaState = world.getBlockState(seaPos);
        FluidState seaFluid = seaState.getFluidState();
        boolean topSolid = topState.isSolidBlock(world, topPos);
        boolean topFluidPresent = !topFluid.isEmpty();
        boolean seaFluidPresent = !seaFluid.isEmpty();

        int noiseX = Math.floorDiv(point.x(), 4);
        int noiseZ = Math.floorDiv(point.z(), 4);
        int y0Quart = 0;
        int seaQuartY = Math.floorDiv(seaLevel, 4);
        int surfaceClassifyQuartY = LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2;
        int callerQuartY = Math.floorDiv(topBlockY, 4);

        RegistryEntry<Biome> baseY0 = template.baseSource().getBiome(noiseX, y0Quart, noiseZ, sampler);
        RegistryEntry<Biome> baseSeaY = template.baseSource().getBiome(noiseX, seaQuartY, noiseZ, sampler);
        RegistryEntry<Biome> baseSurfaceClassifyY = template.baseSource().getBiome(noiseX, surfaceClassifyQuartY, noiseZ, sampler);
        RegistryEntry<Biome> baseCallerY = template.baseSource().getBiome(noiseX, callerQuartY, noiseZ, sampler);

        RegistryEntry<Biome> pickY0 = LatitudeBiomes.pick(
                biomeRegistry, baseY0, point.x(), point.z(), topBlockY, radius, sampler,
                "FRESH_CHUNK_PROOF", null, null, null);
        RegistryEntry<Biome> pickSeaY = LatitudeBiomes.pick(
                biomeRegistry, baseSeaY, point.x(), point.z(), seaLevel, radius, sampler,
                "FRESH_CHUNK_PROOF", null, null, null);
        RegistryEntry<Biome> pickSurfaceClassifyY = LatitudeBiomes.pick(
                biomeRegistry, baseSurfaceClassifyY, point.x(), point.z(), LatitudeBiomes.SURFACE_CLASSIFY_Y, radius, sampler,
                "FRESH_CHUNK_PROOF", null, null, null);
        RegistryEntry<Biome> pickCallerY = LatitudeBiomes.pick(
                biomeRegistry, baseCallerY, point.x(), point.z(), topBlockY, radius, sampler,
                "FRESH_CHUNK_PROOF", null, null, null);

        String biomeAtSurface = biomeId(biomeRegistry, world.getBiome(topPos));
        String biomeAtSampleY = biomeId(biomeRegistry, world.getBiome(new BlockPos(point.x(), sampleY, point.z())));
        String finalBiome = biomeAtSurface;
        NoiseValues noiseValues = sampleNoise(sampler, point.x(), point.z());
        String physicalClass = classifyPhysicalColumn(world, seaLevel, worldSurfaceTopY, motionBlockingTopY,
                topBlockY, topPos, topState, topFluid, seaFluidPresent);

        boolean baseY0Ocean = baseY0.isIn(BiomeTags.IS_OCEAN);
        boolean pickY0Ocean = isOceanId(biomeId(biomeRegistry, pickY0));
        boolean earlyOceanReturnInferred = baseY0Ocean && pickY0Ocean;
        boolean fixedYSignalVisible = !baseY0.isIn(BiomeTags.IS_OCEAN)
                || !baseSeaY.isIn(BiomeTags.IS_OCEAN)
                || !baseSurfaceClassifyY.isIn(BiomeTags.IS_OCEAN);
        boolean finalBiomeOcean = isOceanId(finalBiome);
        String guardJustification = guardJustification(point, physicalClass, earlyOceanReturnInferred, finalBiomeOcean, fixedYSignalVisible);

        return new ProbeResult(
                point,
                lithosphereLoaded,
                world.getSeed(),
                radius,
                seaLevel,
                worldSurfaceTopY,
                worldSurfaceWgTopY,
                motionBlockingTopY,
                oceanFloorTopY,
                topBlockY,
                blockId(topState),
                fluidId(topFluid),
                topSolid,
                topFluidPresent,
                blockId(seaState),
                fluidId(seaFluid),
                seaFluidPresent,
                biomeId(biomeRegistry, baseY0),
                baseY0.isIn(BiomeTags.IS_OCEAN),
                biomeId(biomeRegistry, baseSeaY),
                baseSeaY.isIn(BiomeTags.IS_OCEAN),
                biomeId(biomeRegistry, baseSurfaceClassifyY),
                baseSurfaceClassifyY.isIn(BiomeTags.IS_OCEAN),
                biomeId(biomeRegistry, baseCallerY),
                baseCallerY.isIn(BiomeTags.IS_OCEAN),
                biomeId(biomeRegistry, pickY0),
                biomeId(biomeRegistry, pickSeaY),
                biomeId(biomeRegistry, pickSurfaceClassifyY),
                biomeId(biomeRegistry, pickCallerY),
                finalBiome,
                biomeAtSurface,
                biomeAtSampleY,
                noiseValues,
                physicalClass,
                earlyOceanReturnInferred,
                fixedYSignalVisible,
                guardJustification);
    }

    private static ControlSearchReport deriveTrueOceanControls(ServerWorld world,
                                                               Registry<Biome> biomeRegistry,
                                                               BiomeSamplerTools.SamplerTemplate template,
                                                               NoiseConfig noiseConfig,
                                                               MultiNoiseUtil.MultiNoiseSampler sampler,
                                                               int radius,
                                                               boolean lithosphereLoaded,
                                                               Path mapCandidatesPath,
                                                               boolean unsafeSurfaceScoutEnabled) {
        Set<String> seenColumns = new LinkedHashSet<>();
        List<ProbePoint> candidates = new ArrayList<>();
        for (SearchArea area : CONTROL_SEARCH_AREAS) {
            for (ProbePoint point : area.candidates()) {
                String key = point.x() + "," + point.z();
                if (seenColumns.add(key)) {
                    candidates.add(point);
                }
            }
        }

        List<ProbeResult> derived = new ArrayList<>();
        List<ProbeResult> contradictions = new ArrayList<>();
        List<CandidateScout> cheapRejected = new ArrayList<>();
        List<CandidateScout> verifiedRejected = new ArrayList<>();
        List<CandidateScout> verifiedAccepted = new ArrayList<>();
        ScoutStats scoutStats = new ScoutStats();
        Set<ChunkPos> requestedChunks = new LinkedHashSet<>();
        NoiseChunkGenerator noiseGenerator = world.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator generator
                ? generator
                : null;
        int seaLevel = world.getSeaLevel();
        int cheapSamplesEvaluated = 0;
        int fullVerifications = 0;
        String stopReason = "cheap_candidate_limit_exhausted";
        List<CandidateScout> shortlist = new ArrayList<>();
        List<ProbePoint> mapDerivedCandidates = loadMapDerivedCandidates(mapCandidatesPath);
        Set<ChunkPos> verifiedChunks = new LinkedHashSet<>();

        for (ProbePoint candidate : mapDerivedCandidates) {
            if (fullVerifications >= CONTROL_SEARCH_MAX_FULL_VERIFICATIONS) {
                stopReason = "full_verification_limit_reached";
                break;
            }
            ChunkPos chunkPos = candidate.chunkPos();
            if (!verifiedChunks.add(chunkPos)) {
                continue;
            }
            CandidateScout scout = unsafeSurfaceScoutEnabled
                    ? scoutCandidate(world, biomeRegistry, template, noiseConfig, sampler, radius,
                    noiseGenerator, seaLevel, candidate)
                    : scoutCandidateWithoutSurface(world, biomeRegistry, template, noiseConfig, sampler, radius,
                    noiseGenerator, seaLevel, candidate, "manual_candidate_full_verification_requested");
            shortlist.add(scout);
            fullVerifications++;
            requestedChunks.add(chunkPos);
            world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);

            ProbeResult sampled = samplePoint(world, biomeRegistry, template, sampler, radius, candidate, lithosphereLoaded);
            String rejection = verifyRejectionReason(sampled);
            if (rejection != null) {
                verifiedRejected.add(scout.withVerification(
                        rejection,
                        sampled.finalBiomeId(),
                        sampled.physicalClass(),
                        sampled.worldSurfaceTopY(),
                        sampled.topBlockId(),
                        sampled.topFluidId()));
            } else {
                ProbePoint derivedPoint = new ProbePoint(
                        "derived-true-ocean-control-" + (derived.size() + 1),
                        "derived_true_ocean_control",
                        candidate.x(),
                        candidate.z());
                ProbeResult derivedRow = samplePoint(world, biomeRegistry, template, sampler, radius, derivedPoint, lithosphereLoaded);
                derived.add(derivedRow);
                verifiedAccepted.add(scout.withVerification(
                        "accepted_valid_true_ocean_control",
                        derivedRow.finalBiomeId(),
                        derivedRow.physicalClass(),
                        derivedRow.worldSurfaceTopY(),
                        derivedRow.topBlockId(),
                        derivedRow.topFluidId()));
                if (derived.size() >= CONTROL_SEARCH_TARGET_VALID) {
                    stopReason = "target_valid_controls_found";
                    return new ControlSearchReport(CONTROL_SEARCH_AREAS, CONTROL_SEARCH_MAX_CANDIDATES,
                            CONTROL_SEARCH_TARGET_VALID, cheapSamplesEvaluated, fullVerifications, derived.size(), contradictions.size(),
                            stopReason, requestedChunks, derived, contradictions, shortlist, cheapRejected, verifiedRejected, verifiedAccepted, scoutStats.toMap());
                }
            }
        }

        if (!unsafeSurfaceScoutEnabled && !mapDerivedCandidates.isEmpty()) {
            if (mapDerivedCandidates.isEmpty()) {
                stopReason = "manual_candidate_mode_no_candidates";
            } else if (fullVerifications >= CONTROL_SEARCH_MAX_FULL_VERIFICATIONS) {
                stopReason = "full_verification_limit_reached";
            } else if (derived.isEmpty()) {
                stopReason = "manual_candidate_verification_complete_no_valid_controls";
            } else {
                stopReason = "manual_candidate_verification_complete_partial";
            }
            return new ControlSearchReport(CONTROL_SEARCH_AREAS, CONTROL_SEARCH_MAX_CANDIDATES,
                    CONTROL_SEARCH_TARGET_VALID, cheapSamplesEvaluated, fullVerifications, derived.size(), contradictions.size(),
                    stopReason, requestedChunks, derived, contradictions, shortlist, cheapRejected, verifiedRejected, verifiedAccepted, scoutStats.toMap());
        }

        for (ProbePoint candidate : candidates) {
            if (cheapSamplesEvaluated >= CONTROL_SEARCH_MAX_CANDIDATES) {
                stopReason = "cheap_candidate_limit_reached";
                break;
            }
            cheapSamplesEvaluated++;
            CandidateScout scout = unsafeSurfaceScoutEnabled
                    ? scoutCandidate(world, biomeRegistry, template, noiseConfig, sampler, radius,
                    noiseGenerator, seaLevel, candidate)
                    : scoutCandidateWithoutSurface(world, biomeRegistry, template, noiseConfig, sampler, radius,
                    noiseGenerator, seaLevel, candidate, "safe_prefilter_no_surface_chunk_generation");
            scoutStats.count(scout);
            if (!scout.candidateWorthy()) {
                cheapRejected.add(scout);
                continue;
            }
            shortlist.add(scout);
        }

        shortlist.sort((left, right) -> {
            int cmp = Boolean.compare(right.cheapWaterSignal(), left.cheapWaterSignal());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(right.score(), left.score());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(left.predictedTopY(), right.predictedTopY());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(left.z(), right.z());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(left.x(), right.x());
        });

        for (CandidateScout scout : shortlist) {
            if (fullVerifications >= CONTROL_SEARCH_MAX_FULL_VERIFICATIONS) {
                stopReason = "full_verification_limit_reached";
                break;
            }
            ChunkPos chunkPos = scout.point().chunkPos();
            if (!verifiedChunks.add(chunkPos)) {
                continue;
            }
            fullVerifications++;
            requestedChunks.add(chunkPos);
            world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);

            ProbeResult sampled = samplePoint(world, biomeRegistry, template, sampler, radius, scout.point(), lithosphereLoaded);
            String rejection = verifyRejectionReason(sampled);
            if (rejection != null) {
                verifiedRejected.add(scout.withVerification(
                        rejection,
                        sampled.finalBiomeId(),
                        sampled.physicalClass(),
                        sampled.worldSurfaceTopY(),
                        sampled.topBlockId(),
                        sampled.topFluidId()));
                if ("true_ocean".equals(sampled.physicalClass()) && !isOceanId(sampled.finalBiomeId())) {
                    contradictions.add(samplePoint(world, biomeRegistry, template, sampler, radius,
                            scout.point().withCategory("derived_true_ocean_contradiction"), lithosphereLoaded));
                }
                continue;
            }
            ProbePoint derivedPoint = new ProbePoint(
                    "derived-true-ocean-control-" + (derived.size() + 1),
                    "derived_true_ocean_control",
                    scout.x(),
                    scout.z());
            ProbeResult derivedRow = samplePoint(world, biomeRegistry, template, sampler, radius, derivedPoint, lithosphereLoaded);
            derived.add(derivedRow);
            verifiedAccepted.add(scout.withVerification("accepted_valid_true_ocean_control",
                    derivedRow.finalBiomeId(), derivedRow.physicalClass(),
                    derivedRow.worldSurfaceTopY(), derivedRow.topBlockId(), derivedRow.topFluidId()));
            if (derived.size() >= CONTROL_SEARCH_TARGET_VALID) {
                stopReason = "target_valid_controls_found";
                break;
            }
        }

        if (derived.isEmpty() && "cheap_candidate_limit_exhausted".equals(stopReason)) {
            stopReason = "no_valid_true_ocean_controls_found";
        }
        return new ControlSearchReport(CONTROL_SEARCH_AREAS, CONTROL_SEARCH_MAX_CANDIDATES,
                CONTROL_SEARCH_TARGET_VALID, cheapSamplesEvaluated, fullVerifications, derived.size(), contradictions.size(),
                stopReason, requestedChunks, derived, contradictions, shortlist, cheapRejected, verifiedRejected, verifiedAccepted, scoutStats.toMap());
    }

    private static CandidateScout scoutCandidateWithoutSurface(ServerWorld world,
                                                               Registry<Biome> biomeRegistry,
                                                               BiomeSamplerTools.SamplerTemplate template,
                                                               NoiseConfig noiseConfig,
                                                               MultiNoiseUtil.MultiNoiseSampler sampler,
                                                               int radius,
                                                               NoiseChunkGenerator noiseGenerator,
                                                               int seaLevel,
                                                               ProbePoint candidate,
                                                               String rejectionReason) {
        int predictedTopY = noiseGenerator == null
                ? Integer.MAX_VALUE
                : noiseGenerator.getHeight(candidate.x(), candidate.z(), Heightmap.Type.WORLD_SURFACE_WG, world, noiseConfig);
        int noiseX = Math.floorDiv(candidate.x(), 4);
        int noiseZ = Math.floorDiv(candidate.z(), 4);
        RegistryEntry<Biome> baseY0 = template.baseSource().getBiome(noiseX, 0, noiseZ, sampler);
        RegistryEntry<Biome> baseSeaY = template.baseSource().getBiome(noiseX, Math.floorDiv(seaLevel, 4), noiseZ, sampler);
        RegistryEntry<Biome> pickY0 = LatitudeBiomes.pick(
                biomeRegistry, baseY0, candidate.x(), candidate.z(), seaLevel, radius, sampler,
                "FRESH_CHUNK_CONTROL_PREFILTER", null, null, null);
        RegistryEntry<Biome> pickSeaY = LatitudeBiomes.pick(
                biomeRegistry, baseSeaY, candidate.x(), candidate.z(), seaLevel, radius, sampler,
                "FRESH_CHUNK_CONTROL_PREFILTER", null, null, null);

        String baseY0Id = biomeId(biomeRegistry, baseY0);
        String baseSeaYId = biomeId(biomeRegistry, baseSeaY);
        String pickY0Id = biomeId(biomeRegistry, pickY0);
        String pickSeaYId = biomeId(biomeRegistry, pickSeaY);
        boolean baseY0Ocean = baseY0.isIn(BiomeTags.IS_OCEAN) || isOceanId(baseY0Id);
        boolean baseSeaOcean = baseSeaY.isIn(BiomeTags.IS_OCEAN) || isOceanId(baseSeaYId);
        boolean pickY0Ocean = isOceanId(pickY0Id);
        boolean pickSeaOcean = isOceanId(pickSeaYId);
        boolean oceanSignal = baseY0Ocean || baseSeaOcean || pickY0Ocean || pickSeaOcean;
        boolean lowHeight = predictedTopY <= seaLevel + CONTROL_SCOUT_HEIGHT_MARGIN;
        boolean farFromRaisedAnchors = isFarFromRaisedAnchors(candidate.x(), candidate.z(), 1024);
        boolean oceanScoutRegion = candidate.label().contains("polar")
                || candidate.label().contains("warm")
                || candidate.label().contains("ocean_band")
                || candidate.label().contains("meridian")
                || candidate.label().contains("mid_ring");

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (baseY0Ocean) {
            score += 1;
            reasons.add("base_y0_ocean_hint(+1)");
        }
        if (pickY0Ocean) {
            score += 1;
            reasons.add("pick_y0_ocean_hint(+1)");
        }
        if (baseSeaOcean || pickSeaOcean) {
            score += 1;
            reasons.add("sea_level_ocean_hint(+1)");
        }
        if (lowHeight) {
            score += 2;
            reasons.add("predicted_top_near_sea(+2)");
        } else if (noiseGenerator == null) {
            reasons.add("predicted_top_unknown(no_noise_generator)");
        }
        if (farFromRaisedAnchors) {
            score += 1;
            reasons.add("far_from_raised_anchors(+1)");
        }
        if (oceanScoutRegion) {
            score += 1;
            reasons.add("ocean_scout_region(+1)");
        }
        reasons.add("manual_candidate_supplied(+bounded_full_verify)");

        return new CandidateScout(candidate, predictedTopY, baseY0Id, baseSeaYId, pickY0Id, pickSeaYId,
                baseY0Ocean, baseSeaOcean, pickY0Ocean, pickSeaOcean, oceanSignal, false, lowHeight,
                farFromRaisedAnchors, oceanScoutRegion, score, String.join(", ", reasons),
                true, rejectionReason, "not_verified", "unknown", "unknown",
                0, "unknown", "unknown");
    }

    private static CandidateScout scoutCandidate(ServerWorld world,
                                                 Registry<Biome> biomeRegistry,
                                                 BiomeSamplerTools.SamplerTemplate template,
                                                 NoiseConfig noiseConfig,
                                                 MultiNoiseUtil.MultiNoiseSampler sampler,
                                                 int radius,
                                                 NoiseChunkGenerator noiseGenerator,
                                                 int seaLevel,
                                                 ProbePoint candidate) {
        int predictedTopY = noiseGenerator == null
                ? Integer.MAX_VALUE
                : noiseGenerator.getHeight(candidate.x(), candidate.z(), Heightmap.Type.WORLD_SURFACE_WG, world, noiseConfig);
        Chunk surfaceChunk = world.getChunkManager().getChunk(candidate.chunkX(), candidate.chunkZ(), ChunkStatus.SURFACE, true);
        int cheapSurfaceTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE, candidate.x(), candidate.z());
        int cheapTopBlockY = clampToWorld(world, cheapSurfaceTopY - 1);
        BlockPos cheapTopPos = new BlockPos(candidate.x(), cheapTopBlockY, candidate.z());
        BlockPos cheapSeaPos = new BlockPos(candidate.x(), clampToWorld(world, seaLevel), candidate.z());
        BlockState cheapTopState = surfaceChunk.getBlockState(cheapTopPos);
        FluidState cheapTopFluid = cheapTopState.getFluidState();
        BlockState cheapSeaState = surfaceChunk.getBlockState(cheapSeaPos);
        FluidState cheapSeaFluid = cheapSeaState.getFluidState();
        boolean cheapTopWater = isWaterFluid(cheapTopFluid);
        boolean cheapSeaWater = isWaterFluid(cheapSeaFluid);
        boolean cheapWaterSignal = cheapTopWater || cheapSeaWater;
        boolean cheapTopNearSea = cheapSurfaceTopY <= seaLevel + CONTROL_SCOUT_HEIGHT_MARGIN;
        boolean cheapTopAtOrBelowSea = cheapSurfaceTopY <= seaLevel + 1;
        int noiseX = Math.floorDiv(candidate.x(), 4);
        int noiseZ = Math.floorDiv(candidate.z(), 4);
        RegistryEntry<Biome> baseY0 = template.baseSource().getBiome(noiseX, 0, noiseZ, sampler);
        RegistryEntry<Biome> baseSeaY = template.baseSource().getBiome(noiseX, Math.floorDiv(seaLevel, 4), noiseZ, sampler);
        RegistryEntry<Biome> pickY0 = LatitudeBiomes.pick(
                biomeRegistry, baseY0, candidate.x(), candidate.z(), seaLevel, radius, sampler,
                "FRESH_CHUNK_CONTROL_PREFILTER", null, null, null);
        RegistryEntry<Biome> pickSeaY = LatitudeBiomes.pick(
                biomeRegistry, baseSeaY, candidate.x(), candidate.z(), seaLevel, radius, sampler,
                "FRESH_CHUNK_CONTROL_PREFILTER", null, null, null);

        String baseY0Id = biomeId(biomeRegistry, baseY0);
        String baseSeaYId = biomeId(biomeRegistry, baseSeaY);
        String pickY0Id = biomeId(biomeRegistry, pickY0);
        String pickSeaYId = biomeId(biomeRegistry, pickSeaY);
        boolean baseY0Ocean = baseY0.isIn(BiomeTags.IS_OCEAN) || isOceanId(baseY0Id);
        boolean baseSeaOcean = baseSeaY.isIn(BiomeTags.IS_OCEAN) || isOceanId(baseSeaYId);
        boolean pickY0Ocean = isOceanId(pickY0Id);
        boolean pickSeaOcean = isOceanId(pickSeaYId);
        boolean oceanSignal = baseY0Ocean || baseSeaOcean || pickY0Ocean || pickSeaOcean;
        boolean lowHeight = predictedTopY <= seaLevel + CONTROL_SCOUT_HEIGHT_MARGIN || cheapTopNearSea;
        boolean farFromRaisedAnchors = isFarFromRaisedAnchors(candidate.x(), candidate.z(), 1024);
        boolean oceanScoutRegion = candidate.label().contains("polar")
                || candidate.label().contains("warm")
                || candidate.label().contains("ocean_band")
                || candidate.label().contains("meridian")
                || candidate.label().contains("mid_ring");

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (cheapTopWater) {
            score += 7;
            reasons.add("cheap_top_water(+7)");
        }
        if (cheapSeaWater) {
            score += 6;
            reasons.add("cheap_sea_level_water(+6)");
        }
        if (cheapTopAtOrBelowSea) {
            score += 4;
            reasons.add("cheap_surface_at_or_below_sea(+4)");
        }
        if (cheapTopNearSea) {
            score += 3;
            reasons.add("cheap_surface_near_sea(+3)");
        }
        if (baseY0Ocean) {
            score += 1;
            reasons.add("base_y0_ocean_hint(+1)");
        }
        if (pickY0Ocean) {
            score += 1;
            reasons.add("pick_y0_ocean_hint(+1)");
        }
        if (baseSeaOcean || pickSeaOcean) {
            score += 1;
            reasons.add("sea_level_ocean_hint(+1)");
        }
        if (lowHeight) {
            score += 2;
            reasons.add("predicted_top_near_sea(+2)");
        }
        if (farFromRaisedAnchors) {
            score += 1;
            reasons.add("far_from_raised_anchors(+1)");
        }
        if (oceanScoutRegion) {
            score += 1;
            reasons.add("ocean_scout_region(+1)");
        }

        boolean candidateWorthy = cheapWaterSignal || score >= CONTROL_SCOUT_MIN_SCORE;
        String rejectionReason = candidateWorthy ? "shortlisted_score_" + score : "rejected_low_score_" + score;
        return new CandidateScout(candidate, predictedTopY, baseY0Id, baseSeaYId, pickY0Id, pickSeaYId,
                baseY0Ocean, baseSeaOcean, pickY0Ocean, pickSeaOcean, oceanSignal, cheapWaterSignal, lowHeight,
                farFromRaisedAnchors, oceanScoutRegion, score, String.join(", ", reasons),
                candidateWorthy, rejectionReason, "not_verified", "unknown", "unknown",
                0, "unknown", "unknown");
    }

    private static String verifyRejectionReason(ProbeResult sampled) {
        if (!"true_ocean".equals(sampled.physicalClass())) {
            return "rejected_physical_class_" + sampled.physicalClass();
        }
        if (!sampled.topFluidPresent() && !sampled.seaFluidPresent()) {
            return "rejected_no_water_evidence";
        }
        if (!isOceanId(sampled.finalBiomeId())) {
            return "rejected_final_biome_non_ocean";
        }
        return null;
    }

    private static String classifyPhysicalColumn(ServerWorld world,
                                                 int seaLevel,
                                                 int worldSurfaceTopY,
                                                 int motionBlockingTopY,
                                                 int topBlockY,
                                                 BlockPos topPos,
                                                 BlockState topState,
                                                 FluidState topFluid,
                                                 boolean seaFluidPresent) {
        if (worldSurfaceTopY <= world.getBottomY() + 1) {
            return "cave_or_void";
        }
        if (motionBlockingTopY > seaLevel + 2 && topBlockY > seaLevel + 2 && topFluid.isEmpty()) {
            return "raised_land";
        }
        if (worldSurfaceTopY <= seaLevel + 1 && (!topFluid.isEmpty() || seaFluidPresent)) {
            return "true_ocean";
        }
        return "ambiguous";
    }

    private static String guardJustification(ProbePoint point,
                                             String physicalClass,
                                             boolean earlyOceanReturnInferred,
                                             boolean finalBiomeOcean,
                                             boolean fixedYSignalVisible) {
        if ("requested_control_invalid".equals(point.category())) {
            return "requested_control_validation_row";
        }
        if ("derived_true_ocean_control".equals(point.category())) {
            return "valid_true_ocean_control";
        }
        if ("derived_true_ocean_contradiction".equals(point.category())) {
            return "physical_ocean_final_non_ocean_contradiction";
        }
        if (!"raised_land".equals(physicalClass)) {
            return "no_physical_raised_land_not_proven";
        }
        if (!earlyOceanReturnInferred) {
            return "no_early_ocean_return_not_seen";
        }
        if (!finalBiomeOcean) {
            return "observational_only_final_biome_not_ocean";
        }
        if (fixedYSignalVisible) {
            return "candidate_fixed_y_signal_visible_next_slice_only";
        }
        return "yes_observationally_but_no_fixed_y_prewrite_signal";
    }

    private static NoiseValues sampleNoise(MultiNoiseUtil.MultiNoiseSampler sampler, int blockX, int blockZ) {
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        MultiNoiseUtil.NoiseValuePoint point = sampler.sample(noiseX, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, noiseZ);
        return new NoiseValues(
                MultiNoiseUtil.toFloat(point.continentalnessNoise()),
                MultiNoiseUtil.toFloat(point.erosionNoise()),
                MultiNoiseUtil.toFloat(point.weirdnessNoise()));
    }

    private static void writeMarkdown(Path path,
                                      Instant startedAt,
                                      long worldSeed,
                                      int radius,
                                      boolean mrLithosphereLoaded,
                                      boolean legacyLithosphereLoaded,
                                      boolean latitudeWrapperActive,
                                      Set<ChunkPos> fixedEvidenceChunks,
                                      Set<ChunkPos> explicitRequestedChunks,
                                      Set<ChunkPos> sampledEvidenceChunks,
                                      ControlSearchReport searchReport,
                                      List<ProbeResult> results,
                                      long durationMs) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Lithosphere Fresh Chunk Proof\n\n");
        out.append("- generated_at: ").append(startedAt).append('\n');
        out.append("- world_seed: ").append(worldSeed).append('\n');
        out.append("- radius: ").append(radius).append('\n');
        out.append("- mr_lithosphere_loaded: ").append(mrLithosphereLoaded).append('\n');
        out.append("- legacy_lithosphere_loaded: ").append(legacyLithosphereLoaded).append('\n');
        out.append("- latitude_wrapper_active: ").append(latitudeWrapperActive).append('\n');
        out.append("- fixed_evidence_chunks: ").append(fixedEvidenceChunks.size()).append('\n');
        out.append("- explicit_harness_requested_chunks: ").append(explicitRequestedChunks.size()).append('\n');
        out.append("- sampled_evidence_chunks: ").append(sampledEvidenceChunks.size()).append('\n');
        out.append("- dedicated_server_spawn_region_prep_before_callback: ").append(DEDICATED_SERVER_SPAWN_REGION_PREP_BEFORE_CALLBACK).append('\n');
        out.append("- strict_target_only_generation_guaranteed: ").append(STRICT_TARGET_ONLY_GENERATION_GUARANTEED).append('\n');
        out.append("- conclusions_depend_on_spawn_region_chunks: ").append(CONCLUSIONS_DEPEND_ON_SPAWN_REGION_CHUNKS).append('\n');
        out.append("- duration_ms: ").append(durationMs).append("\n\n");

        ProofClassifications proof = classifyProof(results);
        out.append("## Final Proof Classification\n\n");
        out.append("- raised_land_mismatch_proven: ").append(proof.raisedLandMismatchProven()).append('\n');
        out.append("- valid_true_ocean_controls_proven: ").append(proof.validTrueOceanControlsProven()).append('\n');
        out.append("- true_ocean_preservation_baseline_known: ").append(proof.trueOceanPreservationBaselineKnown()).append('\n');
        out.append("- cheap_pre_biome_signal_found: ").append(proof.cheapPreBiomeSignalFound()).append('\n');
        out.append("- production_fix_safe_to_attempt_next: ").append(proof.productionFixSafeToAttemptNext()).append("\n\n");

        out.append("## Control Search\n\n");
        out.append("- max_candidate_columns: ").append(searchReport.maxCandidates()).append('\n');
        out.append("- target_valid_controls: ").append(searchReport.targetValidControls()).append('\n');
        out.append("- cheap_candidates_evaluated: ").append(searchReport.cheapSamplesEvaluated()).append('\n');
        out.append("- cheap_candidates_shortlisted: ").append(searchReport.shortlist().size()).append('\n');
        out.append("- full_verification_limit: ").append(CONTROL_SEARCH_MAX_FULL_VERIFICATIONS).append('\n');
        out.append("- full_chunk_verifications: ").append(searchReport.fullVerifications()).append('\n');
        out.append("- full_chunk_requests_after_prefilter: ").append(searchReport.requestedChunks().size()).append('\n');
        out.append("- valid_true_ocean_controls_found: ").append(searchReport.validControlsFound()).append('\n');
        out.append("- true_ocean_final_non_ocean_contradictions: ").append(searchReport.contradictionCount()).append('\n');
        out.append("- stop_reason: ").append(searchReport.stopReason()).append('\n');
        out.append("- bounds:\n");
        for (SearchArea area : searchReport.areas()) {
            out.append("  - ").append(area.describe()).append('\n');
        }
        out.append('\n');

        out.append("## Spawn-Region Caveat\n\n");
        out.append("Dedicated-server spawn-region preparation happens before the SERVER_STARTED proof callback. ");
        out.append("The harness therefore reports explicit harness-requested chunks separately from sampled evidence chunks, ");
        out.append("and strict target-only world generation is not claimed. Target/control conclusions depend only on sampled rows from explicitly requested harness chunks.\n\n");

        out.append("## Invalid Controls Explanation\n\n");
        out.append("The original fixed controls are preserved as requested_control_invalid rows. They are invalid controls in this proof world because they do not classify as true_ocean with ocean-family final biome.\n\n");

        appendSection(out, "Raised-Land Positives", results, "raised_land_positive");
        appendSection(out, "Requested Controls Marked Invalid", results, "requested_control_invalid");
        appendSection(out, "Derived True-Ocean Controls", results, "derived_true_ocean_control");
        if (!searchReport.contradictionRows().isEmpty()) {
            appendSection(out, "Derived True-Ocean Contradictions", searchReport.contradictionRows(), "derived_true_ocean_contradiction");
        }
        appendSection(out, "All Sample Rows", results, null);

        out.append("\n## Noise Values\n\n");
        out.append("| label | continentalness | erosion | weirdness |\n");
        out.append("| --- | ---: | ---: | ---: |\n");
        for (ProbeResult r : results) {
            out.append("| ")
                    .append(r.point().label()).append(" | ")
                    .append(formatDouble(r.noiseValues().continentalness())).append(" | ")
                    .append(formatDouble(r.noiseValues().erosion())).append(" | ")
                    .append(formatDouble(r.noiseValues().weirdness())).append(" |\n");
        }
        Files.writeString(path, out.toString());
    }

    private static void appendSection(StringBuilder out, String title, List<ProbeResult> rows, String category) {
        out.append("## ").append(title).append("\n\n");
        out.append("| label | category | x | z | chunk | base_y0 | pick_y0 | base_sea_y | base_surface_classify_y | base_caller_y | final_biome | surface_biome | sample_y_biome | world_surface_top_y | world_surface_wg_top_y | motion_blocking_top_y | top_block | top_fluid | sea_block | sea_fluid | physical_class | early_ocean_return_inferred | fixed_y_signal_visible | guard_justification |\n");
        out.append("| --- | --- | ---: | ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int written = 0;
        for (ProbeResult r : rows) {
            if (category != null && !category.equals(r.point().category())) {
                continue;
            }
            appendProbeRow(out, r);
            written++;
        }
        if (written == 0) {
            out.append("| none | ").append(category == null ? "all" : category).append(" | 0 | 0 | 0,0 | none | none | none | none | none | none | none | none | 0 | 0 | 0 | none | none | none | none | none | false | false | none |\n");
        }
        out.append('\n');
    }

    private static void appendProbeRow(StringBuilder out, ProbeResult r) {
        out.append("| ")
                .append(r.point().label()).append(" | ")
                .append(r.point().category()).append(" | ")
                .append(r.point().x()).append(" | ")
                .append(r.point().z()).append(" | ")
                .append(r.point().chunkX()).append(',').append(r.point().chunkZ()).append(" | ")
                .append(r.baseY0Id()).append(" | ")
                .append(r.pickY0Id()).append(" | ")
                .append(r.baseSeaYId()).append(" | ")
                .append(r.baseSurfaceClassifyYId()).append(" | ")
                .append(r.baseCallerYId()).append(" | ")
                .append(r.finalBiomeId()).append(" | ")
                .append(r.biomeAtSurfaceId()).append(" | ")
                .append(r.biomeAtSampleYId()).append(" | ")
                .append(r.worldSurfaceTopY()).append(" | ")
                .append(r.worldSurfaceWgTopY()).append(" | ")
                .append(r.motionBlockingTopY()).append(" | ")
                .append(r.topBlockId()).append(" | ")
                .append(r.topFluidId()).append(" | ")
                .append(r.seaBlockId()).append(" | ")
                .append(r.seaFluidId()).append(" | ")
                .append(r.physicalClass()).append(" | ")
                .append(r.earlyOceanReturnInferred()).append(" | ")
                .append(r.fixedYSignalVisible()).append(" | ")
                .append(r.guardJustification()).append(" |\n");
    }

    private static void writeJson(Path path,
                                  Instant startedAt,
                                  long worldSeed,
                                  int radius,
                                  boolean mrLithosphereLoaded,
                                  boolean legacyLithosphereLoaded,
                                  boolean latitudeWrapperActive,
                                  Set<ChunkPos> fixedEvidenceChunks,
                                  Set<ChunkPos> explicitRequestedChunks,
                                  Set<ChunkPos> sampledEvidenceChunks,
                                  ControlSearchReport searchReport,
                                  List<ProbeResult> results,
                                  long durationMs) throws IOException {
        StringBuilder out = new StringBuilder();
        ProofClassifications proof = classifyProof(results);
        out.append("{\n");
        jsonField(out, 1, "generated_at", startedAt.toString(), true);
        jsonField(out, 1, "world_seed", worldSeed, true);
        jsonField(out, 1, "radius", radius, true);
        jsonField(out, 1, "mr_lithosphere_loaded", mrLithosphereLoaded, true);
        jsonField(out, 1, "legacy_lithosphere_loaded", legacyLithosphereLoaded, true);
        jsonField(out, 1, "latitude_wrapper_active", latitudeWrapperActive, true);
        jsonField(out, 1, "fixed_evidence_chunk_count", fixedEvidenceChunks.size(), true);
        jsonField(out, 1, "explicit_harness_requested_chunk_count", explicitRequestedChunks.size(), true);
        jsonField(out, 1, "sampled_evidence_chunk_count", sampledEvidenceChunks.size(), true);
        jsonField(out, 1, "dedicated_server_spawn_region_prep_before_callback", DEDICATED_SERVER_SPAWN_REGION_PREP_BEFORE_CALLBACK, true);
        jsonField(out, 1, "strict_target_only_generation_guaranteed", STRICT_TARGET_ONLY_GENERATION_GUARANTEED, true);
        jsonField(out, 1, "conclusions_depend_on_spawn_region_chunks", CONCLUSIONS_DEPEND_ON_SPAWN_REGION_CHUNKS, true);
        jsonField(out, 1, "duration_ms", durationMs, true);
        indent(out, 1).append("\"proof_classifications\": {\n");
        jsonField(out, 2, "raised_land_mismatch_proven", proof.raisedLandMismatchProven(), true);
        jsonField(out, 2, "valid_true_ocean_controls_proven", proof.validTrueOceanControlsProven(), true);
        jsonField(out, 2, "true_ocean_preservation_baseline_known", proof.trueOceanPreservationBaselineKnown(), true);
        jsonField(out, 2, "cheap_pre_biome_signal_found", proof.cheapPreBiomeSignalFound(), true);
        jsonField(out, 2, "production_fix_safe_to_attempt_next", proof.productionFixSafeToAttemptNext(), false);
        indent(out, 1).append("},\n");
        indent(out, 1).append("\"control_search\": {\n");
        jsonField(out, 2, "max_candidate_columns", searchReport.maxCandidates(), true);
        jsonField(out, 2, "target_valid_controls", searchReport.targetValidControls(), true);
        jsonField(out, 2, "cheap_candidates_evaluated", searchReport.cheapSamplesEvaluated(), true);
        jsonField(out, 2, "cheap_candidates_shortlisted", searchReport.shortlist().size(), true);
        jsonField(out, 2, "full_verification_limit", CONTROL_SEARCH_MAX_FULL_VERIFICATIONS, true);
        jsonField(out, 2, "full_chunk_verifications", searchReport.fullVerifications(), true);
        jsonField(out, 2, "full_chunk_requests_after_prefilter", searchReport.requestedChunks().size(), true);
        jsonField(out, 2, "valid_true_ocean_controls_found", searchReport.validControlsFound(), true);
        jsonField(out, 2, "true_ocean_final_non_ocean_contradictions", searchReport.contradictionCount(), true);
        jsonField(out, 2, "stop_reason", searchReport.stopReason(), true);
        indent(out, 2).append("\"cheap_reject_reason_counts\": {\n");
        int reasonIndex = 0;
        int reasonSize = searchReport.cheapRejectReasonCounts().size();
        for (Map.Entry<String, Integer> entry : searchReport.cheapRejectReasonCounts().entrySet()) {
            jsonField(out, 3, entry.getKey(), entry.getValue(), ++reasonIndex < reasonSize);
        }
        indent(out, 2).append("},\n");
        indent(out, 2).append("\"bounds\": [\n");
        for (int i = 0; i < searchReport.areas().size(); i++) {
            SearchArea area = searchReport.areas().get(i);
            indent(out, 3).append("{\n");
            jsonField(out, 4, "label", area.label(), true);
            jsonField(out, 4, "center_x", area.centerX(), true);
            jsonField(out, 4, "center_z", area.centerZ(), true);
            jsonField(out, 4, "radius", area.radius(), true);
            jsonField(out, 4, "step", area.step(), false);
            indent(out, 3).append('}').append(i + 1 < searchReport.areas().size() ? "," : "").append('\n');
        }
        indent(out, 2).append("]\n");
        indent(out, 1).append("},\n");
        indent(out, 1).append("\"samples\": [\n");
        for (int i = 0; i < results.size(); i++) {
            ProbeResult r = results.get(i);
            indent(out, 2).append("{\n");
            jsonField(out, 3, "label", r.point().label(), true);
            jsonField(out, 3, "category", r.point().category(), true);
            jsonField(out, 3, "x", r.point().x(), true);
            jsonField(out, 3, "z", r.point().z(), true);
            jsonField(out, 3, "chunk_x", r.point().chunkX(), true);
            jsonField(out, 3, "chunk_z", r.point().chunkZ(), true);
            jsonField(out, 3, "world_seed", r.worldSeed(), true);
            jsonField(out, 3, "lithosphere_loaded", r.lithosphereLoaded(), true);
            jsonField(out, 3, "sea_level", r.seaLevel(), true);
            jsonField(out, 3, "world_surface_top_y", r.worldSurfaceTopY(), true);
            jsonField(out, 3, "world_surface_wg_top_y", r.worldSurfaceWgTopY(), true);
            jsonField(out, 3, "motion_blocking_top_y", r.motionBlockingTopY(), true);
            jsonField(out, 3, "ocean_floor_top_y", r.oceanFloorTopY(), true);
            jsonField(out, 3, "top_block_y", r.topBlockY(), true);
            jsonField(out, 3, "top_block_id", r.topBlockId(), true);
            jsonField(out, 3, "top_fluid_id", r.topFluidId(), true);
            jsonField(out, 3, "top_solid", r.topSolid(), true);
            jsonField(out, 3, "top_fluid_present", r.topFluidPresent(), true);
            jsonField(out, 3, "sea_block_id", r.seaBlockId(), true);
            jsonField(out, 3, "sea_fluid_id", r.seaFluidId(), true);
            jsonField(out, 3, "sea_fluid_present", r.seaFluidPresent(), true);
            jsonField(out, 3, "base_y0_id", r.baseY0Id(), true);
            jsonField(out, 3, "base_y0_ocean", r.baseY0Ocean(), true);
            jsonField(out, 3, "base_sea_y_id", r.baseSeaYId(), true);
            jsonField(out, 3, "base_sea_y_ocean", r.baseSeaYOcean(), true);
            jsonField(out, 3, "base_surface_classify_y_id", r.baseSurfaceClassifyYId(), true);
            jsonField(out, 3, "base_surface_classify_y_ocean", r.baseSurfaceClassifyYOcean(), true);
            jsonField(out, 3, "base_caller_y_id", r.baseCallerYId(), true);
            jsonField(out, 3, "base_caller_y_ocean", r.baseCallerYOcean(), true);
            jsonField(out, 3, "pick_y0_id", r.pickY0Id(), true);
            jsonField(out, 3, "pick_sea_y_id", r.pickSeaYId(), true);
            jsonField(out, 3, "pick_surface_classify_y_id", r.pickSurfaceClassifyYId(), true);
            jsonField(out, 3, "pick_caller_y_id", r.pickCallerYId(), true);
            jsonField(out, 3, "final_biome_id", r.finalBiomeId(), true);
            jsonField(out, 3, "biome_at_surface_id", r.biomeAtSurfaceId(), true);
            jsonField(out, 3, "biome_at_sample_y_id", r.biomeAtSampleYId(), true);
            jsonField(out, 3, "continentalness", r.noiseValues().continentalness(), true);
            jsonField(out, 3, "erosion", r.noiseValues().erosion(), true);
            jsonField(out, 3, "weirdness", r.noiseValues().weirdness(), true);
            jsonField(out, 3, "physical_class", r.physicalClass(), true);
            jsonField(out, 3, "early_ocean_return_inferred", r.earlyOceanReturnInferred(), true);
            jsonField(out, 3, "fixed_y_signal_visible", r.fixedYSignalVisible(), true);
            jsonField(out, 3, "guard_justification", r.guardJustification(), false);
            indent(out, 2).append('}').append(i + 1 < results.size() ? "," : "").append('\n');
        }
        indent(out, 1).append("]\n");
        out.append("}\n");
        Files.writeString(path, out.toString());
    }

    private static ProofClassifications classifyProof(List<ProbeResult> results) {
        List<ProbeResult> raised = results.stream()
                .filter(r -> "raised_land_positive".equals(r.point().category()))
                .toList();
        List<ProbeResult> derivedControls = results.stream()
                .filter(r -> "derived_true_ocean_control".equals(r.point().category()))
                .toList();

        boolean raisedLandMismatchProven = !raised.isEmpty()
                && raised.stream().allMatch(r -> "raised_land".equals(r.physicalClass()))
                && raised.stream().allMatch(ProbeResult::baseY0Ocean)
                && raised.stream().allMatch(r -> isOceanId(r.finalBiomeId()))
                && raised.stream().allMatch(ProbeResult::earlyOceanReturnInferred);
        boolean validTrueOceanControlsProven = derivedControls.size() >= CONTROL_SEARCH_TARGET_VALID
                && derivedControls.stream().allMatch(r -> "true_ocean".equals(r.physicalClass()))
                && derivedControls.stream().allMatch(r -> isOceanId(r.finalBiomeId()));
        boolean cheapPreBiomeSignalFound = raised.stream().anyMatch(ProbeResult::fixedYSignalVisible);
        boolean productionFixSafe = raisedLandMismatchProven && validTrueOceanControlsProven && cheapPreBiomeSignalFound;

        return new ProofClassifications(
                raisedLandMismatchProven,
                validTrueOceanControlsProven,
                validTrueOceanControlsProven,
                cheapPreBiomeSignalFound,
                productionFixSafe);
    }

    private static String recommendedNextRoute(ProofClassifications proof) {
        if (!proof.raisedLandMismatchProven()) {
            return "proof_blocked_recheck_raised_land_targets";
        }
        if (!proof.validTrueOceanControlsProven()) {
            return "proof_blocked_derive_true_ocean_controls";
        }
        if (!proof.cheapPreBiomeSignalFound()) {
            return "no_production_fix_yet_bounded_prewrite_signal_audit";
        }
        return "next_slice_biomes_stage_guard_validation";
    }

    private static void writeVerdict(Path path,
                                     List<ProbeResult> results,
                                     boolean lithosphereLoaded,
                                     boolean latitudeWrapperActive,
                                     ControlSearchReport searchReport,
                                     long durationMs) throws IOException {
        ProofClassifications proof = classifyProof(results);
        boolean handsOff = true;
        boolean generatedChunks = !results.isEmpty();
        boolean raisedTargetsRaised = results.stream()
                .filter(r -> "raised_land_positive".equals(r.point().category()))
                .allMatch(r -> "raised_land".equals(r.physicalClass()));
        boolean raisedTargetsHaveOceanBase = results.stream()
                .filter(r -> "raised_land_positive".equals(r.point().category()))
                .allMatch(ProbeResult::baseY0Ocean);
        boolean raisedTargetsFinalOcean = results.stream()
                .filter(r -> "raised_land_positive".equals(r.point().category()))
                .anyMatch(r -> isOceanId(r.finalBiomeId()));
        boolean cheapFixedYSignal = results.stream()
                .filter(r -> "raised_land_positive".equals(r.point().category()))
                .anyMatch(ProbeResult::fixedYSignalVisible);
        boolean requestedControlsInvalid = results.stream()
                .filter(r -> "requested_control_invalid".equals(r.point().category()))
                .allMatch(r -> !"true_ocean".equals(r.physicalClass()) || !isOceanId(r.finalBiomeId()));

        StringBuilder out = new StringBuilder();
        out.append("# Lithosphere Fresh Chunk Harness Verdict\n\n");
        out.append("1. Did the harness run hands-off? ").append(handsOff).append(". It ran from server start, wrote files, and requested server stop. duration_ms=").append(durationMs).append('\n');
        out.append("2. Did it generate/load fresh chunks? ").append(generatedChunks)
                .append(". It requested only the target/control chunk set, but dedicated-server spawn-region preparation can occur before this callback; strict target-only world generation is therefore not claimed.\n");
        out.append("3. Did Lithosphere load? ").append(lithosphereLoaded).append(". latitude_wrapper_active=").append(latitudeWrapperActive).append('\n');
        out.append("4. Do raised-land positives classify as raised land? ").append(raisedTargetsRaised).append('\n');
        out.append("5. Raised positives ocean base/final status: base_y0_ocean=").append(raisedTargetsHaveOceanBase)
                .append(", any_final_ocean=").append(raisedTargetsFinalOcean).append('\n');
        out.append("6. Did the harness derive at least two valid true-ocean controls? ").append(proof.validTrueOceanControlsProven())
                .append(" (found=").append(searchReport.validControlsFound()).append(", evaluated=")
                .append(searchReport.cheapSamplesEvaluated()).append(", full_verified=")
                .append(searchReport.fullVerifications()).append(", stop_reason=").append(searchReport.stopReason()).append(")\n");
        out.append("7. Do true-ocean controls remain ocean-family? ").append(proof.trueOceanPreservationBaselineKnown()).append('\n');
        out.append("8. Are the old fixed controls marked invalid? ").append(requestedControlsInvalid).append('\n');
        out.append("9. Is strict target-only generation still unproven due to spawn-region prep? ")
                .append(!STRICT_TARGET_ONLY_GENERATION_GUARANTEED)
                .append(". dedicated_server_spawn_region_prep_before_callback=")
                .append(DEDICATED_SERVER_SPAWN_REGION_PREP_BEFORE_CALLBACK).append('\n');
        out.append("10. Does that caveat affect target/control conclusions? ")
                .append(CONCLUSIONS_DEPEND_ON_SPAWN_REGION_CHUNKS)
                .append(". Conclusions use sampled evidence rows from explicitly requested harness chunks.\n");
        out.append("11. Is any cheap pre-biome signal found? ").append(cheapFixedYSignal).append('\n');
        out.append("12. Is production implementation safe to attempt next? ")
                .append(proof.productionFixSafeToAttemptNext())
                .append(". Recommended next route: ")
                .append(recommendedNextRoute(proof))
                .append('\n');
        out.append("\n## Required Classifications\n\n");
        out.append("- raised_land_mismatch_proven: ").append(proof.raisedLandMismatchProven()).append('\n');
        out.append("- valid_true_ocean_controls_proven: ").append(proof.validTrueOceanControlsProven()).append('\n');
        out.append("- true_ocean_preservation_baseline_known: ").append(proof.trueOceanPreservationBaselineKnown()).append('\n');
        out.append("- cheap_pre_biome_signal_found: ").append(proof.cheapPreBiomeSignalFound()).append('\n');
        out.append("- production_fix_safe_to_attempt_next: ").append(proof.productionFixSafeToAttemptNext()).append('\n');
        out.append("\n## Control Search\n\n");
        out.append("- max_candidate_columns: ").append(searchReport.maxCandidates()).append('\n');
        out.append("- cheap_candidates_evaluated: ").append(searchReport.cheapSamplesEvaluated()).append('\n');
        out.append("- cheap_candidates_shortlisted: ").append(searchReport.shortlist().size()).append('\n');
        out.append("- full_verification_limit: ").append(CONTROL_SEARCH_MAX_FULL_VERIFICATIONS).append('\n');
        out.append("- full_chunk_verifications: ").append(searchReport.fullVerifications()).append('\n');
        out.append("- full_chunk_requests_after_prefilter: ").append(searchReport.requestedChunks().size()).append('\n');
        out.append("- search_bounds: ");
        for (int i = 0; i < searchReport.areas().size(); i++) {
            if (i > 0) {
                out.append("; ");
            }
            out.append(searchReport.areas().get(i).describe());
        }
        out.append('\n');
        Files.writeString(path, out.toString());
    }

    private static void writeControlDiscoveryReport(Path path, ControlSearchReport searchReport) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# True-Ocean Control Discovery\n\n");
        out.append("- cheap_sample_count: ").append(searchReport.cheapSamplesEvaluated()).append('\n');
        out.append("- cheap_shortlist_count: ").append(searchReport.shortlist().size()).append('\n');
        out.append("- full_verification_limit: ").append(CONTROL_SEARCH_MAX_FULL_VERIFICATIONS).append('\n');
        out.append("- full_verification_count: ").append(searchReport.fullVerifications()).append('\n');
        out.append("- valid_controls_found: ").append(searchReport.validControlsFound()).append('\n');
        out.append("- stop_reason: ").append(searchReport.stopReason()).append('\n');
        out.append("- watchdog_risk_note: FULL chunk generation is bounded to shortlisted controls only.\n\n");

        out.append("## Search Bounds\n\n");
        for (SearchArea area : searchReport.areas()) {
            out.append("- ").append(area.describe()).append('\n');
        }
        out.append('\n');

        out.append("## Chosen Controls\n\n");
        if (searchReport.derivedRows().isEmpty()) {
            out.append("- none\n\n");
        } else {
            for (ProbeResult row : searchReport.derivedRows()) {
                out.append("- ").append(row.point().label())
                        .append(": x=").append(row.point().x())
                        .append(", z=").append(row.point().z())
                        .append(", final=").append(row.finalBiomeId())
                        .append(", physical=").append(row.physicalClass())
                        .append(", topY=").append(row.worldSurfaceTopY())
                        .append('\n');
            }
            out.append('\n');
        }

        out.append("## Cheap Reject Aggregates\n\n");
        for (Map.Entry<String, Integer> entry : searchReport.cheapRejectReasonCounts().entrySet()) {
            out.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        out.append('\n');

        out.append("## FULL Verification Rows\n\n");
        out.append("| label | x | z | score | score_reasons | base_y0 | pick_y0 | predicted_top_y | final_biome | world_surface_top_y | top_block | top_fluid | physical_class | result |\n");
        out.append("| --- | ---: | ---: | ---: | --- | --- | --- | ---: | --- | ---: | --- | --- | --- | --- |\n");
        int written = 0;
        for (CandidateScout scout : searchReport.verifiedAccepted()) {
            appendFullVerificationRow(out, scout);
            written++;
        }
        for (CandidateScout scout : searchReport.verifiedRejected()) {
            appendFullVerificationRow(out, scout);
            written++;
        }
        if (written == 0) {
            out.append("| none | 0 | 0 | 0 | none | none | none | 0 | none | 0 | none | none | none | none |\n");
        }
        out.append('\n');

        out.append("## Validity Summary\n\n");
        if (searchReport.validControlsFound() >= CONTROL_SEARCH_TARGET_VALID) {
            out.append("Derived controls satisfy true_ocean physical class, water evidence, and ocean-family final biome.\n");
        } else {
            out.append("No sufficient valid controls were proven inside bounded scout/verify limits.\n");
        }
        Files.writeString(path, out.toString());
    }

    private static void writeControlCandidatesJson(Path path, ControlSearchReport searchReport) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        jsonField(out, 1, "cheap_sample_count", searchReport.cheapSamplesEvaluated(), true);
        jsonField(out, 1, "cheap_shortlist_count", searchReport.shortlist().size(), true);
        jsonField(out, 1, "full_verification_count", searchReport.fullVerifications(), true);
        jsonField(out, 1, "valid_controls_found", searchReport.validControlsFound(), true);
        jsonField(out, 1, "stop_reason", searchReport.stopReason(), true);
        indent(out, 1).append("\"full_verification_rows\": [\n");
        List<CandidateScout> verifiedRows = new ArrayList<>();
        verifiedRows.addAll(searchReport.verifiedAccepted());
        verifiedRows.addAll(searchReport.verifiedRejected());
        for (int i = 0; i < verifiedRows.size(); i++) {
            CandidateScout scout = verifiedRows.get(i);
            indent(out, 2).append("{\n");
            jsonField(out, 3, "label", scout.point().label(), true);
            jsonField(out, 3, "x", scout.x(), true);
            jsonField(out, 3, "z", scout.z(), true);
            jsonField(out, 3, "score", scout.score(), true);
            jsonField(out, 3, "score_reasons", scout.scoreReasons(), true);
            jsonField(out, 3, "base_y0", scout.baseY0Id(), true);
            jsonField(out, 3, "pick_y0", scout.pickY0Id(), true);
            jsonField(out, 3, "predicted_top_y", scout.predictedTopY(), true);
            jsonField(out, 3, "verified_top_y", scout.verifiedTopY(), true);
            jsonField(out, 3, "top_block", scout.verifiedTopBlock(), true);
            jsonField(out, 3, "top_fluid", scout.verifiedTopFluid(), true);
            jsonField(out, 3, "final_biome", scout.verifiedFinalBiome(), true);
            jsonField(out, 3, "physical_class", scout.verifiedPhysicalClass(), true);
            jsonField(out, 3, "result", scout.verificationStatus(), false);
            indent(out, 2).append('}').append(i + 1 < verifiedRows.size() ? "," : "").append('\n');
        }
        indent(out, 1).append("]\n");
        out.append("}\n");
        Files.writeString(path, out.toString());
    }

    private static void appendFullVerificationRow(StringBuilder out, CandidateScout scout) {
        out.append("| ").append(scout.point().label()).append(" | ")
                .append(scout.x()).append(" | ")
                .append(scout.z()).append(" | ")
                .append(scout.score()).append(" | ")
                .append(scout.scoreReasons().isBlank() ? "none" : scout.scoreReasons()).append(" | ")
                .append(scout.baseY0Id()).append(" | ")
                .append(scout.pickY0Id()).append(" | ")
                .append(scout.predictedTopY()).append(" | ")
                .append(scout.verifiedFinalBiome()).append(" | ")
                .append(scout.verifiedTopY()).append(" | ")
                .append(scout.verifiedTopBlock()).append(" | ")
                .append(scout.verifiedTopFluid()).append(" | ")
                .append(scout.verifiedPhysicalClass()).append(" | ")
                .append(scout.verificationStatus()).append(" |\n");
    }

    private static int clampToWorld(ServerWorld world, int y) {
        int min = world.getBottomY();
        int max = world.getBottomY() + world.getHeight() - 1;
        return Math.max(min, Math.min(max, y));
    }

    private static String blockId(BlockState state) {
        Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        return id == null ? "unknown" : id.toString();
    }

    private static String fluidId(FluidState state) {
        Identifier id = net.minecraft.registry.Registries.FLUID.getId(state.getFluid());
        return id == null ? "unknown" : id.toString();
    }

    private static String biomeId(Registry<Biome> biomeRegistry, RegistryEntry<Biome> biome) {
        Identifier id = biomeRegistry.getId(biome.value());
        if (id != null) {
            return id.toString();
        }
        return biome.getKey().map(key -> key.getValue().toString()).orElse("unknown");
    }

    private static boolean isOceanId(String biomeId) {
        return biomeId != null && biomeId.toLowerCase(Locale.ROOT).contains("ocean");
    }

    private static boolean isWaterFluid(FluidState state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        return fluidId(state).toLowerCase(Locale.ROOT).contains("water");
    }

    private static Map<String, String> parsePairs(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String[] parts = raw.split("[;,]");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                out.put(part.trim().toLowerCase(Locale.ROOT), "true");
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static List<ProbePoint> loadMapDerivedCandidates(Path mapCandidatesPath) {
        if (mapCandidatesPath == null || !Files.isRegularFile(mapCandidatesPath)) {
            return List.of();
        }
        try {
            String raw = Files.readString(mapCandidatesPath);
            List<ProbePoint> out = new ArrayList<>();
            Matcher objectMatcher = MAP_CANDIDATE_OBJECT_PATTERN.matcher(raw);
            int index = 1;
            while (objectMatcher.find()) {
                String object = objectMatcher.group();
                Integer x = extractInt(object, MAP_CANDIDATE_X_PATTERN);
                Integer z = extractInt(object, MAP_CANDIDATE_Z_PATTERN);
                if (x == null || z == null) {
                    continue;
                }
                out.add(new ProbePoint("map-derived-candidate-" + index, "map_derived_candidate", x, z));
                index++;
            }
            return out;
        } catch (IOException e) {
            GlobeMod.LOGGER.warn("[latdev][litho-proof] failed to read map candidates path={}", mapCandidatesPath, e);
            return List.of();
        }
    }

    private static Integer extractInt(String raw, Pattern pattern) {
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(String raw) {
        return raw != null && ("true".equalsIgnoreCase(raw)
                || "1".equals(raw)
                || "yes".equalsIgnoreCase(raw)
                || "enabled".equalsIgnoreCase(raw));
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static boolean isFarFromRaisedAnchors(int x, int z, int minDistanceBlocks) {
        long minSq = (long) minDistanceBlocks * minDistanceBlocks;
        for (ProbePoint anchor : RAISED_LAND_ANCHORS) {
            if (distanceSquared(x, z, anchor.x(), anchor.z()) <= minSq) {
                return false;
            }
        }
        return true;
    }

    private static void jsonField(StringBuilder out, int depth, String key, String value, boolean comma) {
        indent(out, depth)
                .append('"').append(jsonEscape(key)).append("\": ")
                .append('"').append(jsonEscape(value)).append('"')
                .append(comma ? "," : "")
                .append('\n');
    }

    private static void jsonField(StringBuilder out, int depth, String key, long value, boolean comma) {
        indent(out, depth)
                .append('"').append(jsonEscape(key)).append("\": ")
                .append(value)
                .append(comma ? "," : "")
                .append('\n');
    }

    private static void jsonField(StringBuilder out, int depth, String key, double value, boolean comma) {
        indent(out, depth)
                .append('"').append(jsonEscape(key)).append("\": ")
                .append(formatDouble(value))
                .append(comma ? "," : "")
                .append('\n');
    }

    private static void jsonField(StringBuilder out, int depth, String key, boolean value, boolean comma) {
        indent(out, depth)
                .append('"').append(jsonEscape(key)).append("\": ")
                .append(value)
                .append(comma ? "," : "")
                .append('\n');
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        return out.append("  ".repeat(Math.max(0, depth)));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record Config(boolean enabled,
                         Path outDir,
                         int radiusBlocks,
                         Path mapCandidatesPath,
                         boolean unsafeSurfaceScoutEnabled) {
    }

    private record ProbePoint(String label, String category, int x, int z) {
        private int chunkX() {
            return Math.floorDiv(x, 16);
        }

        private int chunkZ() {
            return Math.floorDiv(z, 16);
        }

        private ChunkPos chunkPos() {
            return new ChunkPos(chunkX(), chunkZ());
        }

        private ProbePoint withCategory(String category) {
            return new ProbePoint(label, category, x, z);
        }
    }

    private record SearchArea(String label, int centerX, int centerZ, int radius, int step) {
        private List<ProbePoint> candidates() {
            List<ProbePoint> out = new ArrayList<>();
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int dx = -radius; dx <= radius; dx += step) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    out.add(new ProbePoint(label + "-candidate-" + x + "_" + z,
                            "control_search_candidate", x, z));
                }
            }
            out.sort(Comparator
                    .comparingLong((ProbePoint p) -> distanceSquared(p.x(), p.z(), centerX, centerZ))
                    .thenComparingInt(ProbePoint::z)
                    .thenComparingInt(ProbePoint::x));
            return out;
        }

        private String describe() {
            return label + " center=(" + centerX + "," + centerZ + ") radius=" + radius + " step=" + step;
        }
    }

    private record ControlSearchReport(List<SearchArea> areas,
                                       int maxCandidates,
                                       int targetValidControls,
                                       int cheapSamplesEvaluated,
                                       int fullVerifications,
                                       int validControlsFound,
                                       int contradictionCount,
                                       String stopReason,
                                       Set<ChunkPos> requestedChunks,
                                       List<ProbeResult> derivedRows,
                                       List<ProbeResult> contradictionRows,
                                       List<CandidateScout> shortlist,
                                       List<CandidateScout> cheapRejected,
                                       List<CandidateScout> verifiedRejected,
                                       List<CandidateScout> verifiedAccepted,
                                       Map<String, Integer> cheapRejectReasonCounts) {
    }

    private record CandidateScout(ProbePoint point,
                                  int predictedTopY,
                                  String baseY0Id,
                                  String baseSeaYId,
                                  String pickY0Id,
                                  String pickSeaYId,
                                  boolean baseY0Ocean,
                                  boolean baseSeaYOcean,
                                  boolean pickY0Ocean,
                                  boolean pickSeaYOcean,
                                  boolean oceanSignal,
                                  boolean cheapWaterSignal,
                                  boolean lowHeight,
                                  boolean farFromRaisedAnchors,
                                  boolean oceanScoutRegion,
                                  int score,
                                  String scoreReasons,
                                  boolean candidateWorthy,
                                  String rejectionReason,
                                  String verificationStatus,
                                  String verifiedFinalBiome,
                                  String verifiedPhysicalClass,
                                  int verifiedTopY,
                                  String verifiedTopBlock,
                                  String verifiedTopFluid) {
        private int x() {
            return point.x();
        }

        private int z() {
            return point.z();
        }

        private CandidateScout withVerification(String verificationStatus,
                                                String verifiedFinalBiome,
                                                String verifiedPhysicalClass,
                                                int verifiedTopY,
                                                String verifiedTopBlock,
                                                String verifiedTopFluid) {
            return new CandidateScout(point, predictedTopY, baseY0Id, baseSeaYId, pickY0Id, pickSeaYId,
                    baseY0Ocean, baseSeaYOcean, pickY0Ocean, pickSeaYOcean, oceanSignal, cheapWaterSignal, lowHeight,
                    farFromRaisedAnchors, oceanScoutRegion, score, scoreReasons, candidateWorthy, rejectionReason,
                    verificationStatus, verifiedFinalBiome, verifiedPhysicalClass, verifiedTopY, verifiedTopBlock, verifiedTopFluid);
        }
    }

    private static final class ScoutStats {
        private int failNoCheapWaterSignal;
        private int failOceanSignal;
        private int failHeight;
        private int failScore;
        private int singleSignalOnly;
        private int oceanSignalButHeightFail;
        private int oceanSignalAndHeightPassButLowScore;

        private void count(CandidateScout scout) {
            if (!scout.cheapWaterSignal()) {
                failNoCheapWaterSignal++;
            }
            if (!scout.oceanSignal()) {
                failOceanSignal++;
            }
            if (!scout.lowHeight()) {
                failHeight++;
            }
            if (!scout.candidateWorthy()) {
                failScore++;
            }
            int signalCount = 0;
            if (scout.baseY0Ocean()) signalCount++;
            if (scout.pickY0Ocean()) signalCount++;
            if (scout.baseSeaYOcean()) signalCount++;
            if (scout.pickSeaYOcean()) signalCount++;
            if (signalCount == 1) {
                singleSignalOnly++;
            }
            if (scout.oceanSignal() && !scout.lowHeight()) {
                oceanSignalButHeightFail++;
            }
            if (scout.oceanSignal() && scout.lowHeight() && !scout.candidateWorthy()) {
                oceanSignalAndHeightPassButLowScore++;
            }
        }

        private Map<String, Integer> toMap() {
            Map<String, Integer> out = new LinkedHashMap<>();
            out.put("fail_no_cheap_water_signal", failNoCheapWaterSignal);
            out.put("fail_no_ocean_signal", failOceanSignal);
            out.put("fail_predicted_height_above_margin", failHeight);
            out.put("fail_score_threshold", failScore);
            out.put("single_ocean_signal_only", singleSignalOnly);
            out.put("ocean_signal_but_height_fail", oceanSignalButHeightFail);
            out.put("ocean_signal_and_height_pass_but_low_score", oceanSignalAndHeightPassButLowScore);
            return out;
        }
    }

    private record NoiseValues(double continentalness, double erosion, double weirdness) {
    }

    private record ProofClassifications(boolean raisedLandMismatchProven,
                                        boolean validTrueOceanControlsProven,
                                        boolean trueOceanPreservationBaselineKnown,
                                        boolean cheapPreBiomeSignalFound,
                                        boolean productionFixSafeToAttemptNext) {
    }

    private record ProbeResult(ProbePoint point,
                               boolean lithosphereLoaded,
                               long worldSeed,
                               int radius,
                               int seaLevel,
                               int worldSurfaceTopY,
                               int worldSurfaceWgTopY,
                               int motionBlockingTopY,
                               int oceanFloorTopY,
                               int topBlockY,
                               String topBlockId,
                               String topFluidId,
                               boolean topSolid,
                               boolean topFluidPresent,
                               String seaBlockId,
                               String seaFluidId,
                               boolean seaFluidPresent,
                               String baseY0Id,
                               boolean baseY0Ocean,
                               String baseSeaYId,
                               boolean baseSeaYOcean,
                               String baseSurfaceClassifyYId,
                               boolean baseSurfaceClassifyYOcean,
                               String baseCallerYId,
                               boolean baseCallerYOcean,
                               String pickY0Id,
                               String pickSeaYId,
                               String pickSurfaceClassifyYId,
                               String pickCallerYId,
                               String finalBiomeId,
                               String biomeAtSurfaceId,
                               String biomeAtSampleYId,
                               NoiseValues noiseValues,
                               String physicalClass,
                               boolean earlyOceanReturnInferred,
                               boolean fixedYSignalVisible,
                               String guardJustification) {
    }

    private static long distanceSquared(int x, int z, int centerX, int centerZ) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz;
    }
}
