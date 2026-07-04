package com.example.globe.core.geo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GeoAuthority invariants (the testable-invariants list from the locked design synthesis). These are
 * the anti-red guarantees of Phase 2, asserted with margin over the simulation-measured values.
 */
class GeoAuthorityTest {

    private static final long SEED = 2591890304012655616L;
    // 424242 is the disclosed heavy-tail "supercontinent" seed (residual risk #1) — kept in the set
    // so the anti-red guarantees are asserted against the worst case, not just the friendly seeds.
    private static final long[] SEEDS = {SEED, 1L, 987654321L, -42L, 424242L};
    private static final int[] SIZES = {3750, 7500, 10000, 15000, 20000};

    private static int stepFor(int zRadius) {
        return Math.max(16, zRadius / 150); // ~constant pixel count per size => fair cross-size compare
    }

    // T1 — sample() is a pure function; two authorities with the same args agree everywhere.
    @Test
    void deterministicAcrossInstances() {
        GeoAuthority a = new GeoAuthority(SEED, 7500, 15000);
        GeoAuthority b = new GeoAuthority(SEED, 7500, 15000);
        for (int x = -14000; x <= 14000; x += 911) {
            for (int z = -7000; z <= 7000; z += 523) {
                assertEquals(a.sample(x, z), b.sample(x, z), "non-deterministic at x=" + x + " z=" + z);
            }
        }
    }

    // T4 — land fraction inside the earthlike band for every canonical size and several seeds.
    @Test
    void landFractionInTargetBand() {
        for (int zr : SIZES) {
            for (long seed : SEEDS) {
                GeoAuthority geo = new GeoAuthority(seed, zr, zr * 2);
                double land = GeoIdLabeling.build(geo, stepFor(zr)).table().metrics().landFraction();
                assertTrue(land >= 0.28 && land <= 0.46,
                        "land fraction out of band: " + land + " (zr=" + zr + " seed=" + seed + ")");
            }
        }
    }

    // T6 — land fraction is size-invariant (radius-relative scales).
    @Test
    void landFractionSizeInvariant() {
        for (long seed : SEEDS) {
            double itty = GeoIdLabeling.build(new GeoAuthority(seed, 3750, 7500), stepFor(3750))
                    .table().metrics().landFraction();
            double massive = GeoIdLabeling.build(new GeoAuthority(seed, 20000, 40000), stepFor(20000))
                    .table().metrics().landFraction();
            assertTrue(Math.abs(itty - massive) < 0.05,
                    "land fraction not size-invariant: itty=" + itty + " massive=" + massive + " seed=" + seed);
        }
    }

    // T7 — a dominant connected ocean basin exists (the red's worst failure, destroyed).
    @Test
    void dominantOceanBasin() {
        for (long seed : SEEDS) {
            GeoIdTable.Metrics m = GeoIdLabeling.build(new GeoAuthority(seed, 7500, 15000), 32)
                    .table().metrics();
            assertTrue(m.largestOceanBasinShare() >= 0.80,
                    "no dominant ocean basin: " + m.largestOceanBasinShare() + " (seed=" + seed + ")");
            assertTrue(m.dominantOceanBasinCount() <= 4,
                    "too many large basins: " + m.dominantOceanBasinCount() + " (seed=" + seed + ")");
        }
    }

    // T9/T10 — not one sheet. The meaningful anti-red metric is the largest continent as a fraction
    // of the WHOLE WORLD (the red was ~0.60 = 95% of land x 63% land). Even the supercontinent seed
    // stays well under half the world; friendly seeds are ~0.20. (largest-of-land alone is a weaker
    // argument since an Earth-plausible supercontinent can be ~95% of land while land is <45% of world.)
    @Test
    void largestContinentNotHalfTheWorld() {
        for (long seed : SEEDS) {
            GeoIdTable.Metrics m = GeoIdLabeling.build(new GeoAuthority(seed, 7500, 15000), 32)
                    .table().metrics();
            double largestOfWorld = m.largestLandComponentShare() * m.landFraction();
            assertTrue(largestOfWorld <= 0.50,
                    "largest continent covers half the world (one-sheet red): " + largestOfWorld + " (seed=" + seed + ")");
            assertTrue(m.majorContinentCount() >= 1 && m.majorContinentCount() <= 12,
                    "unexpected major continent count: " + m.majorContinentCount() + " (seed=" + seed + ")");
        }
    }

    // T16 — the projection edge is always ocean (no land cliff at the world wall).
    @Test
    void projectionEdgeIsOcean() {
        int zr = 7500, xr = 15000;
        GeoAuthority geo = new GeoAuthority(SEED, zr, xr);
        for (int x : new int[]{xr, -xr}) {
            for (int z = -6000; z <= 6000; z += 300) {
                GeoSummary s = geo.sample(x, z);
                assertTrue(s.isOceanIntent(), "land at projection edge x=" + x + " z=" + z);
                assertTrue(s.projectionEdgeSuitability01() >= 0.9,
                        "low edge suitability at edge: " + s.projectionEdgeSuitability01());
            }
        }
    }

    // T12 — the domain warp never folds (warpedX/Z monotone non-decreasing => bijective).
    @Test
    void warpNeverFolds() {
        GeoAuthority geo = new GeoAuthority(SEED, 7500, 15000);
        for (int z = -6000; z <= 6000; z += 1500) {
            for (int x = -6000; x < 6000; x++) {
                assertTrue(geo.warpedX(x + 1, z) >= geo.warpedX(x, z),
                        "warp folds in x at x=" + x + " z=" + z);
            }
        }
        for (int x = -6000; x <= 6000; x += 1500) {
            for (int z = -6000; z < 6000; z++) {
                assertTrue(geo.warpedZ(x, z + 1) >= geo.warpedZ(x, z),
                        "warp folds in z at x=" + x + " z=" + z);
            }
        }
    }

    // T13 — continentId is stable within one connected landmass (the crux, via the offline table).
    @Test
    void continentIdStableWithinBlob() {
        GeoAuthority geo = new GeoAuthority(SEED, 3750, 7500);
        GeoIdLabeling lab = GeoIdLabeling.build(geo, 32);
        geo.attachIdTable(lab.table());

        // Largest land component root.
        Map<Integer, Integer> landRootSize = new HashMap<>();
        for (int r = 0; r < lab.height(); r++) {
            for (int c = 0; c < lab.width(); c++) {
                if (lab.isLand(r, c)) landRootSize.merge(lab.rootAt(r, c), 1, Integer::sum);
            }
        }
        int bigRoot = -1, big = -1;
        for (Map.Entry<Integer, Integer> e : landRootSize.entrySet()) {
            if (e.getValue() > big) { big = e.getValue(); bigRoot = e.getKey(); }
        }

        // Sample interior pixels of that one landmass; every continentId must match.
        double band = geo.coastBandBlocks();
        Integer expected = null;
        int checked = 0;
        for (int r = 0; r < lab.height() && checked < 40; r++) {
            for (int c = 0; c < lab.width() && checked < 40; c++) {
                if (!lab.isLand(r, c) || lab.rootAt(r, c) != bigRoot) continue;
                int x = lab.xAt(c), z = lab.zAt(r);
                GeoSummary s = geo.sample(x, z);
                if (Math.abs(s.coastDistanceBlocks()) <= band) continue; // gated coastal fringe
                assertTrue(s.continentId() >= 0 && s.oceanBasinId() == -1, "land pixel not land-typed");
                if (expected == null) expected = s.continentId();
                else assertEquals(expected.intValue(), s.continentId(),
                        "continentId not stable within one landmass at x=" + x + " z=" + z);
                checked++;
            }
        }
        assertTrue(checked >= 5, "not enough interior pixels sampled: " + checked);
    }

    // T15 — land and ocean id namespaces are disjoint.
    @Test
    void idNamespacesDisjoint() {
        GeoAuthority geo = new GeoAuthority(SEED, 3750, 7500);
        geo.attachIdTable(GeoIdLabeling.build(geo, 32).table());
        Set<Integer> continents = new HashSet<>(), basins = new HashSet<>();
        for (int x = -7000; x <= 7000; x += 200) {
            for (int z = -3600; z <= 3600; z += 200) {
                GeoSummary s = geo.sample(x, z);
                if (s.continentId() >= 0) continents.add(s.continentId());
                if (s.oceanBasinId() >= 0) basins.add(s.oceanBasinId());
            }
        }
        for (Integer cid : continents) {
            assertFalse(basins.contains(cid), "id " + cid + " is both a continent and a basin");
        }
    }

    // T19 — field sanity: all *01 in [0,1], ids valid, hydrology reserved, land/ocean typing consistent.
    @Test
    void fieldSanity() {
        GeoAuthority geo = new GeoAuthority(SEED, 7500, 15000); // no table => fallback ids
        for (int x = -14000; x <= 14000; x += 337) {
            for (int z = -7000; z <= 7000; z += 211) {
                GeoSummary s = geo.sample(x, z);
                inUnit(s.land01(), "land01");
                inUnit(s.shelf01(), "shelf01");
                inUnit(s.islandArc01(), "islandArc01");
                inUnit(s.archipelago01(), "archipelago01");
                inUnit(s.mountainIntent01(), "mountainIntent01");
                inUnit(s.ruggednessIntent01(), "ruggednessIntent01");
                inUnit(s.projectionEdgeSuitability01(), "projectionEdgeSuitability01");
                if (s.isOceanIntent()) {
                    assertEquals(-1, s.continentId(), "ocean has a continentId");
                    assertTrue(s.oceanBasinId() >= 0, "ocean lacks a basin id");
                } else {
                    assertEquals(-1, s.oceanBasinId(), "land has an oceanBasinId");
                    assertTrue(s.continentId() >= 0, "land lacks a continent id");
                }
                // hydrology reserved this phase
                assertEquals(-1, s.drainageBasinId());
                assertEquals(0.0, s.flowDirection());
                assertEquals(-1, s.coastOutletId());
            }
        }
    }

    private static void inUnit(double v, String name) {
        assertTrue(v >= 0.0 && v <= 1.0, name + " out of [0,1]: " + v);
    }
}
