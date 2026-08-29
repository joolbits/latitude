package com.example.globe.world;

/**
 * Dependency-free decision for whether a block write is vegetation rooting inside the alpine snow
 * cap, which nothing may do (maintainer ruling, 2026-08-15).
 *
 * <p>The alpine cap rewrites high natural surfaces into rock or snow, and its own note argued that
 * the snow zone therefore leaves no grass block for vegetation to root in. That reasoning only
 * covers the surface pass. Decoration runs afterwards and places plants of its own, so grass and
 * flowers were observed standing directly on snow blocks well above the height where the cap is
 * unconditional. The cap decides what the ground is; this decides what may stand on it, and both
 * read the same surface kind so the snow line and the vegetation line cannot drift apart.
 *
 * <p>Membership is tested two ways for the reason {@code PolarFoliagePolicy} already documents: a
 * tag-only test fails OPEN on any modded plant a pack never tagged, which would leave the cap
 * greener than a correctly guarded one, so inheritance from the vanilla plant base is checked too.
 */
public final class AlpineVegetationPolicy {

    /**
     * The surface kind {@code LatitudeBiomes.alpineSurfaceKind} returns for the snow cap, and the
     * kind {@code AlpineSurfaceMixin} paints as snow. Vegetation is refused for exactly this kind:
     * bare alpine rock and the meadow shelf below the snow line keep theirs.
     */
    public static final int SNOW_SURFACE_KIND = 2;

    private AlpineVegetationPolicy() {
    }

    /**
     * Blocks downward from a plant's own position to the block it is actually rooted on.
     *
     * <p>The upper half of a two-block plant is not rooted on the block beneath it — that is its
     * own lower half — so it must look one further down. Both halves then resolve to the SAME
     * footing position and cannot receive opposite decisions. Judging each half at its own
     * position instead is what let a tall plant keep its lower half and lose its upper one when
     * the snow threshold fell between them.
     */
    public static int footingOffsetBlocks(boolean upperHalfOfDoublePlant) {
        return upperHalfOfDoublePlant ? 2 : 1;
    }

    /**
     * True when this block write is plant material rooted in the snow cap and must not be kept.
     *
     * <p>Both the cap's own surface kind and the block actually present are required. The kind
     * alone would remove a plant rooted on legitimate ground one block below the line, purely for
     * standing at the first height the cap calls snow; the block alone would reach snow the cap
     * never placed. Requiring the real block is also what keeps village crops: farmland is not
     * snow, so a farm plot high on a mountain is left alone.
     *
     * @param footingSurfaceKind the cap's surface kind at the FOOTING position, not the plant's
     * @param footingIsSnowBlock whether the block actually present at the footing is snow
     * @param foliage            whether the block is in the curated foliage tag
     * @param vegetationBlock    whether the block inherits the vanilla plant base
     */
    public static boolean shouldSuppressAlpineVegetation(
            int footingSurfaceKind,
            boolean footingIsSnowBlock,
            boolean foliage,
            boolean vegetationBlock) {
        return footingSurfaceKind == SNOW_SURFACE_KIND
                && footingIsSnowBlock
                && (foliage || vegetationBlock);
    }
}
