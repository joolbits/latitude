#!/usr/bin/env python3
"""Verify exact Meadow admission at the late temperate warm-edge fallback."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


SOURCE_REL = Path("src/main/java/com/example/globe/world/LatitudeBiomes.java")
MOUNTAIN_TAG_REL = Path(
    "src/main/resources/data/globe/tags/worldgen/biome/lat_temperate_mountain.json"
)
MEADOW_ID = "minecraft:meadow"


def extract_block(source: str, marker: str, opener: str = "{", closer: str = "}") -> str:
    start = source.find(marker)
    if start < 0:
        raise ValueError(f"missing marker: {marker}")
    brace = source.find(opener, start)
    if brace < 0:
        raise ValueError(f"missing body after marker: {marker}")

    depth = 0
    quote: str | None = None
    escaped = False
    line_comment = False
    block_comment = False
    index = brace
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
        elif block_comment:
            if char == "*" and following == "/":
                block_comment = False
                index += 1
        elif quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char == "/" and following == "/":
            line_comment = True
            index += 1
        elif char == "/" and following == "*":
            block_comment = True
            index += 1
        elif char in {'"', "'"}:
            quote = char
        elif char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1
    raise ValueError(f"unterminated body after marker: {marker}")


def string_ids(block: str) -> list[str]:
    return re.findall(r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"', block)


def mountain_tag_ids(tag: dict[str, object]) -> list[str]:
    result: list[str] = []
    for value in tag.get("values", []):
        if isinstance(value, str):
            result.append(value)
        elif isinstance(value, dict) and isinstance(value.get("id"), str):
            result.append(value["id"])
    return result


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    source = (root / SOURCE_REL).read_text()
    mountain_tag = json.loads((root / MOUNTAIN_TAG_REL).read_text())
    checks: list[tuple[str, bool, str]] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append((name, condition, detail))

    try:
        warm_edge_array = extract_block(
            source,
            "private static final String[] TEMPERATE_WARM_EDGE_TRANSITION_BIOMES",
        )
        warm_edge_predicate = extract_block(
            source,
            "private static boolean isTemperateWarmEdgeTransitionBiome",
        )
        upland_array = extract_block(
            source,
            "private static final String[] TEMPERATE_UPLAND_BIOMES",
        )
        upland_ramp = extract_block(source, "private static double uplandT(int blockY)")
        registry_upland = extract_block(
            source,
            "private static Holder<Biome> pickTemperateLand(Registry<Biome> biomes",
        )
        collection_upland = extract_block(
            source,
            "private static Holder<Biome> pickTemperateLand(Collection<Holder<Biome>> biomes",
        )
        registry_fallback = extract_block(
            source,
            "private static Holder<Biome> pickTemperateWarmEdgeTransitionFallback(Registry<Biome> biomes",
        )
        collection_fallback = extract_block(
            source,
            "private static Holder<Biome> pickTemperateWarmEdgeTransitionFallback(Collection<Holder<Biome>> biomes",
        )
        mountain_family = extract_block(
            source,
            "private static boolean isTemperateMountainFamilyBiome",
        )
        pool_filter = extract_block(
            source,
            "private static List<Holder<Biome>> filteredAllowedLandPool",
        )
    except ValueError as error:
        print(f"MEADOW_WARM_EDGE_ADMISSION_VERIFY_FAIL structural extraction: {error}")
        return 1

    check(
        "warm_edge_pool_excludes_exact_meadow",
        MEADOW_ID not in string_ids(warm_edge_array),
        "late non-upland fallback must not offer exact minecraft:meadow",
    )
    check(
        "warm_edge_predicate_excludes_exact_meadow",
        MEADOW_ID not in string_ids(warm_edge_predicate),
        "base/candidate validation must not re-admit exact minecraft:meadow",
    )

    for lane, fallback in (
        ("registry", registry_fallback),
        ("collection", collection_fallback),
    ):
        check(
            f"{lane}_fallback_uses_shared_pool",
            "TEMPERATE_WARM_EDGE_TRANSITION_BIOMES" in fallback,
            f"{lane} fallback reads the shared transition pool",
        )
        check(
            f"{lane}_fallback_uses_shared_predicate",
            "isTemperateWarmEdgeTransitionBiome" in fallback,
            f"{lane} fallback validates candidates and base identically",
        )

    check(
        "upland_pool_preserves_exact_meadow",
        MEADOW_ID in string_ids(upland_array),
        "the dedicated temperate upland pool still contains Meadow",
    )
    check(
        "upland_threshold_remains_above_112",
        "private static final int UPLAND_MIN_Y = 112;" in source
        and "blockY - UPLAND_MIN_Y" in upland_ramp,
        "the upland ramp remains zero through Y=112 and positive above it",
    )
    for lane, upland_picker in (
        ("registry", registry_upland),
        ("collection", collection_upland),
    ):
        check(
            f"{lane}_upland_path_preserved",
            "double ramp = uplandT(blockY);" in upland_picker
            and "pickTemperateUplandBiome(biomes, blockX, blockZ)" in upland_picker,
            f"{lane} path retains the altitude-gated Meadow-capable picker",
        )

    check(
        "mountain_tag_preserves_exact_meadow",
        MEADOW_ID in mountain_tag_ids(mountain_tag),
        "the dedicated temperate mountain tag still contains Meadow",
    )
    check(
        "registry_and_collection_mountain_promotion_preserved",
        source.count(
            "pickFromTagNoiseOrBase(biomeRegistry, LAT_TEMPERATE_MOUNTAIN"
        )
        == 1
        and source.count(
            "pickFromTagNoiseOrBase(biomePool, LAT_TEMPERATE_MOUNTAIN"
        )
        == 1,
        "both runtime APIs retain the dedicated mountain-tag route",
    )
    check(
        "nonmountain_pool_guard_preserved",
        MEADOW_ID in string_ids(mountain_family)
        and "bandIndex == BAND_TEMPERATE && !mountainLike" in pool_filter
        and "removeTemperateMountainFamily(out)" in pool_filter,
        "ordinary non-mountain pools continue to reject mountain-family Meadow",
    )

    failures = [item for item in checks if not item[1]]
    for name, passed, detail in checks:
        print(f"{'PASS' if passed else 'FAIL'} {name}: {detail}")
    if failures:
        print(f"MEADOW_WARM_EDGE_ADMISSION_VERIFY_FAIL failures={len(failures)}")
        return 1
    print(f"MEADOW_WARM_EDGE_ADMISSION_VERIFY_PASS checks={len(checks)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
