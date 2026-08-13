package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Runs Latitude biome locate searches in bounded server-tick slices with player progress. */
public final class LatitudeBiomeLocateService {
    private static final int COMMAND_RADIUS = 6_400;
    private static final int COMMAND_HORIZONTAL_STEP = 32;
    private static final int COMMAND_VERTICAL_STEP = 64;
    private static final long PROGRESS_UPDATE_NANOS = 250_000_000L;

    private static final Map<MinecraftServer, BiomeLocateJob> ACTIVE_JOBS =
            new IdentityHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(LatitudeBiomeLocateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> cancel(server, null));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                cancel(server, handler.player));
    }

    private LatitudeBiomeLocateService() {
    }

    /**
     * Claims supported biome locate commands in Latitude worlds. The search covers the playable
     * Latitude border from the command origin and distributes that finite work across ticks, so
     * the requester sees a boss bar instead of a stalled client or intermittent chat acknowledgement.
     */
    public static boolean beginIfLatitudeBiome(
            CommandSourceStack source,
            ResourceOrTagArgument.Result<Biome> target) {
        ServerLevel level = source.getLevel();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator generator)
                || !GlobeMod.shouldApplyLatitudeWorldgen(generator)) {
            return false;
        }

        BiomeSource rawSource = generator.getBiomeSource();
        if (rawSource instanceof LatitudeBiomeSource latitudeSource) {
            rawSource = latitudeSource.original();
        }
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        int worldRadius = GlobeMod.borderRadiusForNoiseGenerator(generator);
        RandomState randomState = level.getChunkSource().randomState();
        LatitudeBiomeSource latitudeSource = LatitudeBiomeSource.forLocate(
                rawSource, registry, worldRadius, generator, randomState, level);
        Set<Holder<Biome>> matching = latitudeSource.possibleBiomes().stream()
                .filter(target)
                .collect(Collectors.toUnmodifiableSet());
        if (matching.isEmpty()) {
            return false;
        }

        MinecraftServer server = source.getServer();
        if (ACTIVE_JOBS.containsKey(server)) {
            source.sendFailure(Component.literal(
                    "A Latitude biome search is already running. Its result will appear in chat."));
            return true;
        }

        BlockPos origin = BlockPos.containing(source.getPosition());
        int searchRadius = Math.max(COMMAND_RADIUS,
                LatitudeLocateBudgetPolicy.fullWorldSearchRadius(
                        origin.getX(), origin.getZ(), worldRadius));
        boolean includesSwamp = matching.stream().anyMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:swamp"));
        boolean includesMangrove = matching.stream().anyMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp"));
        boolean wetlandOnly = matching.stream().allMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:swamp")
                        || LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp"));
        boolean includesCave = matching.stream().anyMatch(LatitudeBiomeSource::isCaveBiome);

        BiomeLocateJob job;
        if (!includesCave && wetlandOnly && (includesSwamp || includesMangrove)) {
            job = new WetlandLocateJob(source, target, origin, latitudeSource, worldRadius,
                    searchRadius, randomState.sampler(), includesSwamp, includesMangrove);
        } else if (!includesCave) {
            job = new SurfaceLocateJob(source, target, origin, latitudeSource, worldRadius,
                    searchRadius, randomState.sampler(), matching, includesMangrove);
        } else {
            job = new ThreeDimensionalLocateJob(source, target, origin, latitudeSource, worldRadius,
                    searchRadius, randomState.sampler(), level);
        }
        ACTIVE_JOBS.put(server, job);
        GlobeMod.LOGGER.info("[Latitude] started tick-sliced biome locate target={} route={} origin={} radius={} worldRadius={}",
                target.asPrintable(), job.route(), origin.toShortString(), searchRadius, worldRadius);
        return true;
    }

    private static void tick(MinecraftServer server) {
        BiomeLocateJob job = ACTIVE_JOBS.get(server);
        if (job == null) {
            return;
        }
        try {
            if (job.runTick()) {
                ACTIVE_JOBS.remove(server);
            }
        } catch (Throwable failure) {
            ACTIVE_JOBS.remove(server);
            GlobeMod.LOGGER.error("[Latitude] tick-sliced biome locate failed", failure);
            job.fail(failure);
        }
    }

    private static void cancel(MinecraftServer server, ServerPlayer disconnectedPlayer) {
        BiomeLocateJob job = ACTIVE_JOBS.get(server);
        if (job == null || (disconnectedPlayer != null && !job.belongsTo(disconnectedPlayer))) {
            return;
        }
        ACTIVE_JOBS.remove(server);
        job.cancel();
    }

    private abstract static class BiomeLocateJob {
        protected final CommandSourceStack source;
        protected final ResourceOrTagArgument.Result<Biome> target;
        protected final BlockPos origin;
        protected final LatitudeBiomeSource latitudeSource;
        protected final Climate.Sampler sampler;
        protected final int worldRadius;
        protected final int searchRadius;
        private final ServerPlayer requester;
        private final long startedNanos;
        private final ServerBossEvent bossBar;
        private long nextProgressNanos;
        private boolean finished;

        private BiomeLocateJob(
                CommandSourceStack source,
                ResourceOrTagArgument.Result<Biome> target,
                BlockPos origin,
                LatitudeBiomeSource latitudeSource,
                int worldRadius,
                int searchRadius,
                Climate.Sampler sampler) {
            this.source = source;
            this.target = target;
            this.origin = origin;
            this.latitudeSource = latitudeSource;
            this.worldRadius = worldRadius;
            this.searchRadius = searchRadius;
            this.sampler = sampler;
            this.requester = source.getPlayer();
            this.startedNanos = System.nanoTime();
            this.nextProgressNanos = startedNanos;
            this.bossBar = new ServerBossEvent(
                    java.util.UUID.randomUUID(),
                    Component.literal("Searching for " + target.asPrintable() + "..."),
                    BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setProgress(0.0F);
            if (requester != null) {
                this.bossBar.addPlayer(requester);
            }
        }

        abstract boolean runTick();

        abstract String route();

        final boolean belongsTo(ServerPlayer player) {
            return requester == player;
        }

        final boolean deadlineExceeded(long tickStarted) {
            return System.nanoTime() - tickStarted
                    >= LatitudeLocateBudgetPolicy.MAX_BIOME_LOCATE_TICK_NANOS;
        }

        final boolean isWithinLatitudeWorld(int blockX, int blockZ) {
            return Math.abs((long) blockX) <= worldRadius
                    && Math.abs((long) blockZ) <= worldRadius;
        }

        final void updateProgress(int completed, int total) {
            long now = System.nanoTime();
            if (now < nextProgressNanos) {
                return;
            }
            int percent = total <= 0 ? 0 : Mth.clamp(
                    (int) ((long) completed * 100L / total), 0, 99);
            bossBar.setProgress(percent / 100.0F);
            bossBar.setName(Component.literal(
                    "Searching for " + target.asPrintable() + "... " + percent + "%"));
            nextProgressNanos = now + PROGRESS_UPDATE_NANOS;
        }

        final void finish(Pair<BlockPos, Holder<Biome>> result) {
            if (finished) {
                return;
            }
            finished = true;
            bossBar.removeAllPlayers();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
            if (result == null) {
                source.sendFailure(Component.translatableEscape(
                        "commands.locate.biome.not_found", target.asPrintable()));
            } else {
                LocateCommand.showLocateResult(source, target, origin, result,
                        "commands.locate.biome.success", true, elapsed);
            }
        }

        final void cancel() {
            if (finished) {
                return;
            }
            finished = true;
            bossBar.removeAllPlayers();
        }

        final void fail(Throwable failure) {
            if (finished) {
                return;
            }
            finished = true;
            bossBar.removeAllPlayers();
            source.sendFailure(Component.literal(
                    "Latitude biome search stopped safely: " + failure.getClass().getSimpleName()));
        }
    }

    /** Preserves the exact wetland broad phase because terrain can promote source shorelines. */
    private static final class WetlandLocateJob extends BiomeLocateJob {
        private final boolean includesSwamp;
        private final boolean includesMangrove;
        private final Iterator<BlockPos.MutableBlockPos> offsets;
        private final int surfaceY;
        private int gridProbes;
        private int exactProbes;

        private WetlandLocateJob(
                CommandSourceStack source,
                ResourceOrTagArgument.Result<Biome> target,
                BlockPos origin,
                LatitudeBiomeSource latitudeSource,
                int worldRadius,
                int searchRadius,
                Climate.Sampler sampler,
                boolean includesSwamp,
                boolean includesMangrove) {
            super(source, target, origin, latitudeSource, worldRadius, searchRadius, sampler);
            this.includesSwamp = includesSwamp;
            this.includesMangrove = includesMangrove;
            this.offsets = BlockPos.spiralAround(
                    BlockPos.ZERO,
                    searchRadius / COMMAND_HORIZONTAL_STEP,
                    Direction.EAST,
                    Direction.SOUTH).iterator();
            this.surfaceY = Mth.clamp(
                    LatitudeBiomes.SURFACE_CLASSIFY_Y + 4,
                    source.getLevel().getMinY() + 1,
                    source.getLevel().getMaxY());
        }

        @Override
        String route() {
            return "wetland";
        }

        @Override
        boolean runTick() {
            long tickStarted = System.nanoTime();
            int tickGridProbes = 0;
            int tickExactProbes = 0;
            while (offsets.hasNext()
                    && tickGridProbes < LatitudeLocateBudgetPolicy.MAX_WETLAND_GRID_PROBES_PER_TICK
                    && tickExactProbes < LatitudeLocateBudgetPolicy.MAX_WETLAND_EXACT_PROBES_PER_TICK
                    && !deadlineExceeded(tickStarted)) {
                BlockPos.MutableBlockPos offset = offsets.next();
                tickGridProbes++;
                gridProbes++;

                int sampleX = origin.getX() + offset.getX() * COMMAND_HORIZONTAL_STEP;
                int sampleZ = origin.getZ() + offset.getZ() * COMMAND_HORIZONTAL_STEP;
                int quartX = QuartPos.fromBlock(sampleX);
                int quartZ = QuartPos.fromBlock(sampleZ);
                int blockX = QuartPos.toBlock(quartX) + 2;
                int blockZ = QuartPos.toBlock(quartZ) + 2;
                if (!isWithinLatitudeWorld(blockX, blockZ)) {
                    continue;
                }
                if (!LatitudeBiomes.isPotentialWetlandLocateCandidate(
                        blockX, blockZ, worldRadius, sampler, includesSwamp, includesMangrove)) {
                    continue;
                }

                boolean directMangroveCandidate = includesMangrove
                        && LatitudeBiomes.isPotentialDirectMangroveLocateCandidate(
                                blockX, blockZ, sampler);
                if (!directMangroveCandidate
                        && !latitudeSource.isPotentialWetlandLocateSourceCandidate(
                                quartX, QuartPos.fromBlock(surfaceY), quartZ, sampler)) {
                    continue;
                }

                tickExactProbes++;
                exactProbes++;
                Holder<Biome> exact;
                exact = latitudeSource.getNoiseBiome(
                        quartX, QuartPos.fromBlock(surfaceY), quartZ, sampler);
                if (target.test(exact)) {
                    finish(Pair.of(LatitudeBiomeSource.centerQuartPosition(
                            new BlockPos(blockX, surfaceY, blockZ)), exact));
                    log(true);
                    return true;
                }
            }
            updateProgress(gridProbes, LatitudeLocateBudgetPolicy.worstCaseSamples(
                    searchRadius, COMMAND_HORIZONTAL_STEP, 1));
            if (!offsets.hasNext()) {
                finish(null);
                log(false);
                return true;
            }
            return false;
        }

        private void log(boolean found) {
            GlobeMod.LOGGER.info("[Latitude] finished tick-sliced biome locate target={} route={} gridProbes={} exactProbes={} found={}",
                    target.asPrintable(), route(), gridProbes, exactProbes, found);
        }
    }

    /** Uses the same finite preview and exact fallback budgets as LatitudeBiomeSource. */
    private static final class SurfaceLocateJob extends BiomeLocateJob {
        private final Set<Holder<Biome>> matching;
        private final boolean targetIncludesMangrove;
        private final int surfaceY;
        private final int previewStep;
        private final int fallbackStep;
        private final Iterator<BlockPos.MutableBlockPos> previewOffsets;
        private final Iterator<BlockPos.MutableBlockPos> fallbackOffsets;
        private final int previewTotal;
        private final int fallbackTotal;
        private int previewProbes;
        private int exactCandidateChecks;
        private int fallbackChecks;
        private boolean previewComplete;

        private SurfaceLocateJob(
                CommandSourceStack source,
                ResourceOrTagArgument.Result<Biome> target,
                BlockPos origin,
                LatitudeBiomeSource latitudeSource,
                int worldRadius,
                int searchRadius,
                Climate.Sampler sampler,
                Set<Holder<Biome>> matching,
                boolean targetIncludesMangrove) {
            super(source, target, origin, latitudeSource, worldRadius, searchRadius, sampler);
            this.matching = matching;
            this.targetIncludesMangrove = targetIncludesMangrove;
            this.surfaceY = Mth.clamp(
                    LatitudeBiomes.SURFACE_CLASSIFY_Y + 4,
                    source.getLevel().getMinY() + 1,
                    source.getLevel().getMaxY());
            this.previewStep = LatitudeLocateBudgetPolicy.surfaceHorizontalStep(
                    searchRadius, COMMAND_HORIZONTAL_STEP);
            this.fallbackStep = LatitudeLocateBudgetPolicy.surfaceExactFallbackHorizontalStep(
                    searchRadius, COMMAND_HORIZONTAL_STEP);
            this.previewTotal = LatitudeLocateBudgetPolicy.worstCaseSamples(
                    searchRadius, previewStep, 1);
            this.fallbackTotal = LatitudeLocateBudgetPolicy.worstCaseSamples(
                    searchRadius, fallbackStep, 1);
            this.previewOffsets = BlockPos.spiralAround(BlockPos.ZERO,
                    searchRadius / previewStep, Direction.EAST, Direction.SOUTH).iterator();
            this.fallbackOffsets = BlockPos.spiralAround(BlockPos.ZERO,
                    searchRadius / fallbackStep, Direction.EAST, Direction.SOUTH).iterator();
        }

        @Override
        String route() {
            return "surface";
        }

        @Override
        boolean runTick() {
            long tickStarted = System.nanoTime();
            if (!previewComplete) {
                int tickPreviewProbes = 0;
                while (previewOffsets.hasNext()
                        && tickPreviewProbes < LatitudeLocateBudgetPolicy.MAX_SURFACE_PREVIEW_PROBES_PER_TICK
                        && !deadlineExceeded(tickStarted)) {
                    BlockPos.MutableBlockPos offset = previewOffsets.next();
                    tickPreviewProbes++;
                    previewProbes++;
                    int quartX = QuartPos.fromBlock(origin.getX() + offset.getX() * previewStep);
                    int quartZ = QuartPos.fromBlock(origin.getZ() + offset.getZ() * previewStep);
                    int blockX = QuartPos.toBlock(quartX) + 2;
                    int blockZ = QuartPos.toBlock(quartZ) + 2;
                    if (!isWithinLatitudeWorld(blockX, blockZ)) {
                        continue;
                    }
                    Holder<Biome> preview = latitudeSource.getLocatePreviewNoiseBiome(
                            quartX, QuartPos.fromBlock(surfaceY), quartZ, sampler);
                    boolean plausible = target.test(preview)
                            || (targetIncludesMangrove
                            && LatitudeBiomes.isBiomeIdPublic(preview, "minecraft:swamp"));
                    if (!plausible) {
                        continue;
                    }
                    if (exactCandidateChecks >= LatitudeLocateBudgetPolicy.MAX_SURFACE_EXACT_VERIFICATIONS) {
                        previewComplete = true;
                        break;
                    }
                    exactCandidateChecks++;
                    Holder<Biome> exact = latitudeSource.getNoiseBiome(
                            quartX, QuartPos.fromBlock(surfaceY), quartZ, sampler);
                    if (target.test(exact)) {
                        finish(resultAt(quartX, quartZ, exact));
                        log(true);
                        return true;
                    }
                    // One terrain-aware classification per surface tick keeps a bad terrain sample
                    // from recreating the old integrated-server stall.
                    break;
                }
                if (!previewOffsets.hasNext()) {
                    previewComplete = true;
                }
                updateProgress(previewProbes, previewTotal + fallbackTotal);
                if (!previewComplete) {
                    return false;
                }
            }

            if (fallbackOffsets.hasNext()) {
                BlockPos.MutableBlockPos offset = fallbackOffsets.next();
                fallbackChecks++;
                int quartX = QuartPos.fromBlock(origin.getX() + offset.getX() * fallbackStep);
                int quartZ = QuartPos.fromBlock(origin.getZ() + offset.getZ() * fallbackStep);
                int blockX = QuartPos.toBlock(quartX) + 2;
                int blockZ = QuartPos.toBlock(quartZ) + 2;
                if (!isWithinLatitudeWorld(blockX, blockZ)) {
                    updateProgress(previewTotal + fallbackChecks, previewTotal + fallbackTotal);
                    return false;
                }
                Holder<Biome> exact = latitudeSource.getNoiseBiome(
                        quartX, QuartPos.fromBlock(surfaceY), quartZ, sampler);
                if (target.test(exact)) {
                    finish(resultAt(quartX, quartZ, exact));
                    log(true);
                    return true;
                }
                updateProgress(previewTotal + fallbackChecks, previewTotal + fallbackTotal);
                return false;
            }

            Pair<BlockPos, Holder<Biome>> planned = latitudeSource.findPlannedSurfaceCoverage(
                    matching, new BlockPos(origin.getX(), surfaceY, origin.getZ()), target, sampler);
            finish(planned);
            log(planned != null);
            return true;
        }

        private Pair<BlockPos, Holder<Biome>> resultAt(int quartX, int quartZ, Holder<Biome> exact) {
            return Pair.of(LatitudeBiomeSource.centerQuartPosition(new BlockPos(
                    QuartPos.toBlock(quartX) + 2, surfaceY, QuartPos.toBlock(quartZ) + 2)), exact);
        }

        private void log(boolean found) {
            GlobeMod.LOGGER.info("[Latitude] finished tick-sliced biome locate target={} route={} previewProbes={} exactCandidateChecks={} fallbackChecks={} found={}",
                    target.asPrintable(), route(), previewProbes, exactCandidateChecks, fallbackChecks, found);
        }
    }

    /** Covers cave or mixed tags with the same bounded three-dimensional search as the source. */
    private static final class ThreeDimensionalLocateJob extends BiomeLocateJob {
        private final int[] verticalSamples;
        private final int horizontalStep;
        private final Iterator<BlockPos.MutableBlockPos> offsets;
        private final int totalSamples;
        private BlockPos currentOffset;
        private int verticalIndex;
        private int sampled;

        private ThreeDimensionalLocateJob(
                CommandSourceStack source,
                ResourceOrTagArgument.Result<Biome> target,
                BlockPos origin,
                LatitudeBiomeSource latitudeSource,
                int worldRadius,
                int searchRadius,
                Climate.Sampler sampler,
                ServerLevel level) {
            super(source, target, origin, latitudeSource, worldRadius, searchRadius, sampler);
            this.verticalSamples = Mth.outFromOrigin(origin.getY(),
                    level.getMinY() + 1, level.getMaxY() + 1, COMMAND_VERTICAL_STEP).toArray();
            this.horizontalStep = LatitudeLocateBudgetPolicy.threeDimensionalHorizontalStep(
                    searchRadius, COMMAND_HORIZONTAL_STEP, Math.max(1, verticalSamples.length));
            this.offsets = BlockPos.spiralAround(BlockPos.ZERO,
                    searchRadius / horizontalStep, Direction.EAST, Direction.SOUTH).iterator();
            this.totalSamples = LatitudeLocateBudgetPolicy.worstCaseSamples(
                    searchRadius, horizontalStep, Math.max(1, verticalSamples.length));
        }

        @Override
        String route() {
            return "three-dimensional";
        }

        @Override
        boolean runTick() {
            long tickStarted = System.nanoTime();
            int tickExactProbes = 0;
            while (tickExactProbes < LatitudeLocateBudgetPolicy.MAX_THREE_DIMENSIONAL_EXACT_PROBES_PER_TICK
                    && !deadlineExceeded(tickStarted)) {
                if (currentOffset == null) {
                    if (!offsets.hasNext()) {
                        finish(null);
                        log(false);
                        return true;
                    }
                    BlockPos.MutableBlockPos offset = offsets.next();
                    currentOffset = new BlockPos(offset.getX(), offset.getY(), offset.getZ());
                    verticalIndex = 0;
                }
                int y = verticalSamples[verticalIndex++];
                int quartX = QuartPos.fromBlock(origin.getX() + currentOffset.getX() * horizontalStep);
                int quartY = QuartPos.fromBlock(y);
                int quartZ = QuartPos.fromBlock(origin.getZ() + currentOffset.getZ() * horizontalStep);
                sampled++;
                tickExactProbes++;
                if (!isWithinLatitudeWorld(
                        QuartPos.toBlock(quartX) + 2, QuartPos.toBlock(quartZ) + 2)) {
                    if (verticalIndex >= verticalSamples.length) {
                        currentOffset = null;
                    }
                    continue;
                }
                Holder<Biome> exact = latitudeSource.getNoiseBiome(quartX, quartY, quartZ, sampler);
                if (target.test(exact)) {
                    finish(Pair.of(LatitudeBiomeSource.centerQuartPosition(new BlockPos(
                            QuartPos.toBlock(quartX) + 2, QuartPos.toBlock(quartY) + 2,
                            QuartPos.toBlock(quartZ) + 2)), exact));
                    log(true);
                    return true;
                }
                if (verticalIndex >= verticalSamples.length) {
                    currentOffset = null;
                }
            }
            updateProgress(sampled, totalSamples);
            return false;
        }

        private void log(boolean found) {
            GlobeMod.LOGGER.info("[Latitude] finished tick-sliced biome locate target={} route={} samples={} found={}",
                    target.asPrintable(), route(), sampled, found);
        }
    }
}
