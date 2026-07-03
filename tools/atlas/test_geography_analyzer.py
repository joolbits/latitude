#!/usr/bin/env python3
"""Synthetic fixture tests for geography_analyzer.py.

Per docs/LATITUDE_2_0_OVERHAUL.md's Measurement First proof rules: "Synthetic fixture tests before
trusting real Atlas runs." These build tiny hand-known grids (no file I/O, no real atlas run) and
assert exact expected shares/component counts, so the analyzer's own correctness is proven before
it is pointed at the old-red run or a fresh baseline.

Run with: python3 -m unittest tools/atlas/test_geography_analyzer.py -v
"""
import unittest

import numpy as np

from geography_analyzer import (
    AtlasRun, analyze, band_of_row, classify_biome, coast_mask, component_shares,
    connected_components, family_grid, projection_edge_composition, row_latitudes_deg,
    row_weights, size_gate,
)

LAND_IDX, OCEAN_IDX, RIVER_IDX = 0, 1, 2
IDX2ID = {LAND_IDX: "minecraft:plains", OCEAN_IDX: "minecraft:ocean", RIVER_IDX: "minecraft:river"}


def make_run(ids: np.ndarray, radius: int = 1000, step: int = 10, z_min=None, z_max=None) -> AtlasRun:
    h, _w = ids.shape
    if z_min is None:
        z_min = -radius
    if z_max is None:
        z_max = radius
    return AtlasRun(seed="1", radius=radius, step=step, width=ids.shape[1], height=h,
                     z_min=float(z_min), z_max=float(z_max), ids=ids, idx2id=IDX2ID)


class ClassifyBiomeTest(unittest.TestCase):
    def test_ocean_variants(self):
        for name in ("ocean", "warm_ocean", "deep_frozen_ocean", "lukewarm_ocean"):
            self.assertEqual(classify_biome(name), "ocean")

    def test_river_variants(self):
        for name in ("river", "frozen_river"):
            self.assertEqual(classify_biome(name), "river")

    def test_beach_and_shore_are_land(self):
        # Deliberately land, not water -- see the module docstring/classify_biome docstring.
        for name in ("beach", "stony_shore", "mangrove_swamp", "plains", "jungle"):
            self.assertEqual(classify_biome(name), "land")


class AllLandTest(unittest.TestCase):
    def setUp(self):
        self.ids = np.full((10, 10), LAND_IDX, dtype=np.int32)
        self.run = make_run(self.ids)

    def test_raw_shares(self):
        report = analyze(self.run)
        self.assertAlmostEqual(report["shares"]["raw"]["land"], 1.0)
        self.assertAlmostEqual(report["shares"]["raw"]["water"], 0.0)

    def test_single_land_component(self):
        report = analyze(self.run)
        self.assertEqual(report["components"]["land"]["count"], 1)
        self.assertAlmostEqual(report["components"]["land"]["largest_share"]["raw"], 1.0)

    def test_no_ocean_components(self):
        report = analyze(self.run)
        self.assertEqual(report["components"]["ocean_basin"]["count"], 0)


class AllOceanTest(unittest.TestCase):
    def test_raw_shares(self):
        ids = np.full((6, 6), OCEAN_IDX, dtype=np.int32)
        report = analyze(make_run(ids))
        self.assertAlmostEqual(report["shares"]["raw"]["ocean"], 1.0)
        self.assertEqual(report["components"]["ocean_basin"]["count"], 1)
        self.assertEqual(report["components"]["land"]["count"], 0)


class CoastMaskTest(unittest.TestCase):
    def test_only_land_adjacent_to_water_is_coast(self):
        ids = np.array([
            [LAND_IDX, LAND_IDX, LAND_IDX],
            [LAND_IDX, LAND_IDX, OCEAN_IDX],
            [LAND_IDX, LAND_IDX, LAND_IDX],
        ], dtype=np.int32)
        families = family_grid(make_run(ids))
        land = families == "land"
        water = families == "ocean"
        coast = coast_mask(land, water)
        expected = np.array([
            [False, False, True],
            [False, True, False],
            [False, False, True],
        ])
        np.testing.assert_array_equal(coast, expected)

    def test_landlocked_cell_is_not_coast(self):
        ids = np.full((5, 5), LAND_IDX, dtype=np.int32)
        ids[2, 2] = OCEAN_IDX  # a single inland lake-like cell
        families = family_grid(make_run(ids))
        coast = coast_mask(families == "land", families == "ocean")
        # every land cell directly touching (2,2) should be coast; the corners should not.
        self.assertTrue(coast[1, 2] and coast[3, 2] and coast[2, 1] and coast[2, 3])
        self.assertFalse(coast[0, 0])


class ConnectedComponentsTest(unittest.TestCase):
    def test_diagonal_bridge_isthmus(self):
        # Two land blobs touching only at a single diagonal seam.
        mask = np.array([
            [True, True, False, False],
            [True, True, False, False],
            [False, False, True, True],
            [False, False, True, True],
        ])
        labels4 = connected_components(mask, connectivity=4)
        labels8 = connected_components(mask, connectivity=8)
        comp4 = component_shares(labels4, np.ones_like(mask, dtype=float))
        comp8 = component_shares(labels8, np.ones_like(mask, dtype=float))
        self.assertEqual(len(comp4), 2, "4-connectivity must treat the diagonal touch as two components")
        self.assertEqual(len(comp8), 1, "8-connectivity must treat the diagonal touch as one component")

    def test_checkerboard_is_all_singletons_at_4conn(self):
        mask = np.zeros((4, 4), dtype=bool)
        mask[::2, ::2] = True
        mask[1::2, 1::2] = True
        labels4 = connected_components(mask, connectivity=4)
        comp4 = component_shares(labels4, np.ones_like(mask, dtype=float))
        # every True cell is diagonal-only from its same-color neighbors -> 8 isolated singletons
        self.assertEqual(len(comp4), 8)
        self.assertTrue(all(c["raw_count"] == 1 for c in comp4))

    def test_checkerboard_merges_at_8conn(self):
        mask = np.zeros((4, 4), dtype=bool)
        mask[::2, ::2] = True
        mask[1::2, 1::2] = True
        labels8 = connected_components(mask, connectivity=8)
        comp8 = component_shares(labels8, np.ones_like(mask, dtype=float))
        self.assertEqual(len(comp8), 1, "8-connectivity should merge the whole checkerboard into one component")


class LatitudeWeightingTest(unittest.TestCase):
    def test_equator_row_weight_is_one(self):
        ids = np.full((181, 4), LAND_IDX, dtype=np.int32)
        run = make_run(ids, radius=90, z_min=-90, z_max=90)
        weights = row_weights(run)
        mid = 90  # z = 0 at this row
        self.assertAlmostEqual(weights[mid], 1.0, places=6)

    def test_pole_row_weight_is_near_zero(self):
        ids = np.full((181, 4), LAND_IDX, dtype=np.int32)
        run = make_run(ids, radius=90, z_min=-90, z_max=90)
        weights = row_weights(run)
        self.assertAlmostEqual(weights[0], 0.0, places=6)
        self.assertAlmostEqual(weights[-1], 0.0, places=6)

    def test_weighted_share_deflates_polar_dominant_grid(self):
        # Water only in the top/bottom 10% of rows (polar), land everywhere else.
        ids = np.full((100, 10), LAND_IDX, dtype=np.int32)
        ids[:10, :] = OCEAN_IDX
        ids[-10:, :] = OCEAN_IDX
        run = make_run(ids, radius=90, z_min=-90, z_max=90)
        report = analyze(run)
        raw = report["shares"]["raw"]["ocean"]
        weighted = report["shares"]["area_weighted"]["ocean"]
        self.assertAlmostEqual(raw, 0.20)
        self.assertLess(weighted, raw, "polar-only water should shrink under cosine-latitude area weighting")


class BandOfRowTest(unittest.TestCase):
    def test_known_latitudes_map_to_expected_bands(self):
        lat_deg = np.array([0.0, 10.0, 23.5, 30.0, 40.0, 55.0, 70.0, 90.0])
        band_idx = band_of_row(lat_deg)
        # indices: 0 tropical, 1 subtropical, 2 temperate, 3 subpolar, 4 polar
        expected = [0, 0, 1, 1, 2, 3, 4, 4]
        self.assertEqual(list(band_idx), expected)


class ProjectionEdgeTest(unittest.TestCase):
    def test_all_ocean_edge_reports_100_percent_ocean(self):
        ids = np.full((90, 20), LAND_IDX, dtype=np.int32)
        ids[:, :2] = OCEAN_IDX
        ids[:, -2:] = OCEAN_IDX
        run = make_run(ids, radius=90, z_min=-90, z_max=90, step=1)
        families = family_grid(run)
        lat_deg = row_latitudes_deg(run)
        band_idx = band_of_row(lat_deg)
        edge = projection_edge_composition(run, families, band_idx, edge_frac=0.1)
        for name in ("tropical", "subtropical", "temperate", "subpolar", "polar"):
            self.assertAlmostEqual(edge["west"][name]["ocean"], 1.0)
            self.assertAlmostEqual(edge["east"][name]["ocean"], 1.0)


class SizeGateTest(unittest.TestCase):
    def test_exact_match(self):
        gate = size_gate(7500)
        self.assertEqual(gate["closest_canonical_size"], "small")
        self.assertTrue(gate["exact_match"])

    def test_nearest_match_for_nonstandard_radius(self):
        gate = size_gate(8000)
        self.assertEqual(gate["closest_canonical_size"], "small")
        self.assertFalse(gate["exact_match"])


if __name__ == "__main__":
    unittest.main()
