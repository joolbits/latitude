package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage polish, item 1) -- pure band math for the E/W-EDGE structure-free
 * band. Peetsa saw a generated structure standing at/near the world's E/W border (TEST 83) and asked the
 * edge to be structure-free: "the storm belt is wild, empty land." This class answers ONE question with zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM): is a given block-X column within the
 * veto band inward from the nearest E/W (X) world-border edge?
 *
 * <p><b>The distance axis.</b> {@code distToEdge = xRadius - |x - centerX|} -- distance from a column to the
 * nearest E/W (X) world-border edge, the same shape the passage prompt arms on. {@code xRadius} is the square
 * world-border half (the Mercator latitude override only affects Z, never this X radius).
 *
 * <p><b>Band width is a FIXED absolute 500 blocks -- deliberately NOT the visual fog ramp.</b> This is a
 * WORLDGEN placement-determinism concern (which anchors get a structure), invisible to the eye, so it is kept
 * at a stable, generous absolute width and does NOT track the degree-anchored approach fog / prompt geometry
 * ({@link EdgeGeometry}). Keeping it wider than every feature line guarantees a clean, structure-free frontier
 * around the whole edge experience (and the planned B-6 mirror-band seam) regardless of world size, while a
 * pure absolute value keeps the vetoed-anchor set trivially deterministic. On Itty-Bitty Classic (xRadius
 * 3750) 500 is ~13% of the radius per side, leaving the vast interior untouched.
 *
 * <p>The mixin ({@code EdgeStructureVetoMixin}) applies this at the {@code StructureStart.placeInChunk} HEAD
 * (before any block is written -- no half-built structures), keyed on the structure's ANCHOR chunk so every
 * chunk of a multi-chunk structure is vetoed uniformly, and only for SURFACE structures (underground
 * mineshafts are invisible and strongholds carry End access -- both are left alone). Deterministic: the same
 * seed + flag yields the same set of vetoed anchors, because the decision is a pure function of the anchor's
 * X and the border geometry.
 */
public final class EdgeStructureVeto {

    /** Width (blocks) of the structure-free band inward from EACH E/W (X) world-border edge. A FIXED absolute
     *  value (placement-determinism concern) -- deliberately NOT tied to the degree-anchored visual fog ramp;
     *  it stays wider than every feature line so the whole edge experience sits on a clean, empty frontier. */
    public static final double EDGE_BAND_BLOCKS = 500.0;

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
