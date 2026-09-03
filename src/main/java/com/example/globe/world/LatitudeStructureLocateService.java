package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Runs supported Latitude structure searches without blocking the server tick thread. */
public final class LatitudeStructureLocateService {
    private static final int VANILLA_MAX_RINGS = 100;
    private static final long TELEPORT_TOKEN_TTL_MS = 5 * 60 * 1_000L;
    private static final Map<MinecraftServer, StructureLocateJob> ACTIVE_JOBS =
            new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, PendingTeleport>> PENDING_TELEPORTS =
            new IdentityHashMap<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(LatitudeStructureLocateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            cancel(server, null);
            PENDING_TELEPORTS.remove(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cancel(server, handler.player);
            clearPendingTeleport(server, handler.player);
        });
    }

    private LatitudeStructureLocateService() {
    }

    /** Executes the player-bound, one-time action behind a clickable locate coordinate. */
    public static int runPendingTeleport(CommandSourceStack source, String tokenValue) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This locate teleport belongs to a player."));
            return 0;
        }

        UUID token;
        try {
            token = UUID.fromString(tokenValue);
        } catch (IllegalArgumentException invalidToken) {
            source.sendFailure(Component.literal("That locate teleport is no longer available."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        Map<UUID, PendingTeleport> serverTeleports = PENDING_TELEPORTS.get(server);
        PendingTeleport pending = serverTeleports == null
                ? null
                : serverTeleports.get(player.getUUID());
        if (pending == null || !pending.token().equals(token)) {
            source.sendFailure(Component.literal("That locate teleport is no longer available."));
            return 0;
        }
        serverTeleports.remove(player.getUUID());
        if (serverTeleports.isEmpty()) {
            PENDING_TELEPORTS.remove(server);
        }
        if (Util.getMillis() > pending.expiresAtMs()) {
            source.sendFailure(Component.literal("That locate teleport has expired."));
            return 0;
        }

        ServerLevel targetLevel = server.getLevel(pending.level());
        if (targetLevel == null) {
            source.sendFailure(Component.literal("That locate destination is no longer available."));
            return 0;
        }
        player.teleportTo(
                targetLevel,
                pending.x() + 0.5,
                Math.max(player.getY(), pending.minimumY()),
                pending.z() + 0.5,
                EnumSet.noneOf(Relative.class),
                player.getYRot(),
                player.getXRot(),
                true);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
        if (pending.mayBeBuried()) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "globe.locate.buried_structure.teleported", pending.minimumY() - 2),
                    false);
        }
        return 1;
    }

    private static void clearPendingTeleport(MinecraftServer server, ServerPlayer player) {
        Map<UUID, PendingTeleport> serverTeleports = PENDING_TELEPORTS.get(server);
        if (serverTeleports == null) {
            return;
        }
        serverTeleports.remove(player.getUUID());
        if (serverTeleports.isEmpty()) {
            PENDING_TELEPORTS.remove(server);
        }
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
        Climate.Sampler climateSampler =
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
        BiomeResolver finalBiomeResolver = finalBiomeSource.createResolver(climateSampler);
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
                finalBiomeResolver,
                climateSampler,
                generator,
                randomState,
                server.getStructureTemplateManager(),
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
                        "[Latitude] finished asynchronous structure locate target={} worldRadius={} candidatesTested={} rejectedPlacement={} rejectedBiome={} rejectedVillage={} rejectedGeneration={} rejectedEwDanger={} rejectedBadlandsPolicy={} resolveFailures={} outOfBorder={} ringsScanned={} elapsedMs={} found={}",
                        target.asPrintable(), context.worldRadius(), tally.tested,
                        tally.rejectedPlacement, tally.rejectedBiome, tally.rejectedVillage,
                        tally.rejectedGeneration, tally.rejectedEwDanger, tally.rejectedBadlandsPolicy,
                        tally.resolveFailures, tally.outOfBorder, tally.ringsScanned,
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
        boolean mayBeBuried = mayBeBuried(result.getSecond());
        ClickEvent clickEvent = new ClickEvent.SuggestCommand(
                "tp " + location.getX() + " ~ " + location.getZ());
        ServerPlayer requester = source.getPlayer();
        if (requester != null) {
            UUID token = UUID.randomUUID();
            PENDING_TELEPORTS
                    .computeIfAbsent(source.getServer(), ignored -> new HashMap<>())
                    .put(requester.getUUID(), new PendingTeleport(
                            token,
                            source.getLevel().dimension(),
                            location.getX(),
                            location.getZ(),
                            location.getY(),
                            mayBeBuried,
                            Util.getMillis() + TELEPORT_TOKEN_TTL_MS));
            clickEvent = new ClickEvent.RunCommand("/latitude_locate_teleport " + token);
        }
        ClickEvent finalClickEvent = clickEvent;
        Component coordinates = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", location.getX(), "~", location.getZ()))
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(finalClickEvent)
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.coordinates.tooltip"))));
        Component message = Component.translatable(
                "commands.locate.structure.success", target.asPrintable(), coordinates, distance);
        if (mayBeBuried) {
            message = message.copy()
                    .append(Component.literal(" "))
                    .append(Component.translatable(
                            "globe.locate.buried_structure.hint", location.getY() - 2));
        }
        Component finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
    }

    private static boolean mayBeBuried(Holder<Structure> structure) {
        return structure.unwrapKey()
                .map(key -> key.identifier().getPath().equals("desert_pyramid"))
                .orElse(false);
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
                        BlockPos generatedTarget = evaluateCandidate(
                                context, candidate, candidateChunk, tally);
                        if (generatedTarget == null) {
                            continue;
                        }
                        if (!context.bounds().contains(generatedTarget)) {
                            tally.outOfBorder++;
                            continue;
                        }
                        double targetDx = context.origin().getX() - generatedTarget.getX();
                        double targetDz = context.origin().getZ() - generatedTarget.getZ();
                        double distSqr = targetDx * targetDx + targetDz * targetDz;
                        if (distSqr < ringBestDistSqr) {
                            ringBestDistSqr = distSqr;
                            ringBest = Pair.of(generatedTarget, candidate.holder());
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

    private static BlockPos evaluateCandidate(
            SearchContext context,
            Candidate candidate,
            ChunkPos candidateChunk,
            Tally tally) {
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        if (candidate.village()
                && !evaluateVillagePolicy(context, candidate, blockX, blockZ, tally)) {
            return null;
        }
        if (candidate.village() && candidate.structureId() != null) {
            Holder<Biome> finalBiome;
            try {
                finalBiome = context.finalBiomeResolver().getNoiseBiome(
                        Math.floorDiv(blockX, 4),
                        Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                        Math.floorDiv(blockZ, 4));
            } catch (RuntimeException resolutionFailure) {
                tally.resolveFailures++;
                return null;
            }
            if (finalBiome == null) {
                tally.rejectedBiome++;
                return null;
            }
            Identifier finalBiomeId = context.biomeRegistry().getKey(finalBiome.value());
            if (finalBiomeId != null
                    && LatitudeBiomes.villageVariantVsBiomeMismatch(
                            candidate.structureId().getPath(), finalBiomeId.toString())) {
                tally.rejectedVillage++;
                return null;
            }
        }
        try {
            StructureStart generatedStart = candidate.structure().generate(
                    candidate.holder(),
                    context.level().dimension(),
                    context.level().registryAccess(),
                    context.generator(),
                    context.finalBiomeSource(),
                    context.climateSampler(),
                    context.randomState(),
                    context.templateManager(),
                    context.seed(),
                    candidateChunk,
                    0,
                    context.level(),
                    candidate.structure().biomes()::contains);
            if (generatedStart == null || !generatedStart.isValid()) {
                tally.rejectedGeneration++;
                return null;
            }
            if (!evaluateGeneratedFootprint(context, candidate, generatedStart, tally)) {
                return null;
            }
            BlockPos center = generatedStart.getBoundingBox().getCenter();
            return new BlockPos(
                    center.getX(),
                    generatedStart.getBoundingBox().maxY() + 2,
                    center.getZ());
        } catch (RuntimeException generationFailure) {
            tally.resolveFailures++;
            return null;
        }
    }

    /**
     * Applies the shared whole-footprint law to a real generated start, so locate can never
     * report a site the generation guard would veto: no structure may reach into the east/west
     * danger band, and structures covered by the badlands-desolation ruling must keep their
     * entire footprint out of badlands country.
     */
    private static boolean evaluateGeneratedFootprint(
            SearchContext context,
            Candidate candidate,
            StructureStart generatedStart,
            Tally tally) {
        BoundingBox footprint = generatedStart.getBoundingBox();
        if (StructureSitingPolicy.intersectsEastWestDangerZone(
                footprint.minX(), footprint.maxX(), context.worldRadius())) {
            tally.rejectedEwDanger++;
            return false;
        }
        String structurePath = candidate.structureId() != null
                ? candidate.structureId().getPath()
                : null;
        if (!StructureSitingPolicy.requiresBadlandsFreeFootprint(
                structurePath, candidate.village())) {
            return true;
        }

        List<String> sampledBiomes = new ArrayList<>();
        for (StructureSitingPolicy.FootprintSample sample :
                StructureSitingPolicy.footprintSamples(
                        footprint.minX(), footprint.maxX(), footprint.minZ(), footprint.maxZ())) {
            Holder<Biome> finalBiome = context.finalBiomeResolver().getNoiseBiome(
                    Math.floorDiv(sample.x(), 4),
                    Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                    Math.floorDiv(sample.z(), 4));
            Identifier biomeId = context.biomeRegistry().getKey(finalBiome.value());
            if (biomeId != null) {
                sampledBiomes.add(biomeId.toString());
            }
        }
        if (StructureSitingPolicy.shouldRejectBadlandsFootprint(
                structurePath, candidate.village(), sampledBiomes)) {
            tally.rejectedBadlandsPolicy++;
            return false;
        }
        return true;
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
            BiomeResolver finalBiomeResolver,
            Climate.Sampler climateSampler,
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

    private record PendingTeleport(
            UUID token,
            net.minecraft.resources.ResourceKey<Level> level,
            int x,
            int z,
            int minimumY,
            boolean mayBeBuried,
            long expiresAtMs) {
    }

    private static final class Tally {
        private int tested;
        private int rejectedPlacement;
        private int rejectedBiome;
        private int rejectedVillage;
        private int rejectedGeneration;
        private int rejectedEwDanger;
        private int rejectedBadlandsPolicy;
        private int resolveFailures;
        private int outOfBorder;
        private int ringsScanned;
    }
}
