#!/usr/bin/env python3
"""Structural and policy proof for Latitude's per-column biome cache."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys


ASSERTIONS = 0

MIXIN_PATH = Path(
    "src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java"
)
BIOMES_PATH = Path("src/main/java/com/example/globe/world/LatitudeBiomes.java")


def require(condition: bool, message: str, failures: list[str]) -> None:
    global ASSERTIONS
    ASSERTIONS += 1
    if not condition:
        failures.append(message)


def verify_structure(root: Path) -> list[str]:
    mixin = (root / MIXIN_PATH).read_text(encoding="utf-8")
    biomes = (root / BIOMES_PATH).read_text(encoding="utf-8")
    failures: list[str] = []

    wrap_start = mixin.find("private void globe$wrapBiomeSupplier")
    wrapped_start = mixin.find("BiomeResolver wrapped =", wrap_start)
    cave_fix = mixin.find("if (FIX_SURFACE_CAVE_BIOMES && caveCurrent)", wrapped_start)
    cave_return = mixin.find("if (caveCurrent)", cave_fix + 1)
    cache_window = mixin.find("if (blockY >= colDecisionY - 16)", cave_return)

    require(wrap_start >= 0, "missing globe$wrapBiomeSupplier", failures)
    require(wrapped_start > wrap_start, "missing wrapped resolver", failures)
    require(
        "public static int surfaceDecisionY(" in biomes,
        "surfaceDecisionY is not the public shared cache seam",
        failures,
    )
    require(
        "Long2ObjectOpenHashMap<Holder<Biome>> columnBaseCache" in mixin,
        "missing local fixed-Y base cache",
        failures,
    )
    require(
        "Long2IntOpenHashMap columnDecisionYCache" in mixin,
        "missing local column decision-Y cache",
        failures,
    )
    require(
        "Long2ObjectOpenHashMap<Holder<Biome>> columnPickCache" in mixin,
        "missing local surface-pick cache",
        failures,
    )
    require(
        "Long2ObjectOpenHashMap<Holder<Biome>> columnPickBase" in mixin,
        "missing cached-pick base identity guard",
        failures,
    )

    local_cache_region = (
        mixin[wrap_start:wrapped_start] if wrap_start >= 0 and wrapped_start >= 0 else ""
    )
    for name in (
        "columnBaseCache",
        "columnDecisionYCache",
        "columnPickCache",
        "columnPickBase",
    ):
        require(
            name in local_cache_region,
            f"{name} is not local to one wrap invocation",
            failures,
        )

    require(
        "Holder<Biome> current = sourceSupplier.getNoiseBiome(x, y, z, sampler);"
        in mixin,
        "per-Y current source sample is not preserved",
        failures,
    )
    require(
        "Holder<Biome> base = columnBaseCache.get(colKey);" in mixin
        and "base = sourceSupplier.getNoiseBiome("
        in mixin
        and "columnBaseCache.put(colKey, base);" in mixin,
        "fixed-Y base is not populated once through the local cache",
        failures,
    )
    require(
        cave_fix >= 0
        and cave_return > cave_fix
        and cache_window > cave_return,
        "surface-pick cache runs before cave/deep preservation branches",
        failures,
    )
    require(
        "colDecisionY = LatitudeBiomes.surfaceDecisionY(" in mixin,
        "cache does not use LatitudeBiomes' exact surface decision seam",
        failures,
    )
    require(
        "columnPickBase.get(colKey) == base" in mixin,
        "surface-pick reuse is not guarded by fixed base holder identity",
        failures,
    )
    require(
        "Holder<Biome> picked = globe$pickOrNull(" in mixin
        and "if (picked != null)" in mixin
        and mixin.count("columnPickCache.put(colKey, picked);") == 1
        and mixin.count("columnPickBase.put(colKey, base);") == 1,
        "eligible surface path does not cache only a direct non-null pick",
        failures,
    )
    require(
        "return pickSafeFallback(biomes, blockZ);" in mixin[cache_window:]
        and mixin.find("return pickSafeFallback(biomes, blockZ);", cache_window)
        > mixin.find("if (picked != null)", cache_window),
        "eligible exception/null path does not return an uncached fallback",
        failures,
    )
    require(
        "private static Holder<Biome> globe$pickOrNull(" in mixin
        and "private static Holder<Biome> globe$pickOrFallback(" in mixin
        and "Holder<Biome> picked = globe$pickOrNull(" in mixin,
        "picker attempt and fallback flows are not separated for retry safety",
        failures,
    )
    require(
        "return globe$pickOrFallback(" in mixin
        and "private static Holder<Biome> globe$pickOrFallback(" in mixin,
        "picker exception/null fallback was not kept in one shared flow",
        failures,
    )

    return failures


def verify_policy_model() -> list[str]:
    """Exercise the intended cache policy independently of Minecraft classes."""

    failures: list[str] = []
    columns = 16
    height_quarts = 96
    min_quart_y = -16
    decision_y = 64
    threshold = decision_y - 16

    current_samples = 0
    base_computes = 0
    surface_pick_computes = 0
    deep_pick_computes = 0
    bases: dict[int, object] = {}
    picks: dict[int, tuple[object, object]] = {}

    for column in range(columns):
        for local_y in range(height_quarts):
            block_y = ((min_quart_y + local_y) << 2) + 2
            current_samples += 1
            if column not in bases:
                bases[column] = object()
                base_computes += 1
            base = bases[column]

            if block_y >= threshold:
                cached = picks.get(column)
                if cached is None or cached[0] is not base:
                    picks[column] = (base, object())
                    surface_pick_computes += 1
            else:
                deep_pick_computes += 1

    require(
        current_samples == columns * height_quarts,
        "policy model stopped sampling current at every Y",
        failures,
    )
    require(
        base_computes == columns,
        "policy model did not reduce fixed-Y base computation to once per column",
        failures,
    )
    require(
        surface_pick_computes == columns,
        "policy model did not reduce eligible surface picks to once per column",
        failures,
    )
    require(
        deep_pick_computes > 0,
        "policy model did not retain exact deep-cell picks",
        failures,
    )

    class Model:
        def __init__(self) -> None:
            self.base_cache: dict[int, object] = {}
            self.pick_cache: dict[int, tuple[object, object]] = {}
            self.current_samples = 0
            self.base_computes = 0
            self.pick_attempts = 0

        def resolve(
            self,
            column: int,
            block_y: int,
            decision: int,
            raw_base: object,
            outcome: object | None = None,
            *,
            cave_current: bool = False,
            cave_deck_base: object | None = None,
            throws: bool = False,
        ) -> object:
            self.current_samples += 1
            if column not in self.base_cache:
                self.base_cache[column] = raw_base
                self.base_computes += 1
            base = self.base_cache[column]
            if block_y > 20 and cave_deck_base is not None:
                base = cave_deck_base

            if cave_current:
                return "cave-result"

            if block_y < decision - 16:
                self.pick_attempts += 1
                if throws or outcome is None:
                    return "fallback"
                return outcome

            cached = self.pick_cache.get(column)
            if cached is not None and cached[0] is base:
                return cached[1]

            self.pick_attempts += 1
            if throws or outcome is None:
                return "fallback"
            self.pick_cache[column] = (base, outcome)
            return outcome

    normal_base = object()
    successful_pick = object()
    model = Model()

    cave_result = model.resolve(
        0, -30, decision_y, normal_base, cave_current=True
    )
    require(
        cave_result == "cave-result"
        and model.current_samples == 1
        and model.pick_attempts == 0
        and not model.pick_cache,
        "caveCurrent did not preserve its per-Y result ahead of the surface cache",
        failures,
    )

    thrown_result = model.resolve(
        1, 80, decision_y, normal_base, throws=True
    )
    require(
        thrown_result == "fallback"
        and model.pick_attempts == 1
        and 1 not in model.pick_cache,
        "thrown picker attempt populated the surface cache",
        failures,
    )

    null_result = model.resolve(1, 84, decision_y, normal_base, outcome=None)
    require(
        null_result == "fallback"
        and model.pick_attempts == 2
        and 1 not in model.pick_cache,
        "null picker attempt populated the surface cache or suppressed retry",
        failures,
    )

    retry_result = model.resolve(
        1, 88, decision_y, normal_base, outcome=successful_pick
    )
    cached_result = model.resolve(
        1, 92, decision_y, normal_base, outcome=object()
    )
    require(
        retry_result is successful_pick
        and cached_result is successful_pick
        and model.pick_attempts == 3
        and model.pick_cache[1][1] is successful_pick,
        "successful retry was not cached after transient exception/null failures",
        failures,
    )

    cave_raw_base = object()
    plains_substitute = object()
    high_pick = object()
    low_pick = object()
    high_pick_again = object()
    identity_model = Model()
    identity_model.resolve(
        2,
        22,
        0,
        cave_raw_base,
        high_pick,
        cave_deck_base=plains_substitute,
    )
    identity_model.resolve(2, -10, 0, cave_raw_base, low_pick)
    identity_model.resolve(
        2,
        26,
        0,
        cave_raw_base,
        high_pick_again,
        cave_deck_base=plains_substitute,
    )
    require(
        identity_model.pick_attempts == 3
        and identity_model.base_computes == 1
        and identity_model.pick_cache[2][0] is plains_substitute
        and identity_model.pick_cache[2][1] is high_pick_again,
        "cave-deck base substitution reused a pick from a different base identity",
        failures,
    )

    old_base_samples = columns * height_quarts
    old_pick_samples = columns * height_quarts
    new_pick_samples = surface_pick_computes + deep_pick_computes
    print(
        "MODEL "
        f"current={current_samples} "
        f"base_before={old_base_samples} base_after={base_computes} "
        f"pick_before={old_pick_samples} pick_after={new_pick_samples} "
        f"surface_pick_after={surface_pick_computes} "
        f"deep_pick_after={deep_pick_computes}"
    )
    return failures


def main() -> int:
    global ASSERTIONS
    ASSERTIONS = 0
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source-root",
        type=Path,
        default=Path.cwd(),
        help="Latitude repository root (default: current directory)",
    )
    args = parser.parse_args()

    root = args.source_root.resolve()
    failures = verify_structure(root)
    failures.extend(verify_policy_model())
    if failures:
        for failure in failures:
            print(f"FAIL {failure}")
        print(f"VERDICT RED assertions={ASSERTIONS} failures={len(failures)}")
        return 1

    print(f"VERDICT GREEN structural_and_policy_assertions={ASSERTIONS}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
