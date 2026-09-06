#!/usr/bin/env python3
"""Turn a headless Latitude server run's console into an exit code that means something.

The dedicated server swallows start-up failures. `net.minecraft.server.Main` wraps the whole boot
in a catch of Throwable, logs "Failed to start the minecraft server", and returns normally, so
the JVM -- and with it Gradle's runBiomePreview -- exits 0 whether or not a world ever loaded.
A mixin whose handler no longer applies (InvalidInjectionException at class load) therefore gave
a green task and no atlas, and nothing downstream could tell the difference.

The fix cannot live in the mod. By the time that line is logged no mod code runs again, a
shutdown hook cannot set the exit status, and Runtime.halt from one would race the log flush
that is the only evidence of what happened. So the run task tees the server's console into a
file and this gate reads it afterwards. It answers three ways, never two:

  PASS     a completion marker from the headless runner was seen and no failure marker
  FAIL     a known failure marker was seen: server start failure, server crash, job failure
  INVALID  neither -- the run left no completion evidence, which is not a pass

Only PASS exits 0. Wired in build.gradle: the headless run tasks capture their console under
build/latitude-headless/ and run this gate in doLast, so the task fails when the run did.

Usage:
    python3 tools/verify_headless_run.py --log <captured console> [--task <name>]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Failure markers win over completion markers: the runner logs "stopping server" in a finally
# block, so a failed export still ends with the completion line.
FAILURE_MARKERS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("server start failure", re.compile(r"Failed to start the minecraft server")),
    ("server crash", re.compile(r"Encountered an unexpected exception")),
    ("headless job failure", re.compile(r"\[latdev\]\[[\w-]+\] (?:export|seed search|audit|proof) failed")),
    ("headless job failure", re.compile(r"\[latdev\]\[[\w-]+\] no overworld available")),
    ("headless job failure", re.compile(r"\[Latitude\] (?:structure atlas export|distribution census) failed")),
    ("headless job refused", re.compile(r"\[Latitude\] structure atlas REFUSED")),
)

# Every job in BiomePreviewHeadlessRunner ends with "[latdev][<job>] stopping server"; the
# structure atlas and distribution census exporters log their own summary line instead.
COMPLETION_MARKERS: tuple[re.Pattern[str], ...] = (
    re.compile(r"\[latdev\]\[[\w-]+\] stopping server"),
    re.compile(r"\[Latitude\] structure atlas: \d+ candidates across"),
    re.compile(r"\[Latitude\] distribution census: \d+ samples"),
)

EXIT_CODES = {"PASS": 0, "FAIL": 1, "INVALID": 2}


def classify(lines: list[str]) -> tuple[str, str]:
    """('PASS' | 'FAIL' | 'INVALID', detail) for one run's console lines."""
    for line in lines:
        for reason, pattern in FAILURE_MARKERS:
            if pattern.search(line):
                return "FAIL", f"{reason}: {line.strip()}"
    for line in lines:
        for pattern in COMPLETION_MARKERS:
            if pattern.search(line):
                return "PASS", line.strip()
    return "INVALID", "no completion marker from the headless runner was seen"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True, type=Path, help="captured console of one run")
    parser.add_argument("--task", default="headless run", help="task name for the report line")
    args = parser.parse_args(argv)

    if not args.log.is_file():
        print(f"HEADLESS_RUN_INVALID task={args.task}: console capture {args.log} is missing",
              file=sys.stderr)
        return EXIT_CODES["INVALID"]
    lines = args.log.read_text(encoding="utf-8", errors="replace").splitlines()
    verdict, detail = classify(lines)
    print(f"HEADLESS_RUN_{verdict} task={args.task} lines={len(lines)}: {detail}",
          file=sys.stdout if verdict == "PASS" else sys.stderr)
    return EXIT_CODES[verdict]


if __name__ == "__main__":
    raise SystemExit(main())
