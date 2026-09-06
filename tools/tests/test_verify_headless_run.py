"""Negative and positive controls for the headless-run gate.

The defect this gate closes: the dedicated server logs "Failed to start the minecraft server"
and exits 0, so runBiomePreview reported success after a boot that never loaded a world.
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import verify_headless_run as gate  # noqa: E402

START_FAILURE = [
    "[12:00:01] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.18.1",
    "[12:00:03] [main/INFO]: Compatibility level set to JAVA_21",
    "[12:00:07] [main/FATAL]: Failed to start the minecraft server",
    "java.lang.RuntimeException: Mixin transformation of net.minecraft.world.level.chunk.ChunkGenerator failed",
    "Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: Invalid descriptor on ...",
]
CLEAN_RUN = [
    "[12:00:01] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.18.1",
    "[12:00:20] [Server thread/INFO]: Done (9.812s)! For help, type \"help\"",
    "[12:00:20] [Server thread/INFO]: [latdev][headless] starting export seed=1 worldSeed=1 radius=512 ...",
    "[12:00:31] [Server thread/INFO]: [latdev][headless] stopping server",
    "[12:00:31] [Server thread/INFO]: Stopping server",
]


class ClassifyTest(unittest.TestCase):
    def test_server_start_failure_is_fail(self) -> None:
        verdict, detail = gate.classify(START_FAILURE)
        self.assertEqual(verdict, "FAIL")
        self.assertIn("server start failure", detail)
        self.assertIn("Failed to start the minecraft server", detail)

    def test_clean_run_is_pass(self) -> None:
        verdict, detail = gate.classify(CLEAN_RUN)
        self.assertEqual(verdict, "PASS")
        self.assertIn("stopping server", detail)

    def test_failure_marker_wins_over_completion_marker(self) -> None:
        # The runner's finally block logs "stopping server" even after "export failed".
        lines = CLEAN_RUN[:2] + [
            "[12:00:25] [Server thread/ERROR]: [latdev][headless] export failed",
            "[12:00:25] [Server thread/INFO]: [latdev][headless] stopping server",
        ]
        verdict, detail = gate.classify(lines)
        self.assertEqual(verdict, "FAIL")
        self.assertIn("headless job failure", detail)

    def test_server_thread_crash_is_fail(self) -> None:
        lines = CLEAN_RUN[:1] + ["[12:00:15] [Server thread/ERROR]: Encountered an unexpected exception"]
        self.assertEqual(gate.classify(lines)[0], "FAIL")

    def test_other_jobs_complete_the_same_way(self) -> None:
        for job in ("search", "audit", "locate-boundary"):
            with self.subTest(job=job):
                lines = CLEAN_RUN[:2] + [f"[12:00:31] [Server thread/INFO]: [latdev][{job}] stopping server"]
                self.assertEqual(gate.classify(lines)[0], "PASS")

    def test_no_completion_evidence_is_invalid_not_pass(self) -> None:
        # A boot that hangs, is killed, or never reaches the runner must not read as green.
        verdict, _ = gate.classify(CLEAN_RUN[:2])
        self.assertEqual(verdict, "INVALID")
        self.assertEqual(gate.classify([])[0], "INVALID")


class MainExitCodeTest(unittest.TestCase):
    def run_gate(self, lines: list[str] | None) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "capture.log"
            if lines is not None:
                log.write_text("\n".join(lines) + "\n", encoding="utf-8")
            import contextlib
            import io
            out, err = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = gate.main(["--log", str(log), "--task", "runBiomePreview"])
            return code, out.getvalue() + err.getvalue()

    def test_start_failure_exits_non_zero_with_a_clear_message(self) -> None:
        code, output = self.run_gate(START_FAILURE)
        self.assertEqual(code, 1)
        self.assertIn("HEADLESS_RUN_FAIL task=runBiomePreview", output)
        self.assertIn("server start failure", output)

    def test_clean_run_exits_zero(self) -> None:
        code, output = self.run_gate(CLEAN_RUN)
        self.assertEqual(code, 0)
        self.assertIn("HEADLESS_RUN_PASS", output)

    def test_missing_evidence_exits_non_zero(self) -> None:
        self.assertEqual(self.run_gate(CLEAN_RUN[:1])[0], 2)
        self.assertEqual(self.run_gate(None)[0], 2)


if __name__ == "__main__":
    unittest.main()
