#!/usr/bin/env python3
"""Narrow source-policy checks for the 2026-06-21 biome tuning slice."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"


def read_source() -> str:
    try:
        return SOURCE.read_text(encoding="utf-8")
    except FileNotFoundError:
        print(f"missing source file: {SOURCE}", file=sys.stderr)
        sys.exit(2)


def constant_expr(source: str, name: str) -> str:
    pattern = rf"(?:public|private)\s+static\s+final\s+int\s+{re.escape(name)}\s*=\s*([^;]+)\s*;"
    match = re.search(pattern, source)
    if not match:
        raise AssertionError(f"missing integer constant {name}")
    return match.group(1).strip()


def int_constant(source: str, name: str, seen: set[str] | None = None) -> int:
    seen = seen or set()
    if name in seen:
        raise AssertionError(f"cyclic integer constant reference at {name}")
    seen.add(name)
    expr = constant_expr(source, name)
    if re.fullmatch(r"[0-9]+", expr):
        return int(expr)
    parts = [part.strip() for part in expr.split("+")]
    if len(parts) > 1 and all(re.fullmatch(r"[A-Z0-9_]+", part) for part in parts):
        return sum(int_constant(source, part, seen.copy()) for part in parts)
    raise AssertionError(f"unsupported expression for {name}: {expr}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    source = read_source()

    tree_line_y = int_constant(source, "TREE_LINE_Y")
    tree_line_fade = int_constant(source, "TREE_LINE_FADE_BAND")
    rugged_thresh = int_constant(source, "WINDSWEPT_RUGGED_THRESH")
    rugged_hyst = int_constant(source, "WINDSWEPT_RUGGED_HYST")

    require(tree_line_y == 168, f"TREE_LINE_Y should be restored to 168, found {tree_line_y}")
    require(tree_line_fade == 28, f"TREE_LINE_FADE_BAND should be restored to 28, found {tree_line_fade}")

    min_height = int_constant(source, "TEMPERATE_MOUNTAIN_MIN_HEIGHT_ABOVE_SEA")
    min_rugged = int_constant(source, "TEMPERATE_MOUNTAIN_MIN_RUGGED_DELTA")
    require(min_height >= 56, f"temperate mountain gate should require at least sea+56, found sea+{min_height}")
    require(min_rugged == rugged_thresh + rugged_hyst,
            f"temperate mountain rugged gate should be windswept threshold+hysteresis ({rugged_thresh + rugged_hyst}), found {min_rugged}")

    helper_calls = len(re.findall(r"\btemperateMountainTerrainAuthority\s*\(", source))
    require(helper_calls >= 3,
            "temperateMountainTerrainAuthority should be defined and used in both biome-pick paths")

    low_live_height = 101
    sea_level = 63
    low_live_passes = low_live_height >= sea_level + min_height
    require(not low_live_passes,
            f"Y{low_live_height} live temperate hills should not pass the stony-peaks height gate")

    print("PASS: biome tuning policy matches treeline and temperate-mountain gate expectations")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
