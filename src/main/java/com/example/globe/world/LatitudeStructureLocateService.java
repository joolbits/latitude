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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Replaces vanilla's {@code /locate structure} search in Latitude worlds. Vanilla's own search
 * (100 rings, no border awareness, biome-tested against the raw un-repainted biome source) can
 * report a structure "found" tens of thousands of blocks past the playable world, in a biome
 * Latitude would never actually place it in — and for a never-visited chunk it pays for that
 * wrong answer by driving real chunk generation up to STRUCTURE_STARTS (multi-second stalls).
 *
 * <p>This mirrors vanilla's own nearest-first expanding-ring search over
 * {@link RandomSpreadStructurePlacement} candidates ({@link #search}), but bounds it to the
 * Latitude world border and tests each candidate the same way the placement guard
 * ({@code ExtremePolarVillageStartGuardMixin}) does. For most structures that guard's condition
 * is a single one: Latitude's final, repainted biome must satisfy the structure's biome tag.
 * Villages carry three more of the guard's own conditions on top — the polar-village latitude
 * limit, a declared-climate-vs-band mismatch, and a real terrain-suitability sample — because the
 * guard applies all four for villages and none of the fourth for anything else; a search that only
 * replicated the shared condition would report villages the guard would still veto. See
 * {@link #evaluateVillageCandidate} for the mirror.
 *
 * <p>Deliberately narrow: only claims the command when every placement resolved for the
 * requested structure(s) is {@link RandomSpreadStructurePlacement} (covers pyramids, mineshafts,
 * villages, ocean ruins, shipwrecks, outposts, and similar). Anything else (concentric-rings
 * placements such as strongholds, or an unrecognized placement type) is left to vanilla's
 * original, unmodified path.
 *
 * <p>The search itself runs off the server thread ({@link Util#backgroundExecutor()}) — the same
 * pool vanilla uses for chunk generation and other worldgen-math work, and the one this codebase's
 * own atlas/preview tooling already relies on for calling this class of generation-math function
 * outside the tick loop. Every value the search touches (biome/structure registries, the biome
 * source, the noise-based generator, {@link RandomState}, the world border) is a per-world
 * configuration object queried through pure functions of position and seed, not live per-tick
 * server state, so this is safe without additional synchronization. The result is only ever
 * delivered back to the player, and the diagnostic log line only ever written, after hopping back
 * onto the main thread via {@link CommandSourceStack#getServer()}{@code .execute(...)}.
 *
 * <p>The answer itself is the generated start's own bounding-box center, lifted two blocks above
 * its top — not {@code getLocatePos()}'s placement-cell corner at a hardcoded Y=0 — and the
 * printed coordinate is clickable: it mints a single-use, player-bound, five-minute token that
 * {@link #runPendingTeleport} redeems. That is why this class reports the result itself instead of
 * handing it to {@code LocateCommand.showLocateResult}, which can only suggest a {@code /tp}.
 */
public final class LatitudeStructureLocateService {
    private static final int VANILLA_MAX_RINGS = 100;
    private static final long PROGRESS_UPDATE_NANOS = 100_000_000L;
    private static final long TELEPORT_TOKEN_TTL_MS = 5 * 60 * 1_000L;

    /**
     * One outstanding clickable locate destination per player, per server. Written when a result
     * is delivered and read when its coordinate is clicked; both happen on the server thread (the
     * result is delivered through {@code whenCompleteAsync(..., source.getServer())}), as do the
     * disconnect and shutdown hooks below, so no synchronization is needed.
     */
    private static final Map<MinecraftServer, Map<UUID, PendingTeleport>> PENDING_TELEPORTS =
            new IdentityHashMap<>();

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_TELEPORTS.remove(server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                clearPendingTeleport(server, handler.player));
    }

    private LatitudeStructureLocateService() {
    }

    /**
     * Executes the player-bound, one-time action behind a clickable locate coordinate.
     *
     * <p>Registered without a permission gate (see {@code LatitudeToolsCommand}), so every
     * authorization decision lives here: the token must be the one this server minted, for this
     * player, and it must not have expired. It is consumed on first use whether or not the
     * teleport then succeeds, so a token can never be replayed.
     */
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
        // Land on the ground above the structure, not at a computed altitude.
        //
        // This used to be max(player's current Y, structure top + 2), which had two ways to hurt
        // someone. A buried pyramid's top-plus-two is still inside the dune above it, so the player
        // arrived encased in sand and suffocated. And a player who happened to be flying high kept
        // that altitude, so accepting a locate meant a long fall -- clearing fallDistance stops the
        // damage on arrival, not the drop that follows.
        //
        // The surface heightmap is the first free column above the terrain, and structures are
        // already in it because they generate before this runs. Taking the higher of that and the
        // structure top keeps the "above what you asked for" promise while guaranteeing the
        // destination is not inside a block.
        int surfaceY = targetLevel.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pending.x(), pending.z());
        double landingY = Math.max(surfaceY, pending.minimumY());
        player.teleportTo(
                targetLevel,
                pending.x() + 0.5,
                landingY,
                pending.z() + 0.5,
                EnumSet.noneOf(Relative.class),
                player.getYRot(),
                player.getXRot(),
                true);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0;
        if (pending.mayBeBuried()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "Centered above the generated desert pyramid; it may be buried below Y "
                                    + (pending.minimumY() - 2)
                                    + "."),
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
     * Verdict for one deterministic placement-grid candidate. Locate and the dev structure atlas
     * share these evaluators, so a dot on the atlas and a locate answer can never disagree.
     */
    public enum CandidateVerdict {
        ACCEPTED,
        REJECTED_PLACEMENT,
        REJECTED_CLIMATE_OR_POLAR,
        REJECTED_TERRAIN,
        REJECTED_VARIANT_MISMATCH,
        REJECTED_BADLANDS_POLICY,
        REJECTED_EW_DANGER,
        REJECTED_CUSTOM_BIOME_SITING,
        REJECTED_BIOME,
        PICK_FAILED
    }

    /**
     * Result of one atlas sweep. A refused sweep carries the reason, so an empty artifact always
     * explains itself instead of reading as "this world contains no structures".
     */
    public record AtlasSweep(
            List<AtlasCandidate> candidates,
            String refusalReason,
            int structuresSwept,
            int placementsSwept) {
    }

    /** One evaluated placement-grid candidate, for the dev structure atlas. */
    public record AtlasCandidate(
            String structureId,
            boolean village,
            int blockX,
            int blockZ,
            CandidateVerdict verdict) {
    }

    /**
     * Sweeps every random-spread structure's full placement grid inside the world border and
     * evaluates each candidate with the exact filters locate uses (placement frequency, climate
     * band, terrain suitability, badlands policy, final-biome containment). No chunks are loaded
     * or written and no {@code Structure.generate} preview runs, so verdicts are candidate-level:
     * an ACCEPTED dot is a site the guards would admit, not a promise the jigsaw succeeded.
     * Dev-tooling only; the caller owns output and lifetime.
     */
    public static AtlasSweep sweepStructureCandidatesForAtlas(ServerLevel level) {
        List<AtlasCandidate> rows = new ArrayList<>();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator generator)) {
            return new AtlasSweep(rows,
                    "overworld generator is not noise-based; nothing to sweep", 0, 0);
        }
        if (!GlobeMod.shouldApplyLatitudeWorldgen(generator)) {
            // The measurement is only meaningful where Latitude actually owns worldgen. A vanilla
            // preview world would answer with vanilla's placement, which is not what the atlas is
            // for, so refuse loudly rather than emit numbers that look like Latitude's.
            return new AtlasSweep(rows,
                    "Latitude does not own worldgen in this overworld — create the preview world "
                            + "with a globe world preset (level-type), not minecraft:normal",
                    0, 0);
        }
        Registry<Structure> structureRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        ChunkGeneratorStructureState structureState = level.getChunkSource().getGeneratorState();
        RandomState randomState = level.getChunkSource().randomState();
        BiomeSource rawSource = generator.getBiomeSource();
        if (rawSource instanceof LatitudeBiomeSource wrapped) {
            rawSource = wrapped.original();
        }
        long seed = structureState.getLevelSeed();
        int worldRadius = GlobeMod.borderRadiusForNoiseGenerator(generator);
        WorldBorder border = level.getWorldBorder();
        int radiusChunks = Math.max(1, worldRadius >> 4);
        int structuresSwept = 0;
        int placementsSwept = 0;

        for (Holder.Reference<Structure> reference : structureRegistry.listElements().toList()) {
            Identifier structureId = reference.key().identifier();
            for (StructurePlacement placement
                    : structureState.getPlacementsForStructure(reference)) {
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                    continue;
                }
                placementsSwept++;
                boolean village = reference.is(StructureTags.VILLAGE)
                        || structureId.getPath().contains("village");
                int spacing = Math.max(1, spread.spacing());
                int minCell = Math.floorDiv(-radiusChunks, spacing);
                int maxCell = Math.floorDiv(radiusChunks, spacing);
                for (int cellZ = minCell; cellZ <= maxCell; cellZ++) {
                    for (int cellX = minCell; cellX <= maxCell; cellX++) {
                        ChunkPos candidateChunk = spread.getPotentialStructureChunk(
                                seed, cellX * spacing, cellZ * spacing);
                        if (!border.isWithinBounds(candidateChunk)) {
                            continue;
                        }
                        CandidateVerdict verdict;
                        if (!spread.isStructureChunk(
                                structureState, candidateChunk.x, candidateChunk.z)) {
                            verdict = CandidateVerdict.REJECTED_PLACEMENT;
                        } else if (village) {
                            verdict = evaluateVillageCandidate(
                                    new Candidate(reference, spread, structureId, true),
                                    candidateChunk, biomeRegistry, rawSource, generator,
                                    randomState, level, worldRadius);
                        } else {
                            verdict = evaluateCandidate(
                                    reference.value(), structureId.getPath(), candidateChunk,
                                    biomeRegistry, rawSource, generator, randomState, level,
                                    worldRadius);
                        }
                        rows.add(new AtlasCandidate(
                                structureId.toString(),
                                village,
                                candidateChunk.getMiddleBlockX(),
                                candidateChunk.getMiddleBlockZ(),
                                verdict));
                    }
                }
                structuresSwept++;
            }
        }
        return new AtlasSweep(rows, null, structuresSwept, placementsSwept);
    }

    /** Maps a shared evaluator verdict onto the locate command's diagnostic counters. */
    private static void applyVerdictTally(Tally tally, CandidateVerdict verdict) {
        switch (verdict) {
            case ACCEPTED -> { }
            case REJECTED_PLACEMENT -> tally.rejectedPlacement++;
            case REJECTED_CLIMATE_OR_POLAR, REJECTED_TERRAIN, REJECTED_VARIANT_MISMATCH ->
                    tally.rejectedVillageGuard++;
            case REJECTED_BADLANDS_POLICY -> tally.rejectedBadlandsPolicy++;
            case REJECTED_EW_DANGER -> tally.rejectedEwDanger++;
            case REJECTED_CUSTOM_BIOME_SITING -> tally.rejectedCustomBiomeSiting++;
            case REJECTED_BIOME -> tally.rejectedPicked++;
            case PICK_FAILED -> tally.pickFailed++;
        }
    }

    /**
     * Claims {@code /locate structure} in Latitude worlds when every matched structure resolves
     * only to random-spread placements. The search itself runs asynchronously and owns the same
     * visible blue boss bar as Latitude biome search, so a slow result reads as working without a
     * separate legacy chat acknowledgement.
     *
     * @return true when the command was claimed (the answer may still be pending)
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

        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> matched = structureRegistry.listElements()
                .map(reference -> (Holder<Structure>) reference)
                .filter(target)
                .toList();
        if (matched.isEmpty()) {
            return false;
        }

        ChunkGeneratorStructureState structureState = level.getChunkSource().getGeneratorState();
        List<Candidate> candidateSources = new ArrayList<>();
        for (Holder<Structure> holder : matched) {
            for (StructurePlacement placement : structureState.getPlacementsForStructure(holder)) {
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                    // Unsupported placement type present (e.g. a stronghold-style concentric-rings
                    // structure, possibly mixed into a tag search). Defer the whole request to
                    // vanilla rather than give a partial, silently-incomplete answer.
                    return false;
                }
                Identifier structureId = structureRegistry.getKey(holder.value());
                boolean village = structureId != null
                        && (holder.is(StructureTags.VILLAGE) || structureId.getPath().contains("village"));
                candidateSources.add(new Candidate(holder, spread, structureId, village));
            }
        }
        if (candidateSources.isEmpty()) {
            return false;
        }

        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        BlockPos origin = BlockPos.containing(source.getPosition());
        int worldRadius = GlobeMod.borderRadiusForNoiseGenerator(generator);
        WorldBorder border = level.getWorldBorder();
        RandomState randomState = level.getChunkSource().randomState();
        BiomeSource rawSource = generator.getBiomeSource();
        if (rawSource instanceof LatitudeBiomeSource wrapped) {
            rawSource = wrapped.original();
        }
        long seed = structureState.getLevelSeed();
        BiomeSource finalRawSource = rawSource;

        ServerPlayer requester = source.getPlayer();
        ServerBossEvent bossBar = new ServerBossEvent(
                Component.literal("Searching for " + target.asPrintable() + "..."),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(0.0F);
        if (requester != null) {
            bossBar.addPlayer(requester);
        }
        ProgressReporter progress = new ProgressReporter(
                source, target.asPrintable(), bossBar,
                effectiveRingCount(candidateSources, worldRadius));

        long started = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> {
                    Tally tally = new Tally();
                    Pair<BlockPos, Holder<Structure>> result = search(
                            candidateSources, origin, worldRadius, border,
                            biomeRegistry, finalRawSource, generator, structureState,
                            randomState, level, seed, tally,
                            progress);
                    return new SearchOutcome(result, tally);
                }, Util.backgroundExecutor())
                .whenCompleteAsync((outcome, error) -> {
                    // MinecraftServer.execute runs the task INLINE on the calling thread once the
                    // server is stopped (scheduleExecutables() goes false), and the background
                    // executor outlives an integrated server. Quitting to title mid-search would
                    // therefore land this continuation -- and its PENDING_TELEPORTS write -- on a
                    // worker thread, where the "server thread only, no synchronization needed"
                    // contract this class documents no longer holds. Nothing to report to a server
                    // that is gone, so stop.
                    if (source.getServer().isStopped()) {
                        return;
                    }
                    bossBar.removeAllPlayers();
                    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                    if (error != null) {
                        GlobeMod.LOGGER.warn("[Latitude] structure locate failed for target={}",
                                target.asPrintable(), error);
                        source.sendFailure(Component.translatableEscape(
                                "commands.locate.structure.not_found", target.asPrintable()));
                        return;
                    }
                    Pair<BlockPos, Holder<Structure>> result = outcome.result();
                    Tally tally = outcome.tally();
                    if (result == null) {
                        source.sendFailure(Component.translatableEscape(
                                "commands.locate.structure.not_found", target.asPrintable()));
                    } else {
                        // Reported by us rather than through LocateCommand.showLocateResult,
                        // because vanilla's reporter can only SUGGEST a /tp into the chat box --
                        // its coordinate carries a ClickEvent.SuggestCommand. A found structure
                        // should be one click away, so showTeleportLocateResult builds the same
                        // "commands.locate.structure.success" line with a one-time token action.
                        //
                        // On the Y question that vanilla's showY flag used to decide here: the
                        // reported position is no longer getLocatePos()'s hardcoded Y=0 chunk-grid
                        // hint (which, printed and teleported to literally, once put players in
                        // deep dark). It is now the generated start's own bounding box, centered
                        // and lifted to maxY + 2. That height is validated and trustworthy -- but
                        // it is the top of the structure, not where the structure IS, so printing
                        // it would read as "the mansion is at Y 105" when its floor is at 72. So
                        // the coordinate still prints "~", exactly as vanilla does for structures,
                        // and the trustworthy height goes where it is actually load-bearing: the
                        // teleport lands at max(player Y, maxY + 2), which can neither drop the
                        // player below the roof nor below their own current altitude.
                        showTeleportLocateResult(source, target, origin, result);
                    }
                    GlobeMod.LOGGER.info(
                            "[Latitude] structure locate target={} worldRadius={} candidatesTested={} rejectedPlacement={} rejectedPickedBiome={} rejectedBadlandsPolicy={} rejectedEwDanger={} rejectedCustomBiomeSiting={} rejectedVillageGuard={} rejectedInvalidStart={} startValidationFailures={} pickFailures={} outOfBorder={} ringsScanned={} elapsedMs={} found={}",
                            target.asPrintable(), worldRadius, tally.tested, tally.rejectedPlacement,
                            tally.rejectedPicked, tally.rejectedBadlandsPolicy,
                            tally.rejectedEwDanger, tally.rejectedCustomBiomeSiting,
                            tally.rejectedVillageGuard, tally.rejectedInvalidStart,
                            tally.startValidationFailures, tally.pickFailed, tally.outOfBorder,
                            tally.ringsScanned, elapsed.toMillis(), result != null);
                }, source.getServer());
        return true;
    }

    private record SearchOutcome(Pair<BlockPos, Holder<Structure>> result, Tally tally) {
    }

    /**
     * Prints vanilla's own success line with a coordinate the player can click to travel to.
     *
     * <p>The distance is measured in the horizontal plane only, matching what vanilla reports for
     * structures. The reported position now carries a real height (see the call site), so a 3D
     * distance would quietly inflate the answer for anything far above or below the player.
     */
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

        // Fallback for a non-player source (command block, server console): there is nobody to
        // teleport and nobody to bind a token to, so keep vanilla's suggest-only behaviour.
        ClickEvent clickEvent = new ClickEvent.SuggestCommand(
                "/tp " + location.getX() + " ~ " + location.getZ());
        ServerPlayer requester = source.getPlayer();
        // The disconnect hook fires while the search is still running, so a player who leaves
        // mid-search would have their token minted AFTER the cleanup that was meant to remove it,
        // stranding an entry the hook can no longer see. Ask the player list rather than trusting
        // the captured reference, which stays non-null after the player is gone.
        if (requester != null
                && source.getServer().getPlayerList().getPlayer(requester.getUUID()) == null) {
            requester = null;
        }
        if (requester != null) {
            // An integrated client opens a fresh MinecraftServer per world, and a search that
            // finishes as one is shutting down could otherwise strand its key here for the rest
            // of the session. The SERVER_STOPPED hook cannot catch that race; this does.
            PENDING_TELEPORTS.keySet().removeIf(MinecraftServer::isStopped);
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
                        Component.translatable(
                                "chat.coordinates", location.getX(), "~", location.getZ()))
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(finalClickEvent)
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.coordinates.tooltip"))));
        Component message = Component.translatable(
                "commands.locate.structure.success", target.asPrintable(), coordinates, distance);
        if (mayBeBuried) {
            message = message.copy().append(Component.literal(
                    " Desert pyramids may be buried; generated structure top is near Y "
                            + (location.getY() - 2)
                            + "."));
        }
        Component finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
    }

    /**
     * A desert pyramid is the one supported structure that routinely generates with its roof at
     * or below the sand, so arriving on top of it can look like arriving at nothing. Say so
     * rather than let the player conclude the search lied.
     */
    private static boolean mayBeBuried(Holder<Structure> structure) {
        return structure.unwrapKey()
                .map(key -> key.identifier().getPath().equals("desert_pyramid"))
                .orElse(false);
    }

    private static Pair<BlockPos, Holder<Structure>> search(
            List<Candidate> candidateSources,
            BlockPos origin,
            int worldRadius,
            WorldBorder border,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            ChunkGeneratorStructureState structureState,
            RandomState randomState,
            ServerLevel level,
            long seed,
            Tally tally,
            ProgressReporter progress) {
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;

        for (int ring = 0; ring <= VANILLA_MAX_RINGS; ring++) {
            tally.ringsScanned = ring + 1;
            Pair<BlockPos, Holder<Structure>> ringBest = null;
            double ringBestDistSqr = Double.MAX_VALUE;
            boolean ringExceedsBorder = true;

            for (Candidate candidate : candidateSources) {
                int spacing = candidate.placement.spacing();
                int reach = spacing * ring;
                if ((double) reach > worldRadius + spacing) {
                    // Every cell this placement could still produce on this ring is already past
                    // the border; stop growing rings for it (checked per-candidate below too).
                    continue;
                }

                for (int dz = -ring; dz <= ring; dz++) {
                    boolean dzBorder = dz == -ring || dz == ring;
                    for (int dx = -ring; dx <= ring; dx++) {
                        boolean dxBorder = dx == -ring || dx == ring;
                        if (!dzBorder && !dxBorder) {
                            continue;
                        }
                        ChunkPos candidateChunk = candidate.placement.getPotentialStructureChunk(
                                seed, originChunkX + spacing * dx, originChunkZ + spacing * dz);
                        if (!border.isWithinBounds(candidateChunk)) {
                            tally.outOfBorder++;
                            continue;
                        }
                        ringExceedsBorder = false;
                        tally.tested++;

                        if (!candidate.placement().isStructureChunk(
                                structureState, candidateChunk.x, candidateChunk.z)) {
                            tally.rejectedPlacement++;
                            continue;
                        }
                        CandidateVerdict verdict = candidate.village()
                                ? evaluateVillageCandidate(candidate, candidateChunk, biomeRegistry,
                                        rawSource, generator, randomState, level, worldRadius)
                                : evaluateCandidate(candidate.structure(),
                                        candidate.structureId() != null
                                                ? candidate.structureId().getPath() : null,
                                        candidateChunk, biomeRegistry,
                                        rawSource, generator, randomState, level, worldRadius);
                        applyVerdictTally(tally, verdict);
                        if (verdict != CandidateVerdict.ACCEPTED) {
                            continue;
                        }
                        StructureStart generatedStart = validGeneratedStart(
                                candidate, candidateChunk, generator,
                                randomState, level, seed, tally);
                        if (generatedStart == null) {
                            continue;
                        }
                        CandidateVerdict footprintVerdict = evaluateGeneratedFootprint(
                                generatedStart, candidate, biomeRegistry, generator,
                                randomState, worldRadius);
                        applyVerdictTally(tally, footprintVerdict);
                        if (footprintVerdict != CandidateVerdict.ACCEPTED) {
                            continue;
                        }
                        // Answer with where the structure actually IS, not where its placement
                        // cell begins. getLocatePos() returns the candidate chunk's min corner at
                        // a hardcoded Y=0; the jigsaw can settle its pieces well away from that
                        // corner, so a locate answered from it can point at empty ground beside
                        // the build and gives no usable height at all. The generated start's own
                        // bounding-box center, lifted two blocks above its top, is a position this
                        // search has already validated in full.
                        BlockPos generatedTarget = generatedCenterTarget(generatedStart);
                        if (!border.isWithinBounds(generatedTarget)) {
                            // The candidate chunk was in bounds but the settled footprint's center
                            // is not. Report only what the player can actually reach.
                            tally.outOfBorder++;
                            continue;
                        }
                        // Rank horizontally: the target's height varies per candidate now, so a
                        // 3D comparison would prefer a shallower structure over a nearer one.
                        double targetDx = origin.getX() - generatedTarget.getX();
                        double targetDz = origin.getZ() - generatedTarget.getZ();
                        double distSqr = targetDx * targetDx + targetDz * targetDz;
                        if (distSqr < ringBestDistSqr) {
                            ringBestDistSqr = distSqr;
                            ringBest = Pair.of(generatedTarget, candidate.holder());
                        }
                    }
                }
            }

            progress.update(ring + 1);
            if (ringBest != null) {
                return ringBest;
            }
            if (ringExceedsBorder && ring > 0) {
                // Every placement's reach on this ring (and therefore every later ring too) is
                // entirely past the border. No further ring can produce an in-bounds candidate.
                break;
            }
        }
        return null;
    }

    private static int effectiveRingCount(List<Candidate> candidates, int worldRadius) {
        return candidates.stream()
                .mapToInt(candidate -> {
                    int spacing = candidate.placement().spacing();
                    return Math.min(VANILLA_MAX_RINGS, (worldRadius + spacing) / spacing) + 1;
                })
                .max()
                .orElse(VANILLA_MAX_RINGS + 1);
    }

    /** Throttles background search progress and applies boss-bar changes on the server thread. */
    private static final class ProgressReporter {
        private final CommandSourceStack source;
        private final String targetName;
        private final ServerBossEvent bossBar;
        private final int totalRings;
        private long nextUpdateNanos;

        private ProgressReporter(
                CommandSourceStack source,
                String targetName,
                ServerBossEvent bossBar,
                int totalRings) {
            this.source = source;
            this.targetName = targetName;
            this.bossBar = bossBar;
            this.totalRings = Math.max(1, totalRings);
        }

        private void update(int completedRings) {
            long now = System.nanoTime();
            if (now < nextUpdateNanos) {
                return;
            }
            int percent = Mth.clamp(
                    (int) ((long) completedRings * 100L / totalRings), 1, 99);
            if (source.getServer().isStopped()) {
                return;
            }
            source.getServer().execute(() -> {
                bossBar.setProgress(percent / 100.0F);
                bossBar.setName(Component.literal(
                        "Searching for " + targetName + "... " + percent + "%"));
            });
            nextUpdateNanos = now + PROGRESS_UPDATE_NANOS;
        }
    }

    /** Returns Minecraft's real, write-free generated start, or null when the candidate is invalid. */
    private static StructureStart validGeneratedStart(
            Candidate candidate,
            ChunkPos candidateChunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            long seed,
            Tally tally) {
        try {
            StructureStart start = candidate.structure().generate(
                    candidate.holder(),
                    level.dimension(),
                    level.registryAccess(),
                    generator,
                    generator.getBiomeSource(),
                    randomState,
                    level.getServer().getStructureManager(),
                    seed,
                    candidateChunk,
                    0,
                    level,
                    candidate.structure().biomes()::contains);
            if (!start.isValid()) {
                tally.rejectedInvalidStart++;
                return null;
            }
            return start;
        } catch (RuntimeException validationFailure) {
            tally.startValidationFailures++;
            return null;
        }
    }

    /**
     * The position a locate reports for an accepted candidate: the generated start's own
     * bounding-box center, two blocks above its top. Both halves matter — the center is where the
     * settled build is, and maxY + 2 is a height a player can be put down on safely.
     */
    private static BlockPos generatedCenterTarget(StructureStart start) {
        BlockPos center = start.getBoundingBox().getCenter();
        return new BlockPos(center.getX(), start.getBoundingBox().maxY() + 2, center.getZ());
    }

    private static CandidateVerdict evaluateGeneratedFootprint(
            StructureStart start,
            Candidate candidate,
            Registry<Biome> biomeRegistry,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            int worldRadius) {
        var footprint = start.getBoundingBox();
        if (StructureSitingPolicy.intersectsEastWestDangerZone(
                footprint.minX(), footprint.maxX(), worldRadius)) {
            return CandidateVerdict.REJECTED_EW_DANGER;
        }
        String structurePath = candidate.structureId() != null
                ? candidate.structureId().getPath()
                : null;
        String structureNamespace = candidate.structureId() != null
                ? candidate.structureId().getNamespace()
                : null;
        // Mirror of the placement guard's woodland-mansion rule. The guard runs on real
        // generation only, never on this class's write-free preview, so locate must apply the
        // same rule itself or it would report a mansion the guard is about to refuse.
        if (StructureSitingPolicy.requiresVanillaBiomeSiting(structureNamespace, structurePath)) {
            BlockPos center = footprint.getCenter();
            Holder<Biome> centerBiome = generator.getBiomeSource().getNoiseBiome(
                    Math.floorDiv(center.getX(), 4),
                    Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                    Math.floorDiv(center.getZ(), 4),
                    randomState.sampler());
            Identifier centerBiomeId = biomeRegistry.getKey(centerBiome.value());
            if (centerBiomeId != null && StructureSitingPolicy.shouldRejectCustomBiomeSiting(
                    structureNamespace, structurePath, centerBiomeId.getNamespace())) {
                return CandidateVerdict.REJECTED_CUSTOM_BIOME_SITING;
            }
        }
        if (!StructureSitingPolicy.requiresBadlandsFreeFootprint(
                structurePath, candidate.village())) {
            return CandidateVerdict.ACCEPTED;
        }

        List<String> sampledBiomes = new ArrayList<>();
        for (StructureSitingPolicy.FootprintSample sample :
                StructureSitingPolicy.footprintSamples(
                        footprint.minX(), footprint.maxX(), footprint.minZ(), footprint.maxZ())) {
            Holder<Biome> finalBiome = generator.getBiomeSource().getNoiseBiome(
                    Math.floorDiv(sample.x(), 4),
                    Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                    Math.floorDiv(sample.z(), 4),
                    randomState.sampler());
            Identifier biomeId = biomeRegistry.getKey(finalBiome.value());
            if (biomeId != null) {
                sampledBiomes.add(biomeId.toString());
            }
        }
        return StructureSitingPolicy.shouldRejectBadlandsFootprint(
                structurePath, candidate.village(), sampledBiomes)
                ? CandidateVerdict.REJECTED_BADLANDS_POLICY
                : CandidateVerdict.ACCEPTED;
    }

    /**
     * The general (non-village) path — mirrors {@code ExtremePolarVillageStartGuardMixin}'s
     * {@code else if} branch exactly: Latitude's final, repainted biome at the candidate must
     * satisfy the structure's own biome tag. That guard hands vanilla the repainted biome source
     * at generation time, so this single condition is exactly what real generation applies for
     * every structure that isn't a village. Returns the repainted biome on success, purely for
     * logging symmetry with the guard; the caller only needs the pass/fail outcome.
     */
    private static CandidateVerdict evaluateCandidate(
            Structure structure,
            String structurePath,
            ChunkPos candidateChunk,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            int worldRadius) {
        // Sample at the chunk's middle block, exactly as the placement guard
        // (ExtremePolarVillageStartGuardMixin) does — getLocatePos returns the chunk's min corner,
        // which can straddle a different biome cell than the one real generation judges.
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        // Judge the biome the world will PAINT at this column — the authoritative wrapper
        // source, exactly what populateBiomes writes — never a fresh pick with different terrain
        // evidence. Re-picking under STRUCTURE_START/VILLAGE_START computed preview heights the
        // SOURCE paint path skips, and the two disagreed about where desert is: a measured world
        // at ~1.7% desert produced 0/1348 accepted desert-village candidates with a rejection
        // profile identical to a world with half the desert (2026-08-16).
        Holder<Biome> pickedBiome;
        try {
            pickedBiome = generator.getBiomeSource().getNoiseBiome(
                    Math.floorDiv(blockX, 4),
                    Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                    Math.floorDiv(blockZ, 4),
                    randomState.sampler());
        } catch (RuntimeException pickFailure) {
            return CandidateVerdict.PICK_FAILED;
        }
        if (pickedBiome == null) {
            return CandidateVerdict.PICK_FAILED;
        }
        // Mirror of the generation-time badlands ruling: desert-declared structures and surface
        // outposts never generate on badlands, so locate must never report a site there either.
        Identifier pickedId = biomeRegistry.getKey(pickedBiome.value());
        if (pickedId != null && structurePath != null
                && VillageBiomeAdmissionPolicy.shouldRefuseStructureInVillageFreeBiome(
                        structurePath, pickedId.toString())) {
            return CandidateVerdict.REJECTED_BADLANDS_POLICY;
        }
        return CandidateVerdict.ACCEPTED;
    }

    /**
     * The village path — mirrors {@code ExtremePolarVillageStartGuardMixin}'s village branch,
     * which applies four conditions no other structure gets: the polar-village latitude veto, a
     * declared-climate-vs-band mismatch, a real terrain-suitability sample (the same 5x5 height
     * grid the guard itself samples via {@link VillageTerrainSuitabilityPolicy}), and a
     * village-variant-vs-biome mismatch — on top of the same biome-tag baseline every structure
     * needs. A locate-time evaluator that only replicated the tag check (what this class did until
     * this fix) reported villages the guard would still veto: a real, reproduced case was a
     * reported village whose site had nothing built there.
     */
    private static CandidateVerdict evaluateVillageCandidate(
            Candidate candidate,
            ChunkPos candidateChunk,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            int worldRadius) {
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        String structurePath = candidate.structureId().getPath();

        double absDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, worldRadius);
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        if (LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, worldRadius)
                || LatitudeBiomes.villageClimateVsBandMismatch(structurePath, band)) {
            return CandidateVerdict.REJECTED_CLIMATE_OR_POLAR;
        }

        int[] terrainHeights = new int[VillageTerrainSuitabilityPolicy.SAMPLE_COUNT];
        int terrainIndex = 0;
        for (int dz = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                dz <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                dz += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
            for (int dx = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                    dx <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                    dx += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
                terrainHeights[terrainIndex++] = generator.getBaseHeight(
                        blockX + dx, blockZ + dz, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            }
        }
        if (!VillageTerrainSuitabilityPolicy.isSuitable(terrainHeights)) {
            return CandidateVerdict.REJECTED_TERRAIN;
        }

        // Same single-authority rule as the generic evaluator and the placement guard: the
        // painted biome comes from the wrapper source, never from a re-pick with different
        // terrain evidence (2026-08-16).
        Holder<Biome> finalBiome;
        try {
            finalBiome = generator.getBiomeSource().getNoiseBiome(
                    Math.floorDiv(blockX, 4),
                    Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                    Math.floorDiv(blockZ, 4),
                    randomState.sampler());
        } catch (RuntimeException pickFailure) {
            return CandidateVerdict.PICK_FAILED;
        }
        if (finalBiome == null) {
            return CandidateVerdict.PICK_FAILED;
        }
        Identifier finalBiomeId = biomeRegistry.getKey(finalBiome.value());
        if (finalBiomeId != null
                && LatitudeBiomes.villageVariantVsBiomeMismatch(structurePath, finalBiomeId.toString())) {
            return CandidateVerdict.REJECTED_VARIANT_MISMATCH;
        }
        if (!candidate.structure().biomes().contains(finalBiome)) {
            return CandidateVerdict.REJECTED_BIOME;
        }
        return CandidateVerdict.ACCEPTED;
    }

    /** One clickable locate destination, bound to the player it was shown to and to a deadline. */
    private record PendingTeleport(
            UUID token,
            ResourceKey<Level> level,
            int x,
            int z,
            int minimumY,
            boolean mayBeBuried,
            long expiresAtMs) {
    }

    /** Per-search diagnostic counters, logged once per command so a "not found" is explainable. */
    private static final class Tally {
        private int tested;
        private int rejectedPlacement;
        private int rejectedPicked;
        private int rejectedBadlandsPolicy;
        private int rejectedEwDanger;
        private int rejectedCustomBiomeSiting;
        private int rejectedVillageGuard;
        private int rejectedInvalidStart;
        private int startValidationFailures;
        private int pickFailed;
        private int outOfBorder;
        private int ringsScanned;
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
}
