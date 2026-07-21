package com.example.globe.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure detection + roofing math for the polar POWDER-ROOF CREVASSE TRAPS -- S35 form (Peetsa 2026-07-21,
 * video + verbatim spec): "You must cover traps with POWDER snow" (the S30 solid snow_block cover was "the
 * mistake: snow blocks are solid"), "a powder snow block directly vertically down at the base of the trap so
 * that the player doesn't take fall damage", "a trap over a deep crevasse into a cave... not just a drop of
 * like three blocks", "wider as well, not like one narrow strip", "increase their incidence a bit... truly
 * hazardous for the unprepared player."
 *
 * <p>Where the {@code globe:crevasse} carver cuts a narrow slot through the barrens snowfield, SOME slot
 * openings are bridged flush with a single layer of {@code powder_snow}: vanilla powder physics IS the trap
 * (walk on, sink through, fall the full shaft), the guaranteed powder cushion at every landing point negates
 * the fall damage, and the cold + the climb out are the price. Leather boots remain the full counter (they
 * walk on powder snow) and the powder-vs-snow texture difference up close remains the one learnable tell --
 * that pairing is the fairness budget (level-design decision S35-F: no extra tells, no camouflage solids).
 *
 * <p>This class holds ONLY the decision math (zero Minecraft imports -- Core Logic layer, unit-testable in a
 * plain JVM): candidacy, 2D patch flood-fill + eligibility, the 2D rim-ring bridge law, per-column depth, the
 * roof/deep-drop fraction gates, and the shaft sizing. The world-side wiring
 * ({@code world.PowderCrevasseRoofFeature}) reads WORLD_SURFACE_WG, feeds this class, and places blocks.
 *
 * <h2>Why a windowed local-max reference (unchanged from V1, orientation-independent)</h2>
 * A crevasse winds in any direction. A column is a candidate when the local snowfield maximum
 * ({@link #REFERENCE_WINDOW_RADIUS}-radius window max of WORLD_SURFACE) sits {@link #MIN_SHAFT_DEPTH_BLOCKS}+
 * above the column's own surface. A WIDE canyon's interior sees no snowfield inside its window (its window max
 * is itself canyon-low), so only slots &le; {@code 2*R} wide fully light up -- the grand-canyon-stays-open
 * guarantee. The S35 patch caps ride on top of that certification bound.
 *
 * <h2>S35 patch pipeline (level-designer spec, 2026-07-21)</h2>
 * <ol>
 *   <li>Candidate mask as above.</li>
 *   <li>{@link #floodFillPatches}: 4-connected components in fixed lx-major order (deterministic).</li>
 *   <li>Clip each component to the chunk-interior box {@code [1,14]x[1,14]} so every surviving cell has all
 *       four neighbours readable in-chunk. (The old "span touches the edge -&gt; drop it" rule silently
 *       discarded every crevasse that RUNS THROUGH the chunk -- the #1 reason traps were rare. Trimmed cells
 *       simply stay open slot; the cover ends flush at the trim line as a partial snow bridge, the system's
 *       one deliberate cover-edge-over-open-air, and an honest tell -- INTENTIONAL, sweeper.)</li>
 *   <li>{@link #patchEligible}: minor bounding-box dimension &le; {@link #PATCH_MAX_MINOR_DIMENSION}, area
 *       &le; {@link #PATCH_MAX_AREA}. 1x1 stays legal (pinhole chimney trap).</li>
 *   <li>2D rim ring = in-chunk 4-neighbours of patch cells that are NOT candidates (clipped-candidate
 *       neighbours are excluded -- the slot continues there). {@link #rimRingBridgeable}: ring height spread
 *       &le; {@link #PATCH_RIM_MAX_SPREAD}; {@link #patchRoofY}: the ring's LOWEST rim top-block Y, so the
 *       cover is flush with (never above) every rim -- floating covers impossible by construction.</li>
 *   <li>{@link #columnDeepEnoughForRoof} per cell vs the patch roofY; {@link #patchDeepEnough}: if fewer than
 *       {@link #PATCH_MIN_DEEP_FRACTION} of cells pass, the whole patch is abandoned (a mostly-shallow
 *       component is a scoop, and confetti covers are worse than none).</li>
 *   <li>One {@link #shouldRoofPatch} roll per eligible patch, one {@link #shouldDeepDrop} roll per roofed
 *       patch; draw order = component order (deterministic per seed+chunk, Art VI: no new noise).</li>
 * </ol>
 */
public final class PowderRoofTrap {

    private PowderRoofTrap() {
    }

    /**
     * Minimum drop (blocks, roof surface to landing) for a column to be trap-worthy -- used both for
     * candidacy (vs the windowed reference) and per-column placement (vs the patch's real roofY). S35: 10 ->
     * 12 ("not just a drop of like three blocks"): 12 uncushioned would be ~4.5 hearts, real stakes only the
     * cushion forgives, and it clears {@link #PATCH_RIM_MAX_SPREAD} by 4x so slope noise can never fake
     * candidacy. Not higher -- the carver cuts 20-40 deep, so 12 filters the marginal scoop band while
     * keeping the candidate population intact (depth drama comes from the deep-drop, incidence from the
     * fraction; don't spend candidates here).
     */
    public static final int MIN_SHAFT_DEPTH_BLOCKS = 12;

    /**
     * Half-extent (blocks) of the square window for each column's snowfield reference (local WORLD_SURFACE
     * maximum). 4 -> 9x9: any slot up to 8 columns across still sees open snowfield within the window, while
     * a canyon interior wider than that correctly sees no rim and stays open. UNCHANGED in S35 -- this is the
     * grand-canyon-stays-open certification the patch caps ride on.
     */
    public static final int REFERENCE_WINDOW_RADIUS = 4;

    /**
     * Maximum minor bounding-box dimension (columns) of a roofable patch. 7 = one under the window's 8-wide
     * certification bound -- the widest slot the reference math can vouch for, and a real multi-stride
     * pitfall instead of a strip (owner: "wider, not like one narrow strip").
     */
    public static final int PATCH_MAX_MINOR_DIMENSION = 7;

    /**
     * Maximum area (columns) of a roofable patch. 48 permits the signature 3-4 wide x 12-14 long snow bridge
     * over a winding crevasse while rejecting wide-mouth bowls (a 7x7+ basin is terrain, not a trap).
     */
    public static final int PATCH_MAX_AREA = 48;

    /**
     * Maximum height spread (blocks) across a patch's full rim ring for the patch to read as a slot through
     * continuous snowfield. Census-calibrated 3 -> 4 (rig, 2026-07-21): a full ring on the real rough glacier
     * commonly undulates 4, and 3 was suppressing the incidence the owner asked to RAISE; 4 is still 3x below
     * {@link #MIN_SHAFT_DEPTH_BLOCKS}, so a slope's ring (spread ~ slot depth) fails instantly. Flushness
     * stays a hard invariant via {@link #patchRoofY} (the cover sits AT or BELOW every rim, never above).
     */
    public static final int PATCH_RIM_MAX_SPREAD = 4;

    /**
     * Minimum fraction of a patch's columns that must pass the depth check for the patch to place at all.
     * Census-calibrated 0.60 -> 0.50 (rig, 2026-07-21, same incidence pass as the rim spread): a real winding
     * slot's component carries shallow shoulder cells, and the majority vote still rejects the
     * mostly-shallow scoops whose confetti covers read worse than leaving the slot open.
     */
    public static final float PATCH_MIN_DEEP_FRACTION = 0.50f;

    /**
     * Deterministic fraction of ELIGIBLE patches that get roofed (per-patch roll {@code <} this). S35: 0.40
     * -> 0.50 ("increase their incidence a bit") -- deliberately modest, because the structural changes
     * (edge-clip recovery + patches replacing strips) carry most of the increase; this is the single
     * calibration dial to pull back to 0.40 if the owner's flight reads too hot. Do not shrink the patches.
     */
    public static final float ROOF_FRACTION = 0.50f;

    /**
     * Is a column a trap candidate: does the local snowfield reference sit {@link #MIN_SHAFT_DEPTH_BLOCKS}+
     * above the column's own surface (a deep shaft cut through the snowfield)? Both args are heightmap
     * first-air reads; a water-ponded slot reads its water top and is naturally excluded.
     */
    public static boolean isTrapCandidate(int columnSurfaceY, int referenceSnowY) {
        return referenceSnowY - columnSurfaceY >= MIN_SHAFT_DEPTH_BLOCKS;
    }

    /**
     * 4-connected flood fill of the candidate mask into components, deterministic lx-major discovery order
     * (component list order = first-seen order = the feature's random-draw order). Cells are {@code {lx,lz}}.
     * Input is a {@code [16][16]} mask; null returns an empty list. Pure and allocation-only.
     */
    public static List<List<int[]>> floodFillPatches(boolean[][] candidate) {
        List<List<int[]>> patches = new ArrayList<>();
        if (candidate == null) {
            return patches;
        }
        boolean[][] seen = new boolean[16][16];
        int[] queue = new int[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (!candidate[lx][lz] || seen[lx][lz]) {
                    continue;
                }
                List<int[]> cells = new ArrayList<>();
                int head = 0;
                int tail = 0;
                queue[tail++] = (lx << 4) | lz;
                seen[lx][lz] = true;
                while (head < tail) {
                    int packed = queue[head++];
                    int cx = packed >> 4;
                    int cz = packed & 15;
                    cells.add(new int[]{cx, cz});
                    if (cx > 0 && candidate[cx - 1][cz] && !seen[cx - 1][cz]) {
                        seen[cx - 1][cz] = true;
                        queue[tail++] = ((cx - 1) << 4) | cz;
                    }
                    if (cx < 15 && candidate[cx + 1][cz] && !seen[cx + 1][cz]) {
                        seen[cx + 1][cz] = true;
                        queue[tail++] = ((cx + 1) << 4) | cz;
                    }
                    if (cz > 0 && candidate[cx][cz - 1] && !seen[cx][cz - 1]) {
                        seen[cx][cz - 1] = true;
                        queue[tail++] = (cx << 4) | (cz - 1);
                    }
                    if (cz < 15 && candidate[cx][cz + 1] && !seen[cx][cz + 1]) {
                        seen[cx][cz + 1] = true;
                        queue[tail++] = (cx << 4) | (cz + 1);
                    }
                }
                patches.add(cells);
            }
        }
        return patches;
    }

    /** Is a cell inside the chunk-interior box {@code [1,14]x[1,14]} (all four neighbours in-chunk)? */
    public static boolean isInteriorCell(int lx, int lz) {
        return lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
    }

    /**
     * Patch eligibility on the CLIPPED cell set: non-empty, area &le; {@link #PATCH_MAX_AREA}, and minor
     * bounding-box dimension &le; {@link #PATCH_MAX_MINOR_DIMENSION}. Cells are {@code {lx,lz}}.
     */
    public static boolean patchEligible(List<int[]> clippedCells) {
        if (clippedCells == null || clippedCells.isEmpty() || clippedCells.size() > PATCH_MAX_AREA) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : clippedCells) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        int minorDim = Math.min(maxX - minX + 1, maxZ - minZ + 1);
        return minorDim <= PATCH_MAX_MINOR_DIMENSION;
    }

    /** Minor bounding-box dimension of a cell set (used for shaft sizing). Empty/null returns 0. */
    public static int patchMinorDimension(List<int[]> cells) {
        if (cells == null || cells.isEmpty()) {
            return 0;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        return Math.min(maxX - minX + 1, maxZ - minZ + 1);
    }

    /** Does the patch's rim ring read as continuous snowfield: {@code max-min <= } {@link #PATCH_RIM_MAX_SPREAD}? */
    public static boolean rimRingBridgeable(int minRimFirstAir, int maxRimFirstAir) {
        return maxRimFirstAir - minRimFirstAir <= PATCH_RIM_MAX_SPREAD;
    }

    /** The patch's single flat cover Y: the LOWEST rim's top-block Y ({@code min(firstAir)-1}) -- flush with
     *  (never above) every rim, so a floating cover is impossible by construction. */
    public static int patchRoofY(int minRimFirstAir) {
        return minRimFirstAir - 1;
    }

    /**
     * May a column carry the cover at {@code roofY}: is the drop from the cover surface to this column's
     * floor at least {@link #MIN_SHAFT_DEPTH_BLOCKS}, measured against the REAL patch cover height? Failing
     * columns stay open (natural windows in the cover).
     */
    public static boolean columnDeepEnoughForRoof(int columnFirstAir, int roofY) {
        return (roofY + 1) - columnFirstAir >= MIN_SHAFT_DEPTH_BLOCKS;
    }

    /** Patch-level depth vote: at least {@link #PATCH_MIN_DEEP_FRACTION} of cells must pass the depth check. */
    public static boolean patchDeepEnough(int deepCells, int totalCells) {
        if (totalCells <= 0) {
            return false;
        }
        return deepCells >= Math.ceil(PATCH_MIN_DEEP_FRACTION * totalCells) - 1e-9;
    }

    /**
     * The fraction gate: should an eligible patch be roofed, given a uniform roll in {@code [0,1)}? True iff
     * {@code 0 <= roll01 < }{@link #ROOF_FRACTION}. Out-of-range never roofs (safe direction: leave it open).
     */
    public static boolean shouldRoofPatch(float roll01) {
        return roll01 >= 0.0f && roll01 < ROOF_FRACTION;
    }

    // --- S30/S35 DEEP DROP ("sometimes you can drop down into a deep glacial cave") ---------------------------

    /**
     * Deterministic fraction of ROOFED patches that attempt the punch-through to a pre-carved cave below.
     * S35: 0.30 -> 0.50 -- probe misses (no void in reach) bring REALIZED cave-drops to roughly 25-35% of
     * traps: common enough to be the signature moment, not the default.
     */
    public static final float DEEP_DROP_FRACTION = 0.50f;

    /**
     * How far (blocks) below the shaft column's crevasse floor the deep-drop probe looks for cave void.
     * S35: 16 -> 24. Typical trap floors sit Y ~35-65; 24 reaches Y ~11-41 -- well into glacial-caves
     * country (&lt; Y48) and the tunnel labyrinth -- for total roof-to-cushion drops of 35-60 blocks, the
     * owner's reference fall. Still bounded chunk-local work.
     */
    public static final int DEEP_DROP_PROBE_DEPTH = 24;

    /**
     * Minimum contiguous AIR run that counts as a genuine cave to connect to -- never a 1-block seam or an
     * aquifer bubble. 4 clears a player plus a landing gap (raising it only starves hits).
     */
    public static final int MIN_DEEP_VOID_AIR = 4;

    /**
     * Patch minor-dimension at/above which the deep-drop shaft widens from 2x2 to 3x3: a 2x2 throat under a
     * 6-wide cover catches almost nobody; the throat should swallow the centre mass of a wide patch.
     */
    public static final int SHAFT_WIDE_MINOR_DIM = 4;

    /** The deep-drop shaft's square side (blocks) for a patch of this minor bounding-box dimension. */
    public static int shaftSide(int patchMinorDimension) {
        return patchMinorDimension >= SHAFT_WIDE_MINOR_DIM ? 3 : 2;
    }

    /** The deep-drop patch gate: {@code 0 <= roll01 < }{@link #DEEP_DROP_FRACTION}; out-of-range never drops. */
    public static boolean shouldDeepDrop(float roll01) {
        return roll01 >= 0.0f && roll01 < DEEP_DROP_FRACTION;
    }

    /** Does a probed contiguous-air run qualify as a connectable deep void ({@code >= }{@link #MIN_DEEP_VOID_AIR})? */
    public static boolean qualifiesDeepDrop(int contiguousAirRun) {
        return contiguousAirRun >= MIN_DEEP_VOID_AIR;
    }
}
