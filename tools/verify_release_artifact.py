#!/usr/bin/env python3
"""Release-artifact gate for the 2.0 line — jar contents + exclusion-spec drift.

Two checks, both born from a live 2026-08-07 defect (the debug/** exclusion clause was dropped
from build.gradle while the package still shipped):

1. CONTENT: the built remapped jar must contain no dev/debug tooling, no script/binary
   patterns, no extracted-source trees. Verified against the actual zip, not the spec.
2. DRIFT: the exclusion clauses present in build.gradle are diffed against the committed
   baseline (tools/release-excludes-baseline.txt). Removing a clause fails the build until the
   baseline is consciously updated in the same commit — a dropped guard becomes a reviewed
   one-line diff instead of a silent regression.

Usage:
    python3 tools/verify_release_artifact.py            # verify (exit 1 on failure)
    python3 tools/verify_release_artifact.py --update   # rewrite the drift baseline
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "tools/release-excludes-baseline.txt"

FORBIDDEN = [
    re.compile(r"^com/example/globe/dev(/|$)"),
    re.compile(r"^com/example/globe/debug(/|$)"),
    re.compile(r"^com/example/globe/client/ClipboardImageWriter"),
    re.compile(r"\.(sh|bat|exe|dll|dylib|so)$"),
    re.compile(r"^tools/"),
    re.compile(r"^scripts/"),
    re.compile(r"^_mcsrc"),
    re.compile(r"^run-headless/"),
]


def exclusion_clauses() -> list[str]:
    """Every path literal that appears inside releaseArtifactExcludeSpec + pattern excludes."""
    src = (ROOT / "build.gradle").read_text(encoding="utf-8")
    spec = src.split("releaseArtifactExcludeSpec", 1)[1]
    clauses = sorted(set(re.findall(r"'((?:com/|\*\*/)[^']+)'", spec.split("releaseArtifactPatternExcludes", 1)[0])))
    patterns = sorted(set(re.findall(r"'(\*\*/[^']+)'", spec)))
    return clauses + patterns


def release_jar() -> Path | None:
    libs = ROOT / "build/libs"
    if not libs.is_dir():
        return None
    jars = [p for p in libs.glob("*.jar") if "-sources" not in p.name and "-dev" not in p.name]
    return max(jars, key=lambda p: p.stat().st_mtime) if jars else None


def main() -> int:
    clauses = "\n".join(exclusion_clauses()) + "\n"
    if "--update" in sys.argv:
        BASELINE.write_text(clauses)
        print(f"baseline updated: {len(clauses.splitlines())} exclusion entries")
        return 0

    failures: list[str] = []

    if not BASELINE.exists():
        failures.append(f"missing {BASELINE.name} — run with --update and review the diff")
    elif BASELINE.read_text() != clauses:
        failures.append(
            "exclusion-spec DRIFT vs tools/release-excludes-baseline.txt — a guard clause was "
            "added or removed. If deliberate, update the baseline IN THIS COMMIT "
            "(python3 tools/verify_release_artifact.py --update).")

    jar = release_jar()
    if jar is None:
        failures.append("no release jar under build/libs — run remapJar first")
    else:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                for pat in FORBIDDEN:
                    if pat.search(name):
                        failures.append(f"forbidden entry in {jar.name}: {name}")
                        break

    if failures:
        print("verify_release_artifact: FAIL")
        for f in failures[:20]:
            print(f"  - {f}")
        return 1
    print(f"verify_release_artifact: OK — {jar.name} clean, exclusion spec matches baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
