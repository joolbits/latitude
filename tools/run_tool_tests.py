#!/usr/bin/env python3
"""Run the unit tests under tools/tests and refuse to call zero tests a pass.

`python3 -m unittest discover` exits 0 when it finds nothing to run, which is the silent-skip
shape this repository's gates exist to close. This runner prints the count it actually ran and
fails when that count is zero. Wired into `check` as latitudeToolsUnitTest in build.gradle.

Usage:
    python3 tools/run_tool_tests.py [--min-tests N]
"""

from __future__ import annotations

import argparse
import sys
import unittest
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent / "tests"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--min-tests", type=int, default=1,
                        help="fail unless at least this many tests ran (default 1)")
    args = parser.parse_args(argv)

    suite = unittest.defaultTestLoader.discover(str(TESTS_DIR), pattern="test_*.py",
                                                top_level_dir=str(TESTS_DIR))
    result = unittest.TextTestRunner(verbosity=1, stream=sys.stdout).run(suite)
    if result.testsRun < args.min_tests:
        print(f"TOOL_TESTS_FAIL tests={result.testsRun} (fewer than {args.min_tests}; discovery "
              f"found nothing to run under {TESTS_DIR})")
        return 1
    if not result.wasSuccessful():
        print(f"TOOL_TESTS_FAIL tests={result.testsRun} failures={len(result.failures)} "
              f"errors={len(result.errors)}")
        return 1
    print(f"TOOL_TESTS_PASS tests={result.testsRun}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
