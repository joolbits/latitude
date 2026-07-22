#!/usr/bin/env python3
"""Focused structural proof for the Phase 8 Pale Garden owner repair."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"


def check_source(source: str) -> list[tuple[str, bool]]:
    candidate_count_match = re.search(
        r"PALE_GARDEN_ANCHOR_CANDIDATE_COUNT\s*=\s*(\d+)", source
    )
    candidate_count = int(candidate_count_match.group(1)) if candidate_count_match else 0
    region_call_count = len(re.findall(
        r"paleGardenRegionHit\(WORLD_SEED,\s*blockX,\s*blockZ,\s*effectiveRadius,\s*sampler\)",
        source,
    ))
    core_call_count = len(re.findall(
        r"paleGardenCoreHit\(WORLD_SEED,\s*blockX,\s*blockZ,\s*effectiveRadius,\s*sampler\)",
        source,
    ))
    selector = re.search(
        r"paleGardenAnchor\(long worldSeed,\s*int effectiveRadiusHint,\s*Climate\.Sampler sampler\)",
        source,
    )
    owner_call_positions = [match.start() for match in re.finditer(r"enforcePaleGardenRegion\(", source)]
    beach_return_positions = [match.start() for match in re.finditer(r"if \(beachLike && allowBeachShortcut", source)]
    ocean_return_positions = [match.start() for match in re.finditer(r"if \(base\.is\(BiomeTags\.IS_OCEAN\) \|\| oceanAuthority\)", source)]

    return [
        ("bounded_candidate_count_at_least_32", 32 <= candidate_count <= 128),
        ("anchor_record_carries_seed_radius_sampler_and_coordinates", bool(re.search(
            r"record PaleGardenAnchor\(long worldSeed,\s*int radius,\s*Climate\.Sampler sampler,\s*int x,\s*int z,\s*boolean landlocked\)",
            source,
        ))),
        ("single_cached_anchor_authority", "PALE_GARDEN_ANCHOR_CACHE" in source and bool(selector)),
        ("cache_hit_does_not_take_global_monitor", "private static synchronized PaleGardenAnchor paleGardenAnchor(" not in source),
        ("only_cache_miss_selection_is_synchronized", "private static synchronized PaleGardenAnchor selectPaleGardenAnchor(" in source),
        ("candidate_requires_temperate_band", "authoritativeLandBandIndex(candidateX, candidateZ, radius) != BAND_TEMPERATE" in source),
        ("candidate_requires_384_block_ocean_clearance", bool(re.search(
            r"candidateOceanDistance\s*>=\s*PALE_GARDEN_MIN_OCEAN_DISTANCE_BLOCKS", source
        ))),
        ("coastline_constant_remains_384", bool(re.search(
            r"PALE_GARDEN_MIN_OCEAN_DISTANCE_BLOCKS\s*=\s*384\s*;", source
        ))),
        ("ordinary_beach_veto_remains", source.count("boolean tooWet = isBeachLike(base)") == 2),
        ("outer_uses_selected_sampler_anchor", region_call_count == 4),
        ("core_uses_selected_sampler_anchor", core_call_count == 2),
        ("both_owner_overloads_receive_sampler", source.count(
            "effectiveRadius, oceanDistance, sampler)"
        ) == 2),
        ("anchor_cache_cleared_on_world_context_changes", source.count(
            "PALE_GARDEN_ANCHOR_CACHE = null;"
        ) >= 5),
        ("beach_and_ocean_returns_stay_before_owner", bool(
            len(owner_call_positions) >= 2
            and len(beach_return_positions) >= 2
            and len(ocean_return_positions) >= 2
            and beach_return_positions[0] < ocean_return_positions[0] < owner_call_positions[0]
            and beach_return_positions[1] < ocean_return_positions[1] < owner_call_positions[1]
        )),
        ("no_hardcoded_row07_anchor", "1005" not in source and "-1720" not in source),
    ]


def run_negative_controls(source: str) -> list[tuple[str, bool]]:
    controls: list[tuple[str, str, str]] = [
        (
            "zero_coast_clearance_rejected",
            source.replace("PALE_GARDEN_MIN_OCEAN_DISTANCE_BLOCKS = 384;", "PALE_GARDEN_MIN_OCEAN_DISTANCE_BLOCKS = 0;", 1),
            "coastline_constant_remains_384",
        ),
        (
            "unbounded_candidate_count_rejected",
            re.sub(r"PALE_GARDEN_ANCHOR_CANDIDATE_COUNT\s*=\s*\d+", "PALE_GARDEN_ANCHOR_CANDIDATE_COUNT = 4096", source, count=1),
            "bounded_candidate_count_at_least_32",
        ),
        (
            "non_temperate_candidate_rejected",
            source.replace(
                "authoritativeLandBandIndex(candidateX, candidateZ, radius) != BAND_TEMPERATE",
                "authoritativeLandBandIndex(candidateX, candidateZ, radius) == BAND_TEMPERATE",
                1,
            ),
            "candidate_requires_temperate_band",
        ),
        (
            "outer_sampler_disconnect_rejected",
            source.replace(
                "paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius, sampler)",
                "paleGardenRegionHit(WORLD_SEED, blockX, blockZ, effectiveRadius, null)",
            ),
            "outer_uses_selected_sampler_anchor",
        ),
        (
            "hardcoded_row07_anchor_rejected",
            source + "\n// forbidden anchor 1005, -1720\n",
            "no_hardcoded_row07_anchor",
        ),
        (
            "synchronized_hot_path_rejected",
            source.replace(
                "private static PaleGardenAnchor paleGardenAnchor(",
                "private static synchronized PaleGardenAnchor paleGardenAnchor(",
                1,
            ),
            "cache_hit_does_not_take_global_monitor",
        ),
    ]
    results: list[tuple[str, bool]] = []
    for name, mutant, expected_failure in controls:
        status = dict(check_source(mutant))
        results.append((name, status.get(expected_failure) is False))
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--negative-controls", action="store_true")
    args = parser.parse_args()

    source = args.source.read_text(encoding="utf-8")
    checks = check_source(source)
    if args.negative_controls:
        checks.extend(run_negative_controls(source))

    failed = 0
    for name, passed in checks:
        print(f"{name}={'PASS' if passed else 'FAIL'}")
        failed += 0 if passed else 1
    print(f"verdict={'GREEN' if failed == 0 else 'RED'} failures={failed} assertions={len(checks)}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
