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
 *   <li><b>Water proximity</b> - a bounded sweep of columns ordered by true distance, never a radius-squared
 *       sweep. Rings are walked outward and the search stops at the first hit, so the answer is
 *       the true distance to the waterline and a bank column pays only the few probes it takes to
 *       find its own water. The predecessor probed sixteen fixed columns (eight compass directions
 *       at two distances); that stencil left holes wide enough for a genuine bank column seven
 *       blocks from a river to read as dry, which is what made verdant reaches print as dotted
 *       blobs rather than continuous banks (live find, 2026-08-25).</li>
 *   <li><b>Distance taper</b> - inside the core distance every qualifying column greens; beyond it
 *       the survival chance falls to zero at the search radius, so a reach fades out as it climbs
 *       away from the water instead of ending at a hard rim.</li>
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
     * Radii probed, in order, when looking for the waterline. Dense where the taper needs
     * precision (a bank's first few blocks) and coarser further out, where the only question left
     * is whether any water is in reach at all.
     */
    private static final int[] PROBE_RADII = {1, 2, 3, 4, 6, 8, 10, 12, 14, 15};

    /**
     * Every probe column, de-duplicated across rings and sorted by true squared distance, as
     * {@code {dx, dz, dx*dx + dz*dz}}. Sixteen directions per ring keep the arc gap under four
     * blocks even on the outermost ring, so a river cannot slip between two rays -- the failure
     * the eight-direction stencil had.
     *
     * <p>Sorting by true distance rather than walking ring by ring is what makes the sweep's
     * answer correct rather than merely close. Rounding a ring onto the block grid does not
     * preserve ring order: whether a later ring can hold a column nearer than an earlier ring's
     * farthest depends on the arithmetic of (direction count x radius set), and this table only
     * happens to be free of such inversions at sixteen directions -- twenty-four inverts by 0.35
     * blocks on the same radii. A future edit to either constant would silently reintroduce the
     * error, and the taper consumes this distance directly, so the column would plant at the
     * wrong density. Sorting removes the failure mode instead of relying on it not to occur.
     * The pass also drops the four corner columns that two rings would otherwise both probe.</p>
     */
    private static final int[][] PROBE_OFFSETS = buildProbeOffsets();

    private static int[][] buildProbeOffsets() {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int radius : PROBE_RADII) {
            for (int step = 0; step < 16; step++) {
                double angle = (Math.PI * 2.0 * step) / 16.0;
                int dx = (int) Math.round(Math.cos(angle) * radius);
                int dz = (int) Math.round(Math.sin(angle) * radius);
                if (dx == 0 && dz == 0) continue;
                if (seen.add((((long) dx) << 32) ^ (dz & 0xFFFF_FFFFL))) {
                    offsets.add(new int[]{dx, dz, dx * dx + dz * dz});
                }
            }
        }
        offsets.sort(java.util.Comparator.comparingInt(offset -> offset[2]));
        return offsets.toArray(new int[0][]);
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
        double distance = waterDistance(context, pos);
        if (distance < 0.0D) {
            return false;
        }
        return random.nextFloat() < bankDensityAt(distance);
    }

    /**
     * Survival chance for a qualifying column at this distance from the waterline: certain within
     * the core, fading smoothly to nothing at the search radius. The fade is what feathers a
     * reach's landward edge; without it every green patch ended on a hard rim.
     */
    private float bankDensityAt(double distance) {
        double core = Math.max(1.0D, this.searchRadius / 3.0D);
        if (distance <= core) {
            return 1.0F;
        }
        float span = (float) (this.searchRadius - core);
        if (!(span > 0.0F)) {
            return 1.0F;
        }
        float t = (float) ((distance - core) / span);
        t = Math.min(1.0F, Math.max(0.0F, t));
        // Smoothstep, so the thinning reads as a gradient rather than a linear ramp with a visible
        // start and end.
        return 1.0F - (t * t * (3.0F - 2.0F * t));
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
     * Distance in blocks from this column to the nearest waterline within the search radius, or
     * {@code -1} when there is none. Rings are walked outward and the sweep returns on its first
     * hit, so a column on the water's edge answers in a handful of probes and only a genuinely dry
     * column pays for the whole sweep.
     */
    private double waterDistance(PlacementContext context, BlockPos pos) {
        int limitSquared = this.searchRadius * this.searchRadius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] offset : PROBE_OFFSETS) {
            if (offset[2] > limitSquared) {
                break;
            }
            if (isWaterlineAt(context, cursor, pos, offset[0], offset[1])) {
                return Math.sqrt(offset[2]);
            }
        }
        return -1.0D;
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
