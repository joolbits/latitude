package com.example.globe.core.geo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.globe.core.geo.GeoNoise.clamp;
import static com.example.globe.core.geo.GeoNoise.clamp01;
import static com.example.globe.core.geo.GeoNoise.hash01;
import static com.example.globe.core.geo.GeoNoise.mix64;
import static com.example.globe.core.geo.GeoNoise.mix64toInt;
import static com.example.globe.core.geo.GeoNoise.packCell;
import static com.example.globe.core.geo.GeoNoise.smoothstep;
import static com.example.globe.core.geo.GeoNoise.valueNoise;
import static com.example.globe.core.geo.GeoAuthorityParams.*;

/**
 * GeoAuthority — deterministic macro-geography ("Inverted-Plate Continentality", Phase 2).
 *
 * <p>Pure Java, zero Minecraft imports (Core Logic layer). Produces coherent continents and a
 * connected ocean sheet from {@code (worldSeed, zRadius, xRadius)} alone. Land is the minority upper
 * tail of a Jacobian-safe domain-warped 4-octave continentality FBM, biased by a rank-normalized
 * Worley plate-continentalness field; ocean is therefore the connected complement (a dominant basin
 * emerges by construction). See {@code docs/design/geoauthority-design-20260703.md}.
 *
 * <p>The land/ocean/coast/derived FIELD is fully correct from the constructor alone — the only
 * per-world precompute is {@link #plateNorm} (rank-normalize ~209 plate values; cheap, bounded, NOT a
 * flood-fill). Connected-component {@code continentId}/{@code oceanBasinId} come from an optional
 * {@link GeoIdTable} built offline by {@link GeoIdLabeling}; with no table attached, ids fall back to
 * the (coherent but not connected-component-correct) plate id.
 *
 * <p>Deterministic and stateless after construction: {@link #sample} is a pure function of its args
 * and the immutable precomputes, so consecutive world loads with the same seed/radii are identical.
 */
public final class GeoAuthority {

    private final long seed;
    private final int zRadius;
    private final int xRadius;

    // Derived block-space scales (radius-relative; computed once).
    private final int lc, lwarp, warpAmp, lplate, lid, shelfW, arcWL, mtnWL;
    private final double coastClamp, coastBand;

    private final Map<Long, Double> plateNorm;   // plate cell key -> rank-normalized continentalness [0,1]
    private volatile GeoIdTable idTable;         // optional; null => plate-id fallback

    public GeoAuthority(long seed, int zRadius, int xRadius) {
        if (zRadius <= 0 || xRadius <= 0) {
            throw new IllegalArgumentException("radii must be positive: z=" + zRadius + " x=" + xRadius);
        }
        this.seed = seed;
        this.zRadius = zRadius;
        this.xRadius = xRadius;
        this.lc = Math.max(1, (int) Math.round(LC_RATIO * zRadius));
        this.lwarp = Math.max(1, (int) Math.round(LWARP_RATIO * zRadius));
        // Sweeper audit #2 finding #12/#25 (2026-07-05): every sibling radius-relative scale on this
        // line and below is floored at 1 via Math.max(1, ...); this one wasn't, so zRadius<=8 rounded
        // warpAmp to 0 and silently collapsed the domain warp to identity (warpedX==x) instead of
        // erroring -- unreachable at any canonical world size (itty=3750+ gives warpAmp=225) but an
        // inconsistency vs. every other scale here. Floored for the same robustness/consistency reason.
        this.warpAmp = Math.max(1, (int) Math.round(WARP_AMP_RATIO * zRadius));
        this.lplate = Math.max(1, (int) Math.round(LPLATE_RATIO * zRadius));
        this.lid = Math.max(1, (int) Math.round(LID_RATIO * zRadius));
        this.shelfW = Math.max(1, (int) Math.round(SHELF_W_RATIO * zRadius));
        this.arcWL = Math.max(1, (int) Math.round(ARC_WL_RATIO * zRadius));
        this.mtnWL = Math.max(1, (int) Math.round(MTN_WL_RATIO * zRadius));
        this.coastClamp = COAST_CLAMP_LC_FRAC * lc;
        this.coastBand = COAST_BAND_LID_FRAC * lid;
        this.plateNorm = buildPlateNorm();
    }

    public long seed() { return seed; }
    public int zRadius() { return zRadius; }
    public int xRadius() { return xRadius; }
    public int idCellSize() { return lid; }
    public int plateCellSize() { return lplate; }
    public double coastBandBlocks() { return coastBand; }

    /** Attach an offline-built connected-component id table (enables true continent/basin ids). */
    public void attachIdTable(GeoIdTable table) {
        this.idTable = table;
    }

    public GeoIdTable idTable() {
        return idTable;
    }

    // --- rank-normalized plate continentalness (cheap per-world precompute; no flood-fill) ---
    private Map<Long, Double> buildPlateNorm() {
        int ciMin = Math.floorDiv(-xRadius, lplate) - 1;
        int ciMax = Math.floorDiv(xRadius, lplate) + 1;
        int cjMin = Math.floorDiv(-zRadius, lplate) - 1;
        int cjMax = Math.floorDiv(zRadius, lplate) + 1;
        List<long[]> entries = new ArrayList<>();  // [key, rawBitsOrderProxy] — sort by raw value
        List<Double> raws = new ArrayList<>();
        List<Long> keys = new ArrayList<>();
        for (int ci = ciMin; ci <= ciMax; ci++) {
            for (int cj = cjMin; cj <= cjMax; cj++) {
                keys.add(packCell(ci, cj));
                raws.add(hash01(seed, ci, cj, S_PLATECONT));
            }
        }
        int n = keys.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // Stable sort by raw value, tie-break by key for full determinism.
        final List<Double> fr = raws;
        final List<Long> fk = keys;
        java.util.Arrays.sort(order, (a, b) -> {
            int c = Double.compare(fr.get(a), fr.get(b));
            return c != 0 ? c : Long.compare(fk.get(a), fk.get(b));
        });
        Map<Long, Double> map = new HashMap<>(n * 2);
        for (int rank = 0; rank < n; rank++) {
            double norm = n <= 1 ? 0.5 : rank / (double) (n - 1);
            map.put(keys.get(order[rank]), norm);
        }
        return map;
    }

    // Warped coordinates (exposed for the no-fold invariant test T12: warpedX must be monotone
    // non-decreasing in x, i.e. the warp is bijective / never folds).
    public int warpedX(int x, int z) {
        return x + (int) Math.round((valueNoise(seed ^ S_WX, x, z, lwarp) - 0.5) * 2.0 * warpAmp);
    }

    public int warpedZ(int x, int z) {
        return z + (int) Math.round((valueNoise(seed ^ S_WZ, x, z, lwarp) - 0.5) * 2.0 * warpAmp);
    }

    private double plateNormOf(int ci, int cj) {
        Double v = plateNorm.get(packCell(ci, cj));
        return v == null ? 0.5 : v;
    }

    // --- scalar field (contEdged) — used by the offline labeling rasterizer without allocating a summary ---
    public double contEdgedAt(int x, int z) {
        int wx = x + (int) Math.round((valueNoise(seed ^ S_WX, x, z, lwarp) - 0.5) * 2.0 * warpAmp);
        int wz = z + (int) Math.round((valueNoise(seed ^ S_WZ, x, z, lwarp) - 0.5) * 2.0 * warpAmp);
        double fbm = FBM_W1 * valueNoise(seed ^ S_C1, wx, wz, lc)
                + FBM_W2 * valueNoise(seed ^ S_C2, wx, wz, Math.max(1, lc / 2))
                + FBM_W3 * valueNoise(seed ^ S_C3, wx, wz, Math.max(1, lc / 4))
                + FBM_W4 * valueNoise(seed ^ S_C4, wx, wz, Math.max(1, lc / 8));
        double cont = (fbm - 0.5) * 2.0;
        long f1 = nearestPlateKey(x, z);
        double pn = plateNorm.getOrDefault(f1, 0.5);
        double blended = cont + PLATE_BIAS_W * ((pn - 0.5) * 2.0);
        double ex = Math.abs((double) x) / xRadius;
        double ez = Math.abs((double) z) / zRadius;
        double edgeB = smoothstep(EDGE_START, 1.0, ex);
        double poleB = smoothstep(POLE_START, 1.0, ez);
        return blended - EDGE_STR * edgeB - POLE_STR * poleB;
    }

    public boolean isOceanIntentAt(int x, int z) {
        return contEdgedAt(x, z) < SEA_LEVEL;
    }

    /** Packed (ci,cj) of the id-cell owning this column (table index / labeling unit). */
    public long idCellKey(int x, int z) {
        return packCell(Math.floorDiv(x, lid), Math.floorDiv(z, lid));
    }

    // Lean nearest-plate scan (F1 only), no allocation.
    private long nearestPlateKey(int x, int z) {
        int ci0 = Math.floorDiv(x, lplate);
        int cj0 = Math.floorDiv(z, lplate);
        double best = Double.POSITIVE_INFINITY;
        int bci = ci0, bcj = cj0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int ci = ci0 + di, cj = cj0 + dj;
                double jx = 0.15 + 0.70 * hash01(seed, ci, cj, S_PLATEJIT_X);
                double jz = 0.15 + 0.70 * hash01(seed, ci, cj, S_PLATEJIT_Z);
                double fx = (ci + jx) * lplate, fz = (cj + jz) * lplate;
                double d = (fx - x) * (fx - x) + (fz - z) * (fz - z);
                if (d < best) { best = d; bci = ci; bcj = cj; }
            }
        }
        return packCell(bci, bcj);
    }

    /** Full macro-geography summary for a column. Pure; allocates only the returned record. */
    public GeoSummary sample(int x, int z) {
        // 1. Jacobian-safe domain warp (2 noise).
        int wx = x + (int) Math.round((valueNoise(seed ^ S_WX, x, z, lwarp) - 0.5) * 2.0 * warpAmp);
        int wz = z + (int) Math.round((valueNoise(seed ^ S_WZ, x, z, lwarp) - 0.5) * 2.0 * warpAmp);

        // 2. 4-octave continentality FBM at the warped point (4 noise).
        double n1 = valueNoise(seed ^ S_C1, wx, wz, lc);
        double n2 = valueNoise(seed ^ S_C2, wx, wz, Math.max(1, lc / 2));
        double n3 = valueNoise(seed ^ S_C3, wx, wz, Math.max(1, lc / 4));
        double n4 = valueNoise(seed ^ S_C4, wx, wz, Math.max(1, lc / 8));
        double cont = (FBM_W1 * n1 + FBM_W2 * n2 + FBM_W3 * n3 + FBM_W4 * n4 - 0.5) * 2.0;

        // 3. plate cell via 3x3 jittered Worley (F1 owner, F2 neighbor). 18 hashes, no loop-of-unknown-length.
        int ci0 = Math.floorDiv(x, lplate), cj0 = Math.floorDiv(z, lplate);
        double b1 = Double.POSITIVE_INFINITY, b2 = Double.POSITIVE_INFINITY;
        int f1ci = ci0, f1cj = cj0, f2ci = ci0, f2cj = cj0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int ci = ci0 + di, cj = cj0 + dj;
                double jx = 0.15 + 0.70 * hash01(seed, ci, cj, S_PLATEJIT_X);
                double jz = 0.15 + 0.70 * hash01(seed, ci, cj, S_PLATEJIT_Z);
                double fx = (ci + jx) * lplate, fz = (cj + jz) * lplate;
                double d = (fx - x) * (fx - x) + (fz - z) * (fz - z);
                if (d < b1) { b2 = b1; f2ci = f1ci; f2cj = f1cj; b1 = d; f1ci = ci; f1cj = cj; }
                else if (d < b2) { b2 = d; f2ci = ci; f2cj = cj; }
            }
        }
        double dF1 = Math.sqrt(b1), dF2 = Math.sqrt(b2);
        long pKey = packCell(f1ci, f1cj), f2Key = packCell(f2ci, f2cj);
        double plateN = plateNormOf(f1ci, f1cj);
        double blended = cont + PLATE_BIAS_W * ((plateN - 0.5) * 2.0);

        // 4. projection-edge + pole ocean bias.
        double ex = Math.abs((double) x) / xRadius, ez = Math.abs((double) z) / zRadius;
        double edgeB = smoothstep(EDGE_START, 1.0, ex), poleB = smoothstep(POLE_START, 1.0, ez);
        double contEdged = blended - EDGE_STR * edgeB - POLE_STR * poleB;

        // 5. land/ocean + coast distance.
        double land01 = smoothstep(-COAST_W, COAST_W, contEdged - SEA_LEVEL);
        boolean ocean = contEdged < SEA_LEVEL;
        double gradConst = (2.0 * FBM_W1) / lc;
        double coastDist = clamp((contEdged - SEA_LEVEL) / gradConst, -coastClamp, coastClamp); // + land / - ocean

        // 6. continent/basin id — one table lookup, or plate-id fallback.
        int compId;
        GeoIdTable table = idTable;
        if (table != null) {
            compId = table.compIdForCell(idCellKey(x, z));
        } else {
            compId = GeoNoise.typedCompId(pKey, !ocean); // fallback: plate-id
        }
        // Enforce land/ocean namespace parity (land even, ocean odd) by the column's OWN type. In the
        // coastal fringe an id-cell's majority type can disagree with a given pixel; this guarantees
        // continentId and oceanBasinId are always disjoint. (Ids in the fringe are unreliable anyway;
        // consumers gate on |coastDistanceBlocks| > coastBand.)
        compId = ocean ? (compId | 1) : (compId & ~1);
        int continentId = ocean ? -1 : compId;
        int oceanBasinId = ocean ? compId : -1;

        // 7. derived fields (arc on ocean side, mtn on land side; archipelago/rugged reuse n3,n4).
        double shelf01 = ocean ? clamp01(1.0 - (-coastDist) / shelfW) : 0.0;
        double seamProx = 1.0 - clamp01((dF2 - dF1) / (ARC_SEAM_W * lplate));
        int ptF1 = sign(plateN - 0.5), ptF2 = sign(plateNormOf(f2ci, f2cj) - 0.5);
        double typeContrast = (ptF1 != ptF2) ? 1.0 : 0.3;

        double islandArc01 = 0.0, mountainIntent01 = 0.0;
        int orogenId = -1;
        if (ocean) {
            double arcN = 1.0 - Math.abs(2.0 * valueNoise(seed ^ S_ARC, x, z, arcWL) - 1.0);
            islandArc01 = clamp01(seamProx * typeContrast * arcN);
        } else {
            double interior = clamp01(coastDist / (0.10 * zRadius));
            double beltN = 1.0 - Math.abs(2.0 * valueNoise(seed ^ S_MTN, x, z, mtnWL) - 1.0);
            double collision = seamProx * ((ptF1 != ptF2) ? 0.7 : 0.3);
            mountainIntent01 = clamp01(Math.max(collision, clamp01((beltN - 0.60) / 0.40) * (0.4 + 0.6 * interior)));
            if (mountainIntent01 > 0.35) {
                long a = Math.min(pKey, f2Key), c = Math.max(pKey, f2Key);
                orogenId = mix64toInt(S_OROG ^ mix64(a) ^ (mix64(c) * 3L));
            }
        }
        double archipelago01 = ocean ? clamp01((0.5 * n3 + 0.5 * n4 - 0.55) / 0.20) * shelf01 : 0.0;
        double rugged = ocean ? clamp01(0.3 * archipelago01)
                : clamp01(0.6 * mountainIntent01 + 0.4 * (0.5 * n3 + 0.5 * n4));
        double edgeSuit = Math.max(edgeB, poleB);

        return new GeoSummary(land01, ocean, continentId, oceanBasinId, coastDist,
                shelf01, islandArc01, archipelago01, mountainIntent01, orogenId,
                rugged, edgeSuit, -1, 0.0, -1); // hydrology reserved this phase
    }

    private static int sign(double v) {
        return v > 0.0 ? 1 : (v < 0.0 ? -1 : 0);
    }
}
