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
 *   <li>S35 PATCH PASS (owner spec + level-designer spec, 2026-07-21): flood-fill candidates into
 *       4-connected components ({@link PowderRoofTrap#floodFillPatches}), clip to the chunk-interior box
 *       (trimmed cells stay open -- the cover ends flush at the trim line, the deliberate partial-snow-bridge
 *       edge), gate on {@link PowderRoofTrap#patchEligible} (wide PATCHES, not strips: minor dimension &le; 7,
 *       area &le; 48), roll {@link PowderRoofTrap#shouldRoofPatch} once per eligible patch (deterministic
 *       seeded RandomSource, Art VI: no new noise).</li>
 *   <li>2D RIM-RING LAW (the anti-floating invariant, generalized from S32): the ring of non-candidate
 *       neighbours must exist and span &le; {@link PowderRoofTrap#PATCH_RIM_MAX_SPREAD} in height; the whole
 *       patch covers FLAT at {@link PowderRoofTrap#patchRoofY} (the LOWEST rim's top-block Y -- flush with
 *       every rim, floating impossible). Columns failing {@link PowderRoofTrap#columnDeepEnoughForRoof} stay
 *       open windows; a patch below {@link PowderRoofTrap#PATCH_MIN_DEEP_FRACTION} deep columns is abandoned.</li>
 *   <li>S35 COVER + CUSHION (owner verbatim: "snow blocks are solid. You must cover traps with POWDER snow,
 *       and there must be a powder snow block directly vertically down at the base"): the cover is exactly ONE
 *       layer of {@code powder_snow} -- vanilla sink-through IS the trigger, no scripted event -- and EVERY
 *       covered column gets one powder cushion at its own landing point, whatever the floor material (except
 *       water, which already negates the fall and would float a fake shelf).</li>
 *   <li>DEEP DROP ("a trap over a deep crevasse into a cave"): {@link PowderRoofTrap#DEEP_DROP_FRACTION} of
 *       roofed patches probe {@link PowderRoofTrap#DEEP_DROP_PROBE_DEPTH} below their DEEPEST column's floor;
 *       on a {@link PowderRoofTrap#MIN_DEEP_VOID_AIR}+ void hit, a {@link PowderRoofTrap#shaftSide}-square
 *       shaft (2x2, or 3x3 under wide patches) is punched through, cushions at the void's true bottom -- the
 *       signature drop into the glacial-cave labyrinth.</li>
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
    /** Used to carve the deep-drop connecting shaft (open a roofed patch's floor down to a pre-carved cave). */
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

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

        // Per-column snowfield reference (windowed local max) + candidate mask (unchanged since V1).
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

        // S35 PATCH PASS (level-designer spec 2026-07-21; supersedes the 1-D span pass). Components in
        // deterministic flood order; one roof roll per ELIGIBLE patch and one deep-drop roll per WON roof
        // roll, drawn back-to-back so the random stream is a pure function of the eligible-patch sequence.
        boolean placedAny = false;
        int patchesRoofed = 0;
        int placedCovers = 0;
        int shaftsPunched = 0;
        int firstCoverX = Integer.MIN_VALUE;
        int firstCoverY = 0;
        int firstCoverZ = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (List<int[]> component : PowderRoofTrap.floodFillPatches(candidate)) {
            // Clip to the chunk-interior box so every surviving cell has all four neighbours readable
            // in-chunk; trimmed cells stay open slot (the cover ends flush at the trim line -- the
            // deliberate partial-snow-bridge edge, INTENTIONAL).
            List<int[]> clipped = new ArrayList<>();
            for (int[] c : component) {
                if (PowderRoofTrap.isInteriorCell(c[0], c[1])) {
                    clipped.add(c);
                }
            }
            if (!PowderRoofTrap.patchEligible(clipped)) {
                continue; // no roll consumed: the eligible-patch sequence defines the stream
            }
            if (!PowderRoofTrap.shouldRoofPatch(random.nextFloat())) {
                continue;
            }
            boolean tryDeep = PowderRoofTrap.shouldDeepDrop(random.nextFloat());

            // 2D rim ring: in-chunk 4-neighbours of patch cells that are NOT candidates. (Neighbours that
            // are candidates -- in-patch, clipped-out, or a separate slot -- are the slot continuing, not
            // rim.) The ring must exist and read as continuous snowfield.
            int minRim = Integer.MAX_VALUE;
            int maxRim = Integer.MIN_VALUE;
            for (int[] c : clipped) {
                for (int d = 0; d < 4; d++) {
                    int nx = c[0] + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nz = c[1] + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (candidate[nx][nz]) {
                        continue;
                    }
                    int rim = surfaceFirstAir[nx][nz];
                    minRim = Math.min(minRim, rim);
                    maxRim = Math.max(maxRim, rim);
                }
            }
            if (minRim == Integer.MAX_VALUE || !PowderRoofTrap.rimRingBridgeable(minRim, maxRim)) {
                continue; // no anchoring rim, or slope-not-slot: leave the whole component open.
            }
            int roofY = PowderRoofTrap.patchRoofY(minRim);

            // Patch-level depth vote against the REAL cover height; failing columns become open windows,
            // and a mostly-shallow component is abandoned outright (a scoop, not a trap).
            int deepCells = 0;
            for (int[] c : clipped) {
                if (PowderRoofTrap.columnDeepEnoughForRoof(surfaceFirstAir[c[0]][c[1]], roofY)) {
                    deepCells++;
                }
            }
            if (!PowderRoofTrap.patchDeepEnough(deepCells, clipped.size())) {
                continue;
            }

            // Deep-drop shaft: sited at the DEEPEST passing cell (shortest remaining rock, most
            // crevasse-like read; ties broken by flood order), footprint 2x2 or 3x3 by patch width,
            // clamped in-chunk. Probe below the site's floor for a qualifying cave void.
            int shaftSide = PowderRoofTrap.shaftSide(PowderRoofTrap.patchMinorDimension(clipped));
            int shaftMinX = Integer.MIN_VALUE;
            int shaftMinZ = Integer.MIN_VALUE;
            int voidTop = Integer.MIN_VALUE;
            int voidBottom = Integer.MIN_VALUE;
            int siteFloorY = Integer.MIN_VALUE;
            if (tryDeep) {
                int bestDepth = -1;
                int siteX = -1;
                int siteZ = -1;
                for (int[] c : clipped) {
                    int own = surfaceFirstAir[c[0]][c[1]];
                    if (!PowderRoofTrap.columnDeepEnoughForRoof(own, roofY)) {
                        continue;
                    }
                    int depth = (roofY + 1) - own;
                    if (depth > bestDepth) {
                        bestDepth = depth;
                        siteX = c[0];
                        siteZ = c[1];
                    }
                }
                if (siteX >= 0) {
                    siteFloorY = surfaceFirstAir[siteX][siteZ] - 1;
                    int run = 0;
                    for (int y = siteFloorY - 1; y >= siteFloorY - PowderRoofTrap.DEEP_DROP_PROBE_DEPTH; y--) {
                        cursor.set(baseX + siteX, y, baseZ + siteZ);
                        if (level.getBlockState(cursor).isAir()) {
                            if (run == 0) {
                                voidTop = y;
                            }
                            run++;
                            voidBottom = y;
                        } else if (PowderRoofTrap.qualifiesDeepDrop(run)) {
                            break;
                        } else {
                            run = 0;
                        }
                    }
                    // STRATEGIST MUST-FIX (S35 risk 2, "closet-void entombment"): a 4-air VERTICAL pocket is
                    // not a cave -- a bootless player dropped 25 blocks into a sealed closet has only
                    // die-and-respawn as counterplay, the one unacceptable outcome. Punch only when the void
                    // is horizontally TRAVERSABLE: some cardinal column 2 blocks out (just beyond any shaft
                    // footprint) has standing room (2 air) at the landing level -- i.e. the void goes
                    // somewhere. Otherwise the patch stays a floor trap; no shaft.
                    if (PowderRoofTrap.qualifiesDeepDrop(run) && globe$voidTraversable(
                            level, cursor, baseX + siteX, baseZ + siteZ, voidBottom)) {
                        shaftMinX = Math.min(Math.max(siteX - (shaftSide - 1) / 2, 0), 16 - shaftSide);
                        shaftMinZ = Math.min(Math.max(siteZ - (shaftSide - 1) / 2, 0), 16 - shaftSide);
                    }
                }
            }
            boolean shaftQualified = shaftMinX != Integer.MIN_VALUE;

            // COVER + CUSHIONS. Cover = exactly ONE layer of powder_snow at roofY (owner law: solid snow
            // was "the mistake"; vanilla sink-through IS the trigger). Cushion = one powder at EVERY covered
            // column's own landing point (owner law: "directly vertically down at the base"), whatever the
            // floor material -- except water, which already negates the fall and would float a fake shelf.
            int patchCovers = 0;
            for (int[] c : clipped) {
                int lx = c[0];
                int lz = c[1];
                int own = surfaceFirstAir[lx][lz];
                if (!PowderRoofTrap.columnDeepEnoughForRoof(own, roofY)) {
                    continue; // natural window in the cover
                }
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                // STRATEGIST MUST-FIX (S35 risk 1, "skinned-pond landing"): a water floor gets no cushion
                // (powder would float a fake shelf), and gen-time "safe water" is a lie later -- the tick
                // freeze law can grow an ice SKIN on a roofed pond, turning a 30-block drop into a landing
                // on ice with NO cushion. A column whose floor is fluid is therefore never COVERED at all
                // (it stays an open window); the cushion guarantee is absolute over every covered column.
                cursor.set(worldX, own - 1, worldZ);
                if (!level.getBlockState(cursor).getFluidState().isEmpty()) {
                    continue;
                }
                boolean inShaft = shaftQualified
                        && lx >= shaftMinX && lx < shaftMinX + shaftSide
                        && lz >= shaftMinZ && lz < shaftMinZ + shaftSide;
                if (!inShaft) {
                    // Floor cushion at this column's first-air landing point (own), only into air.
                    cursor.set(worldX, own, worldZ);
                    safeSetBlock(level, cursor, POWDER_SNOW, BlockState::isAir);
                }
                // The cover itself: only into air, and only with air directly beneath (never seal a column
                // whose shaft is blocked at the throat -- a cover over solid is the outlawed stepping-stone).
                cursor.set(worldX, roofY, worldZ);
                boolean roofAir = level.getBlockState(cursor).isAir();
                cursor.set(worldX, roofY - 1, worldZ);
                boolean underAir = level.getBlockState(cursor).isAir();
                if (roofAir && underAir) {
                    cursor.set(worldX, roofY, worldZ);
                    setBlock(level, cursor, POWDER_SNOW);
                    placedAny = true;
                    placedCovers++;
                    patchCovers++;
                    if (firstCoverX == Integer.MIN_VALUE) {
                        firstCoverX = worldX;
                        firstCoverY = roofY;
                        firstCoverZ = worldZ;
                    }
                }
            }

            // Punch the shaft AFTER covers so its columns read their own guards cleanly: carve sub-floor
            // rock from the SITE's floor down to the void (solid only -- the snowfield above and the flush
            // cover are untouched), then one powder cushion per shaft column at the void's true bottom.
            if (shaftQualified && patchCovers > 0) {
                for (int cx = shaftMinX; cx < shaftMinX + shaftSide; cx++) {
                    for (int cz = shaftMinZ; cz < shaftMinZ + shaftSide; cz++) {
                        int wx2 = baseX + cx;
                        int wz2 = baseZ + cz;
                        // Remove any floor cushion or floating shelf in the throat so the victim passes through.
                        cursor.set(wx2, siteFloorY + 1, wz2);
                        if (level.getBlockState(cursor).getBlock() == Blocks.POWDER_SNOW) {
                            setBlock(level, cursor, AIR);
                        }
                        for (int y = siteFloorY; y > voidTop; y--) {
                            cursor.set(wx2, y, wz2);
                            safeSetBlock(level, cursor, AIR, st -> !st.isAir()); // carve solid only, sub-floor only
                        }
                        cursor.set(wx2, voidBottom - 1, wz2);
                        boolean waterBottom = !level.getBlockState(cursor).getFluidState().isEmpty();
                        if (!waterBottom) {
                            cursor.set(wx2, voidBottom, wz2);
                            safeSetBlock(level, cursor, POWDER_SNOW, BlockState::isAir);
                        }
                    }
                }
                shaftsPunched++;
                placedAny = true;
            }
            if (patchCovers > 0) {
                patchesRoofed++;
            }
        }

        if (DEBUG) {
            int candidates = 0;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (candidate[lx][lz]) {
                        candidates++;
                    }
                }
            }
            if (candidates > 0 || placedAny) {
                GlobeMod.LOGGER.info("[LAT][COLLAPSE] chunk=({},{}) candidates={} patchesRoofed={} covers={} shafts={} firstCover={},{},{}",
                        baseX >> 4, baseZ >> 4, candidates, patchesRoofed, placedCovers, shaftsPunched,
                        firstCoverX, firstCoverY, firstCoverZ);
            }
        }
        return placedAny;
    }

    /**
     * Is the probed void HORIZONTALLY TRAVERSABLE at the landing level -- a cave that goes somewhere, not a
     * sealed pocket? True when any cardinal column 2 blocks from the shaft site (just beyond a 2x2/3x3
     * footprint) has standing room ({@code voidBottom} and {@code voidBottom+1} both air). WorldGenLevel
     * exposes the 3x3-chunk decoration region, so the +/-2 reads are always in range.
     */
    private static boolean globe$voidTraversable(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int siteWorldX, int siteWorldZ, int voidBottom) {
        int[][] probes = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] p : probes) {
            cursor.set(siteWorldX + p[0], voidBottom, siteWorldZ + p[1]);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            cursor.set(siteWorldX + p[0], voidBottom + 1, siteWorldZ + p[1]);
            if (level.getBlockState(cursor).isAir()) {
                return true;
            }
        }
        return false;
    }
}
