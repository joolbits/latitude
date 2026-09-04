#!/usr/bin/env python3
"""Create a sanitized donor-versus-candidate Latitude atlas summary."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
from typing import Any

from PIL import Image


SCHEMA = "latitude-recorder-atlas-summary-v1"
REQUIRED_FILES = (
    "biome_ids.png",
    "biome_palette.json",
    "chosen_bands.png",
    "land_bands.png",
    "legend.json",
    "seam_band_legend.txt",
    "world_biome_inventory.json",
)
SAFE_LABEL = re.compile(r"[a-z0-9][a-z0-9-]{0,63}")


class AtlasSummaryError(ValueError):
    pass


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise AtlasSummaryError(f"unreadable atlas JSON: {path.name}") from error


def require_bundle(path: Path) -> None:
    if not path.is_dir():
        raise AtlasSummaryError("atlas bundle is not a directory")
    missing = [name for name in REQUIRED_FILES if not (path / name).is_file()]
    if missing:
        raise AtlasSummaryError("atlas bundle is missing required files: " + ", ".join(missing))


def palette(path: Path) -> dict[int, str]:
    rows = load_json(path / "biome_palette.json").get("biomes", [])
    result: dict[int, str] = {}
    for row in rows:
        index = row.get("index")
        biome_id = row.get("biome_id")
        if not isinstance(index, int) or not isinstance(biome_id, str):
            raise AtlasSummaryError("biome palette contains an invalid row")
        if index in result:
            raise AtlasSummaryError(f"biome palette repeats index {index}")
        result[index] = biome_id
    if not result:
        raise AtlasSummaryError("biome palette is empty")
    return result


def biome_cells(path: Path) -> tuple[tuple[int, int], list[str]]:
    colors = palette(path)
    with Image.open(path / "biome_ids.png") as image:
        rgb = image.convert("RGB")
        size = rgb.size
        cells: list[str] = []
        for red, _green, _blue in rgb.getdata():
            if red not in colors:
                raise AtlasSummaryError(f"biome image uses missing palette index {red}")
            cells.append(colors[red])
    return size, cells


def band_colors(path: Path) -> dict[tuple[int, int, int], str]:
    result: dict[tuple[int, int, int], str] = {}
    for raw in (path / "seam_band_legend.txt").read_text(encoding="utf-8").splitlines():
        if "=" not in raw:
            continue
        band, encoded = raw.split("=", 1)
        if not re.fullmatch(r"#[0-9A-Fa-f]{6}", encoded.strip()):
            raise AtlasSummaryError("band legend contains an invalid color")
        value = int(encoded.strip()[1:], 16)
        result[((value >> 16) & 255, (value >> 8) & 255, value & 255)] = band.strip()
    if not result:
        raise AtlasSummaryError("band legend is empty")
    return result


def placement_bands(path: Path) -> tuple[tuple[int, int], list[str]]:
    colors = band_colors(path)
    with Image.open(path / "land_bands.png") as image:
        rgb = image.convert("RGB")
        size = rgb.size
        bands: list[str] = []
        for color in rgb.getdata():
            if color not in colors:
                raise AtlasSummaryError("land-band image uses an unknown color")
            bands.append(colors[color])
    return size, bands


def inventory(path: Path) -> set[str]:
    rows = load_json(path / "world_biome_inventory.json").get("biomes", [])
    return {
        row["biome_id"]
        for row in rows
        if isinstance(row, dict) and isinstance(row.get("biome_id"), str)
    }


def allowed_bands(path: Path) -> dict[str, set[str]]:
    values = load_json(path / "legend.json").get("biomeBands", {})
    if not isinstance(values, dict):
        raise AtlasSummaryError("legend biomeBands is not an object")
    return {
        biome_id: {str(band) for band in bands}
        for biome_id, bands in values.items()
        if isinstance(biome_id, str) and isinstance(bands, list)
    }


def recipe_identity(path: Path) -> dict[str, Any]:
    legend = load_json(path / "legend.json")
    return {
        "seed": legend.get("seed"),
        "radiusBlocks": legend.get("radiusBlocks"),
        "stepBlocks": legend.get("stepBlocks"),
        "y": legend.get("y"),
        "layers": legend.get("layers"),
        "maskTargets": legend.get("maskTargets"),
        "overlays": legend.get("overlays"),
    }


def fingerprint(value: Any) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def namespace_counts(biomes: set[str]) -> dict[str, int]:
    counts = Counter(biome.split(":", 1)[0] if ":" in biome else "unknown" for biome in biomes)
    return dict(sorted(counts.items()))


def wrong_band_violations(path: Path, cells: list[str]) -> dict[int, tuple[str, str]]:
    band_size, bands = placement_bands(path)
    biome_size, _ = biome_cells(path)
    if band_size != biome_size or len(bands) != len(cells):
        raise AtlasSummaryError("biome and land-band images have different dimensions")
    allowed = allowed_bands(path)
    violations: dict[int, tuple[str, str]] = {}
    for index, (biome_id, band) in enumerate(zip(cells, bands)):
        accepted = allowed.get(biome_id, set())
        if accepted and band not in accepted:
            violations[index] = (biome_id, band)
    return violations


def wrong_band_comparison(
        donor: Path,
        donor_cells: list[str],
        candidate: Path,
        candidate_cells: list[str],
) -> dict[str, Any]:
    donor_violations = wrong_band_violations(donor, donor_cells)
    candidate_violations = wrong_band_violations(candidate, candidate_cells)
    new_violations = Counter(
        biome_id
        for index, (biome_id, band) in candidate_violations.items()
        if donor_violations.get(index) != (biome_id, band)
    )
    resolved_violations = Counter(
        biome_id
        for index, (biome_id, band) in donor_violations.items()
        if candidate_violations.get(index) != (biome_id, band)
    )
    candidate_biomes = Counter(biome_id for biome_id, _band in candidate_violations.values())
    return {
        "donor_count": len(donor_violations),
        "candidate_count": len(candidate_violations),
        "new_count": sum(new_violations.values()),
        "resolved_count": sum(resolved_violations.values()),
        "new_biomes": dict(sorted(new_violations.items())),
        "candidate_biomes": dict(sorted(candidate_biomes.items())),
    }


def compare(donor: Path, candidate: Path, donor_label: str, candidate_label: str) -> dict[str, Any]:
    require_bundle(donor)
    require_bundle(candidate)
    if not SAFE_LABEL.fullmatch(donor_label) or not SAFE_LABEL.fullmatch(candidate_label):
        raise AtlasSummaryError("labels must be lowercase safe tokens")

    donor_recipe = recipe_identity(donor)
    candidate_recipe = recipe_identity(candidate)
    if donor_recipe != candidate_recipe:
        raise AtlasSummaryError("donor and candidate atlas recipes do not match")
    donor_size, donor_cells = biome_cells(donor)
    candidate_size, candidate_cells = biome_cells(candidate)
    if donor_size != candidate_size:
        raise AtlasSummaryError("donor and candidate biome images have different dimensions")

    donor_inventory = inventory(donor)
    candidate_inventory = inventory(candidate)
    donor_counts = Counter(donor_cells)
    candidate_counts = Counter(candidate_cells)
    cell_count = len(candidate_cells)
    all_biomes = sorted(set(donor_counts) | set(candidate_counts))
    share_changes = []
    for biome_id in all_biomes:
        donor_share = donor_counts[biome_id] * 100.0 / cell_count
        candidate_share = candidate_counts[biome_id] * 100.0 / cell_count
        delta = candidate_share - donor_share
        if abs(delta) < 0.0000001:
            continue
        share_changes.append({
            "biome_id": biome_id,
            "donor_percent": round(donor_share, 4),
            "candidate_percent": round(candidate_share, 4),
            "delta_percentage_points": round(delta, 4),
        })
    share_changes.sort(key=lambda row: (-abs(row["delta_percentage_points"]), row["biome_id"]))

    donor_providers = namespace_counts(donor_inventory)
    candidate_providers = namespace_counts(candidate_inventory)
    lost_providers = sorted(
        provider for provider, count in donor_providers.items()
        if count > 0 and candidate_providers.get(provider, 0) == 0
    )
    non_minecraft_winner_changes = sum(
        1
        for before, after in zip(donor_cells, candidate_cells)
        if before.split(":", 1)[0] != "minecraft" and before != after
    )
    wrong_band = wrong_band_comparison(donor, donor_cells, candidate, candidate_cells)
    unclassified = {
        "donor": sorted(set(donor_cells) - allowed_bands(donor).keys()),
        "candidate": sorted(set(candidate_cells) - allowed_bands(candidate).keys()),
    }
    verdict = "fail" if wrong_band["new_count"] or lost_providers else "pass"
    if verdict == "pass" and any(unclassified.values()):
        verdict = "unknown"

    return {
        "schema": SCHEMA,
        "donor_label": donor_label,
        "candidate_label": candidate_label,
        "recipe_fingerprint": fingerprint(donor_recipe),
        "image": {"width": donor_size[0], "height": donor_size[1], "cells": cell_count},
        "new_arrivals": sorted(candidate_inventory - donor_inventory),
        "disappearances": sorted(donor_inventory - candidate_inventory),
        "wrong_band": wrong_band,
        "unclassified_biomes": unclassified,
        "provider_reachability": {
            "donor": donor_providers,
            "candidate": candidate_providers,
            "lost_providers": lost_providers,
        },
        "non_minecraft_winner_changes": non_minecraft_winner_changes,
        "share_changes": share_changes,
        "verdict": verdict,
    }


def friendly_text(summary: dict[str, Any]) -> str:
    arrivals = summary["new_arrivals"]
    disappearances = summary["disappearances"]
    provider = summary["provider_reachability"]
    wrong = summary["wrong_band"]
    unclassified = summary.get("unclassified_biomes", {"donor": [], "candidate": []})
    lines = [
        "Latitude Recorder Lite atlas comparison",
        f"Verdict: {summary['verdict'].upper()}",
        f"Recipe fingerprint: {summary['recipe_fingerprint']}",
        f"Compared cells: {summary['image']['cells']}",
        "New arrivals: " + (", ".join(arrivals) if arrivals else "none"),
        "Disappearances: " + (", ".join(disappearances) if disappearances else "none"),
        f"New wrong-band cells: {wrong['new_count']}",
        f"Candidate wrong-band total: {wrong['candidate_count']} "
        f"(donor baseline: {wrong['donor_count']})",
        "Lost providers: " + (", ".join(provider["lost_providers"])
                               if provider["lost_providers"] else "none"),
        f"Changed non-Minecraft winners: {summary['non_minecraft_winner_changes']}",
        "Unclassified donor biomes: " + (", ".join(unclassified["donor"]) or "none"),
        "Unclassified candidate biomes: " + (", ".join(unclassified["candidate"]) or "none"),
        "Largest biome-share changes:",
    ]
    for row in summary["share_changes"][:10]:
        lines.append(
            f"- {row['biome_id']}: {row['delta_percentage_points']:+.4f} percentage points "
            f"({row['donor_percent']:.4f}% -> {row['candidate_percent']:.4f}%)")
    if not summary["share_changes"]:
        lines.append("- none")
    return "\n".join(lines) + "\n"


def atomic_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two Latitude exact-ID atlas bundles")
    parser.add_argument("--donor", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--donor-label", default="donor")
    parser.add_argument("--candidate-label", default="candidate")
    parser.add_argument("--json-out", required=True, type=Path)
    parser.add_argument("--text-out", required=True, type=Path)
    args = parser.parse_args()

    try:
        summary = compare(
            args.donor.resolve(),
            args.candidate.resolve(),
            args.donor_label,
            args.candidate_label)
        atomic_write(args.json_out.resolve(), json.dumps(summary, indent=2, sort_keys=True) + "\n")
        atomic_write(args.text_out.resolve(), friendly_text(summary))
    except AtlasSummaryError as error:
        parser.error(str(error))
    print(f"LATITUDE_RECORDER_ATLAS_SUMMARY_COMPLETE verdict={summary['verdict']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
