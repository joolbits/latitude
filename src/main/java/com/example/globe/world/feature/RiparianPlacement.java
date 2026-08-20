package com.example.globe.world.feature;

import com.example.globe.util.ValueNoise2D;
import com.example.globe.world.LatitudeWorldgenScope;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Keeps a decoration position only where an arid biome meets fresh water, and only inside the
 * coherent stretches of waterline that the world seed marks verdant.
 *
 * <p>This is a decoration filter and nothing else. It never reports a biome, never changes one,
 * and refuses outright unless {@link LatitudeWorldgenScope} says Latitude owns the generator that
 * is currently decorating, so a datapack cannot borrow it into another dimension.</p>
 *
 * <p>The gate is deliberately two-part (maintainer ruling, 2026-08-19: "planting only", and not
 * everywhere):</p>
 * <ul>
 *   <li><b>Water proximity</b> - a bounded ring sample, never a radius-squared sweep. Sixteen
 *       columns (eight compass directions at two distances) are probed through the world-surface
 *       heightmap, so each column costs one heightmap read and one block read.</li>
 *   <li><b>Coherence</b> - one {@link ValueNoise2D} sample at a low frequency, so a verdant bank
 *       runs for hundreds of blocks instead of speckling block by block. Article VI: the
 *       coherence comes from seeded value noise, never from cell hashing.</li>
 * </ul>
 */
public class RiparianPlacement extends PlacementFilter {
    /** Horizontal reach of the water probe, in blocks. */
    public static final int DEFAULT_SEARCH_RADIUS = 10;
    /** How far the ground may stand above the waterline and still count as bank. */
    public static final int DEFAULT_BANK_HEIGHT = 4;
    /** Cell size of the coherence noise, in blocks. */
    public static final int DEFAULT_NOISE_SCALE = 640;
    /** Fraction of qualifying waterline that greens up. */
    public static final double DEFAULT_VERDANT_THRESHOLD = 0.5D;

    /**
     * Keeps the coherence field independent of every other seeded field in the mod: two features
     * that shared a salt would green and dry in lockstep.
     */
    private static final long NOISE_SALT = 0x5216B3D74F0C91A7L;

    /**
     * Eight compass directions. Sampled at two distances each, which is 16 probes per candidate
     * and bounded regardless of how large the search radius is set.
     */
    private static final int[][] COMPASS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    public static final MapCodec<RiparianPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Capped at 15 on purpose: a feature may start 15 blocks into its own chunk, and the
            // decoration region only reaches one chunk out, so 15 + 15 is the last offset that is
            // guaranteed readable.
            Codec.intRange(1, 15)
                    .optionalFieldOf("search_radius", DEFAULT_SEARCH_RADIUS)
                    .forGetter(placement -> placement.searchRadius),
            Codec.intRange(0, 32)
                    .optionalFieldOf("bank_height", DEFAULT_BANK_HEIGHT)
                    .forGetter(placement -> placement.bankHeight),
            Codec.intRange(16, 8192)
                    .optionalFieldOf("noise_scale", DEFAULT_NOISE_SCALE)
                    .forGetter(placement -> placement.noiseScale),
            Codec.doubleRange(0.0D, 1.0D)
                    .optionalFieldOf("verdant_threshold", DEFAULT_VERDANT_THRESHOLD)
                    .forGetter(placement -> placement.verdantThreshold)
    ).apply(instance, RiparianPlacement::new));

    private final int searchRadius;
    private final int bankHeight;
    private final int noiseScale;
    private final double verdantThreshold;

    public RiparianPlacement(int searchRadius, int bankHeight, int noiseScale, double verdantThreshold) {
        this.searchRadius = searchRadius;
        this.bankHeight = bankHeight;
        this.noiseScale = noiseScale;
        this.verdantThreshold = verdantThreshold;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        if (!LatitudeWorldgenScope.isActive() || !LatitudeRiparianBanks.isEnabled()) {
            return false;
        }
        WorldGenLevel level = context.getLevel();
        if (level == null) {
            return false;
        }
        // The decoration loop unions the feature lists of every biome in the region, so a bank
        // feature invited by one arid chunk can be offered positions in its non-arid neighbour.
        // The biome under the position is the authority, not the biome that invited the feature.
        if (!LatitudeRiparianBanks.isAridLand(level.getBiome(pos))) {
            return false;
        }
        // Cheapest gate first: one noise sample rejects roughly half of all candidates before any
        // block is read.
        if (!isVerdantStretch(level.getSeed(), pos)) {
            return false;
        }
        // A position standing in the water itself is not a bank.
        BlockState below = context.getBlockState(pos.below());
        if (!below.getFluidState().isEmpty()) {
            return false;
        }
        return hasWaterNearby(context, pos);
    }

    /**
     * Low-frequency coherence: whole stretches of shoreline share a verdict, so the desert keeps
     * long green reaches and long bare ones instead of a dusting of isolated tufts.
     */
    private boolean isVerdantStretch(long worldSeed, BlockPos pos) {
        double sample = ValueNoise2D.sampleBlocks(worldSeed ^ NOISE_SALT, pos.getX(), pos.getZ(), this.noiseScale);
        return sample < this.verdantThreshold;
    }

    private boolean hasWaterNearby(PlacementContext context, BlockPos pos) {
        int inner = Math.max(1, this.searchRadius / 2);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] direction : COMPASS) {
            if (isWaterlineAt(context, cursor, pos, direction[0] * inner, direction[1] * inner)) {
                return true;
            }
            if (isWaterlineAt(context, cursor, pos, direction[0] * this.searchRadius, direction[1] * this.searchRadius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One probe: read the surface height of a neighbouring column, reject it if it is not within a
     * bank's rise of this position, then test that single block for water. Water is identified by
     * blockstate presence only - Latitude's oceans are the same block as its rivers and lakes, and
     * the maintainer ruled the distinction out of scope for the planting pass (2026-08-19).
     */
    private boolean isWaterlineAt(PlacementContext context, BlockPos.MutableBlockPos cursor, BlockPos origin, int dx, int dz) {
        int x = origin.getX() + dx;
        int z = origin.getZ() + dz;
        // The world-surface heightmap reports the first open block above the column, so one below
        // it is the surface itself - the top water block over any river, lake or sea.
        int surfaceY = context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int drop = origin.getY() - 1 - surfaceY;
        if (drop < 0 || drop > this.bankHeight) {
            return false;
        }
        cursor.set(x, surfaceY, z);
        return context.getBlockState(cursor).is(Blocks.WATER);
    }

    @Override
    public PlacementModifierType<?> type() {
        return LatitudeRiparianBanks.RIPARIAN;
    }
}
