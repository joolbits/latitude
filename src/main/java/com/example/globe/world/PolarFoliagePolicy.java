package com.example.globe.world;

/**
 * Strict polar-foliage boundary and extensible foliage classification.
 *
 * <p>This policy is deliberately independent from the 74.5-degree biome ecology clamp and the
 * village placement policy. General foliage remains eligible through exactly 80 degrees and is
 * suppressed only beyond it. Snowy firefly bushes have a separate, narrower climate exception
 * from the canonical 50-degree Subpolar boundary, while sweet berry bushes remain legal
 * everywhere.
 */
public final class PolarFoliagePolicy {
    public static final double SUBPOLAR_MIN_ABSOLUTE_LATITUDE_DEGREES = 50.0;
    public static final double MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES = 80.0;

    /**
     * Tree line. Woody and tree-derived content (logs, leaves, saplings, forest-floor mushrooms —
     * everything in {@code globe:polar_woody}) stops here, while ordinary ground vegetation
     * continues to {@link #MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES}.
     *
     * <p>Earth is the reference (maintainer ruling, 2026-08-10: "earthlike at 70-72"). The Arctic
     * treeline runs roughly 65-72 degrees north and the northernmost trees on the planet stand at
     * about 72.5; 72.0 is that outer edge. Tundra is emphatically not bare above it — Svalbard at 78
     * has flowering plants and sedges run past 80 — so a SINGLE cliff for all vegetation would be
     * less earthlike than the 80 it replaced, not more. That is why this is a second threshold
     * rather than a change to the first.
     */
    public static final double MAX_WOODY_ABSOLUTE_LATITUDE_DEGREES = 72.0;

    /**
     * A tree may legally begin just inside the 72-degree tree line while its outer leaves extend
     * a few blocks beyond it. Keep those already-started trees whole without moving the latitude
     * at which new trees may begin.
     */
    public static final int WOODY_COMPLETION_BUFFER_BLOCKS = 16;

    private PolarFoliagePolicy() {
    }

    public static double absoluteLatitudeDegrees(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : borderRadiusFallback;
        return Math.abs(blockZ) * 90.0 / Math.max(1, radius);
    }

    public static boolean isBeyondLimit(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        return absoluteLatitudeDegrees(blockZ, activeRadiusBlocks, borderRadiusFallback)
                > MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES;
    }

    public static boolean isBeyondWoodyLimit(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        return absoluteLatitudeDegrees(blockZ, activeRadiusBlocks, borderRadiusFallback)
                > MAX_WOODY_ABSOLUTE_LATITUDE_DEGREES;
    }

    /**
     * Final block writes use a tiny completion band beyond the strict tree-start limit. Feature
     * guards still use {@link #isBeyondWoodyLimit} so a tree cannot start outside 72 degrees.
     */
    public static boolean isBeyondWoodyCompletionLimit(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : borderRadiusFallback;
        double treeLineZ = Math.max(1, radius)
                * MAX_WOODY_ABSOLUTE_LATITUDE_DEGREES / 90.0;
        return Math.abs(blockZ) > treeLineZ + WOODY_COMPLETION_BUFFER_BLOCKS;
    }

    /**
     * Block-level polar suppression, applied to every worldgen block write rather than to a
     * hand-picked pair of feature classes.
     *
     * <p>The feature-class guards ask "is this a {@code TreeFeature} or a {@code SimpleBlockFeature}?"
     * — a question with an unbounded answer set. {@code minecraft:fallen_tree} is a sibling of
     * {@code TreeFeature}, not a subclass, so felled trunks (and the mushrooms its {@code
     * log_decorators} ride on) walked straight past the tree guard with no packs installed; BOP's
     * fallen-log and shrub families, huge mushrooms, glow lichen and {@code block_column} sugar cane
     * did the same. Guarding the block WRITE instead closes all of them at once and cannot be
     * outflanked by a new feature type.
     *
     * <p>{@code vegetationBlock} is the pack-independent half: every vanilla plant and every modded
     * plant that extends the vanilla plant bases answers true, while fluids, snow, ice, terrain and
     * logs structurally cannot — so an untagged modded ground cover (the case where a tag-only test
     * fails OPEN and leaves the polar cap greener than a correctly-guarded biome) is still caught.
     * Woody content is tag-driven because "is this tree-derived?" is a curation question that no
     * class hierarchy answers: a log is not a plant block.
     *
     * <p>Sweet berries keep their exemption at every latitude, which is itself earthlike — berry
     * shrubs are classic tundra.
     */
    public static boolean shouldSuppressPolarBlock(
            boolean beyondWoodyLimit,
            boolean beyondFoliageLimit,
            boolean woody,
            boolean foliage,
            boolean vegetationBlock,
            boolean sweetBerryBush) {
        if (sweetBerryBush) {
            return false;
        }
        if (beyondWoodyLimit && woody) {
            return true;
        }
        return beyondFoliageLimit && (foliage || vegetationBlock);
    }

    public static boolean shouldSuppressSimpleBlock(
            boolean beyondLimit,
            boolean snowySubpolarOrPolar,
            boolean fireflyBush,
            boolean foliage,
            boolean sweetBerryBush) {
        if (sweetBerryBush) {
            return false;
        }
        if (fireflyBush && snowySubpolarOrPolar) {
            return true;
        }
        return beyondLimit && foliage;
    }

    public static boolean isSubpolarOrPolar(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        return absoluteLatitudeDegrees(blockZ, activeRadiusBlocks, borderRadiusFallback)
                >= SUBPOLAR_MIN_ABSOLUTE_LATITUDE_DEGREES;
    }
}
