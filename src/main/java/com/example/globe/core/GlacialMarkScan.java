package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 5 Crew 8 / S29 -- the one piece of non-trivial pure math behind the ground-truth {@code /latdev
 * markGlacial} command (Peetsa 2026-07-20, verbatim: "None of this is working. Locate crevasse and teleport
 * just puts me in the same spot... there is no falling through the snow... To make it easier just for dev,
 * can you turn on a simple color filter for the trap crevasses -- maybe typing a command causes them to glow
 * green?"). Where {@link CrevasseLocator} PREDICTS a carver's seeded start chunk (and so can miss the visible
 * opening, which the arc may cut up to 8 chunks away), {@code markGlacial} instead inspects REAL generated
 * blocks and marks what is actually there -- so it needs no seed math, only a local snowfield reference to
 * decide "does this column sit in a deep open shaft below the surrounding snow".
 *
 * <p>That reference is the same one {@code world.PowderCrevasseRoofFeature} uses to place the powder-snow
 * roofs: the local MAXIMUM of the WORLD_SURFACE height over a small square window (the feature's {@link
 * PowderRoofTrap#REFERENCE_WINDOW_RADIUS}). A cut slot column's low floor never raises that max; the
 * surrounding snowfield does -- so {@code windowedMax - ownSurface} is the shaft depth, fed straight into the
 * already-tested {@link PowderRoofTrap#isTrapCandidate(int, int)} depth gate. This class keeps that
 * sentinel-aware local maximum plus the independent physical trap-volume verifier. Both remain free of
 * Minecraft imports and unit-testable in a plain JVM; the MC-coupled glue (loaded-block reads, block-state
 * classification, and particle beacons) lives in {@code LatitudeDevCommands.markGlacial}.
 */
public final class GlacialMarkScan {

    private static final int OWNER_CHUNK_SIDE = 16;
    private static final int ESCAPE_HORIZONTAL_MARGIN = 3;
    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * Sentinel height for a column the scan could not read -- its chunk was not loaded, so we must NOT
     * force-generate it (that would silently change the world the tester is verifying). Chosen as {@link
     * Integer#MIN_VALUE}, far below any real block-Y (world floor is around -64), so a genuine height can
     * never collide with it and {@link #windowedMax} can skip it with a plain {@code != UNLOADED} test.
     */
    public static final int UNLOADED = Integer.MIN_VALUE;

    /** Legacy TEST127 floating-roof probe result; not sufficient to prove a TEST128 trap. */
    @Deprecated(forRemoval = false)
    public enum RoofProbeKind {
        NONE,
        POWDER,
        SHOULDER
    }

    private GlacialMarkScan() {
    }

    /**
     * Highest inclusive Y a physical adapter must sample above its highest powder cover.
     *
     * <p>A valid rising tail may end one block above that cover, retain a thin snow layer at {@code +2},
     * and require measured air at {@code +3}. The world ceiling is exclusive. Long arithmetic and integer
     * saturation keep malformed or extreme inputs from wrapping into a plausible low sample bound.
     */
    public static int physicalSampleMaxYInclusive(int maxCoverY, int worldMaxYExclusive) {
        long requiredTop = (long) maxCoverY + 3L;
        long worldTop = (long) worldMaxYExclusive - 1L;
        long bounded = Math.min(requiredTop, worldTop);
        return (int) Math.max(Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, bounded));
    }

    /**
     * One measured cell in the bounded block volume supplied to the physical trap scanner.
     *
     * <p>The enum deliberately describes safety-relevant block behaviour rather than Minecraft block
     * identities. The Minecraft-facing command is responsible for classifying each loaded block state:
     * fluids, gravity blocks, block entities, and unread cells must never be collapsed into {@link #AIR} or
     * {@link #DRY_SOLID}.
     */
    public enum PhysicalCellKind {
        AIR,
        PASSABLE_DRY,
        POWDER_SNOW,
        SNOW_BLOCK,
        SNOW_LAYER,
        DRY_SOLID,
        DRY_UNSTABLE,
        GRAVITY_SOLID,
        FLUID,
        BLOCK_ENTITY,
        UNLOADED
    }

    /**
     * Stable physical census returned by {@link #scanPhysicalTrapVolume(PhysicalCellKind[][][], int)}.
     *
     * <p>Cover, cushion, escape, and encounter counters include only candidates which pass every physical
     * check. Rejected powder components remain visible through {@link #candidates()},
     * {@link #partialComponents()}, {@link #unsafeComponents()}, and {@link #rejectionReasons()}.
     */
    public record PhysicalScanReport(
            int candidates,
            int validTraps,
            int encounters,
            int coverColumns,
            int cushionMatches,
            int validEscapeRoutes,
            int partialComponents,
            int unsafeComponents,
            Map<String, Integer> rejectionReasons) {

        public PhysicalScanReport {
            rejectionReasons = rejectionReasons == null
                    ? Map.of() : java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(rejectionReasons));
        }
    }

    private record SurfaceCover(int x, int y, int z) {
    }

    private record DropColumn(int x, int coverY, int z, int cushionY) {
    }

    private record WalkNode(int x, int feetY, int z) {
    }

    private record PhysicalRejection(String reason, boolean unsafe) {
    }

    private record DropCheck(List<DropColumn> drops, PhysicalRejection rejection) {
        DropCheck {
            drops = drops == null ? List.of() : List.copyOf(drops);
        }
    }

    private record EscapeCheck(boolean valid, PhysicalRejection rejection) {
    }

    /**
     * Independently prove concealed traps from a bounded snapshot of real block cells.
     *
     * <p>Candidate identity is a cardinally connected component of top-surface powder snow whose adjacent
     * covers differ by at most one block. Normal-snow gaps are intentionally absent from that component: they
     * are camouflage, not missing authored roof cells. Every powder cover must then have a clear fall of at
     * least {@code minimumDrop} blocks to a powder-snow cushion on dry, non-gravity support. Finally, a bounded
     * walking flood-fill must find a two-block-high cardinal route from the landing level, with at most one
     * block of rise per step, to a two- or three-column snow mining tail whose final three-block column reaches
     * an intact full-snow surface (optionally beneath a thin layer). The route may touch the fall volume only
     * at its landing doorway.
     *
     * <p>No generator plan, authored rectangle, shoulder list, or route metadata participates in the result.
     * Out-of-volume and ragged cells read as {@link PhysicalCellKind#UNLOADED}; candidates too close to that
     * boundary are rejected rather than speculatively accepted. Escape search is restricted to the component
     * bounding box plus {@value #ESCAPE_HORIZONTAL_MARGIN} cells, keeping repeated scans bounded.
     *
     * @param cells measured cells indexed {@code [x][y][z]}, with Y increasing upward
     * @param minimumDrop minimum uninterrupted passable cells between cover and cushion
     */
    public static PhysicalScanReport scanPhysicalTrapVolume(
            PhysicalCellKind[][][] cells, int minimumDrop) {
        return scanPhysicalTrapVolume(cells, minimumDrop, null);
    }

    /**
     * Scan only the measured surface-powder component containing one local X/Z anchor.
     *
     * <p>This is the bounded command adapter entry point: callers may discover each component once in a large
     * two-dimensional loaded-chunk census, then provide a small full-height block volume around that component.
     * Other powder components inside the required physical halo remain visible as terrain and hazards, but
     * cannot be double-counted as additional candidates in this invocation.
     */
    public static PhysicalScanReport scanPhysicalTrapVolumeAt(
            PhysicalCellKind[][][] cells, int minimumDrop, int anchorX, int anchorZ) {
        return scanPhysicalTrapVolume(cells, minimumDrop, new int[]{anchorX, anchorZ});
    }

    private static PhysicalScanReport scanPhysicalTrapVolume(
            PhysicalCellKind[][][] cells, int minimumDrop, int[] anchor) {
        if (minimumDrop < 1) {
            throw new IllegalArgumentException("minimumDrop must be positive");
        }
        VolumeBounds bounds = VolumeBounds.of(cells);
        if (bounds.xSize() == 0 || bounds.ySize() == 0 || bounds.zSize() == 0) {
            return new PhysicalScanReport(0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        }

        // Physical Y remains the only authority for drops and exits. snowfieldY removes only a certified thin
        // layer's cosmetic block of relief, and is consumed solely by the immediate camouflage check below.
        int[][] surfaceY = new int[bounds.xSize()][bounds.zSize()];
        int[][] snowfieldY = new int[bounds.xSize()][bounds.zSize()];
        PhysicalCellKind[][] surfaceKind =
                new PhysicalCellKind[bounds.xSize()][bounds.zSize()];
        boolean[][] powderSurface = new boolean[bounds.xSize()][bounds.zSize()];
        for (int x = 0; x < bounds.xSize(); x++) {
            for (int z = 0; z < bounds.zSize(); z++) {
                surfaceY[x][z] = UNLOADED;
                snowfieldY[x][z] = UNLOADED;
                for (int y = bounds.ySize() - 1; y >= 0; y--) {
                    PhysicalCellKind kind = physicalCellAt(cells, x, y, z);
                    if (isPassable(kind)) {
                        continue;
                    }
                    surfaceY[x][z] = y;
                    surfaceKind[x][z] = kind;
                    powderSurface[x][z] = kind == PhysicalCellKind.POWDER_SNOW;
                    snowfieldY[x][z] = kind == PhysicalCellKind.SNOW_LAYER
                                    && isDryStableSupport(
                                            physicalCellAt(cells, x, y - 1, z))
                            ? y - 1 : y;
                    break;
                }
            }
        }

        List<List<SurfaceCover>> components =
                connectedSurfacePowderComponents(powderSurface, surfaceY);
        if (anchor != null) {
            components = components.stream()
                    .filter(component -> component.stream().anyMatch(
                            cover -> cover.x() == anchor[0] && cover.z() == anchor[1]))
                    .toList();
        }
        int validTraps = 0;
        int coverColumns = 0;
        int cushionMatches = 0;
        int validEscapeRoutes = 0;
        int partialComponents = 0;
        int unsafeComponents = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();

        for (List<SurfaceCover> component : components) {
            PhysicalRejection rejection = camouflageRejection(
                    cells, bounds, component, surfaceY, snowfieldY, surfaceKind);
            List<DropColumn> drops = List.of();
            if (rejection == null) {
                DropCheck dropCheck = checkDrops(cells, component, minimumDrop);
                rejection = dropCheck.rejection();
                drops = dropCheck.drops();
            }
            if (rejection == null) {
                EscapeCheck escape = checkEscape(cells, bounds, component, drops, surfaceY);
                rejection = escape.rejection();
                if (escape.valid()) {
                    validTraps++;
                    coverColumns += component.size();
                    cushionMatches += drops.size();
                    validEscapeRoutes++;
                    continue;
                }
            }

            if (rejection == null) {
                rejection = new PhysicalRejection("MISSING_ESCAPE", false);
            }
            reasons.merge(rejection.reason(), 1, Integer::sum);
            if (rejection.unsafe()) {
                unsafeComponents++;
            } else {
                partialComponents++;
            }
        }

        return new PhysicalScanReport(
                components.size(),
                validTraps,
                validTraps,
                coverColumns,
                cushionMatches,
                validEscapeRoutes,
                partialComponents,
                unsafeComponents,
                reasons);
    }

    private record VolumeBounds(int xSize, int ySize, int zSize) {
        static VolumeBounds of(PhysicalCellKind[][][] cells) {
            if (cells == null) {
                return new VolumeBounds(0, 0, 0);
            }
            int ySize = 0;
            int zSize = 0;
            for (PhysicalCellKind[][] ys : cells) {
                if (ys == null) {
                    continue;
                }
                ySize = Math.max(ySize, ys.length);
                for (PhysicalCellKind[] zs : ys) {
                    if (zs != null) {
                        zSize = Math.max(zSize, zs.length);
                    }
                }
            }
            return new VolumeBounds(cells.length, ySize, zSize);
        }
    }

    private static List<List<SurfaceCover>> connectedSurfacePowderComponents(
            boolean[][] powderSurface, int[][] surfaceY) {
        boolean[][] seen = new boolean[powderSurface.length][];
        for (int x = 0; x < powderSurface.length; x++) {
            seen[x] = new boolean[powderSurface[x].length];
        }
        List<List<SurfaceCover>> result = new ArrayList<>();
        for (int x = 0; x < powderSurface.length; x++) {
            for (int z = 0; z < powderSurface[x].length; z++) {
                if (!powderSurface[x][z] || seen[x][z]) {
                    continue;
                }
                List<SurfaceCover> component = new ArrayList<>();
                ArrayDeque<SurfaceCover> queue = new ArrayDeque<>();
                queue.addLast(new SurfaceCover(x, surfaceY[x][z], z));
                seen[x][z] = true;
                while (!queue.isEmpty()) {
                    SurfaceCover cover = queue.removeFirst();
                    component.add(cover);
                    for (int[] direction : CARDINAL_DIRECTIONS) {
                        int nx = cover.x() + direction[0];
                        int nz = cover.z() + direction[1];
                        if (nx < 0 || nx >= powderSurface.length
                                || nz < 0 || nz >= powderSurface[nx].length
                                || seen[nx][nz] || !powderSurface[nx][nz]
                                || Math.abs(surfaceY[nx][nz] - cover.y()) > 1) {
                            continue;
                        }
                        seen[nx][nz] = true;
                        queue.addLast(new SurfaceCover(nx, surfaceY[nx][nz], nz));
                    }
                }
                result.add(List.copyOf(component));
            }
        }
        return List.copyOf(result);
    }

    private static PhysicalRejection camouflageRejection(
            PhysicalCellKind[][][] cells,
            VolumeBounds bounds,
            List<SurfaceCover> component,
            int[][] surfaceY,
            int[][] snowfieldY,
            PhysicalCellKind[][] surfaceKind) {
        int minX = component.stream().mapToInt(SurfaceCover::x).min().orElse(0);
        int maxX = component.stream().mapToInt(SurfaceCover::x).max().orElse(0);
        int minZ = component.stream().mapToInt(SurfaceCover::z).min().orElse(0);
        int maxZ = component.stream().mapToInt(SurfaceCover::z).max().orElse(0);
        int minY = component.stream().mapToInt(SurfaceCover::y).min().orElse(UNLOADED);
        int maxY = component.stream().mapToInt(SurfaceCover::y).max().orElse(UNLOADED);

        if (minX - ESCAPE_HORIZONTAL_MARGIN < 0
                || minZ - ESCAPE_HORIZONTAL_MARGIN < 0
                || maxX + ESCAPE_HORIZONTAL_MARGIN >= bounds.xSize()
                || maxZ + ESCAPE_HORIZONTAL_MARGIN >= bounds.zSize()
                || minY <= 1 || maxY >= bounds.ySize() - 1) {
            return new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false);
        }
        if (maxY - minY > 1) {
            return new PhysicalRejection("SURFACE_RELIEF_TOO_HIGH", false);
        }

        Set<Long> componentColumns = new HashSet<>();
        for (SurfaceCover cover : component) {
            componentColumns.add(packHorizontal(cover.x(), cover.z()));
        }
        // Visual camouflage is local: the powder component plus exactly its Chebyshev-1 snowfield collar.
        // Deep or dramatic terrain two and three columns away remains relevant to route safety, not silhouette.
        Set<Long> collarColumns = new HashSet<>();
        for (SurfaceCover cover : component) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int x = cover.x() + dx;
                    int z = cover.z() + dz;
                    long key = packHorizontal(x, z);
                    if (!componentColumns.contains(key)) {
                        collarColumns.add(key);
                    }
                }
            }
        }

        boolean ordinarySnowSeen = false;
        for (long key : collarColumns) {
            int x = (int) (key >> 32);
            int z = (int) key;
            if (x < 0 || x >= bounds.xSize() || z < 0 || z >= bounds.zSize()) {
                return new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false);
            }
            int measuredY = surfaceY[x][z];
            PhysicalCellKind kind = surfaceKind[x][z];
            if (measuredY == UNLOADED || kind == null
                    || kind == PhysicalCellKind.UNLOADED) {
                return new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false);
            }
            if (kind == PhysicalCellKind.SNOW_LAYER
                    && isDryStableSupport(
                            physicalCellAt(cells, x, measuredY - 1, z))) {
                ordinarySnowSeen = true;
                continue;
            }
            if (kind == PhysicalCellKind.SNOW_BLOCK) {
                ordinarySnowSeen = true;
                continue;
            }
            if (kind != PhysicalCellKind.POWDER_SNOW) {
                return new PhysicalRejection("SNOWFIELD_CAMOUFLAGE_MISMATCH", false);
            }
        }
        if (!ordinarySnowSeen) {
            return new PhysicalRejection("SNOWFIELD_CAMOUFLAGE_MISMATCH", false);
        }

        Set<Long> snowfieldColumns = new HashSet<>(componentColumns);
        snowfieldColumns.addAll(collarColumns);
        int minEffectiveY = Integer.MAX_VALUE;
        int maxEffectiveY = Integer.MIN_VALUE;
        for (long key : snowfieldColumns) {
            int x = (int) (key >> 32);
            int z = (int) key;
            int effectiveY = snowfieldY[x][z];
            if (effectiveY == UNLOADED) {
                return new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false);
            }
            minEffectiveY = Math.min(minEffectiveY, effectiveY);
            maxEffectiveY = Math.max(maxEffectiveY, effectiveY);
        }
        if (maxEffectiveY - minEffectiveY > 3) {
            return new PhysicalRejection("SURFACE_RELIEF_TOO_HIGH", false);
        }
        for (long key : snowfieldColumns) {
            int x = (int) (key >> 32);
            int z = (int) key;
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (snowfieldColumns.contains(packHorizontal(nx, nz))
                        && Math.abs(snowfieldY[nx][nz] - snowfieldY[x][z]) > 1) {
                    return new PhysicalRejection("SURFACE_RELIEF_TOO_HIGH", false);
                }
            }
        }
        return null;
    }

    private static DropCheck checkDrops(
            PhysicalCellKind[][][] cells,
            List<SurfaceCover> component,
            int minimumDrop) {
        List<DropColumn> drops = new ArrayList<>();
        for (SurfaceCover cover : component) {
            boolean landed = false;
            for (int y = cover.y() - 1; y >= 0; y--) {
                PhysicalCellKind kind = physicalCellAt(cells, cover.x(), y, cover.z());
                if (isPassable(kind)) {
                    continue;
                }
                if (kind == PhysicalCellKind.UNLOADED) {
                    return new DropCheck(drops,
                            new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false));
                }
                if (kind == PhysicalCellKind.FLUID) {
                    return new DropCheck(drops,
                            new PhysicalRejection("FLUID_IN_FALL", true));
                }
                if (kind == PhysicalCellKind.BLOCK_ENTITY) {
                    return new DropCheck(drops,
                            new PhysicalRejection("BLOCK_ENTITY_IN_FALL", true));
                }
                if (kind == PhysicalCellKind.GRAVITY_SOLID) {
                    return new DropCheck(drops,
                            new PhysicalRejection("GRAVITY_IN_FALL", true));
                }
                if (kind != PhysicalCellKind.POWDER_SNOW) {
                    String reason = cover.y() - y - 1 >= minimumDrop
                            && isDryStableSupport(kind)
                                    ? "MISSING_CUSHION" : "OBSTRUCTED_SHAFT";
                    return new DropCheck(drops, new PhysicalRejection(reason, false));
                }
                if (cover.y() - y - 1 < minimumDrop) {
                    return new DropCheck(drops,
                            new PhysicalRejection("DROP_TOO_SHALLOW", false));
                }
                PhysicalCellKind support =
                        physicalCellAt(cells, cover.x(), y - 1, cover.z());
                if (!isDryStableSupport(support)) {
                    return new DropCheck(drops,
                            new PhysicalRejection("UNSAFE_CUSHION_SUPPORT", true));
                }
                drops.add(new DropColumn(cover.x(), cover.y(), cover.z(), y));
                landed = true;
                break;
            }
            if (!landed) {
                return new DropCheck(drops,
                        new PhysicalRejection("MISSING_CUSHION", false));
            }
        }
        return new DropCheck(drops, null);
    }

    private static EscapeCheck checkEscape(
            PhysicalCellKind[][][] cells,
            VolumeBounds bounds,
            List<SurfaceCover> component,
            List<DropColumn> drops,
            int[][] surfaceY) {
        int minX = component.stream().mapToInt(SurfaceCover::x).min().orElse(0)
                - ESCAPE_HORIZONTAL_MARGIN;
        int maxX = component.stream().mapToInt(SurfaceCover::x).max().orElse(0)
                + ESCAPE_HORIZONTAL_MARGIN;
        int minZ = component.stream().mapToInt(SurfaceCover::z).min().orElse(0)
                - ESCAPE_HORIZONTAL_MARGIN;
        int maxZ = component.stream().mapToInt(SurfaceCover::z).max().orElse(0)
                + ESCAPE_HORIZONTAL_MARGIN;
        if (minX < 0 || minZ < 0 || maxX >= bounds.xSize() || maxZ >= bounds.zSize()) {
            return new EscapeCheck(false,
                    new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false));
        }

        Map<Long, DropColumn> dropByColumn = new LinkedHashMap<>();
        for (DropColumn drop : drops) {
            dropByColumn.put(packHorizontal(drop.x(), drop.z()), drop);
        }
        ArrayDeque<WalkNode> queue = new ArrayDeque<>();
        Set<WalkNode> visited = new HashSet<>();
        for (DropColumn drop : drops) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int x = drop.x() + direction[0];
                int z = drop.z() + direction[1];
                if (dropByColumn.containsKey(packHorizontal(x, z))) {
                    continue;
                }
                for (int feetY = drop.cushionY() - 1;
                        feetY <= drop.cushionY() + 1; feetY++) {
                    WalkNode entry = new WalkNode(x, feetY, z);
                    if (insideEscapeBounds(entry, minX, maxX, minZ, maxZ, bounds.ySize())
                            && isWalkNode(cells, entry)
                            && visited.add(entry)) {
                        queue.addLast(entry);
                    }
                }
            }
        }
        if (queue.isEmpty()) {
            return new EscapeCheck(false,
                    new PhysicalRejection("MISSING_ESCAPE", false));
        }

        while (!queue.isEmpty()) {
            WalkNode current = queue.removeFirst();
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int nx = current.x() + direction[0];
                int nz = current.z() + direction[1];
                if (dropByColumn.containsKey(packHorizontal(nx, nz))) {
                    continue;
                }
                for (int deltaY : new int[]{0, 1, -1}) {
                    WalkNode next = new WalkNode(nx, current.feetY() + deltaY, nz);
                    if (!insideEscapeBounds(
                            next, minX, maxX, minZ, maxZ, bounds.ySize())
                            || !isWalkNode(cells, next) || !visited.add(next)) {
                        continue;
                    }
                    queue.addLast(next);
                }
            }
        }

        Set<Long> doorwayColumns = new HashSet<>();
        for (WalkNode node : visited) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                DropColumn adjacent = dropByColumn.get(packHorizontal(
                        node.x() + direction[0], node.z() + direction[1]));
                if (adjacent == null) {
                    continue;
                }
                if (node.feetY() == adjacent.cushionY()
                        || node.feetY() == adjacent.cushionY() + 1) {
                    doorwayColumns.add(packHorizontal(node.x(), node.z()));
                } else if (node.feetY() > adjacent.cushionY() + 1
                        && node.feetY() < adjacent.coverY()) {
                    return new EscapeCheck(false,
                            new PhysicalRejection("ESCAPE_SHORTCUT_TO_FALL", true));
                }
            }
        }
        if (doorwayColumns.size() != 1) {
            return new EscapeCheck(false,
                    new PhysicalRejection(
                            doorwayColumns.isEmpty()
                                    ? "MISSING_ESCAPE" : "ESCAPE_SHORTCUT_TO_FALL",
                            doorwayColumns.size() > 1));
        }

        boolean reachedSurfaceTailZone = false;
        for (WalkNode node : visited) {
            int localSurfaceY = surfaceY[node.x()][node.z()];
            if (localSurfaceY != UNLOADED
                    && localSurfaceY - node.feetY() >= 0
                    && localSurfaceY - node.feetY() <= 4) {
                reachedSurfaceTailZone = true;
            }
        }
        TailEvidence tail = new TailEvidence();
        for (WalkNode node : visited) {
            if (searchMineableTail(
                    cells, surfaceY, dropByColumn, node, 0, new HashSet<>(), tail)) {
                return new EscapeCheck(true, null);
            }
        }
        if (tail.brokenPlug) {
            return new EscapeCheck(false,
                    new PhysicalRejection("OPEN_SURFACE_PLUG", false));
        }
        return new EscapeCheck(false, new PhysicalRejection(
                reachedSurfaceTailZone || tail.started
                        ? "MISSING_ESCAPE_TAIL" : "MISSING_ESCAPE",
                false));
    }

    private static final class TailEvidence {
        private boolean started;
        private boolean brokenPlug;
    }

    /**
     * Virtually mine two or three cardinal snow columns. Each column is three blocks tall and the next base
     * rises by one, matching the physical staircase a player can open with ordinary mining.
     */
    private static boolean searchMineableTail(
            PhysicalCellKind[][][] cells,
            int[][] surfaceY,
            Map<Long, DropColumn> dropByColumn,
            WalkNode previous,
            int length,
            Set<Long> usedColumns,
            TailEvidence evidence) {
        int nextFeetY = length == 0 ? previous.feetY() : previous.feetY() + 1;
        for (int[] direction : CARDINAL_DIRECTIONS) {
            int nextX = previous.x() + direction[0];
            int nextZ = previous.z() + direction[1];
            long key = packHorizontal(nextX, nextZ);
            if (usedColumns.contains(key) || dropByColumn.containsKey(key)) {
                continue;
            }
            if (!isMineableSnowColumn(cells, nextX, nextFeetY, nextZ)) {
                evidence.brokenPlug |= isOpenedSurfaceTailColumn(
                        cells, surfaceY, nextX, nextFeetY, nextZ);
                continue;
            }
            evidence.started = true;
            usedColumns.add(key);
            WalkNode mined = new WalkNode(nextX, nextFeetY, nextZ);
            int nextLength = length + 1;
            if (nextLength >= 2
                    && isIntactSurfaceTailColumn(cells, surfaceY, mined)) {
                return true;
            }
            if (nextLength < 3 && searchMineableTail(
                    cells, surfaceY, dropByColumn, mined, nextLength, usedColumns, evidence)) {
                return true;
            }
            usedColumns.remove(key);
        }
        return false;
    }

    private static boolean isMineableSnowColumn(
            PhysicalCellKind[][][] cells, int x, int feetY, int z) {
        return isDryStableSupport(physicalCellAt(cells, x, feetY - 1, z))
                && physicalCellAt(cells, x, feetY, z) == PhysicalCellKind.SNOW_BLOCK
                && physicalCellAt(cells, x, feetY + 1, z) == PhysicalCellKind.SNOW_BLOCK
                && physicalCellAt(cells, x, feetY + 2, z) == PhysicalCellKind.SNOW_BLOCK;
    }

    private static boolean isIntactSurfaceTailColumn(
            PhysicalCellKind[][][] cells,
            int[][] surfaceY,
            WalkNode tailEnd) {
        int surface = measuredSurfaceAt(surfaceY, tailEnd.x(), tailEnd.z());
        int headY = tailEnd.feetY() + 2;
        PhysicalCellKind above = physicalCellAt(
                cells, tailEnd.x(), headY + 1, tailEnd.z());
        if (surface == headY) {
            return isPassable(above);
        }
        return surface == headY + 1
                && above == PhysicalCellKind.SNOW_LAYER
                && isPassable(physicalCellAt(
                        cells, tailEnd.x(), headY + 2, tailEnd.z()));
    }

    private static boolean isOpenedSurfaceTailColumn(
            PhysicalCellKind[][][] cells,
            int[][] surfaceY,
            int x,
            int feetY,
            int z) {
        if (!isDryStableSupport(physicalCellAt(cells, x, feetY - 1, z))
                || physicalCellAt(cells, x, feetY, z) != PhysicalCellKind.SNOW_BLOCK
                || physicalCellAt(cells, x, feetY + 1, z) != PhysicalCellKind.SNOW_BLOCK
                || !isPassable(physicalCellAt(cells, x, feetY + 2, z))) {
            return false;
        }
        int surface = measuredSurfaceAt(surfaceY, x, z);
        return surface == feetY + 1
                || (surface == feetY + 3
                        && physicalCellAt(cells, x, feetY + 3, z)
                                == PhysicalCellKind.SNOW_LAYER);
    }

    private static int measuredSurfaceAt(int[][] surfaceY, int x, int z) {
        if (x < 0 || x >= surfaceY.length
                || z < 0 || z >= surfaceY[x].length) {
            return UNLOADED;
        }
        return surfaceY[x][z];
    }

    private static boolean insideEscapeBounds(
            WalkNode node,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int ySize) {
        return node.x() >= minX && node.x() <= maxX
                && node.z() >= minZ && node.z() <= maxZ
                && node.feetY() > 0 && node.feetY() + 1 < ySize;
    }

    private static boolean isWalkNode(PhysicalCellKind[][][] cells, WalkNode node) {
        return isPassable(
                        physicalCellAt(cells, node.x(), node.feetY(), node.z()))
                && isPassable(
                        physicalCellAt(cells, node.x(), node.feetY() + 1, node.z()))
                && isDryStableSupport(
                        physicalCellAt(cells, node.x(), node.feetY() - 1, node.z()));
    }

    private static boolean isPassable(PhysicalCellKind kind) {
        return kind == PhysicalCellKind.AIR
                || kind == PhysicalCellKind.PASSABLE_DRY;
    }

    private static boolean isDryStableSupport(PhysicalCellKind kind) {
        return kind == PhysicalCellKind.SNOW_BLOCK
                || kind == PhysicalCellKind.DRY_SOLID;
    }

    private static PhysicalCellKind physicalCellAt(
            PhysicalCellKind[][][] cells, int x, int y, int z) {
        if (cells == null || x < 0 || x >= cells.length || cells[x] == null
                || y < 0 || y >= cells[x].length || cells[x][y] == null
                || z < 0 || z >= cells[x][y].length
                || cells[x][y][z] == null) {
            return PhysicalCellKind.UNLOADED;
        }
        return cells[x][y][z];
    }

    private static long packHorizontal(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Classify one measured roof block under the exact generator contracts. Powder remains the stricter proof:
     * it needs two air blocks beneath the roof probe. A solid authored shoulder needs one air block, matching
     * the generator's two-block opening floor. Supported natural snow therefore remains ordinary terrain.
     */
    @Deprecated(forRemoval = false)
    public static RoofProbeKind classifyFloatingRoof(boolean powderSnow, boolean solidSnow,
            boolean firstBelowAir, boolean secondBelowAir) {
        if (powderSnow == solidSnow || !firstBelowAir) {
            return RoofProbeKind.NONE;
        }
        if (powderSnow) {
            return secondBelowAir ? RoofProbeKind.POWDER : RoofProbeKind.NONE;
        }
        return RoofProbeKind.SHOULDER;
    }

    /**
     * May a neighboring column provide natural approach-bank evidence? A selected cover cell is not a bank,
     * and neither is any other physically detected floating powder/solid roof. The scanner uses zero as its
     * "no floating roof measured" sentinel; only ordinary terrain may contribute height or walkable contact.
     */
    @Deprecated(forRemoval = false)
    public static boolean naturalBankEvidenceEligible(
            boolean selectedCoverCell, int detectedFloatingRoofY) {
        return !selectedCoverCell && detectedFloatingRoofY == 0;
    }

    /** Same physical-terrain gate plus the shared one-step walkable-height requirement. */
    @Deprecated(forRemoval = false)
    public static boolean naturalWalkableBankContactEligible(boolean selectedCoverCell,
            int detectedFloatingRoofY, int bankFirstAir, int coverFirstAir) {
        return naturalBankEvidenceEligible(selectedCoverCell, detectedFloatingRoofY)
                && Math.abs((long) bankFirstAir - coverFirstAir)
                        <= PowderRoofTrap.MAX_APPROACH_SURFACE_OFFSET;
    }

    /**
     * The local snowfield reference: the MAXIMUM value over the square window of Chebyshev {@code radius}
     * centred on grid cell ({@code cx}, {@code cz}), skipping {@link #UNLOADED} cells and clamping the window
     * to the grid's bounds. This is {@code world.PowderCrevasseRoofFeature}'s per-column reference (windowed
     * WORLD_SURFACE max) generalised to a cross-chunk grid with holes: the feature clamps its window to the
     * decorating chunk's 16x16, whereas a ground-truth detector spanning several chunks sees the true local
     * snowfield across chunk borders (an intentional fidelity gain, not a reinvention -- the window shape and
     * radius are the feature's).
     *
     * <p>Returns {@link #UNLOADED} iff no in-window cell holds a real height (an all-unloaded neighbourhood,
     * an empty grid, or a negative radius -- all degrade to "no reference here" rather than throwing). A
     * {@code radius} of 0 returns the centre cell's own value (or {@link #UNLOADED} if the centre is out of
     * bounds or unloaded); the centre itself does not need to be loaded for a neighbour to supply the max.
     * Pure and allocation-free; only array reads.
     *
     * @param grid    row-major surface heights, {@code grid[x][z]}; ragged rows are tolerated (each row's own
     *                length bounds its z-scan) and {@link #UNLOADED} marks unread columns
     * @param cx      centre X index into {@code grid}
     * @param cz      centre Z index into the row
     * @param radius  Chebyshev half-extent of the window (>= 0; a negative radius yields {@link #UNLOADED})
     */
    public static int windowedMax(int[][] grid, int cx, int cz, int radius) {
        if (grid == null || grid.length == 0 || radius < 0) {
            return UNLOADED;
        }
        int max = UNLOADED;
        int xLo = Math.max(0, cx - radius);
        int xHi = Math.min(grid.length - 1, cx + radius);
        for (int x = xLo; x <= xHi; x++) {
            int[] row = grid[x];
            if (row == null) {
                continue;
            }
            int zLo = Math.max(0, cz - radius);
            int zHi = Math.min(row.length - 1, cz + radius);
            for (int z = zLo; z <= zHi; z++) {
                int v = row[z];
                if (v != UNLOADED && v > max) {
                    max = v;
                }
            }
        }
        return max;
    }

    /**
     * Four-connected components in an arbitrary rectangular/ragged mask. This turns powder ROOF COLUMNS into
     * honest trap ENCOUNTERS for {@code markGlacial}; counting every block in a 32-block cap as 32 traps made
     * the incidence readout useless. Components and cells use deterministic x-major discovery order.
     */
    public static List<List<int[]>> connectedComponents(boolean[][] mask) {
        List<List<int[]>> components = new ArrayList<>();
        if (mask == null || mask.length == 0) {
            return components;
        }
        boolean[][] seen = new boolean[mask.length][];
        for (int x = 0; x < mask.length; x++) {
            seen[x] = mask[x] == null ? new boolean[0] : new boolean[mask[x].length];
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 0; x < mask.length; x++) {
            if (mask[x] == null) {
                continue;
            }
            for (int z = 0; z < mask[x].length; z++) {
                if (!mask[x][z] || seen[x][z]) {
                    continue;
                }
                List<int[]> component = new ArrayList<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.addLast(new int[]{x, z});
                seen[x][z] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    component.add(cell);
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int nz = cell[1] + direction[1];
                        if (nx >= 0 && nx < mask.length && mask[nx] != null
                                && nz >= 0 && nz < mask[nx].length
                                && mask[nx][nz] && !seen[nx][nz]) {
                            seen[nx][nz] = true;
                            queue.addLast(new int[]{nx, nz});
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }

    /**
     * Four-connected components whose cells must also share one integer value. A powder roof at Y=90 that
     * touches another roof at Y=91 is two physical caps, not one malformed mixed-height cap; this keeps the
     * S36 verifier honest without allowing a single encounter to step between planes. Cells with no matching
     * value-grid entry, or the {@link #UNLOADED} sentinel, are ignored. Discovery remains deterministic and
     * x-major, like {@link #connectedComponents(boolean[][])}.
     */
    public static List<List<int[]>> connectedComponentsByValue(boolean[][] mask, int[][] values) {
        List<List<int[]>> components = new ArrayList<>();
        if (mask == null || values == null || mask.length == 0) {
            return components;
        }
        boolean[][] seen = new boolean[mask.length][];
        for (int x = 0; x < mask.length; x++) {
            seen[x] = mask[x] == null ? new boolean[0] : new boolean[mask[x].length];
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 0; x < mask.length; x++) {
            if (mask[x] == null) {
                continue;
            }
            for (int z = 0; z < mask[x].length; z++) {
                if (!mask[x][z] || seen[x][z] || !hasValue(values, x, z)
                        || values[x][z] == UNLOADED) {
                    continue;
                }
                int componentValue = values[x][z];
                List<int[]> component = new ArrayList<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.addLast(new int[]{x, z});
                seen[x][z] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    component.add(cell);
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int nz = cell[1] + direction[1];
                        if (nx >= 0 && nx < mask.length && mask[nx] != null
                                && nz >= 0 && nz < mask[nx].length
                                && mask[nx][nz] && !seen[nx][nz]
                                && hasValue(values, nx, nz) && values[nx][nz] == componentValue) {
                            seen[nx][nz] = true;
                            queue.addLast(new int[]{nx, nz});
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }

    /** One physical same-plane cover, seeded by powder and split into hazardous and camouflage cells. */
    @Deprecated(forRemoval = false)
    public record ConcealedRoofComponent(List<int[]> powder, List<int[]> shoulders, List<int[]> cover) {
        public ConcealedRoofComponent {
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

    private record RoofOptions(List<int[]> powder, List<ConcealedRoofComponent> candidates) {
        RoofOptions {
            powder = immutableCells(powder);
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /**
     * Enumerate bounded physical encounters under the generator's owner-chunk law. Powder components, not the
     * combined powder+shoulder cover, own encounter identity. This is essential after the deep-first revision:
     * shallow solid continuations may touch two otherwise independent crevasses and must not merge them. Each
     * powder component is reconstructed from its exact physical powder/solid partition on its measured roof
     * plane and owner tile. Every long-side station must terminate at a known ordinary in-owner cell; another
     * floating roof, an unread cell, or the owner edge is never bank evidence. A connected same-plane powder
     * collision is rejected rather than split speculatively. Optional shoulder alternatives are first assigned
     * at the mandatory three-station size, then expanded to four only where they remain disjoint.
     */
    @Deprecated(forRemoval = false)
    public static List<ConcealedRoofComponent> concealedRoofComponents(
            boolean[][] powderMask, boolean[][] shoulderMask, int[][] roofY) {
        List<RoofOptions> groups = new ArrayList<>();
        if (powderMask == null || shoulderMask == null || roofY == null || powderMask.length == 0) {
            return List.of();
        }
        boolean[][] seenPowder = new boolean[powderMask.length][];
        for (int x = 0; x < powderMask.length; x++) {
            seenPowder[x] = powderMask[x] == null ? new boolean[0] : new boolean[powderMask[x].length];
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 0; x < powderMask.length; x++) {
            if (powderMask[x] == null) {
                continue;
            }
            for (int z = 0; z < powderMask[x].length; z++) {
                if (!powderMask[x][z] || seenPowder[x][z] || !hasValue(roofY, x, z)
                        || roofY[x][z] == UNLOADED) {
                    continue;
                }
                int plane = roofY[x][z];
                int ownerMinX = x / OWNER_CHUNK_SIDE * OWNER_CHUNK_SIDE;
                int ownerMinZ = z / OWNER_CHUNK_SIDE * OWNER_CHUNK_SIDE;
                int ownerMaxX = ownerMinX + OWNER_CHUNK_SIDE - 1;
                int ownerMaxZ = ownerMinZ + OWNER_CHUNK_SIDE - 1;
                List<int[]> powder = new ArrayList<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.addLast(new int[]{x, z});
                seenPowder[x][z] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    powder.add(cell);
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int nz = cell[1] + direction[1];
                        if (nx < ownerMinX || nx > ownerMaxX || nz < ownerMinZ || nz > ownerMaxZ
                                || !hasMaskCell(powderMask, nx, nz) || seenPowder[nx][nz]
                                || !powderMask[nx][nz]
                                || !hasValue(roofY, nx, nz) || roofY[nx][nz] != plane) {
                            continue;
                        }
                        seenPowder[nx][nz] = true;
                        queue.addLast(new int[]{nx, nz});
                    }
                }

                List<int[]> localPowder = new ArrayList<>();
                for (int[] cell : powder) {
                    localPowder.add(new int[]{cell[0] - ownerMinX, cell[1] - ownerMinZ});
                }
                boolean[][] localShoulder = new boolean[OWNER_CHUNK_SIDE][OWNER_CHUNK_SIDE];
                boolean[][] localFloating = new boolean[OWNER_CHUNK_SIDE][OWNER_CHUNK_SIDE];
                boolean[][] localKnown = new boolean[OWNER_CHUNK_SIDE][OWNER_CHUNK_SIDE];
                for (int lx = 0; lx < OWNER_CHUNK_SIDE; lx++) {
                    for (int lz = 0; lz < OWNER_CHUNK_SIDE; lz++) {
                        int wx = ownerMinX + lx;
                        int wz = ownerMinZ + lz;
                        boolean known = hasValue(roofY, wx, wz)
                                && roofY[wx][wz] != UNLOADED;
                        localKnown[lx][lz] = known;
                        if (!known) {
                            continue;
                        }
                        localFloating[lx][lz] = (hasMaskCell(powderMask, wx, wz)
                                && powderMask[wx][wz])
                                || (hasMaskCell(shoulderMask, wx, wz)
                                && shoulderMask[wx][wz]);
                        if (roofY[wx][wz] != plane) {
                            continue;
                        }
                        if (hasMaskCell(shoulderMask, wx, wz) && shoulderMask[wx][wz]) {
                            localShoulder[lx][lz] = true;
                        }
                    }
                }

                List<ConcealedRoofComponent> options = new ArrayList<>();
                Set<String> optionKeys = new HashSet<>();
                for (PowderRoofTrap.ConcealedSegment segment :
                        PowderRoofTrap.physicalContouredSegmentCandidates(
                                localPowder, localShoulder, localFloating, localKnown)) {
                    if (!optionKeys.add(segmentKey(segment))) {
                        continue;
                    }
                    options.add(new ConcealedRoofComponent(
                            toWorldCells(segment.powder(), ownerMinX, ownerMinZ),
                            toWorldCells(segment.shoulders(), ownerMinX, ownerMinZ),
                            toWorldCells(segment.cover(), ownerMinX, ownerMinZ)));
                }
                options.sort(Comparator.comparingInt((ConcealedRoofComponent option) -> option.cover().size())
                        .thenComparingInt(option -> option.shoulders().size()));
                groups.add(new RoofOptions(powder, options));
            }
        }

        // First reserve the smallest legal (three-station) disjoint forms, maximizing recoverable encounters.
        List<ConcealedRoofComponent> selected = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        for (RoofOptions group : groups) {
            ConcealedRoofComponent choice = group.candidates().stream()
                    .filter(candidate -> disjoint(candidate.cover(), claimed))
                    .findFirst().orElse(null);
            if (choice == null) {
                selected.add(new ConcealedRoofComponent(group.powder(), List.of(), group.powder()));
                continue;
            }
            selected.add(choice);
            addCells(claimed, choice.cover());
        }

        // Then recover optional fourth stations wherever doing so cannot consume another encounter's cells.
        for (int i = 0; i < groups.size(); i++) {
            ConcealedRoofComponent current = selected.get(i);
            if (current.shoulders().isEmpty()) {
                continue;
            }
            removeCells(claimed, current.cover());
            ConcealedRoofComponent expanded = groups.get(i).candidates().stream()
                    .sorted(Comparator.comparingInt((ConcealedRoofComponent option) -> option.cover().size())
                            .reversed())
                    .filter(candidate -> disjoint(candidate.cover(), claimed))
                    .findFirst().orElse(current);
            selected.set(i, expanded);
            addCells(claimed, expanded.cover());
        }
        return List.copyOf(selected);
    }

    private static String segmentKey(PowderRoofTrap.ConcealedSegment segment) {
        List<String> powder = segment.powder().stream()
                .map(cell -> cell[0] + "," + cell[1]).sorted().toList();
        List<String> shoulders = segment.shoulders().stream()
                .map(cell -> cell[0] + "," + cell[1]).sorted().toList();
        return String.join(";", powder) + "|" + String.join(";", shoulders);
    }

    /** Scanner and generator share the exact variable 12..16-station geometry law. */
    @Deprecated(forRemoval = false)
    public static boolean authoredRoofShapeEligible(List<int[]> powder, List<int[]> shoulders) {
        return PowderRoofTrap.concealedSegmentEligible(powder, shoulders);
    }

    private static boolean hasMaskCell(boolean[][] mask, int x, int z) {
        return x >= 0 && x < mask.length && mask[x] != null && z >= 0 && z < mask[x].length;
    }

    private static List<int[]> toWorldCells(List<int[]> cells, int ownerMinX, int ownerMinZ) {
        List<int[]> world = new ArrayList<>();
        for (int[] cell : cells) {
            world.add(new int[]{ownerMinX + cell[0], ownerMinZ + cell[1]});
        }
        return world;
    }

    private static boolean disjoint(List<int[]> cells, Set<String> claimed) {
        return cells.stream().noneMatch(cell -> claimed.contains(cell[0] + "," + cell[1]));
    }

    private static void addCells(Set<String> target, List<int[]> cells) {
        for (int[] cell : cells) {
            target.add(cell[0] + "," + cell[1]);
        }
    }

    private static void removeCells(Set<String> target, List<int[]> cells) {
        for (int[] cell : cells) {
            target.remove(cell[0] + "," + cell[1]);
        }
    }

    private static List<int[]> immutableCells(List<int[]> cells) {
        List<int[]> copy = new ArrayList<>();
        for (int[] cell : cells) {
            copy.add(new int[]{cell[0], cell[1]});
        }
        return List.copyOf(copy);
    }

    private static boolean hasValue(int[][] values, int x, int z) {
        return x < values.length && values[x] != null && z < values[x].length;
    }

    /** Cell nearest a component's arithmetic centre; null/empty components return {@code null}. */
    public static int[] centreRepresentative(List<int[]> component) {
        if (component == null || component.isEmpty()) {
            return null;
        }
        long sumX = 0L;
        long sumZ = 0L;
        for (int[] cell : component) {
            sumX += cell[0];
            sumZ += cell[1];
        }
        int[] best = component.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (int[] cell : component) {
            long dx = (long) cell[0] * component.size() - sumX;
            long dz = (long) cell[1] * component.size() - sumZ;
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return new int[]{best[0], best[1]};
    }
}
