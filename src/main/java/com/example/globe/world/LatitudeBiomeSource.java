package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.mixin.BiomeSourceAccessor;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class LatitudeBiomeSource extends BiomeSource {
    private static final int MAX_CAVE_BIOME_Y = Integer.getInteger("latitude.maxCaveBiomeY", 96);
    private static final int HARD_DECK_SURFACE_Y = Integer.getInteger("latitude.hardDeckSurfaceY", 20);
    private static final int DEEP_DARK_MAX_Y = -16;
    private static final Identifier LUSH_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "lush_caves");
    private static final Identifier DRIPSTONE_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "dripstone_caves");
    private static final Identifier DEEP_DARK_ID = Identifier.fromNamespaceAndPath("minecraft", "deep_dark");
    private static final Identifier SULFUR_CAVES_ID = Identifier.fromNamespaceAndPath("minecraft", "sulfur_caves");

    private final BiomeSource original;
    private final Collection<Holder<Biome>> biomes;
    private final Registry<Biome> biomeRegistry;
    private final int borderRadiusBlocks;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState noiseConfig;
    private final LevelHeightAccessor heightView;
    private final String callerContext;

    public LatitudeBiomeSource(BiomeSource original, Collection<Holder<Biome>> biomes, int borderRadiusBlocks) {
        this(original, biomes, null, borderRadiusBlocks, null, null, null, "SOURCE");
    }

    public static LatitudeBiomeSource forLocate(BiomeSource original,
                                                Registry<Biome> biomeRegistry,
                                                int borderRadiusBlocks,
                                                NoiseBasedChunkGenerator generator,
                                                RandomState noiseConfig,
                                                LevelHeightAccessor heightView) {
        return new LatitudeBiomeSource(original, original.possibleBiomes(), biomeRegistry,
                borderRadiusBlocks, generator, noiseConfig, heightView, "MIXIN");
    }

    /**
     * Gives vanilla structure starts the same resolved surface-biome view that populates Latitude
     * terrain. Structure starts must perform their legal-biome check before a start is saved.
     */
    public static LatitudeBiomeSource forStructure(BiomeSource original,
                                                   Registry<Biome> biomeRegistry,
                                                   int borderRadiusBlocks,
                                                   NoiseBasedChunkGenerator generator,
                                                   RandomState noiseConfig,
                                                   LevelHeightAccessor heightView) {
        BiomeSource base = original instanceof LatitudeBiomeSource latitudeSource
                ? latitudeSource.original()
                : original;
        return new LatitudeBiomeSource(base, base.possibleBiomes(), biomeRegistry,
                borderRadiusBlocks, generator, noiseConfig, heightView, "MIXIN");
    }

    private LatitudeBiomeSource(BiomeSource original,
                                Collection<Holder<Biome>> biomes,
                                Registry<Biome> biomeRegistry,
                                int borderRadiusBlocks,
                                NoiseBasedChunkGenerator generator,
                                RandomState noiseConfig,
                                LevelHeightAccessor heightView,
                                String callerContext) {
        this.original = original;
        this.biomes = biomes;
        this.biomeRegistry = biomeRegistry;
        this.borderRadiusBlocks = borderRadiusBlocks;
        this.generator = generator;
        this.noiseConfig = noiseConfig;
        this.heightView = heightView;
        this.callerContext = callerContext == null || callerContext.isBlank() ? "SOURCE" : callerContext;
    }

    public BiomeSource original() {
        return original;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BiomeSource> codec() {
        @SuppressWarnings("unchecked")
        MapCodec<BiomeSource> delegate = (MapCodec<BiomeSource>) ((BiomeSourceAccessor) original).globe$invokeCodec();
        return new MapCodec<>() {
            @Override
            public <T> RecordBuilder<T> encode(BiomeSource input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                BiomeSource toEncode = input instanceof LatitudeBiomeSource wrapper ? wrapper.original : input;
                return delegate.encode(toEncode, ops, prefix);
            }

            @Override
            public <T> DataResult<BiomeSource> decode(DynamicOps<T> ops, MapLike<T> input) {
                return delegate.decode(ops, input);
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }
        };
    }

    /**
     * Locate-time candidate pool ({@code biomeRegistry != null}, see {@link #forLocate}) unions in
     * every custom biome Latitude can paint. TerraBlender does not inject into {@code globe:} noise
     * settings, so {@code original.possibleBiomes()} structurally excludes ALL custom biomes —
     * tag-routed and ledger-routed alike (measured live on the 26.2 provider stack: 0 of 42
     * registered ledger biomes present in {@code possibleBiomes()}). Real generation never
     * calls this path for biome selection ({@code ChunkGeneratorPopulateBiomesMixin} resolves
     * against the full registry directly), so leaving it unexpanded when {@code biomeRegistry ==
     * null} changes nothing about what generates — only {@code /locate biome}'s candidate-membership
     * gate, which previously declared every custom biome unfindable before any search ran.
     */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Collection<Holder<Biome>> base = LatitudeBiomes.expandSourceCandidatePool(original.possibleBiomes());
        if (biomeRegistry == null) {
            return base.stream();
        }
        java.util.Map<Identifier, Holder<Biome>> union = new java.util.LinkedHashMap<>();
        for (Holder<Biome> holder : base) {
            holder.unwrapKey().ifPresent(key -> union.putIfAbsent(key.identifier(), holder));
        }
        for (Holder<Biome> holder : LatitudePaintableCustomBiomes.allPaintableCustomBiomes(biomeRegistry)) {
            holder.unwrapKey().ifPresent(key -> union.putIfAbsent(key.identifier(), holder));
        }
        return union.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> current = original.getNoiseBiome(x, y, z, sampler);
        Holder<Biome> base = original.getNoiseBiome(x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
        // The populate-biomes resolver classifies the center of each quart cell. Locate's
        // terrain-aware wrapper must use that same point or it can report the neighbouring
        // biome at a boundary even though the returned Holder passed the predicate.
        int quartCenterOffset = "MIXIN".equalsIgnoreCase(callerContext) ? 2 : 0;
        int blockX = (x << 2) + quartCenterOffset;
        int blockZ = (z << 2) + quartCenterOffset;
        int blockY = (y << 2) + quartCenterOffset;
        if (shouldPreserveCave(current, base, blockY)) {
            if (biomeRegistry != null) {
                return LatitudeBiomes.caveCoverageOverride(biomeRegistry, current, blockX, blockY, blockZ);
            }
            return LatitudeBiomes.caveCoverageOverride(biomes, current, blockX, blockY, blockZ);
        }
        if (biomeRegistry != null) {
            return LatitudeBiomes.pick(biomeRegistry, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler,
                    callerContext, generator, noiseConfig, heightView);
        }
        Collection<Holder<Biome>> sourceCandidates = LatitudeBiomes.expandSourceCandidatePool(biomes);
        return LatitudeBiomes.pick(sourceCandidates, base, blockX, blockZ, blockY, borderRadiusBlocks, sampler,
                callerContext, generator, noiseConfig, heightView);
    }

    /**
     * Second, terrain-free broad phase for the tick-sliced wetland locator.
     *
     * <p>This deliberately uses the registry picker, exact quart center, active generator sea
     * level, and the same base holder as the final MIXIN resolver. Terrain inputs remain absent,
     * so no base-height query occurs. Shoreline early returns fail open because real terrain can
     * legitimately route those positions back through land selection.
     */
    boolean isPotentialWetlandLocateSourceCandidate(
            int x,
            int y,
            int z,
            Climate.Sampler sampler) {
        if (biomeRegistry == null) {
            return true;
        }
        Holder<Biome> current = original.getNoiseBiome(x, y, z, sampler);
        Holder<Biome> base = original.getNoiseBiome(
                x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
        int blockX = (x << 2) + 2;
        int blockZ = (z << 2) + 2;
        int blockY = (y << 2) + 2;
        Holder<Biome> preview;
        if (shouldPreserveCave(current, base, blockY)) {
            preview = current;
        } else {
            preview = LatitudeBiomes.pick(
                    biomeRegistry,
                    base,
                    blockX,
                    blockZ,
                    blockY,
                    borderRadiusBlocks,
                    sampler,
                    "SOURCE",
                    generator,
                    null,
                    null);
        }
        return LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland(
                LatitudeBiomes.isBiomeIdPublic(preview, "minecraft:swamp"),
                LatitudeBiomes.isBiomeIdPublic(preview, "minecraft:mangrove_swamp"),
                preview.is(BiomeTags.IS_OCEAN),
                preview.is(BiomeTags.IS_RIVER),
                isBeachBiome(preview),
                base.is(BiomeTags.IS_OCEAN)
                        || base.is(BiomeTags.IS_RIVER)
                        || isBeachBiome(base)
                        || LatitudeBiomes.hasWetlandLocateOceanAuthority(
                                blockX,
                                blockZ,
                                sampler));
    }

    /**
     * Vanilla's 6,400-block locate command performs about one million synchronous biome
     * classifications (401 x 401 horizontal points times the Overworld's vertical layers).
     * Latitude's classifier is intentionally richer than vanilla's, so that exhaustive loop
     * can monopolize the integrated-server thread for minutes. Keep vanilla's nearest-first
     * spiral and exact predicate, but place a seed-independent upper bound on its work.
     */
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            BlockPos origin,
            int radius,
            int horizontalStep,
            int verticalStep,
            Predicate<Holder<Biome>> target,
            Climate.Sampler sampler,
            LevelReader level) {
        Set<Holder<Biome>> matching = possibleBiomes().stream()
                .filter(target)
                .collect(Collectors.toUnmodifiableSet());
        if (matching.isEmpty()) {
            return null;
        }

        boolean hasCaveTarget = matching.stream().anyMatch(LatitudeBiomeSource::isCaveBiome);
        boolean hasSurfaceTarget = matching.stream().anyMatch(candidate -> !isCaveBiome(candidate));
        int safeRadius = Math.max(0, radius);
        int safeHorizontalStep = Math.max(1, horizontalStep);
        int safeVerticalStep = Math.max(1, verticalStep);
        int boundedHorizontalStep;
        int verticalSamples;
        BlockPos boundedOrigin = origin;
        int boundedVerticalStep = safeVerticalStep;

        if (hasSurfaceTarget && !hasCaveTarget) {
            int surfaceY = Mth.clamp(
                    LatitudeBiomes.SURFACE_CLASSIFY_Y + 4,
                    level.getMinY() + 1,
                    level.getMaxY());
            boundedOrigin = new BlockPos(origin.getX(), surfaceY, origin.getZ());
            boundedHorizontalStep = LatitudeLocateBudgetPolicy.surfaceHorizontalStep(
                    safeRadius, safeHorizontalStep);
            // Larger than the complete build-height span: Mth.outFromOrigin emits one Y.
            boundedVerticalStep = Math.max(1, level.getMaxY() - level.getMinY() + 2);
            verticalSamples = 1;
            long started = System.nanoTime();
            boolean targetIncludesMangrove = matching.stream().anyMatch(candidate ->
                    LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp"));
            boolean wetlandOnlyTarget = matching.stream().allMatch(candidate ->
                    LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp")
                            || LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:swamp"));
            SurfaceLocateOutcome outcome = findClosestSurfaceBiome(
                    boundedOrigin,
                    safeRadius,
                    safeHorizontalStep,
                    boundedHorizontalStep,
                    boundedVerticalStep,
                    matching,
                    target,
                    targetIncludesMangrove,
                    wetlandOnlyTarget,
                    sampler,
                    level);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
            int previewWorstCase = LatitudeLocateBudgetPolicy.worstCaseSamples(
                    safeRadius, boundedHorizontalStep, 1);
            GlobeMod.LOGGER.info(
                    "[Latitude] bounded biome locate surfaceOnly=true radius={} previewStep={} previewProbes={} previewWorstCase={} exactCandidateChecks={} exactCandidateLimit={} fallbackUsed={} plannedFallbackUsed={} fallbackWorstCase={} elapsedMs={} found={}",
                    safeRadius,
                    boundedHorizontalStep,
                    outcome.previewProbes(),
                    previewWorstCase,
                    outcome.exactCandidateChecks(),
                    LatitudeLocateBudgetPolicy.MAX_SURFACE_EXACT_VERIFICATIONS,
                    outcome.fallbackUsed(),
                    outcome.plannedFallbackUsed(),
                    outcome.fallbackWorstCaseSamples(),
                    elapsedMillis,
                    outcome.result() != null);
            return outcome.result();
        } else {
            verticalSamples = (int) Mth.outFromOrigin(
                    origin.getY(),
                    level.getMinY() + 1,
                    level.getMaxY() + 1,
                    safeVerticalStep).count();
            verticalSamples = Math.max(1, verticalSamples);
            boundedHorizontalStep = LatitudeLocateBudgetPolicy.threeDimensionalHorizontalStep(
                    safeRadius, safeHorizontalStep, verticalSamples);
        }

        int worstCaseSamples = LatitudeLocateBudgetPolicy.worstCaseSamples(
                safeRadius, boundedHorizontalStep, verticalSamples);
        long started = System.nanoTime();
        Pair<BlockPos, Holder<Biome>> result = super.findClosestBiome3d(
                boundedOrigin,
                safeRadius,
                boundedHorizontalStep,
                boundedVerticalStep,
                target,
                sampler,
                level);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        GlobeMod.LOGGER.info(
                "[Latitude] bounded biome locate surfaceOnly={} radius={} horizontalStep={} verticalSamples={} worstCaseSamples={} elapsedMs={} found={}",
                hasSurfaceTarget && !hasCaveTarget,
                safeRadius,
                boundedHorizontalStep,
                verticalSamples,
                worstCaseSamples,
                elapsedMillis,
                result != null);
        return centerQuartResult(result);
    }

    private SurfaceLocateOutcome findClosestSurfaceBiome(
            BlockPos origin,
            int radius,
            int requestedHorizontalStep,
            int previewHorizontalStep,
            int oneLayerVerticalStep,
            Set<Holder<Biome>> matching,
            Predicate<Holder<Biome>> target,
            boolean targetIncludesMangrove,
            boolean wetlandOnlyTarget,
            Climate.Sampler sampler,
            LevelReader level) {
        int rings = radius / Math.max(1, previewHorizontalStep);
        int quartY = QuartPos.fromBlock(origin.getY());
        int previewProbes = 0;
        int exactCandidateChecks = 0;

        for (BlockPos.MutableBlockPos offset : BlockPos.spiralAround(
                BlockPos.ZERO, rings, Direction.EAST, Direction.SOUTH)) {
            int sampleX = origin.getX() + offset.getX() * previewHorizontalStep;
            int sampleZ = origin.getZ() + offset.getZ() * previewHorizontalStep;
            int quartX = QuartPos.fromBlock(sampleX);
            int quartZ = QuartPos.fromBlock(sampleZ);
            Holder<Biome> preview = getLocatePreviewNoiseBiome(quartX, quartY, quartZ, sampler);
            previewProbes++;

            // Live mangroves are the terrain-validated coastal form of a SOURCE swamp,
            // so swamp is a necessary broad-phase proxy for a mangrove request.
            boolean plausible = target.test(preview)
                    || (targetIncludesMangrove
                    && LatitudeBiomes.isBiomeIdPublic(preview, "minecraft:swamp"));
            if (!plausible) {
                continue;
            }
            if (exactCandidateChecks >= LatitudeLocateBudgetPolicy.MAX_SURFACE_EXACT_VERIFICATIONS) {
                break;
            }

            exactCandidateChecks++;
            Holder<Biome> exact = getNoiseBiome(quartX, quartY, quartZ, sampler);
            if (target.test(exact)) {
                BlockPos located = centerQuartPosition(new BlockPos(
                        QuartPos.toBlock(quartX) + 2,
                        origin.getY(),
                        QuartPos.toBlock(quartZ) + 2));
                return new SurfaceLocateOutcome(
                        Pair.of(located, exact), previewProbes, exactCandidateChecks,
                        false, false, 0);
            }
        }

        if (wetlandOnlyTarget) {
            // SOURCE swamp/mangrove output is a superset of the live terrain-validated
            // wetlands. If every broad-phase candidate failed the exact check, a second
            // terrain sweep cannot reveal another wetland family and only adds a stall.
            return new SurfaceLocateOutcome(
                    null, previewProbes, exactCandidateChecks, false, false, 0);
        }

        // Terrain-derived biomes may not appear in the cheap SOURCE broad phase. Give those
        // requests a much smaller exact spiral rather than silently making them unlocatable.
        int fallbackHorizontalStep = LatitudeLocateBudgetPolicy.surfaceExactFallbackHorizontalStep(
                radius, requestedHorizontalStep);
        int fallbackWorstCase = LatitudeLocateBudgetPolicy.worstCaseSamples(
                radius, fallbackHorizontalStep, 1);
        Pair<BlockPos, Holder<Biome>> fallback = centerQuartResult(super.findClosestBiome3d(
                origin,
                radius,
                fallbackHorizontalStep,
                oneLayerVerticalStep,
                target,
                sampler,
                level));
        Pair<BlockPos, Holder<Biome>> plannedFallback = fallback == null
                ? findPlannedSurfaceCoverage(matching, origin, target, sampler)
                : null;
        return new SurfaceLocateOutcome(
                fallback != null ? fallback : plannedFallback,
                previewProbes,
                exactCandidateChecks,
                true,
                plannedFallback != null,
                fallbackWorstCase);
    }

    /** Resolves reserved land first, then preserves the existing surface/water fallback. */
    Pair<BlockPos, Holder<Biome>> findPlannedSurfaceCoverage(
            Set<Holder<Biome>> matching,
            BlockPos origin,
            Predicate<Holder<Biome>> target,
            Climate.Sampler sampler) {
        Pair<BlockPos, Holder<Biome>> land = findPlannedLandCoverage(
                matching, origin, target, sampler);
        return land != null
                ? land
                : findPlannedSurfaceWaterCoverage(matching, origin, target, sampler);
    }

    private Pair<BlockPos, Holder<Biome>> findPlannedLandCoverage(
            Set<Holder<Biome>> matching,
            BlockPos origin,
            Predicate<Holder<Biome>> target,
            Climate.Sampler sampler) {
        Set<String> requestedIds = matching.stream()
                .map(LatitudeBiomes::biomeIdPublic)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        VanillaBiomeCoveragePlan.Anchor anchor =
                LatitudeBiomes.nearestPlannedLandCoverageAnchor(
                        requestedIds, origin.getX(), origin.getZ());
        if (anchor == null) return null;
        for (BlockPos sample : plannedLandCoverageSamplePositions(anchor, origin)) {
            int quartX = QuartPos.fromBlock(sample.getX());
            int quartZ = QuartPos.fromBlock(sample.getZ());
            Holder<Biome> exact = getNoiseBiome(
                    quartX,
                    QuartPos.fromBlock(origin.getY()),
                    quartZ,
                    sampler);
            if (target.test(exact)
                    && anchor.biomeId().equals(LatitudeBiomes.biomeIdPublic(exact))) {
                return Pair.of(centerQuartPosition(sample), exact);
            }
        }
        GlobeMod.LOGGER.info("[Latitude] planned land locate target={} anchor={} route={} had no final surviving center-or-shoulder sample",
                requestedIds, anchor.biomeId(), anchor.route());
        return null;
    }

    /**
     * The coverage planner proves an anchor only at its centre and four half-radius shoulders.
     * Locate must query those same final-output samples: later terrain authority can correctly
     * rewrite the centre without invalidating a still-visible shoulder in the reserved province.
     */
    static List<BlockPos> plannedLandCoverageSamplePositions(
            VanillaBiomeCoveragePlan.Anchor anchor, BlockPos origin) {
        int halfRadius = anchor.radiusBlocks() / 2;
        List<BlockPos> samples = new ArrayList<>(5);
        samples.add(new BlockPos(anchor.blockX(), origin.getY(), anchor.blockZ()));
        samples.add(new BlockPos(anchor.blockX() + halfRadius, origin.getY(), anchor.blockZ()));
        samples.add(new BlockPos(anchor.blockX() - halfRadius, origin.getY(), anchor.blockZ()));
        samples.add(new BlockPos(anchor.blockX(), origin.getY(), anchor.blockZ() + halfRadius));
        samples.add(new BlockPos(anchor.blockX(), origin.getY(), anchor.blockZ() - halfRadius));
        samples.sort(Comparator.comparingLong(sample -> horizontalDistanceSquared(sample, origin)));
        return List.copyOf(samples);
    }

    private static long horizontalDistanceSquared(BlockPos left, BlockPos right) {
        long dx = (long) left.getX() - right.getX();
        long dz = (long) left.getZ() - right.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Resolves the final planned-water fallback used by bounded surface-biome locate searches.
     * The tick-sliced command worker calls this only after its finite direct probes miss.
     */
    Pair<BlockPos, Holder<Biome>> findPlannedSurfaceWaterCoverage(
            Set<Holder<Biome>> matching,
            BlockPos origin,
            Predicate<Holder<Biome>> target,
            Climate.Sampler sampler) {
        Set<String> requestedIds = matching.stream()
                .map(LatitudeBiomes::biomeIdPublic)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        VanillaSurfaceWaterCoveragePlan.Anchor anchor =
                LatitudeBiomes.nearestPlannedSurfaceWaterCoverageAnchor(
                        requestedIds, origin.getX(), origin.getZ());
        if (anchor == null) return null;
        int quartX = QuartPos.fromBlock(anchor.blockX());
        int quartZ = QuartPos.fromBlock(anchor.blockZ());
        Holder<Biome> exact = getNoiseBiome(
                quartX,
                QuartPos.fromBlock(origin.getY()),
                quartZ,
                sampler);
        if (!target.test(exact)
                || !anchor.biomeId().equals(LatitudeBiomes.biomeIdPublic(exact))) {
            return null;
        }
        return Pair.of(centerQuartPosition(
                new BlockPos(anchor.blockX(), origin.getY(), anchor.blockZ())), exact);
    }

    static BlockPos centerQuartPosition(BlockPos position) {
        return new BlockPos(
                LatitudeLocateBudgetPolicy.quartCenterBlock(position.getX()),
                LatitudeLocateBudgetPolicy.quartCenterBlock(position.getY()),
                LatitudeLocateBudgetPolicy.quartCenterBlock(position.getZ()));
    }

    private static Pair<BlockPos, Holder<Biome>> centerQuartResult(
            Pair<BlockPos, Holder<Biome>> result) {
        return result == null ? null
                : Pair.of(centerQuartPosition(result.getFirst()), result.getSecond());
    }

    /**
     * Terrain-free broad-phase preview for the tick-sliced surface-biome locate worker.
     * A preview is never reported as a locate result; callers must verify it with
     * {@link #getNoiseBiome(int, int, int, Climate.Sampler)}.
     */
    Holder<Biome> getLocatePreviewNoiseBiome(
            int x,
            int y,
            int z,
            Climate.Sampler sampler) {
        Holder<Biome> current = original.getNoiseBiome(x, y, z, sampler);
        Holder<Biome> base = original.getNoiseBiome(
                x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
        int blockX = x << 2;
        int blockZ = z << 2;
        int blockY = y << 2;
        if (shouldPreserveCave(current, base, blockY)) {
            return LatitudeBiomes.caveCoverageOverride(biomes, current, blockX, blockY, blockZ);
        }
        // Locate's candidate set already includes the registry-resolved Latitude roster. Use the
        // same existing registry picker here so an admitted custom surface biome can reach the
        // terrain-aware exact check; the donor source's possible-biome collection omits provider
        // biomes by construction.
        if (biomeRegistry != null) {
            return LatitudeBiomes.pick(
                    biomeRegistry,
                    base,
                    blockX,
                    blockZ,
                    blockY,
                    borderRadiusBlocks,
                    sampler,
                    "SOURCE",
                    null,
                    null,
                    null);
        }
        Collection<Holder<Biome>> sourceCandidates = LatitudeBiomes.expandSourceCandidatePool(biomes);
        return LatitudeBiomes.pick(
                sourceCandidates,
                base,
                blockX,
                blockZ,
                blockY,
                borderRadiusBlocks,
                sampler,
                "SOURCE",
                null,
                null,
                null);
    }

    private record SurfaceLocateOutcome(
            Pair<BlockPos, Holder<Biome>> result,
            int previewProbes,
            int exactCandidateChecks,
            boolean fallbackUsed,
            boolean plannedFallbackUsed,
            int fallbackWorstCaseSamples) {
    }

    private static boolean shouldPreserveCave(Holder<Biome> current, Holder<Biome> surfaceBase, int blockY) {
        if (!isCaveBiome(current)) {
            return false;
        }
        if (blockY > MAX_CAVE_BIOME_Y) {
            return false;
        }
        if (isDeepDark(current) && blockY > DEEP_DARK_MAX_Y) {
            return false;
        }
        if (blockY > HARD_DECK_SURFACE_Y && isCaveBiome(surfaceBase)) {
            return false;
        }
        return true;
    }

    static boolean isCaveBiome(Holder<Biome> entry) {
        if (entry.is(ConventionalBiomeTags.IS_CAVE)
                || entry.is(ConventionalBiomeTags.IS_UNDERGROUND)) {
            return true;
        }
        Identifier id = biomeId(entry);
        if (id == null) {
            return false;
        }
        return id.equals(LUSH_CAVES_ID)
                || id.equals(DRIPSTONE_CAVES_ID)
                || id.equals(DEEP_DARK_ID)
                || id.equals(SULFUR_CAVES_ID);
    }

    private static boolean isDeepDark(Holder<Biome> entry) {
        Identifier id = biomeId(entry);
        return id != null && id.equals(DEEP_DARK_ID);
    }

    private static boolean isBeachBiome(Holder<Biome> entry) {
        if (entry.is(BiomeTags.IS_BEACH)) {
            return true;
        }
        Identifier id = biomeId(entry);
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.contains("beach") || path.contains("shore");
    }

    private static Identifier biomeId(Holder<Biome> entry) {
        if (entry == null) {
            return null;
        }
        return entry.unwrapKey().map(key -> key.identifier()).orElse(null);
    }
}
