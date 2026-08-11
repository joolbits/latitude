package com.example.globe.world;

/** Pure arithmetic policy for keeping biome-locate work finite and testable. */
public final class LatitudeLocateBudgetPolicy {
    public static final int MAX_SURFACE_HORIZONTAL_RINGS = 50;
    public static final int MAX_SURFACE_EXACT_VERIFICATIONS = 256;
    public static final int MAX_SURFACE_EXACT_FALLBACK_RINGS = 16;
    public static final int MAX_THREE_DIMENSIONAL_SAMPLES = 20_000;
    public static final long MAX_WETLAND_LOCATE_TICK_NANOS = 8_000_000L;
    public static final int MAX_WETLAND_GRID_PROBES_PER_TICK = 4_096;
    public static final int MAX_WETLAND_EXACT_PROBES_PER_TICK = 1;
    public static final long MAX_BIOME_LOCATE_TICK_NANOS = 8_000_000L;
    public static final int MAX_SURFACE_PREVIEW_PROBES_PER_TICK = 4_096;
    public static final int MAX_THREE_DIMENSIONAL_EXACT_PROBES_PER_TICK = 8;

    private LatitudeLocateBudgetPolicy() {
    }

    public static int surfaceHorizontalStep(int radius, int requestedStep) {
        return horizontalStepForRings(radius, requestedStep, MAX_SURFACE_HORIZONTAL_RINGS);
    }

    public static int surfaceExactFallbackHorizontalStep(int radius, int requestedStep) {
        return horizontalStepForRings(radius, requestedStep, MAX_SURFACE_EXACT_FALLBACK_RINGS);
    }

    public static int threeDimensionalHorizontalStep(
            int radius,
            int requestedStep,
            int verticalSamples) {
        int safeVerticalSamples = Math.max(1, verticalSamples);
        int horizontalBudget = Math.max(1, MAX_THREE_DIMENSIONAL_SAMPLES / safeVerticalSamples);
        int side = (int) Math.floor(Math.sqrt(horizontalBudget));
        if ((side & 1) == 0) {
            side--;
        }
        int rings = Math.max(0, (side - 1) / 2);
        return horizontalStepForRings(radius, requestedStep, rings);
    }

    public static int worstCaseSamples(int radius, int horizontalStep, int verticalSamples) {
        int rings = Math.max(0, radius) / Math.max(1, horizontalStep);
        long side = 2L * rings + 1L;
        long samples = side * side * Math.max(1, verticalSamples);
        return samples > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) samples;
    }

    /**
     * Smallest square search radius that covers every playable coordinate in a Latitude world
     * from the command origin. The caller still rejects probes outside the world border.
     */
    public static int fullWorldSearchRadius(int originX, int originZ, int worldRadius) {
        long safeWorldRadius = Math.max(0L, worldRadius);
        long xReach = safeWorldRadius + Math.abs((long) originX);
        long zReach = safeWorldRadius + Math.abs((long) originZ);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(xReach, zReach));
    }

    /** Exact block coordinate represented by the center of a four-block biome quart cell. */
    public static int quartCenterBlock(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 4) * 4 + 2;
    }

    /**
     * Whether a climate-valid swamp point can still satisfy the requested wetland identity.
     *
     * <p>Swamp searches keep every swamp proxy. Mangrove-only searches keep swamp proxies only
     * in bands where the final identity law can promote swamp to mangrove. Direct mangrove
     * candidates are evaluated independently by the caller.
     */
    public static boolean allowsSwampProxyForTarget(
            boolean includeSwamp,
            boolean includeMangrove,
            int landBandIndex,
            int maxMangroveBandIndex) {
        return includeSwamp
                || (includeMangrove
                        && landBandIndex >= 0
                        && landBandIndex <= maxMangroveBandIndex);
    }

    /**
     * Whether a registry-backed, terrain-free preview still needs the expensive exact resolver.
     * Ocean, river, and beach are retained because real surface evidence can bypass their early
     * shoreline returns before the normal land picker runs.
     */
    public static boolean sourcePreviewCanBecomeWetland(
            boolean previewIsSwamp,
            boolean previewIsMangrove,
            boolean previewIsOcean,
            boolean previewIsRiver,
            boolean previewIsBeach,
            boolean rawBaseIsShoreline) {
        return previewIsSwamp
                || previewIsMangrove
                || previewIsOcean
                || previewIsRiver
                || previewIsBeach
                || rawBaseIsShoreline;
    }

    private static int horizontalStepForRings(int radius, int requestedStep, int maxRings) {
        int safeRadius = Math.max(0, radius);
        int safeRequestedStep = Math.max(1, requestedStep);
        if (maxRings <= 0) {
            return Math.max(safeRequestedStep, safeRadius == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : safeRadius + 1);
        }
        int budgetStep = (int) Math.min(
                Integer.MAX_VALUE,
                ((long) safeRadius + maxRings - 1L) / maxRings);
        return Math.max(safeRequestedStep, Math.max(1, budgetStep));
    }
}
