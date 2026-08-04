package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fresh-world V2 availability plan for vanilla surface and water hard authorities.
 *
 * <p>The plan owns only coherent locations. The live picker still requires the matching physical
 * authority (ocean depth, shoreline, river channel, or coastal wetland) before returning an ID.
 * Mushroom Fields is the sole terrain-forming entry: its reserved deep-ocean province supplies a
 * compact density lift so the selected biome is a real isolated island rather than an underwater
 * label.</p>
 */
public final class VanillaSurfaceWaterCoveragePlan {
    public enum Family { OCEAN, SHORE, RIVER, MANGROVE, MUSHROOM }

    public enum Route {
        WARM_SHALLOW_OCEAN(Family.OCEAN, 0.05, 0.20),
        LUKEWARM_SHALLOW_OCEAN(Family.OCEAN, 0.25, 0.36),
        LUKEWARM_DEEP_OCEAN(Family.OCEAN, 0.25, 0.36),
        TEMPERATE_SHALLOW_OCEAN(Family.OCEAN, 0.42, 0.54),
        TEMPERATE_DEEP_OCEAN(Family.OCEAN, 0.42, 0.54),
        COLD_SHALLOW_OCEAN(Family.OCEAN, 0.60, 0.72),
        COLD_DEEP_OCEAN(Family.OCEAN, 0.60, 0.72),
        FROZEN_SHALLOW_OCEAN(Family.OCEAN, 0.78, 0.90),
        FROZEN_DEEP_OCEAN(Family.OCEAN, 0.78, 0.90),
        TEMPERATE_BEACH(Family.SHORE, 0.38, 0.54),
        COLD_SNOWY_BEACH(Family.SHORE, 0.60, 0.90),
        ROCKY_SHORE(Family.SHORE, 0.38, 0.88),
        TEMPERATE_RIVER(Family.RIVER, 0.18, 0.54),
        COLD_RIVER(Family.RIVER, 0.60, 0.90),
        WARM_COASTAL_MANGROVE(Family.MANGROVE, 0.06, 0.27),
        ISOLATED_MUSHROOM_ISLAND(Family.MUSHROOM, 0.38, 0.54);

        private final Family family;
        private final double minimumLatitudeFraction;
        private final double maximumLatitudeFraction;

        Route(Family family, double minimumLatitudeFraction, double maximumLatitudeFraction) {
            this.family = family;
            this.minimumLatitudeFraction = minimumLatitudeFraction;
            this.maximumLatitudeFraction = maximumLatitudeFraction;
        }

        public Family family() { return family; }
        public double minimumLatitudeFraction() { return minimumLatitudeFraction; }
        public double maximumLatitudeFraction() { return maximumLatitudeFraction; }
    }

    private static final int MAX_ATTEMPTS_PER_ID = 4_096;
    private static final double BOUNDARY_WOBBLE = 0.10;
    private static final Map<String, String> VERIFIED_COUNTERPARTS = Map.of();
    private static final Map<String, Route> REQUIREMENTS = buildRequirements();

    @FunctionalInterface
    public interface CandidateEvaluator {
        boolean isEligible(String biomeId, Route route, int blockX, int blockZ);
    }

    public record Anchor(String biomeId, Route route, int blockX, int blockZ, int radiusBlocks,
                         long shapeSalt) {
        public Anchor {
            if (biomeId == null || route == null || radiusBlocks < 96) {
                throw new IllegalArgumentException("invalid surface/water coverage anchor");
            }
        }

        public boolean contains(int x, int z) {
            long dx = (long) x - blockX;
            long dz = (long) z - blockZ;
            long outer = Math.round(radiusBlocks * (1.0 + BOUNDARY_WOBBLE));
            if (Math.abs(dx) > outer || Math.abs(dz) > outer) return false;
            return organicRadialDistance(this, x, z) <= 1.0;
        }
    }

    /** Counts the exact gate reached by a failed deterministic anchor search. */
    public record SearchStats(int centerEligible, int topologyEligible, int overlapRejected) {}

    private final List<Anchor> anchors;
    private final List<String> missingBiomeIds;
    private final Map<String, SearchStats> missingDiagnostics;
    private final int seaLevel;

    private VanillaSurfaceWaterCoveragePlan(List<Anchor> anchors, List<String> missingBiomeIds,
                                             Map<String, SearchStats> missingDiagnostics,
                                             int seaLevel) {
        this.anchors = List.copyOf(anchors);
        this.missingBiomeIds = List.copyOf(missingBiomeIds);
        this.missingDiagnostics = Map.copyOf(missingDiagnostics);
        this.seaLevel = seaLevel;
    }

    public static VanillaSurfaceWaterCoveragePlan build(int worldRadius, long worldSeed,
                                                         int seaLevel,
                                                         CandidateEvaluator evaluator) {
        return build(worldRadius, worldSeed, seaLevel, REQUIREMENTS, evaluator);
    }

    public static VanillaSurfaceWaterCoveragePlan build(int worldRadius, long worldSeed,
                                                         int seaLevel,
                                                         Map<String, Route> targets,
                                                         CandidateEvaluator evaluator) {
        return build(worldRadius, worldSeed, seaLevel, targets, false, evaluator);
    }

    public static VanillaSurfaceWaterCoveragePlan build(int worldRadius, long worldSeed,
                                                         int seaLevel,
                                                         Map<String, Route> targets,
                                                         boolean compactRepresentation,
                                                         CandidateEvaluator evaluator) {
        if (worldRadius <= 0 || evaluator == null) {
            return new VanillaSurfaceWaterCoveragePlan(
                    List.of(), List.copyOf(targets.keySet()), Map.of(), seaLevel);
        }
        List<Anchor> anchors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, SearchStats> diagnostics = new LinkedHashMap<>();
        List<Map.Entry<String, Route>> orderedTargets = new ArrayList<>(targets.entrySet());
        if (compactRepresentation) {
            // Reserve the one terrain-forming island before ordinary ocean-identity provinces.
            // Otherwise a valid Itty island can be rejected merely because an earlier deep-ocean
            // label owns the same water that the island must rise from.
            orderedTargets.sort(java.util.Comparator
                    .comparingInt((Map.Entry<String, Route> entry) ->
                            entry.getValue().family() == Family.MUSHROOM ? 0 : 1)
                    .thenComparing(Map.Entry::getKey));
        }
        for (Map.Entry<String, Route> requirement : orderedTargets) {
            int provinceRadius = provinceRadius(
                    worldRadius, requirement.getValue().family(), compactRepresentation);
            int[] stats = new int[3];
            Anchor anchor = findAnchor(worldRadius, worldSeed, provinceRadius,
                    requirement.getKey(), requirement.getValue(), evaluator, anchors, stats);
            if (anchor == null) {
                missing.add(requirement.getKey());
                diagnostics.put(requirement.getKey(),
                        new SearchStats(stats[0], stats[1], stats[2]));
            }
            else anchors.add(anchor);
        }
        return new VanillaSurfaceWaterCoveragePlan(anchors, missing, diagnostics, seaLevel);
    }

    public Anchor match(Family family, int blockX, int blockZ) {
        for (Anchor anchor : anchors) {
            if (family == anchor.route().family() && anchor.contains(blockX, blockZ)) {
                return anchor;
            }
        }
        return null;
    }

    /**
     * Returns the nearest birth-plan anchor for an exact requested biome identity.
     *
     * <p>This is intentionally independent of vanilla's ordinary 6,400-block scan radius. The
     * plan already did the expensive geography validation once at world birth, so a failed local
     * scan can consult this bounded list instead of rescanning or falsely claiming that a required
     * finite-world biome does not exist.</p>
     */
    public Anchor nearestAnchorFor(Collection<String> biomeIds, int originX, int originZ) {
        if (biomeIds == null || biomeIds.isEmpty()) return null;
        Anchor nearest = null;
        long nearestDistanceSquared = Long.MAX_VALUE;
        for (Anchor anchor : anchors) {
            if (!biomeIds.contains(anchor.biomeId())) continue;
            long dx = (long) anchor.blockX() - originX;
            long dz = (long) anchor.blockZ() - originZ;
            long distanceSquared = dx * dx + dz * dz;
            if (nearest == null || distanceSquared < nearestDistanceSquared) {
                nearest = anchor;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    /** Adds a smooth compact island to the reserved Mushroom Fields province only. */
    public double mushroomDensity(double originalDensity, int blockX, int blockY, int blockZ) {
        Anchor anchor = mushroomAnchor();
        if (anchor == null) return originalDensity;
        double radial = organicRadialDistance(anchor, blockX, blockZ);
        if (radial >= 1.0) return originalDensity;
        double interior = smoothstep(1.0 - radial);
        double targetSurface = mushroomSurface(anchor, blockX, blockZ, interior);
        double islandDensity = (targetSurface - blockY) / 14.0;
        return Math.max(originalDensity, islandDensity);
    }

    public boolean isMushroomLand(int blockX, int blockZ) {
        Anchor anchor = mushroomAnchor();
        if (anchor == null) return false;
        double radial = organicRadialDistance(anchor, blockX, blockZ);
        if (radial >= 1.0) return false;
        double interior = smoothstep(1.0 - radial);
        // Keep at least one entirely solid block above sea level. A biome label at the exact
        // waterline recreates the submerged Mushroom Fields failure this plan replaces.
        return mushroomSurface(anchor, blockX, blockZ, interior) >= seaLevel + 2.0;
    }

    /** Whether the live chunk writer must materialize solid island terrain at this block. */
    public boolean isMushroomSolid(int blockX, int blockY, int blockZ) {
        Anchor anchor = mushroomAnchor();
        if (anchor == null) return false;
        double radial = organicRadialDistance(anchor, blockX, blockZ);
        if (radial >= 1.0) return false;
        double interior = smoothstep(1.0 - radial);
        double targetSurface = mushroomSurface(anchor, blockX, blockZ, interior);
        return targetSurface >= seaLevel + 2.0 && blockY <= Math.floor(targetSurface);
    }

    public List<Anchor> anchors() { return anchors; }
    public List<String> missingBiomeIds() { return missingBiomeIds; }
    public Map<String, SearchStats> missingDiagnostics() { return missingDiagnostics; }
    public boolean complete() { return missingBiomeIds.isEmpty(); }
    public int seaLevel() { return seaLevel; }
    public static Map<String, Route> requirements() { return REQUIREMENTS; }
    public static Map<String, String> verifiedCounterparts() { return VERIFIED_COUNTERPARTS; }

    public String stableFingerprint() {
        StringBuilder out = new StringBuilder(Integer.toString(seaLevel));
        for (Anchor anchor : anchors) {
            out.append('|').append(anchor.biomeId()).append('@')
                    .append(anchor.blockX()).append(',').append(anchor.blockZ()).append(',')
                    .append(anchor.radiusBlocks()).append(',').append(anchor.shapeSalt());
        }
        for (String missing : missingBiomeIds) out.append("|missing:").append(missing);
        return out.toString();
    }

    private Anchor mushroomAnchor() {
        for (Anchor anchor : anchors) {
            if (anchor.route().family() == Family.MUSHROOM) return anchor;
        }
        return null;
    }

    private static Anchor findAnchor(int worldRadius, long worldSeed, int provinceRadius,
                                     String biomeId, Route route, CandidateEvaluator evaluator,
                                     List<Anchor> existing, int[] stats) {
        long baseSalt = mix64(worldSeed ^ ((long) biomeId.hashCode() * 0x9e3779b97f4a7c15L));
        int margin = provinceRadius + 64;
        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_ID; attempt++) {
            long h1 = mix64(baseSalt + attempt * 0x9e3779b97f4a7c15L);
            long h2 = mix64(h1 ^ 0xc2b2ae3d27d4eb4fL);
            double latFraction = route.minimumLatitudeFraction()
                    + unit(h1) * (route.maximumLatitudeFraction() - route.minimumLatitudeFraction());
            int hemisphere = ((h1 >>> 17) & 1L) == 0L ? -1 : 1;
            int z = align16((int) Math.round(worldRadius * latFraction) * hemisphere);
            int maxX = (int) Math.floor(Math.sqrt(Math.max(0.0,
                    (double) (worldRadius - margin) * (worldRadius - margin) - (double) z * z)));
            if (maxX < margin) continue;
            int x = align16((int) Math.round((unit(h2) * 2.0 - 1.0) * maxX));
            if (!evaluator.isEligible(biomeId, route, x, z)) continue;
            stats[0]++;
            if (!hasSubstantialTopology(biomeId, route, x, z, provinceRadius, evaluator)) continue;
            stats[1]++;
            if (overlapsSamePhase(existing, route.family(), x, z, provinceRadius)) {
                stats[2]++;
                continue;
            }
            return new Anchor(biomeId, route, x, z, provinceRadius,
                    mix64(baseSalt ^ 0x3c6ef372fe94f82bL));
        }
        return null;
    }

    private static boolean hasSubstantialTopology(String id, Route route, int x, int z, int radius,
                                                   CandidateEvaluator evaluator) {
        int half = Math.max(48, radius / 2);
        if (route.family() == Family.SHORE || route.family() == Family.RIVER
                || route.family() == Family.MANGROVE) {
            // Coastlines, tidewaters, and rivers are long narrow features, not circular disks.
            // Sample a dense, orientation-independent local grid and require both enough exact
            // eligible columns and a multi-chunk span. The final picker still rechecks the exact
            // physical predicate at every selected column.
            int matches = 0;
            int occupiedRows = 0;
            boolean[] occupiedColumns = new boolean[(half * 2) / 16 + 1];
            for (int dz = -half; dz <= half; dz += 16) {
                boolean rowOccupied = false;
                int columnIndex = 0;
                for (int dx = -half; dx <= half; dx += 16, columnIndex++) {
                    if ((long) dx * dx + (long) dz * dz > (long) half * half) continue;
                    if (evaluator.isEligible(id, route, x + dx, z + dz)) {
                        matches++;
                        rowOccupied = true;
                        occupiedColumns[columnIndex] = true;
                    }
                }
                if (rowOccupied) occupiedRows++;
            }
            int occupiedColumnCount = 0;
            for (boolean occupied : occupiedColumns) {
                if (occupied) occupiedColumnCount++;
            }
            int minimumMatches = route.family() == Family.MANGROVE ? 9 : 6;
            return matches >= minimumMatches
                    && Math.max(occupiedRows, occupiedColumnCount) >= 4;
        }
        return evaluator.isEligible(id, route, x + half, z)
                && evaluator.isEligible(id, route, x - half, z)
                && evaluator.isEligible(id, route, x, z + half)
                && evaluator.isEligible(id, route, x, z - half);
    }

    private static boolean overlapsSamePhase(List<Anchor> existing, Family family,
                                             int x, int z, int radius) {
        for (Anchor anchor : existing) {
            if (!sameSelectionPhase(family, anchor.route().family())) continue;
            long dx = (long) x - anchor.blockX();
            long dz = (long) z - anchor.blockZ();
            long required = Math.round((radius + anchor.radiusBlocks()) * 1.12);
            if (dx * dx + dz * dz < required * required) return true;
        }
        return false;
    }

    private static boolean sameSelectionPhase(Family requested, Family anchor) {
        if (requested == anchor) return true;
        return (requested == Family.OCEAN && anchor == Family.MUSHROOM)
                || (requested == Family.MUSHROOM && anchor == Family.OCEAN);
    }

    private static int provinceRadius(int worldRadius, Family family,
                                      boolean compactRepresentation) {
        return switch (family) {
            // Shores and rivers are linear features. A larger circular reservation demanded an
            // implausibly wide unbroken beach/channel and made Stony Shore disappear in TEST 31.
            case SHORE, RIVER -> 128;
            // A sixteen-chunk-wide coastal reservation is substantial while still fitting real
            // lowland tidewater geometry. Eligibility clips it to the actual wet coast.
            case MANGROVE -> 128;
            // A 16-chunk-diameter island is still substantial in compact worlds and fits real
            // open-ocean pockets that cannot hold V2's full-size 24+ chunk reservation.
            case MUSHROOM -> compactRepresentation && worldRadius <= 7_500
                    ? 128 : Math.max(192, Math.min(384, worldRadius / 18));
            // Twelve chunks across remains a substantial compact ocean province while allowing
            // narrow deep-water climate corridors to satisfy their true depth predicate.
            case OCEAN -> compactRepresentation && worldRadius <= 7_500
                    ? 96 : Math.max(128, Math.min(320, worldRadius / 18));
        };
    }

    private double mushroomSurface(Anchor anchor, int x, int z, double interior) {
        double phaseX = unit(mix64(anchor.shapeSalt() ^ 0x510e527fade682d1L)) * Math.PI * 2.0;
        double phaseZ = unit(mix64(anchor.shapeSalt() ^ 0x1f83d9abfb41bd6bL)) * Math.PI * 2.0;
        double undulation = 2.2 * Math.sin((x - anchor.blockX()) / 43.0 + phaseX)
                + 1.6 * Math.sin((z - anchor.blockZ()) / 59.0 + phaseZ)
                + 1.1 * Math.sin((x + z) / 89.0 + phaseX - phaseZ);
        return seaLevel - 5.0 + interior * (20.0 + undulation);
    }

    private static double organicRadialDistance(Anchor anchor, int x, int z) {
        double dx = x - (double) anchor.blockX();
        double dz = z - (double) anchor.blockZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance == 0.0) return 0.0;
        double angle = Math.atan2(dz, dx);
        double phaseA = unit(anchor.shapeSalt()) * Math.PI * 2.0;
        double phaseB = unit(mix64(anchor.shapeSalt() ^ 0x6a09e667f3bcc909L)) * Math.PI * 2.0;
        double phaseC = unit(mix64(anchor.shapeSalt() ^ 0xbb67ae8584caa73bL)) * Math.PI * 2.0;
        double wobble = 1.0 + BOUNDARY_WOBBLE
                * (0.52 * Math.sin(angle * 3.0 + phaseA)
                + 0.31 * Math.sin(angle * 5.0 + phaseB)
                + 0.17 * Math.sin(angle * 7.0 + phaseC));
        return distance / (anchor.radiusBlocks() * wobble);
    }

    private static Map<String, Route> buildRequirements() {
        Map<String, Route> out = new LinkedHashMap<>();
        out.put("minecraft:warm_ocean", Route.WARM_SHALLOW_OCEAN);
        out.put("minecraft:lukewarm_ocean", Route.LUKEWARM_SHALLOW_OCEAN);
        out.put("minecraft:deep_lukewarm_ocean", Route.LUKEWARM_DEEP_OCEAN);
        out.put("minecraft:ocean", Route.TEMPERATE_SHALLOW_OCEAN);
        out.put("minecraft:deep_ocean", Route.TEMPERATE_DEEP_OCEAN);
        out.put("minecraft:cold_ocean", Route.COLD_SHALLOW_OCEAN);
        out.put("minecraft:deep_cold_ocean", Route.COLD_DEEP_OCEAN);
        out.put("minecraft:frozen_ocean", Route.FROZEN_SHALLOW_OCEAN);
        out.put("minecraft:deep_frozen_ocean", Route.FROZEN_DEEP_OCEAN);
        out.put("minecraft:beach", Route.TEMPERATE_BEACH);
        out.put("minecraft:snowy_beach", Route.COLD_SNOWY_BEACH);
        out.put("minecraft:stony_shore", Route.ROCKY_SHORE);
        out.put("minecraft:river", Route.TEMPERATE_RIVER);
        out.put("minecraft:frozen_river", Route.COLD_RIVER);
        out.put("minecraft:mangrove_swamp", Route.WARM_COASTAL_MANGROVE);
        out.put("minecraft:mushroom_fields", Route.ISOLATED_MUSHROOM_ISLAND);
        return Collections.unmodifiableMap(out.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new)));
    }

    private static int align16(int value) { return Math.floorDiv(value, 16) * 16; }
    private static double smoothstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
