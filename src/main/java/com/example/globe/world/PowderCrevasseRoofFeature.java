package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PowderRoofTrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Phase 5 S25b POWDER-ROOF CREVASSE TRAP ({@code globe:powder_crevasse_roof}) -- the mod's first custom
 * worldgen {@link Feature}. Owner-hunted (Peetsa 2026-07-20: "I was looking for the snow-covered traps
 * covering crevasses and couldn't find any"; banked in the B-9 design doc S-BANK item 1). Runs at the
 * TOP_LAYER_MODIFICATION decoration stage of {@code globe:polar_barrens}, i.e. AFTER carving (crevasses are
 * already cut), AFTER the glacier surface pass, and AFTER {@code minecraft:freeze_top_layer} (this feature is
 * appended LAST in that step's list) so the snowfield reference is final and nothing overwrites the roof.
 *
 * <p><b>Per chunk (bounded, no recursion):</b>
 * <ol>
 *   <li>Read WORLD_SURFACE_WG for the 16x16 columns of the decorating chunk (heightmap reads: fluids count,
 *       so a water-ponded slot reads its water top and is naturally excluded from the deep-shaft test).</li>
 *   <li>Per column, derive the snowfield REFERENCE = the maximum surface over a {@link
 *       PowderRoofTrap#REFERENCE_WINDOW_RADIUS}-radius window (a cut slot column's low floor never raises the
 *       max; the surrounding snowfield does), then mark it a candidate via {@link
 *       PowderRoofTrap#isTrapCandidate(int, int)} (window max at least {@link
 *       PowderRoofTrap#MIN_SHAFT_DEPTH_BLOCKS} above the column's own surface).</li>
 *   <li>Detect roofable spans ({@link PowderRoofTrap#roofableSpans(boolean[])}, width &le; {@link
 *       PowderRoofTrap#MAX_ROOF_SPAN_WIDTH}) along BOTH chunk axes and, for each span that wins the {@link
 *       PowderRoofTrap#shouldRoofSpan(float)} fraction roll (the feature's own vanilla-seeded RandomSource --
 *       deterministic per seed+chunk, Art VI: no new noise), union its columns into the roofed mask.</li>
 *   <li>For each roofed column: bridge the slot opening flush with the snowfield by placing one {@code
 *       powder_snow} at the reference surface Y (only into air), and give the crevasse floor a {@code
 *       snow_block} cushion when that floor is bare stone/ice (the fall costs warmth/position, rarely the
 *       run -- the banked design).</li>
 * </ol>
 *
 * <p><b>Vanilla-first / flag honesty.</b> The feature is REGISTERED unconditionally at mod-init (registries
 * must be consistent across sessions; see {@link #register()}), but its BEHAVIOUR is gated on {@link
 * LatitudeV2Flags#POLAR_BARRENS_ENABLED} -- the same flag that makes {@code globe:polar_barrens} generate at
 * all. Flag-off the barrens biome never generates, so this feature (listed only in that biome) never runs;
 * the early return is belt-and-suspenders. No custom carver/noise: it reads existing heightmaps and reuses
 * the vanilla per-placement RandomSource.
 */
public final class PowderCrevasseRoofFeature extends Feature<NoneFeatureConfiguration> {

    /** The registered singleton (populated by {@link #register()}), mirroring {@code PolarOutfitting}. */
    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    /** S25b post-crew fix (Crew 4's own risk flag): the cushion is POWDER SNOW, not snow_block —
     *  snow_block does NOT reduce vanilla fall damage, so a 20-40 deep roofed drop could kill outright,
     *  violating the trap law ("costs warmth/position, rarely the run"). Powder snow fully negates fall
     *  damage, the victim sinks into it, and then the COLD is the price — exactly the design. Leather
     *  boots let them climb back out. */
    private static final BlockState CUSHION = Blocks.POWDER_SNOW.defaultBlockState();

    public PowderCrevasseRoofFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * Register {@code globe:powder_crevasse_roof} into {@link BuiltInRegistries#FEATURE}. Called
     * UNCONDITIONALLY from {@code GlobeMod.onInitialize} during the mod-init window, before registry freeze
     * (the FEATURE registry is a bootstrap registry frozen early -- a datapack {@code configured_feature}
     * referencing an unregistered type would hard-fail at load). Idempotent per JVM by registry contract.
     */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "powder_crevasse_roof"),
                new PowderCrevasseRoofFeature(NoneFeatureConfiguration.CODEC));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            return false; // barrens biome never generates flag-off; defensive parity with the glacier mixin.
        }
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = ctx.origin();
        // Snap to the decorating chunk's min corner so the 16x16 scan never straddles two chunks (in_square
        // offsets the single origin randomly within the chunk).
        int baseX = (origin.getX() >> 4) << 4;
        int baseZ = (origin.getZ() >> 4) << 4;

        // WORLD_SURFACE_WG per column (this is LevelReader.getHeight = firstAir = topSolid + 1). We keep the
        // "first air" convention throughout: reference/own surfaces subtract identically so the depth is
        // unaffected, and the powder roof lands at referenceFirstAir - 1 = the snowfield's own top-block Y.
        int[][] surfaceFirstAir = new int[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                surfaceFirstAir[lx][lz] =
                        level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, baseX + lx, baseZ + lz);
            }
        }

        // Per-column snowfield reference (windowed local max) + candidate mask.
        int[][] reference = new int[16][16];
        boolean[][] candidate = new boolean[16][16];
        int r = PowderRoofTrap.REFERENCE_WINDOW_RADIUS;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int max = surfaceFirstAir[lx][lz];
                for (int wx = Math.max(0, lx - r); wx <= Math.min(15, lx + r); wx++) {
                    for (int wz = Math.max(0, lz - r); wz <= Math.min(15, lz + r); wz++) {
                        if (surfaceFirstAir[wx][wz] > max) {
                            max = surfaceFirstAir[wx][wz];
                        }
                    }
                }
                reference[lx][lz] = max;
                candidate[lx][lz] = PowderRoofTrap.isTrapCandidate(surfaceFirstAir[lx][lz], max);
            }
        }

        // Span pass along BOTH axes; union the winning spans into the roofed mask (a slot of either
        // orientation is caught; double-marking a column is idempotent at placement time).
        boolean[][] roofed = new boolean[16][16];
        boolean[] line = new boolean[16];
        // X-rows (fixed lz, run over lx).
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                line[lx] = candidate[lx][lz];
            }
            for (int[] span : PowderRoofTrap.roofableSpans(line)) {
                if (PowderRoofTrap.shouldRoofSpan(random.nextFloat())) {
                    for (int lx = span[0]; lx < span[0] + span[1]; lx++) {
                        roofed[lx][lz] = true;
                    }
                }
            }
        }
        // Z-columns (fixed lx, run over lz).
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                line[lz] = candidate[lx][lz];
            }
            for (int[] span : PowderRoofTrap.roofableSpans(line)) {
                if (PowderRoofTrap.shouldRoofSpan(random.nextFloat())) {
                    for (int lz = span[0]; lz < span[0] + span[1]; lz++) {
                        roofed[lx][lz] = true;
                    }
                }
            }
        }

        boolean placedAny = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (!roofed[lx][lz]) {
                    continue;
                }
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int roofY = reference[lx][lz] - 1; // the snowfield's own top-block Y -> flush bridge.
                int floorY = surfaceFirstAir[lx][lz] - 1; // this column's crevasse-floor top-solid block.

                // Cushion first (below), so a later heightmap-touched read of the roof is not needed. Only if
                // the floor is bare stone/ice: place a snow_block on the floor's first air block.
                cursor.set(worldX, floorY, worldZ);
                if (isBareLandingFloor(level.getBlockState(cursor).getBlock())) {
                    cursor.set(worldX, floorY + 1, worldZ);
                    safeSetBlock(level, cursor, CUSHION, BlockState::isAir);
                }

                // Bridge the opening flush with the snowfield -- only into air (never overwrite a wall/ledge).
                cursor.set(worldX, roofY, worldZ);
                if (level.getBlockState(cursor).isAir()) {
                    setBlock(level, cursor, POWDER_SNOW);
                    placedAny = true;
                }
            }
        }
        return placedAny;
    }

    /**
     * Is {@code block} a bare stone/ice-family crevasse floor that warrants a {@code snow_block} cushion? The
     * carver cuts through the glacier body (packed_ice / blue_ice), the permafrost stratum, and native stone
     * below the sole; a dry slot bottoms on one of those. Dirt/gravel/snow floors already read soft, so they
     * are left as-is (no cushion needed, and the design scopes the cushion to "bare stone/ice").
     */
    private static boolean isBareLandingFloor(Block block) {
        return block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.GRANITE
                || block == Blocks.DIORITE || block == Blocks.ANDESITE || block == Blocks.TUFF
                || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE || block == Blocks.ICE;
    }
}
