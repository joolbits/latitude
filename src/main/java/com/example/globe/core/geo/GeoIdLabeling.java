package com.example.globe.core.geo;

import java.util.HashMap;
import java.util.Map;

/**
 * OFFLINE connected-component labeling of a {@link GeoAuthority} land/ocean field (flood-fill is
 * allowed here — this is the analyzer/export path, never the worldgen hot path). Rasterizes
 * {@code isOceanIntentAt} over the whole world at a given step, 4-connected union-find (the same
 * connectivity {@code tools/atlas/geography_analyzer.py} uses), majority-votes each id-cell to a
 * component, and bakes a {@link GeoIdTable} plus the macro-geography metrics.
 *
 * <p>Deterministic: derives purely from the field + a fixed raster-scan order + order-independent
 * union-find, so the same {@code (seed, zRadius, xRadius, step)} yields byte-identical tables.
 */
public final class GeoIdLabeling {

    private final int width, height, step, xMin, zMin;
    private final boolean[] land;    // row-major, true = land
    private final int[] root;        // row-major union-find root per pixel
    private final GeoIdTable table;

    private GeoIdLabeling(int width, int height, int step, int xMin, int zMin,
                          boolean[] land, int[] root, GeoIdTable table) {
        this.width = width;
        this.height = height;
        this.step = step;
        this.xMin = xMin;
        this.zMin = zMin;
        this.land = land;
        this.root = root;
        this.table = table;
    }

    public GeoIdTable table() { return table; }
    public int width() { return width; }
    public int height() { return height; }
    public int step() { return step; }
    public int xAt(int col) { return xMin + col * step; }
    public int zAt(int row) { return zMin + row * step; }
    public boolean isLand(int row, int col) { return land[row * width + col]; }
    public int rootAt(int row, int col) { return root[row * width + col]; }

    public static GeoIdLabeling build(GeoAuthority geo, int step) {
        if (step <= 0) throw new IllegalArgumentException("step must be positive");
        int xr = geo.xRadius(), zr = geo.zRadius();
        int width = (2 * xr) / step + 1;
        int height = (2 * zr) / step + 1;
        int xMin = -xr, zMin = -zr;

        boolean[] land = new boolean[width * height];
        for (int r = 0; r < height; r++) {
            int z = zMin + r * step;
            int base = r * width;
            for (int c = 0; c < width; c++) {
                int x = xMin + c * step;
                land[base + c] = !geo.isOceanIntentAt(x, z);
            }
        }

        int[] parent = new int[width * height];
        for (int i = 0; i < parent.length; i++) parent[i] = i;

        // Two-pass 4-connected union-find (up + left neighbors).
        for (int r = 0; r < height; r++) {
            int base = r * width;
            for (int c = 0; c < width; c++) {
                int i = base + c;
                boolean li = land[i];
                if (c > 0 && land[i - 1] == li) union(parent, i, i - 1);
                if (r > 0 && land[i - width] == li) union(parent, i, i - width);
            }
        }
        int[] root = new int[width * height];
        for (int i = 0; i < root.length; i++) root[i] = find(parent, i);

        // Component pixel counts + type.
        Map<Integer, Integer> compSize = new HashMap<>();
        Map<Integer, Boolean> compIsLand = new HashMap<>();
        long landPixels = 0, oceanPixels = 0;
        for (int i = 0; i < root.length; i++) {
            int rt = root[i];
            compSize.merge(rt, 1, Integer::sum);
            compIsLand.putIfAbsent(rt, land[i]);
            if (land[i]) landPixels++; else oceanPixels++;
        }

        // Majority-vote each id-cell to a component root.
        Map<Long, Map<Integer, Integer>> votes = new HashMap<>();
        for (int r = 0; r < height; r++) {
            int z = zMin + r * step;
            int base = r * width;
            for (int c = 0; c < width; c++) {
                int x = xMin + c * step;
                long idKey = geo.idCellKey(x, z);
                votes.computeIfAbsent(idKey, k -> new HashMap<>()).merge(root[base + c], 1, Integer::sum);
            }
        }
        Map<Long, Integer> idcToRoot = new HashMap<>(votes.size() * 2);
        for (Map.Entry<Long, Map<Integer, Integer>> e : votes.entrySet()) {
            int bestRoot = -1, bestCount = -1;
            for (Map.Entry<Integer, Integer> v : e.getValue().entrySet()) {
                // argmax by count, tie-break by smaller root for determinism.
                if (v.getValue() > bestCount || (v.getValue() == bestCount && v.getKey() < bestRoot)) {
                    bestCount = v.getValue();
                    bestRoot = v.getKey();
                }
            }
            idcToRoot.put(e.getKey(), bestRoot);
        }

        // Canonical stable compId per root = mix64(min id-cell key mapped to it ^ typeBit).
        Map<Integer, Long> rootMinKey = new HashMap<>();
        for (Map.Entry<Long, Integer> e : idcToRoot.entrySet()) {
            rootMinKey.merge(e.getValue(), e.getKey(), Math::min);
        }
        Map<Integer, Integer> rootToComp = new HashMap<>();
        for (Map.Entry<Integer, Long> e : rootMinKey.entrySet()) {
            boolean isLand = compIsLand.getOrDefault(e.getKey(), false);
            rootToComp.put(e.getKey(), GeoNoise.typedCompId(e.getValue(), isLand));
        }
        Map<Long, Integer> cellToComp = new HashMap<>(idcToRoot.size() * 2);
        for (Map.Entry<Long, Integer> e : idcToRoot.entrySet()) {
            Integer comp = rootToComp.get(e.getValue());
            if (comp != null) cellToComp.put(e.getKey(), comp);
        }

        GeoIdTable.Metrics metrics = computeMetrics(compSize, compIsLand, landPixels, oceanPixels,
                width, height, step);
        GeoIdTable table = new GeoIdTable(cellToComp, metrics);
        return new GeoIdLabeling(width, height, step, xMin, zMin, land, root, table);
    }

    private static GeoIdTable.Metrics computeMetrics(Map<Integer, Integer> compSize,
                                                     Map<Integer, Boolean> compIsLand,
                                                     long landPixels, long oceanPixels,
                                                     int width, int height, int step) {
        long total = landPixels + oceanPixels;
        int landComps = 0, oceanComps = 0, majorContinents = 0, dominantBasins = 0;
        long largestLand = 0, largestOcean = 0;
        for (Map.Entry<Integer, Integer> e : compSize.entrySet()) {
            boolean isLand = compIsLand.getOrDefault(e.getKey(), false);
            int sz = e.getValue();
            if (isLand) {
                landComps++;
                if (sz > largestLand) largestLand = sz;
                if (landPixels > 0 && sz >= 0.03 * landPixels) majorContinents++;
            } else {
                oceanComps++;
                if (sz > largestOcean) largestOcean = sz;
                if (oceanPixels > 0 && sz >= 0.05 * oceanPixels) dominantBasins++;
            }
        }
        return new GeoIdTable.Metrics(
                total > 0 ? landPixels / (double) total : 0.0,
                landComps,
                landPixels > 0 ? largestLand / (double) landPixels : 0.0,
                majorContinents,
                oceanComps,
                oceanPixels > 0 ? largestOcean / (double) oceanPixels : 0.0,
                dominantBasins,
                width, height, step);
    }

    private static int find(int[] parent, int x) {
        int r = x;
        while (parent[r] != r) r = parent[r];
        while (parent[x] != r) { int nx = parent[x]; parent[x] = r; x = nx; }
        return r;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) {
            if (ra < rb) parent[rb] = ra; else parent[ra] = rb;
        }
    }
}
