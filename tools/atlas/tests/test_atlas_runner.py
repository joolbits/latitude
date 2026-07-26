#!/usr/bin/env python3
"""Regression tests for atlas-run configuration fidelity.

Run with: python3 -m unittest tools/atlas/tests/test_atlas_runner.py -v
"""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
RUNNER_PATH = REPO_ROOT / "tools" / "atlas" / "atlas_runner.py"


def load_runner():
    spec = importlib.util.spec_from_file_location("atlas_runner", RUNNER_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class RuggednessReplayTest(unittest.TestCase):
    def setUp(self):
        self.runner = load_runner()
        self.tempdir = tempfile.TemporaryDirectory()
        self.runs_root = Path(self.tempdir.name) / "runs"
        self.run_dir = self.runs_root / "source-run"
        self.run_dir.mkdir(parents=True)
        (self.run_dir / "run_manifest.json").write_text(json.dumps({
            "seed": "12345",
            "size": "small",
            "aspect": 1.0,
            "sysprops": {
                "latitude.polarBarrens.enabled": "true",
                "latitude.glacialCavesV1": "true",
            },
        }), encoding="utf-8")
        self.source_step_dir = Path(self.tempdir.name) / "fresh" / "step16"
        self.source_step_dir.mkdir(parents=True)
        (self.source_step_dir / "ruggedness.png").write_bytes(b"fixture")
        self.preview_calls = []
        self.copy_calls = []
        self.original_runs_root = self.runner.RUNS_ROOT
        self.original_preview = self.runner.run_gradle_preview
        self.original_find = self.runner.find_fresh_step_dir
        self.original_copy = self.runner.shutil.copy2
        self.runner.RUNS_ROOT = self.runs_root
        self.runner.run_gradle_preview = lambda **kwargs: self.preview_calls.append(kwargs)
        self.runner.find_fresh_step_dir = lambda **_kwargs: self.source_step_dir
        self.runner.shutil.copy2 = lambda source, target: self.copy_calls.append((source, target))

    def tearDown(self):
        self.runner.RUNS_ROOT = self.original_runs_root
        self.runner.run_gradle_preview = self.original_preview
        self.runner.find_fresh_step_dir = self.original_find
        self.runner.shutil.copy2 = self.original_copy
        self.tempdir.cleanup()

    def test_ruggedness_replays_manifest_aspect_and_system_properties(self):
        self.runner.generate_ruggedness(run_id="source-run", step=16)

        self.assertEqual([{
            "seed": 12345,
            "size": "small",
            "step": 16,
            "layers": "ruggedness",
            "aspect": 1.0,
            "sysprops": [
                "latitude.polarBarrens.enabled=true",
                "latitude.glacialCavesV1=true",
            ],
        }], self.preview_calls)
        self.assertEqual([(self.source_step_dir / "ruggedness.png",
                           self.run_dir / "step16_ruggedness.png")], self.copy_calls)

    def test_legacy_manifest_replays_shipping_shape_with_no_extra_properties(self):
        (self.run_dir / "run_manifest.json").write_text(json.dumps({
            "seed": "54321",
            "size": "small",
        }), encoding="utf-8")

        self.runner.generate_ruggedness(run_id="source-run", step=16)

        self.assertEqual(2.0, self.preview_calls[0]["aspect"])
        self.assertEqual([], self.preview_calls[0]["sysprops"])

    def test_new_manifest_records_aspect_and_explicit_system_properties(self):
        manifest_path = Path(self.tempdir.name) / "new-run-manifest.json"

        self.runner.write_manifest(
            manifest_path,
            run_id="new-run",
            seed=999,
            size="small",
            radius=7500,
            step=16,
            aspect=1.0,
            sysprops=[
                "latitude.polarBarrens.enabled=true",
                "latitude.glacialCavesV1=true",
            ],
        )

        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(1.0, manifest["aspect"])
        self.assertEqual({
            "latitude.polarBarrens.enabled": "true",
            "latitude.glacialCavesV1": "true",
        }, manifest["sysprops"])


class BuildDefaultParityTest(unittest.TestCase):
    def test_normal_client_and_atlas_defaults_match_the_shipped_polar_configuration(self):
        build = (REPO_ROOT / "build.gradle").read_text(encoding="utf-8")

        for key in (
            "latitude.polePassageV2.enabled",
            "latitude.polarBarrens.enabled",
            "latitude.glacialCavesV1",
        ):
            self.assertIn(f"System.getProperty('{key}', 'true')", build)
            self.assertNotIn(f"System.getProperty('{key}', 'false')", build)
        # Solar Tilt is forwarded in both the normal client and the headless atlas run. Count both
        # locations so deleting one forwarding line cannot leave this parity proof falsely green.
        self.assertEqual(2, build.count("System.getProperty('latitude.solarTiltV2.enabled', 'true')"))
        self.assertEqual(0, build.count("System.getProperty('latitude.solarTiltV2.enabled', 'false')"))
        self.assertEqual(2, build.count("System.getProperty('latitude.solarTilt.functionalMinDeg', '60.0')"))
        self.assertEqual(0, build.count("System.getProperty('latitude.solarTilt.functionalMinDeg', '74.5')"))


if __name__ == "__main__":
    unittest.main()
