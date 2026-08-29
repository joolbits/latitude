#!/usr/bin/env python3
"""Dependency-free structural verifier for Latitude Performance Slice B."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


SOURCE_REL = Path("src/main/java/com/example/globe/world/LatitudeBiomes.java")
CONSTANT_ID_NAMES = {"SWAMP_ID", "MANGROVE_ID"}
# Structural tripwire for the current production callsite inventory. The lowland-Meadow
# correction left 205 calls; the accepted temperate-windswept ownership helper adds exactly
# the two literal calls frozen below. Future changes require fresh source provenance.
EXPECTED_ACTIVE_CONSTANT_ID_CALLS = 207
EXPECTED_TEMPERATE_WINDSWEPT_IDS = [
    '"minecraft:windswept_forest"',
    '"minecraft:windswept_gravelly_hills"',
]


def extract_method(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise ValueError(f"missing method signature: {signature}")
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
    raise ValueError(f"unterminated method body: {signature}")


def private_id_call_arguments(source: str) -> tuple[list[str], list[str]]:
    active: list[str] = []
    public_wrapper: list[str] = []
    for line_number, line in enumerate(source.splitlines(), 1):
        if (
            "isBiomeId(" not in line
            or "boolean isBiomeId" in line
            or line.lstrip().startswith("*")
        ):
            continue
        for match in re.finditer(r"isBiomeId\s*\([^,]+,\s*([^\)]+)\)", line):
            argument = match.group(1).strip()
            if "return isBiomeId(entry, id);" in line:
                public_wrapper.append(argument)
            else:
                active.append(argument)
    return active, public_wrapper


def is_constant_id(argument: str) -> bool:
    return bool(re.fullmatch(r'"[^"\\]*"', argument)) or argument in CONSTANT_ID_NAMES


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source-root",
        type=Path,
        default=Path("."),
        help="root containing src/main/java/.../LatitudeBiomes.java",
    )
    parser.add_argument(
        "--baseline-root",
        type=Path,
        help="optional preserved-HEAD source root for forbidden-method comparisons",
    )
    args = parser.parse_args()

    source_path = args.source_root / SOURCE_REL
    source = source_path.read_text()
    baseline = None
    if args.baseline_root is not None:
        baseline = (args.baseline_root / SOURCE_REL).read_text()

    checks: list[tuple[str, bool, str]] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append((name, condition, detail))

    active_ids, public_ids = private_id_call_arguments(source)
    dynamic_active = [argument for argument in active_ids if not is_constant_id(argument)]
    check(
        "active_identifier_keys_constant",
        len(active_ids) == EXPECTED_ACTIVE_CONSTANT_ID_CALLS and not dynamic_active,
        f"active_calls={len(active_ids)} dynamic={dynamic_active}",
    )
    check(
        "public_dynamic_wrapper_isolated",
        not public_ids and source.count("isBiomeIdPublic(") == 1,
        f"public_wrapper_args={public_ids} definitions_or_calls={source.count('isBiomeIdPublic(')}",
    )
    try:
        windswept_helper = extract_method(
            source,
            "private static boolean isTemperateWindsweptVariant(Holder<Biome> biome)",
        )
        windswept_ids, windswept_public_ids = private_id_call_arguments(windswept_helper)
        check(
            "temperate_windswept_inventory_provenance",
            windswept_ids == EXPECTED_TEMPERATE_WINDSWEPT_IDS
            and not windswept_public_ids
            and all(is_constant_id(argument) for argument in windswept_ids),
            f"helper_calls={windswept_ids} helper_public={windswept_public_ids}",
        )
    except ValueError as error:
        check("temperate_windswept_inventory_provenance", False, str(error))
    check(
        "no_beach_identifier_cache_calls",
        not re.search(r'isBiomeId\([^\n]*(?:beach|shore)', source),
        "isBiomeId has no beach/shore key",
    )

    cache_declaration = re.search(
        r"private static final java\.util\.concurrent\.ConcurrentHashMap"
        r"<String, Identifier> ID_PARSE_CACHE\s*=\s*"
        r"new java\.util\.concurrent\.ConcurrentHashMap<>\(\);",
        source,
    )
    check("identifier_cache_thread_safe", cache_declaration is not None, "ConcurrentHashMap")
    check(
        "identifier_cache_single_compute",
        source.count("ID_PARSE_CACHE.computeIfAbsent(id, Identifier::parse)") == 1,
        f"compute_calls={source.count('ID_PARSE_CACHE.computeIfAbsent(id, Identifier::parse)')}",
    )

    try:
        private_helper = extract_method(
            source, "private static boolean isBiomeId(Holder<Biome> entry, String id)"
        )
        check(
            "private_hot_path_uses_cache",
            "ID_PARSE_CACHE.computeIfAbsent(id, Identifier::parse)" in private_helper,
            "private isBiomeId",
        )
        check(
            "private_null_semantics_preserved",
            private_helper.find("if (entry == null)") >= 0
            and private_helper.find("if (entry == null)")
            < private_helper.find("ID_PARSE_CACHE.computeIfAbsent"),
            "null entry returns before parsing",
        )
    except ValueError as error:
        check("private_hot_path_uses_cache", False, str(error))
        check("private_null_semantics_preserved", False, str(error))

    try:
        public_helper = extract_method(
            source, "public static boolean isBiomeIdPublic(Holder<Biome> entry, String id)"
        )
        check(
            "public_dynamic_path_uncached",
            "Identifier.parse(id)" in public_helper
            and "ID_PARSE_CACHE" not in public_helper
            and "return isBiomeId(entry, id)" not in public_helper,
            "public helper parses without retaining arbitrary keys",
        )
        check(
            "public_null_semantics_preserved",
            public_helper.find("if (entry == null)") >= 0
            and public_helper.find("if (entry == null)")
            < public_helper.find("Identifier.parse(id)"),
            "null entry returns before parsing",
        )
    except ValueError as error:
        check("public_dynamic_path_uncached", False, str(error))
        check("public_null_semantics_preserved", False, str(error))

    try:
        entry_by_id = extract_method(
            source,
            "private static Holder<Biome> entryById(Collection<Holder<Biome>> biomes, String id)",
        )
        check(
            "entry_by_id_remains_uncached",
            "Identifier target = Identifier.parse(id);" in entry_by_id
            and "ID_PARSE_CACHE" not in entry_by_id,
            "variable and beach-serving helper excluded",
        )
    except ValueError as error:
        check("entry_by_id_remains_uncached", False, str(error))

    property_fields = {
        "DISABLE_RADIUS_OVERRIDE": (
            'Boolean.getBoolean("latitude.disableRadiusOverride")',
            "latitude.disableRadiusOverride",
            6,
        ),
        "SKIP_PREVIEW_HEIGHT_FOR_BIOME_PNG": (
            'Boolean.parseBoolean(System.getProperty("latitude.skipPreviewHeightForBiomePng", "true"))',
            "latitude.skipPreviewHeightForBiomePng",
            2,
        ),
    }
    for field, (expression, key, expected_uses) in property_fields.items():
        field_occurrences = len(re.findall(rf"\b{field}\b", source))
        declaration = re.search(
            rf"private static final boolean {field}\s*=\s*{re.escape(expression)};",
            source,
        )
        check(
            f"{field.lower()}_exact_launch_expression",
            declaration is not None,
            expression,
        )
        check(
            f"{field.lower()}_single_raw_read",
            source.count(key) == 1,
            f"key_occurrences={source.count(key)}",
        )
        check(
            f"{field.lower()}_all_uses_cached",
            field_occurrences == expected_uses,
            f"field_occurrences={field_occurrences}",
        )

    check(
        "no_property_mutation_in_owner",
        "System.setProperty(" not in source and "System.clearProperty(" not in source,
        "LatitudeBiomes contains no runtime property mutation",
    )
    check(
        "only_admitted_property_caches",
        len(re.findall(r"private static final boolean (?:DISABLE_RADIUS_OVERRIDE|SKIP_PREVIEW_HEIGHT_FOR_BIOME_PNG)", source))
        == 2,
        # SKIP_PREVIEW_HEIGHT_FOR_WORLDGEN was dropped when live worldgen stopped being allowed to
        # re-enter the chunk generator for a terrain preview: MIXIN/CAVE_CLAMP now skip it as an
        # invariant, not as a launch-time tuning flag, so there is no property left to cache.
        "exactly two admitted cached flags",
    )

    if baseline is not None:
        forbidden_methods = [
            "private static Holder<Biome> entryById(Collection<Holder<Biome>> biomes, String id)",
            "private static Holder<Biome> pickBeachForBand(Registry<Biome> biomes",
            "private static Holder<Biome> pickBeachForBand(Collection<Holder<Biome>> biomes",
            "private static boolean allowBeachShortcut(NoiseBasedChunkGenerator generator",
        ]
        for signature in forbidden_methods:
            try:
                check(
                    f"baseline_match_{signature.split('(')[0].split()[-1]}_{'collection' if 'Collection' in signature else 'registry' if 'Registry' in signature else 'policy'}",
                    extract_method(source, signature) == extract_method(baseline, signature),
                    signature,
                )
            except ValueError as error:
                check(f"baseline_match_{len(checks)}", False, str(error))

    failed = [(name, detail) for name, ok, detail in checks if not ok]
    print(
        "OVERHEAD "
        f"active_constant_id_calls={len(active_ids)} "
        f"identifier_parse_sites={source.count('Identifier.parse(')} "
        f"launch_property_key_reads="
        f"{source.count('latitude.disableRadiusOverride') + source.count('latitude.skipPreviewHeightForBiomePng')}"
    )
    for name, detail in failed:
        print(f"FAIL {name}: {detail}")
    if failed:
        print(f"VERDICT RED failures={len(failed)} assertions={len(checks)}")
        return 1
    print(f"VERDICT GREEN assertions={len(checks)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
