package com.example.globe.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Birth-stable coherent provinces that keep route-managed vanilla biomes available in finite
 * Latitude worlds.
 *
 * <p>This plan is deliberately separate from provider fairness. It never substitutes a custom
 * biome by name or tag: a custom counterpart may suppress a vanilla requirement only after it is
 * explicitly entered in {@link #VERIFIED_COUNTERPARTS}. The initial contract has no such
 * substitutions.</p>
 */
public final class VanillaBiomeCoveragePlan {
    private static final int MAX_ATTEMPTS_PER_BIOME = 2_048;
    private static final double BOUNDARY_WOBBLE = 0.12;
    private static final Map<String, String> VERIFIED_COUNTERPARTS = Map.of();
    private static final Map<String, BiomeRoute> REQUIRED_ROUTES = buildRequiredRoutes();

    @FunctionalInterface
    public interface CandidateEvaluator {
        boolean isEligible(String biomeId, BiomeRoute route, int blockX, int blockZ);
    }

    public record Anchor(String biomeId, BiomeRoute route, int blockX, int blockZ, int radiusBlocks,
                         long shapeSalt) {
        public Anchor {
            if (biomeId == null || route == null || radiusBlocks < 1) {
                throw new IllegalArgumentException("invalid vanilla coverage anchor");
            }
        }

        boolean contains(int x, int z) {
            long dx = (long) x - blockX;
            long dz = (long) z - blockZ;
            long outer = Math.round(radiusBlocks * (1.0 + BOUNDARY_WOBBLE));
            if (Math.abs(dx) > outer || Math.abs(dz) > outer) return false;
            double angle = Math.atan2(dz, dx);
            double phaseA = unit(shapeSalt) * Math.PI * 2.0;
            double phaseB = unit(mix64(shapeSalt ^ 0x6a09e667f3bcc909L)) * Math.PI * 2.0;
            double wobble = 1.0 + BOUNDARY_WOBBLE
                    * (0.62 * Math.sin(angle * 3.0 + phaseA)
                    + 0.38 * Math.sin(angle * 5.0 + phaseB));
            double limit = radiusBlocks * wobble;
            return dx * dx + dz * dz <= limit * limit;
        }
    }

    /** Counts the exact gate reached by a failed deterministic anchor search. */
    public record SearchStats(int centerEligible, int topologyEligible, int overlapRejected) {}

    private final List<Anchor> anchors;
    private final List<String> missingBiomeIds;
    private final Map<String, SearchStats> missingDiagnostics;

    private VanillaBiomeCoveragePlan(List<Anchor> anchors, List<String> missingBiomeIds,
                                     Map<String, SearchStats> missingDiagnostics) {
        this.anchors = List.copyOf(anchors);
        this.missingBiomeIds = List.copyOf(missingBiomeIds);
        this.missingDiagnostics = Map.copyOf(missingDiagnostics);
    }

    public static VanillaBiomeCoveragePlan build(int radiusBlocks, long worldSeed,
                                                   BiomeSelectionProfile profile,
                                                   CandidateEvaluator evaluator) {
        return build(radiusBlocks, worldSeed, profile, REQUIRED_ROUTES, evaluator);
    }

    public static VanillaBiomeCoveragePlan build(int radiusBlocks, long worldSeed,
                                                   BiomeSelectionProfile profile,
                                                   Map<String, BiomeRoute> targets,
                                                   CandidateEvaluator evaluator) {
        return build(radiusBlocks, worldSeed, profile, targets, false, evaluator);
    }

    public static VanillaBiomeCoveragePlan build(int radiusBlocks, long worldSeed,
                                                   BiomeSelectionProfile profile,
                                                   Map<String, BiomeRoute> targets,
                                                   boolean compactRepresentation,
                                                   CandidateEvaluator evaluator) {
        if (radiusBlocks <= 0 || profile == null || evaluator == null) {
            return new VanillaBiomeCoveragePlan(
                    List.of(), List.copyOf(targets.keySet()), Map.of());
        }
        List<Anchor> anchors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, SearchStats> diagnostics = new LinkedHashMap<>();
        List<Map.Entry<String, BiomeRoute>> orderedTargets = new ArrayList<>(targets.entrySet());
        orderedTargets.sort(Comparator
                .comparingInt((Map.Entry<String, BiomeRoute> entry) ->
                        DappledForestPlacementPolicy.BIOME_ID.equals(entry.getKey()) ? 1 : 0)
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, BiomeRoute> requirement : orderedTargets) {
            String biomeId = requirement.getKey();
            BiomeRoute route = requirement.getValue();
            String counterpart = VERIFIED_COUNTERPARTS.get(biomeId);
            if (counterpart != null && profile.contains(route, counterpart)) continue;
            if (!profile.contains(route, biomeId)) {
                missing.add(biomeId);
                diagnostics.put(biomeId, new SearchStats(0, 0, 0));
                continue;
            }
            int provinceRadius = provinceRadius(
                    radiusBlocks, biomeId, route, compactRepresentation);
            int[] stats = new int[3];
            Anchor anchor = findAnchor(radiusBlocks, provinceRadius, worldSeed, biomeId, route,
                    evaluator, anchors, stats);
            if (anchor == null) {
                missing.add(biomeId);
                diagnostics.put(biomeId, new SearchStats(stats[0], stats[1], stats[2]));
            }
            else anchors.add(anchor);
        }
        return new VanillaBiomeCoveragePlan(anchors, missing, diagnostics);
    }

    public Anchor match(int blockX, int blockZ) {
        for (Anchor anchor : anchors) {
            if (anchor.contains(blockX, blockZ)) return anchor;
        }
        return null;
    }

    /** All reservations covering a column, in stable biome-ID order. */
    public List<Anchor> matches(int blockX, int blockZ) {
        List<Anchor> matches = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.contains(blockX, blockZ)) matches.add(anchor);
        }
        return List.copyOf(matches);
    }

    /** Returns the nearest birth-plan anchor for an exact requested biome identity. */
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

    public List<Anchor> anchors() { return anchors; }
    public List<String> missingBiomeIds() { return missingBiomeIds; }
    public Map<String, SearchStats> missingDiagnostics() { return missingDiagnostics; }
    public boolean complete() { return missingBiomeIds.isEmpty(); }
    public static Map<String, BiomeRoute> requiredRoutes() { return REQUIRED_ROUTES; }
    public static Map<String, String> verifiedCounterparts() { return VERIFIED_COUNTERPARTS; }

    private static Anchor findAnchor(int worldRadius, int provinceRadius, long worldSeed,
                                     String biomeId, BiomeRoute route, CandidateEvaluator evaluator,
                                     List<Anchor> existing, int[] stats) {
        if (DappledForestPlacementPolicy.BIOME_ID.equals(biomeId)) {
            return findDappledAnchor(
                    worldRadius, provinceRadius, worldSeed, biomeId, route,
                    evaluator, existing, stats);
        }
        long baseSalt = mix64(worldSeed ^ biomeId.hashCode() * 0x9e3779b97f4a7c15L);
        double[] latRange = latitudeRange(route);
        int margin = provinceRadius + 48;
        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_BIOME; attempt++) {
            long h1 = mix64(baseSalt + attempt * 0x9e3779b97f4a7c15L);
            long h2 = mix64(h1 ^ 0xc2b2ae3d27d4eb4fL);
            double latFraction = latRange[0] + unit(h1) * (latRange[1] - latRange[0]);
            int hemisphere = ((h1 >>> 17) & 1L) == 0L ? -1 : 1;
            int z = align16((int) Math.round(worldRadius * latFraction) * hemisphere);
            int maxX = (int) Math.floor(Math.sqrt(Math.max(0.0,
                    (double) (worldRadius - margin) * (worldRadius - margin) - (double) z * z)));
            if (maxX < margin) continue;
            int x = align16((int) Math.round((unit(h2) * 2.0 - 1.0) * maxX));
            if (!evaluator.isEligible(biomeId, route, x, z)) continue;
            stats[0]++;
            if (!evaluator.isEligible(biomeId, route, x + provinceRadius / 2, z)
                    || !evaluator.isEligible(biomeId, route, x - provinceRadius / 2, z)
                    || !evaluator.isEligible(biomeId, route, x, z + provinceRadius / 2)
                    || !evaluator.isEligible(biomeId, route, x, z - provinceRadius / 2)) continue;
            stats[1]++;
            if (!hasDistinctVisibleCore(existing, route, x, z)) {
                stats[2]++;
                continue;
            }
            return new Anchor(biomeId, route, x, z, provinceRadius,
                    mix64(baseSalt ^ 0x3c6ef372fe94f82bL));
        }
        return null;
    }

    /**
     * Exhaustive, seed-rotated search of Dappled's narrow cool-border corridor.
     *
     * <p>The general planner spends a fixed number of random attempts across a route's whole
     * latitude range. That is appropriate for broad routes, but Dappled intentionally owns only
     * one half of one transition envelope and must also route around wetlands, mountains, Pale
     * Garden country, and earlier same-route reservations. This sweep visits each aligned center
     * in that corridor once, in a seed-dependent order, so a valid province cannot be missed by
     * bad sampling luck.</p>
     */
    private static Anchor findDappledAnchor(
            int worldRadius,
            int provinceRadius,
            long worldSeed,
            String biomeId,
            BiomeRoute route,
            CandidateEvaluator evaluator,
            List<Anchor> existing,
            int[] stats) {
        long baseSalt = mix64(worldSeed ^ biomeId.hashCode() * 0x9e3779b97f4a7c15L);
        double[] latRange = latitudeRange(route);
        int minimumAbsZ = align16Ceil((int) Math.ceil(worldRadius * latRange[0]));
        int maximumAbsZ = align16((int) Math.floor(worldRadius * latRange[1]));
        if (maximumAbsZ < minimumAbsZ) {
            return null;
        }
        int zCount = (maximumAbsZ - minimumAbsZ) / 16 + 1;
        int firstZ = (int) Long.remainderUnsigned(baseSalt, zCount);
        int firstHemisphere = (baseSalt & 1L) == 0L ? -1 : 1;
        int margin = provinceRadius + 48;
        int usableRadius = worldRadius - margin;
        if (usableRadius <= 0) {
            return null;
        }

        for (int zOffset = 0; zOffset < zCount; zOffset++) {
            int absZ = minimumAbsZ + ((firstZ + zOffset) % zCount) * 16;
            for (int hemisphereOffset = 0; hemisphereOffset < 2; hemisphereOffset++) {
                int hemisphere = hemisphereOffset == 0 ? firstHemisphere : -firstHemisphere;
                int z = absZ * hemisphere;
                int maxX = align16((int) Math.floor(Math.sqrt(Math.max(0.0,
                        (double) usableRadius * usableRadius - (double) z * z))));
                if (maxX < 0) continue;
                int xCount = maxX * 2 / 16 + 1;
                long rowSalt = mix64(baseSalt ^ ((long) z * 0xc2b2ae3d27d4eb4fL));
                int firstX = (int) Long.remainderUnsigned(rowSalt, xCount);
                for (int xOffset = 0; xOffset < xCount; xOffset++) {
                    int x = -maxX + ((firstX + xOffset) % xCount) * 16;
                    if (!evaluator.isEligible(biomeId, route, x, z)) continue;
                    stats[0]++;
                    if (!evaluator.isEligible(biomeId, route, x + provinceRadius / 2, z)
                            || !evaluator.isEligible(biomeId, route, x - provinceRadius / 2, z)
                            || !evaluator.isEligible(biomeId, route, x, z + provinceRadius / 2)
                            || !evaluator.isEligible(biomeId, route, x, z - provinceRadius / 2)
                            || !hasDappledVisibleCore(evaluator, biomeId, route, x, z)) {
                        continue;
                    }
                    stats[1]++;
                    if (!hasDappledDistinctVisibleCore(
                            existing, route, x, z, provinceRadius)) {
                        stats[2]++;
                        continue;
                    }
                    return new Anchor(biomeId, route, x, z, provinceRadius,
                            mix64(baseSalt ^ ((long) x << 32) ^ z));
                }
            }
        }
        return null;
    }

    private static boolean hasDappledVisibleCore(
            CandidateEvaluator evaluator,
            String biomeId,
            BiomeRoute route,
            int centerX,
            int centerZ) {
        int radius = DappledForestPlacementPolicy.VISIBLE_CORE_RADIUS_BLOCKS;
        for (int offsetZ = -radius; offsetZ <= radius; offsetZ += 16) {
            for (int offsetX = -radius; offsetX <= radius; offsetX += 16) {
                if (!evaluator.isEligible(
                        biomeId, route, centerX + offsetX, centerZ + offsetZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasDappledDistinctVisibleCore(
            List<Anchor> existing,
            BiomeRoute route,
            int centerX,
            int centerZ,
            int provinceRadius) {
        int radius = DappledForestPlacementPolicy.VISIBLE_CORE_RADIUS_BLOCKS;
        for (int offsetZ = -radius; offsetZ <= radius; offsetZ += 16) {
            for (int offsetX = -radius; offsetX <= radius; offsetX += 16) {
                for (Anchor anchor : existing) {
                    if (anchor.route() == route
                            && anchor.contains(centerX + offsetX, centerZ + offsetZ)) {
                        return false;
                    }
                }
            }
        }
        int shoulder = provinceRadius / 2;
        int[][] shoulderOffsets = {
                {shoulder, 0}, {-shoulder, 0}, {0, shoulder}, {0, -shoulder}
        };
        for (int[] offset : shoulderOffsets) {
            for (Anchor anchor : existing) {
                if (anchor.route() == route
                        && anchor.contains(centerX + offset[0], centerZ + offset[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean hasDistinctVisibleCore(List<Anchor> existing, BiomeRoute route, int x, int z) {
        // Same-route provinces may share their outer shoulders on a narrow mountain ridge. The
        // earlier anchor wins wherever they overlap, so preserve a 32-block-wide unshadowed core
        // around every later center. This admits finite-world capacity without allowing a hidden
        // reservation or a one-chunk identity token.
        int[][] coreSamples = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}};
        for (int[] sample : coreSamples) {
            for (Anchor anchor : existing) {
                // Different physical routes are mutually exclusive at final selection and can
                // safely share map area; only an earlier identity in the same route can shadow us.
                if (anchor.route() == route && anchor.contains(x + sample[0], z + sample[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double[] latitudeRange(BiomeRoute route) {
        return switch (route) {
            case TROPICAL_HUMID_LOWLAND -> new double[]{0.05, 0.23};
            case SUBTROPICAL_HUMID_LOWLAND, WARM_TRANSITION, WARM_UPLAND,
                    ARID_LOWLAND, ARID_UPLAND -> new double[]{0.28, 0.38};
            case TEMPERATE_LOWLAND, TEMPERATE_WETLAND, TEMPERATE_UPLAND -> new double[]{0.42, 0.55};
            // SUBPOLAR_UPLAND sits in the subpolar band's own latitudes, not COLD_UPLAND's wider
            // subpolar-through-polar span: the windswept family it carries stops at 66.5 degrees.
            case SUBPOLAR_LOWLAND, SUBPOLAR_WETLAND, SUBPOLAR_UPLAND -> new double[]{0.60, 0.72};
            case POLAR_LOWLAND -> new double[]{0.78, 0.90};
            case COLD_UPLAND -> new double[]{0.62, 0.88};
            case CAVE_SHALLOW, CAVE_DEEP -> throw new IllegalArgumentException("cave routes use CaveBiomeCoveragePlan");
        };
    }

    private static int provinceRadius(int worldRadius, String biomeId, BiomeRoute route,
                                      boolean compactRepresentation) {
        if (compactRepresentation
                && DappledForestPlacementPolicy.BIOME_ID.equals(biomeId)) {
            return 96;
        }
        return switch (route) {
            // Eight chunks across is still a visibly substantial province, but it fits the width
            // of a coherent vanilla ridge. Requiring the lowland radius here made every sampled
            // point across a 224-block disk satisfy rare mountain noise simultaneously.
            case TEMPERATE_UPLAND, SUBPOLAR_UPLAND, COLD_UPLAND, WARM_UPLAND, ARID_UPLAND ->
                    compactRepresentation && worldRadius <= 7_500 ? 48 : 64;
            // Desert and one explicit Badlands-family representative may legitimately coexist in
            // the same arid corridor. A 320-block diameter remains a substantial province while
            // leaving room for both identities to retain distinct visible cores in Small worlds.
            case ARID_LOWLAND -> compactRepresentation
                    ? Math.max(96, Math.min(160, worldRadius / 30))
                    : Math.max(96, Math.min(224, worldRadius / 30));
            // Wetlands answer the same disease the upland branch above records, for the same
            // reason: an anchor requires the centre AND four probes at +/-radius/2 to satisfy the
            // route together, so a wide disk demands rare, fast-oscillating noise hold across 224
            // blocks in four directions at once. Measured on certified beta.3 bytes, 12 seeds,
            // end-to-end fresh-world boots reading the plan's own diagnostics: at the 224 default
            // the swamp province fails to anchor on 4 of 12 seeds on this line and 3 of 12 on the
            // 1.21.11 line; at 112 it anchors on every seed tested on both lines. 112 is the
            // LARGEST radius that clears the sample -- the smallest departure from the shipped
            // geometry that fixes it, chosen over 64 (which also clears) to keep the province
            // substantial.
            //
            // BE CLEAR ABOUT WHAT THIS IS: a geometry MITIGATION, not the principled fix. The
            // actual defect is that findAnchor's topology test is a four-point AND over a sparse
            // field; shrinking the radius only makes those four shoulders more correlated so the
            // conjunction survives. The defect stays latent for any future route whose field is
            // sparser than swamp's -- this is the third occurrence in this file already (uplands,
            // eroded_badlands per the ARID_UPLAND note, now wetland), each cured by shrinking a
            // radius. The principled form already exists one branch over:
            // VanillaSurfaceWaterCoveragePlan.hasSubstantialTopology samples a dense 16-block grid
            // and requires PROPORTIONAL eligibility plus a multi-chunk span, explicitly because
            // linear features are not circular disks -- but ONLY for shore, river, mangrove and
            // deep-ocean routes. Every other family there falls through to this same four-point
            // AND. Occurrence four extends that branch to the rest; it does not shrink a radius.
            case TEMPERATE_WETLAND, SUBPOLAR_WETLAND -> Math.min(112, Math.max(96, Math.min(224, worldRadius / 30)));
            case CAVE_SHALLOW, CAVE_DEEP -> throw new IllegalArgumentException("cave routes use CaveBiomeCoveragePlan");
            default -> Math.max(96, Math.min(224, worldRadius / 30));
        };
    }

    private static Map<String, BiomeRoute> buildRequiredRoutes() {
        Map<String, BiomeRoute> routes = new LinkedHashMap<>();
        add(routes, BiomeRoute.TROPICAL_HUMID_LOWLAND,
                "minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:sparse_jungle");
        add(routes, BiomeRoute.TEMPERATE_LOWLAND,
                "minecraft:birch_forest", "minecraft:dappled_forest", "minecraft:dark_forest", "minecraft:flower_forest",
                "minecraft:forest", "minecraft:old_growth_birch_forest",
                "minecraft:old_growth_pine_taiga", "minecraft:plains",
                "minecraft:sunflower_plains", "minecraft:taiga");
        add(routes, BiomeRoute.TEMPERATE_WETLAND, "minecraft:swamp");
        add(routes, BiomeRoute.TEMPERATE_UPLAND,
                "minecraft:cherry_grove", "minecraft:grove", "minecraft:meadow",
                "minecraft:stony_peaks");
        add(routes, BiomeRoute.WARM_TRANSITION, "minecraft:savanna");
        add(routes, BiomeRoute.WARM_UPLAND,
                "minecraft:savanna_plateau", "minecraft:windswept_savanna");
        // eroded_badlands is guaranteed on ARID_LOWLAND, not ARID_UPLAND (maintainer ruling,
        // 2026-08-12). ARID_UPLAND asks for mountain terrain AND a WARM_DRY province at every
        // sampled point of the reservation, and those are independent sparse fields: on the live
        // Regular seed 8507730871486520283 not one of 66 center-eligible candidates kept all four
        // shoulders eligible, so the guarantee could never be issued. Lowland is what the rest of
        // the mod already says — VanillaBiomeRepresentationProfile routes BADLANDS_UPLAND_VARIANT
        // to ARID_LOWLAND, the ledger admits it there, and the picker places eroded_badlands with
        // no mountain requirement at all. This map guarantees identity availability; it does not
        // owe every BiomeRoute a reservation, and ARID_UPLAND stays a legal ledger route.
        add(routes, BiomeRoute.ARID_LOWLAND,
                "minecraft:badlands", "minecraft:desert", "minecraft:eroded_badlands",
                "minecraft:wooded_badlands");
        add(routes, BiomeRoute.SUBPOLAR_LOWLAND,
                "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga");
        add(routes, BiomeRoute.POLAR_LOWLAND, "minecraft:ice_spikes", "minecraft:snowy_plains");
        // windswept_* moved TEMPERATE_UPLAND -> COLD_UPLAND with the ledger (maintainer ruling,
        // 2026-08-10), then COLD_UPLAND -> SUBPOLAR_UPLAND (2026-08-18, after a vanilla-only census
        // found windswept_hills was the second most common land biome at the pole). This map is the
        // vanilla-coverage guarantee and must name the SAME route the ledger routes each identity
        // to, or the plan anchors a biome into a band the picker will never choose it in — the
        // suite asserts that agreement and caught both moves.
        add(routes, BiomeRoute.SUBPOLAR_UPLAND,
                "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills",
                "minecraft:windswept_hills");
        add(routes, BiomeRoute.COLD_UPLAND,
                "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:snowy_slopes");
        return Collections.unmodifiableMap(routes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new)));
    }

    private static void add(Map<String, BiomeRoute> routes, BiomeRoute route, String... ids) {
        for (String id : ids) {
            if (routes.putIfAbsent(id, route) != null) {
                throw new IllegalStateException("duplicate vanilla coverage route: " + id);
            }
        }
    }

    private static int align16(int value) { return Math.floorDiv(value, 16) * 16; }
    private static int align16Ceil(int value) { return -Math.floorDiv(-value, 16) * 16; }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
