package com.example.globe.core;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure detection + planning math for the polar POWDER-ROOF CREVASSE TRAPS -- S36 hidden-bridge form
 * (Peetsa 2026-07-21 video correction + independent level-design review). A valid encounter is ordinary,
 * level-looking ground spanning a real crevasse: broad enough to walk onto naturally, flush with two opposing
 * banks, completely covered, and deep/cushioned under every powder column. Tiny lids, recessed shelves,
 * one-wide strips, and covers with late-opened holes are invalid.
 *
 * <p>Where the {@code globe:crevasse} carver cuts a narrow slot through the barrens snowfield, one complete
 * 12..16-station natural section may become a continuous level-looking crossing: three or four stations of
 * solid snow, a preferred eight-station (or exact six-station incidence fallback) full-width powder core, then
 * three or four more solid stations. Vanilla powder physics is the only trigger; every powder column retains
 * its guaranteed cushion and 18+ block drop, while solid shoulders are camouflage roof only and never enter
 * fall-path, landing, cushion, or shaft logic.
 *
 * <p>This class holds ONLY the decision math (zero Minecraft imports -- Core Logic layer, unit-testable in a
 * plain JVM): candidacy, 2D patch flood-fill + eligibility, opposing-bank alignment, per-column depth, and
 * shaft sizing. The current world-side adapter ({@code world.PowderCrevasseRoofFeature}) reads the maintained
 * feature-stage WORLD_SURFACE heightmap; WORLD_SURFACE_WG freezes before top-layer feature writes.
 *
 * <h2>Why a windowed local-max reference (unchanged from V1, orientation-independent)</h2>
 * A crevasse winds in any direction. A powder-core column is a candidate when the local snowfield maximum
 * ({@link #REFERENCE_WINDOW_RADIUS}-radius window max of WORLD_SURFACE) sits
 * {@link #MIN_CANDIDATE_DEPTH_BLOCKS}+
 * above the column's own surface. A WIDE canyon's interior sees no snowfield inside its window (its window max
 * is itself canyon-low), so only slots &le; {@code 2*R} wide fully light up -- the grand-canyon-stays-open
 * guarantee. The complete natural opening may taper to {@link #MIN_COVER_DEPTH_BLOCKS} under solid shoulders;
 * those cells never become fall columns. S36 separately requires an 18-block drop from the actual cover plane
 * under every powder cell before placement.
 *
 * <h2>S36 hidden-bridge contract (level-designer spec, 2026-07-21)</h2>
 * <ol>
 *   <li>A natural-opening mask uses the two-block cover floor; the middle powder core independently keeps the
 *       ten-block candidacy floor.</li>
 *   <li>{@link #floodFillPatches}: 4-connected DEEP components in fixed lx-major order (deterministic). The
 *       shallow two-block opening mask is never globally flooded into terrain basins.</li>
 *   <li>{@link #concealedSegmentCandidates}: a complete 8-station deep powder core ranks first; a complete
 *       6-station/18-column core is the bounded incidence fallback. Three solid stations per end are mandatory
 *       and a fourth is optional, producing 12..16 total stations without invented or cropped cells.</li>
 *   <li>Each selected station keeps one complete maximal run touching the preceding station. Shoulders prefer
 *       continuation through the deep component, then fall back to one unambiguous shallow cover run. Multiple
 *       touching shallow runs or a run wider than seven cells reject as an ambiguous basin. Both shoulders must
 *       inherit a natural edge shift; a clean rectangular frame is rejected.</li>
 *   <li>{@link #selectSnowfieldRoofY}: the cover sits on a surrounding local-snowfield reference with the
 *       strongest walkable opposing banks, never on a low shelf inside the opening.</li>
 *   <li>{@link #plannedLandingFirstAir}: every powder column receives an 18-block landing, retaining a
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

    /** A solid shoulder may follow a shallower natural taper, but must still span air below the roof plane. */
    public static final int MIN_COVER_DEPTH_BLOCKS = 2;

    /** Minimum final drop from the accepted cover plane to every cushioned landing. */
    public static final int MIN_SHAFT_DEPTH_BLOCKS = 18;
    /** Runtime-authored falls stay inside the bounded cushion/precipitation identity scan. */
    public static final int MAX_SHAFT_DEPTH_BLOCKS = 128;
    /** Optional cave throats stop before bedrock/lava-tier drops; 96 blocks is still a major expedition fall. */
    public static final int MAX_DEEP_SHAFT_DEPTH_BLOCKS = 96;

    /** Maximum extra depth used to turn an existing terraced crevasse floor into a safe full fall. */
    public static final int MAX_FLOOR_DEEPEN_BLOCKS = 8;

    /**
     * Half-extent (blocks) of the square window for each column's snowfield reference (local WORLD_SURFACE
     * maximum). 4 -> 9x9: any slot up to 8 columns across still sees open snowfield within the window, while
     * a canyon interior wider than that correctly sees no rim and stays open. UNCHANGED in S35 -- this is the
     * grand-canyon-stays-open certification the patch caps ride on.
     */
    public static final int REFERENCE_WINDOW_RADIUS = 4;

    /** Minimum full natural station width: three blocks. */
    public static final int PATCH_MIN_MINOR_DIMENSION = 3;

    /** Minimum natural-bank contacts required on each side of a credible walk-on crossing. */
    public static final int PATCH_MIN_APPROACH_SAMPLES = 2;

    /** Widest station the local reference window can still certify as a narrow crevasse rather than a basin. */
    public static final int PATCH_MAX_MINOR_DIMENSION = 7;

    /** Variable concealed crossing: 3..4 solid / 8 preferred or 6 fallback powder / 3..4 solid. */
    public static final int MIN_CONCEALED_STATIONS = 12;
    public static final int MAX_CONCEALED_STATIONS = 16;
    public static final int PREFERRED_POWDER_STATIONS = 8;
    public static final int FALLBACK_POWDER_STATIONS = 6;
    public static final int MIN_SHOULDER_STATIONS_PER_END = 3;
    public static final int MAX_SHOULDER_STATIONS_PER_END = 4;
    /** Exact first-bank contour added laterally around every selected spine station. */
    public static final int MIN_LATERAL_APRON_CELLS_PER_SIDE = 0;
    public static final int MAX_LATERAL_APRON_CELLS_PER_SIDE = 9;
    /** With a truthful bank cell still inside each owner edge, an augmented station can occupy at most 14. */
    public static final int MAX_BANK_TO_BANK_COVER_WIDTH = 14;

    /** Preferred eight-station area band; values above it remain safe but rank behind the band. */
    public static final int PREFERRED_POWDER_AREA_MIN = 24;
    public static final int PREFERRED_POWDER_AREA_MAX = 36;
    /** Exact six-station fallback floor (six complete three-wide stations). */
    public static final int FALLBACK_MIN_POWDER_AREA = 18;
    /** Compatibility name retained for reports which mean the preferred area's lower bound. */
    public static final int POWDER_MIN_AREA = PREFERRED_POWDER_AREA_MIN;

    /** Maximum one-step mismatch between a natural approach and the powder cover's walking surface. */
    public static final int MAX_APPROACH_SURFACE_OFFSET = 1;

    /** Required terrain-bank coverage on each long side: four of every five longitudinal stations. */
    public static final int MIN_LONG_SIDE_BANK_COVERAGE_PERCENT = 80;

    /**
     * Is a column a trap candidate: does the local snowfield reference sit
     * {@link #MIN_CANDIDATE_DEPTH_BLOCKS}+
     * above the column's own surface (a deep shaft cut through the snowfield)? Both args are heightmap
     * first-air reads; a water-ponded slot reads its water top and is naturally excluded.
     */
    public static boolean isTrapCandidate(int columnSurfaceY, int referenceSnowY) {
        return referenceSnowY - columnSurfaceY >= MIN_CANDIDATE_DEPTH_BLOCKS;
    }

    /** Is this column part of the real crevasse opening that a solid camouflage shoulder may follow? */
    public static boolean isCoverCandidate(int columnSurfaceY, int referenceSnowY) {
        return referenceSnowY - columnSurfaceY >= MIN_COVER_DEPTH_BLOCKS;
    }

    /**
     * Pure preflight seam for the world-side write batch. Targets are {@code {worldX,y,worldZ}} and must be
     * unique, non-empty, and inside the one 16x16 owner chunk whose aligned minimum is supplied.
     */
    public static boolean ownerChunkWritePlanEligible(
            int ownerMinX, int ownerMinZ, List<int[]> worldTargets) {
        if ((ownerMinX & 15) != 0 || (ownerMinZ & 15) != 0
                || worldTargets == null || worldTargets.isEmpty()) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (int[] target : worldTargets) {
            if (target == null || target.length < 3) {
                return false;
            }
            long localX = (long) target[0] - ownerMinX;
            long localZ = (long) target[2] - ownerMinZ;
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16
                    || !unique.add(target[0] + "," + target[1] + "," + target[2])) {
                return false;
            }
        }
        return true;
    }

    /** A write batch is encounter-successful only when every non-empty planned target was accounted for. */
    public static boolean completeWriteBatchSucceeded(int plannedWrites, int completedWrites) {
        return plannedWrites > 0 && completedWrites == plannedWrites;
    }

    /** Pure phases for the checked world-side write plan. */
    public enum TrapWritePhase {
        CUSHION,
        CLEAR_PATH,
        SOLID_ROOF,
        POWDER_ROOF
    }

    /** One immutable coordinate/phase in the checked world-side write plan. */
    public record TrapWrite(int x, int y, int z, TrapWritePhase phase) {
    }

    /**
     * Build the coordinate/phase plan used by worldgen. The optional throat must be wholly inside powder; solid
     * shoulders can therefore appear only as {@link TrapWritePhase#SOLID_ROOF}, never in cushion, clear-path, or
     * shaft work. Returns {@code null} for a conflicting, out-of-grid, or shoulder-crossing plan.
     */
    public static List<TrapWrite> concealedWritePlan(int baseX, int baseZ,
            List<int[]> powder, List<int[]> shoulders, int[][] landingFirstAir, int roofY,
            boolean shaftQualified, int shaftMinX, int shaftMinZ, int shaftSide, int voidBottom) {
        List<int[]> canonicalPowder = canonicalCells(powder);
        List<int[]> canonicalShoulders = canonicalCells(shoulders);
        if (canonicalPowder.isEmpty() || canonicalShoulders.isEmpty()
                || !allInsideLocalChunk(canonicalPowder) || !allInsideLocalChunk(canonicalShoulders)) {
            return null;
        }
        Set<Long> powderSet = packedCells(canonicalPowder);
        for (int[] shoulder : canonicalShoulders) {
            if (powderSet.contains(pack(shoulder[0], shoulder[1]))) {
                return null;
            }
        }
        if (shaftQualified) {
            if (shaftSide <= 0) {
                return null;
            }
            for (int lx = shaftMinX; lx < shaftMinX + shaftSide; lx++) {
                for (int lz = shaftMinZ; lz < shaftMinZ + shaftSide; lz++) {
                    if (!powderSet.contains(pack(lx, lz))) {
                        return null;
                    }
                }
            }
        }

        Map<String, TrapWrite> writes = new LinkedHashMap<>();
        for (int[] cell : canonicalPowder) {
            if (!hasHeightCell(landingFirstAir, cell[0], cell[1])) {
                return null;
            }
            boolean inShaft = inShaft(cell, shaftQualified, shaftMinX, shaftMinZ, shaftSide);
            if (!inShaft && !addTrapWrite(writes, baseX + cell[0],
                    landingFirstAir[cell[0]][cell[1]], baseZ + cell[1], TrapWritePhase.CUSHION)) {
                return null;
            }
        }
        if (shaftQualified) {
            for (int lx = shaftMinX; lx < shaftMinX + shaftSide; lx++) {
                for (int lz = shaftMinZ; lz < shaftMinZ + shaftSide; lz++) {
                    if (!addTrapWrite(writes, baseX + lx, voidBottom, baseZ + lz,
                            TrapWritePhase.CUSHION)) {
                        return null;
                    }
                }
            }
        }
        for (int[] cell : canonicalPowder) {
            if (inShaft(cell, shaftQualified, shaftMinX, shaftMinZ, shaftSide)) {
                continue;
            }
            int landing = landingFirstAir[cell[0]][cell[1]];
            for (int y = roofY - 1; y > landing; y--) {
                if (!addTrapWrite(writes, baseX + cell[0], y, baseZ + cell[1],
                        TrapWritePhase.CLEAR_PATH)) {
                    return null;
                }
            }
        }
        if (shaftQualified) {
            for (int lx = shaftMinX; lx < shaftMinX + shaftSide; lx++) {
                for (int lz = shaftMinZ; lz < shaftMinZ + shaftSide; lz++) {
                    for (int y = roofY - 1; y > voidBottom; y--) {
                        if (!addTrapWrite(writes, baseX + lx, y, baseZ + lz,
                                TrapWritePhase.CLEAR_PATH)) {
                            return null;
                        }
                    }
                }
            }
        }
        for (int[] cell : canonicalShoulders) {
            if (!addTrapWrite(writes, baseX + cell[0], roofY, baseZ + cell[1],
                    TrapWritePhase.SOLID_ROOF)) {
                return null;
            }
        }
        for (int[] cell : canonicalPowder) {
            if (!addTrapWrite(writes, baseX + cell[0], roofY, baseZ + cell[1],
                    TrapWritePhase.POWDER_ROOF)) {
                return null;
            }
        }
        return List.copyOf(writes.values());
    }

    private static boolean inShaft(
            int[] cell, boolean qualified, int minX, int minZ, int side) {
        return qualified && cell[0] >= minX && cell[0] < minX + side
                && cell[1] >= minZ && cell[1] < minZ + side;
    }

    private static boolean addTrapWrite(Map<String, TrapWrite> writes,
            int x, int y, int z, TrapWritePhase phase) {
        String key = x + "," + y + "," + z;
        TrapWrite existing = writes.get(key);
        if (existing != null) {
            return existing.phase() == phase;
        }
        writes.put(key, new TrapWrite(x, y, z, phase));
        return true;
    }

    /**
     * 4-connected flood fill of the candidate mask into components, deterministic lx-major discovery order
     * (component list order = first-seen order = the feature's random-draw order). Cells are {@code {lx,lz}}.
     * Input may be any non-empty rectangular mask; null or ragged masks return an empty list. Pure and
     * allocation-only.
     */
    public static List<List<int[]>> floodFillPatches(boolean[][] candidate) {
        List<List<int[]>> patches = new ArrayList<>();
        GridSize grid = gridSize(candidate);
        if (grid == null) {
            return patches;
        }
        boolean[][] seen = new boolean[grid.sizeX()][grid.sizeZ()];
        int[] queue = new int[grid.sizeX() * grid.sizeZ()];
        for (int lx = 0; lx < grid.sizeX(); lx++) {
            for (int lz = 0; lz < grid.sizeZ(); lz++) {
                if (!candidate[lx][lz] || seen[lx][lz]) {
                    continue;
                }
                List<int[]> cells = new ArrayList<>();
                int head = 0;
                int tail = 0;
                queue[tail++] = lx * grid.sizeZ() + lz;
                seen[lx][lz] = true;
                while (head < tail) {
                    int packed = queue[head++];
                    int cx = packed / grid.sizeZ();
                    int cz = packed % grid.sizeZ();
                    cells.add(new int[]{cx, cz});
                    if (cx > 0 && candidate[cx - 1][cz] && !seen[cx - 1][cz]) {
                        seen[cx - 1][cz] = true;
                        queue[tail++] = (cx - 1) * grid.sizeZ() + cz;
                    }
                    if (cx + 1 < grid.sizeX() && candidate[cx + 1][cz] && !seen[cx + 1][cz]) {
                        seen[cx + 1][cz] = true;
                        queue[tail++] = (cx + 1) * grid.sizeZ() + cz;
                    }
                    if (cz > 0 && candidate[cx][cz - 1] && !seen[cx][cz - 1]) {
                        seen[cx][cz - 1] = true;
                        queue[tail++] = cx * grid.sizeZ() + cz - 1;
                    }
                    if (cz + 1 < grid.sizeZ() && candidate[cx][cz + 1] && !seen[cx][cz + 1]) {
                        seen[cx][cz + 1] = true;
                        queue[tail++] = cx * grid.sizeZ() + cz + 1;
                    }
                }
                patches.add(cells);
            }
        }
        return patches;
    }

    /** Immutable, disjoint output plan. Only {@link #powder} may enter fall/cushion/shaft logic. */
    public record ConcealedSegment(List<int[]> powder, List<int[]> shoulders, List<int[]> cover,
            boolean majorX, int firstStation, int powderStations,
            int leftShoulderStations, int rightShoulderStations) {
        public ConcealedSegment {
            powder = immutableCells(powder);
            shoulders = immutableCells(shoulders);
            cover = immutableCells(cover);
        }

        @Override
        public List<int[]> powder() {
            return immutableCells(powder);
        }

        @Override
        public List<int[]> shoulders() {
            return immutableCells(shoulders);
        }

        @Override
        public List<int[]> cover() {
            return immutableCells(cover);
        }
    }

    /**
     * Diagnostic result used by worldgen's incidence recorder; candidate order is already authoritative.
     * {@code coreShapeRejects} remains the inclusive legacy count. The four post-spine fields partition its
     * first-bank/augmented-shape subset, so callers must not add them back into the core count.
     */
    public record ConcealedSearchResult(List<ConcealedSegment> candidates,
            int coreShapeRejects, int shoulderMissingRejects, int shoulderAmbiguousRejects,
            int powderShareRejects, int staggerRejects,
            int traceOver9, int traceOwnerEdgeOrUnknown,
            int augmentedStagger, int augmentedOtherShape,
            int preferredCandidateCount, int fallbackCandidateCount) {
        public ConcealedSearchResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /** Size policy for the read-only cross-chunk planner; all other hidden-bridge laws remain unchanged. */
    public record CrossPlanOptions(int maxIndividualApron, int maxBankToBankSpan) {
        public CrossPlanOptions {
            if (maxIndividualApron < 0 || maxBankToBankSpan < PATCH_MIN_MINOR_DIMENSION) {
                throw new IllegalArgumentException("cross-plan caps must be non-negative and physically usable");
            }
        }
    }

    /** Cross-plan candidates plus otherwise-valid contours excluded only by the requested visual size caps. */
    public record CrossPlanSearchResult(List<ConcealedSegment> candidates,
            List<ConcealedSegment> sizeRejectedCandidates) {
        public CrossPlanSearchResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            sizeRejectedCandidates = sizeRejectedCandidates == null
                    ? List.of() : List.copyOf(sizeRejectedCandidates);
        }
    }

    /** Stable world-coordinate identity used to deduplicate one plan seen through overlapping scan windows. */
    public record CrossPlanIdentity(String canonicalKey, int midpointWorldX, int midpointWorldZ,
            boolean crossChunk, int powderColumns, int solidColumns) {
    }

    /** One compact, canonical-owner diagnostic row slice for a requested total-span cap. */
    public record CrossPlanCapSummary(int totalSpanCap, int potentialPlans, int powderColumns,
            int solidColumns, int sizeRejects, int crossChunkPlans,
            int selectedPlans, int selectedPowderColumns, int selectedSolidColumns,
            int selectedTouchedChunks) {
    }

    private record SearchOutcome(
            ConcealedSearchResult result, List<ConcealedSegment> sizeRejectedCandidates) {
    }

    private record RankedSegment(ConcealedSegment segment, boolean preferredCore,
            boolean preferredArea, int shoulderStations, int minimumDepth, long totalDepth) {
    }

    private record Station(int low, int high, int count) {
        int width() {
            return high - low + 1;
        }
    }

    private enum ApronTraceFailure {
        NONE,
        OVER_NINE,
        OWNER_EDGE_OR_UNKNOWN
    }

    private record ApronTrace(List<int[]> cells, ApronTraceFailure failure) {
        ApronTrace {
            cells = immutableCells(cells);
        }
    }

    private record FirstBankExtension(ConcealedSegment segment, ApronTraceFailure failure,
            int maximumIndividualApron, int bankToBankSpan) {
    }

    private enum ShoulderFailure {
        NONE,
        MISSING,
        AMBIGUOUS
    }

    private record ShoulderExtension(List<int[]> cells, int stations) {
        ShoulderExtension {
            cells = immutableCells(cells);
        }
    }

    private record ShoulderOptions(List<ShoulderExtension> extensions, ShoulderFailure failure) {
        ShoulderOptions {
            extensions = extensions == null ? List.of() : List.copyOf(extensions);
        }
    }

    /**
     * Enumerate deep-first chunk-local concealed crossings. The supplied component contains only ten-block-deep
     * cells; the separate two-block cover mask is consulted one station at a time and is never globally flooded.
     * Every selected station retains its complete natural run. An optional fourth shoulder is emitted alongside
     * the mandatory three-station form so scanner reconstruction can resolve touching, non-overlapping roofs;
     * ranking puts the fuller natural continuation first for generation.
     */
    public static List<ConcealedSegment> concealedSegmentCandidates(
            List<int[]> deepComponent, boolean[][] coverMask, int[][] depthByColumn) {
        return concealedSegmentSearch(deepComponent, coverMask, depthByColumn).candidates();
    }

    /**
     * Reconstruct one physically observed first-bank contour from its exact powder component, same-plane solid
     * mask, all-plane floating occupancy, and known-cell mask. The scanner cannot recover pre-write depth under
     * solid snow, so it enumerates complete maximal station runs, then requires an ordinary known in-owner bank
     * immediately beyond both long sides and rejects any attached floating roof omitted by the candidate.
     */
    public static List<ConcealedSegment> physicalContouredSegmentCandidates(
            List<int[]> powder, boolean[][] solidMask,
            boolean[][] floatingMask, boolean[][] knownMask) {
        List<int[]> canonicalPowder = canonicalCells(powder);
        if (canonicalPowder.isEmpty() || canonicalPowder.size() != (powder == null ? 0 : powder.size())
                || !allInsideLocalChunk(canonicalPowder) || solidMask == null
                || floatingMask == null || knownMask == null) {
            return List.of();
        }
        boolean[][] powderMask = cellMask(canonicalPowder);
        boolean[][] coverMask = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (hasMaskCell(solidMask, x, z) && solidMask[x][z]) {
                    if (powderMask[x][z]) {
                        return List.of();
                    }
                    coverMask[x][z] = true;
                }
            }
        }
        for (int[] cell : canonicalPowder) {
            coverMask[cell[0]][cell[1]] = true;
        }

        List<ConcealedSegment> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (boolean majorX : new boolean[]{true, false}) {
            if (!powderCoreAxisEligible(canonicalPowder, majorX)) {
                continue;
            }
            int powderStart = minimumStation(canonicalPowder, majorX);
            int powderEnd = maximumStation(canonicalPowder, majorX);
            int powderStations = powderEnd - powderStart + 1;
            for (int left = MIN_SHOULDER_STATIONS_PER_END;
                    left <= MAX_SHOULDER_STATIONS_PER_END; left++) {
                for (int right = MIN_SHOULDER_STATIONS_PER_END;
                        right <= MAX_SHOULDER_STATIONS_PER_END; right++) {
                    int first = powderStart - left;
                    int total = left + powderStations + right;
                    if (first < 0 || first + total > 16) {
                        continue;
                    }
                    List<List<Station>> paths = new ArrayList<>();
                    collectPhysicalCoverPaths(coverMask, canonicalPowder, majorX,
                            first, total, 0, null, new ArrayList<>(), paths);
                    for (List<Station> path : paths) {
                        List<int[]> cover = new ArrayList<>();
                        for (int offset = 0; offset < path.size(); offset++) {
                            addStationCells(cover, majorX, first + offset, path.get(offset));
                        }
                        cover.sort(PowderRoofTrap::compareCell);
                        Set<Long> powderSet = packedCells(canonicalPowder);
                        List<int[]> shoulders = cover.stream()
                                .filter(cell -> !powderSet.contains(pack(cell[0], cell[1])))
                                .map(cell -> new int[]{cell[0], cell[1]}).toList();
                        if (!concealedSegmentAxisEligible(
                                canonicalPowder, shoulders, cover, majorX)
                                || !physicalContourBanksEligible(
                                        cover, majorX, floatingMask, knownMask)) {
                            continue;
                        }
                        ConcealedSegment segment = new ConcealedSegment(
                                canonicalPowder, shoulders, cover, majorX, first,
                                powderStations, left, right);
                        String key = cellKey(segment.powder()) + "|" + cellKey(segment.shoulders());
                        if (seen.add(key)) {
                            candidates.add(segment);
                        }
                    }
                }
            }
        }
        return rankConcealedSegments(candidates, null);
    }

    private static void collectPhysicalCoverPaths(boolean[][] coverMask, List<int[]> powder,
            boolean majorX, int first, int total, int offset, Station previous,
            List<Station> path, List<List<Station>> output) {
        if (offset == total) {
            output.add(List.copyOf(path));
            return;
        }
        int along = first + offset;
        Station powderRun = station(powder, majorX, along);
        boolean powderStation = powderRun.count() > 0;
        for (Station run : stationRuns(coverMask, majorX, along)) {
            if (!fullCoverStationEligible(run)
                    || previous != null && !stationsTouch(previous, run)
                    || powderStation && (run.low() > powderRun.low() || run.high() < powderRun.high())) {
                continue;
            }
            path.add(run);
            collectPhysicalCoverPaths(coverMask, powder, majorX,
                    first, total, offset + 1, run, path, output);
            path.remove(path.size() - 1);
        }
    }

    private static boolean physicalContourBanksEligible(List<int[]> cover, boolean majorX,
            boolean[][] floatingMask, boolean[][] knownMask) {
        Set<Long> coverSet = packedCells(cover);
        for (int[] cell : cover) {
            if (!hasMaskCell(knownMask, cell[0], cell[1]) || !knownMask[cell[0]][cell[1]]
                    || !hasMaskCell(floatingMask, cell[0], cell[1])
                    || !floatingMask[cell[0]][cell[1]]) {
                return false;
            }
            for (int[] direction : CARDINALS) {
                int x = cell[0] + direction[0];
                int z = cell[1] + direction[1];
                if (x >= 0 && x < 16 && z >= 0 && z < 16
                        && hasMaskCell(floatingMask, x, z) && floatingMask[x][z]
                        && !coverSet.contains(pack(x, z))) {
                    return false; // an attached roof is physical occupancy, never cropped away.
                }
            }
        }
        int first = minimumStation(cover, majorX);
        int last = maximumStation(cover, majorX);
        for (int along = first; along <= last; along++) {
            Station run = station(cover, majorX, along);
            if (!ordinaryKnownBank(floatingMask, knownMask,
                    majorX, along, run.low() - 1)
                    || !ordinaryKnownBank(floatingMask, knownMask,
                            majorX, along, run.high() + 1)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ordinaryKnownBank(boolean[][] floatingMask, boolean[][] knownMask,
            boolean majorX, int along, int cross) {
        int x = majorX ? along : cross;
        int z = majorX ? cross : along;
        return x >= 0 && x < 16 && z >= 0 && z < 16
                && hasMaskCell(knownMask, x, z) && knownMask[x][z]
                && hasMaskCell(floatingMask, x, z) && !floatingMask[x][z];
    }

    /** Same search as {@link #concealedSegmentCandidates}, with footprint rejection diagnostics. */
    public static ConcealedSearchResult concealedSegmentSearch(
            List<int[]> deepComponent, boolean[][] coverMask, int[][] depthByColumn) {
        return concealedSegmentSearchInternal(
                deepComponent, coverMask, depthByColumn, null).result();
    }

    /**
     * Read-only rectangular-grid search which measures the complete first-bank contour before applying visual
     * size caps. It changes no world state and does not relax the spine, powder, shoulder, depth, or stagger law.
     */
    public static CrossPlanSearchResult concealedSegmentSearch(
            List<int[]> deepComponent, boolean[][] coverMask, int[][] depthByColumn,
            CrossPlanOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("cross-plan options are required");
        }
        SearchOutcome outcome = concealedSegmentSearchInternal(
                deepComponent, coverMask, depthByColumn, options);
        return new CrossPlanSearchResult(
                outcome.result().candidates(), outcome.sizeRejectedCandidates());
    }

    /** Convert a local-grid partition into its deterministic world-coordinate identity. */
    public static CrossPlanIdentity crossPlanIdentity(
            int gridMinX, int gridMinZ, ConcealedSegment segment) {
        if (segment == null || segment.powder().isEmpty() || segment.shoulders().isEmpty()) {
            throw new IllegalArgumentException("a complete powder/shoulder partition is required");
        }
        StringBuilder key = new StringBuilder("P:");
        appendWorldCells(key, gridMinX, gridMinZ, segment.powder());
        key.append("|S:");
        appendWorldCells(key, gridMinX, gridMinZ, segment.shoulders());

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int firstChunkX = Integer.MIN_VALUE;
        int firstChunkZ = Integer.MIN_VALUE;
        boolean crossChunk = false;
        for (int[] cell : segment.cover()) {
            int worldX = gridMinX + cell[0];
            int worldZ = gridMinZ + cell[1];
            minX = Math.min(minX, worldX);
            maxX = Math.max(maxX, worldX);
            minZ = Math.min(minZ, worldZ);
            maxZ = Math.max(maxZ, worldZ);
            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;
            if (firstChunkX == Integer.MIN_VALUE) {
                firstChunkX = chunkX;
                firstChunkZ = chunkZ;
            } else if (chunkX != firstChunkX || chunkZ != firstChunkZ) {
                crossChunk = true;
            }
        }
        int midpointX = (int) Math.floorDiv((long) minX + maxX, 2L);
        int midpointZ = (int) Math.floorDiv((long) minZ + maxZ, 2L);
        return new CrossPlanIdentity(key.toString(), midpointX, midpointZ, crossChunk,
                segment.powder().size(), segment.shoulders().size());
    }

    /**
     * Pure probe aggregation. Only plans (and otherwise-valid size rejects) whose footprint midpoint belongs to
     * the supplied decorating chunk are counted, so overlapping 48x48 observations have one reporting owner.
     */
    public static List<CrossPlanCapSummary> crossPlanSummaries(
            boolean[][] deepMask, boolean[][] coverMask, int[][] depthByColumn,
            int gridMinX, int gridMinZ, int ownerChunkX, int ownerChunkZ,
            int maximumIndividualApron, int... totalSpanCaps) {
        GridSize deepGrid = gridSize(deepMask);
        GridSize grid = matchingGridSize(coverMask, depthByColumn);
        if (deepGrid == null || grid == null || !deepGrid.equals(grid)
                || totalSpanCaps == null || totalSpanCaps.length == 0) {
            return List.of();
        }
        List<List<int[]>> components = floodFillPatches(deepMask);
        List<CrossPlanCapSummary> summaries = new ArrayList<>();
        for (int totalSpanCap : totalSpanCaps) {
            CrossPlanOptions options = new CrossPlanOptions(maximumIndividualApron, totalSpanCap);
            Map<String, CrossPlanIdentity> accepted = new LinkedHashMap<>();
            Map<String, CrossPlanIdentity> sizeRejected = new LinkedHashMap<>();
            Map<String, CrossPlanIdentity> selected = new LinkedHashMap<>();
            Set<Long> selectedTouchedChunks = new HashSet<>();
            for (List<int[]> component : components) {
                CrossPlanSearchResult result = concealedSegmentSearch(
                        component, coverMask, depthByColumn, options);
                collectOwnedCrossPlans(accepted, result.candidates(), gridMinX, gridMinZ,
                        ownerChunkX, ownerChunkZ);
                collectOwnedCrossPlans(sizeRejected, result.sizeRejectedCandidates(),
                        gridMinX, gridMinZ, ownerChunkX, ownerChunkZ);
                if (!result.candidates().isEmpty()) {
                    collectOwnedSelectedCrossPlan(selected, selectedTouchedChunks,
                            result.candidates().get(0), gridMinX, gridMinZ,
                            ownerChunkX, ownerChunkZ);
                }
            }
            int powderColumns = accepted.values().stream()
                    .mapToInt(CrossPlanIdentity::powderColumns).sum();
            int solidColumns = accepted.values().stream()
                    .mapToInt(CrossPlanIdentity::solidColumns).sum();
            int crossChunkPlans = (int) accepted.values().stream()
                    .filter(CrossPlanIdentity::crossChunk).count();
            int selectedPowderColumns = selected.values().stream()
                    .mapToInt(CrossPlanIdentity::powderColumns).sum();
            int selectedSolidColumns = selected.values().stream()
                    .mapToInt(CrossPlanIdentity::solidColumns).sum();
            summaries.add(new CrossPlanCapSummary(totalSpanCap, accepted.size(),
                    powderColumns, solidColumns, sizeRejected.size(), crossChunkPlans,
                    selected.size(), selectedPowderColumns, selectedSolidColumns,
                    selectedTouchedChunks.size()));
        }
        return List.copyOf(summaries);
    }

    private static void collectOwnedCrossPlans(Map<String, CrossPlanIdentity> output,
            List<ConcealedSegment> segments, int gridMinX, int gridMinZ,
            int ownerChunkX, int ownerChunkZ) {
        for (ConcealedSegment segment : segments) {
            CrossPlanIdentity identity = crossPlanIdentity(gridMinX, gridMinZ, segment);
            if ((identity.midpointWorldX() >> 4) == ownerChunkX
                    && (identity.midpointWorldZ() >> 4) == ownerChunkZ) {
                output.putIfAbsent(identity.canonicalKey(), identity);
            }
        }
    }

    private static void collectOwnedSelectedCrossPlan(
            Map<String, CrossPlanIdentity> output, Set<Long> touchedChunks,
            ConcealedSegment segment, int gridMinX, int gridMinZ,
            int ownerChunkX, int ownerChunkZ) {
        CrossPlanIdentity identity = crossPlanIdentity(gridMinX, gridMinZ, segment);
        if ((identity.midpointWorldX() >> 4) != ownerChunkX
                || (identity.midpointWorldZ() >> 4) != ownerChunkZ
                || output.putIfAbsent(identity.canonicalKey(), identity) != null) {
            return;
        }
        for (int[] cell : segment.cover()) {
            touchedChunks.add(pack((gridMinX + cell[0]) >> 4, (gridMinZ + cell[1]) >> 4));
        }
    }

    private static void appendWorldCells(
            StringBuilder key, int gridMinX, int gridMinZ, List<int[]> cells) {
        for (int[] cell : cells) {
            key.append(gridMinX + cell[0]).append(',')
                    .append(gridMinZ + cell[1]).append(';');
        }
    }

    private static SearchOutcome concealedSegmentSearchInternal(
            List<int[]> deepComponent, boolean[][] coverMask, int[][] depthByColumn,
            CrossPlanOptions crossOptions) {
        List<int[]> canonical = canonicalCells(deepComponent);
        GridSize grid = matchingGridSize(coverMask, depthByColumn);
        if (canonical.isEmpty() || grid == null || !allInsideGrid(canonical, grid)
                || !matchesDeepComponentMask(canonical, coverMask, depthByColumn)) {
            return new SearchOutcome(new ConcealedSearchResult(List.of(), 1, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0), List.of());
        }

        List<ConcealedSegment> candidates = new ArrayList<>();
        List<ConcealedSegment> sizeRejectedCandidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int coreShapeRejects = 0;
        int shoulderMissingRejects = 0;
        int shoulderAmbiguousRejects = 0;
        int powderShareRejects = 0;
        int staggerRejects = 0;
        int traceOver9 = 0;
        int traceOwnerEdgeOrUnknown = 0;
        int augmentedStagger = 0;
        int augmentedOtherShape = 0;
        int preferredCandidates = 0;
        int fallbackCandidates = 0;

        int xSpan = span(canonical, true);
        int zSpan = span(canonical, false);
        boolean primaryMajorX = xSpan >= zSpan;
        boolean[][] componentMask = cellMask(canonical, grid.sizeX(), grid.sizeZ());
        boolean preferredAcceptedAlongX = false;
        boolean preferredAcceptedAlongZ = false;
        for (int powderStations : new int[]{PREFERRED_POWDER_STATIONS, FALLBACK_POWDER_STATIONS}) {
            for (boolean majorX : new boolean[]{primaryMajorX, !primaryMajorX}) {
                int minAlong = canonical.stream().mapToInt(c -> majorX ? c[0] : c[1]).min().orElseThrow();
                int maxAlong = canonical.stream().mapToInt(c -> majorX ? c[0] : c[1]).max().orElseThrow();
                if (powderStations == FALLBACK_POWDER_STATIONS
                        && (majorX ? preferredAcceptedAlongX : preferredAcceptedAlongZ)) {
                    // Six is an incidence fallback, but only a fully accepted preferred segment may suppress it.
                    continue;
                }
                for (int powderStart = minAlong;
                        powderStart + powderStations - 1 <= maxAlong; powderStart++) {
                    List<List<int[]>> powderPaths = deepStationPaths(componentMask, coverMask,
                            depthByColumn, majorX, powderStart, powderStations);
                    if (powderPaths.isEmpty()) {
                        coreShapeRejects++;
                        continue;
                    }
                    for (List<int[]> powder : powderPaths) {
                        Station firstPowder = station(powder, majorX, powderStart);
                        Station lastPowder = station(
                                powder, majorX, powderStart + powderStations - 1);
                        ShoulderOptions left = shoulderOptions(componentMask, coverMask,
                                depthByColumn, majorX, powderStart, -1, firstPowder);
                        ShoulderOptions right = shoulderOptions(componentMask, coverMask,
                                depthByColumn, majorX,
                                powderStart + powderStations - 1, 1, lastPowder);
                        if (left.failure() != ShoulderFailure.NONE
                                || right.failure() != ShoulderFailure.NONE) {
                            if (left.failure() == ShoulderFailure.AMBIGUOUS
                                    || right.failure() == ShoulderFailure.AMBIGUOUS) {
                                shoulderAmbiguousRejects++;
                            } else {
                                shoulderMissingRejects++;
                            }
                            continue;
                        }

                        for (ShoulderExtension leftExtension : left.extensions()) {
                            for (ShoulderExtension rightExtension : right.extensions()) {
                                List<int[]> shoulders = new ArrayList<>(leftExtension.cells());
                                shoulders.addAll(rightExtension.cells());
                                List<int[]> cover = new ArrayList<>(powder);
                                cover.addAll(shoulders);
                                cover.sort(PowderRoofTrap::compareCell);
                                int firstStation = powderStart - leftExtension.stations();
                                ConcealedSegment spine = new ConcealedSegment(
                                        powder, shoulders, cover, majorX, firstStation, powderStations,
                                        leftExtension.stations(), rightExtension.stations());
                                if ((long) powder.size() * 2L < cover.size()) {
                                    powderShareRejects++;
                                    continue;
                                }
                                if (!naturalShoulderStaggerForDepthProfile(
                                        cover, depthByColumn, majorX,
                                        leftExtension.stations(), powderStations,
                                        rightExtension.stations())) {
                                    staggerRejects++;
                                    continue;
                                }
                                if (!concealedSegmentAxisEligible(powder, shoulders, cover, majorX)) {
                                    coreShapeRejects++;
                                    continue;
                                }
                                FirstBankExtension extension = extendSpineToFirstBanks(
                                        spine, coverMask, crossOptions != null);
                                if (extension.failure() != ApronTraceFailure.NONE) {
                                    if (extension.failure() == ApronTraceFailure.OVER_NINE) {
                                        traceOver9++;
                                    } else {
                                        traceOwnerEdgeOrUnknown++;
                                    }
                                    coreShapeRejects++;
                                    continue;
                                }
                                ConcealedSegment segment = extension.segment();
                                int structuralWidth = crossOptions == null
                                        ? MAX_BANK_TO_BANK_COVER_WIDTH
                                        : Math.max(grid.sizeX(), grid.sizeZ());
                                if (!concealedSegmentAxisEligible(
                                        segment.powder(), segment.shoulders(),
                                        segment.cover(), segment.majorX(), structuralWidth,
                                        crossOptions == null
                                                ? MAX_LATERAL_APRON_CELLS_PER_SIDE : structuralWidth)) {
                                    if (!naturalShoulderStaggerForAxis(
                                            segment.cover(), segment.majorX(),
                                            segment.leftShoulderStations(), segment.powderStations(),
                                            segment.rightShoulderStations())) {
                                        augmentedStagger++;
                                    } else {
                                        // Defensive: successful first-bank traces currently prove every other
                                        // augmented shape invariant, but retain ownership if that ever changes.
                                        augmentedOtherShape++;
                                    }
                                    coreShapeRejects++;
                                    continue;
                                }
                                if (crossOptions != null
                                        && (extension.maximumIndividualApron()
                                                > crossOptions.maxIndividualApron()
                                            || extension.bankToBankSpan()
                                                > crossOptions.maxBankToBankSpan())) {
                                    sizeRejectedCandidates.add(segment);
                                    continue;
                                }
                                if (powderStations == PREFERRED_POWDER_STATIONS) {
                                    if (majorX) {
                                        preferredAcceptedAlongX = true;
                                    } else {
                                        preferredAcceptedAlongZ = true;
                                    }
                                }
                                String key = cellKey(segment.powder()) + "|"
                                        + cellKey(segment.shoulders());
                                if (!seen.add(key)) {
                                    continue;
                                }
                                candidates.add(segment);
                                if (powderStations == PREFERRED_POWDER_STATIONS) {
                                    preferredCandidates++;
                                } else {
                                    fallbackCandidates++;
                                }
                            }
                        }
                    }
                }
            }
        }
        int rankingWidth = crossOptions == null
                ? MAX_BANK_TO_BANK_COVER_WIDTH : Math.max(grid.sizeX(), grid.sizeZ());
        ConcealedSearchResult result = new ConcealedSearchResult(
                rankConcealedSegments(candidates, depthByColumn, rankingWidth),
                coreShapeRejects, shoulderMissingRejects, shoulderAmbiguousRejects,
                powderShareRejects, staggerRejects,
                traceOver9, traceOwnerEdgeOrUnknown, augmentedStagger, augmentedOtherShape,
                preferredCandidates, fallbackCandidates);
        return new SearchOutcome(result, uniqueSegments(sizeRejectedCandidates));
    }

    /**
     * Extend an already-valid narrow spine laterally through its exact contiguous opening to the first known
     * in-owner bank on both sides of every station. All added cells are solid shoulders. Each side independently
     * contributes its truthful 0..9 distance; mixed immediate-bank and nonzero stations are natural contour.
     */
    private static FirstBankExtension extendSpineToFirstBanks(
            ConcealedSegment spine, boolean[][] coverMask, boolean measureCompleteContour) {
        List<int[]> aprons = new ArrayList<>();
        int maximumIndividualApron = 0;
        int first = minimumStation(spine.cover(), spine.majorX());
        int last = maximumStation(spine.cover(), spine.majorX());
        for (int along = first; along <= last; along++) {
            Station run = station(spine.cover(), spine.majorX(), along);
            ApronTrace low = traceToFirstBank(
                    coverMask, spine.majorX(), along, run.low(), -1, measureCompleteContour);
            ApronTrace high = traceToFirstBank(
                    coverMask, spine.majorX(), along, run.high(), 1, measureCompleteContour);
            if (low.failure() != ApronTraceFailure.NONE) {
                return new FirstBankExtension(null, low.failure(), 0, 0);
            }
            if (high.failure() != ApronTraceFailure.NONE) {
                return new FirstBankExtension(null, high.failure(), 0, 0);
            }
            maximumIndividualApron = Math.max(maximumIndividualApron,
                    Math.max(low.cells().size(), high.cells().size()));
            aprons.addAll(low.cells());
            aprons.addAll(high.cells());
        }
        if (aprons.isEmpty()) {
            return new FirstBankExtension(spine, ApronTraceFailure.NONE, 0,
                    span(spine.cover(), !spine.majorX()));
        }
        List<int[]> shoulders = new ArrayList<>(spine.shoulders());
        shoulders.addAll(aprons);
        shoulders.sort(PowderRoofTrap::compareCell);
        List<int[]> cover = new ArrayList<>(spine.powder());
        cover.addAll(shoulders);
        cover.sort(PowderRoofTrap::compareCell);
        return new FirstBankExtension(new ConcealedSegment(spine.powder(), shoulders, cover,
                spine.majorX(), spine.firstStation(), spine.powderStations(),
                spine.leftShoulderStations(), spine.rightShoulderStations()),
                ApronTraceFailure.NONE, maximumIndividualApron,
                span(cover, !spine.majorX()));
    }

    private static ApronTrace traceToFirstBank(boolean[][] coverMask,
            boolean majorX, int along, int spineEdge, int direction,
            boolean measureCompleteContour) {
        List<int[]> cells = new ArrayList<>();
        int cross = spineEdge + direction;
        while (true) {
            int x = majorX ? along : cross;
            int z = majorX ? cross : along;
            if (!hasMaskCell(coverMask, x, z)) {
                // Owner edge or an unread mask cell is unknown, never a bank.
                return new ApronTrace(List.of(), ApronTraceFailure.OWNER_EDGE_OR_UNKNOWN);
            }
            if (!coverMask[x][z]) {
                return new ApronTrace(cells, ApronTraceFailure.NONE);
            }
            cells.add(new int[]{x, z});
            if (!measureCompleteContour
                    && cells.size() > MAX_LATERAL_APRON_CELLS_PER_SIDE) {
                return new ApronTrace(List.of(), ApronTraceFailure.OVER_NINE);
            }
            cross += direction;
        }
    }

    /** Deterministic ordering: eight stations, preferred area, fuller shoulders, then measured depth. */
    public static List<ConcealedSegment> rankConcealedSegments(
            List<ConcealedSegment> candidates, int[][] depthByColumn) {
        return rankConcealedSegments(candidates, depthByColumn, MAX_BANK_TO_BANK_COVER_WIDTH);
    }

    private static List<ConcealedSegment> rankConcealedSegments(
            List<ConcealedSegment> candidates, int[][] depthByColumn, int maximumCoverWidth) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<RankedSegment> ranked = new ArrayList<>();
        for (ConcealedSegment segment : candidates) {
            boolean eligible = segment != null && (maximumCoverWidth == MAX_BANK_TO_BANK_COVER_WIDTH
                    ? concealedSegmentEligible(segment.powder(), segment.shoulders())
                    : concealedSegmentAxisEligible(segment.powder(), segment.shoulders(),
                            segment.cover(), segment.majorX(), maximumCoverWidth, maximumCoverWidth));
            if (!eligible) {
                continue;
            }
            int minimumDepth = 0;
            long totalDepth = 0L;
            if (depthByColumn != null) {
                minimumDepth = Integer.MAX_VALUE;
                for (int[] cell : segment.powder()) {
                    if (!hasHeightCell(depthByColumn, cell[0], cell[1])) {
                        minimumDepth = Integer.MIN_VALUE;
                        totalDepth = Long.MIN_VALUE;
                        break;
                    }
                    int depth = depthByColumn[cell[0]][cell[1]];
                    minimumDepth = Math.min(minimumDepth, depth);
                    totalDepth += depth;
                }
            }
            boolean preferredCore = segment.powderStations() == PREFERRED_POWDER_STATIONS;
            boolean preferredArea = preferredCore
                    && segment.powder().size() >= PREFERRED_POWDER_AREA_MIN
                    && segment.powder().size() <= PREFERRED_POWDER_AREA_MAX;
            ranked.add(new RankedSegment(segment, preferredCore, preferredArea,
                    segment.leftShoulderStations() + segment.rightShoulderStations(),
                    minimumDepth, totalDepth));
        }
        ranked.sort((left, right) -> {
            int byCore = Boolean.compare(right.preferredCore(), left.preferredCore());
            if (byCore != 0) {
                return byCore;
            }
            int byAreaBand = Boolean.compare(right.preferredArea(), left.preferredArea());
            if (byAreaBand != 0) {
                return byAreaBand;
            }
            int byShoulders = Integer.compare(right.shoulderStations(), left.shoulderStations());
            if (byShoulders != 0) {
                return byShoulders;
            }
            int byMinimumDepth = Integer.compare(right.minimumDepth(), left.minimumDepth());
            if (byMinimumDepth != 0) {
                return byMinimumDepth;
            }
            int byTotalDepth = Long.compare(right.totalDepth(), left.totalDepth());
            if (byTotalDepth != 0) {
                return byTotalDepth;
            }
            int byArea = Integer.compare(right.segment().powder().size(), left.segment().powder().size());
            if (byArea != 0) {
                return byArea;
            }
            int byAxis = Boolean.compare(right.segment().majorX(), left.segment().majorX());
            if (byAxis != 0) {
                return byAxis;
            }
            int byStart = Integer.compare(left.segment().firstStation(), right.segment().firstStation());
            return byStart != 0 ? byStart
                    : compareFootprints(left.segment().cover(), right.segment().cover());
        });
        return ranked.stream().map(RankedSegment::segment).toList();
    }

    private static boolean[][] cellMask(List<int[]> cells) {
        return cellMask(cells, 16, 16);
    }

    private static boolean[][] cellMask(List<int[]> cells, int sizeX, int sizeZ) {
        boolean[][] mask = new boolean[sizeX][sizeZ];
        for (int[] cell : cells) {
            mask[cell[0]][cell[1]] = true;
        }
        return mask;
    }

    /** Enumerate connected paths of complete maximal deep-component runs, never whole station slices. */
    private static List<List<int[]>> deepStationPaths(boolean[][] componentMask,
            boolean[][] coverMask, int[][] depthByColumn, boolean majorX,
            int start, int stations) {
        List<List<Station>> runPaths = new ArrayList<>();
        collectDeepStationPaths(componentMask, coverMask, depthByColumn, majorX,
                start, stations, 0, null, new ArrayList<>(), runPaths);
        List<List<int[]>> cellPaths = new ArrayList<>();
        for (List<Station> runPath : runPaths) {
            List<int[]> cells = new ArrayList<>();
            for (int offset = 0; offset < runPath.size(); offset++) {
                addStationCells(cells, majorX, start + offset, runPath.get(offset));
            }
            cells.sort(PowderRoofTrap::compareCell);
            if (powderCoreAxisEligible(cells, majorX)) {
                cellPaths.add(cells);
            }
        }
        return cellPaths;
    }

    private static void collectDeepStationPaths(boolean[][] componentMask,
            boolean[][] coverMask, int[][] depthByColumn, boolean majorX,
            int start, int stations, int offset, Station previous,
            List<Station> path, List<List<Station>> output) {
        if (offset == stations) {
            output.add(List.copyOf(path));
            return;
        }
        int along = start + offset;
        for (Station run : stationRuns(componentMask, majorX, along)) {
            if (!stationEligible(run)
                    || previous != null && !stationsTouch(previous, run)
                    || !stationDeepAndCovered(
                            coverMask, depthByColumn, majorX, along, run)) {
                continue;
            }
            path.add(run);
            collectDeepStationPaths(componentMask, coverMask, depthByColumn, majorX,
                    start, stations, offset + 1, run, path, output);
            path.remove(path.size() - 1);
        }
    }

    /** The caller may not omit, invent, or shave a ten-block cell from the flooded deep component. */
    private static boolean matchesDeepComponentMask(
            List<int[]> component, boolean[][] coverMask, int[][] depthByColumn) {
        Set<Long> expected = packedCells(component);
        Set<Long> reached = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int[] first = component.get(0);
        queue.addLast(first);
        reached.add(pack(first[0], first[1]));
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            if (!hasMaskCell(coverMask, cell[0], cell[1]) || !coverMask[cell[0]][cell[1]]
                    || !hasHeightCell(depthByColumn, cell[0], cell[1])
                    || depthByColumn[cell[0]][cell[1]] < MIN_CANDIDATE_DEPTH_BLOCKS) {
                return false;
            }
            for (int[] direction : CARDINALS) {
                int x = cell[0] + direction[0];
                int z = cell[1] + direction[1];
                long packed = pack(x, z);
                if (hasMaskCell(coverMask, x, z) && coverMask[x][z]
                        && hasHeightCell(depthByColumn, x, z)
                        && depthByColumn[x][z] >= MIN_CANDIDATE_DEPTH_BLOCKS
                        && reached.add(packed)) {
                    queue.addLast(new int[]{x, z});
                }
            }
        }
        return reached.equals(expected);
    }

    private static ShoulderOptions shoulderOptions(boolean[][] componentMask,
            boolean[][] coverMask, int[][] depthByColumn, boolean majorX,
            int powderEdgeStation, int direction, Station powderEdge) {
        List<ShoulderExtension> extensions = new ArrayList<>();
        boolean[] failure = new boolean[2]; // 0=missing, 1=present-but-invalid/ambiguous
        collectShoulderExtensions(componentMask, coverMask, depthByColumn, majorX,
                powderEdgeStation, direction, 1, powderEdge,
                new ArrayList<>(), extensions, failure);
        if (failure[1]) {
            return new ShoulderOptions(List.of(), ShoulderFailure.AMBIGUOUS);
        }
        if (!extensions.isEmpty()) {
            Map<String, ShoulderExtension> unique = new LinkedHashMap<>();
            for (ShoulderExtension extension : extensions) {
                unique.putIfAbsent(cellKey(extension.cells()), extension);
            }
            return new ShoulderOptions(new ArrayList<>(unique.values()), ShoulderFailure.NONE);
        }
        return new ShoulderOptions(List.of(), ShoulderFailure.MISSING);
    }

    private static void collectShoulderExtensions(boolean[][] componentMask,
            boolean[][] coverMask, int[][] depthByColumn, boolean majorX,
            int powderEdgeStation, int direction, int step, Station previous,
            List<int[]> cells, List<ShoulderExtension> output, boolean[] failure) {
        if (step > MAX_SHOULDER_STATIONS_PER_END) {
            return;
        }
        int target = powderEdgeStation + direction * step;
        List<Station> deepTouching = touchingRuns(
                componentMask, majorX, target, previous);
        boolean useDeep = !deepTouching.isEmpty();
        List<Station> touching = useDeep
                ? deepTouching : touchingRuns(coverMask, majorX, target, previous);
        if (touching.isEmpty()) {
            if (step <= MIN_SHOULDER_STATIONS_PER_END) {
                failure[0] = true;
            }
            return;
        }

        // A shallow scanner shoulder represents the visible roof edge, so a split or
        // overwide cover run is genuinely ambiguous. Deep fissure shoulders are
        // different: branches belong to the underground component and each valid
        // continuing run must be considered independently.
        if (!useDeep) {
            Station coverRun = touching.size() == 1 ? touching.get(0) : null;
            if (coverRun == null || !stationEligible(coverRun)
                    || !stationEntirelyShallow(
                            depthByColumn, majorX, target, coverRun)) {
                // A present fourth station is optional only when it is a legal
                // continuation. Cropping before a colliding/overwide visible run
                // would misclassify the same physical roof as a clean 3-station edge.
                failure[1] = true;
                return;
            }
        }

        boolean foundValid = false;
        for (Station run : touching) {
            boolean valid = stationEligible(run) && (!useDeep
                    || stationDeepAndCovered(
                            coverMask, depthByColumn, majorX, target, run));
            if (!valid) {
                continue;
            }
            foundValid = true;
            List<int[]> nextCells = new ArrayList<>(cells);
            addStationCells(nextCells, majorX, target, run);
            if (step >= MIN_SHOULDER_STATIONS_PER_END) {
                output.add(new ShoulderExtension(nextCells, step));
            }
            collectShoulderExtensions(componentMask, coverMask, depthByColumn, majorX,
                    powderEdgeStation, direction, step + 1, run,
                    nextCells, output, failure);
        }
        if (!foundValid && step <= MIN_SHOULDER_STATIONS_PER_END) {
            failure[1] = true;
        }
    }

    private static List<Station> touchingRuns(
            boolean[][] mask, boolean majorX, int along, Station previous) {
        List<Station> touching = new ArrayList<>();
        for (Station run : stationRuns(mask, majorX, along)) {
            if (stationsTouch(previous, run)) {
                touching.add(run);
            }
        }
        return touching;
    }

    private static List<Station> stationRuns(boolean[][] mask, boolean majorX, int targetAlong) {
        List<Station> runs = new ArrayList<>();
        GridSize grid = gridSize(mask);
        if (grid == null) {
            return runs;
        }
        int crossLimit = majorX ? grid.sizeZ() : grid.sizeX();
        int runStart = -1;
        for (int cross = 0; cross <= crossLimit; cross++) {
            int x = majorX ? targetAlong : cross;
            int z = majorX ? cross : targetAlong;
            boolean present = cross < crossLimit && hasMaskCell(mask, x, z) && mask[x][z];
            if (present && runStart < 0) {
                runStart = cross;
            } else if (!present && runStart >= 0) {
                runs.add(new Station(runStart, cross - 1, cross - runStart));
                runStart = -1;
            }
        }
        return runs;
    }

    private static boolean stationEntirelyShallow(
            int[][] depthByColumn, boolean majorX, int along, Station station) {
        for (int cross = station.low(); cross <= station.high(); cross++) {
            int x = majorX ? along : cross;
            int z = majorX ? cross : along;
            if (hasHeightCell(depthByColumn, x, z)
                    && depthByColumn[x][z] >= MIN_CANDIDATE_DEPTH_BLOCKS) {
                return false;
            }
        }
        return true;
    }

    private static boolean stationDeepAndCovered(boolean[][] coverMask,
            int[][] depthByColumn, boolean majorX, int along, Station station) {
        for (int cross = station.low(); cross <= station.high(); cross++) {
            int x = majorX ? along : cross;
            int z = majorX ? cross : along;
            if (!hasMaskCell(coverMask, x, z) || !coverMask[x][z]
                    || !hasHeightCell(depthByColumn, x, z)
                    || depthByColumn[x][z] < MIN_CANDIDATE_DEPTH_BLOCKS) {
                return false;
            }
        }
        return true;
    }

    private static void addStationCells(
            List<int[]> cells, boolean majorX, int along, Station station) {
        for (int cross = station.low(); cross <= station.high(); cross++) {
            cells.add(majorX ? new int[]{along, cross} : new int[]{cross, along});
        }
    }

    /** Full-width, connected/hole-free six- or eight-station powder hazard. */
    public static boolean powderCoreEligible(List<int[]> powder) {
        Geometry geometry = powder == null ? null : geometry(powder);
        if (geometry == null
                || !isFourConnected(geometry.occupied())
                || hasInteriorHole(geometry.occupied(), geometry.minX(), geometry.maxX(),
                        geometry.minZ(), geometry.maxZ())) {
            return false;
        }
        return powderCoreAxisEligible(powder, true) || powderCoreAxisEligible(powder, false);
    }

    private static boolean powderCoreAxisEligible(List<int[]> powder, boolean majorX) {
        if (powder == null || powder.isEmpty()) {
            return false;
        }
        int powderStations = span(powder, majorX);
        if (powderStations != PREFERRED_POWDER_STATIONS
                && powderStations != FALLBACK_POWDER_STATIONS) {
            return false;
        }
        int minimumArea = powderStations == PREFERRED_POWDER_STATIONS
                ? PREFERRED_POWDER_AREA_MIN : FALLBACK_MIN_POWDER_AREA;
        Geometry geometry = geometry(powder);
        if (geometry == null || powder.size() < minimumArea
                || !isFourConnected(geometry.occupied())
                || hasInteriorHole(geometry.occupied(), geometry.minX(), geometry.maxX(),
                        geometry.minZ(), geometry.maxZ())) {
            return false;
        }
        int start = powder.stream().mapToInt(c -> majorX ? c[0] : c[1]).min().orElseThrow();
        Station previous = null;
        for (int station = start; station < start + powderStations; station++) {
            Station current = station(powder, majorX, station);
            if (!stationEligible(current) || previous != null && !stationsTouch(previous, current)) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    /** Complete scanner/generator shape law for a disjoint material split. */
    public static boolean concealedSegmentEligible(List<int[]> powder, List<int[]> shoulders) {
        List<int[]> canonicalPowder = canonicalCells(powder);
        List<int[]> canonicalShoulders = canonicalCells(shoulders);
        if (canonicalPowder.isEmpty() || canonicalShoulders.isEmpty()
                || canonicalPowder.size() != (powder == null ? 0 : powder.size())
                || canonicalShoulders.size() != (shoulders == null ? 0 : shoulders.size())) {
            return false;
        }
        Set<Long> occupied = new HashSet<>();
        for (int[] cell : canonicalPowder) {
            occupied.add(pack(cell[0], cell[1]));
        }
        for (int[] cell : canonicalShoulders) {
            if (!occupied.add(pack(cell[0], cell[1]))) {
                return false;
            }
        }
        List<int[]> cover = new ArrayList<>(canonicalPowder);
        cover.addAll(canonicalShoulders);
        cover.sort(PowderRoofTrap::compareCell);
        return concealedSegmentAxisEligible(canonicalPowder, canonicalShoulders, cover, true)
                || concealedSegmentAxisEligible(canonicalPowder, canonicalShoulders, cover, false);
    }

    /** Is this exact material split a valid concealed segment along the requested station axis? */
    public static boolean concealedSegmentAxisEligible(List<int[]> powder, List<int[]> shoulders,
            List<int[]> cover, boolean majorX) {
        return concealedSegmentAxisEligible(powder, shoulders, cover, majorX,
                MAX_BANK_TO_BANK_COVER_WIDTH, MAX_LATERAL_APRON_CELLS_PER_SIDE);
    }

    private static boolean concealedSegmentAxisEligible(List<int[]> powder, List<int[]> shoulders,
            List<int[]> cover, boolean majorX, int maximumCoverWidth, int maximumApron) {
        if (!powderCoreAxisEligible(powder, majorX)) {
            return false;
        }
        int start = minimumStation(cover, majorX);
        int end = maximumStation(cover, majorX);
        int powderStart = minimumStation(powder, majorX);
        int powderEnd = maximumStation(powder, majorX);
        int powderStations = powderEnd - powderStart + 1;
        int leftShoulderStations = powderStart - start;
        int rightShoulderStations = end - powderEnd;
        if (leftShoulderStations < MIN_SHOULDER_STATIONS_PER_END
                || leftShoulderStations > MAX_SHOULDER_STATIONS_PER_END
                || rightShoulderStations < MIN_SHOULDER_STATIONS_PER_END
                || rightShoulderStations > MAX_SHOULDER_STATIONS_PER_END) {
            return false;
        }
        Set<Long> powderSet = packedCells(powder);
        Set<Long> shoulderSet = packedCells(shoulders);
        Set<Long> coverSet = packedCells(cover);
        if (powderSet.size() != powder.size() || shoulderSet.size() != shoulders.size()
                || coverSet.size() != cover.size()) {
            return false;
        }
        Set<Long> materialSet = new HashSet<>(powderSet);
        for (long shoulder : shoulderSet) {
            if (!materialSet.add(shoulder)) {
                return false;
            }
        }
        if (!materialSet.equals(coverSet)) {
            return false;
        }
        return bankToBankMaterialAxisEligible(powder, cover, majorX,
                start, powderStart, powderEnd,
                leftShoulderStations, powderStations, rightShoulderStations,
                maximumCoverWidth, maximumApron);
    }

    private static boolean bankToBankMaterialAxisEligible(List<int[]> powder, List<int[]> cover,
            boolean majorX, int start, int powderStart, int powderEnd,
            int leftShoulders, int powderStations, int rightShoulders,
            int maximumCoverWidth, int maximumApron) {
        if (!coverAxisEligible(cover, majorX, false, maximumCoverWidth)
                || !naturalShoulderStaggerForAxis(
                        cover, majorX, leftShoulders, powderStations, rightShoulders)) {
            return false;
        }
        int total = leftShoulders + powderStations + rightShoulders;
        for (int offset = 0; offset < total; offset++) {
            int along = start + offset;
            Station full = station(cover, majorX, along);
            if (!fullCoverStationEligible(full, maximumCoverWidth)
                    || full.width() < PATCH_MIN_MINOR_DIMENSION
                            + 2 * MIN_LATERAL_APRON_CELLS_PER_SIDE) {
                return false;
            }
            if (along < powderStart || along > powderEnd) {
                continue; // longitudinal end stations remain wholly solid.
            }
            Station hazard = station(powder, majorX, along);
            if (!stationEligible(hazard)) {
                return false;
            }
            int lowApron = hazard.low() - full.low();
            int highApron = full.high() - hazard.high();
            if (lowApron < MIN_LATERAL_APRON_CELLS_PER_SIDE
                    || lowApron > maximumApron
                    || highApron < MIN_LATERAL_APRON_CELLS_PER_SIDE
                    || highApron > maximumApron) {
                return false;
            }
        }
        return true;
    }

    /** Reject a visually framed rectangle for every legal 12..16-station partition. */
    public static boolean hasNaturalShoulderStagger(List<int[]> cover) {
        return naturalShoulderStaggerForAxis(cover, true) || naturalShoulderStaggerForAxis(cover, false);
    }

    private static boolean naturalShoulderStaggerForAxis(List<int[]> cover, boolean majorX) {
        if (!coverAxisEligible(cover, majorX, false)) {
            return false;
        }
        int total = span(cover, majorX);
        for (int powderStations : new int[]{PREFERRED_POWDER_STATIONS, FALLBACK_POWDER_STATIONS}) {
            for (int left = MIN_SHOULDER_STATIONS_PER_END;
                    left <= MAX_SHOULDER_STATIONS_PER_END; left++) {
                int right = total - powderStations - left;
                if (right >= MIN_SHOULDER_STATIONS_PER_END
                        && right <= MAX_SHOULDER_STATIONS_PER_END
                        && naturalShoulderStaggerForAxis(
                                cover, majorX, left, powderStations, right)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean naturalShoulderStaggerForAxis(List<int[]> cover, boolean majorX,
            int leftShoulders, int powderStations, int rightShoulders) {
        int total = leftShoulders + powderStations + rightShoulders;
        if (span(cover, majorX) != total) {
            return false;
        }
        int start = minimumStation(cover, majorX);
        Station[] stations = new Station[total];
        for (int i = 0; i < stations.length; i++) {
            stations[i] = station(cover, majorX, start + i);
        }
        boolean left = false;
        for (int i = 0; i < leftShoulders - 1; i++) {
            left |= stationEdgeShift(stations[i], stations[i + 1]);
        }
        boolean right = false;
        int rightStart = leftShoulders + powderStations;
        for (int i = rightStart; i < total - 1; i++) {
            right |= stationEdgeShift(stations[i], stations[i + 1]);
        }
        return left && right;
    }

    /**
     * Search-time stagger additionally preserves the observed depth profile. A shift where a deep station is
     * reclassified next to a shallow station is a material seam, not a natural bend inside the solid shoulder.
     */
    private static boolean naturalShoulderStaggerForDepthProfile(List<int[]> cover,
            int[][] depthByColumn, boolean majorX,
            int leftShoulders, int powderStations, int rightShoulders) {
        int total = leftShoulders + powderStations + rightShoulders;
        if (span(cover, majorX) != total) {
            return false;
        }
        int start = minimumStation(cover, majorX);
        Station[] stations = new Station[total];
        boolean[] deep = new boolean[total];
        for (int i = 0; i < total; i++) {
            stations[i] = station(cover, majorX, start + i);
            deep[i] = stationEntirelyDeep(
                    depthByColumn, majorX, start + i, stations[i]);
        }
        return shoulderHasSameDepthStagger(stations, deep, 0, leftShoulders)
                && shoulderHasSameDepthStagger(stations, deep,
                        leftShoulders + powderStations, rightShoulders);
    }

    private static boolean shoulderHasSameDepthStagger(
            Station[] stations, boolean[] deep, int start, int count) {
        for (int i = start; i < start + count - 1; i++) {
            if (deep[i] == deep[i + 1]
                    && stationEdgeShift(stations[i], stations[i + 1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean stationEntirelyDeep(
            int[][] depthByColumn, boolean majorX, int along, Station station) {
        for (int cross = station.low(); cross <= station.high(); cross++) {
            int x = majorX ? along : cross;
            int z = majorX ? cross : along;
            if (!hasHeightCell(depthByColumn, x, z)
                    || depthByColumn[x][z] < MIN_CANDIDATE_DEPTH_BLOCKS) {
                return false;
            }
        }
        return true;
    }

    private static boolean coverAxisEligible(List<int[]> cover, boolean majorX, boolean requireStagger) {
        return coverAxisEligible(
                cover, majorX, requireStagger, MAX_BANK_TO_BANK_COVER_WIDTH);
    }

    private static boolean coverAxisEligible(List<int[]> cover, boolean majorX,
            boolean requireStagger, int maximumCoverWidth) {
        Geometry geometry = cover == null ? null : geometry(cover);
        int stations = geometry == null ? 0 : span(cover, majorX);
        if (geometry == null || stations < MIN_CONCEALED_STATIONS
                || stations > MAX_CONCEALED_STATIONS
                || !isFourConnected(geometry.occupied())
                || hasInteriorHole(geometry.occupied(), geometry.minX(), geometry.maxX(),
                        geometry.minZ(), geometry.maxZ())) {
            return false;
        }
        int start = minimumStation(cover, majorX);
        Station previous = null;
        for (int along = start; along < start + stations; along++) {
            Station current = station(cover, majorX, along);
            if (!fullCoverStationEligible(current, maximumCoverWidth)
                    || previous != null && !stationsTouch(previous, current)) {
                return false;
            }
            previous = current;
        }
        return !requireStagger || naturalShoulderStaggerForAxis(cover, majorX);
    }

    /** Six means the incidence fallback; area alone is deliberately not a fallback classifier. */
    public static int powderStationCount(List<int[]> powder, List<int[]> shoulders) {
        List<int[]> canonicalPowder = canonicalCells(powder);
        List<int[]> canonicalShoulders = canonicalCells(shoulders);
        if (canonicalPowder.isEmpty() || canonicalShoulders.isEmpty()) {
            return 0;
        }
        List<int[]> cover = new ArrayList<>(canonicalPowder);
        cover.addAll(canonicalShoulders);
        cover.sort(PowderRoofTrap::compareCell);
        if (concealedSegmentAxisEligible(canonicalPowder, canonicalShoulders, cover, true)) {
            return span(canonicalPowder, true);
        }
        if (concealedSegmentAxisEligible(canonicalPowder, canonicalShoulders, cover, false)) {
            return span(canonicalPowder, false);
        }
        return 0;
    }

    private static boolean stationEligible(Station station) {
        return station.count() > 0 && station.count() == station.width()
                && station.width() >= PATCH_MIN_MINOR_DIMENSION
                && station.width() <= PATCH_MAX_MINOR_DIMENSION;
    }

    private static boolean fullCoverStationEligible(Station station) {
        return fullCoverStationEligible(station, MAX_BANK_TO_BANK_COVER_WIDTH);
    }

    private static boolean fullCoverStationEligible(Station station, int maximumCoverWidth) {
        return station.count() > 0 && station.count() == station.width()
                && station.width() >= PATCH_MIN_MINOR_DIMENSION
                && station.width() <= maximumCoverWidth;
    }

    private static boolean stationsTouch(Station left, Station right) {
        return left.low() <= right.high() && right.low() <= left.high();
    }

    private static boolean stationEdgeShift(Station left, Station right) {
        return left.low() != right.low() || left.high() != right.high();
    }

    private static Station station(List<int[]> cells, boolean majorX, int targetAlong) {
        int minCross = Integer.MAX_VALUE;
        int maxCross = Integer.MIN_VALUE;
        int count = 0;
        for (int[] cell : cells) {
            if ((majorX ? cell[0] : cell[1]) != targetAlong) {
                continue;
            }
            int cross = majorX ? cell[1] : cell[0];
            minCross = Math.min(minCross, cross);
            maxCross = Math.max(maxCross, cross);
            count++;
        }
        return new Station(minCross, maxCross, count);
    }

    private static int minimumStation(List<int[]> cells, boolean majorX) {
        return cells.stream().mapToInt(c -> majorX ? c[0] : c[1]).min().orElseThrow();
    }

    private static int maximumStation(List<int[]> cells, boolean majorX) {
        return cells.stream().mapToInt(c -> majorX ? c[0] : c[1]).max().orElseThrow();
    }

    private static List<int[]> canonicalCells(List<int[]> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<int[]> canonical = new ArrayList<>();
        Set<Long> unique = new HashSet<>();
        for (int[] cell : cells) {
            if (cell == null || cell.length < 2 || !unique.add(pack(cell[0], cell[1]))) {
                return List.of();
            }
            canonical.add(new int[]{cell[0], cell[1]});
        }
        canonical.sort(PowderRoofTrap::compareCell);
        return canonical;
    }

    private static List<int[]> immutableCells(List<int[]> cells) {
        List<int[]> copy = new ArrayList<>();
        if (cells != null) {
            for (int[] cell : cells) {
                copy.add(new int[]{cell[0], cell[1]});
            }
        }
        copy.sort(PowderRoofTrap::compareCell);
        return List.copyOf(copy);
    }

    private static Set<Long> packedCells(List<int[]> cells) {
        Set<Long> packed = new HashSet<>();
        for (int[] cell : cells) {
            packed.add(pack(cell[0], cell[1]));
        }
        return packed;
    }

    private static String cellKey(List<int[]> cells) {
        StringBuilder key = new StringBuilder();
        for (int[] cell : cells) {
            key.append(cell[0]).append(',').append(cell[1]).append(';');
        }
        return key.toString();
    }

    private static List<ConcealedSegment> uniqueSegments(List<ConcealedSegment> segments) {
        Map<String, ConcealedSegment> unique = new LinkedHashMap<>();
        for (ConcealedSegment segment : segments) {
            if (segment != null) {
                unique.putIfAbsent(cellKey(segment.powder()) + "|" + cellKey(segment.shoulders()), segment);
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean allInsideLocalChunk(List<int[]> cells) {
        return cells.stream().allMatch(c -> c[0] >= 0 && c[0] < 16 && c[1] >= 0 && c[1] < 16);
    }

    private static boolean allInsideGrid(List<int[]> cells, GridSize grid) {
        return cells.stream().allMatch(c -> c[0] >= 0 && c[0] < grid.sizeX()
                && c[1] >= 0 && c[1] < grid.sizeZ());
    }

    private record GridSize(int sizeX, int sizeZ) {
    }

    private static GridSize gridSize(boolean[][] mask) {
        if (mask == null || mask.length == 0 || mask[0] == null || mask[0].length == 0) {
            return null;
        }
        int sizeZ = mask[0].length;
        for (boolean[] row : mask) {
            if (row == null || row.length != sizeZ) {
                return null;
            }
        }
        return new GridSize(mask.length, sizeZ);
    }

    private static GridSize matchingGridSize(boolean[][] mask, int[][] heights) {
        GridSize grid = gridSize(mask);
        if (grid == null || heights == null || heights.length != grid.sizeX()) {
            return null;
        }
        for (int[] row : heights) {
            if (row == null || row.length != grid.sizeZ()) {
                return null;
            }
        }
        return grid;
    }

    /**
     * Does a generated footprint have known terrain along both of its true long sides for at least 80% of its
     * longitudinal stations? Candidate cells are opening, {@code false} cells are bank, and coordinates beyond
     * the supplied mask are unknown rather than bank. End contacts never substitute for a missing long side.
     */
    public static boolean hasOpposingLongSideBankCoverage(
            List<int[]> footprint, boolean[][] candidateMask) {
        Boolean majorX = inferredMajorAxis(footprint);
        return majorX != null && hasOpposingLongSideBankCoverage(footprint, candidateMask, majorX);
    }

    /** Axis-explicit generation form; the selected powder/shoulder station axis is authoritative. */
    public static boolean hasOpposingLongSideBankCoverage(
            List<int[]> footprint, boolean[][] candidateMask, boolean majorX) {
        Geometry geometry = footprint == null ? null : geometry(footprint);
        if (geometry == null || candidateMask == null || !coverAxisEligible(footprint, majorX, false)) {
            return false;
        }
        List<int[]> knownBankContacts = new ArrayList<>();
        for (int[] cell : footprint) {
            for (int[] direction : CARDINALS) {
                int x = cell[0] + direction[0];
                int z = cell[1] + direction[1];
                if (hasMaskCell(candidateMask, x, z)
                        && !candidateMask[x][z]
                        && !geometry.occupied().contains(pack(x, z))) {
                    knownBankContacts.add(new int[]{x, z});
                }
            }
        }
        return hasOpposingLongSideBankContacts(footprint, knownBankContacts, majorX);
    }

    /**
     * Generation-time form of the scanner's walkable-bank rule. A structural bank counts only when its known
     * first-air height is within one step of the selected cover plane. Both this method and the scanner feed
     * the resulting coordinates into {@link #hasOpposingLongSideBankContacts(List, List)}, so their 80%
     * orientation and station semantics cannot drift.
     */
    public static boolean hasOpposingLongSideWalkableBankCoverage(List<int[]> footprint,
            boolean[][] candidateMask, int[][] surfaceFirstAir, int coverFirstAir) {
        Boolean majorX = inferredMajorAxis(footprint);
        return majorX != null && hasOpposingLongSideWalkableBankCoverage(
                footprint, candidateMask, surfaceFirstAir, coverFirstAir, majorX);
    }

    /** Axis-explicit walkable-bank form used by an already selected concealed segment. */
    public static boolean hasOpposingLongSideWalkableBankCoverage(List<int[]> footprint,
            boolean[][] candidateMask, int[][] surfaceFirstAir, int coverFirstAir, boolean majorX) {
        Geometry geometry = footprint == null ? null : geometry(footprint);
        if (geometry == null || candidateMask == null || surfaceFirstAir == null
                || !coverAxisEligible(footprint, majorX, false)) {
            return false;
        }
        List<int[]> walkableBankContacts = new ArrayList<>();
        for (int[] cell : footprint) {
            for (int[] direction : CARDINALS) {
                int x = cell[0] + direction[0];
                int z = cell[1] + direction[1];
                if (hasMaskCell(candidateMask, x, z)
                        && hasHeightCell(surfaceFirstAir, x, z)
                        && !candidateMask[x][z]
                        && !geometry.occupied().contains(pack(x, z))
                        && Math.abs((long) surfaceFirstAir[x][z] - coverFirstAir)
                                <= MAX_APPROACH_SURFACE_OFFSET) {
                    walkableBankContacts.add(new int[]{x, z});
                }
            }
        }
        return hasOpposingLongSideBankContacts(footprint, walkableBankContacts, majorX);
    }

    /**
     * Coverage form used by the multi-chunk physical scanner. Callers supply only known, walkable bank cells;
     * missing/out-of-scan cells therefore remain unknown. Coordinates are deduplicated before station counts.
     */
    public static boolean hasOpposingLongSideBankContacts(
            List<int[]> footprint, List<int[]> knownBankContacts) {
        Boolean majorX = inferredMajorAxis(footprint);
        return majorX != null && hasOpposingLongSideBankContacts(footprint, knownBankContacts, majorX);
    }

    /** Axis-explicit scanner form; end contacts cannot substitute for the selected segment's long sides. */
    public static boolean hasOpposingLongSideBankContacts(
            List<int[]> footprint, List<int[]> knownBankContacts, boolean majorX) {
        Geometry geometry = footprint == null ? null : geometry(footprint);
        if (geometry == null || knownBankContacts == null
                || !coverAxisEligible(footprint, majorX, false)) {
            return false;
        }
        Set<Long> contacts = new HashSet<>();
        for (int[] contact : knownBankContacts) {
            if (contact != null && contact.length >= 2) {
                contacts.add(pack(contact[0], contact[1]));
            }
        }
        int minAlong = Integer.MAX_VALUE;
        int maxAlong = Integer.MIN_VALUE;
        for (long packed : geometry.occupied()) {
            int x = (int) (packed >> 32);
            int z = (int) packed;
            int along = majorX ? x : z;
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
        }
        int lowSideContacts = 0;
        int highSideContacts = 0;
        for (int along = minAlong; along <= maxAlong; along++) {
            int minCross = Integer.MAX_VALUE;
            int maxCross = Integer.MIN_VALUE;
            for (long packed : geometry.occupied()) {
                int x = (int) (packed >> 32);
                int z = (int) packed;
                if ((majorX ? x : z) != along) {
                    continue;
                }
                int cross = majorX ? z : x;
                minCross = Math.min(minCross, cross);
                maxCross = Math.max(maxCross, cross);
            }
            long lowSide = majorX ? pack(along, minCross - 1) : pack(minCross - 1, along);
            long highSide = majorX ? pack(along, maxCross + 1) : pack(maxCross + 1, along);
            if (contacts.contains(lowSide)) {
                lowSideContacts++;
            }
            if (contacts.contains(highSide)) {
                highSideContacts++;
            }
        }
        int stations = maxAlong - minAlong + 1;
        return (long) lowSideContacts * 100 >= (long) stations * MIN_LONG_SIDE_BANK_COVERAGE_PERCENT
                && (long) highSideContacts * 100
                        >= (long) stations * MIN_LONG_SIDE_BANK_COVERAGE_PERCENT;
    }

    private static Boolean inferredMajorAxis(List<int[]> footprint) {
        boolean xEligible = coverAxisEligible(footprint, true, false);
        boolean zEligible = coverAxisEligible(footprint, false, false);
        if (!xEligible && !zEligible) {
            return null;
        }
        return xEligible && (!zEligible || span(footprint, true) >= span(footprint, false));
    }

    private static boolean hasMaskCell(boolean[][] mask, int x, int z) {
        return x >= 0 && x < mask.length && mask[x] != null && z >= 0 && z < mask[x].length;
    }

    private static boolean hasHeightCell(int[][] heights, int x, int z) {
        return x >= 0 && x < heights.length && heights[x] != null && z >= 0 && z < heights[x].length;
    }

    private static Geometry geometry(List<int[]> cells) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        Set<Long> occupied = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : cells) {
            if (c == null || c.length < 2 || !occupied.add(pack(c[0], c[1]))) {
                return null;
            }
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        return new Geometry(occupied, minX, maxX, minZ, maxZ);
    }

    private record Geometry(Set<Long> occupied, int minX, int maxX, int minZ, int maxZ) {
    }

    private static int span(List<int[]> cells, boolean xAxis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] c : cells) {
            int value = xAxis ? c[0] : c[1];
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min + 1;
    }

    private static int compareCell(int[] left, int[] right) {
        int byX = Integer.compare(left[0], right[0]);
        return byX != 0 ? byX : Integer.compare(left[1], right[1]);
    }

    private static int compareFootprints(List<int[]> left, List<int[]> right) {
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            int comparison = compareCell(left.get(i), right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
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
