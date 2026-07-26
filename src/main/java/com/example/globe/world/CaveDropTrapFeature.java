package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.CaveDropTrap;
import com.example.globe.core.LatitudeV2Flags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * S44 IN-CAVE DROP TRAP ({@code globe:cave_drop_trap}) -- Peetsa 2026-07-25, B-9 punchlist item 1: "I want
 * to add traps inside caves that drop the player down to a deeper layer of the cave." World-side adapter
 * for the pure {@link CaveDropTrap} law; runs at UNDERGROUND_DECORATION of both glacial biomes, AFTER
 * carvers and the S37 ice body, so gallery shapes and materials are final when it scans.
 *
 * <p><b>Per chunk (bounded, single pass):</b> scan every column's vertical profile in the cave band
 * ({@link #SCAN_TOP_Y} down to {@link #SCAN_BOTTOM_Y}); a DROP CELL is a walkable gallery floor
 * ({@link CaveDropTrap#MIN_GALLERY_AIR}+ air) over a THIN shell (1..{@link CaveDropTrap#MAX_SHELL_THICKNESS}
 * solid, never bedrock) over a REAL drop ({@link CaveDropTrap#MIN_DROP_AIR}+ air) with a solid landing.
 * Cells flood into patches (4-neighbour, shell tops within 1 block -- one coherent floor panel, cap
 * {@link CaveDropTrap#PATCH_MAX_AREA}); one {@link CaveDropTrap#shouldTrapPatch} roll per patch. A winning
 * patch: shell TOP becomes {@code powder_snow} (the S35 cover fiction -- sink-through IS the trigger),
 * deeper shell blocks are punched to air so the sink continues, and every cell's landing gets a powder
 * cushion (the S35 fall law). S35 safety laws carried verbatim: WATER landings exclude the cell (the
 * skinned-pond law), and the patch only fires if its landing is horizontally TRAVERSABLE (the entombment
 * law -- standing room two blocks out in some cardinal direction).
 */
public final class CaveDropTrapFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Cave-band scan ceiling: covers the upper cavern country (surface p95 ~120 would be surface, not
     *  cave; galleries under the glacier live below ~100). */
    private static final int SCAN_TOP_Y = 100;
    /** Scan floor: below ~8 the deepslate cellar hosts the lava/aquifer story -- drops that deep are the
     *  deep-drop shafts' business (S35), not a floor panel's. */
    private static final int SCAN_BOTTOM_Y = 8;

    private static final boolean DEBUG = Boolean.getBoolean("latitude.debugCollapse");

    public CaveDropTrapFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Registered unconditionally at mod init (registry-consistency law), listed only in the glacial biomes. */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "cave_drop_trap"),
                new CaveDropTrapFeature(NoneFeatureConfiguration.CODEC));
    }

    /** One drop cell: local coords, the shell's TOP solid Y, its thickness, and the landing's first-air Y. */
    private record DropCell(int lx, int lz, int shellTopY, int shellThickness, int landingAirY) {
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Pass 1: per column, the TOPMOST qualifying drop cell in the scan band (one per column keeps the
        // scan single-pass and the trap story simple: the floor you are walking on is the one that lies).
        DropCell[][] cells = new DropCell[16][16];
        int candidateCount = 0;
        int scanBottom = Math.max(level.getMinY() + 2, SCAN_BOTTOM_Y);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int airAbove = 0;
                int y = SCAN_TOP_Y;
                while (y >= scanBottom) {
                    cursor.set(wx, y, wz);
                    BlockState state = level.getBlockState(cursor);
                    boolean airLike = state.isAir();
                    if (airLike) {
                        airAbove++;
                        y--;
                        continue;
                    }
                    // Solid (or fluid) run begins. Only a THIN solid shell under enough standing air is
                    // interesting; measure it, then the drop below.
                    if (airAbove >= CaveDropTrap.MIN_GALLERY_AIR && state.getFluidState().isEmpty()
                            && !state.is(Blocks.BEDROCK)) {
                        int shellTop = y;
                        int thickness = 0;
                        boolean shellClean = true;
                        while (y >= scanBottom && thickness <= CaveDropTrap.MAX_SHELL_THICKNESS) {
                            cursor.set(wx, y, wz);
                            BlockState s = level.getBlockState(cursor);
                            if (s.isAir()) {
                                break;
                            }
                            if (!s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK)) {
                                shellClean = false; // fluid or bedrock in the shell: honest floor, skip run
                            }
                            thickness++;
                            y--;
                        }
                        if (shellClean && thickness >= 1 && thickness <= CaveDropTrap.MAX_SHELL_THICKNESS) {
                            // Count the drop air below the shell.
                            int dropAir = 0;
                            int dy = y;
                            while (dy >= level.getMinY() + 1 && dropAir <= CaveDropTrap.MIN_DROP_AIR + 24) {
                                cursor.set(wx, dy, wz);
                                if (!level.getBlockState(cursor).isAir()) {
                                    break;
                                }
                                dropAir++;
                                dy--;
                            }
                            if (CaveDropTrap.cellQualifies(airAbove, thickness, dropAir)) {
                                // Landing = first non-air below the drop run; exclude WATER landings
                                // outright (skinned-pond law).
                                cursor.set(wx, dy, wz);
                                BlockState landing = level.getBlockState(cursor);
                                if (landing.getFluidState().isEmpty() && cells[lx][lz] == null) {
                                    cells[lx][lz] = new DropCell(lx, lz, shellTop, thickness, dy + 1);
                                    candidateCount++;
                                }
                            }
                        }
                        airAbove = 0;
                        continue; // y already advanced past the shell
                    }
                    airAbove = 0;
                    y--;
                }
            }
        }

        // Pass 2: flood cells into coherent floor panels (4-neighbour, shell tops within 1), roll the
        // fraction gate, verify the entombment law at the patch's first cell, then write.
        boolean placedAny = false;
        int patches = 0;
        int covers = 0;
        boolean[][] seen = new boolean[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (cells[lx][lz] == null || seen[lx][lz]) {
                    continue;
                }
                // Flood the patch.
                List<DropCell> patch = new ArrayList<>();
                ArrayList<int[]> queue = new ArrayList<>();
                queue.add(new int[]{lx, lz});
                seen[lx][lz] = true;
                while (!queue.isEmpty() && patch.size() < CaveDropTrap.PATCH_MAX_AREA) {
                    int[] c = queue.remove(queue.size() - 1);
                    DropCell cell = cells[c[0]][c[1]];
                    patch.add(cell);
                    int[][] neigh = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
                    for (int[] n : neigh) {
                        if (n[0] < 0 || n[0] > 15 || n[1] < 0 || n[1] > 15 || seen[n[0]][n[1]]) {
                            continue;
                        }
                        DropCell nc = cells[n[0]][n[1]];
                        if (nc != null && CaveDropTrap.cellsJoinPatch(cell.shellTopY(), nc.shellTopY())) {
                            seen[n[0]][n[1]] = true;
                            queue.add(n);
                        }
                    }
                }
                if (patch.size() < CaveDropTrap.MIN_PATCH_AREA) {
                    continue; // S50: single blocks and slivers never trap -- carpets only
                }
                if (patches >= CaveDropTrap.MAX_PATCHES_PER_CHUNK) {
                    continue; // census cap: one hidden false floor per chunk at most
                }
                if (!CaveDropTrap.shouldTrapPatch(random.nextFloat())) {
                    continue;
                }
                // Entombment law: the patch's first cell's landing must have standing room two blocks out
                // in some cardinal direction (the deeper layer goes somewhere).
                DropCell site = patch.get(0);
                if (!landingTraversable(level, cursor, baseX + site.lx(), baseZ + site.lz(), site.landingAirY())) {
                    continue;
                }
                patches++;
                for (DropCell cell : patch) {
                    int wx = baseX + cell.lx();
                    int wz = baseZ + cell.lz();
                    // Cover: the shell's walking surface becomes powder (sink-through trigger)...
                    cursor.set(wx, cell.shellTopY(), wz);
                    level.setBlock(cursor, POWDER_SNOW, 2);
                    // ...and any deeper shell blocks are punched so the sink continues into the drop.
                    for (int y2 = cell.shellTopY() - 1; y2 > cell.shellTopY() - cell.shellThickness(); y2--) {
                        cursor.set(wx, y2, wz);
                        level.setBlock(cursor, AIR, 2);
                    }
                    // Cushion at the landing (the S35 fall law), only into air.
                    cursor.set(wx, cell.landingAirY(), wz);
                    if (level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, POWDER_SNOW, 2);
                    }
                    covers++;
                    placedAny = true;
                }
            }
        }

        if (DEBUG && (candidateCount > 0 || placedAny)) {
            GlobeMod.LOGGER.info("[LAT][CAVEDROP] chunk=({},{}) candidates={} patches={} covers={}",
                    baseX >> 4, baseZ >> 4, candidateCount, patches, covers);
        }
        return placedAny;
    }

    /** S35 entombment law, verbatim geometry: some cardinal column two blocks from the site must have
     *  standing room (2 air) at landing level -- the deeper layer is a place, not a coffin. */
    private static boolean landingTraversable(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int siteX, int siteZ, int landingAirY) {
        int[][] probes = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] p : probes) {
            cursor.set(siteX + p[0], landingAirY, siteZ + p[1]);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            cursor.set(siteX + p[0], landingAirY + 1, siteZ + p[1]);
            if (level.getBlockState(cursor).isAir()) {
                return true;
            }
        }
        return false;
    }
}
