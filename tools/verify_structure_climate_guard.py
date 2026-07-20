#!/usr/bin/env python3
"""Focused model and structural proof for the village climate guard."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BIOMES_SOURCE = ROOT / "src/main/java/com/example/globe/world/LatitudeBiomes.java"
MIXIN_SOURCE = (
    ROOT
    / "src/main/java/com/example/globe/mixin/StructureBiomeMatchGuardMixin.java"
)
MIXIN_CONFIG = ROOT / "src/main/resources/globe.mixins.json"


def modeled_mismatch(structure_path: str | None, band: str | None) -> bool:
    if structure_path is None or band is None:
        return False
    path = structure_path.lower()
    if "village" not in path:
        return False
    warm_declared = any(
        token in path
        for token in ("desert", "savanna", "badlands", "mesa", "jungle")
    )
    cold_declared = any(
        token in path for token in ("snowy", "frozen", "glacier", "taiga")
    )
    cold_band = band in {"TEMPERATE", "SUBPOLAR", "POLAR"}
    warm_band = band in {"TROPICAL", "SUBTROPICAL"}
    return (warm_declared and cold_band) or (cold_declared and warm_band)


def modeled_cancel(
    *,
    canonical_latitude_generator: bool,
    latitude_geometry: bool,
    structure_path: str | None,
    band: str | None,
) -> bool:
    return (
        canonical_latitude_generator
        and latitude_geometry
        and modeled_mismatch(structure_path, band)
    )


def main() -> int:
    failures: list[str] = []
    biomes = BIOMES_SOURCE.read_text(encoding="utf-8")
    mixins = MIXIN_CONFIG.read_text(encoding="utf-8")
    guard = (
        MIXIN_SOURCE.read_text(encoding="utf-8")
        if MIXIN_SOURCE.exists()
        else ""
    )

    helper_fragments = (
        "public static boolean villageClimateVsBandMismatch(",
        "if (structurePath == null || band == null)",
        "if (!p.contains(\"village\"))",
        'p.contains("desert")',
        'p.contains("savanna")',
        'p.contains("badlands")',
        'p.contains("mesa")',
        'p.contains("jungle")',
        'p.contains("snowy")',
        'p.contains("frozen")',
        'p.contains("glacier")',
        'p.contains("taiga")',
        "band == LatitudeBands.Band.TROPICAL",
        "band == LatitudeBands.Band.SUBTROPICAL",
        "band == LatitudeBands.Band.TEMPERATE",
        "band == LatitudeBands.Band.SUBPOLAR",
        "band == LatitudeBands.Band.POLAR",
    )
    for fragment in helper_fragments:
        if fragment not in biomes:
            failures.append(f"helper missing: {fragment}")

    guard_fragments = (
        "@Mixin(StructureStart.class)",
        '@Inject(method = "placeInChunk(',
        'at = @At("HEAD"), cancellable = true',
        "world.registryAccess().lookupOrThrow(Registries.STRUCTURE)",
        "registry.getKey(this.getStructure())",
        "chunkGenerator instanceof NoiseBasedChunkGenerator noise",
        "GlobeMod.shouldApplyLatitudeWorldgen(noise)",
        "LatitudeMath.halfSize(border)",
        "halfSize < 1_000_000.0",
        "this.getChunkPos().getMiddleBlockZ()",
        "LatitudeMath.degreesFromZ(border, blockZ)",
        "LatitudeBands.fromAbsoluteLatitudeDeg(absDeg)",
        "LatitudeBiomes.villageClimateVsBandMismatch(structureId.getPath(), band)",
        "ci.cancel();",
        "catch (Throwable ignored)",
    )
    for fragment in guard_fragments:
        if fragment not in guard:
            failures.append(f"mixin missing: {fragment}")

    exact_descriptor = (
        "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;"
        "Lnet/minecraft/world/level/StructureManager;"
        "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
        "Lnet/minecraft/util/RandomSource;"
        "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"
        "Lnet/minecraft/world/level/ChunkPos;)V"
    )
    if exact_descriptor not in guard:
        failures.append("mixin does not target the exact 26.2 placeInChunk descriptor")
    if "world.getBiome(" in guard or "chunkBox.minY()" in guard:
        failures.append("unreliable placement-time biome sampling was reintroduced")
    if "isBlockBeyondPolarVillageLimit" in guard:
        failures.append("climate guard absorbed the separate >80-degree policy")
    generator_gate_index = guard.find(
        "chunkGenerator instanceof NoiseBasedChunkGenerator noise"
    )
    border_gate_index = guard.find("LatitudeMath.halfSize(border)")
    if generator_gate_index < 0 or border_gate_index < 0:
        failures.append("canonical generator and geometry gate order cannot be proved")
    elif generator_gate_index >= border_gate_index:
        failures.append("border size is still consulted before canonical generator identity")
    if mixins.count('"StructureBiomeMatchGuardMixin"') != 1:
        failures.append("StructureBiomeMatchGuardMixin must be registered exactly once")

    cases = {
        "desert village in BOP tundra / SUBPOLAR rejects": modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_desert",
            band="SUBPOLAR",
        ),
        "desert village in SUBTROPICAL allows": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_desert",
            band="SUBTROPICAL",
        ),
        "snowy village in tundra / SUBPOLAR allows": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_snowy",
            band="SUBPOLAR",
        ),
        "taiga village in tundra / SUBPOLAR allows": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_taiga",
            band="SUBPOLAR",
        ),
        "plains village is neutral": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_plains",
            band="SUBPOLAR",
        ),
        "neutral non-village structure allows": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="desert_pyramid",
            band="SUBPOLAR",
        ),
        "null structure fails open": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path=None,
            band="SUBPOLAR",
        ),
        "unknown band fails open": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_desert",
            band=None,
        ),
        "small border without canonical Latitude generator allows": not modeled_cancel(
            canonical_latitude_generator=False,
            latitude_geometry=True,
            structure_path="village_desert",
            band="SUBPOLAR",
        ),
        "canonical generator without valid Latitude geometry allows": not modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=False,
            structure_path="village_desert",
            band="SUBPOLAR",
        ),
        "cold village in TROPICAL rejects": modeled_cancel(
            canonical_latitude_generator=True,
            latitude_geometry=True,
            structure_path="village_snowy",
            band="TROPICAL",
        ),
    }
    failures.extend(name for name, passed in cases.items() if not passed)

    if failures:
        print("STRUCTURE_CLIMATE_GUARD: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("STRUCTURE_CLIMATE_GUARD: PASS")
    print(" village-only warm/cold band matrix passed")
    print(" null, neutral, non-village, and non-Latitude paths fail open")
    print(" canonical generator identity precedes border geometry sanity")
    print(" exact 26.2 placement descriptor and single registration confirmed")
    print(" no placement-time biome/minY sampling or >80-policy coupling")
    return 0


if __name__ == "__main__":
    sys.exit(main())
