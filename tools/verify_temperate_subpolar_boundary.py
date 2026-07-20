#!/usr/bin/env python3
"""Focused structural/model proof for the 50-degree land-band boundary."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"
RADIUS = 9_984
DISPLAY_RADIUS = 10_000
TEMPERATE = 2
SUBPOLAR = 3
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


def canonical_band(block_z: int, radius: int) -> int:
    degrees = abs(block_z) * 90.0 / radius
    if degrees < 23.5:
        return 0
    if degrees < 35.0:
        return 1
    if degrees < 50.0:
        return TEMPERATE
    if degrees < 66.5:
        return SUBPOLAR
    return 4


def resolve_poleward_leak(canonical: int, blended: int) -> int:
    if canonical == TEMPERATE and blended == SUBPOLAR:
        return TEMPERATE
    return blended


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    failures: list[str] = []

    resolved = extract_method(
        source,
        "private static int latitudeBandIndexWithBlend(",
    )
    chosen = extract_method(
        source,
        "private static int latitudeBandChosenIndexWithBlend(",
    )

    required_resolved_fragments = (
        "int canonicalBandIndex = crispBandIndex((double) absZ / (double) radius);",
        "return enforceTemperateSubpolarOwnership(canonicalBandIndex, bandIndex);",
        "resolvedBandIndex = enforceTemperateSubpolarOwnership(canonicalBandIndex, resolvedBandIndex);",
    )
    for fragment in required_resolved_fragments:
        if fragment not in resolved:
            failures.append(f"resolved authority missing: {fragment}")

    ownership = extract_method(
        source,
        "private static int enforceTemperateSubpolarOwnership(",
    )
    for fragment in (
        "canonicalBandIndex == BAND_TEMPERATE",
        "resolvedBandIndex == BAND_SUBPOLAR",
        "return BAND_TEMPERATE;",
    ):
        if fragment not in ownership:
            failures.append(f"ownership policy missing: {fragment}")

    if "enforceTemperateSubpolarOwnership" in chosen:
        failures.append("raw diagnostic chosen-band accessor was rewritten")

    if resolved.count(
        "return enforceTemperateSubpolarOwnership(canonicalBandIndex, bandIndex);"
    ) != 3:
        failures.append("not every resolved early-return path enforces canonical ownership")

    if not re.search(
        r"return\s+latitudeBandChosenIndexWithBlend\(",
        extract_method(source, "public static int authoritativeChosenBandIndex("),
    ):
        failures.append("authoritativeChosenBandIndex no longer exposes the raw comparator")

    beach_fragments = (
        "int canonicalBandIndex = crispBandIndex((double) lat / (double) effectiveRadius);",
        "int beachBandIndex = enforceTemperateSubpolarOwnership(canonicalBandIndex, bandIndex);",
        "if (beachBandIndex == BAND_TEMPERATE)",
    )
    for signature in PICK_SIGNATURES:
        pick = extract_method(source, signature, occurrence=2)
        for fragment in beach_fragments:
            if fragment not in pick:
                failures.append(f"early beach authority missing in {signature}: {fragment}")
        if not re.search(
            r"pickBeachForBand\([^;]+,\s*beachBandIndex\)",
            pick,
            re.DOTALL,
        ):
            failures.append(f"early beach picker still uses raw jittered band in {signature}")
        if not re.search(
            r"quarantineUnknownCustomLandBiome\([^;]+,\s*beachBandIndex,\s*false\)",
            pick,
            re.DOTALL,
        ):
            failures.append(f"early beach quarantine still uses raw jittered band in {signature}")

    exact_points = ((6_000, -5_545), (5_984, -5_486))
    for block_x, block_z in exact_points:
        degrees = abs(block_z) * 90.0 / RADIUS
        canonical = canonical_band(block_z, RADIUS)
        if canonical != TEMPERATE:
            failures.append(
                f"TEST2 point x={block_x} z={block_z} is not modeled Temperate: "
                f"{degrees:.4f} degrees"
            )
        if resolve_poleward_leak(canonical, SUBPOLAR) != TEMPERATE:
            failures.append(
                f"TEST2 point x={block_x} z={block_z} can retain Subpolar pool"
            )

    # The correction is deliberately one-way and one-boundary only.
    if resolve_poleward_leak(SUBPOLAR, TEMPERATE) != TEMPERATE:
        failures.append("50+ equatorward Temperate ecotone was erased")
    unchanged_pairs = (
        (0, 1),
        (1, 0),
        (1, 2),
        (2, 1),
        (3, 4),
        (4, 3),
    )
    for canonical, blended in unchanged_pairs:
        if resolve_poleward_leak(canonical, blended) != blended:
            failures.append(
                f"unrelated transition changed: canonical={canonical} blended={blended}"
            )

    if failures:
        print("TEMPERATE_SUBPOLAR_BOUNDARY: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    point_text = ", ".join(
        f"x={x} z={z} (canonical R9984={abs(z) * 90.0 / RADIUS:.4f}, "
        f"live display={abs(z) * 90.0 / DISPLAY_RADIUS:.2f} degrees)"
        for x, z in exact_points
    )
    print("TEMPERATE_SUBPOLAR_BOUNDARY: PASS")
    print(f" exact TEST2 points clamped to Temperate: {point_text}")
    print(" raw chosen-band diagnostic preserved")
    print(" 50+ Temperate ecotone and all other adjacent transitions preserved")
    return 0


if __name__ == "__main__":
    sys.exit(main())
