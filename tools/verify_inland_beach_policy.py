#!/usr/bin/env python3
"""Focused structural/model proof for Latitude's coastal beach shortcut."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"
PICK_SIGNATURES = (
    "public static Holder<Biome> pick(Registry<Biome> biomeRegistry",
    "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool",
)


def extract_method(source: str, signature: str, occurrence: int = 1) -> str:
    start = -1
    search_from = 0
    for _ in range(occurrence):
        start = source.find(signature, search_from)
        if start < 0:
            raise ValueError(f"missing method occurrence {occurrence}: {signature}")
        search_from = start + len(signature)
    brace = source.find("{", start)
    if brace < 0:
        raise ValueError(f"missing method body: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise ValueError(f"unterminated method: {signature}")


def modeled_shortcut(
    *,
    sea_level_delta: int,
    upland_t: float,
    sampler_available: bool,
    ocean_distance: int,
) -> bool:
    if sea_level_delta > 16:
        return False
    if upland_t > 0.25:
        return False
    if not sampler_available:
        return False
    return ocean_distance <= 384


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    failures: list[str] = []
    helper = extract_method(source, "private static boolean allowBeachShortcut(")

    required_helper_fragments = (
        "Climate.Sampler sampler",
        "int blockX",
        "int blockZ",
        "seaLevelDelta > BEACH_SHORTCUT_MAX_SEA_LEVEL_DELTA",
        "uplandT(surfaceY) > BEACH_SHORTCUT_MAX_UPLAND_T",
        "if (sampler == null)",
        "oceanDistanceBlocks(blockX, blockZ, sampler)",
        "MANGROVE_COASTAL_MAX_BLOCKS",
    )
    for fragment in required_helper_fragments:
        if fragment not in helper:
            failures.append(f"beach helper missing: {fragment}")

    if "sampler.sample" in helper or "previewTerrain" in helper or "previewHeight" in helper:
        failures.append("beach helper introduced terrain/noise re-entry")

    if "private static final int MANGROVE_COASTAL_MAX_BLOCKS = 384;" not in source:
        failures.append("coastal threshold is not the existing general 384-block contract")

    expected_call = re.compile(
        r"allowBeachShortcut\(\s*generator,\s*columnDecisionY,\s*"
        r"sampler,\s*blockX,\s*blockZ\s*\)"
    )
    for signature in PICK_SIGNATURES:
        method = extract_method(source, signature, occurrence=2)
        if not expected_call.search(method):
            failures.append(f"coastal helper not wired in {signature}")

    cases = {
        "exact inland 608 rejected": not modeled_shortcut(
            sea_level_delta=16,
            upland_t=0.25,
            sampler_available=True,
            ocean_distance=608,
        ),
        "ordinary shore 0 retained": modeled_shortcut(
            sea_level_delta=2,
            upland_t=0.0,
            sampler_available=True,
            ocean_distance=0,
        ),
        "near-ocean edge 384 retained": modeled_shortcut(
            sea_level_delta=16,
            upland_t=0.25,
            sampler_available=True,
            ocean_distance=384,
        ),
        "385 is inland": not modeled_shortcut(
            sea_level_delta=2,
            upland_t=0.0,
            sampler_available=True,
            ocean_distance=385,
        ),
        "missing sampler cannot claim coast": not modeled_shortcut(
            sea_level_delta=2,
            upland_t=0.0,
            sampler_available=False,
            ocean_distance=0,
        ),
        "high ridge still rejected": not modeled_shortcut(
            sea_level_delta=17,
            upland_t=0.0,
            sampler_available=True,
            ocean_distance=0,
        ),
        "strong upland still rejected": not modeled_shortcut(
            sea_level_delta=2,
            upland_t=0.26,
            sampler_available=True,
            ocean_distance=0,
        ),
    }
    failures.extend(name for name, passed in cases.items() if not passed)

    if failures:
        print("INLAND_BEACH_POLICY: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("INLAND_BEACH_POLICY: PASS")
    print(" controlled reported-area oceanDist 608 rejected; 0..384 near-ocean beaches retained")
    print(" missing sampler fails closed; no terrain/noise re-entry added")
    print(" Registry and Collection production paths share the same policy")
    return 0


if __name__ == "__main__":
    sys.exit(main())
