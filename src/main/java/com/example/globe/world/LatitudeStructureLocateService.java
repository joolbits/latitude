package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
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
 */
public final class LatitudeStructureLocateService {
    private static final int VANILLA_MAX_RINGS = 100;

    private LatitudeStructureLocateService() {
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

        long started = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> {
                    Tally tally = new Tally();
                    Pair<BlockPos, Holder<Structure>> result = search(
                            candidateSources, origin, worldRadius, border,
                            biomeRegistry, finalRawSource, generator, randomState, level, seed, tally);
                    return new SearchOutcome(result, tally);
                }, Util.backgroundExecutor())
                .whenCompleteAsync((outcome, error) -> {
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
                        // showY=false, matching vanilla's own locateStructure call exactly.
                        // getLocatePos() hardcodes Y=0 (a chunk-grid hint, not a validated surface
                        // height) -- vanilla masks that with "~" in the clickable suggestion so
                        // /tp keeps the player's current Y; showY=true (the bug here, now fixed)
                        // printed and teleported to the literal 0, straight into whatever is
                        // physically at bedrock-to-low-Y, e.g. deep dark.
                        LocateCommand.showLocateResult(source, target, origin, result,
                                "commands.locate.structure.success", false, elapsed);
                    }
                    GlobeMod.LOGGER.info(
                            "[Latitude] structure locate target={} worldRadius={} candidatesTested={} rejectedPickedBiome={} rejectedVillageGuard={} pickFailures={} outOfBorder={} ringsScanned={} elapsedMs={} found={}",
                            target.asPrintable(), worldRadius, tally.tested, tally.rejectedPicked,
                            tally.rejectedVillageGuard, tally.pickFailed, tally.outOfBorder,
                            tally.ringsScanned, elapsed.toMillis(), result != null);
                }, source.getServer());
        return true;
    }

    private record SearchOutcome(Pair<BlockPos, Holder<Structure>> result, Tally tally) {
    }

    private static Pair<BlockPos, Holder<Structure>> search(
            List<Candidate> candidateSources,
            BlockPos origin,
            int worldRadius,
            WorldBorder border,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            long seed,
            Tally tally) {
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

                        BlockPos locatePos = candidate.placement.getLocatePos(candidateChunk);
                        boolean accepted = candidate.village()
                                ? evaluateVillageCandidate(candidate, candidateChunk, biomeRegistry,
                                        rawSource, generator, randomState, level, worldRadius, tally)
                                : evaluateCandidate(candidate.structure(), candidateChunk, biomeRegistry,
                                        rawSource, generator, randomState, level, worldRadius, tally) != null;
                        if (!accepted) {
                            continue;
                        }
                        double distSqr = origin.distSqr(locatePos);
                        if (distSqr < ringBestDistSqr) {
                            ringBestDistSqr = distSqr;
                            ringBest = Pair.of(locatePos, candidate.holder());
                        }
                    }
                }
            }

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

    /**
     * The general (non-village) path — mirrors {@code ExtremePolarVillageStartGuardMixin}'s
     * {@code else if} branch exactly: Latitude's final, repainted biome at the candidate must
     * satisfy the structure's own biome tag. That guard hands vanilla the repainted biome source
     * at generation time, so this single condition is exactly what real generation applies for
     * every structure that isn't a village. Returns the repainted biome on success, purely for
     * logging symmetry with the guard; the caller only needs the pass/fail outcome.
     */
    private static Holder<Biome> evaluateCandidate(
            Structure structure,
            ChunkPos candidateChunk,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            int worldRadius,
            Tally tally) {
        // Sample at the chunk's middle block, exactly as the placement guard
        // (ExtremePolarVillageStartGuardMixin) does — getLocatePos returns the chunk's min corner,
        // which can straddle a different biome cell than the one real generation judges.
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        Holder<Biome> baseBiome = rawSource.getNoiseBiome(
                Math.floorDiv(blockX, 4),
                Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                Math.floorDiv(blockZ, 4),
                randomState.sampler());
        Holder<Biome> pickedBiome;
        try {
            pickedBiome = LatitudeBiomes.pick(
                    biomeRegistry,
                    baseBiome,
                    blockX,
                    blockZ,
                    LatitudeBiomes.SURFACE_CLASSIFY_Y,
                    worldRadius,
                    randomState.sampler(),
                    "STRUCTURE_START",
                    generator,
                    randomState,
                    level);
        } catch (RuntimeException pickFailure) {
            tally.pickFailed++;
            return null;
        }
        if (pickedBiome == null) {
            tally.pickFailed++;
            return null;
        }
        if (!structure.biomes().contains(pickedBiome)) {
            tally.rejectedPicked++;
            return null;
        }
        return pickedBiome;
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
    private static boolean evaluateVillageCandidate(
            Candidate candidate,
            ChunkPos candidateChunk,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            int worldRadius,
            Tally tally) {
        int blockX = candidateChunk.getMiddleBlockX();
        int blockZ = candidateChunk.getMiddleBlockZ();
        String structurePath = candidate.structureId().getPath();

        double absDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, worldRadius);
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        if (LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, worldRadius)
                || LatitudeBiomes.villageClimateVsBandMismatch(structurePath, band)) {
            tally.rejectedVillageGuard++;
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
                terrainHeights[terrainIndex++] = generator.getBaseHeight(
                        blockX + dx, blockZ + dz, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            }
        }
        if (!VillageTerrainSuitabilityPolicy.isSuitable(terrainHeights)) {
            tally.rejectedVillageGuard++;
            return false;
        }

        Holder<Biome> baseBiome = rawSource.getNoiseBiome(
                Math.floorDiv(blockX, 4),
                Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                Math.floorDiv(blockZ, 4),
                randomState.sampler());
        Holder<Biome> pickedBiome;
        try {
            pickedBiome = LatitudeBiomes.pick(
                    biomeRegistry,
                    baseBiome,
                    blockX,
                    blockZ,
                    LatitudeBiomes.SURFACE_CLASSIFY_Y,
                    worldRadius,
                    randomState.sampler(),
                    "VILLAGE_START",
                    generator,
                    randomState,
                    level);
        } catch (RuntimeException pickFailure) {
            tally.pickFailed++;
            return false;
        }
        // A null pick falls back to the raw biome rather than counting as a failure, exactly like
        // ExtremePolarVillageStartGuardMixin's own finalBiome fallback.
        Holder<Biome> finalBiome = pickedBiome != null ? pickedBiome : baseBiome;
        Identifier finalBiomeId = biomeRegistry.getKey(finalBiome.value());
        if (finalBiomeId != null
                && LatitudeBiomes.villageVariantVsBiomeMismatch(structurePath, finalBiomeId.toString())) {
            tally.rejectedVillageGuard++;
            return false;
        }
        if (!candidate.structure().biomes().contains(finalBiome)) {
            tally.rejectedPicked++;
            return false;
        }
        return true;
    }

    /** Per-search diagnostic counters, logged once per command so a "not found" is explainable. */
    private static final class Tally {
        private int tested;
        private int rejectedPicked;
        private int rejectedVillageGuard;
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
