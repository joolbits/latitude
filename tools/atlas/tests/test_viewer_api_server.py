import importlib.util
import os
from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
VIEWER_API_SERVER_PATH = REPO_ROOT / "tools" / "atlas" / "viewer_api_server.py"


def load_viewer_api_server():
    spec = importlib.util.spec_from_file_location("viewer_api_server", VIEWER_API_SERVER_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


@unittest.skipIf(os.name == "nt", "Non-Windows runner contract only applies on macOS/Linux.")
class ViewerApiServerGenerationCommandTest(unittest.TestCase):
    def test_generation_command_uses_checked_in_cross_platform_runner(self):
        module = load_viewer_api_server()

        command = module._generation_command(16)

        self.assertEqual(command[0], module.sys.executable)
        self.assertEqual(Path(command[1]), REPO_ROOT / "tools" / "atlas" / "atlas_runner.py")
        self.assertTrue(Path(command[1]).exists())
        self.assertEqual(command[2:], ["generate", "--step", "16", "--no-viewer-open"])


if __name__ == "__main__":
    unittest.main()
