package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S50 MAGMA QUENCH SWEEP ({@code globe:magma_quench_sweep}) -- Peetsa 2026-07-26, TEST 138 flight:
 * "magma not creating obsidian surrounding it." Her screenshot showed the vanilla {@code
 * minecraft:underwater_magma} FEATURE's patches on a flooded gallery floor -- and features run AFTER
 * {@code buildSurface}, so the S48 quench in {@code PolarBarrensGlacierMixin}'s surface-stage post-pass
 * ran before that magma existed and could never see it. THIS feature is the fix: it runs at the very END
 * of TOP_LAYER_MODIFICATION (step 10) in both glacial biomes -- after underwater_magma, fluid springs,
 * freeze_top_layer, and every other water/ice writer -- and sweeps the whole chunk column once:
 *
 * <ul>
 *   <li><b>FLOODED magma</b> (water anywhere in the 3x3x3 shell): the shell's ice-family AND
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
 * keep a half-open shell. S52: vanilla 26.2 {@code underwater_magma} has
 * {@code placement_radius_around_floor=1}, so the final sweep scans the invoking chunk plus exactly one
 * X/Z block of halo. That catches a neighbor feature's one-block spill into an already-swept owner chunk;
 * repeat sightings are harmless because the shell transform is idempotent. Scan band
 * {@link #SCAN_BOTTOM_Y}..{@link #SCAN_TOP_Y}: the ice country, including the short sub-Y0 diffusion
 * tail. Magma below that tail remains the deepslate world's own business (the S49 cellar ruling).
 *
 * <p>Census line under {@code -Dlatitude.debugGlacialDressing=true} (or the backward-compatible
 * {@code -Dlatitude.debugCollapse=true}): {@code [LAT][QUENCH] chunk=...
 * invoked=true candidateMagma=N flooded=F dry=D attemptedWrites=A successfulWrites=S
 * failedWrites=E residualShellCells=R} for rig calibration/audit.
 */
public final class MagmaQuenchSweepFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Identifier GLACIAL_CAVES = Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "glacial_caves");
    private static final Identifier POLAR_BARRENS = Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "polar_barrens");

    private static final int SCAN_TOP_Y = 100;
    static final int SCAN_BOTTOM_Y = PolarBarrensBand.ICE_BODY_FLOOR_Y
            - PolarBarrensBand.PERMAFROST_BAND_BLOCKS;
    static final int SCAN_HORIZONTAL_HALO = 1;

    private static final boolean DEBUG = debugEnabled(
            Boolean.getBoolean("latitude.debugGlacialDressing"),
            Boolean.getBoolean("latitude.debugCollapse"));

    static boolean debugEnabled(boolean dedicated, boolean legacyCollapse) {
        return dedicated || legacyCollapse;
    }

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

    private static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER) || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    private static boolean isGlacialHost(WorldGenLevel level, BlockPos pos) {
        // LevelReader#getBiome uses a fuzzy eight-quart sampler. At an exact glacial boundary that
        // sampler can select a neighboring non-glacial quart, leaving a real glacial magma pocket
        // unswept. The stored noise-biome quart owns this exact block and stays inside the feature's
        // legal owner-plus-halo region, matching the lake-fringe lookup contract.
        return level.getNoiseBiome(
                        QuartPos.fromBlock(pos.getX()),
                        QuartPos.fromBlock(pos.getY()),
                        QuartPos.fromBlock(pos.getZ()))
                .unwrapKey().map(ResourceKey::identifier)
                .map(id -> id.equals(GLACIAL_CAVES) || id.equals(POLAR_BARRENS))
                .orElse(false);
    }

    record SweepStats(
            int candidateMagma,
            int flooded,
            int dry,
            int attemptedWrites,
            int successfulWrites,
            int failedWrites,
            int residualShellCells) {

        boolean completedWithoutResidual() {
            return successfulWrites > 0 && failedWrites == 0 && residualShellCells == 0;
        }
    }

    private record ShellClassification(boolean flooded, boolean touchesFaceIce) {
        boolean eligible() {
            return flooded || touchesFaceIce;
        }
    }

    private record WriteCounts(int attempted, int successful, int failed) {
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }
        WorldGenLevel level = ctx.level();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        SweepStats stats = sweep(level, baseX, baseZ);
        if (DEBUG) {
            GlobeMod.LOGGER.info(debugLine(baseX >> 4, baseZ >> 4, stats));
        }
        return stats.completedWithoutResidual();
    }

    static SweepStats sweep(WorldGenLevel level, int baseX, int baseZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanBottom = Math.max(level.getMinY() + 1, SCAN_BOTTOM_Y);

        List<BlockPos> candidateMagmaPositions = new ArrayList<>();
        int flooded = 0;
        int dry = 0;
        int attemptedWrites = 0;
        int successfulWrites = 0;
        int failedWrites = 0;
        for (int lx = -SCAN_HORIZONTAL_HALO; lx < 16 + SCAN_HORIZONTAL_HALO; lx++) {
            for (int lz = -SCAN_HORIZONTAL_HALO; lz < 16 + SCAN_HORIZONTAL_HALO; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                for (int y = scanBottom; y <= SCAN_TOP_Y; y++) {
                    cursor.set(wx, y, wz);
                    if (level.getBlockState(cursor).getBlock() != Blocks.MAGMA_BLOCK) {
                        continue;
                    }
                    if (!isGlacialHost(level, cursor)) {
                        continue;
                    }
                    BlockPos magma = cursor.immutable();
                    candidateMagmaPositions.add(magma);
                    // Classify: any shell water makes a visibly flooded pocket; flooded beats dry.
                    // Ice still needs a FACE contact for the dry pocket path. This prevents a prior lake-ice
                    // write from hiding diagonal/edge water and misrouting the pocket to face-air carving.
                    ShellClassification classification = classifyShell(level, magma, cursor);
                    if (!classification.eligible()) {
                        continue;
                    }
                    if (classification.flooded()) {
                        flooded++;
                    } else {
                        dry++;
                    }
                    WriteCounts writes = writeEligibleShell(level, magma, classification, cursor);
                    attemptedWrites += writes.attempted();
                    successfulWrites += writes.successful();
                    failedWrites += writes.failed();
                }
            }
        }
        int residualShellCells = countResidualEligibleShellCells(level, candidateMagmaPositions);
        return new SweepStats(
                candidateMagmaPositions.size(), flooded, dry,
                attemptedWrites, successfulWrites, failedWrites, residualShellCells);
    }

    private static ShellClassification classifyShell(
            WorldGenLevel level, BlockPos magma, BlockPos.MutableBlockPos cursor) {
        boolean flooded = false;
        boolean touchesFaceIce = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int ny = magma.getY() + dy;
                    if (ny < SCAN_BOTTOM_Y || ny <= level.getMinY() || ny >= level.getMaxY()) {
                        continue;
                    }
                    cursor.set(magma.getX() + dx, ny, magma.getZ() + dz);
                    BlockState shellState = level.getBlockState(cursor);
                    if (isWater(shellState)) {
                        flooded = true;
                    }
                    if (faceAdjacent(dx, dy, dz) && isIceFamily(shellState.getBlock())) {
                        touchesFaceIce = true;
                    }
                }
            }
        }
        return new ShellClassification(flooded, touchesFaceIce);
    }

    private static WriteCounts writeEligibleShell(
            WorldGenLevel level,
            BlockPos magma,
            ShellClassification classification,
            BlockPos.MutableBlockPos cursor) {
        int attempted = 0;
        int successful = 0;
        int failed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int ny = magma.getY() + dy;
                    if (ny < SCAN_BOTTOM_Y || ny <= level.getMinY() || ny >= level.getMaxY()) {
                        continue;
                    }
                    cursor.set(magma.getX() + dx, ny, magma.getZ() + dz);
                    BlockState target = targetState(
                            classification.flooded(), dx, dy, dz, level.getBlockState(cursor));
                    if (target == null) {
                        continue;
                    }
                    attempted++;
                    boolean accepted = level.setBlock(cursor, target, 2);
                    boolean observed = level.getBlockState(cursor).equals(target);
                    if (accepted && observed) {
                        successful++;
                    } else {
                        failed++;
                    }
                }
            }
        }
        return new WriteCounts(attempted, successful, failed);
    }

    private static int countResidualEligibleShellCells(
            WorldGenLevel level, List<BlockPos> candidateMagmaPositions) {
        Set<BlockPos> residual = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos magma : candidateMagmaPositions) {
            if (!level.getBlockState(magma).is(Blocks.MAGMA_BLOCK)) {
                continue;
            }
            ShellClassification classification = classifyShell(level, magma, cursor);
            if (!classification.eligible()) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int ny = magma.getY() + dy;
                        if (ny < SCAN_BOTTOM_Y || ny <= level.getMinY() || ny >= level.getMaxY()) {
                            continue;
                        }
                        cursor.set(magma.getX() + dx, ny, magma.getZ() + dz);
                        if (targetState(
                                classification.flooded(), dx, dy, dz,
                                level.getBlockState(cursor)) != null) {
                            residual.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        return residual.size();
    }

    private static BlockState targetState(
            boolean flooded, int dx, int dy, int dz, BlockState current) {
        boolean ice = isIceFamily(current.getBlock());
        if (flooded) {
            return ice || isWater(current) ? OBSIDIAN : null;
        }
        if (!ice) {
            return null;
        }
        return faceAdjacent(dx, dy, dz) ? AIR : OBSIDIAN;
    }

    private static boolean faceAdjacent(int dx, int dy, int dz) {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
    }

    static String debugLine(int chunkX, int chunkZ, SweepStats stats) {
        return "[LAT][QUENCH] chunk=(" + chunkX + "," + chunkZ + ")"
                + " invoked=true"
                + " candidateMagma=" + stats.candidateMagma()
                + " flooded=" + stats.flooded()
                + " dry=" + stats.dry()
                + " attemptedWrites=" + stats.attemptedWrites()
                + " successfulWrites=" + stats.successfulWrites()
                + " failedWrites=" + stats.failedWrites()
                + " residualShellCells=" + stats.residualShellCells();
    }
}
