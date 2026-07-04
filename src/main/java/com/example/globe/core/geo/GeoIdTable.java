package com.example.globe.core.geo;

import java.util.Map;

/**
 * Offline-baked {@code idCell -> connected-component id} remap plus the macro-geography metrics of
 * one {@code (seed, zRadius, xRadius)} field, produced by {@link GeoIdLabeling}. The hot path serves
 * {@code continentId}/{@code oceanBasinId} by a single {@link #compIdForCell} lookup — no flood-fill.
 *
 * <p>Land and ocean component ids live in disjoint namespaces (a type bit is mixed into the id), so
 * a continentId can never equal an oceanBasinId.
 */
public final class GeoIdTable {

    private final Map<Long, Integer> cellToComp;
    private final Metrics metrics;

    public GeoIdTable(Map<Long, Integer> cellToComp, Metrics metrics) {
        this.cellToComp = cellToComp;
        this.metrics = metrics;
    }

    /** Component id for an id-cell key; a stable hash fallback for keys outside the labeled area. */
    public int compIdForCell(long idCellKey) {
        Integer v = cellToComp.get(idCellKey);
        return v != null ? v : GeoNoise.mix64toInt(idCellKey ^ 0x9E3779B97F4A7C15L);
    }

    public int size() {
        return cellToComp.size();
    }

    public Metrics metrics() {
        return metrics;
    }

    /** Macro-geography metrics measured over the labeled raster (raw pixel shares). */
    public record Metrics(
            double landFraction,
            int landComponentCount,
            double largestLandComponentShare,   // of all land pixels
            int majorContinentCount,            // land components >= 3% of land
            int oceanBasinCount,
            double largestOceanBasinShare,      // of all ocean pixels
            int dominantOceanBasinCount,        // basins each >= 5% of ocean
            int width, int height, int step) {
    }
}
