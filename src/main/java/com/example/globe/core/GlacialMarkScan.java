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
    /** Entry plus the longest eight-station descent and its untouched target opening. */
    private static final int DESCENT_HORIZONTAL_MARGIN = 9;
    private static final int MIN_DESCENT_STATIONS = 3;
    private static final int MAX_DESCENT_STATIONS = 8;
    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    private static final List<String> AUTHORED_ROUTE_ACTION_WORDS = authoredRouteActionWords();

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
     * <p>The scanner needs the cover itself plus the complete three-block headroom sample above it; therefore
     * a highest cover at {@code Y} requires cells through {@code Y+3}. The world ceiling is exclusive. Long
     * arithmetic and integer saturation keep malformed or extreme inputs from wrapping into a plausible low
     * sample bound.
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
        ORE,
        BEDROCK,
        DEEPSLATE,
        MAGMA,
        UNLOADED
    }

    /** Saved-biome evidence for each measured floor and clear cell in an irregular authored cavern. */
    public enum PhysicalBiomeKind {
        GLACIAL_CAVES,
        OTHER,
        UNLOADED
    }

    /** The two physically distinct endpoints accepted by the shared scanner. */
    public enum EndpointKind {
        NATURAL_CAVE,
        AUTHORED_CAVERN
    }

    /** Stable authored-endpoint failures exposed by the physical audit. */
    public enum AuthoredCavernRejection {
        AUTHORED_CAVERN_SIZE,
        AUTHORED_CAVERN_SPAN,
        AUTHORED_CAVERN_FILL,
        AUTHORED_CAVERN_FULL_5X5,
        AUTHORED_CAVERN_LOBES,
        AUTHORED_CAVERN_THROAT,
        AUTHORED_CAVERN_CLEAR_HEIGHT,
        AUTHORED_CAVERN_BIOME,
        AUTHORED_CAVERN_BEND,
        AUTHORED_CAVERN_PORTAL,
        AUTHORED_CAVERN_ABOVE_Y0,
        AUTHORED_CAVERN_OWNER_NEIGHBOR,
        AUTHORED_CAVERN_SHELL,
        AUTHORED_CAVERN_HAZARD,
        AUTHORED_ROUTE_RISE,
        AUTHORED_ROUTE_REVERSE,
        AUTHORED_ROUTE_DISCONNECTED
    }

    /** One two-wide station reconstructed from blocks, never from generator metadata. */
    public record PhysicalRouteStation(
            int floorY, int firstX, int firstZ, int secondX, int secondZ) {
    }

    /** Evidence that the reconstructed route obeys the downward, bounded-dogleg law. */
    public record PhysicalRouteEvidence(
            List<PhysicalRouteStation> stations,
            int turns,
            int initialHeadingX,
            int initialHeadingZ,
            boolean downwardOrLevel,
            boolean neverReversesInitial) {
        public PhysicalRouteEvidence {
            stations = stations == null ? List.of() : List.copyOf(stations);
        }
    }

    /** Independent measurements of an irregular cross-chunk authored cavern. */
    public record PhysicalCavernEvidence(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minFloorY,
            int maxFloorY,
            String direction,
            int ownerChunkX,
            int ownerChunkZ,
            int neighbourChunkX,
            int neighbourChunkZ,
            int floorColumns,
            int clearCells,
            int stableSubstrateCells,
            int stableCeilingCells,
            int stablePerimeterCells,
            int glacialBiomeCells,
            int span,
            int boundingArea,
            double boundingFill,
            int primaryLobeSpan,
            int primaryLobeWidth,
            int secondaryLobeSpan,
            int secondaryLobeWidth,
            int throatWidth,
            int distinctClearHeights,
            int passageDirections,
            boolean bent,
            boolean crossesOwnerNeighbour,
            boolean contractPassed) {
    }

    /** Per-trap proof retained for JSON audits and human-readable command diagnostics. */
    public record PhysicalTrapEvidence(
            int mouthX,
            int mouthY,
            int mouthZ,
            EndpointKind endpointKind,
            PhysicalRouteEvidence route,
            PhysicalCavernEvidence cavern) {
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
            Map<String, Integer> rejectionReasons,
            List<PhysicalTrapEvidence> validTrapEvidence) {

        public PhysicalScanReport {
            rejectionReasons = rejectionReasons == null
                    ? Map.of() : java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(rejectionReasons));
            validTrapEvidence = validTrapEvidence == null ? List.of() : List.copyOf(validTrapEvidence);
        }

        /** Keeps the existing command adapter source- and behavior-compatible. */
        public PhysicalScanReport(
                int candidates,
                int validTraps,
                int encounters,
                int coverColumns,
                int cushionMatches,
                int validEscapeRoutes,
                int partialComponents,
                int unsafeComponents,
                Map<String, Integer> rejectionReasons) {
            this(candidates, validTraps, encounters, coverColumns, cushionMatches, validEscapeRoutes,
                    partialComponents, unsafeComponents, rejectionReasons, List.of());
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

    private record EscapeCheck(
            boolean valid, PhysicalRejection rejection, PhysicalTrapEvidence evidence) {
    }

    private record HorizontalBounds(int minX, int maxXExclusive, int minZ, int maxZExclusive) {
        boolean contains(SurfaceCover cover) {
            return cover.x() >= minX && cover.x() < maxXExclusive
                    && cover.z() >= minZ && cover.z() < maxZExclusive;
        }
    }

    /**
     * Independently prove concealed traps from a bounded snapshot of real block cells.
     *
     * <p>Candidate identity is a cardinally connected component of top-surface powder snow whose adjacent
     * covers differ by at most one block. Normal-snow gaps are intentionally absent from that component: they
     * are camouflage, not missing authored roof cells. Every powder cover must then have a clear fall of at
     * least {@code minimumDrop} blocks to a powder-snow cushion on dry, non-gravity support. Finally, the
     * scanner must reconstruct a two-wide, three-high, downward-or-level route which never rises or reverses,
     * turns at most twice, and ends directly in either connected natural cave space or a measured irregular
     * cross-chunk glacial cavern. The route may touch the fall volume only at its landing doorway.
     *
     * <p>No generator plan, authored rectangle, shoulder list, or route metadata participates in the result.
     * Out-of-volume and ragged cells read as {@link PhysicalCellKind#UNLOADED}; candidates too close to that
     * boundary are rejected rather than speculatively accepted. Escape search is restricted to the component
     * bounding box plus {@value #DESCENT_HORIZONTAL_MARGIN} cells, keeping repeated scans bounded.
     *
     * @param cells measured cells indexed {@code [x][y][z]}, with Y increasing upward
     * @param minimumDrop minimum uninterrupted passable cells between cover and cushion
     */
    public static PhysicalScanReport scanPhysicalTrapVolume(
            PhysicalCellKind[][][] cells, int minimumDrop) {
        return scanPhysicalTrapVolume(cells, null, minimumDrop, 0, null, null);
    }

    /**
     * Full physical scan with cave-biome evidence. Natural endpoints retain their historical block-only law;
     * authored endpoints are independently reconstructed as irregular cross-chunk glacial caverns.
     */
    public static PhysicalScanReport scanPhysicalTrapVolume(
            PhysicalCellKind[][][] cells, PhysicalBiomeKind[][][] biomes, int minimumDrop) {
        return scanPhysicalTrapVolume(cells, biomes, minimumDrop, 0, null, null);
    }

    /**
     * Scan candidates whose measured powder mouth intersects the requested horizontal rectangle. The cells
     * outside that rectangle remain available as physical halo and endpoint evidence.
     */
    public static PhysicalScanReport scanPhysicalTrapVolumeInHorizontalBounds(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            int minimumDrop,
            int minX,
            int maxXExclusive,
            int minZ,
            int maxZExclusive) {
        if (minX >= maxXExclusive || minZ >= maxZExclusive) {
            throw new IllegalArgumentException("horizontal target bounds must be non-empty");
        }
        return scanPhysicalTrapVolume(cells, biomes, minimumDrop, 0, null,
                new HorizontalBounds(minX, maxXExclusive, minZ, maxZExclusive));
    }

    /**
     * Horizontal-bounds scan whose array index zero corresponds to {@code worldMinY}. Authored caverns use
     * this offset to prove that every floor and clear cell is physically above world Y=0.
     */
    public static PhysicalScanReport scanPhysicalTrapVolumeInHorizontalBounds(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            int minimumDrop,
            int worldMinY,
            int minX,
            int maxXExclusive,
            int minZ,
            int maxZExclusive) {
        if (minX >= maxXExclusive || minZ >= maxZExclusive) {
            throw new IllegalArgumentException("horizontal target bounds must be non-empty");
        }
        return scanPhysicalTrapVolume(cells, biomes, minimumDrop, worldMinY, null,
                new HorizontalBounds(minX, maxXExclusive, minZ, maxZExclusive));
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
        return scanPhysicalTrapVolume(cells, null, minimumDrop, 0,
                new int[]{anchorX, anchorZ}, null);
    }

    private static PhysicalScanReport scanPhysicalTrapVolume(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            int minimumDrop,
            int worldMinY,
            int[] anchor,
            HorizontalBounds targetBounds) {
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
        if (targetBounds != null) {
            components = components.stream()
                    .filter(component -> component.stream().anyMatch(targetBounds::contains))
                    .toList();
        }
        int validTraps = 0;
        int coverColumns = 0;
        int cushionMatches = 0;
        int validEscapeRoutes = 0;
        int partialComponents = 0;
        int unsafeComponents = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        List<PhysicalTrapEvidence> evidence = new ArrayList<>();

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
                EscapeCheck descent = checkDescent(
                        cells, biomes, bounds, component, drops, worldMinY);
                rejection = descent.rejection();
                if (descent.valid()) {
                    validTraps++;
                    coverColumns += component.size();
                    cushionMatches += drops.size();
                    validEscapeRoutes++;
                    evidence.add(descent.evidence());
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
                reasons,
                evidence);
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

        if (minX - DESCENT_HORIZONTAL_MARGIN < 0
                || minZ - DESCENT_HORIZONTAL_MARGIN < 0
                || maxX + DESCENT_HORIZONTAL_MARGIN >= bounds.xSize()
                || maxZ + DESCENT_HORIZONTAL_MARGIN >= bounds.zSize()
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

    private static EscapeCheck checkDescent(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            VolumeBounds bounds,
            List<SurfaceCover> component,
            List<DropColumn> drops,
            int worldMinY) {
        int minX = component.stream().mapToInt(SurfaceCover::x).min().orElse(0)
                - DESCENT_HORIZONTAL_MARGIN;
        int maxX = component.stream().mapToInt(SurfaceCover::x).max().orElse(0)
                + DESCENT_HORIZONTAL_MARGIN;
        int minZ = component.stream().mapToInt(SurfaceCover::z).min().orElse(0)
                - DESCENT_HORIZONTAL_MARGIN;
        int maxZ = component.stream().mapToInt(SurfaceCover::z).max().orElse(0)
                + DESCENT_HORIZONTAL_MARGIN;
        if (minX < 0 || minZ < 0 || maxX >= bounds.xSize() || maxZ >= bounds.zSize()) {
            return new EscapeCheck(false,
                    new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false), null);
        }

        Map<Long, DropColumn> dropByColumn = new LinkedHashMap<>();
        for (DropColumn drop : drops) {
            dropByColumn.put(packHorizontal(drop.x(), drop.z()), drop);
        }
        int lowestLanding = drops.stream().mapToInt(DropColumn::cushionY).min().orElse(0);
        for (DropColumn drop : drops) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int x = drop.x() + direction[0];
                int z = drop.z() + direction[1];
                if (dropByColumn.containsKey(packHorizontal(x, z))) {
                    continue;
                }
                for (int[] forward : CARDINAL_DIRECTIONS) {
                    int widthX = -forward[1];
                    int widthZ = forward[0];
                    for (int widthSign : new int[]{-1, 1}) {
                        for (int stations = MIN_DESCENT_STATIONS;
                                stations <= MAX_DESCENT_STATIONS; stations++) {
                            PhysicalRouteEvidence route = descendingRouteToNaturalCave(cells, bounds, dropByColumn,
                                    x, drop.cushionY() - 1, z,
                                    forward[0], forward[1], widthX * widthSign, widthZ * widthSign,
                                    minX, maxX, minZ, maxZ, lowestLanding, stations);
                            if (route != null) {
                                SurfaceCover mouth = canonicalMouth(component);
                                return new EscapeCheck(true, null, new PhysicalTrapEvidence(
                                        mouth.x(), mouth.y(), mouth.z(), EndpointKind.NATURAL_CAVE,
                                        route, null));
                            }
                        }
                    }
                }
            }
        }

        // The natural law above is intentionally first and unchanged. Legacy command callers deliberately
        // omit biome evidence, which also keeps their historical MISSING_DESCENT rejection byte-for-byte.
        if (biomes == null) {
            return new EscapeCheck(false,
                    new PhysicalRejection("MISSING_DESCENT", false), null);
        }

        // Only a failed natural search may use the distinct authored-cavern reconstruction, whose biome,
        // geometry, cross-chunk, and shell evidence cannot impersonate nature.
        PhysicalRejection bestAuthoredRejection = null;
        SurfaceCover mouth = canonicalMouth(component);
        for (DropColumn drop : drops) {
            for (int[] outward : CARDINAL_DIRECTIONS) {
                int entryX = drop.x() + outward[0];
                int entryZ = drop.z() + outward[1];
                if (dropByColumn.containsKey(packHorizontal(entryX, entryZ))) {
                    continue;
                }
                int widthX = -outward[1];
                int widthZ = outward[0];
                for (int widthSign : new int[]{-1, 1}) {
                    RouteFootprint start = RouteFootprint.of(
                            entryX, entryZ,
                            entryX + widthX * widthSign, entryZ + widthZ * widthSign);
                    AuthoredSearch authored = findAuthoredCavern(
                            cells, biomes, bounds, dropByColumn, start,
                            drop.cushionY() - 1, outward[0], outward[1],
                            worldMinY, mouth);
                    if (authored.evidence() != null) {
                        PhysicalTrapEvidence evidence = new PhysicalTrapEvidence(
                                mouth.x(), mouth.y(), mouth.z(), EndpointKind.AUTHORED_CAVERN,
                                authored.evidence().route(), authored.evidence().cavern());
                        return new EscapeCheck(true, null, evidence);
                    }
                    bestAuthoredRejection = preferredAuthoredRejection(
                            bestAuthoredRejection, authored.rejection());
                }
            }
        }
        return new EscapeCheck(false, bestAuthoredRejection == null
                ? new PhysicalRejection("MISSING_DESCENT", false) : bestAuthoredRejection, null);
    }

    private static PhysicalRouteEvidence descendingRouteToNaturalCave(
            PhysicalCellKind[][][] cells, VolumeBounds bounds, Map<Long, DropColumn> drops,
            int entryX, int entryFloorY, int entryZ, int forwardX, int forwardZ, int widthX, int widthZ,
            int minX, int maxX, int minZ, int maxZ, int lowestLanding, int stations) {
        List<PhysicalRouteStation> routeStations = new ArrayList<>(stations);
        for (int station = 0; station < stations; station++) {
            int x = entryX + forwardX * station;
            int z = entryZ + forwardZ * station;
            int floorY = entryFloorY - station;
            if (drops.containsKey(packHorizontal(x, z))
                    || drops.containsKey(packHorizontal(x + widthX, z + widthZ))
                    || !isThreeHighDescentStation(cells, bounds, x, floorY, z)
                    || !isThreeHighDescentStation(cells, bounds, x + widthX, floorY, z + widthZ)) {
                return null;
            }
            routeStations.add(new PhysicalRouteStation(
                    floorY, x, z, x + widthX, z + widthZ));
        }
        int targetX = entryX + forwardX * stations;
        int targetZ = entryZ + forwardZ * stations;
        int targetFloorY = entryFloorY - stations;
        if (targetFloorY >= lowestLanding
                || !insideDescentBounds(targetX, targetFloorY, targetZ, minX, maxX, minZ, maxZ, bounds)
                || !insideDescentBounds(targetX + widthX, targetFloorY, targetZ + widthZ,
                        minX, maxX, minZ, maxZ, bounds)
                || !isNaturalCaveColumn(cells, targetX, targetFloorY, targetZ)
                || !isNaturalCaveColumn(cells, targetX + widthX, targetFloorY, targetZ + widthZ)) {
            return null;
        }
        if (connectedNaturalCaveFloors(cells, bounds, targetX, targetFloorY, targetZ,
                targetX + widthX, targetZ + widthZ) < 8) {
            return null;
        }
        return new PhysicalRouteEvidence(
                routeStations, 0, forwardX, forwardZ, true, true);
    }

    private record RouteFootprint(int firstX, int firstZ, int secondX, int secondZ) {
        static RouteFootprint of(int firstX, int firstZ, int secondX, int secondZ) {
            if (firstX > secondX || (firstX == secondX && firstZ > secondZ)) {
                return new RouteFootprint(secondX, secondZ, firstX, firstZ);
            }
            return new RouteFootprint(firstX, firstZ, secondX, secondZ);
        }

        RouteFootprint translate(int dx, int dz) {
            return of(firstX + dx, firstZ + dz, secondX + dx, secondZ + dz);
        }

        Set<Long> columns() {
            return Set.of(packHorizontal(firstX, firstZ), packHorizontal(secondX, secondZ));
        }
    }

    private record AuthoredPath(List<RouteFootprint> footprints, int turns) {
        AuthoredPath {
            footprints = List.copyOf(footprints);
        }
    }

    private record CavernFloor(int x, int y, int z, int clearHeight) {
    }

    private record CavernColumnProbe(CavernFloor floor, PhysicalRejection rejection) {
    }

    private record CavernCheck(
            PhysicalCavernEvidence evidence, PhysicalRejection rejection) {
    }

    private record AuthoredEvidence(
            PhysicalRouteEvidence route, PhysicalCavernEvidence cavern) {
    }

    private record AuthoredSearch(AuthoredEvidence evidence, PhysicalRejection rejection) {
    }

    private record Voxel(int x, int y, int z) {
    }

    private record LobeDimensions(int span, int width, int area) {
    }

    private record LobeMetrics(
            int primarySpan,
            int primaryWidth,
            int secondarySpan,
            int secondaryWidth,
            int throatWidth,
            boolean bent) {
    }

    private static AuthoredSearch findAuthoredCavern(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            VolumeBounds bounds,
            Map<Long, DropColumn> drops,
            RouteFootprint start,
            int entryFloorY,
            int initialX,
            int initialZ,
            int worldMinY,
            SurfaceCover mouth) {
        if (!isRouteFootprint(cells, bounds, drops, start, entryFloorY)) {
            return new AuthoredSearch(null, null);
        }
        PhysicalRejection best = null;
        for (String actionWord : AUTHORED_ROUTE_ACTION_WORDS) {
            List<AuthoredPath> paths = List.of(new AuthoredPath(List.of(start), 0));
            char previousAction = 'F';
            for (int actionIndex = 0; actionIndex < actionWord.length() && !paths.isEmpty(); actionIndex++) {
                char action = actionWord.charAt(actionIndex);
                int headingX = actionHeadingX(initialX, initialZ, action);
                int headingZ = actionHeadingZ(initialX, initialZ, action);
                boolean turn = action != previousAction;
                int stationIndex = actionIndex + 1;
                int floorY = entryFloorY - Math.min(stationIndex, 5);
                List<AuthoredPath> expanded = new ArrayList<>();
                for (AuthoredPath path : paths) {
                    RouteFootprint current = path.footprints().getLast();
                    List<RouteFootprint> candidates = turn
                            ? turnFootprints(current, headingX, headingZ)
                            : List.of(current.translate(headingX, headingZ));
                    Set<Long> occupied = new HashSet<>();
                    path.footprints().forEach(footprint -> occupied.addAll(footprint.columns()));
                    for (RouteFootprint next : candidates) {
                        if (next.columns().stream().anyMatch(occupied::contains)
                                || !isRouteFootprint(cells, bounds, drops, next, floorY)) {
                            continue;
                        }
                        int turns = path.turns() + (turn ? 1 : 0);
                        if (turns > 2) {
                            continue;
                        }
                        List<RouteFootprint> stations = new ArrayList<>(path.footprints());
                        stations.add(next);
                        expanded.add(new AuthoredPath(stations, turns));
                    }
                }
                paths = List.copyOf(expanded);
                previousAction = action;
            }
            for (AuthoredPath path : paths) {
                RouteFootprint terminal = path.footprints().getLast();
                int floorY = entryFloorY - 5;
                Set<Long> routeColumns = new HashSet<>();
                path.footprints().forEach(footprint -> routeColumns.addAll(footprint.columns()));
                List<PhysicalRouteStation> stations = new ArrayList<>();
                for (int index = 0; index < path.footprints().size(); index++) {
                    RouteFootprint footprint = path.footprints().get(index);
                    stations.add(new PhysicalRouteStation(
                            entryFloorY - Math.min(index, 5),
                            footprint.firstX(), footprint.firstZ(),
                            footprint.secondX(), footprint.secondZ()));
                }
                PhysicalRouteEvidence route = new PhysicalRouteEvidence(
                        stations, path.turns(), initialX, initialZ,
                        routeNeverRises(stations), true);
                char finalAction = actionWord.charAt(actionWord.length() - 1);
                int portalDx = actionHeadingX(initialX, initialZ, finalAction);
                int portalDz = actionHeadingZ(initialX, initialZ, finalAction);
                if (!authoredPortalFollowsFinalHeading(
                        portalDx, portalDz, portalDx, portalDz)) {
                    throw new IllegalStateException("authored portal heading invariant failed");
                }
                RouteFootprint portal = terminal.translate(portalDx, portalDz);
                if (!portal.columns().stream().anyMatch(routeColumns::contains)) {
                    for (int[] neighbourDirection : CARDINAL_DIRECTIONS) {
                        CavernCheck checked = validateCavern(
                                cells, biomes, bounds, portal, floorY, route,
                                routeColumns, mouth, neighbourDirection[0], neighbourDirection[1],
                                worldMinY);
                        if (checked.evidence() != null) {
                            return new AuthoredSearch(
                                    new AuthoredEvidence(route, checked.evidence()), null);
                        }
                        best = preferredAuthoredRejection(best, checked.rejection());
                    }
                }
            }
        }
        if (best == null) {
            best = diagnoseAuthoredRoute(cells, bounds, drops, start, entryFloorY, initialX, initialZ);
        }
        return new AuthoredSearch(null, best);
    }

    private static SurfaceCover canonicalMouth(List<SurfaceCover> component) {
        return component.stream().min(Comparator.comparingInt(SurfaceCover::x)
                .thenComparingInt(SurfaceCover::z)
                .thenComparingInt(SurfaceCover::y)).orElseThrow();
    }

    private static boolean isRouteFootprint(
            PhysicalCellKind[][][] cells,
            VolumeBounds bounds,
            Map<Long, DropColumn> drops,
            RouteFootprint footprint,
            int floorY) {
        return !drops.containsKey(packHorizontal(footprint.firstX(), footprint.firstZ()))
                && !drops.containsKey(packHorizontal(footprint.secondX(), footprint.secondZ()))
                && isThreeHighDescentStation(
                        cells, bounds, footprint.firstX(), floorY, footprint.firstZ())
                && isThreeHighDescentStation(
                        cells, bounds, footprint.secondX(), floorY, footprint.secondZ());
    }

    private static List<RouteFootprint> turnFootprints(
            RouteFootprint current, int headingX, int headingZ) {
        Set<RouteFootprint> result = new HashSet<>();
        for (int[] anchor : new int[][]{
                {current.firstX(), current.firstZ()},
                {current.secondX(), current.secondZ()}}) {
            int firstX = anchor[0] + headingX;
            int firstZ = anchor[1] + headingZ;
            for (int sign : new int[]{-1, 1}) {
                result.add(RouteFootprint.of(
                        firstX, firstZ,
                        firstX - headingZ * sign, firstZ + headingX * sign));
            }
        }
        return result.stream().sorted(Comparator.comparingInt(RouteFootprint::firstX)
                .thenComparingInt(RouteFootprint::firstZ)
                .thenComparingInt(RouteFootprint::secondX)
                .thenComparingInt(RouteFootprint::secondZ)).toList();
    }

    private static int actionHeadingX(int initialX, int initialZ, char action) {
        return switch (action) {
            case 'F' -> initialX;
            case 'L' -> -initialZ;
            case 'R' -> initialZ;
            default -> throw new IllegalArgumentException("unknown authored route action " + action);
        };
    }

    private static int actionHeadingZ(int initialX, int initialZ, char action) {
        return switch (action) {
            case 'F' -> initialZ;
            case 'L' -> initialX;
            case 'R' -> -initialX;
            default -> throw new IllegalArgumentException("unknown authored route action " + action);
        };
    }

    static boolean authoredPortalFollowsFinalHeading(
            int finalHeadingX, int finalHeadingZ, int portalDx, int portalDz) {
        return Math.abs(finalHeadingX) + Math.abs(finalHeadingZ) == 1
                && portalDx == finalHeadingX && portalDz == finalHeadingZ;
    }

    private static List<String> authoredRouteActionWords() {
        List<String> words = new ArrayList<>();
        for (int moves = 5; moves <= 11; moves++) {
            List<String> sameLength = new ArrayList<>();
            sameLength.add("F".repeat(moves));
            for (char side : new char[]{'L', 'R'}) {
                for (int first = 0; first < moves; first++) {
                    int second = moves - first;
                    sameLength.add("F".repeat(first) + String.valueOf(side).repeat(second));
                }
                for (int first = 0; first <= moves - 2; first++) {
                    for (int middle = 1; middle <= moves - first - 1; middle++) {
                        int last = moves - first - middle;
                        sameLength.add("F".repeat(first)
                                + String.valueOf(side).repeat(middle) + "F".repeat(last));
                    }
                }
            }
            sameLength.sort(String::compareTo);
            words.addAll(sameLength);
        }
        return List.copyOf(words);
    }

    private static CavernCheck validateCavern(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            VolumeBounds bounds,
            RouteFootprint portal,
            int portalFloorY,
            PhysicalRouteEvidence route,
            Set<Long> routeColumns,
            SurfaceCover mouth,
            int neighbourDx,
            int neighbourDz,
            int worldMinY) {
        int ownerChunkX = Math.floorDiv(mouth.x(), OWNER_CHUNK_SIDE);
        int ownerChunkZ = Math.floorDiv(mouth.z(), OWNER_CHUNK_SIDE);
        int neighbourChunkX = ownerChunkX + neighbourDx;
        int neighbourChunkZ = ownerChunkZ + neighbourDz;

        Map<Long, CavernFloor> floors = new LinkedHashMap<>();
        ArrayDeque<CavernFloor> queue = new ArrayDeque<>();
        PhysicalRejection bestRejectedColumn = null;
        for (long packed : portal.columns()) {
            int x = (int) (packed >> 32);
            int z = (int) packed;
            if (!insideOwnerNeighbour(x, z, ownerChunkX, ownerChunkZ,
                    neighbourChunkX, neighbourChunkZ)) {
                return new CavernCheck(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_OWNER_NEIGHBOR, false));
            }
            CavernColumnProbe probe = probeCavernColumn(
                    cells, biomes, bounds, x, portalFloorY, z, worldMinY);
            if (probe.floor() == null) {
                PhysicalRejection rejection = probe.rejection() == null
                        ? authoredRejection(AuthoredCavernRejection.AUTHORED_CAVERN_PORTAL, false)
                        : probe.rejection();
                return new CavernCheck(null, rejection);
            }
            floors.put(packHorizontal(x, z), probe.floor());
            queue.addLast(probe.floor());
        }

        while (!queue.isEmpty() && floors.size() <= 1024) {
            CavernFloor current = queue.removeFirst();
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int nx = current.x() + direction[0];
                int nz = current.z() + direction[1];
                long packed = packHorizontal(nx, nz);
                if (floors.containsKey(packed) || routeColumns.contains(packed)
                        || !insideOwnerNeighbour(nx, nz, ownerChunkX, ownerChunkZ,
                                neighbourChunkX, neighbourChunkZ)) {
                    continue;
                }
                CavernFloor next = null;
                for (int dy : new int[]{0, -1, 1}) {
                    CavernColumnProbe probe = probeCavernColumn(
                            cells, biomes, bounds, nx, current.y() + dy, nz, worldMinY);
                    if (probe.floor() != null) {
                        next = probe.floor();
                        break;
                    }
                    if (looksLikeCavernColumn(cells, nx, current.y() + dy, nz)) {
                        bestRejectedColumn = preferredAuthoredRejection(
                                bestRejectedColumn, probe.rejection());
                    }
                }
                if (next != null) {
                    floors.put(packed, next);
                    queue.addLast(next);
                }
            }
        }
        if (bestRejectedColumn != null) {
            return new CavernCheck(null, bestRejectedColumn);
        }

        List<CavernFloor> component = List.copyOf(floors.values());
        PhysicalRejection secondNeighbour = secondNeighbourContinuationRejection(
                cells, biomes, bounds, component, ownerChunkX, ownerChunkZ,
                neighbourChunkX, neighbourChunkZ, worldMinY);
        if (secondNeighbour != null) {
            return new CavernCheck(null, secondNeighbour);
        }
        if (component.size() < 96) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_SIZE, false));
        }
        int minX = component.stream().mapToInt(CavernFloor::x).min().orElse(0);
        int maxX = component.stream().mapToInt(CavernFloor::x).max().orElse(0);
        int minZ = component.stream().mapToInt(CavernFloor::z).min().orElse(0);
        int maxZ = component.stream().mapToInt(CavernFloor::z).max().orElse(0);
        int minFloorY = component.stream().mapToInt(CavernFloor::y).min().orElse(0);
        int maxFloorY = component.stream().mapToInt(CavernFloor::y).max().orElse(0);
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int span = Math.max(spanX, spanZ);
        if (span < 18) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_SPAN, false));
        }
        int boundingArea = spanX * spanZ;
        double fill = component.size() / (double) boundingArea;
        if (fill < 0.45 || fill > 0.75) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_FILL, false));
        }
        Set<Long> floorColumns = Set.copyOf(floors.keySet());
        if (containsFilledFiveByFive(floorColumns, minX, maxX, minZ, maxZ)) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_FULL_5X5, false));
        }

        Set<Integer> heights = new HashSet<>();
        component.forEach(floor -> heights.add(floor.clearHeight()));
        if (heights.size() < 3 || heights.stream().anyMatch(height -> height < 4 || height > 7)) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_CLEAR_HEIGHT, false));
        }
        LobeMetrics lobes = measureLobes(component, floorColumns, spanX >= spanZ);
        if (lobes == null) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_LOBES, false));
        }
        if (lobes.throatWidth() < 3 || lobes.throatWidth() > 5) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_THROAT, false));
        }
        if (!lobes.bent()) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_BEND, false));
        }

        int ownerFloors = 0;
        int neighbourFloors = 0;
        for (CavernFloor floor : component) {
            int chunkX = Math.floorDiv(floor.x(), OWNER_CHUNK_SIDE);
            int chunkZ = Math.floorDiv(floor.z(), OWNER_CHUNK_SIDE);
            if (chunkX == ownerChunkX && chunkZ == ownerChunkZ) {
                ownerFloors++;
            } else if (chunkX == neighbourChunkX && chunkZ == neighbourChunkZ) {
                neighbourFloors++;
            } else {
                return new CavernCheck(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_OWNER_NEIGHBOR, false));
            }
        }
        boolean crosses = ownerFloors > 0 && neighbourFloors > 0;
        if (!crosses) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_OWNER_NEIGHBOR, false));
        }

        Set<Voxel> volume = new HashSet<>();
        int clearCells = 0;
        for (CavernFloor floor : component) {
            volume.add(new Voxel(floor.x(), floor.y(), floor.z()));
            for (int dy = 1; dy <= floor.clearHeight(); dy++) {
                volume.add(new Voxel(floor.x(), floor.y() + dy, floor.z()));
                clearCells++;
            }
        }
        Set<Voxel> routeVolume = new HashSet<>();
        for (PhysicalRouteStation station : route.stations()) {
            for (int[] xz : new int[][]{
                    {station.firstX(), station.firstZ()},
                    {station.secondX(), station.secondZ()}}) {
                for (int dy = 0; dy <= 3; dy++) {
                    routeVolume.add(new Voxel(xz[0], station.floorY() + dy, xz[1]));
                }
            }
        }
        Set<Voxel> perimeter = new HashSet<>();
        for (Voxel cell : volume) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                Voxel neighbour = new Voxel(
                        cell.x() + direction[0], cell.y(), cell.z() + direction[1]);
                if (!volume.contains(neighbour) && !routeVolume.contains(neighbour)) {
                    perimeter.add(neighbour);
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (physicalCellAt(cells, cell.x() + dx, cell.y() + dy, cell.z() + dz)
                                == PhysicalCellKind.MAGMA) {
                            return new CavernCheck(null, authoredRejection(
                                    AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true));
                        }
                    }
                }
            }
        }
        for (Voxel probe : perimeter) {
            PhysicalCellKind kind = physicalCellAt(cells, probe.x(), probe.y(), probe.z());
            if (kind == PhysicalCellKind.UNLOADED) {
                return new CavernCheck(null,
                        new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false));
            }
            if (isCavernHazard(kind)) {
                return new CavernCheck(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true));
            }
            if (!isDryStableSupport(kind)) {
                return new CavernCheck(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_SHELL, false));
            }
        }

        int passageDirections = passageDirections(floorColumns);
        if (passageDirections < 2) {
            return new CavernCheck(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_BEND, false));
        }
        int glacialBiomeCells = component.stream()
                .mapToInt(floor -> floor.clearHeight() + 1).sum();
        String direction = neighbourDx > 0 ? "EAST" : neighbourDx < 0 ? "WEST"
                : neighbourDz > 0 ? "SOUTH" : "NORTH";
        return new CavernCheck(new PhysicalCavernEvidence(
                minX, maxX, minZ, maxZ, minFloorY, maxFloorY,
                direction, ownerChunkX, ownerChunkZ, neighbourChunkX, neighbourChunkZ,
                component.size(), clearCells, component.size(), component.size(), perimeter.size(),
                glacialBiomeCells, span, boundingArea, fill,
                lobes.primarySpan(), lobes.primaryWidth(),
                lobes.secondarySpan(), lobes.secondaryWidth(), lobes.throatWidth(),
                heights.size(), passageDirections, true, true, true), null);
    }

    private static CavernColumnProbe probeCavernColumn(
            PhysicalCellKind[][][] cells,
            PhysicalBiomeKind[][][] biomes,
            VolumeBounds bounds,
            int x,
            int floorY,
            int z,
            int worldMinY) {
        if (!insideVolume(bounds, x, floorY, z)) {
            return new CavernColumnProbe(null,
                    new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false));
        }
        if ((long) worldMinY + floorY <= 0L) {
            return new CavernColumnProbe(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_ABOVE_Y0, false));
        }
        PhysicalCellKind floor = physicalCellAt(cells, x, floorY, z);
        if (isCavernHazard(floor)) {
            return new CavernColumnProbe(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true));
        }
        if (floor != PhysicalCellKind.SNOW_BLOCK) {
            return new CavernColumnProbe(null, null);
        }
        PhysicalCellKind substrate = physicalCellAt(cells, x, floorY - 1, z);
        if (isCavernHazard(substrate)) {
            return new CavernColumnProbe(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true));
        }
        if (!isDryStableSupport(substrate)) {
            return new CavernColumnProbe(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_SHELL, false));
        }
        int clearHeight = 0;
        for (int dy = 1; dy <= 8; dy++) {
            PhysicalCellKind kind = physicalCellAt(cells, x, floorY + dy, z);
            if (isPassable(kind)) {
                clearHeight++;
                continue;
            }
            if (isCavernHazard(kind)) {
                return new CavernColumnProbe(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true));
            }
            break;
        }
        if (clearHeight < 4 || clearHeight > 7) {
            return new CavernColumnProbe(null, authoredRejection(
                    AuthoredCavernRejection.AUTHORED_CAVERN_CLEAR_HEIGHT, false));
        }
        PhysicalCellKind ceiling = physicalCellAt(cells, x, floorY + clearHeight + 1, z);
        if (!isDryStableSupport(ceiling)) {
            return new CavernColumnProbe(null, isCavernHazard(ceiling)
                    ? authoredRejection(AuthoredCavernRejection.AUTHORED_CAVERN_HAZARD, true)
                    : authoredRejection(AuthoredCavernRejection.AUTHORED_CAVERN_SHELL, false));
        }
        for (int dy = 0; dy <= clearHeight; dy++) {
            PhysicalBiomeKind biome = physicalBiomeAt(biomes, x, floorY + dy, z);
            if (biome == PhysicalBiomeKind.UNLOADED) {
                return new CavernColumnProbe(null,
                        new PhysicalRejection("UNLOADED_OR_SCAN_BOUNDARY", false));
            }
            if (biome != PhysicalBiomeKind.GLACIAL_CAVES) {
                return new CavernColumnProbe(null, authoredRejection(
                        AuthoredCavernRejection.AUTHORED_CAVERN_BIOME, false));
            }
        }
        return new CavernColumnProbe(new CavernFloor(x, floorY, z, clearHeight), null);
    }

    private static boolean looksLikeCavernColumn(
            PhysicalCellKind[][][] cells, int x, int floorY, int z) {
        PhysicalCellKind floor = physicalCellAt(cells, x, floorY, z);
        return floor == PhysicalCellKind.SNOW_BLOCK
                || isCavernHazard(floor)
                || (isPassable(physicalCellAt(cells, x, floorY + 1, z))
                        && isPassable(physicalCellAt(cells, x, floorY + 2, z))
                        && isPassable(physicalCellAt(cells, x, floorY + 3, z)));
    }

    private static boolean insideOwnerNeighbour(
            int x, int z, int ownerChunkX, int ownerChunkZ,
            int neighbourChunkX, int neighbourChunkZ) {
        int chunkX = Math.floorDiv(x, OWNER_CHUNK_SIDE);
        int chunkZ = Math.floorDiv(z, OWNER_CHUNK_SIDE);
        return (chunkX == ownerChunkX && chunkZ == ownerChunkZ)
                || (chunkX == neighbourChunkX && chunkZ == neighbourChunkZ);
    }

    private static PhysicalRejection secondNeighbourContinuationRejection(
            PhysicalCellKind[][][] cells, PhysicalBiomeKind[][][] biomes, VolumeBounds bounds,
            List<CavernFloor> component, int ownerChunkX, int ownerChunkZ,
            int neighbourChunkX, int neighbourChunkZ, int worldMinY) {
        for (CavernFloor floor : component) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int nx = floor.x() + direction[0];
                int nz = floor.z() + direction[1];
                if (insideOwnerNeighbour(nx, nz, ownerChunkX, ownerChunkZ,
                        neighbourChunkX, neighbourChunkZ)) {
                    continue;
                }
                for (int dy : new int[]{0, -1, 1}) {
                    if (probeCavernColumn(cells, biomes, bounds, nx, floor.y() + dy, nz,
                            worldMinY).floor() != null) {
                        return authoredRejection(
                                AuthoredCavernRejection.AUTHORED_CAVERN_OWNER_NEIGHBOR, false);
                    }
                }
            }
        }
        return null;
    }

    private static boolean containsFilledFiveByFive(
            Set<Long> floors, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX - 4; x++) {
            for (int z = minZ; z <= maxZ - 4; z++) {
                boolean filled = true;
                for (int dx = 0; dx < 5 && filled; dx++) {
                    for (int dz = 0; dz < 5; dz++) {
                        if (!floors.contains(packHorizontal(x + dx, z + dz))) {
                            filled = false;
                            break;
                        }
                    }
                }
                if (filled) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LobeMetrics measureLobes(
            List<CavernFloor> component, Set<Long> floors, boolean majorX) {
        Map<Integer, Set<Integer>> slices = new LinkedHashMap<>();
        for (CavernFloor floor : component) {
            int major = majorX ? floor.x() : floor.z();
            int minor = majorX ? floor.z() : floor.x();
            slices.computeIfAbsent(major, ignored -> new HashSet<>()).add(minor);
        }
        List<Integer> coordinates = slices.keySet().stream().sorted().toList();
        LobeMetrics best = null;
        int bestArea = -1;
        for (int coordinate : coordinates) {
            int throatWidth = slices.get(coordinate).size();
            if (throatWidth < 3 || throatWidth > 5
                    || !slices.containsKey(coordinate - 1)
                    || !slices.containsKey(coordinate + 1)
                    || !throatJoinsBothSides(slices, coordinate)) {
                continue;
            }
            List<CavernFloor> first = component.stream()
                    .filter(floor -> (majorX ? floor.x() : floor.z()) < coordinate).toList();
            List<CavernFloor> second = component.stream()
                    .filter(floor -> (majorX ? floor.x() : floor.z()) > coordinate).toList();
            LobeDimensions firstDimensions = lobeDimensions(first, majorX);
            LobeDimensions secondDimensions = lobeDimensions(second, majorX);
            boolean fits = lobeFits(firstDimensions, 8, 7) && lobeFits(secondDimensions, 6, 5)
                    || lobeFits(secondDimensions, 8, 7) && lobeFits(firstDimensions, 6, 5);
            if (!fits) {
                continue;
            }
            LobeDimensions primary = firstDimensions.area() >= secondDimensions.area()
                    ? firstDimensions : secondDimensions;
            LobeDimensions secondary = primary == firstDimensions
                    ? secondDimensions : firstDimensions;
            double firstCentre = minorCentre(first, majorX);
            double secondCentre = minorCentre(second, majorX);
            boolean bent = Math.abs(firstCentre - secondCentre) >= 2.0;
            int area = firstDimensions.area() + secondDimensions.area();
            if (area > bestArea) {
                bestArea = area;
                best = new LobeMetrics(
                        primary.span(), primary.width(), secondary.span(), secondary.width(),
                        throatWidth, bent);
            }
        }
        return best;
    }

    private static boolean throatJoinsBothSides(Map<Integer, Set<Integer>> slices, int coordinate) {
        for (int minor : slices.get(coordinate)) {
            boolean left = slices.get(coordinate - 1).contains(minor);
            boolean right = slices.get(coordinate + 1).contains(minor);
            if (left && right) {
                return true;
            }
        }
        return false;
    }

    private static LobeDimensions lobeDimensions(List<CavernFloor> floors, boolean majorX) {
        if (floors.isEmpty()) {
            return new LobeDimensions(0, 0, 0);
        }
        int minMajor = floors.stream().mapToInt(floor -> majorX ? floor.x() : floor.z()).min().orElse(0);
        int maxMajor = floors.stream().mapToInt(floor -> majorX ? floor.x() : floor.z()).max().orElse(0);
        int minMinor = floors.stream().mapToInt(floor -> majorX ? floor.z() : floor.x()).min().orElse(0);
        int maxMinor = floors.stream().mapToInt(floor -> majorX ? floor.z() : floor.x()).max().orElse(0);
        return new LobeDimensions(
                maxMajor - minMajor + 1, maxMinor - minMinor + 1, floors.size());
    }

    private static boolean lobeFits(LobeDimensions dimensions, int span, int width) {
        return dimensions.span() >= span && dimensions.width() >= width;
    }

    private static double minorCentre(List<CavernFloor> floors, boolean majorX) {
        return floors.stream().mapToInt(floor -> majorX ? floor.z() : floor.x())
                .average().orElse(0.0);
    }

    private static int passageDirections(Set<Long> floors) {
        boolean x = false;
        boolean z = false;
        for (long packed : floors) {
            int cellX = (int) (packed >> 32);
            int cellZ = (int) packed;
            x |= floors.contains(packHorizontal(cellX - 1, cellZ))
                    || floors.contains(packHorizontal(cellX + 1, cellZ));
            z |= floors.contains(packHorizontal(cellX, cellZ - 1))
                    || floors.contains(packHorizontal(cellX, cellZ + 1));
        }
        return (x ? 1 : 0) + (z ? 1 : 0);
    }

    private static boolean isCavernHazard(PhysicalCellKind kind) {
        return kind == PhysicalCellKind.FLUID
                || kind == PhysicalCellKind.BLOCK_ENTITY
                || kind == PhysicalCellKind.GRAVITY_SOLID
                || kind == PhysicalCellKind.ORE
                || kind == PhysicalCellKind.BEDROCK
                || kind == PhysicalCellKind.DEEPSLATE
                || kind == PhysicalCellKind.MAGMA;
    }

    private static PhysicalRejection authoredRejection(
            AuthoredCavernRejection reason, boolean unsafe) {
        return new PhysicalRejection(reason.name(), unsafe);
    }

    private static boolean routeNeverRises(List<PhysicalRouteStation> stations) {
        for (int index = 1; index < stations.size(); index++) {
            if (stations.get(index).floorY() > stations.get(index - 1).floorY()) {
                return false;
            }
        }
        return true;
    }

    private static PhysicalRejection diagnoseAuthoredRoute(
            PhysicalCellKind[][][] cells,
            VolumeBounds bounds,
            Map<Long, DropColumn> drops,
            RouteFootprint start,
            int entryFloorY,
            int initialX,
            int initialZ) {
        record DiagnosticPath(RouteFootprint current, Set<Long> occupied, int headingX, int headingZ) {
        }
        List<DiagnosticPath> paths = List.of(new DiagnosticPath(
                start, start.columns(), initialX, initialZ));
        for (int station = 1; station <= 11 && !paths.isEmpty(); station++) {
            int floorY = entryFloorY - Math.min(station, 5);
            List<DiagnosticPath> expanded = new ArrayList<>();
            for (DiagnosticPath path : paths) {
                for (int[] heading : CARDINAL_DIRECTIONS) {
                    List<RouteFootprint> nextFootprints = heading[0] == path.headingX()
                                    && heading[1] == path.headingZ()
                            ? List.of(path.current().translate(heading[0], heading[1]))
                            : turnFootprints(path.current(), heading[0], heading[1]);
                    for (RouteFootprint next : nextFootprints) {
                        if (next.columns().stream().anyMatch(path.occupied()::contains)
                                || !isRouteFootprint(cells, bounds, drops, next, floorY)) {
                            continue;
                        }
                        if (heading[0] == -initialX && heading[1] == -initialZ) {
                            return new PhysicalRejection("AUTHORED_ROUTE_REVERSE", false);
                        }
                        if (expanded.size() < 256) {
                            Set<Long> occupied = new HashSet<>(path.occupied());
                            occupied.addAll(next.columns());
                            expanded.add(new DiagnosticPath(
                                    next, Set.copyOf(occupied), heading[0], heading[1]));
                        }
                    }
                }
            }
            paths = List.copyOf(expanded);
        }
        for (char action : new char[]{'F', 'L', 'R'}) {
            int dx = actionHeadingX(initialX, initialZ, action);
            int dz = actionHeadingZ(initialX, initialZ, action);
            for (RouteFootprint next : turnFootprints(start, dx, dz)) {
                if (isRouteFootprint(cells, bounds, drops, next, entryFloorY)) {
                    return new PhysicalRejection("AUTHORED_ROUTE_RISE", false);
                }
            }
            RouteFootprint translated = start.translate(dx, dz);
            if (isRouteFootprint(cells, bounds, drops, translated, entryFloorY)) {
                return new PhysicalRejection("AUTHORED_ROUTE_RISE", false);
            }
        }
        return new PhysicalRejection("AUTHORED_ROUTE_DISCONNECTED", false);
    }

    private static PhysicalRejection preferredAuthoredRejection(
            PhysicalRejection current, PhysicalRejection candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || authoredRejectionPriority(candidate.reason())
                > authoredRejectionPriority(current.reason())) {
            return candidate;
        }
        return current;
    }

    private static int authoredRejectionPriority(String reason) {
        return switch (reason) {
            case "AUTHORED_CAVERN_HAZARD" -> 16;
            case "AUTHORED_CAVERN_BIOME" -> 15;
            case "AUTHORED_CAVERN_ABOVE_Y0" -> 14;
            case "AUTHORED_CAVERN_FULL_5X5" -> 13;
            case "AUTHORED_CAVERN_CLEAR_HEIGHT" -> 12;
            case "AUTHORED_CAVERN_SHELL" -> 11;
            case "AUTHORED_CAVERN_LOBES" -> 10;
            case "AUTHORED_CAVERN_THROAT" -> 9;
            case "AUTHORED_CAVERN_BEND" -> 8;
            case "AUTHORED_CAVERN_FILL" -> 7;
            case "AUTHORED_CAVERN_SPAN" -> 6;
            case "AUTHORED_CAVERN_SIZE" -> 5;
            case "AUTHORED_CAVERN_OWNER_NEIGHBOR" -> 4;
            case "AUTHORED_CAVERN_PORTAL" -> 3;
            case "AUTHORED_ROUTE_REVERSE", "AUTHORED_ROUTE_RISE" -> 2;
            case "AUTHORED_ROUTE_DISCONNECTED" -> 1;
            default -> 0;
        };
    }

    private static boolean isThreeHighDescentStation(
            PhysicalCellKind[][][] cells, VolumeBounds bounds, int x, int floorY, int z) {
        return insideVolume(bounds, x, floorY, z)
                && physicalCellAt(cells, x, floorY, z) == PhysicalCellKind.SNOW_BLOCK
                && isPassable(physicalCellAt(cells, x, floorY + 1, z))
                && isPassable(physicalCellAt(cells, x, floorY + 2, z))
                && isPassable(physicalCellAt(cells, x, floorY + 3, z))
                && isDryStableSupport(physicalCellAt(cells, x, floorY + 4, z));
    }

    private static boolean isNaturalCaveColumn(PhysicalCellKind[][][] cells, int x, int floorY, int z) {
        return physicalCellAt(cells, x, floorY, z) == PhysicalCellKind.DRY_SOLID
                && physicalCellAt(cells, x, floorY - 1, z) == PhysicalCellKind.DRY_SOLID
                && isPassable(physicalCellAt(cells, x, floorY + 1, z))
                && isPassable(physicalCellAt(cells, x, floorY + 2, z))
                && isPassable(physicalCellAt(cells, x, floorY + 3, z));
    }

    private static int connectedNaturalCaveFloors(
            PhysicalCellKind[][][] cells, VolumeBounds bounds, int firstX, int floorY, int firstZ,
            int secondX, int secondZ) {
        ArrayDeque<WalkNode> queue = new ArrayDeque<>();
        Set<WalkNode> visited = new HashSet<>();
        for (WalkNode start : List.of(new WalkNode(firstX, floorY, firstZ),
                new WalkNode(secondX, floorY, secondZ))) {
            if (visited.add(start)) {
                queue.addLast(start);
            }
        }
        while (!queue.isEmpty() && visited.size() < 8) {
            WalkNode current = queue.removeFirst();
            for (int[] direction : CARDINAL_DIRECTIONS) {
                for (int dy : new int[]{0, -1, 1}) {
                    WalkNode next = new WalkNode(current.x() + direction[0], current.feetY() + dy,
                            current.z() + direction[1]);
                    if (insideVolume(bounds, next.x(), next.feetY(), next.z())
                            && isNaturalCaveColumn(cells, next.x(), next.feetY(), next.z())
                            && visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
        }
        return visited.size();
    }

    private static boolean insideDescentBounds(
            int x, int y, int z, int minX, int maxX, int minZ, int maxZ, VolumeBounds bounds) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && insideVolume(bounds, x, y, z);
    }

    private static boolean insideVolume(VolumeBounds bounds, int x, int y, int z) {
        return x >= 0 && x < bounds.xSize() && y > 0 && y + 3 < bounds.ySize() && z >= 0 && z < bounds.zSize();
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

    private static PhysicalBiomeKind physicalBiomeAt(
            PhysicalBiomeKind[][][] biomes, int x, int y, int z) {
        if (biomes == null || x < 0 || x >= biomes.length || biomes[x] == null
                || y < 0 || y >= biomes[x].length || biomes[x][y] == null
                || z < 0 || z >= biomes[x][y].length
                || biomes[x][y][z] == null) {
            return PhysicalBiomeKind.UNLOADED;
        }
        return biomes[x][y][z];
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
