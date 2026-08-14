package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Runs supported Latitude structure searches without blocking the server tick thread. */
public final class LatitudeStructureLocateService {
    private static final int VANILLA_MAX_RINGS = 100;
    private static final Map<MinecraftServer, StructureLocateJob> ACTIVE_JOBS =
            new IdentityHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(LatitudeStructureLocateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> cancel(server, null));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                cancel(server, handler.player));
    }

    private LatitudeStructureLocateService() {
    }

    /**
     * Claims a Latitude structure locate only when every matching placement is random-spread.
     * Unsupported or mixed placement sets are left wholly to vanilla, never partially searched.
     */
    public static boolean beginIfApplicable(
            CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> target) {
        ServerLevel level = source.getLevel();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator generator)
                || !GlobeMod.shouldApplyLatitudeWorldgen(generator)) {
            return false;
        }

        Registry<Structure> structureRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> matched = structureRegistry.listElements()
                .map(reference -> (Holder<Structure>) reference)
                .filter(target)
                .toList();
        if (matched.isEmpty()) {
            return false;
        }

        ChunkGeneratorStructureState structureState =
                level.getChunkSource().getGeneratorState();
        List<Candidate> candidates = new ArrayList<>();
        for (Holder<Structure> holder : matched) {
            Identifier structureId = structureRegistry.getKey(holder.value());
            for (Holder<StructureSet> setHolder : structureState.possibleStructureSets()) {
                StructureSet structureSet = setHolder.value();
                boolean containsTarget = structureSet.structures().stream()
                        .anyMatch(entry -> entry.structure().equals(holder));
                if (!containsTarget) {
                    continue;
                }
                StructurePlacement placement = structureSet.placement();
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                    return false;
                }
                boolean village = holder.is(StructureTags.VILLAGE)
                        || (structureId != null && structureId.getPath().contains("village"));
                candidates.add(new Candidate(holder, spread, structureId, village));
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }

        // The structure lookup runs entirely on the background pool, so avoid forcing
        // any extra synchronous preparation here that can block the server tick thread.
        MinecraftServer server = source.getServer();
        if (ACTIVE_JOBS.containsKey(server)) {
            source.sendFailure(Component.literal(
                    "A Latitude structure search is already running. Its result will appear in chat."));
            return true;
        }

        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        RandomState randomState = level.getChunkSource().randomState();
        int worldRadius = GlobeMod.borderRadiusForNoiseGenerator(generator);
        LatitudeBiomeSource finalBiomeSource = LatitudeBiomeSource.forStructure(
                generator.getBiomeSource(), biomeRegistry, worldRadius,
                generator, randomState, level);
        SearchBounds bounds = new SearchBounds(
                Math.max(-worldRadius, Mth.floor(level.getWorldBorder().getMinX())),
                Math.min(worldRadius, Mth.ceil(level.getWorldBorder().getMaxX())),
                Math.max(-worldRadius, Mth.floor(level.getWorldBorder().getMinZ())),
                Math.min(worldRadius, Mth.ceil(level.getWorldBorder().getMaxZ())));
        SearchContext context = new SearchContext(
                List.copyOf(candidates),
                BlockPos.containing(source.getPosition()),
                structureState,
                structureState.getLevelSeed(),
                worldRadius,
                bounds,
                biomeRegistry,
                finalBiomeSource,
                generator,
                randomState,
                server.getStructureManager(),
                level);

        StructureLocateJob job = new StructureLocateJob(source, target, context);
        ACTIVE_JOBS.put(server, job);
        try {
            job.start();
        } catch (Throwable failure) {
            ACTIVE_JOBS.remove(server);
            job.finishWithFailure(failure);
        }
        GlobeMod.LOGGER.info(
                "[Latitude] started asynchronous structure locate target={} origin={} worldRadius={}",
                target.asPrintable(), context.origin().toShortString(), worldRadius);
        return true;
    }

    private static void tick(MinecraftServer server) {
        StructureLocateJob job = ACTIVE_JOBS.get(server);
        if (job == null) {
            return;
        }
        try {
            job.updateProgress();
            if (job.isDone()) {
                ACTIVE_JOBS.remove(server);
                job.complete();
            }
        } catch (Throwable failure) {
            ACTIVE_JOBS.remove(server);
            job.finishWithFailure(failure);
        }
    }

    private static void cancel(MinecraftServer server, ServerPlayer disconnectedPlayer) {
        StructureLocateJob job = ACTIVE_JOBS.get(server);
        if (job == null || (disconnectedPlayer != null && !job.belongsTo(disconnectedPlayer))) {
            return;
        }
        ACTIVE_JOBS.remove(server);
        job.cancel();
    }

    private static final class StructureLocateJob {
        private final CommandSourceStack source;
        private final ResourceOrTagKeyArgument.Result<Structure> target;
        private final SearchContext context;
        private final ServerPlayer requester;
        private final ServerBossEvent bossBar;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger completedRings = new AtomicInteger();
        private final long startedNanos = System.nanoTime();
        private CompletableFuture<SearchOutcome> future;
        private int lastPercent = -1;
        private boolean finished;

        private StructureLocateJob(
                CommandSourceStack source,
                ResourceOrTagKeyArgument.Result<Structure> target,
                SearchContext context) {
            this.source = source;
            this.target = target;
            this.context = context;
            this.requester = source.getPlayer();
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

        private void start() {
            future = CompletableFuture.supplyAsync(
                    () -> search(context, completedRings, cancelled),
                    Util.backgroundExecutor());
        }

        private boolean belongsTo(ServerPlayer player) {
            return requester == player;
        }

        private boolean isDone() {
            return future != null && future.isDone();
        }

        private void updateProgress() {
            int percent = Mth.clamp(
                    completedRings.get() * 100 / (VANILLA_MAX_RINGS + 1), 0, 99);
            if (percent == lastPercent) {
                return;
            }
            lastPercent = percent;
            bossBar.setProgress(percent / 100.0F);
            bossBar.setName(Component.literal(
                    "Searching for " + target.asPrintable() + "... " + percent + "%"));
        }

        private void complete() {
            if (finished) {
                return;
            }
            try {
                finishWithResult(future.join());
            } catch (Throwable failure) {
                Throwable cause = failure instanceof CompletionException
                                && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                finishWithFailure(cause);
            }
        }

        private void finishWithResult(SearchOutcome outcome) {
            if (finished) {
                return;
            }
            try {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
                if (outcome.result() == null) {
                    source.sendFailure(Component.translatableEscape(
                            "commands.locate.structure.not_found", target.asPrintable()));
                } else {
                    showTeleportLocateResult(source, target, context.origin(), outcome.result());
                }
                Tally tally = outcome.tally();
                GlobeMod.LOGGER.info(
                        "[Latitude] finished asynchronous structure locate target={} worldRadius={} candidatesTested={} rejectedPlacement={} rejectedBiome={} rejectedVillage={} rejectedGeneration={} resolveFailures={} outOfBorder={} ringsScanned={} elapsedMs={} found={}",
                        target.asPrintable(), context.worldRadius(), tally.tested,
                        tally.rejectedPlacement, tally.rejectedBiome, tally.rejectedVillage,
                        tally.rejectedGeneration, tally.resolveFailures, tally.outOfBorder, tally.ringsScanned,
                        elapsed.toMillis(), outcome.result() != null);
            } catch (Throwable failure) {
                finishWithFailure(failure);
            } finally {
                finished = true;
                clearBossBar();
            }
        }

        private void finishWithFailure(Throwable failure) {
            if (finished) {
                return;
            }
            finished = true;
            cancelled.set(true);
            if (future != null) {
                future.cancel(false);
            }
            try {
                GlobeMod.LOGGER.error("[Latitude] asynchronous structure locate failed", failure);
                source.sendFailure(Component.literal(
                        "Latitude structure search stopped safely: "
                                + failure.getClass().getSimpleName()));
            } catch (Throwable deliveryFailure) {
                GlobeMod.LOGGER.error(
                        "[Latitude] could not deliver structure locate failure", deliveryFailure);
            } finally {
                clearBossBar();
            }
        }

        private void cancel() {
            if (finished) {
                return;
            }
            finished = true;
            cancelled.set(true);
            if (future != null) {
                future.cancel(false);
            }
            clearBossBar();
        }

        private void clearBossBar() {
            bossBar.removeAllPlayers();
        }
    }

    private static void showTeleportLocateResult(
            CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> target,
            BlockPos origin,
            Pair<BlockPos, Holder<Structure>> result) {
        BlockPos location = result.getFirst();
        double dx = origin.getX() - location.getX();
        double dz = origin.getZ() - location.getZ();
        int distance = Mth.floor((float) Math.sqrt(dx * dx + dz * dz));
        ClickEvent clickEvent = new ClickEvent.RunCommand(
                "/tp " + location.getX() + " ~ " + location.getZ());
        Component coordinates = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", location.getX(), "~", location.getZ()))
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(clickEvent)
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.locate.structure.success", target.asPrintable(), coordinates, distance),
                false);
    }

    private static SearchOutcome search(
            SearchContext context,
            AtomicInteger completedRings,
            AtomicBoolean cancelled) {
        Tally tally = new Tally();
        int originChunkX = context.origin().getX() >> 4;
        int originChunkZ = context.origin().getZ() >> 4;

        for (int ring = 0; ring <= VANILLA_MAX_RINGS && !cancelled.get(); ring++) {
            tally.ringsScanned = ring + 1;
            Pair<BlockPos, Holder<Structure>> ringBest = null;
            double ringBestDistSqr = Double.MAX_VALUE;
            boolean ringHasInBoundsCell = false;

            for (Candidate candidate : context.candidates()) {
                int spacing = candidate.placement().spacing();
                for (int dz = -ring; dz <= ring && !cancelled.get(); dz++) {
                    boolean dzBorder = dz == -ring || dz == ring;
                    for (int dx = -ring; dx <= ring && !cancelled.get(); dx++) {
                        boolean dxBorder = dx == -ring || dx == ring;
                        if (!dzBorder && !dxBorder) {
                            continue;
                        }
                        ChunkPos candidateChunk = candidate.placement()
                                .getPotentialStructureChunk(
                                        context.seed(),
                                        originChunkX + spacing * dx,
                                        originChunkZ + spacing * dz);
                        BlockPos locatePos = candidate.placement().getLocatePos(candidateChunk);
                        if (!context.bounds().contains(locatePos)) {
                            tally.outOfBorder++;
                            continue;
                        }
                        ringHasInBoundsCell = true;
                        if (!candidate.placement().isStructureChunk(
                                context.structureState(), candidateChunk.x(), candidateChunk.z())) {
                            tally.rejectedPlacement++;
                            continue;
                        }
                        tally.tested++;
                        if (!evaluateCandidate(context, candidate, candidateChunk, tally)) {
                            continue;
                        }
                        double distSqr = context.origin().distSqr(locatePos);
                        if (distSqr < ringBestDistSqr) {
                            ringBestDistSqr = distSqr;
                            ringBest = Pair.of(locatePos, candidate.holder());
                        }
                    }
                }
            }

            completedRings.set(ring + 1);
            if (ringBest != null) {
                return new SearchOutcome(ringBest, tally);
            }
            if (!ringHasInBoundsCell && ring > 0) {
                break;
            }
        }
        return new SearchOutcome(null, tally);
    }

    private static boolean evaluateCandidate(
            SearchContext context,
            Candidate candidate,
            ChunkPos candidateChunk,
            Tally tally) {
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        if (candidate.village()
                && !evaluateVillagePolicy(context, candidate, blockX, blockZ, tally)) {
            return false;
        }
        if (candidate.village() && candidate.structureId() != null) {
            Holder<Biome> finalBiome;
            try {
                finalBiome = context.finalBiomeSource().getNoiseBiome(
                        Math.floorDiv(blockX, 4),
                        Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                        Math.floorDiv(blockZ, 4),
                        context.randomState().sampler());
            } catch (RuntimeException resolutionFailure) {
                tally.resolveFailures++;
                return false;
            }
            if (finalBiome == null) {
                tally.rejectedBiome++;
                return false;
            }
            Identifier finalBiomeId = context.biomeRegistry().getKey(finalBiome.value());
            if (finalBiomeId != null
                    && LatitudeBiomes.villageVariantVsBiomeMismatch(
                            candidate.structureId().getPath(), finalBiomeId.toString())) {
                tally.rejectedVillage++;
                return false;
            }
        }
        try {
            StructureStart generatedStart = candidate.structure().generate(
                    candidate.holder(),
                    context.level().dimension(),
                    context.level().registryAccess(),
                    context.generator(),
                    context.finalBiomeSource(),
                    context.randomState(),
                    context.templateManager(),
                    context.seed(),
                    candidateChunk,
                    0,
                    context.level(),
                    candidate.structure().biomes()::contains);
            if (generatedStart == null || !generatedStart.isValid()) {
                tally.rejectedGeneration++;
                return false;
            }
            return true;
        } catch (RuntimeException generationFailure) {
            tally.resolveFailures++;
            return false;
        }
    }

    private static boolean evaluateVillagePolicy(
            SearchContext context,
            Candidate candidate,
            int blockX,
            int blockZ,
            Tally tally) {
        if (candidate.structureId() == null) {
            return true;
        }
        double absDeg = Math.abs((double) blockZ) * 90.0
                / Math.max(1, context.worldRadius());
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        if (LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, context.worldRadius())
                || LatitudeBiomes.villageClimateVsBandMismatch(
                        candidate.structureId().getPath(), band)) {
            tally.rejectedVillage++;
            return false;
        }

        int[] terrainHeights = new int[VillageTerrainSuitabilityPolicy.SAMPLE_COUNT];
        int terrainIndex = 0;
        for (int dz = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                dz <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                dz += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
            for (int dx = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                    dx <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                    dx += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
                terrainHeights[terrainIndex++] = context.generator().getBaseHeight(
                        blockX + dx,
                        blockZ + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.level(),
                        context.randomState());
            }
        }
        if (!VillageTerrainSuitabilityPolicy.isSuitable(terrainHeights)) {
            tally.rejectedVillage++;
            return false;
        }
        return true;
    }

    private record SearchContext(
            List<Candidate> candidates,
            BlockPos origin,
            ChunkGeneratorStructureState structureState,
            long seed,
            int worldRadius,
            SearchBounds bounds,
            Registry<Biome> biomeRegistry,
            LatitudeBiomeSource finalBiomeSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            StructureTemplateManager templateManager,
            ServerLevel level) {
    }

    private record SearchBounds(int minX, int maxX, int minZ, int maxZ) {
        boolean contains(BlockPos position) {
            return position.getX() >= minX && position.getX() <= maxX
                    && position.getZ() >= minZ && position.getZ() <= maxZ;
        }
    }

    private record SearchOutcome(Pair<BlockPos, Holder<Structure>> result, Tally tally) {
    }

    private record Candidate(
            Holder<Structure> holder,
            RandomSpreadStructurePlacement placement,
            Identifier structureId,
            boolean village) {
        Structure structure() {
            return holder.value();
        }
    }

    private static final class Tally {
        private int tested;
        private int rejectedPlacement;
        private int rejectedBiome;
        private int rejectedVillage;
        private int rejectedGeneration;
        private int resolveFailures;
        private int outOfBorder;
        private int ringsScanned;
    }
}
