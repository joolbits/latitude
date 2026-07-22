package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PowderRoofTrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Phase 5 S36 HIDDEN-BRIDGE CREVASSE TRAP ({@code globe:powder_crevasse_roof}). Owner-corrected from the
 * S35 small-patch form after video proof showed that a trap must read as ordinary, nearly level snowfield
 * spanning a deep crevasse, not as a few powder blocks down inside an already-visible opening. Runs at the
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
 *       PowderRoofTrap#MIN_CANDIDATE_DEPTH_BLOCKS} above the column's own surface).</li>
 *   <li>S36 BRIDGE PASS (owner spec + independent level-designer review, 2026-07-21): flood-fill candidates,
 *       then select a contiguous, hole-free 12..48-column segment that is 3..7 columns across and remains
 *       wholly inside this chunk. Tiny lids, one-wide strips, and chunk-border fragments are invalid.</li>
 *   <li>WALK-ON PLANE: the cover is flat at the footprint's surrounding snowfield reference, never at a low
 *       shelf inside the opening, and at least one opposing bank pair must meet it within one normal terrain
 *       step. Every covered column must pass the 18-block final-depth gate or nothing is written.</li>
 *   <li>COVER + CUSHION (owner verbatim: "snow blocks are solid. You must cover traps with POWDER snow,
 *       and there must be a powder snow block directly vertically down at the base"): the cover is exactly ONE
 *       layer of {@code powder_snow} -- vanilla sink-through IS the trigger, no scripted event -- and EVERY
 *       covered column gets one powder cushion on solid non-fluid support. A vanilla surface snow layer at
 *       that exact landing cell is safely replaced; any other occupied cell rejects the whole plan.</li>
 *   <li>DEEP CAVE OPTION: a contained 3x3 throat may connect the bridge to an existing traversable cave, but
 *       only after every landing cell, support block, void block, and cushion is preflighted atomically.</li>
 * </ol>
 *
 * <p><b>Vanilla-first / flag honesty.</b> The feature is REGISTERED unconditionally at mod-init (registries
 * must be consistent across sessions; see {@link #register()}), but its BEHAVIOUR is gated on {@link
 * LatitudeV2Flags#POLAR_BARRENS_ENABLED} and {@link LatitudeV2Flags#GLACIAL_CAVES_V1_ENABLED}. Flag-off the
 * barrens biome never generates, and flag-off crevasses must never acquire synthetic trap shafts; the early
 * return keeps both contracts explicit. No custom carver/noise: it reads existing heightmaps.
 */
public final class PowderCrevasseRoofFeature extends Feature<NoneFeatureConfiguration> {

    /** The registered singleton (populated by {@link #register()}), mirroring {@code PolarOutfitting}. */
    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    /** Used to carve the deep-drop connecting shaft (open a roofed patch's floor down to a pre-carved cave). */
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Identifier POLAR_BARRENS_ID =
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "polar_barrens");
    /** A 3x3 shaft alone is nine cells; sixteen standable cells proves the landing opens beyond its throat. */
    private static final int MIN_TRAVERSABLE_LANDING_CELLS = 16;
    /** Lava spreads four horizontal cells in the Overworld; a deep landing must sit outside that reach. */
    private static final int LANDING_FLUID_CLEARANCE = 4;
    /** A water source can advance seven horizontal cells; optional cave throats must stay outside that reach. */
    private static final int SHAFT_WATER_CLEARANCE = 7;

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
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false; // no barrens or no crevasse carver: no trap behavior.
        }
        if (DEBUG) {
            long n = DEBUG_CALLS.incrementAndGet();
            if (n == 1L || n % 64L == 0L) {
                GlobeMod.LOGGER.info("[LAT][COLLAPSE] alive calls={} at chunk=({},{})",
                        n, ctx.origin().getX() >> 4, ctx.origin().getZ() >> 4);
            }
        }
        WorldGenLevel level = ctx.level();
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

        // S36 HIDDEN-BRIDGE PASS (Peetsa correction + independent level-design review, 2026-07-21).
        // Each accepted footprint is one atomic local bridge: meaningful area/width, no holes, aligned
        // opposing banks, all columns deep and safe, and every write preflighted before the first mutation.
        boolean placedAny = false;
        int patchesRoofed = 0;
        int placedCovers = 0;
        int shaftsPunched = 0;
        int componentsSeen = 0;
        int footprintRejects = 0;
        int approachRejects = 0;
        int depthRejects = 0;
        int safetyRejects = 0;
        int bankAccessRejects = 0;
        int biomeRejects = 0;
        int floorRejects = 0;
        int cushionRejects = 0;
        int roofBlockRejects = 0;
        int firstCoverX = Integer.MIN_VALUE;
        int firstCoverY = 0;
        int firstCoverZ = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (List<int[]> component : PowderRoofTrap.floodFillPatches(candidate)) {
            componentsSeen++;

            // A local encounter never writes the chunk border. A through-running crevasse may continue past
            // the bridge's long ends, but may not be cut across its narrow axis: that would be a partial lid,
            // not an authored crossing. The one-block border also makes adjacent chunk passes non-overlapping.
            List<List<int[]>> footprintCandidates = PowderRoofTrap.bridgeFootprintCandidates(component);
            if (footprintCandidates.isEmpty()) {
                footprintRejects++;
                continue;
            }
            for (List<int[]> clipped : footprintCandidates) {
                List<Integer> snowfieldReferences = new ArrayList<>();
                for (int[] c : clipped) {
                    snowfieldReferences.add(reference[c[0]][c[1]]);
                }

                // Record every cardinal bank separately, then select the locally referenced snowfield plane with
                // the strongest cardinal OR diagonal opposing pair. A low shelf is never a candidate plane.
                List<Integer> northBank = new ArrayList<>();
                List<Integer> southBank = new ArrayList<>();
                List<Integer> westBank = new ArrayList<>();
                List<Integer> eastBank = new ArrayList<>();
                for (int[] c : clipped) {
                    if (!candidate[c[0]][c[1] - 1]) {
                        northBank.add(surfaceFirstAir[c[0]][c[1] - 1]);
                    }
                    if (!candidate[c[0]][c[1] + 1]) {
                        southBank.add(surfaceFirstAir[c[0]][c[1] + 1]);
                    }
                    if (!candidate[c[0] - 1][c[1]]) {
                        westBank.add(surfaceFirstAir[c[0] - 1][c[1]]);
                    }
                    if (!candidate[c[0] + 1][c[1]]) {
                        eastBank.add(surfaceFirstAir[c[0] + 1][c[1]]);
                    }
                }
                int roofY = PowderRoofTrap.selectSnowfieldRoofY(
                        snowfieldReferences, northBank, southBank, westBank, eastBank);
                if (roofY == Integer.MIN_VALUE) {
                    approachRejects++;
                    bankAccessRejects++;
                    continue;
                }

                // Plan every landing against the FINAL snowfield plane. Existing deep floors stay untouched;
                // shallow terraced shoulders may deepen by at most eight replaceable blocks. This widens a real
                // crevasse mouth without inventing a shaft in intact ground. One impossible column rejects all.
                int[][] landingFirstAir = new int[16][16];
                boolean landingPlanValid = true;
                for (int[] c : clipped) {
                    int planned = PowderRoofTrap.plannedLandingFirstAir(
                            surfaceFirstAir[c[0]][c[1]], roofY);
                    if (planned == Integer.MIN_VALUE) {
                        landingPlanValid = false;
                        break;
                    }
                    landingFirstAir[c[0]][c[1]] = planned;
                }
                if (!landingPlanValid) {
                    depthRejects++;
                    continue;
                }

                // Optional deep cave connection. Every accepted bridge may look; actual placement still requires
                // a contained 3x3 throat, a traversable void, solid non-fluid support beneath every final cushion,
                // and a final landing at least as deep as the bridge contract.
                int shaftSide = PowderRoofTrap.shaftSide(PowderRoofTrap.patchMinorDimension(clipped));
                int shaftMinX = Integer.MIN_VALUE;
                int shaftMinZ = Integer.MIN_VALUE;
                int voidBottom = Integer.MIN_VALUE;
                int bestFinalDepth = -1;
                for (int[] c : clipped) {
                    int proposedMinX = c[0] - shaftSide / 2;
                    int proposedMinZ = c[1] - shaftSide / 2;
                    if (!PowderRoofTrap.containsSquare(clipped, proposedMinX, proposedMinZ, shaftSide)) {
                        continue;
                    }
                    int siteFloorY = surfaceFirstAir[c[0]][c[1]] - 1;
                    int localBottom = Integer.MIN_VALUE;
                    int run = 0;
                    for (int y = siteFloorY - 1; y >= siteFloorY - PowderRoofTrap.DEEP_DROP_PROBE_DEPTH; y--) {
                        cursor.set(baseX + c[0], y, baseZ + c[1]);
                        if (level.getBlockState(cursor).isAir()) {
                            run++;
                            localBottom = y;
                        } else if (PowderRoofTrap.qualifiesDeepDrop(run)) {
                            break;
                        } else {
                            run = 0;
                            localBottom = Integer.MIN_VALUE;
                        }
                    }
                    int finalDepth = localBottom == Integer.MIN_VALUE ? -1 : (roofY + 1) - localBottom;
                    if (!PowderRoofTrap.qualifiesDeepDrop(run)
                            || !PowderRoofTrap.deepDropDepthEligible(finalDepth)
                            || finalDepth <= bestFinalDepth
                            || !globe$voidTraversable(level, cursor, baseX + c[0], baseZ + c[1], localBottom)
                            || !globe$landingFluidSafe(level, cursor, baseX, baseZ,
                                    proposedMinX, proposedMinZ, shaftSide, localBottom)
                            || !globe$shaftPathSafe(level, cursor, baseX, baseZ,
                                    proposedMinX, proposedMinZ, shaftSide, surfaceFirstAir,
                                    localBottom, roofY)) {
                        continue;
                    }
                    bestFinalDepth = finalDepth;
                    shaftMinX = proposedMinX;
                    shaftMinZ = proposedMinZ;
                    voidBottom = localBottom;
                }
                boolean shaftQualified = shaftMinX != Integer.MIN_VALUE;

                // Atomic preflight: biome membership, final landing support, every deepened block, cover air,
                // throat air, and cushion target must all be valid before the first change. A single failure
                // rejects the encounter; there are no late windows, partial pits, or orphan cushions.
                boolean safe = true;
                for (int[] c : clipped) {
                    int lx = c[0];
                    int lz = c[1];
                    int own = surfaceFirstAir[lx][lz];
                    int worldX = baseX + lx;
                    int worldZ = baseZ + lz;
                    if (!globe$isPolarBarrens(level, cursor, worldX, roofY + 1, worldZ)) {
                        biomeRejects++;
                        safe = false;
                        break;
                    }
                    // Validate the ordinary landing for every column even when a cave throat is tentatively
                    // selected. If the optional extension proves wet below, the complete bridge must still be
                    // able to fall back atomically to these ordinary landings.
                    int landing = landingFirstAir[lx][lz];
                    if (!globe$landingLavaSafe(level, cursor, worldX, worldZ, landing)) {
                        cushionRejects++;
                        safe = false;
                        break;
                    }
                    cursor.set(worldX, landing - 1, worldZ);
                    BlockState support = level.getBlockState(cursor);
                    if (support.isAir() || !support.getFluidState().isEmpty()) {
                        floorRejects++;
                        safe = false;
                        break;
                    }
                    for (int y = landing; y < roofY; y++) {
                        cursor.set(worldX, y, worldZ);
                        BlockState path = level.getBlockState(cursor);
                        if (!path.getFluidState().isEmpty()
                                || (!path.isAir() && !path.is(Blocks.SNOW)
                                        && (y >= own || !path.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)))) {
                            cushionRejects++;
                            safe = false;
                            break;
                        }
                    }
                    if (!safe) {
                        break;
                    }
                    cursor.set(worldX, roofY, worldZ);
                    boolean roofAir = level.getBlockState(cursor).isAir();
                    cursor.set(worldX, roofY - 1, worldZ);
                    boolean underAir = level.getBlockState(cursor).isAir();
                    if (!roofAir || !underAir) {
                        roofBlockRejects++;
                        safe = false;
                        break;
                    }
                }
                if (!safe) {
                    safetyRejects++;
                    continue;
                }

                // A locally dry path can still be invaded after chunk promotion by side water and then freeze
                // into a shallow ice shelf. Follow actual passable space backwards from the complete planned
                // fall volume; solid walls remain barriers. A wet ordinary volume rejects this footprint so the
                // planner can try the next segment. Wetness confined to the deeper cave extension discards only
                // that optional extension and retains the independently verified ordinary bridge.
                if (!globe$fallVolumeFluidIsolated(level, cursor, baseX, baseZ, clipped,
                        landingFirstAir, roofY, Integer.MIN_VALUE, Integer.MIN_VALUE, 0, Integer.MIN_VALUE)) {
                    cushionRejects++;
                    safetyRejects++;
                    continue;
                }
                if (shaftQualified && !globe$fallVolumeFluidIsolated(level, cursor, baseX, baseZ, clipped,
                        landingFirstAir, roofY, shaftMinX, shaftMinZ, shaftSide, voidBottom)) {
                    shaftQualified = false;
                    shaftMinX = Integer.MIN_VALUE;
                    shaftMinZ = Integer.MIN_VALUE;
                    voidBottom = Integer.MIN_VALUE;
                }

                // All guards are green: write the complete plan. Non-shaft columns receive a landing cushion after
                // any bounded shoulder deepening; shaft columns share a fully preflighted cave landing.
                for (int[] c : clipped) {
                    int lx = c[0];
                    int lz = c[1];
                    boolean inShaft = shaftQualified
                            && lx >= shaftMinX && lx < shaftMinX + shaftSide
                            && lz >= shaftMinZ && lz < shaftMinZ + shaftSide;
                    if (!inShaft) {
                        int landing = landingFirstAir[lx][lz];
                        for (int y = roofY - 1; y > landing; y--) {
                            cursor.set(baseX + lx, y, baseZ + lz);
                            if (!level.getBlockState(cursor).isAir()) {
                                setBlock(level, cursor, AIR);
                            }
                        }
                        cursor.set(baseX + lx, landing, baseZ + lz);
                        setBlock(level, cursor, POWDER_SNOW);
                    }
                }
                if (shaftQualified) {
                    for (int cx = shaftMinX; cx < shaftMinX + shaftSide; cx++) {
                        for (int cz = shaftMinZ; cz < shaftMinZ + shaftSide; cz++) {
                            int wx2 = baseX + cx;
                            int wz2 = baseZ + cz;
                            for (int y = roofY - 1; y > voidBottom; y--) {
                                cursor.set(wx2, y, wz2);
                                if (!level.getBlockState(cursor).isAir()) {
                                    setBlock(level, cursor, AIR);
                                }
                            }
                            cursor.set(wx2, voidBottom, wz2);
                            setBlock(level, cursor, POWDER_SNOW);
                        }
                    }
                    shaftsPunched++;
                }
                for (int[] c : clipped) {
                    int worldX = baseX + c[0];
                    int worldZ = baseZ + c[1];
                    cursor.set(worldX, roofY, worldZ);
                    setBlock(level, cursor, POWDER_SNOW);
                    if (firstCoverX == Integer.MIN_VALUE) {
                        firstCoverX = worldX;
                        firstCoverY = roofY;
                        firstCoverZ = worldZ;
                    }
                }
                placedAny = true;
                patchesRoofed++;
                placedCovers += clipped.size();
                break; // at most one authored encounter per natural component in this chunk.
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
                GlobeMod.LOGGER.info("[LAT][COLLAPSE] chunk=({},{}) candidates={} components={} encounters={} covers={} shafts={} rejectFootprint={} rejectApproach={} rejectBankAccess={} rejectDepth={} rejectSafety={} rejectBiome={} rejectFloor={} rejectCushion={} rejectRoofBlock={} firstCover={},{},{}",
                        baseX >> 4, baseZ >> 4, candidates, componentsSeen, patchesRoofed, placedCovers,
                        shaftsPunched, footprintRejects, approachRejects, bankAccessRejects, depthRejects,
                        safetyRejects, biomeRejects, floorRejects,
                        cushionRejects, roofBlockRejects,
                        firstCoverX, firstCoverY, firstCoverZ);
            }
        }
        return placedAny;
    }

    private static boolean globe$isPolarBarrens(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int worldX, int y, int worldZ) {
        cursor.set(worldX, y, worldZ);
        return level.getBiome(cursor).unwrapKey()
                .map(key -> key.identifier().equals(POLAR_BARRENS_ID))
                .orElse(false);
    }

    /**
     * Atomic deep-shaft preflight. Every block the later 3x3 writer would remove must already be air or
     * ordinary carver-replaceable terrain, never fluid or protected/non-carvable material. The complete fall
     * path is checked down to the shared powder cushion, whose support must be solid and dry in every column.
     */
    private static boolean globe$shaftPathSafe(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int baseX, int baseZ, int shaftMinX, int shaftMinZ, int shaftSide,
            int[][] surfaceFirstAir, int voidBottom, int roofY) {
        for (int lx = shaftMinX; lx < shaftMinX + shaftSide; lx++) {
            for (int lz = shaftMinZ; lz < shaftMinZ + shaftSide; lz++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int own = surfaceFirstAir[lx][lz];
                for (int y = roofY - 1; y > voidBottom; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState carve = level.getBlockState(cursor);
                    if (!carve.getFluidState().isEmpty()
                            || (!carve.isAir() && !carve.is(Blocks.SNOW)
                                    && (y >= own || !carve.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)))) {
                        return false;
                    }
                }
                cursor.set(worldX, voidBottom, worldZ);
                if (!level.getBlockState(cursor).isAir()) {
                    return false;
                }
                cursor.set(worldX, voidBottom - 1, worldZ);
                BlockState support = level.getBlockState(cursor);
                if (support.isAir() || !support.getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A cave can be spacious yet still be a lethal landing if lava or water can immediately spread onto the
     * powder cushion. Require the complete 3x3 throat plus a four-block horizontal halo to be fluid-free at
     * the landing and player-height planes. Four blocks is the Overworld lava-spread reach; water is excluded
     * by the same stronger safety boundary. Read-only and bounded (11x11x3 at the widest throat).
     */
    private static boolean globe$landingFluidSafe(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int baseX, int baseZ, int shaftMinX, int shaftMinZ, int shaftSide, int voidBottom) {
        int minX = baseX + shaftMinX - LANDING_FLUID_CLEARANCE;
        int maxX = baseX + shaftMinX + shaftSide - 1 + LANDING_FLUID_CLEARANCE;
        int minZ = baseZ + shaftMinZ - LANDING_FLUID_CLEARANCE;
        int maxZ = baseZ + shaftMinZ + shaftSide - 1 + LANDING_FLUID_CLEARANCE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = voidBottom; y <= voidBottom + 2; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Runtime-stable, path-aware fluid gate for one complete bridge plan. The flood starts from every block
     * that the plan will turn into powder or fall-through space and walks horizontally or upward (the reverse
     * of water's horizontal/downward motion) through cells that do not block motion. Planned-to-clear terrain
     * is explicitly passable; unrelated solid walls remain barriers. The search is bounded to water's
     * seven-block horizontal reach around the footprint and seven blocks above the roof.
     *
     * <p>The optional override lowers only the selected throat's landing. Passing no override validates the
     * ordinary bridge; passing one validates the combined ordinary bridge plus cave extension.
     */
    private static boolean globe$fallVolumeFluidIsolated(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int baseX, int baseZ, List<int[]> footprint, int[][] ordinaryLandingFirstAir, int roofY,
            int overrideMinX, int overrideMinZ, int overrideSide, int overrideLandingY) {
        int minLocalX = footprint.stream().mapToInt(c -> c[0]).min().orElseThrow();
        int maxLocalX = footprint.stream().mapToInt(c -> c[0]).max().orElseThrow();
        int minLocalZ = footprint.stream().mapToInt(c -> c[1]).min().orElseThrow();
        int maxLocalZ = footprint.stream().mapToInt(c -> c[1]).max().orElseThrow();
        int minLandingY = Integer.MAX_VALUE;
        for (int[] c : footprint) {
            boolean overridden = overrideSide > 0
                    && c[0] >= overrideMinX && c[0] < overrideMinX + overrideSide
                    && c[1] >= overrideMinZ && c[1] < overrideMinZ + overrideSide;
            minLandingY = Math.min(minLandingY,
                    overridden ? overrideLandingY : ordinaryLandingFirstAir[c[0]][c[1]]);
        }

        int minX = baseX + minLocalX - SHAFT_WATER_CLEARANCE;
        int maxX = baseX + maxLocalX + SHAFT_WATER_CLEARANCE;
        int minZ = baseZ + minLocalZ - SHAFT_WATER_CLEARANCE;
        int maxZ = baseZ + maxLocalZ + SHAFT_WATER_CLEARANCE;
        int minY = minLandingY + 1;
        int maxY = Math.min(level.getMaxY() - 1, roofY + SHAFT_WATER_CLEARANCE);
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int sizeY = maxY - minY + 1;
        int capacity = sizeX * sizeZ * sizeY;
        boolean[] planned = new boolean[capacity];
        boolean[] visited = new boolean[capacity];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int[] c : footprint) {
            boolean overridden = overrideSide > 0
                    && c[0] >= overrideMinX && c[0] < overrideMinX + overrideSide
                    && c[1] >= overrideMinZ && c[1] < overrideMinZ + overrideSide;
            int landingY = overridden ? overrideLandingY : ordinaryLandingFirstAir[c[0]][c[1]];
            int xOffset = baseX + c[0] - minX;
            int zOffset = baseZ + c[1] - minZ;
            for (int y = landingY + 1; y <= roofY; y++) {
                int index = globe$volumeIndex(xOffset, zOffset, y - minY, sizeZ, sizeY);
                planned[index] = true;
                if (!visited[index]) {
                    visited[index] = true;
                    queue.addLast(index);
                }
            }
        }

        int[][] horizontal = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int yOffset = index % sizeY;
            int plane = index / sizeY;
            int zOffset = plane % sizeZ;
            int xOffset = plane / sizeZ;
            cursor.set(minX + xOffset, minY + yOffset, minZ + zOffset);
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            for (int[] direction : horizontal) {
                int nextX = xOffset + direction[0];
                int nextZ = zOffset + direction[1];
                if (nextX >= 0 && nextX < sizeX && nextZ >= 0 && nextZ < sizeZ) {
                    int next = globe$volumeIndex(nextX, nextZ, yOffset, sizeZ, sizeY);
                    if (!visited[next] && globe$waterReachableCell(level, cursor, planned[next],
                            minX + nextX, minY + yOffset, minZ + nextZ)) {
                        visited[next] = true;
                        queue.addLast(next);
                    }
                }
            }
            if (yOffset + 1 < sizeY) {
                int above = globe$volumeIndex(xOffset, zOffset, yOffset + 1, sizeZ, sizeY);
                if (!visited[above] && globe$waterReachableCell(level, cursor, planned[above],
                        minX + xOffset, minY + yOffset + 1, minZ + zOffset)) {
                    visited[above] = true;
                    queue.addLast(above);
                }
            }
        }
        return true;
    }

    private static int globe$volumeIndex(int xOffset, int zOffset, int yOffset, int sizeZ, int sizeY) {
        return (xOffset * sizeZ + zOffset) * sizeY + yOffset;
    }

    private static boolean globe$waterReachableCell(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            boolean planned, int x, int y, int z) {
        if (planned) {
            return true;
        }
        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);
        return !state.getFluidState().isEmpty() || !state.blocksMotion();
    }

    /**
     * Ordinary crevasse landings do not punch a new cave room, but their powder cushion still must not sit
     * inside lava's four-block Overworld spread reach. Water has a narrow runtime cushion guard; lava remains
     * vanilla, so a nearby lava cell rejects the encounter before any write. Manhattan distance mirrors the
     * actual horizontal step budget and avoids excluding harmless diagonal fluids outside that reach.
     */
    private static boolean globe$landingLavaSafe(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int worldX, int worldZ, int landingY) {
        for (int dx = -LANDING_FLUID_CLEARANCE; dx <= LANDING_FLUID_CLEARANCE; dx++) {
            for (int dz = -LANDING_FLUID_CLEARANCE; dz <= LANDING_FLUID_CLEARANCE; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > LANDING_FLUID_CLEARANCE) {
                    continue;
                }
                for (int y = landingY; y <= landingY + 2; y++) {
                    cursor.set(worldX + dx, y, worldZ + dz);
                    if (level.getFluidState(cursor).is(FluidTags.LAVA)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Is the probed void a real landing room rather than a shaft-sized closet? Flood at the landing's two-air
     * walking plane within a bounded 9x9 box. Sixteen reachable columns necessarily extend beyond the 3x3
     * throat and leave the player somewhere to orient, move, and climb from.
     */
    private static boolean globe$voidTraversable(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
            int siteWorldX, int siteWorldZ, int voidBottom) {
        final int radius = 4;
        final int side = radius * 2 + 1;
        boolean[][] seen = new boolean[side][side];
        int[] queue = new int[side * side];
        int head = 0;
        int tail = 0;
        queue[tail++] = (radius << 8) | radius;
        seen[radius][radius] = true;
        int reachable = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int packed = queue[head++];
            int gx = packed >> 8;
            int gz = packed & 0xff;
            int worldX = siteWorldX + gx - radius;
            int worldZ = siteWorldZ + gz - radius;
            cursor.set(worldX, voidBottom, worldZ);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            cursor.set(worldX, voidBottom + 1, worldZ);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            reachable++;
            if (reachable >= MIN_TRAVERSABLE_LANDING_CELLS) {
                return true;
            }
            for (int[] direction : directions) {
                int nx = gx + direction[0];
                int nz = gz + direction[1];
                if (nx >= 0 && nx < side && nz >= 0 && nz < side && !seen[nx][nz]) {
                    seen[nx][nz] = true;
                    queue[tail++] = (nx << 8) | nz;
                }
            }
        }
        return false;
    }
}
