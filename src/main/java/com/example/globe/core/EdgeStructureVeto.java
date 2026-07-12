package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage polish, item 1) -- pure band math for the E/W-EDGE structure-free
 * band. Peetsa saw a generated structure standing at/near the world's E/W border (TEST 83) and asked the
 * edge to be structure-free: "the storm belt is wild, empty land." This class answers ONE question with zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM): is a given block-X column within the
 * veto band inward from the nearest E/W (X) world-border edge?
 *
 * <p><b>The distance axis (shared with the whole edge story).</b> {@code distToEdge = xRadius - |x - centerX|}
 * -- the exact quantity the passage prompt arms on ({@code GlobeClientState.distanceToEwBorderBlocks}) and the
 * server re-validates a cross on ({@code GlobeMod.distanceToEwEdgeBlocks}). {@code xRadius} is
 * {@code halfSize(border)} (the square world-border half; the Mercator latitude override only affects Z, never
 * this X radius), so one convention drives the fog, the prompt, and now the veto.
 *
 * <p><b>Band width == the fog band.</b> {@link #EDGE_BAND_BLOCKS} is 500, matching {@link
 * HemispherePassage#FOG_START}: the structure-free strip is exactly the visible approach-fog belt, so the two
 * read as one thing -- the wild, empty, storm-hazed frontier before the crossing. Absolute blocks (not a
 * fraction) so it fits sanely inside the smallest world: on Itty-Bitty Classic (xRadius 3750) 500 is ~13% of
 * the radius per side, leaving the vast interior untouched.
 *
 * <p>The mixin ({@code EdgeStructureVetoMixin}) applies this at the {@code StructureStart.placeInChunk} HEAD
 * (before any block is written -- no half-built structures), keyed on the structure's ANCHOR chunk so every
 * chunk of a multi-chunk structure is vetoed uniformly, and only for SURFACE structures (underground
 * mineshafts are invisible and strongholds carry End access -- both are left alone). Deterministic: the same
 * seed + flag yields the same set of vetoed anchors, because the decision is a pure function of the anchor's
 * X and the border geometry.
 */
public final class EdgeStructureVeto {

    /** Width (blocks) of the structure-free band inward from EACH E/W (X) world-border edge. Matches
     *  {@link HemispherePassage#FOG_START} (500) so the empty strip and the visible approach fog are one belt. */
    public static final double EDGE_BAND_BLOCKS = HemispherePassage.FOG_START; // 500

    private EdgeStructureVeto() {
    }

    /**
     * True iff the column at {@code blockX} lies within {@code bandWidth} blocks of the nearest E/W (X)
     * world-border edge, i.e. {@code xRadius - |blockX - centerX| <= bandWidth}. Degenerate geometry
     * ({@code xRadius <= 0} or {@code bandWidth <= 0}) returns {@code false} (never veto), so a bad border read
     * fails open. A column at or beyond the edge ({@code distToEdge <= 0}) is inside the band too.
     */
    public static boolean inEdgeBand(double blockX, double centerX, double xRadius, double bandWidth) {
        if (!(xRadius > 0.0) || !(bandWidth > 0.0)) {
            return false;
        }
        double distToEdge = xRadius - Math.abs(blockX - centerX);
        return distToEdge <= bandWidth;
    }
}
