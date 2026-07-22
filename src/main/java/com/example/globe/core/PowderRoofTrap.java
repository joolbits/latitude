package com.example.globe.core;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure detection + planning math for the polar POWDER-ROOF CREVASSE TRAPS -- S36 hidden-bridge form
 * (Peetsa 2026-07-21 video correction + independent level-design review). A valid encounter is ordinary,
 * level-looking ground spanning a real crevasse: broad enough to walk onto naturally, flush with two opposing
 * banks, completely covered, and deep/cushioned under every powder column. Tiny lids, recessed shelves,
 * one-wide strips, and covers with late-opened holes are invalid.
 *
 * <p>Where the {@code globe:crevasse} carver cuts a narrow slot through the barrens snowfield, SOME slot
 * openings are bridged flush with a single layer of {@code powder_snow}: vanilla powder physics IS the trap
 * (walk on, sink through, fall the full shaft), the guaranteed powder cushion at every landing point negates
 * the fall damage, and the cold + the climb out are the price. Leather boots remain the full counter (they
 * walk on powder snow) and the powder-vs-snow texture difference up close remains the one learnable tell --
 * that pairing is the fairness budget (level-design decision S35-F: no extra tells, no camouflage solids).
 *
 * <p>This class holds ONLY the decision math (zero Minecraft imports -- Core Logic layer, unit-testable in a
 * plain JVM): candidacy, 2D patch flood-fill + eligibility, opposing-bank alignment, per-column depth, and
 * shaft sizing. The world-side wiring
 * ({@code world.PowderCrevasseRoofFeature}) reads WORLD_SURFACE_WG, feeds this class, and places blocks.
 *
 * <h2>Why a windowed local-max reference (unchanged from V1, orientation-independent)</h2>
 * A crevasse winds in any direction. A column is a candidate when the local snowfield maximum
 * ({@link #REFERENCE_WINDOW_RADIUS}-radius window max of WORLD_SURFACE) sits
 * {@link #MIN_CANDIDATE_DEPTH_BLOCKS}+
 * above the column's own surface. A WIDE canyon's interior sees no snowfield inside its window (its window max
 * is itself canyon-low), so only slots &le; {@code 2*R} wide fully light up -- the grand-canyon-stays-open
 * guarantee. S36 separately requires an 18-block drop from the actual cover plane before placement.
 *
 * <h2>S36 hidden-bridge contract (level-designer spec, 2026-07-21)</h2>
 * <ol>
 *   <li>Candidate mask as above.</li>
 *   <li>{@link #floodFillPatches}: 4-connected components in fixed lx-major order (deterministic).</li>
 *   <li>{@link #patchEligible}: contiguous, hole-free footprint; minor dimension
 *       {@value #PATCH_MIN_MINOR_DIMENSION}..{@value #PATCH_MAX_MINOR_DIMENSION}; area
 *       {@value #PATCH_MIN_AREA}..{@value #PATCH_MAX_AREA}. The old one/two-cell lids are rejected.</li>
 *   <li>{@link #selectSnowfieldRoofY}: the cover sits on a surrounding local-snowfield reference with the
 *       strongest walkable opposing banks, never on a low shelf inside the opening.</li>
 *   <li>{@link #plannedLandingFirstAir}: every planned cover column receives an 18-block landing, retaining a
 *       naturally deep floor or deepening an existing terrace by at most eight replaceable blocks. World-side
 *       fluid, biome, cover-air, cushion, and optional cave checks are atomic.</li>
 *   <li>Every fully qualified encounter roofs. The optional cave connection is still gated by actual void,
 *       contained-shaft, fluid, landing, and traversability checks rather than rarity for rarity's sake.</li>
 * </ol>
 */
public final class PowderRoofTrap {

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PowderRoofTrap() {
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Natural-opening detector floor. Ten blocks is already a real crevasse rather than intact ground; S36's
     * atomic planner may deepen only its existing replaceable shoulder by at most eight blocks, and still
     * requires the stronger 18-block final fall. Never lower this incidence boundary further.
     */
    public static final int MIN_CANDIDATE_DEPTH_BLOCKS = 10;

    /** Minimum final drop from the accepted cover plane to every cushioned landing. */
    public static final int MIN_SHAFT_DEPTH_BLOCKS = 18;
    /** Runtime-authored falls stay inside the bounded cushion/precipitation identity scan. */
    public static final int MAX_SHAFT_DEPTH_BLOCKS = 128;
    /** Optional cave throats stop before bedrock/lava-tier drops; 96 blocks is still a major expedition fall. */
    public static final int MAX_DEEP_SHAFT_DEPTH_BLOCKS = 96;

    /** Maximum extra floor depth used to turn an existing terraced crevasse shoulder into a safe full fall. */
    public static final int MAX_FLOOR_DEEPEN_BLOCKS = 8;

    /**
     * Half-extent (blocks) of the square window for each column's snowfield reference (local WORLD_SURFACE
     * maximum). 4 -> 9x9: any slot up to 8 columns across still sees open snowfield within the window, while
     * a canyon interior wider than that correctly sees no rim and stays open. UNCHANGED in S35 -- this is the
     * grand-canyon-stays-open certification the patch caps ride on.
     */
    public static final int REFERENCE_WINDOW_RADIUS = 4;

    /** Minimum minor dimension of a hidden bridge: three blocks, never a line or pinhole. */
    public static final int PATCH_MIN_MINOR_DIMENSION = 3;

    /** Minimum natural-bank contacts required on each side of a credible walk-on crossing. */
    public static final int PATCH_MIN_APPROACH_SAMPLES = 2;

    /**
     * Maximum minor bounding-box dimension (columns) of a roofable patch. 7 = one under the window's 8-wide
     * certification bound -- the widest slot the reference math can vouch for, and a real multi-stride
     * pitfall instead of a strip (owner: "wider, not like one narrow strip").
     */
    public static final int PATCH_MAX_MINOR_DIMENSION = 7;

    /** Minimum contiguous cover area: a 3x4 crossing, six times the reported two-block S35 failure. */
    public static final int PATCH_MIN_AREA = 12;

    /** Preferred authored bridge area inside the legal 12..48 range: a substantial multi-stride crossing. */
    public static final int PATCH_TARGET_AREA = 32;

    /** Maximum one-step mismatch between a natural approach and the powder cover's walking surface. */
    public static final int MAX_APPROACH_SURFACE_OFFSET = 1;

    /**
     * Maximum area (columns) of a roofable patch. 48 permits the signature 3-4 wide x 12-14 long snow bridge
     * over a winding crevasse while rejecting wide-mouth bowls (a 7x7+ basin is terrain, not a trap).
     */
    public static final int PATCH_MAX_AREA = 48;

    /**
     * Is a column a trap candidate: does the local snowfield reference sit
     * {@link #MIN_CANDIDATE_DEPTH_BLOCKS}+
     * above the column's own surface (a deep shaft cut through the snowfield)? Both args are heightmap
     * first-air reads; a water-ponded slot reads its water top and is naturally excluded.
     */
    public static boolean isTrapCandidate(int columnSurfaceY, int referenceSnowY) {
        return referenceSnowY - columnSurfaceY >= MIN_CANDIDATE_DEPTH_BLOCKS;
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

    /** Is a cell inside the chunk-interior box {@code [1,14]x[1,14]} used for an atomic local encounter. */
    public static boolean isInteriorCell(int lx, int lz) {
        return lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
    }

    /**
     * Select one deterministic, complete hidden-bridge segment from a possibly much larger through-running
     * crevasse component. The old planner either rejected a 49+ cell component or clipped it arbitrarily;
     * this planner slides along the component's long axis and chooses the eligible interior slice nearest the
     * authored {@link #PATCH_TARGET_AREA}. It never trims across the inferred narrow axis, so an eight-wide
     * canyon remains open rather than receiving a misleading partial cap.
     */
    public static List<int[]> selectBridgeFootprint(List<int[]> component) {
        List<List<int[]>> candidates = bridgeFootprintCandidates(component);
        return candidates.isEmpty() ? List.of() : candidates.get(0);
    }

    /**
     * All distinct eligible long-axis slices, ordered nearest the authored target area first. The world-side
     * planner tries them in this order against real bank heights and safety blocks; a geometrically excellent
     * slice with rough banks must not cause the whole natural crevasse component to be discarded when its next
     * segment would make a level crossing.
     */
    public static List<List<int[]>> bridgeFootprintCandidates(List<int[]> component) {
        if (component == null || component.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<int[]> interior = new ArrayList<>();
        for (int[] c : component) {
            if (c == null || c.length < 2) {
                continue;
            }
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
            if (isInteriorCell(c[0], c[1])) {
                interior.add(new int[]{c[0], c[1]});
            }
        }
        if (interior.size() < PATCH_MIN_AREA) {
            return List.of();
        }
        boolean primaryMajorX = maxX - minX >= maxZ - minZ;
        List<List<int[]>> candidates = new ArrayList<>();
        Set<Set<Long>> seen = new HashSet<>();
        for (boolean majorX : new boolean[]{primaryMajorX, !primaryMajorX}) {
            int componentAlongExtent = majorX ? maxX - minX + 1 : maxZ - minZ + 1;
            int componentCrossExtent = majorX ? maxZ - minZ + 1 : maxX - minX + 1;
            for (int start = 1; start <= 14; start++) {
                for (int end = start; end <= 14; end++) {
                    List<int[]> slice = new ArrayList<>();
                    for (int[] c : interior) {
                        int along = majorX ? c[0] : c[1];
                        if (along >= start && along <= end) {
                            slice.add(new int[]{c[0], c[1]});
                        }
                    }
                    if (!preservesNarrowAxis(
                            slice, majorX, componentAlongExtent, componentCrossExtent)
                            || !patchEligible(slice)) {
                        continue;
                    }
                    Set<Long> key = new HashSet<>();
                    for (int[] c : slice) {
                        key.add(pack(c[0], c[1]));
                    }
                    if (seen.add(Set.copyOf(key))) {
                        candidates.add(slice);
                    }
                }
            }
        }
        candidates.sort((left, right) -> {
            int leftDelta = Math.abs(left.size() - PATCH_TARGET_AREA);
            int rightDelta = Math.abs(right.size() - PATCH_TARGET_AREA);
            int byDelta = Integer.compare(leftDelta, rightDelta);
            return byDelta != 0 ? byDelta : Integer.compare(right.size(), left.size());
        });
        return candidates;
    }

    /**
     * A sliding window may shorten a crevasse along its length, but it may never manufacture a legal minor
     * dimension by slicing across a wide basin. The selected window therefore has to retain a certified
     * cross-axis width ({@code <= 7}). A locally curving segment may choose the secondary axis when that slice
     * itself is at least as long as it is wide; only a whole component at least two blocks longer than its
     * cross-axis may use a shorter authored cap such as 6x7 to stay under the 48-cell area ceiling. Requiring
     * that margin keeps a near-square 7x8 bowl visibly open instead of disguising part of it as a fissure.
     */
    private static boolean preservesNarrowAxis(List<int[]> slice, boolean majorX,
            int componentAlongExtent, int componentCrossExtent) {
        if (slice == null || slice.isEmpty()) {
            return false;
        }
        int minAlong = Integer.MAX_VALUE;
        int maxAlong = Integer.MIN_VALUE;
        int minCross = Integer.MAX_VALUE;
        int maxCross = Integer.MIN_VALUE;
        for (int[] c : slice) {
            int along = majorX ? c[0] : c[1];
            int cross = majorX ? c[1] : c[0];
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
            minCross = Math.min(minCross, cross);
            maxCross = Math.max(maxCross, cross);
        }
        int alongExtent = maxAlong - minAlong + 1;
        int crossExtent = maxCross - minCross + 1;
        return crossExtent <= PATCH_MAX_MINOR_DIMENSION
                && (alongExtent >= crossExtent || componentAlongExtent >= componentCrossExtent + 2);
    }

    /**
     * Hidden-bridge footprint eligibility: unique and 4-connected, no enclosed holes, area within
     * [{@link #PATCH_MIN_AREA}, {@link #PATCH_MAX_AREA}], and minor dimension within
     * [{@link #PATCH_MIN_MINOR_DIMENSION}, {@link #PATCH_MAX_MINOR_DIMENSION}]. Cells are {@code {lx,lz}}.
     */
    public static boolean patchEligible(List<int[]> cells) {
        if (cells == null || cells.size() < PATCH_MIN_AREA || cells.size() > PATCH_MAX_AREA) {
            return false;
        }
        Set<Long> occupied = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            if (c == null || c.length < 2 || !occupied.add(pack(c[0], c[1]))) {
                return false;
            }
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        int minorDim = Math.min(maxX - minX + 1, maxZ - minZ + 1);
        return minorDim >= PATCH_MIN_MINOR_DIMENSION
                && minorDim <= PATCH_MAX_MINOR_DIMENSION
                && isFourConnected(occupied)
                && !hasInteriorHole(occupied, minX, maxX, minZ, maxZ);
    }

    private static boolean isFourConnected(Set<Long> occupied) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long first = occupied.iterator().next();
        seen.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = (int) (packed >> 32);
            int z = (int) packed;
            for (int[] d : CARDINALS) {
                long neighbor = pack(x + d[0], z + d[1]);
                if (occupied.contains(neighbor) && seen.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return seen.size() == occupied.size();
    }

    private static boolean hasInteriorHole(Set<Long> occupied, int minX, int maxX, int minZ, int maxZ) {
        Set<Long> outside = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            addOpenBoundary(x, minZ, occupied, outside, queue);
            addOpenBoundary(x, maxZ, occupied, outside, queue);
        }
        for (int z = minZ; z <= maxZ; z++) {
            addOpenBoundary(minX, z, occupied, outside, queue);
            addOpenBoundary(maxX, z, occupied, outside, queue);
        }
        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = (int) (packed >> 32);
            int z = (int) packed;
            for (int[] d : CARDINALS) {
                int nx = x + d[0];
                int nz = z + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                    continue;
                }
                long neighbor = pack(nx, nz);
                if (!occupied.contains(neighbor) && outside.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        int boxArea = (maxX - minX + 1) * (maxZ - minZ + 1);
        return occupied.size() + outside.size() != boxArea;
    }

    private static void addOpenBoundary(int x, int z, Set<Long> occupied, Set<Long> outside,
            ArrayDeque<Long> queue) {
        long packed = pack(x, z);
        if (!occupied.contains(packed) && outside.add(packed)) {
            queue.addLast(packed);
        }
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

    /** Median first-air height of a height sample; empty/null returns {@link Integer#MIN_VALUE}. */
    public static int medianFirstAir(List<Integer> bankFirstAir) {
        if (bankFirstAir == null || bankFirstAir.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        List<Integer> sorted = new ArrayList<>(bankFirstAir);
        sorted.sort(Integer::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    /**
     * Select a flat cover block Y from the footprint's local-snowfield reference heights. Every distinct
     * reference plane is tried against the real natural banks; the plane with the strongest opposing support
     * wins, ties preferring the footprint median. Cardinal and diagonal pairs are both considered, because a
     * winding crevasse's two banks commonly divide north+east versus south+west rather than one axis exactly.
     */
    public static int selectSnowfieldRoofY(List<Integer> referenceFirstAir, List<Integer> north,
            List<Integer> south, List<Integer> west, List<Integer> east) {
        if (referenceFirstAir == null || referenceFirstAir.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        int median = medianFirstAir(referenceFirstAir);
        Set<Integer> planes = new HashSet<>(referenceFirstAir);
        int bestRoofY = Integer.MIN_VALUE;
        int bestSupport = -1;
        int bestMedianDistance = Integer.MAX_VALUE;
        for (int coverFirstAir : planes) {
            int support = opposingBankSupport(coverFirstAir, north, south, west, east);
            if (support < PATCH_MIN_APPROACH_SAMPLES) {
                continue;
            }
            int medianDistance = Math.abs(coverFirstAir - median);
            if (support > bestSupport
                    || (support == bestSupport && medianDistance < bestMedianDistance)
                    || (support == bestSupport && medianDistance == bestMedianDistance
                            && coverFirstAir > bestRoofY + 1)) {
                bestRoofY = coverFirstAir - 1;
                bestSupport = support;
                bestMedianDistance = medianDistance;
            }
        }
        return bestRoofY;
    }

    private static int opposingBankSupport(int coverFirstAir, List<Integer> north, List<Integer> south,
            List<Integer> west, List<Integer> east) {
        int n = walkableBankSamples(north, coverFirstAir);
        int s = walkableBankSamples(south, coverFirstAir);
        int w = walkableBankSamples(west, coverFirstAir);
        int e = walkableBankSamples(east, coverFirstAir);
        return Math.max(
                Math.max(Math.min(n, s), Math.min(w, e)),
                Math.max(Math.min(n + w, s + e), Math.min(n + e, s + w)));
    }

    private static int walkableBankSamples(List<Integer> bank, int coverFirstAir) {
        if (bank == null) {
            return 0;
        }
        int count = 0;
        for (int firstAir : bank) {
            if (Math.abs(firstAir - coverFirstAir) <= MAX_APPROACH_SURFACE_OFFSET) {
                count++;
            }
        }
        return count;
    }

    /**
     * May a column carry the cover at {@code roofY}: is the drop from the cover surface to this column's
     * floor at least {@link #MIN_SHAFT_DEPTH_BLOCKS}, measured against the REAL patch cover height? One
     * failing column rejects the complete bridge; accepted covers never contain post-plan windows.
     */
    public static boolean columnDeepEnoughForRoof(int columnFirstAir, int roofY) {
        return (roofY + 1) - columnFirstAir >= MIN_SHAFT_DEPTH_BLOCKS;
    }

    /** Complete authored-depth contract shared by generation, scanning, and runtime cushion identity. */
    public static boolean columnDepthEligible(int columnFirstAir, int roofY) {
        int depth = (roofY + 1) - columnFirstAir;
        return depth >= MIN_SHAFT_DEPTH_BLOCKS && depth <= MAX_SHAFT_DEPTH_BLOCKS;
    }

    /**
     * Planned landing first-air for a covered column. Existing deep floors are retained; a shallower floor may
     * move down only enough to meet the 18-block contract and never more than eight blocks. An impossible
     * deepening returns {@link Integer#MIN_VALUE} so the complete bridge remains visibly open.
     */
    public static int plannedLandingFirstAir(int naturalFirstAir, int roofY) {
        int deepestAllowedLanding = roofY + 1 - MIN_SHAFT_DEPTH_BLOCKS;
        int planned = Math.min(naturalFirstAir, deepestAllowedLanding);
        return naturalFirstAir - planned <= MAX_FLOOR_DEEPEN_BLOCKS
                        && columnDepthEligible(planned, roofY)
                ? planned
                : Integer.MIN_VALUE;
    }

    /** True only when every cell in the requested square is part of the powder-cover footprint. */
    public static boolean containsSquare(List<int[]> cells, int minX, int minZ, int side) {
        if (cells == null || side <= 0) {
            return false;
        }
        Set<Long> occupied = new HashSet<>();
        for (int[] c : cells) {
            if (c != null && c.length >= 2) {
                occupied.add(pack(c[0], c[1]));
            }
        }
        for (int x = minX; x < minX + side; x++) {
            for (int z = minZ; z < minZ + side; z++) {
                if (!occupied.contains(pack(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- S30/S35 DEEP DROP ("sometimes you can drop down into a deep glacial cave") ---------------------------

    /**
     * How far (blocks) below the shaft column's crevasse floor the deep-drop probe looks for cave void. S36
     * reaches 48 blocks because the verified surface-aligned roofs sit higher than S35's recessed patches;
     * this reaches the glacial-caves country below Y48 while remaining bounded chunk-local work.
     */
    public static final int DEEP_DROP_PROBE_DEPTH = 48;

    /**
     * Minimum contiguous AIR run that counts as a genuine cave to connect to -- never a 1-block seam or an
     * aquifer bubble. 4 clears a player plus a landing gap (raising it only starves hits).
     */
    public static final int MIN_DEEP_VOID_AIR = 4;

    /**
     * Patch minor-dimension at/above which the deep-drop shaft widens from 2x2 to 3x3: a 2x2 throat under a
     * 6-wide cover catches almost nobody; the throat should swallow the centre mass of a wide patch.
     */
    public static final int SHAFT_WIDE_MINOR_DIM = 3;

    /** The deep-drop shaft's square side (blocks) for a patch of this minor bounding-box dimension. */
    public static int shaftSide(int patchMinorDimension) {
        return patchMinorDimension >= SHAFT_WIDE_MINOR_DIM ? 3 : 2;
    }

    /** Does a probed contiguous-air run qualify as a connectable deep void ({@code >= }{@link #MIN_DEEP_VOID_AIR})? */
    public static boolean qualifiesDeepDrop(int contiguousAirRun) {
        return contiguousAirRun >= MIN_DEEP_VOID_AIR;
    }

    /** Fair-depth gate for the optional punched cave throat; ordinary natural crevasse columns have no cap. */
    public static boolean deepDropDepthEligible(int finalDepth) {
        return finalDepth >= MIN_SHAFT_DEPTH_BLOCKS && finalDepth <= MAX_DEEP_SHAFT_DEPTH_BLOCKS;
    }
}
