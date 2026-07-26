package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * S50 MAGMA QUENCH SWEEP ({@code globe:magma_quench_sweep}) -- Peetsa 2026-07-26, TEST 138 flight:
 * "magma not creating obsidian surrounding it." Her screenshot showed the vanilla {@code
 * minecraft:underwater_magma} FEATURE's patches on a flooded gallery floor -- and features run AFTER
 * {@code buildSurface}, so the S48 quench in {@code PolarBarrensGlacierMixin}'s surface-stage post-pass
 * ran before that magma existed and could never see it. THIS feature is the fix: it runs at the very END
 * of UNDERGROUND_DECORATION (step 7) in both glacial biomes -- after underwater_magma, after every other
 * magma source -- and sweeps the whole chunk column once:
 *
 * <ul>
 *   <li><b>FLOODED magma</b> (any face-adjacent water): the full 3x3x3 neighborhood's ice-family AND
 *       water cells quench to OBSIDIAN -- a sealed shell, her exact "surrounded by obsidian" ask; no
 *       gen-time air bubble is ever written under water.</li>
 *   <li><b>DRY ice-touching magma</b>: the S43b read, verbatim -- face-adjacent ice melts to AIR (the
 *       pocket), the remaining 3x3x3 ice becomes the obsidian rim.</li>
 *   <li>Magma touching neither water nor ice (bare stone country) is left alone.</li>
 * </ul>
 *
 * <p>The S48 mixin post-pass stays: it still handles surface-stage magma early, and this sweep is
 * idempotent over its output (obsidian and air match neither ice nor water, so already-quenched pockets
 * are skipped). S51: the sweep reads AND writes across chunk borders -- a decoration feature owns its
 * full region, and a border magma must neither classify dry because its water sits one chunk over nor
 * keep a half-open shell (scans stay anchored to this chunk's 16x16 columns, so no double-processing:
 * each magma is swept exactly once, by its own chunk). Scan band Y0..{@link #SCAN_TOP_Y}: the ice
 * country -- sub-Y0 stone-cellar magma is the deepslate world's own business (the S49 cellar ruling).
 *
 * <p>Census line under {@code -Dlatitude.debugCollapse}: {@code [LAT][QUENCH] chunk=... magma=N
 * flooded=F dry=D} for rig calibration/audit.
 */
public final class MagmaQuenchSweepFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final int SCAN_TOP_Y = 100;
    private static final int SCAN_BOTTOM_Y = 0;

    private static final boolean DEBUG = Boolean.getBoolean("latitude.debugCollapse");

    public MagmaQuenchSweepFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Registered unconditionally at mod init (registry-consistency law), listed only in the glacial biomes. */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "magma_quench_sweep"),
                new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC));
    }

    private static boolean isIceFamily(Block b) {
        return b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b == Blocks.ICE || b == Blocks.SNOW_BLOCK;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }
        WorldGenLevel level = ctx.level();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanBottom = Math.max(level.getMinY() + 1, SCAN_BOTTOM_Y);

        int magma = 0;
        int flooded = 0;
        int dry = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                for (int y = scanBottom; y <= SCAN_TOP_Y; y++) {
                    cursor.set(wx, y, wz);
                    if (level.getBlockState(cursor).getBlock() != Blocks.MAGMA_BLOCK) {
                        continue;
                    }
                    magma++;
                    // Classify: flooded beats dry; neither = leave alone (bare stone country).
                    // S51: cross-chunk faces are CHECKED and shells WRITTEN whole -- the chunk-local
                    // discipline was inherited from the surface-stage mixin, but a decoration feature
                    // legally owns its full region; a border magma must not classify dry because its
                    // only water sits one chunk over (the TEST 139 flight's suspected miss class).
                    boolean isFlooded = false;
                    boolean touchesIce = false;
                    for (int f = 0; f < 6; f++) {
                        int fx = wx + (f == 0 ? 1 : f == 1 ? -1 : 0);
                        int fy = y + (f == 2 ? 1 : f == 3 ? -1 : 0);
                        int fz = wz + (f == 4 ? 1 : f == 5 ? -1 : 0);
                        if (fy <= level.getMinY() || fy >= level.getMaxY()) {
                            continue;
                        }
                        cursor.set(fx, fy, fz);
                        BlockState fs = level.getBlockState(cursor);
                        if (fs.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                            isFlooded = true;
                        }
                        if (isIceFamily(fs.getBlock())) {
                            touchesIce = true;
                        }
                    }
                    if (!isFlooded && !touchesIce) {
                        continue;
                    }
                    if (isFlooded) {
                        flooded++;
                    } else {
                        dry++;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dy == 0 && dz == 0) {
                                    continue;
                                }
                                int nx = wx + dx;
                                int ny = y + dy;
                                int nz = wz + dz;
                                if (ny <= level.getMinY() || ny >= level.getMaxY()) {
                                    continue;
                                }
                                cursor.set(nx, ny, nz);
                                BlockState ns = level.getBlockState(cursor);
                                boolean ice = isIceFamily(ns.getBlock());
                                boolean water = ns.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                                if (isFlooded) {
                                    if (ice || water) {
                                        level.setBlock(cursor, OBSIDIAN, 2);
                                    }
                                } else if (ice) {
                                    boolean faceAdjacent = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
                                    level.setBlock(cursor, faceAdjacent ? AIR : OBSIDIAN, 2);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (DEBUG && magma > 0) {
            GlobeMod.LOGGER.info("[LAT][QUENCH] chunk=({},{}) magma={} flooded={} dry={}",
                    baseX >> 4, baseZ >> 4, magma, flooded, dry);
        }
        return flooded + dry > 0;
    }
}
