package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
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
 * ({@code ExtremePolarVillageStartGuardMixin}) does: the raw biome must satisfy the structure's
 * biome tag (matching what vanilla's own unmodified check requires) AND Latitude's final,
 * repainted biome must also satisfy it (matching what the guard additionally requires). A
 * candidate that passes both is guaranteed to be the same answer real generation would give,
 * without ever touching the chunk generator.
 *
 * <p>Deliberately narrow: only claims the command when every placement resolved for the
 * requested structure(s) is {@link RandomSpreadStructurePlacement} (covers pyramids, mineshafts,
 * villages, ocean ruins, shipwrecks, outposts, and similar). Anything else (concentric-rings
 * placements such as strongholds, or an unrecognized placement type) is left to vanilla's
 * original, unmodified path.
 */
public final class LatitudeStructureLocateService {
    private static final int VANILLA_MAX_RINGS = 100;

    private LatitudeStructureLocateService() {
    }

    /**
     * Claims {@code /locate structure} in Latitude worlds when every matched structure resolves
     * only to random-spread placements.
     *
     * @return true when the command was claimed and answered synchronously
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
                candidateSources.add(new Candidate(holder, spread));
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

        long started = System.nanoTime();
        int[] candidatesTested = {0};
        Pair<BlockPos, Holder<Structure>> result = search(
                candidateSources, origin, worldRadius, border,
                biomeRegistry, rawSource, generator, randomState, level, seed, candidatesTested);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        if (result == null) {
            source.sendFailure(Component.translatableEscape(
                    "commands.locate.structure.not_found", target.asPrintable()));
        } else {
            LocateCommand.showLocateResult(
                    source, target, origin, result, "commands.locate.structure.success", true, elapsed);
        }
        GlobeMod.LOGGER.info(
                "[Latitude] structure locate target={} worldRadius={} candidatesTested={} elapsedMs={} found={}",
                target.asPrintable(), worldRadius, candidatesTested[0], elapsed.toMillis(), result != null);
        return true;
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
            int[] candidatesTested) {
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;

        for (int ring = 0; ring <= VANILLA_MAX_RINGS; ring++) {
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
                            continue;
                        }
                        ringExceedsBorder = false;
                        candidatesTested[0]++;

                        BlockPos locatePos = candidate.placement.getLocatePos(candidateChunk);
                        Holder<Biome> picked = evaluateCandidate(
                                candidate.structure(), locatePos, biomeRegistry, rawSource,
                                generator, randomState, level, worldRadius);
                        if (picked == null) {
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
     * Tests one candidate location exactly the way real generation would: the raw biome must
     * satisfy the structure's own biome tag (what vanilla's unmodified check requires), and
     * Latitude's repainted biome at the same point must also satisfy it (what the placement guard
     * additionally requires). Returns the repainted biome on success, purely for logging symmetry
     * with the guard; the caller only needs the pass/fail outcome.
     */
    private static Holder<Biome> evaluateCandidate(
            Structure structure,
            BlockPos locatePos,
            Registry<Biome> biomeRegistry,
            BiomeSource rawSource,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            ServerLevel level,
            int worldRadius) {
        int blockX = locatePos.getX();
        int blockZ = locatePos.getZ();
        Holder<Biome> baseBiome = rawSource.getNoiseBiome(
                Math.floorDiv(blockX, 4),
                Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                Math.floorDiv(blockZ, 4),
                randomState.sampler());
        if (!structure.biomes().contains(baseBiome)) {
            return null;
        }
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
            return null;
        }
        if (pickedBiome == null || !structure.biomes().contains(pickedBiome)) {
            return null;
        }
        return pickedBiome;
    }

    private record Candidate(Holder<Structure> holder, RandomSpreadStructurePlacement placement) {
        Structure structure() {
            return holder.value();
        }
    }
}
