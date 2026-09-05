from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('atlas_runner', Path(__file__).parents[1] / 'atlas/atlas_runner.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class AtlasRunnerTest(unittest.TestCase):
    def test_regular_routes_exact_preset_seed_and_radius(self):
        command = runner.command_for(Path('test-output/atlas job'), seed=1, size='regular', step=32, sysprops=[])
        self.assertEqual(str(runner.ROOT / 'gradlew'), command[0])
        self.assertIn('-Platdev.preview.levelType=globe:globe_large', command)
        self.assertIn('-Platdev.preview.levelSeed=1', command)
        recipe = next(x for x in command if x.startswith('--args='))
        self.assertIn('--radius=10000 --step=32', recipe)
        self.assertIn('--out="test-output/atlas job/export"', recipe)
        self.assertNotIn('max-tick-time', ' '.join(command))

    def test_unreviewed_runtime_override_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unsupported preview property'):
            runner.command_for(Path('test-output/job'), seed=1, size='regular', step=32,
                               sysprops=['unknown.property=true'])

    def test_wrong_export_recipe_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in runner.REQUIRED:
                (root / name).write_bytes(b'x')
            (root / 'legend.json').write_text(json.dumps({'seed': 2, 'radiusBlocks': 10000, 'stepBlocks': 32}))
            with self.assertRaisesRegex(ValueError, 'recipe does not match'):
                runner.validate_export(root, seed=1, radius=10000, step=32)

    def test_dimensions_are_checked_against_radius_and_step(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in runner.REQUIRED:
                (root / name).write_bytes(b'x')
            (root / 'legend.json').write_text(json.dumps({'seed': 1, 'radiusBlocks': 3750, 'stepBlocks': 512}))
            header = b'\x89PNG\r\n\x1a\n' + b'\0' * 8 + struct.pack('>II', 15, 15)
            for name in ['biomes.png', 'biome_ids.png']:
                (root / name).write_bytes(header)
            runner.validate_export(root, seed=1, radius=3750, step=512)
            (root / 'biome_ids.png').write_bytes(header[:-8] + struct.pack('>II', 16, 15))
            with self.assertRaisesRegex(ValueError, 'dimensions'):
                runner.validate_export(root, seed=1, radius=3750, step=512)

    def test_failed_generator_does_not_publish_history_and_releases_own_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch.dict(runner.os.environ, {'JAVA_HOME': 'test-java'}), \
                    patch.object(runner, 'ROOT', root), patch.object(runner, 'RUNS_ROOT', root / 'history'), \
                    patch.object(runner, 'git', return_value='test'), \
                    patch.object(runner.subprocess, 'run', return_value=type('Result', (), {'returncode': 1})()):
                with self.assertRaisesRegex(RuntimeError, 'Preview failed'):
                    runner.generate(seed=1, size='itty', step=512, sysprops=[])
                self.assertFalse((root / 'history').exists())
                self.assertFalse((root / 'run-headless/lav-jobs/generation.lock').exists())
                props = next((root / 'run-headless/lav-jobs').glob('preview-*/runtime/server.properties')).read_text()
                self.assertIn('pause-when-empty-seconds=0', props)
                self.assertNotIn('max-tick-time', props)


if __name__ == '__main__':
    unittest.main()
