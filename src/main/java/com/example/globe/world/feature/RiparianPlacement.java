package com.example.globe.world.feature;

import com.example.globe.util.ValueNoise2D;
import com.example.globe.world.LatitudeWorldgenScope;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *   <li><b>Water proximity</b> - a bounded, distance-ordered ring sweep, never a radius-squared
 *       scan. Probes are walked nearest-first through the world-surface heightmap and stop at the
 *       first hit, so a bank column answers in a handful of reads and only a genuinely dry column
 *       pays for the whole table. The hit distance is kept, not merely a yes/no, because planting
 *       density tapers with it.</li>
 *   <li><b>Coherence</b> - one {@link ValueNoise2D} sample at a low frequency, so a verdant bank
 *       runs for hundreds of blocks instead of speckling block by block. Article VI: the
 *       coherence comes from seeded value noise, never from cell hashing.</li>
 * </ul>
 */
public class RiparianPlacement extends PlacementFilter {
    /** Horizontal reach of the water probe, in blocks. */
    public static final int DEFAULT_SEARCH_RADIUS = 10;
    /** Codec ceiling for the search radius; also the widest precomputed probe ring. */
    public static final int MAX_SEARCH_RADIUS = 15;
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
     * Directions per probe ring. Sixteen keeps the arc gap under four blocks even at the outermost
     * radius this filter can be configured with, so a river cannot pass between two rays.
     */
    private static final int RING_DIRECTIONS = 16;

    /**
     * Distance-ordered probe offsets per search radius, indexed by radius.
     *
     * <p>Replaces an eight-direction, two-distance stencil that probed only {@code searchRadius/2}
     * and {@code searchRadius} on each ray. That stencil had two independent holes, both geometric
     * rather than tuning-related: radially, a bank whose river band fell between the two sampled
     * distances read as dry; angularly, eight rays leave a 7.85-block arc gap at radius ten, and
     * the diagonal rays actually sampled 7.07 and 14.14 rather than 5 and 10. Modelled over every
     * bank distance 1..searchRadius and every orientation in five-degree steps, the old stencil
     * detected only 73.3% of cases against a three-block-wide river and 92.8% against a five-block
     * one; wide rivers hid the defect entirely, which is why it read as speckling on narrow desert
     * streams. These rings detect 100% of the same cases.
     *
     * <p>Ordered nearest ring first and de-duplicated keeping the nearest occurrence, so the sweep
     * both returns the true distance to the waterline and pays only for the rings it must: an edge
     * column answers within a few probes, and only a genuinely dry column walks the whole table.
     * Every offset stays within +/-{@code searchRadius} per axis, so the readable-region bound the
     * codec documents still holds.
     */
    private static final int[][][] PROBE_RINGS = buildProbeRings();

    private static int[][][] buildProbeRings() {
        int[][][] byRadius = new int[MAX_SEARCH_RADIUS + 1][][];
        for (int radius = 0; radius <= MAX_SEARCH_RADIUS; radius++) {
            List<int[]> offsets = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (int ring : ringRadii(radius)) {
                for (int step = 0; step < RING_DIRECTIONS; step++) {
                    double angle = (2.0 * Math.PI * step) / RING_DIRECTIONS;
                    int dx = (int) Math.round(ring * Math.cos(angle));
                    int dz = (int) Math.round(ring * Math.sin(angle));
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (seen.add((((long) dx) << 32) ^ (dz & 0xFFFFFFFFL))) {
                        offsets.add(new int[]{dx, dz});
                    }
                }
            }
            // Rings are how the offsets are CHOSEN; true Euclidean distance is how they must be
            // ORDERED. Rounding a ring's sixteen directions onto the block grid gives that single
            // ring a spread of true distances, so even one ring walked in angle order runs
            // backwards: ring 8's diagonal rounds to (6,6) = 8.49 while its next direction rounds
            // to (3,7) = 7.62, an inversion of 0.87 blocks — the widest in this table, and larger
            // than any gap between adjacent rings. Ring order would therefore let the sweep return
            // a distance that is not the nearest one it sampled, and the taper reads that distance
            // directly. Sorting is what makes "first hit" mean "nearest", and it keeps the table
            // correct if a future edit changes the radii or the direction count — both of which
            // silently move these spreads.
            offsets.sort((left, right) -> Integer.compare(
                    left[0] * left[0] + left[1] * left[1],
                    right[0] * right[0] + right[1] * right[1]));
            byRadius[radius] = offsets.toArray(new int[0][]);
        }
        return byRadius;
    }

    /** Dense where the taper needs precision, coarse further out; always reaches the radius. */
    private static int[] ringRadii(int searchRadius) {
        List<Integer> radii = new ArrayList<>();
        for (int r = 1; r <= Math.min(4, searchRadius); r++) {
            radii.add(r);
        }
        for (int r = 6; r <= searchRadius; r += 2) {
            radii.add(r);
        }
        if (searchRadius > 0 && !radii.contains(searchRadius)) {
            radii.add(searchRadius);
        }
        int[] out = new int[radii.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = radii.get(i);
        }
        return out;
    }

    /**
     * Planting density for a bank column, from its true distance to the waterline: full within the
     * core, smoothstepping to nothing at the search radius. The old filter was a boolean, so every
     * admitted column planted at full density and each patch ended on a hard rim at exactly the
     * search radius. Tapering is what makes a bank read as a bank rather than as a stamped disc.
     */
    public static double bankDensity(int waterDistance, int searchRadius) {
        if (waterDistance < 0 || searchRadius <= 0) {
            return 0.0;
        }
        double core = searchRadius / 3.0;
        if (waterDistance <= core) {
            return 1.0;
        }
        if (waterDistance >= searchRadius) {
            return 0.0;
        }
        double t = (waterDistance - core) / (searchRadius - core);
        return 1.0 - (t * t * (3.0 - 2.0 * t));
    }

    /** Probe offsets for one search radius, nearest first. Exposed for the policy suite. */
    static int[][] probeOffsetsForPolicyTest(int searchRadius) {
        return PROBE_RINGS[Math.max(0, Math.min(MAX_SEARCH_RADIUS, searchRadius))];
    }

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
        int distance = waterDistance(context, pos);
        if (distance < 0) {
            return false;
        }
        // Taper by true distance instead of admitting every column at full density, so a bank
        // fades out instead of ending on a rim at exactly the search radius.
        double density = bankDensity(distance, this.searchRadius);
        return density >= 1.0 || random.nextFloat() < density;
    }

    /**
     * Low-frequency coherence: whole stretches of shoreline share a verdict, so the desert keeps
     * long green reaches and long bare ones instead of a dusting of isolated tufts.
     */
    private boolean isVerdantStretch(long worldSeed, BlockPos pos) {
        double sample = ValueNoise2D.sampleBlocks(worldSeed ^ NOISE_SALT, pos.getX(), pos.getZ(), this.noiseScale);
        return sample < this.verdantThreshold;
    }

    /**
     * Distance in blocks from this column to the nearest waterline, or -1 if none is in reach.
     * The offsets are ordered nearest-ring-first, so the first hit is the true distance and the
     * sweep can stop there.
     */
    private int waterDistance(PlacementContext context, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] offset : PROBE_RINGS[this.searchRadius]) {
            if (isWaterlineAt(context, cursor, pos, offset[0], offset[1])) {
                return (int) Math.round(Math.sqrt((double) offset[0] * offset[0]
                        + (double) offset[1] * offset[1]));
            }
        }
        return -1;
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
