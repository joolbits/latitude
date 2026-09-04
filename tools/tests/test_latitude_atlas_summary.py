from __future__ import annotations

from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import latitude_atlas_summary as summary_tool


class LatitudeAtlasSummaryTest(unittest.TestCase):
    def bundle(
            self,
            root: Path,
            name: str,
            cells: list[str],
            allowed: dict[str, list[str]],
            bands: list[str],
            include_chosen_bands: bool = True,
            land_bands: list[str] | None = None,
    ) -> Path:
        path = root / name
        path.mkdir()
        palette_ids = sorted(set(cells))
        palette = {biome_id: index for index, biome_id in enumerate(palette_ids)}
        (path / "biome_palette.json").write_text(json.dumps({
            "biomes": [
                {"index": index, "biome_id": biome_id}
                for biome_id, index in palette.items()
            ]
        }))
        image = Image.new("RGB", (2, 2))
        image.putdata([(palette[biome], 0, 0) for biome in cells])
        image.save(path / "biome_ids.png")

        band_colors = {
            "tropical": (122, 74, 40),
            "temperate": (63, 175, 90),
            "polar": (183, 200, 229),
        }
        if include_chosen_bands:
            chosen = Image.new("RGB", (2, 2))
            chosen.putdata([band_colors[band] for band in bands])
            chosen.save(path / "chosen_bands.png")
        land = Image.new("RGB", (2, 2))
        land.putdata([band_colors[band] for band in (land_bands or bands)])
        land.save(path / "land_bands.png")
        (path / "seam_band_legend.txt").write_text(
                "\n".join(f"{band}=#{r:02X}{g:02X}{b:02X}"
                          for band, (r, g, b) in band_colors.items()) + "\n")
        (path / "legend.json").write_text(json.dumps({
            "seed": 987654321,
            "radiusBlocks": 7500,
            "stepBlocks": 128,
            "y": 64,
            "layers": ["biomes"],
            "maskTargets": [],
            "overlays": [],
            "biomeBands": allowed,
        }))
        (path / "world_biome_inventory.json").write_text(json.dumps({
            "seed": 987654321,
            "biomes": [{"biome_id": biome_id} for biome_id in sorted(set(cells))],
        }))
        return path

    def test_reports_arrivals_disappearances_providers_and_share_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(
                root,
                "private-donor-path",
                ["minecraft:forest", "minecraft:forest", "example:woods", "example:woods"],
                {"minecraft:forest": ["temperate"], "example:woods": ["temperate"]},
                ["temperate"] * 4)
            candidate = self.bundle(
                root,
                "private-candidate-path",
                ["minecraft:dappled_forest", "minecraft:dappled_forest", "example:woods", "example:woods"],
                {"minecraft:dappled_forest": ["temperate"], "example:woods": ["temperate"]},
                ["temperate"] * 4)
            result = summary_tool.compare(donor, candidate, "donor", "candidate")
            self.assertEqual(["minecraft:dappled_forest"], result["new_arrivals"])
            self.assertEqual(["minecraft:forest"], result["disappearances"])
            self.assertEqual({"example": 1, "minecraft": 1},
                             result["provider_reachability"]["candidate"])
            self.assertEqual([], result["provider_reachability"]["lost_providers"])
            self.assertEqual(0, result["non_minecraft_winner_changes"])
            self.assertEqual("pass", result["verdict"])
            self.assertTrue(result["share_changes"])
            rendered = json.dumps(result) + summary_tool.friendly_text(result)
            self.assertNotIn("987654321", rendered)
            self.assertNotIn("private-donor-path", rendered)
            self.assertNotIn("private-candidate-path", rendered)

    def test_wrong_band_fixture_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(
                root, "donor", ["minecraft:desert"] * 4,
                {"minecraft:desert": ["tropical"]}, ["tropical"] * 4)
            candidate = self.bundle(
                root, "candidate", ["minecraft:desert"] * 4,
                {"minecraft:desert": ["tropical"]}, ["polar"] * 4)
            result = summary_tool.compare(donor, candidate, "donor", "candidate")
            self.assertEqual(4, result["wrong_band"]["new_count"])
            self.assertEqual(4, result["wrong_band"]["candidate_count"])
            self.assertEqual(
                {"minecraft:desert": 4}, result["wrong_band"]["new_biomes"])
            self.assertEqual("fail", result["verdict"])

    def test_raw_chosen_band_drift_uses_final_land_authority(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(
                root, "donor", ["minecraft:forest"] * 4,
                {"minecraft:forest": ["temperate"]}, ["temperate"] * 4)
            candidate = self.bundle(
                root, "candidate", ["minecraft:forest"] * 4,
                {"minecraft:forest": ["temperate"]}, ["polar"] * 4,
                land_bands=["temperate"] * 4)
            result = summary_tool.compare(donor, candidate, "donor", "candidate")
            self.assertEqual(0, result["wrong_band"]["new_count"])
            self.assertEqual("pass", result["verdict"])

    def test_missing_band_policy_is_unknown_not_exempt(self):
        for missing_side in ("donor", "candidate"):
            with self.subTest(missing_side=missing_side), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                bundles = {}
                for side in ("donor", "candidate"):
                    allowed = {} if side == missing_side else {"minecraft:dappled_forest": ["temperate"]}
                    bundles[side] = self.bundle(
                        root, side, ["minecraft:dappled_forest"] * 4,
                        allowed, ["temperate"] * 4)
                result = summary_tool.compare(bundles["donor"], bundles["candidate"], "donor", "candidate")
                self.assertEqual("unknown", result["verdict"])
                self.assertEqual(["minecraft:dappled_forest"], result["unclassified_biomes"][missing_side])
                self.assertIn("Unclassified " + missing_side + " biomes: minecraft:dappled_forest",
                              summary_tool.friendly_text(result))

    def test_cli_names_report_completion_and_unknown_verdict(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(root, "donor", ["minecraft:dappled_forest"] * 4,
                                {}, ["temperate"] * 4)
            candidate = self.bundle(root, "candidate", ["minecraft:dappled_forest"] * 4,
                                    {}, ["temperate"] * 4)
            output = io.StringIO()
            argv = ["atlas-summary", "--donor", str(donor), "--candidate", str(candidate),
                    "--json-out", str(root / "summary.json"), "--text-out", str(root / "summary.txt")]
            with patch.object(sys, "argv", argv), redirect_stdout(output):
                self.assertEqual(0, summary_tool.main())
            self.assertIn("SUMMARY_COMPLETE verdict=unknown", output.getvalue())
            self.assertNotIn("SUMMARY_PASS", output.getvalue())
            self.assertEqual("unknown", json.loads((root / "summary.json").read_text())["verdict"])

    def test_explicit_band_exemption_remains_valid(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(root, "donor", ["minecraft:ocean"] * 4,
                                {"minecraft:ocean": []}, ["tropical"] * 4)
            candidate = self.bundle(root, "candidate", ["minecraft:ocean"] * 4,
                                    {"minecraft:ocean": []}, ["polar"] * 4)
            result = summary_tool.compare(donor, candidate, "donor", "candidate")
            self.assertEqual("pass", result["verdict"])
            self.assertEqual({"donor": [], "candidate": []}, result["unclassified_biomes"])

    def test_missing_bundle_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            donor = self.bundle(
                root, "donor", ["minecraft:forest"] * 4,
                {"minecraft:forest": ["temperate"]}, ["temperate"] * 4)
            candidate = self.bundle(
                root, "candidate", ["minecraft:forest"] * 4,
                {"minecraft:forest": ["temperate"]}, ["temperate"] * 4,
                include_chosen_bands=False)
            with self.assertRaisesRegex(
                    summary_tool.AtlasSummaryError, "missing required files"):
                summary_tool.compare(donor, candidate, "donor", "candidate")


if __name__ == "__main__":
    unittest.main()
