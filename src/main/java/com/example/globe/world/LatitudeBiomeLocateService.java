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

/** Runs exact wetland locate searches in small server-tick slices. */
public final class LatitudeBiomeLocateService {
    private static final int COMMAND_RADIUS = 6_400;
    private static final int COMMAND_HORIZONTAL_STEP = 32;
    private static final int COMMAND_GRID_PROBES = 160_801;
    private static final long FIRST_PROGRESS_NANOS = 5_000_000_000L;
    private static final long FOLLOWUP_PROGRESS_NANOS = 10_000_000_000L;

    private static final Map<MinecraftServer, WetlandLocateJob> ACTIVE_JOBS =
            new IdentityHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(LatitudeBiomeLocateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(ACTIVE_JOBS::remove);
    }

    private LatitudeBiomeLocateService() {
    }

    /**
     * Claims wetland-only locate commands in Latitude worlds. Other worlds and mixed biome
     * tags continue through the normal command path.
     *
     * @return true when the command was claimed and will finish across later server ticks
     */
    public static boolean beginIfLatitudeWetland(
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
        boolean includesSwamp = matching.stream().anyMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:swamp"));
        boolean includesMangrove = matching.stream().anyMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp"));
        boolean wetlandOnly = matching.stream().allMatch(candidate ->
                LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:swamp")
                        || LatitudeBiomes.isBiomeIdPublic(candidate, "minecraft:mangrove_swamp"));
        if (!wetlandOnly || (!includesSwamp && !includesMangrove)) {
            return false;
        }

        MinecraftServer server = source.getServer();
        if (ACTIVE_JOBS.containsKey(server)) {
            source.sendFailure(Component.literal(
                    "A Latitude biome search is already running. Its result will appear in chat."));
            return true;
        }

        BlockPos origin = BlockPos.containing(source.getPosition());
        WetlandLocateJob job = new WetlandLocateJob(
                source,
                target,
                origin,
                latitudeSource,
                worldRadius,
                randomState.sampler(),
                includesSwamp,
                includesMangrove);
        ACTIVE_JOBS.put(server, job);
        GlobeMod.LOGGER.info(
                "[Latitude] started tick-sliced wetland locate target={} origin={} radius={} step={} worstCaseGridProbes={}",
                target.asPrintable(),
                origin.toShortString(),
                COMMAND_RADIUS,
                COMMAND_HORIZONTAL_STEP,
                LatitudeLocateBudgetPolicy.worstCaseSamples(
                        COMMAND_RADIUS, COMMAND_HORIZONTAL_STEP, 1));
        return true;
    }

    private static void tick(MinecraftServer server) {
        WetlandLocateJob job = ACTIVE_JOBS.get(server);
        if (job == null) {
            return;
        }
        try {
            if (job.runTick()) {
                ACTIVE_JOBS.remove(server);
            }
        } catch (Throwable failure) {
            ACTIVE_JOBS.remove(server);
            GlobeMod.LOGGER.error("[Latitude] tick-sliced wetland locate failed", failure);
            job.fail(failure);
        }
    }

    private static final class WetlandLocateJob {
        private final CommandSourceStack source;
        private final ResourceOrTagArgument.Result<Biome> target;
        private final BlockPos origin;
        private final LatitudeBiomeSource latitudeSource;
        private final int worldRadius;
        private final Climate.Sampler sampler;
        private final boolean includesSwamp;
        private final boolean includesMangrove;
        private final Iterator<BlockPos.MutableBlockPos> offsets;
        private final int surfaceY;
        private final long startedNanos;
        private final ServerBossEvent bossBar;

        private int gridProbes;
        private int sourcePreviewProbes;
        private int sourcePreviewCandidates;
        private int directMangroveCandidates;
        private int eligibleProbes;
        private int exactProbes;
        private long nextProgressNanos;
        private long maxSamplingTickNanos;
        private long totalExactProbeNanos;
        private long maxExactProbeNanos;

        private WetlandLocateJob(
                CommandSourceStack source,
                ResourceOrTagArgument.Result<Biome> target,
                BlockPos origin,
                LatitudeBiomeSource latitudeSource,
                int worldRadius,
                Climate.Sampler sampler,
                boolean includesSwamp,
                boolean includesMangrove) {
            this.source = source;
            this.target = target;
            this.origin = origin;
            this.latitudeSource = latitudeSource;
            this.worldRadius = worldRadius;
            this.sampler = sampler;
            this.includesSwamp = includesSwamp;
            this.includesMangrove = includesMangrove;
            this.offsets = BlockPos.spiralAround(
                    BlockPos.ZERO,
                    COMMAND_RADIUS / COMMAND_HORIZONTAL_STEP,
                    Direction.EAST,
                    Direction.SOUTH).iterator();
            this.surfaceY = Mth.clamp(
                    LatitudeBiomes.SURFACE_CLASSIFY_Y + 4,
                    source.getLevel().getMinY() + 1,
                    source.getLevel().getMaxY());
            this.startedNanos = System.nanoTime();
            this.nextProgressNanos = startedNanos + FIRST_PROGRESS_NANOS;
            this.bossBar = new ServerBossEvent(
                    Component.literal("Searching for " + target.asPrintable() + "..."),
                    BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setProgress(0.0F);
            ServerPlayer requester = source.getPlayer();
            if (requester != null) {
                this.bossBar.addPlayer(requester);
            }
        }

        private boolean runTick() {
            long tickStarted = System.nanoTime();
            long deadline = tickStarted
                    + LatitudeLocateBudgetPolicy.MAX_WETLAND_LOCATE_TICK_NANOS;
            int tickGridProbes = 0;
            int tickExactProbes = 0;
            while (offsets.hasNext()
                    && tickGridProbes
                    < LatitudeLocateBudgetPolicy.MAX_WETLAND_GRID_PROBES_PER_TICK
                    && tickExactProbes
                    < LatitudeLocateBudgetPolicy.MAX_WETLAND_EXACT_PROBES_PER_TICK
                    && System.nanoTime() < deadline) {
                BlockPos.MutableBlockPos offset = offsets.next();
                tickGridProbes++;
                gridProbes++;

                int sampleX = origin.getX() + offset.getX() * COMMAND_HORIZONTAL_STEP;
                int sampleZ = origin.getZ() + offset.getZ() * COMMAND_HORIZONTAL_STEP;
                int quartX = QuartPos.fromBlock(sampleX);
                int quartZ = QuartPos.fromBlock(sampleZ);
                int blockX = QuartPos.toBlock(quartX) + 2;
                int blockZ = QuartPos.toBlock(quartZ) + 2;
                if (!LatitudeBiomes.isPotentialWetlandLocateCandidate(
                        blockX,
                        blockZ,
                        worldRadius,
                        sampler,
                        includesSwamp,
                        includesMangrove)) {
                    continue;
                }

                boolean directMangroveCandidate = includesMangrove
                        && LatitudeBiomes.isPotentialDirectMangroveLocateCandidate(
                                blockX,
                                blockZ,
                                sampler);
                if (directMangroveCandidate) {
                    directMangroveCandidates++;
                } else {
                    sourcePreviewProbes++;
                    if (!latitudeSource.isPotentialWetlandLocateSourceCandidate(
                            quartX,
                            QuartPos.fromBlock(surfaceY),
                            quartZ,
                            sampler)) {
                        continue;
                    }
                    sourcePreviewCandidates++;
                }

                eligibleProbes++;
                exactProbes++;
                tickExactProbes++;
                long exactProbeStarted = System.nanoTime();
                Holder<Biome> exact;
                try {
                    exact = latitudeSource.getNoiseBiome(
                            quartX,
                            QuartPos.fromBlock(surfaceY),
                            quartZ,
                            sampler);
                } finally {
                    long exactProbeNanos = System.nanoTime() - exactProbeStarted;
                    totalExactProbeNanos += exactProbeNanos;
                    maxExactProbeNanos = Math.max(maxExactProbeNanos, exactProbeNanos);
                }
                if (target.test(exact)) {
                    recordSamplingTick(tickStarted);
                    finish(Pair.of(LatitudeBiomeSource.centerQuartPosition(
                            new BlockPos(blockX, surfaceY, blockZ)), exact));
                    return true;
                }
            }

            if (!offsets.hasNext()) {
                recordSamplingTick(tickStarted);
                finish(null);
                return true;
            }
            recordSamplingTick(tickStarted);
            reportProgressIfDue();
            return false;
        }

        private void recordSamplingTick(long tickStarted) {
            maxSamplingTickNanos = Math.max(
                    maxSamplingTickNanos,
                    System.nanoTime() - tickStarted);
        }

        private void reportProgressIfDue() {
            long now = System.nanoTime();
            if (now < nextProgressNanos) {
                return;
            }
            int percent = Mth.clamp(
                    (int) ((long) gridProbes * 100L / COMMAND_GRID_PROBES),
                    0,
                    99);
            bossBar.setProgress(percent / 100.0F);
            bossBar.setName(Component.literal(
                    "Searching for " + target.asPrintable() + "... " + percent + "%"));
            GlobeMod.LOGGER.info(
                    "[Latitude] tick-sliced wetland locate progress target={} percent={} gridProbes={} sourcePreviewProbes={} sourcePreviewCandidates={} directMangroveCandidates={} eligibleProbes={} exactProbes={} totalExactProbeMicros={} maxExactProbeMicros={}",
                    target.asPrintable(),
                    percent,
                    gridProbes,
                    sourcePreviewProbes,
                    sourcePreviewCandidates,
                    directMangroveCandidates,
                    eligibleProbes,
                    exactProbes,
                    totalExactProbeNanos / 1_000L,
                    maxExactProbeNanos / 1_000L);
            nextProgressNanos = now + FOLLOWUP_PROGRESS_NANOS;
        }

        private void finish(Pair<BlockPos, Holder<Biome>> result) {
            bossBar.removeAllPlayers();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
            if (result == null) {
                source.sendFailure(Component.translatableEscape(
                        "commands.locate.biome.not_found", target.asPrintable()));
            } else {
                LocateCommand.showLocateResult(
                        source,
                        target,
                        origin,
                        result,
                        "commands.locate.biome.success",
                        true,
                        elapsed);
            }
            GlobeMod.LOGGER.info(
                    "[Latitude] finished tick-sliced wetland locate target={} elapsedMs={} maxSamplingTickMicros={} gridProbes={} sourcePreviewProbes={} sourcePreviewCandidates={} directMangroveCandidates={} eligibleProbes={} exactProbes={} totalExactProbeMicros={} maxExactProbeMicros={} found={}",
                    target.asPrintable(),
                    elapsed.toMillis(),
                    maxSamplingTickNanos / 1_000L,
                    gridProbes,
                    sourcePreviewProbes,
                    sourcePreviewCandidates,
                    directMangroveCandidates,
                    eligibleProbes,
                    exactProbes,
                    totalExactProbeNanos / 1_000L,
                    maxExactProbeNanos / 1_000L,
                    result != null);
        }

        private void fail(Throwable failure) {
            bossBar.removeAllPlayers();
            source.sendFailure(Component.literal(
                    "Latitude biome search stopped safely: "
                            + failure.getClass().getSimpleName()));
        }
    }
}
