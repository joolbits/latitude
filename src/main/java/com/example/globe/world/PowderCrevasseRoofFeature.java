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

import java.util.ArrayList;
import java.util.List;

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
 *   <li>For each roofed column: build the S30 roof SANDWICH -- {@code snow_block} flush at the reference
 *       surface Y (indistinguishable from the snowfield: the "unsuspecting" tell is gone) directly over one
 *       hidden {@code powder_snow} marker, both only into air -- and give the crevasse floor a {@code
 *       powder_snow} cushion when that floor is bare stone/ice (the fall costs warmth/position, rarely the
 *       run). The runtime collapse event keys off this exact sandwich; the old EXPOSED powder lid (a visible
 *       sink-through) is superseded.</li>
 *   <li>DEEP DROP (Peetsa 2026-07-20 sketch: "sometimes you can drop down into a deep glacial cave"): a
 *       deterministic {@link PowderRoofTrap#DEEP_DROP_FRACTION} of roofed spans (the feature's own seeded
 *       RandomSource) probe straight down from the span-centre floor; if a contiguous cave void of at least
 *       {@link PowderRoofTrap#MIN_DEEP_VOID_AIR} air opens within {@link PowderRoofTrap#DEEP_DROP_PROBE_DEPTH}
 *       blocks, a narrow 2x2 shaft is punched connecting the crevasse floor to that void and the powder cushion
 *       is relocated to the true bottom -- the rare drop into the glacial-tunnel labyrinth below.</li>
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
    /** S30 (Peetsa 2026-07-20 eve sketch: snow_block flush with the snowfield = "normal-looking snow", the
     *  unsuspecting part) -- the TOP of the roof SANDWICH. Indistinguishable from the surrounding snowfield;
     *  directly beneath it sits the hidden {@link #POWDER_SNOW} marker, then the open void. The runtime collapse
     *  event ({@code world.SnowCollapseRuntime}) reads exactly this shape as its trigger signature. */
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    /** Used to carve the S30 deep-drop connecting shaft (open a roofed span's floor down to a pre-carved cave). */
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** S25b post-crew fix (Crew 4's own risk flag): the cushion is POWDER SNOW, not snow_block —
     *  snow_block does NOT reduce vanilla fall damage, so a 20-40 deep roofed drop could kill outright,
     *  violating the trap law ("costs warmth/position, rarely the run"). Powder snow fully negates fall
     *  damage, the victim sinks into it, and then the COLD is the price — exactly the design. Leather
     *  boots let them climb back out. S30: the cushion also lands at the TRUE BOTTOM of a deep-drop shaft so
     *  the sketch's "drop down into a deep glacial cave (onto powder snow so you don't take fall damage)" holds. */
    private static final BlockState CUSHION = Blocks.POWDER_SNOW.defaultBlockState();

    /** S31 flight recorder ({@code -Dlatitude.debugCollapse=true}): the TEST 121 flight reported "0 matches /
     *  never encountered a trap", so the feature now can NAME what it did per chunk — candidates seen, spans
     *  rolled, sandwiches placed, deep drops punched — the same instrument-before-reclaiming law as debugFreeze. */
    private static final boolean DEBUG = Boolean.getBoolean("latitude.debugCollapse");
    /** S31: with DEBUG on, prove the feature RUNS at all (a candidates=0 chunk logs nothing, which is
     *  indistinguishable from "never invoked" — this heartbeat names the difference every 64th call). */
    private static final java.util.concurrent.atomic.AtomicLong DEBUG_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

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
        if (DEBUG) {
            long n = DEBUG_CALLS.incrementAndGet();
            if (n == 1L || n % 64L == 0L) {
                GlobeMod.LOGGER.info("[LAT][COLLAPSE] alive calls={} at chunk=({},{})",
                        n, ctx.origin().getX() >> 4, ctx.origin().getZ() >> 4);
            }
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
        // orientation is caught; double-marking a column is idempotent at placement time). A roofed span
        // additionally rolls the S30 deep-drop fraction and, if it wins, records its centre column for the
        // downward connect-to-cave pass below (order of RandomSource draws is fixed => deterministic per
        // seed+chunk, Art VI; there is no flag-ON byte-identity axis -- the whole place() is barrens-gated).
        boolean[][] roofed = new boolean[16][16];
        List<int[]> deepDropCenters = new ArrayList<>();
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
                    if (PowderRoofTrap.shouldDeepDrop(random.nextFloat())) {
                        deepDropCenters.add(new int[]{span[0] + span[1] / 2, lz});
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
                    if (PowderRoofTrap.shouldDeepDrop(random.nextFloat())) {
                        deepDropCenters.add(new int[]{lx, span[0] + span[1] / 2});
                    }
                }
            }
        }

        boolean placedAny = false;
        int placedRoofs = 0; // S31 recorder: sandwiches ACTUALLY written (air-guard survivors), not just roofed-mask size
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (!roofed[lx][lz]) {
                    continue;
                }
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int roofY = reference[lx][lz] - 1; // the snowfield's own top-block Y -> flush sandwich top.
                int floorY = surfaceFirstAir[lx][lz] - 1; // this column's crevasse-floor top-solid block.

                // Cushion first (below), so a later heightmap-touched read of the roof is not needed. Only if
                // the floor is bare stone/ice: place a powder-snow cushion on the floor's first air block.
                cursor.set(worldX, floorY, worldZ);
                if (isBareLandingFloor(level.getBlockState(cursor).getBlock())) {
                    cursor.set(worldX, floorY + 1, worldZ);
                    safeSetBlock(level, cursor, CUSHION, BlockState::isAir);
                }

                // S30 SANDWICH: snow_block flush with the snowfield over a hidden powder_snow marker, both ONLY
                // into air and ONLY when BOTH slots are air (never overwrite a wall/ledge, and never leave a
                // half-sandwich the runtime signature would misread). roofY-1 is one block into the open slot
                // below the flush surface, so in a genuine cut it is air; a column where it is NOT is skipped.
                cursor.set(worldX, roofY, worldZ);
                boolean roofAir = level.getBlockState(cursor).isAir();
                cursor.set(worldX, roofY - 1, worldZ);
                boolean markerAir = level.getBlockState(cursor).isAir();
                if (roofAir && markerAir) {
                    cursor.set(worldX, roofY - 1, worldZ);
                    setBlock(level, cursor, POWDER_SNOW);
                    cursor.set(worldX, roofY, worldZ);
                    setBlock(level, cursor, SNOW_BLOCK);
                    placedAny = true;
                    placedRoofs++;
                }
            }
        }

        // S30 DEEP DROP: connect a fraction of roofed spans to a pre-carved cave below. Bounded (per centre:
        // one downward probe of <= DEEP_DROP_PROBE_DEPTH, then a 2x2 x <=depth carve + 4 cushion writes), and
        // chunk-local (the 2x2 base is clamped to [0,14] so both columns stay inside this 16x16 decorating chunk).
        for (int[] c : deepDropCenters) {
            int lx = c[0];
            int lz = c[1];
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || !roofed[lx][lz]) {
                continue; // only where a sandwich actually placed
            }
            int worldX = baseX + lx;
            int worldZ = baseZ + lz;
            int floorY = surfaceFirstAir[lx][lz] - 1; // centre column's crevasse-floor top-solid block

            // Probe straight down through the floor for the first contiguous AIR run that qualifies as a cave.
            int voidTop = Integer.MIN_VALUE;
            int voidBottom = Integer.MIN_VALUE;
            int run = 0;
            for (int y = floorY - 1; y >= floorY - PowderRoofTrap.DEEP_DROP_PROBE_DEPTH; y--) {
                cursor.set(worldX, y, worldZ);
                if (level.getBlockState(cursor).isAir()) {
                    if (run == 0) {
                        voidTop = y;
                    }
                    run++;
                    voidBottom = y;
                } else if (PowderRoofTrap.qualifiesDeepDrop(run)) {
                    break; // a qualifying void already found above this solid block -- stop
                } else {
                    run = 0; // reset: too-shallow air pocket, keep probing deeper
                }
            }
            if (!PowderRoofTrap.qualifiesDeepDrop(run)) {
                continue; // no connectable cave within reach -- leave this span a shallow crevasse trap
            }

            int bx = Math.min(lx, 14);
            int bz = Math.min(lz, 14);
            // Remove the centre's floating floor cushion so the victim actually drops through. Carving starts at
            // the crevasse FLOOR (never at the snowfield surface), so a 2x2 whose neighbours are slot WALLS only
            // cuts sub-floor rock/ice -- the snowfield above floorY, and the flush sandwich, stay intact/hidden.
            cursor.set(worldX, floorY + 1, worldZ);
            if (level.getBlockState(cursor).getBlock() == Blocks.POWDER_SNOW) {
                setBlock(level, cursor, AIR);
            }
            for (int cx = bx; cx <= bx + 1; cx++) {
                for (int cz = bz; cz <= bz + 1; cz++) {
                    int wx2 = baseX + cx;
                    int wz2 = baseZ + cz;
                    for (int y = floorY; y > voidTop; y--) {
                        cursor.set(wx2, y, wz2);
                        safeSetBlock(level, cursor, AIR, st -> !st.isAir()); // carve solid only, sub-floor only
                    }
                    cursor.set(wx2, voidBottom, wz2);
                    safeSetBlock(level, cursor, CUSHION, BlockState::isAir); // deep cushion at the true bottom
                }
            }
            placedAny = true;
        }
        if (DEBUG) {
            int candidates = 0;
            int sandwiches = 0;
            int firstX = Integer.MIN_VALUE;
            int firstY = 0;
            int firstZ = 0;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (candidate[lx][lz]) {
                        candidates++;
                    }
                    if (roofed[lx][lz]) {
                        sandwiches++;
                        if (firstX == Integer.MIN_VALUE) {
                            firstX = baseX + lx;
                            firstY = reference[lx][lz] - 1;
                            firstZ = baseZ + lz;
                        }
                    }
                }
            }
            if (candidates > 0 || placedAny) {
                GlobeMod.LOGGER.info("[LAT][COLLAPSE] chunk=({},{}) candidates={} roofedCols={} placedRoofs={} deepDrops={} firstRoof={},{},{}",
                        baseX >> 4, baseZ >> 4, candidates, sandwiches, placedRoofs, deepDropCenters.size(),
                        firstX, firstY, firstZ);
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
