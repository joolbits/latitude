#!/usr/bin/env python3
"""Focused structural/model and Atlas-diff verifier for the beach-ridge slice."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

from PIL import Image


SOURCE_REL = Path("src/main/java/com/example/globe/world/LatitudeBiomes.java")
REGISTRY_PICK = "public static Holder<Biome> pick(Registry<Biome> biomeRegistry"
COLLECTION_PICK = "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool"
BEACH_HELPER = "private static boolean allowBeachShortcut(NoiseBasedChunkGenerator generator"
BEACH_IDS = ("beach", "shore", "coast")


def extract_method(source: str, signature: str, occurrence: int = 1) -> str:
    start = -1
    search_from = 0
    for _ in range(occurrence):
        start = source.find(signature, search_from)
        if start < 0:
            raise ValueError(f"missing method signature occurrence {occurrence}: {signature}")
        search_from = start + len(signature)
    brace = source.find("{", start)
    if brace < 0:
        raise ValueError(f"missing method body: {signature}")
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
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1
    raise ValueError(f"unterminated method: {signature}")


def normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def is_beach(biome_id: str) -> bool:
    lowered = biome_id.lower()
    return any(token in lowered for token in BEACH_IDS)


def modeled_shortcut(
    *,
    is_beach_candidate: bool,
    sea_level_delta: int,
    upland_t: float,
    sampler_available: bool,
    ocean_distance: int,
    temperate_band: bool,
    mountain_signal: bool | None,
) -> tuple[str, int]:
    sampler_calls = 0
    if not is_beach_candidate:
        return "ordinary_land_path", sampler_calls
    if sea_level_delta > 16 or upland_t > 0.25:
        return "ordinary_land_path", sampler_calls
    if not sampler_available or ocean_distance > 384:
        return "ordinary_land_path", sampler_calls
    mountain_like = False
    if temperate_band:
        if mountain_signal is not None:
            sampler_calls += 1
            mountain_like = mountain_signal
    return ("ordinary_land_path" if mountain_like else "beach_shortcut"), sampler_calls


def modeled_is_mountain(continentalness: float, erosion: float, weirdness: float) -> bool:
    return continentalness > 0.10 and erosion < -0.25 and abs(weirdness) > 0.25


def read_atlas(atlas_root: Path) -> tuple[list[list[str]], Path]:
    images = sorted(atlas_root.rglob("biome_ids.png"))
    if len(images) != 1:
        raise ValueError(f"expected exactly one biome_ids.png below {atlas_root}, found {len(images)}")
    image_path = images[0]
    palette_path = image_path.with_name("biome_palette.json")
    palette_json = json.loads(palette_path.read_text())
    palette = {int(row["index"]): row["biome_id"] for row in palette_json["biomes"]}
    image = Image.open(image_path).convert("RGB")
    rows: list[list[str]] = []
    for y in range(image.height):
        row: list[str] = []
        for x in range(image.width):
            red, green, blue = image.getpixel((x, y))
            if red != green or green != blue:
                raise ValueError(f"non-index pixel at {x},{y}: {(red, green, blue)}")
            if red not in palette:
                raise ValueError(f"palette index {red} missing at {x},{y}")
            row.append(palette[red])
        rows.append(row)
    return rows, image_path


def compare_atlases(
    baseline_root: Path,
    candidate_root: Path,
) -> tuple[list[tuple[int, int, str, str]], Counter[str], Counter[str], Path, Path]:
    baseline, baseline_path = read_atlas(baseline_root)
    candidate, candidate_path = read_atlas(candidate_root)
    if len(baseline) != len(candidate) or len(baseline[0]) != len(candidate[0]):
        raise ValueError(
            f"atlas dimensions differ: baseline={len(baseline[0])}x{len(baseline)} "
            f"candidate={len(candidate[0])}x{len(candidate)}"
        )
    changes: list[tuple[int, int, str, str]] = []
    before_counts: Counter[str] = Counter()
    after_counts: Counter[str] = Counter()
    for y, (before_row, after_row) in enumerate(zip(baseline, candidate)):
        for x, (before, after) in enumerate(zip(before_row, after_row)):
            before_counts[before] += 1
            after_counts[after] += 1
            if before != after:
                changes.append((x, y, before, after))
    return changes, before_counts, after_counts, baseline_path, candidate_path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, default=Path("."))
    parser.add_argument("--baseline-atlas", type=Path)
    parser.add_argument("--candidate-atlas", type=Path)
    args = parser.parse_args()

    source = (args.source_root / SOURCE_REL).read_text()
    checks: list[tuple[str, bool, str]] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append((name, condition, detail))

    try:
        helper = extract_method(source, BEACH_HELPER)
        helper_norm = normalized(helper)
        check(
            "cheap_surface_gate_unchanged",
            "int seaLevelDelta = surfaceY - seaLevel;" in helper
            and "seaLevelDelta > BEACH_SHORTCUT_MAX_SEA_LEVEL_DELTA" in helper
            and "uplandT(surfaceY) > BEACH_SHORTCUT_MAX_UPLAND_T" in helper,
            "sea+16 and upland gates remain",
        )
        check(
            "coast_authority_required",
            "if (sampler == null)" in helper
            and "oceanDistanceBlocks(blockX, blockZ, sampler)" in helper
            and "oceanDistance <= MANGROVE_COASTAL_MAX_BLOCKS" in helper
            and "private static final int MANGROVE_COASTAL_MAX_BLOCKS = 384;" in source,
            "cached ODF authority is required at the existing 384-block near-ocean threshold",
        )
        check(
            "coast_gate_contains_no_generator_reentry",
            not any(
                token in helper
                for token in (
                    "previewTerrain",
                    "previewHeight",
                    "getBaseHeight",
                    "ValueNoise",
                    "sampler.sample",
                )
            ),
            "cached ODF work is allowed; no terrain preview, generator re-entry, new noise, or direct sampler probe",
        )
        check(
            "cheap_gate_order_preserved",
            helper_norm.index("seaLevelDelta > BEACH_SHORTCUT_MAX_SEA_LEVEL_DELTA")
            < helper_norm.index("uplandT(surfaceY) > BEACH_SHORTCUT_MAX_UPLAND_T")
            < helper_norm.index("if (sampler == null)")
            < helper_norm.index("oceanDistanceBlocks(blockX, blockZ, sampler)")
            < helper_norm.index("return oceanDistance <= MANGROVE_COASTAL_MAX_BLOCKS;"),
            "height then upland then coast authority then allow",
        )
    except (ValueError, IndexError) as error:
        check("cheap_surface_gate_unchanged", False, str(error))
        check("coast_authority_required", False, str(error))
        check("coast_gate_contains_no_generator_reentry", False, str(error))
        check("cheap_gate_order_preserved", False, str(error))

    expected_shortcut = normalized(
        """
        boolean beachLike = isBeachLike(base);
        boolean beachMountainNoiseSampled = false;
        boolean beachMountainNoiseLike = false;
        if (beachLike && allowBeachShortcut(generator, columnDecisionY, sampler, blockX, blockZ)) {
            if (beachBandIndex == BAND_TEMPERATE) {
                beachMountainNoiseSampled = true;
                beachMountainNoiseLike = isMountainLike(sampler, blockX, blockZ);
            }
            if (!beachMountainNoiseLike) {
        """
    )
    expected_reuse = normalized(
        """
        boolean mountainNoiseLike = landBandIndex == BAND_TEMPERATE
                && (beachMountainNoiseSampled
                ? beachMountainNoiseLike
                : isMountainLike(sampler, blockX, blockZ));
        """
    )

    for name, signature in (
        ("registry", REGISTRY_PICK),
        ("collection", COLLECTION_PICK),
    ):
        try:
            method = extract_method(source, signature, occurrence=2)
            method_norm = normalized(method)
            shortcut_index = method_norm.find(expected_shortcut)
            reuse_index = method_norm.find(expected_reuse)
            blended_index = method_norm.find("int blendedBandIndex")
            check(
                f"{name}_shortcut_samples_after_cheap_gate",
                shortcut_index >= 0,
                "one temperate-only isMountainLike call after allowBeachShortcut",
            )
            check(
                f"{name}_mountain_reject_falls_through",
                shortcut_index >= 0
                and "if (!beachMountainNoiseLike) {" in method_norm[shortcut_index:blended_index]
                and blended_index > shortcut_index,
                "gentle returns beach; mountain reaches ordinary path",
            )
            check(
                f"{name}_sample_reused",
                reuse_index > blended_index > shortcut_index,
                "ordinary terrain path reuses the one new sample",
            )
            early_section = method_norm[shortcut_index:blended_index] if shortcut_index >= 0 else ""
            check(
                f"{name}_no_generator_reentry",
                shortcut_index >= 0
                and not any(
                    token in early_section
                    for token in (
                        "previewTerrain",
                        "previewHeight",
                        "getBaseHeight",
                        "ValueNoise",
                    )
                ),
                "shortcut may use cached ODF authority but contains no preview, height re-entry, or new noise",
            )
            check(
                f"{name}_single_new_sampler_call",
                early_section.count("isMountainLike(sampler, blockX, blockZ)") == 1,
                f"shortcut_calls={early_section.count('isMountainLike(sampler, blockX, blockZ)')}",
            )
        except ValueError as error:
            for suffix in (
                "shortcut_samples_after_cheap_gate",
                "mountain_reject_falls_through",
                "sample_reused",
                "no_generator_reentry",
                "single_new_sampler_call",
            ):
                check(f"{name}_{suffix}", False, str(error))

    model_cases = [
        ("low_beach", True, 2, 0.02, True, 0, True, False, "beach_shortcut", 1),
        ("rolling_foredune", True, 12, 0.20, True, 64, True, False, "beach_shortcut", 1),
        ("near_ocean_edge", True, 12, 0.20, True, 384, True, False, "beach_shortcut", 1),
        ("inclusive_height_edge", True, 16, 0.24, True, 32, True, False, "beach_shortcut", 1),
        ("inclusive_upland_edge", True, 15, 0.25, True, 32, True, False, "beach_shortcut", 1),
        ("mountain_class_ridge", True, 12, 0.20, True, 32, True, True, "ordinary_land_path", 1),
        ("null_sampler_rejects_unknown_coast", True, 12, 0.20, False, 0, True, None, "ordinary_land_path", 0),
        ("reported_area_inland_608_rejected", True, 12, 0.20, True, 608, True, False, "ordinary_land_path", 0),
        ("first_inland_block_rejected", True, 12, 0.20, True, 385, True, False, "ordinary_land_path", 0),
        ("height_reject_before_sampler", True, 17, 0.20, True, 0, True, True, "ordinary_land_path", 0),
        ("upland_reject_before_sampler", True, 12, 0.26, True, 0, True, True, "ordinary_land_path", 0),
        ("non_temperate_unchanged", True, 12, 0.20, True, 32, False, True, "beach_shortcut", 0),
        ("non_beach_unchanged", False, 12, 0.20, True, 0, True, True, "ordinary_land_path", 0),
    ]
    for (
        case,
        beach,
        delta,
        upland,
        sampler_available,
        ocean_distance,
        temperate,
        mountain,
        expected_path,
        expected_calls,
    ) in model_cases:
        actual_path, actual_calls = modeled_shortcut(
            is_beach_candidate=beach,
            sea_level_delta=delta,
            upland_t=upland,
            sampler_available=sampler_available,
            ocean_distance=ocean_distance,
            temperate_band=temperate,
            mountain_signal=mountain,
        )
        check(
            f"model_{case}",
            (actual_path, actual_calls) == (expected_path, expected_calls),
            f"path={actual_path} sampler_calls={actual_calls}",
        )

    climate_cases = [
        ("all_just_beyond_thresholds", 0.1001, -0.2501, 0.2501, True),
        ("continentalness_equality_is_not_mountain", 0.10, -0.2501, 0.2501, False),
        ("erosion_equality_is_not_mountain", 0.1001, -0.25, 0.2501, False),
        ("positive_weirdness_equality_is_not_mountain", 0.1001, -0.2501, 0.25, False),
        ("negative_weirdness_equality_is_not_mountain", 0.1001, -0.2501, -0.25, False),
    ]
    for case, continentalness, erosion, weirdness, expected in climate_cases:
        actual = modeled_is_mountain(continentalness, erosion, weirdness)
        check(
            f"model_climate_{case}",
            actual == expected,
            (
                f"continentalness={continentalness} erosion={erosion} "
                f"weirdness={weirdness} mountain={actual}"
            ),
        )

    if args.baseline_atlas is not None or args.candidate_atlas is not None:
        if args.baseline_atlas is None or args.candidate_atlas is None:
            check("atlas_pair_supplied", False, "both --baseline-atlas and --candidate-atlas are required")
        else:
            try:
                changes, before_counts, after_counts, before_path, after_path = compare_atlases(
                    args.baseline_atlas,
                    args.candidate_atlas,
                )
                negative_ids = {
                    biome_id
                    for biome_id in before_counts
                    if after_counts[biome_id] - before_counts[biome_id] < 0
                }
                positive_ids = {
                    biome_id
                    for biome_id in after_counts
                    if after_counts[biome_id] - before_counts[biome_id] > 0
                }
                changed_from = {before for _, _, before, _ in changes}
                changed_to = {after for _, _, _, after in changes}
                check(
                    "atlas_exact_compare_completed",
                    sum(before_counts.values()) == sum(after_counts.values()),
                    f"changed_cells={len(changes)}",
                )
                check(
                    "atlas_changes_only_from_beach_family",
                    all(is_beach(before) for _, _, before, _ in changes),
                    f"changed_from={sorted(changed_from)}",
                )
                check(
                    "atlas_changes_leave_beach_family",
                    all(not is_beach(after) for _, _, _, after in changes),
                    f"changed_to={sorted(changed_to)}",
                )
                check(
                    "atlas_distribution_only_loses_beach",
                    all(is_beach(biome_id) for biome_id in negative_ids),
                    f"negative_ids={sorted(negative_ids)}",
                )
                check(
                    "atlas_distribution_only_gains_land",
                    all(not is_beach(biome_id) for biome_id in positive_ids),
                    f"positive_ids={sorted(positive_ids)}",
                )
                print(
                    "ATLAS "
                    f"baseline={before_path} candidate={after_path} "
                    f"total_cells={sum(before_counts.values())} changed_cells={len(changes)} "
                    f"changed_from={sorted(changed_from)} changed_to={sorted(changed_to)}"
                )
                for biome_id in sorted(before_counts.keys() | after_counts.keys()):
                    delta = after_counts[biome_id] - before_counts[biome_id]
                    if delta:
                        print(
                            f"ATLAS_DELTA biome={biome_id} before={before_counts[biome_id]} "
                            f"after={after_counts[biome_id]} delta={delta:+d}"
                        )
            except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
                check("atlas_compare", False, str(error))

    failed = [(name, detail) for name, ok, detail in checks if not ok]
    for name, detail in failed:
        print(f"FAIL {name}: {detail}")
    if failed:
        print(f"VERDICT RED failures={len(failed)} assertions={len(checks)}")
        return 1
    print(f"VERDICT GREEN assertions={len(checks)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
