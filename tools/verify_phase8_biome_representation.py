#!/usr/bin/env python3
"""Focused structural proof for the Phase 8 Pale Garden owner repair."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"
DEFAULT_TEMPERATE_MOUNTAIN_TAG = (
    ROOT
    / "src/main/resources/data/globe/tags/worldgen/biome/lat_temperate_mountain.json"
)
WINDSWEPT_VARIANTS = {
    "minecraft:windswept_forest",
    "minecraft:windswept_gravelly_hills",
}

EXPECTED_WINDSWEPT_HELPER_BODY = """
return isBiomeId(biome, "minecraft:windswept_forest")
        || isBiomeId(biome, "minecraft:windswept_gravelly_hills");
"""
EXPECTED_WINDSWEPT_OWNER_BODY = """
if (!isTemperateWindsweptVariant(candidate)
        || (bandIndex == BAND_TEMPERATE && mountainLike)) {
    return candidate;
}
Holder<Biome> fallback = safeVanillaFallbackForBand(biomes, bandIndex);
return fallback != null ? fallback : candidate;
"""


def normalize_java_body(body: str) -> str:
    """Bind reviewed method semantics while ignoring formatting-only whitespace."""
    return re.sub(r"\s+", "", body)


def extract_java_method_body(source: str, signature_pattern: str) -> str | None:
    """Extract one Java method body with balanced braces, ignoring strings/comments."""
    match = re.search(signature_pattern, source, re.DOTALL)
    if match is None:
        return None
    brace = source.rfind("{", match.start(), match.end())
    if brace < 0:
        return None

    depth = 1
    index = brace + 1
    state = "code"
    escaped = False
    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == "/" and nxt == "/":
                state = "line_comment"
                index += 2
                continue
            if char == "/" and nxt == "*":
                state = "block_comment"
                index += 2
                continue
            if char == '"':
                state = "string"
                escaped = False
            elif char == "'":
                state = "char"
                escaped = False
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source[brace + 1:index]
        elif state == "line_comment":
            if char == "\n":
                state = "code"
        elif state == "block_comment":
            if char == "*" and nxt == "/":
                state = "code"
                index += 2
                continue
        elif state in {"string", "char"}:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif (state == "string" and char == '"') or (state == "char" and char == "'"):
                state = "code"
        index += 1
    return None


def picker_has_terminal_windswept_clamp(body: str | None, pool_name: str) -> bool:
    if body is None:
        return False
    quarantine = (
        f"out = quarantineUnknownCustomLandBiome({pool_name}, out, base, blockX, blockZ, "
        "landBandIndex, mountainLike);"
    )
    owner = (
        f"out = clampTemperateWindsweptMountainOwnership({pool_name}, out, "
        "landBandIndex, mountainLike);"
    )
    sequence_matches = list(re.finditer(
        re.escape(quarantine) + r"\s*" + re.escape(owner), body
    ))
    if len(sequence_matches) != 1:
        return False
    if body.count(quarantine) != 1 or body.count(owner) != 1:
        return False

    tail = body[sequence_matches[0].end():]
    returns = list(re.finditer(r"\breturn\s+out\s*;", tail))
    if len(returns) != 1:
        return False
    before_return = tail[:returns[0].start()]
    if re.search(r"\bout\s*(?:[+\-*/%&|^]?=|\+\+|--)", before_return):
        return False
    return tail[returns[0].end():].strip() == ""


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


def mountain_tag_ids(tag_text: str) -> set[str]:
    payload = json.loads(tag_text)
    ids: set[str] = set()
    for value in payload.get("values", []):
        if isinstance(value, str):
            ids.add(value)
        elif isinstance(value, dict) and isinstance(value.get("id"), str):
            ids.add(value["id"])
    return ids


def check_windswept_owner(source: str, tag_text: str) -> list[tuple[str, bool]]:
    tag_ids = mountain_tag_ids(tag_text)
    owner_signature_count = len(re.findall(
        r"clampTemperateWindsweptMountainOwnership\((?:Registry<Biome>|Collection<Holder<Biome>>) biomes,",
        source,
    ))
    owner_call_count = len(re.findall(
        r"out\s*=\s*clampTemperateWindsweptMountainOwnership\("
        r"(?:biomeRegistry|biomePool),\s*out,\s*landBandIndex,\s*mountainLike\);",
        source,
    ))
    quarantine_positions = [
        match.start()
        for match in re.finditer(
            r"out\s*=\s*quarantineUnknownCustomLandBiome\("
            r"(?:biomeRegistry|biomePool),\s*out,\s*base,\s*blockX,\s*blockZ,\s*"
            r"landBandIndex,\s*mountainLike\);",
            source,
        )
    ]
    owner_call_positions = [
        match.start()
        for match in re.finditer(
            r"out\s*=\s*clampTemperateWindsweptMountainOwnership\(", source
        )
    ]
    owner_after_quarantine = (
        len(quarantine_positions) == 2
        and len(owner_call_positions) == 2
        and all(owner > quarantine for owner, quarantine in zip(owner_call_positions, quarantine_positions))
    )
    helper_body = extract_java_method_body(
        source,
        r"private\s+static\s+boolean\s+isTemperateWindsweptVariant\(\s*"
        r"Holder<Biome>\s+biome\s*\)\s*\{",
    )
    registry_owner_body = extract_java_method_body(
        source,
        r"private\s+static\s+Holder<Biome>\s+clampTemperateWindsweptMountainOwnership\(\s*"
        r"Registry<Biome>\s+biomes,\s*Holder<Biome>\s+candidate,\s*int\s+bandIndex,\s*"
        r"boolean\s+mountainLike\s*\)\s*\{",
    )
    collection_owner_body = extract_java_method_body(
        source,
        r"private\s+static\s+Holder<Biome>\s+clampTemperateWindsweptMountainOwnership\(\s*"
        r"Collection<Holder<Biome>>\s+biomes,\s*Holder<Biome>\s+candidate,\s*int\s+bandIndex,\s*"
        r"boolean\s+mountainLike\s*\)\s*\{",
    )
    registry_picker_body = extract_java_method_body(
        source,
        r"public\s+static\s+Holder<Biome>\s+pick\(\s*Registry<Biome>\s+biomeRegistry,"
        r"[^{}]*NoiseBasedChunkGenerator\s+generator,\s*RandomState\s+noiseConfig,\s*"
        r"LevelHeightAccessor\s+heightView\s*\)\s*\{",
    )
    collection_picker_body = extract_java_method_body(
        source,
        r"public\s+static\s+Holder<Biome>\s+pick\(\s*Collection<Holder<Biome>>\s+biomePool,"
        r"[^{}]*NoiseBasedChunkGenerator\s+generator,\s*RandomState\s+noiseConfig,\s*"
        r"LevelHeightAccessor\s+heightView\s*\)\s*\{",
    )
    helper_exact = (
        helper_body is not None
        and normalize_java_body(helper_body) == normalize_java_body(EXPECTED_WINDSWEPT_HELPER_BODY)
    )
    owner_bodies_exact = all(
        body is not None
        and normalize_java_body(body) == normalize_java_body(EXPECTED_WINDSWEPT_OWNER_BODY)
        for body in (registry_owner_body, collection_owner_body)
    )
    final_clamps_terminal = (
        picker_has_terminal_windswept_clamp(registry_picker_body, "biomeRegistry")
        and picker_has_terminal_windswept_clamp(collection_picker_body, "biomePool")
    )

    return [
        ("mountain_tag_contains_windswept_forest", "minecraft:windswept_forest" in tag_ids),
        ("mountain_tag_contains_windswept_gravelly_hills", "minecraft:windswept_gravelly_hills" in tag_ids),
        ("exactly_two_owner_overloads", owner_signature_count == 2),
        ("both_final_picker_paths_call_owner", owner_call_count == 2),
        ("owner_is_after_custom_quarantine", owner_after_quarantine),
        ("variant_helper_names_only_both_admitted_ids", helper_body is not None and all(
            variant in helper_body for variant in WINDSWEPT_VARIANTS
        ) and "minecraft:windswept_hills" not in helper_body),
        ("variant_helper_body_exact", helper_exact),
        ("owner_requires_temperate_and_mountain", owner_bodies_exact),
        ("owner_uses_band_safe_fallback", owner_bodies_exact),
        ("both_final_owner_clamps_terminal", final_clamps_terminal),
        ("nonmountain_temperate_filter_still_removes_family", bool(re.search(
            r"if \(bandIndex == BAND_TEMPERATE && !mountainLike\)\s*\{\s*"
            r"out = removeTemperateMountainFamily\(out\);",
            source,
        ))),
        ("mountain_promotion_still_temperate_only", source.count(
            "boolean mountainPromotion = mountainLike\n"
            "                        && landBandIndex == BAND_TEMPERATE;"
        ) == 1 and source.count(
            "boolean mountainPromotion = mountainLike\n"
            "                    && landBandIndex == BAND_TEMPERATE;"
        ) == 1),
    ]


def run_negative_controls(source: str, tag_text: str) -> list[tuple[str, bool]]:
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
    windswept_controls: list[tuple[str, str, str, str]] = [
        (
            "missing_gravelly_mountain_admission_rejected",
            source,
            tag_text.replace('    "minecraft:windswept_gravelly_hills",\n', "", 1),
            "mountain_tag_contains_windswept_gravelly_hills",
        ),
        (
            "missing_forest_mountain_admission_rejected",
            source,
            tag_text.replace('    "minecraft:windswept_forest",\n', "", 1),
            "mountain_tag_contains_windswept_forest",
        ),
        (
            "missing_final_owner_call_rejected",
            source.replace(
                "out = clampTemperateWindsweptMountainOwnership(biomePool, out, landBandIndex, mountainLike);",
                "// hostile control: final owner call removed",
                1,
            ),
            tag_text,
            "both_final_picker_paths_call_owner",
        ),
        (
            "nonmountain_survival_rejected",
            source.replace(
                "bandIndex == BAND_TEMPERATE && mountainLike",
                "bandIndex == BAND_TEMPERATE",
            ),
            tag_text,
            "owner_requires_temperate_and_mountain",
        ),
        (
            "warm_band_survival_rejected",
            source.replace(
                "bandIndex == BAND_TEMPERATE && mountainLike",
                "bandIndex <= BAND_TEMPERATE && mountainLike",
            ),
            tag_text,
            "owner_requires_temperate_and_mountain",
        ),
        (
            "helper_always_false_rejected",
            source.replace(
                'return isBiomeId(biome, "minecraft:windswept_forest")\n'
                '                || isBiomeId(biome, "minecraft:windswept_gravelly_hills");',
                'return (isBiomeId(biome, "minecraft:windswept_forest")\n'
                '                || isBiomeId(biome, "minecraft:windswept_gravelly_hills")) && false;',
                1,
            ),
            tag_text,
            "variant_helper_body_exact",
        ),
        (
            "owner_bypassed_by_early_return_rejected",
            source.replace(
                "boolean mountainLike) {\n"
                "        if (!isTemperateWindsweptVariant(candidate)",
                "boolean mountainLike) {\n"
                "        if (true) { return candidate; }\n"
                "        if (!isTemperateWindsweptVariant(candidate)",
            ),
            tag_text,
            "owner_requires_temperate_and_mountain",
        ),
        (
            "subpolar_early_survival_rejected",
            source.replace(
                "boolean mountainLike) {\n"
                "        if (!isTemperateWindsweptVariant(candidate)",
                "boolean mountainLike) {\n"
                "        if (isTemperateWindsweptVariant(candidate) "
                "&& bandIndex == BAND_SUBPOLAR && mountainLike) {\n"
                "            return candidate;\n"
                "        }\n"
                "        if (!isTemperateWindsweptVariant(candidate)",
            ),
            tag_text,
            "owner_requires_temperate_and_mountain",
        ),
        (
            "late_reintroduction_after_clamp_rejected",
            source.replace(
                "out = clampTemperateWindsweptMountainOwnership("
                "biomeRegistry, out, landBandIndex, mountainLike);",
                "out = clampTemperateWindsweptMountainOwnership("
                "biomeRegistry, out, landBandIndex, mountainLike);\n"
                "        out = base;",
                1,
            ).replace(
                "out = clampTemperateWindsweptMountainOwnership("
                "biomePool, out, landBandIndex, mountainLike);",
                "out = clampTemperateWindsweptMountainOwnership("
                "biomePool, out, landBandIndex, mountainLike);\n"
                "        out = base;",
                1,
            ),
            tag_text,
            "both_final_owner_clamps_terminal",
        ),
    ]
    for name, mutant_source, mutant_tag, expected_failure in windswept_controls:
        status = dict(check_windswept_owner(mutant_source, mutant_tag))
        results.append((name, status.get(expected_failure) is False))
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--temperate-mountain-tag", type=Path, default=DEFAULT_TEMPERATE_MOUNTAIN_TAG)
    parser.add_argument("--negative-controls", action="store_true")
    args = parser.parse_args()

    source = args.source.read_text(encoding="utf-8")
    tag_text = args.temperate_mountain_tag.read_text(encoding="utf-8")
    checks = check_source(source)
    checks.extend(check_windswept_owner(source, tag_text))
    if args.negative_controls:
        checks.extend(run_negative_controls(source, tag_text))

    failed = 0
    for name, passed in checks:
        print(f"{name}={'PASS' if passed else 'FAIL'}")
        failed += 0 if passed else 1
    print(f"verdict={'GREEN' if failed == 0 else 'RED'} failures={failed} assertions={len(checks)}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
