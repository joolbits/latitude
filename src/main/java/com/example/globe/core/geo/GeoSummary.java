package com.example.globe.core.geo;

/**
 * Macro-geography summary for a single {@code (blockX, blockZ)} column, per the field contract
 * in {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 *
 * <p>This is a Phase 0 no-op contract: the type exists so Platform Adapter and Mixin Hook code
 * can be written against a stable shape, but nothing populates it with real geography intent
 * yet -- that is Phase 2 (GeoAuthority Prototype) work, gated behind
 * {@link com.example.globe.core.LatitudeV2Flags#GEO_V2_ENABLED}. {@link #NEUTRAL} is the only
 * instance in use while the flag is off; all "*Id" fields use {@code -1} to mean "unassigned",
 * and all "*01" fields default to {@code 0.0}.
 *
 * <p>Pure Java, no Minecraft imports -- Core Logic layer.
 */
public record GeoSummary(
        double land01,
        boolean isOceanIntent,
        int continentId,
        int oceanBasinId,
        double coastDistanceBlocks,
        double shelf01,
        double islandArc01,
        double archipelago01,
        double mountainIntent01,
        int orogenId,
        double ruggednessIntent01,
        double projectionEdgeSuitability01,
        int drainageBasinId,
        double flowDirection,
        int coastOutletId,
        // Phase 5 Slice B-2 (Fix 1): the X-only projection-edge term (= smoothstep(EDGE_START,1,|x|/xR)),
        // exposed separately from projectionEdgeSuitability01 (= max(edgeB, poleB)) so the edge-ocean
        // consumer can bias the east/west border toward ocean WITHOUT touching the icy pole LAND shelf
        // that the poleB component would otherwise convert to frozen ocean (B-1 amendment 1). 0.0 = interior.
        double projectionEdgeXOnly01
) {

    /** Neutral/no-op summary: not land, not ocean, no ids assigned, no intent signaled. */
    public static final GeoSummary NEUTRAL = new GeoSummary(
            0.0, false, -1, -1, 0.0, 0.0, 0.0, 0.0, 0.0, -1, 0.0, 0.0, -1, 0.0, -1, 0.0);
}
