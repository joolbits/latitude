package com.example.globe.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, donor-cave-gated anchors for V4 underground representation. */
public final class CaveBiomeCoveragePlan {
    public record Anchor(String biomeId, BiomeRoute route, int x, int y, int z,
                         int horizontalRadius, int verticalRadius) {
        public boolean contains(int blockX, int blockY, int blockZ) {
            long dx = (long) blockX - x;
            long dz = (long) blockZ - z;
            return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius
                    && Math.abs(blockY - y) <= verticalRadius;
        }
    }

    @FunctionalInterface
    public interface CandidateEvaluator {
        boolean eligible(BiomeRoute route, int blockX, int blockY, int blockZ);
    }

    private static final int MAX_CANDIDATES_PER_TARGET = 192;
    private final List<Anchor> anchors;
    private final List<String> missingBiomeIds;

    private CaveBiomeCoveragePlan(List<Anchor> anchors, List<String> missingBiomeIds) {
        this.anchors = List.copyOf(anchors);
        this.missingBiomeIds = List.copyOf(missingBiomeIds);
    }

    public static CaveBiomeCoveragePlan build(int worldRadius, long seed,
                                              CaveBiomeRepresentationProfile profile,
                                              CandidateEvaluator evaluator) {
        if (worldRadius <= 0 || profile == null || evaluator == null) {
            return new CaveBiomeCoveragePlan(List.of(), List.of("missing cave coverage authority"));
        }
        Map<String, BiomeRoute> targets = new LinkedHashMap<>();
        CaveBiomeRepresentationProfile.mandatoryIds().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> targets.put(entry.getKey(), entry.getValue()));
        profile.customShowcaseTargets(seed).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> targets.put(entry.getKey(), entry.getValue()));

        int horizontalRadius = Math.max(80, Math.min(224, worldRadius / 24));
        int verticalRadius = 24;
        List<Anchor> anchors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int ordinal = 0;
        for (Map.Entry<String, BiomeRoute> target : targets.entrySet()) {
            Anchor found = findAnchor(worldRadius, seed, ordinal++, target.getKey(), target.getValue(),
                    horizontalRadius, verticalRadius, anchors, evaluator);
            if (found == null) missing.add(target.getKey());
            else anchors.add(found);
        }
        anchors.sort(Comparator.comparing(Anchor::biomeId));
        missing.sort(Comparator.naturalOrder());
        return new CaveBiomeCoveragePlan(anchors, missing);
    }

    public Anchor match(int blockX, int blockY, int blockZ) {
        for (Anchor anchor : anchors) if (anchor.contains(blockX, blockY, blockZ)) return anchor;
        return null;
    }

    public List<Anchor> anchors() { return anchors; }
    public List<String> missingBiomeIds() { return missingBiomeIds; }
    public boolean complete() { return missingBiomeIds.isEmpty(); }

    private static Anchor findAnchor(int radius, long seed, int ordinal, String biomeId, BiomeRoute route,
                                     int horizontalRadius, int verticalRadius, List<Anchor> existing,
                                     CandidateEvaluator evaluator) {
        int safeRadius = Math.max(1, radius - horizontalRadius - 64);
        for (int candidate = 0; candidate < MAX_CANDIDATES_PER_TARGET; candidate++) {
            long mixed = mix64(seed ^ ((long) ordinal << 32) ^ candidate * 0x9E3779B97F4A7C15L);
            double angle = unit(mixed) * Math.PI * 2.0;
            double distance = Math.sqrt(unit(mix64(mixed ^ 0x51A7E5L))) * safeRadius;
            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);
            int y = candidateY(route, mixed);
            if (!evaluator.eligible(route, x, y, z) || overlaps(existing, x, y, z, horizontalRadius, verticalRadius)) continue;
            return new Anchor(biomeId, route, x, y, z, horizontalRadius, verticalRadius);
        }
        return null;
    }

    private static boolean overlaps(List<Anchor> existing, int x, int y, int z,
                                    int horizontalRadius, int verticalRadius) {
        for (Anchor anchor : existing) {
            long dx = (long) x - anchor.x();
            long dz = (long) z - anchor.z();
            int minimum = horizontalRadius + anchor.horizontalRadius() + 96;
            if (dx * dx + dz * dz <= (long) minimum * minimum
                    && Math.abs(y - anchor.y()) <= verticalRadius + anchor.verticalRadius()) return true;
        }
        return false;
    }

    private static int candidateY(BiomeRoute route, long mixed) {
        int[] shallow = {72, 48, 24, 0, -24, -48};
        int[] deep = {-24, -40, -56, -8};
        int[] values = route == BiomeRoute.CAVE_DEEP ? deep : shallow;
        return values[(int) Math.floorMod(mixed, values.length)];
    }

    private static double unit(long mixed) { return ((mixed >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53); }
    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
