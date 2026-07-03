#!/usr/bin/env python3
"""Latitude 2.0 Phase 1 Measurement Harness: macro-geography analyzer for an atlas run.

Makes the "current red" from docs/LATITUDE_2_0_OVERHAUL.md measurable and repeatable. Reads the
same exact-ID biome artifacts (biome_ids.png + biome_palette.json) that band_correctness_check.py
and overrep_rank.py already trust as biome truth, and computes:

  - Raw land/water/coast shares (fraction of all sampled cells).
  - Cosine-latitude-weighted land/water/coast shares (a flat equirectangular-style raster
    over-represents polar area; weighting each row by cos(latitude) approximates true
    on-a-sphere area so a wide polar ocean band doesn't look bigger than it "really" is).
  - Land components, ocean-basin components, river components, coast components (4-connected by
    default), with raw and area-weighted largest-component share.
  - Projection-edge (the map's east/west border -- this world does not wrap) biome-family
    composition, broken down by latitude band.
  - Bridge sensitivity: re-labels components at 8-connectivity and reports how much the landmass/
    ocean-basin count and largest share shift -- a proxy for how much a single diagonal-only
    "bridge" (a one-cell isthmus or river gap) is holding two components together or apart.
  - A size-aware note: which of Itty/Small/Regular/Large/Massive canonical world sizes this run's
    radius maps to, and whether the sampling step is coarse enough that component counts should be
    treated as provisional (re-run at step16 or better before trusting a component count).

Deliberately does NOT touch terrain height. "Biome water" here means biomes tagged ocean/river in
the exact-ID export; "terrain water" (actual height below sea level) requires --emitHeight and is
out of scope until a run captures it -- the report says so explicitly rather than silently
conflating the two, per this file's own Proof Rules in docs/LATITUDE_2_0_OVERHAUL.md.

No scipy dependency (not installed in this environment) -- connected components use a from-scratch
two-pass union-find over the exact-ID grid.

Usage:
  python3 geography_analyzer.py <atlas-run-dir> [--prefix step16_] [--edge-frac 0.02]
                                 [--json-out <path>] [--text-out <path>]

  <atlas-run-dir> contains <prefix>biome_ids.png, <prefix>biome_palette.json, <prefix>biomes.txt
  (auto-detects <prefix> the same way band_correctness_check.py does, if not given).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

BANDS = [
    ("tropical", 0.0, 23.5),
    ("subtropical", 23.5, 35.0),
    ("temperate", 35.0, 50.0),
    ("subpolar", 50.0, 66.5),
    ("polar", 66.5, 90.0),
]

# Canonical world-size radii, mirrored from tools/atlas/atlas_runner.py's SIZE_TO_RADIUS so the
# size-aware gate table stays in sync with the runner that actually produces these atlas runs.
SIZE_TO_RADIUS = {
    "itty": 3750,
    "small": 7500,
    "regular": 10000,
    "large": 15000,
    "massive": 20000,
}


def classify_biome(short_name: str) -> str:
    """Biome-water family classification. Land includes beach/shore/mangrove -- those are solid
    ground touching water, not water themselves; excluding them from "water" is deliberate here
    even though band_correctness_check.py's is_water() lumps them in for a different purpose
    (wrong-band contamination checks don't care about coastline shape, geography measurement does).
    """
    if short_name == "river" or short_name.endswith("_river"):
        return "river"
    if "ocean" in short_name:
        return "ocean"
    return "land"


@dataclass
class AtlasRun:
    seed: str
    radius: int
    step: int
    width: int
    height: int
    z_min: float
    z_max: float
    ids: np.ndarray  # (H, W) int, palette index per cell
    idx2id: dict
    source_commit: str = ""


def find_prefix(run_dir: str) -> str:
    if os.path.exists(os.path.join(run_dir, "biome_ids.png")):
        return ""
    for name in sorted(os.listdir(run_dir)):
        if name.endswith("biome_ids.png"):
            return name[: -len("biome_ids.png")]
    raise FileNotFoundError(f"No *biome_ids.png found under {run_dir}")


def load_run(run_dir: str, prefix: str | None = None) -> AtlasRun:
    if prefix is None:
        prefix = find_prefix(run_dir)

    def p(name: str) -> str:
        return os.path.join(run_dir, f"{prefix}{name}")

    palette = json.load(open(p("biome_palette.json")))["biomes"]
    idx2id = {b["index"]: b["biome_id"] for b in palette}

    txt = open(p("biomes.txt")).read()
    radius = int(re.search(r"radiusBlocks=(\d+)", txt).group(1))
    step = int(re.search(r"stepBlocks=(\d+)", txt).group(1))
    seed = re.search(r"seed=(-?\d+)", txt).group(1)
    bounds = re.search(r"blockBounds=x\[(-?\d+)\.\.(-?\d+)\],z\[(-?\d+)\.\.(-?\d+)\]", txt)
    z_min, z_max = float(bounds.group(3)), float(bounds.group(4))

    ids = np.array(Image.open(p("biome_ids.png")).convert("RGB"))[:, :, 0].astype(np.int32)
    height, width = ids.shape

    return AtlasRun(
        seed=seed, radius=radius, step=step, width=width, height=height,
        z_min=z_min, z_max=z_max, ids=ids, idx2id=idx2id,
    )


def family_grid(run: AtlasRun) -> np.ndarray:
    """Returns an (H, W) array of dtype '<U5' with values 'land'/'ocean'/'river'."""
    max_index = max(run.idx2id.keys()) if run.idx2id else -1
    lut = np.empty(max_index + 1, dtype="<U5")
    for i in range(max_index + 1):
        biome_id = run.idx2id.get(i)
        short = biome_id.split(":", 1)[-1] if biome_id else "unknown"
        lut[i] = classify_biome(short)
    clipped = np.clip(run.ids, 0, max_index)
    return lut[clipped]


def row_latitudes_deg(run: AtlasRun) -> np.ndarray:
    if run.height <= 1:
        return np.zeros(run.height)
    zs = run.z_min + np.arange(run.height) * ((run.z_max - run.z_min) / (run.height - 1))
    lat = np.abs(zs) / run.radius * 90.0
    return np.clip(lat, 0.0, 90.0)


def row_weights(run: AtlasRun) -> np.ndarray:
    """cos(latitude) per row -- a flat raster's row-area-on-a-sphere proxy."""
    lat_deg = row_latitudes_deg(run)
    return np.cos(np.radians(lat_deg))


def band_of_row(lat_deg: np.ndarray) -> np.ndarray:
    band_idx = np.full(lat_deg.shape, -1, dtype=np.int32)
    for i, (_name, lo, hi) in enumerate(BANDS):
        band_idx[(lat_deg >= lo) & (lat_deg < hi if i < len(BANDS) - 1 else lat_deg <= hi)] = i
    return band_idx


def coast_mask(land: np.ndarray, water: np.ndarray) -> np.ndarray:
    adj = np.zeros_like(water)
    adj[1:, :] |= water[:-1, :]
    adj[:-1, :] |= water[1:, :]
    adj[:, 1:] |= water[:, :-1]
    adj[:, :-1] |= water[:, 1:]
    return land & adj


def connected_components(mask: np.ndarray, connectivity: int = 4) -> np.ndarray:
    """Two-pass union-find connected-component labeling. No scipy dependency.

    Returns an (H, W) int64 array: -1 where mask is False, otherwise a component id (not
    contiguous, but stable and comparable within one call).
    """
    h, w = mask.shape
    n = h * w
    parent = list(range(n))

    def find(x: int) -> int:
        root = x
        while parent[root] != root:
            root = parent[root]
        while parent[x] != root:
            parent[x], x = root, parent[x]
        return root

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            if ra < rb:
                parent[rb] = ra
            else:
                parent[ra] = rb

    neighbors = [(-1, 0), (0, -1)]
    if connectivity == 8:
        neighbors += [(-1, -1), (-1, 1)]

    m = mask.tolist()  # nested python bools: much faster to index in a tight loop than numpy
    for r in range(h):
        row = m[r]
        for c in range(w):
            if not row[c]:
                continue
            cur = r * w + c
            for dr, dc in neighbors:
                nr, nc = r + dr, c + dc
                if 0 <= nr < h and 0 <= nc < w and m[nr][nc]:
                    union(cur, nr * w + nc)

    labels = np.full(n, -1, dtype=np.int64)
    for r in range(h):
        row = m[r]
        base = r * w
        for c in range(w):
            if row[c]:
                i = base + c
                labels[i] = find(i)
    return labels.reshape(h, w)


def component_shares(labels: np.ndarray, weights_2d: np.ndarray) -> list[dict]:
    """Per-component raw cell count and area-weighted sum, sorted largest-raw-first."""
    flat_labels = labels.reshape(-1)
    present = flat_labels >= 0
    if not present.any():
        return []
    ids = flat_labels[present]
    raw_ones = np.ones_like(ids, dtype=np.int64)
    w_flat = weights_2d.reshape(-1)[present]
    uniq = np.unique(ids)
    raw_counts = {}
    weighted_sums = {}
    # bincount needs dense small ids; ids here are arbitrary linear indices, so use a dict via
    # np.add.at against a compact remap instead of relying on bincount over huge sparse ids.
    remap = {v: i for i, v in enumerate(uniq)}
    compact = np.array([remap[v] for v in ids], dtype=np.int64)
    raw_bins = np.bincount(compact, minlength=len(uniq))
    weighted_bins = np.bincount(compact, weights=w_flat, minlength=len(uniq))
    order = np.argsort(-raw_bins)
    return [
        {"raw_count": int(raw_bins[i]), "weighted_sum": float(weighted_bins[i])}
        for i in order
    ]


def projection_edge_composition(run: AtlasRun, families: np.ndarray, band_idx: np.ndarray,
                                 edge_frac: float) -> dict:
    edge_cols = max(1, int(round(run.width * edge_frac)))
    result = {}
    for side, cols in (("west", families[:, :edge_cols]), ("east", families[:, -edge_cols:])):
        by_band = {}
        for i, (name, _lo, _hi) in enumerate(BANDS):
            rows = band_idx == i
            if not rows.any():
                by_band[name] = {"land": 0.0, "ocean": 0.0, "river": 0.0, "samples": 0}
                continue
            region = cols[rows]
            total = region.size
            by_band[name] = {
                "land": float(np.mean(region == "land")) if total else 0.0,
                "ocean": float(np.mean(region == "ocean")) if total else 0.0,
                "river": float(np.mean(region == "river")) if total else 0.0,
                "samples": int(total),
            }
        result[side] = by_band
    result["edge_cols"] = edge_cols
    return result


def size_gate(radius: int) -> dict:
    closest = min(SIZE_TO_RADIUS.items(), key=lambda kv: abs(kv[1] - radius))
    return {"closest_canonical_size": closest[0], "closest_canonical_radius": closest[1],
            "exact_match": closest[1] == radius}


def analyze(run: AtlasRun, edge_frac: float = 0.02) -> dict:
    families = family_grid(run)
    land = families == "land"
    ocean = families == "ocean"
    river = families == "river"
    water = ocean | river
    coast = coast_mask(land, water)

    weights_row = row_weights(run)
    weights_2d = np.broadcast_to(weights_row[:, None], families.shape)
    total_weight = float(weights_2d.sum())
    lat_deg = row_latitudes_deg(run)
    band_idx = band_of_row(lat_deg)

    def raw_share(mask: np.ndarray) -> float:
        return float(mask.sum()) / mask.size

    def weighted_share(mask: np.ndarray) -> float:
        return float((mask * weights_2d).sum()) / total_weight if total_weight else 0.0

    land_labels_4 = connected_components(land, connectivity=4)
    ocean_labels_4 = connected_components(ocean, connectivity=4)
    river_labels_4 = connected_components(river, connectivity=4)
    coast_labels_4 = connected_components(coast, connectivity=4)

    land_comp = component_shares(land_labels_4, weights_2d)
    ocean_comp = component_shares(ocean_labels_4, weights_2d)
    river_comp = component_shares(river_labels_4, weights_2d)
    coast_comp = component_shares(coast_labels_4, weights_2d)

    def largest_share(components: list[dict], mask: np.ndarray) -> dict:
        if not components:
            return {"raw": 0.0, "weighted": 0.0}
        total_raw = int(mask.sum())
        total_weighted = float((mask * weights_2d).sum())
        top = components[0]
        return {
            "raw": (top["raw_count"] / total_raw) if total_raw else 0.0,
            "weighted": (top["weighted_sum"] / total_weighted) if total_weighted else 0.0,
        }

    # Bridge sensitivity: land/ocean at 8-connectivity vs 4-connectivity.
    land_labels_8 = connected_components(land, connectivity=8)
    ocean_labels_8 = connected_components(ocean, connectivity=8)
    land_comp_8 = component_shares(land_labels_8, weights_2d)
    ocean_comp_8 = component_shares(ocean_labels_8, weights_2d)

    return {
        "run": {
            "seed": run.seed, "radiusBlocks": run.radius, "stepBlocks": run.step,
            "width": run.width, "height": run.height,
        },
        "size_gate": size_gate(run.radius),
        "shares": {
            "raw": {"land": raw_share(land), "water": raw_share(water),
                    "ocean": raw_share(ocean), "river": raw_share(river),
                    "coast": raw_share(coast)},
            "area_weighted": {"land": weighted_share(land), "water": weighted_share(water),
                               "ocean": weighted_share(ocean), "river": weighted_share(river),
                               "coast": weighted_share(coast)},
        },
        "components": {
            "land": {"count": len(land_comp), "largest_share": largest_share(land_comp, land)},
            "ocean_basin": {"count": len(ocean_comp), "largest_share": largest_share(ocean_comp, ocean)},
            "river": {"count": len(river_comp), "largest_share": largest_share(river_comp, river)},
            "coast": {"count": len(coast_comp), "largest_share": largest_share(coast_comp, coast)},
        },
        "bridge_sensitivity": {
            "land": {
                "count_4conn": len(land_comp), "count_8conn": len(land_comp_8),
                "largest_share_4conn": largest_share(land_comp, land)["raw"],
                "largest_share_8conn": largest_share(land_comp_8, land)["raw"],
            },
            "ocean_basin": {
                "count_4conn": len(ocean_comp), "count_8conn": len(ocean_comp_8),
                "largest_share_4conn": largest_share(ocean_comp, ocean)["raw"],
                "largest_share_8conn": largest_share(ocean_comp_8, ocean)["raw"],
            },
        },
        "projection_edge": projection_edge_composition(run, families, band_idx, edge_frac),
    }


def format_text_report(report: dict) -> str:
    lines = []
    r = report["run"]
    lines.append(f"=== Latitude 2.0 geography analyzer ===  seed={r['seed']} radius={r['radiusBlocks']} "
                 f"step={r['stepBlocks']} image={r['width']}x{r['height']}")
    sg = report["size_gate"]
    lines.append(f"size gate: closest canonical size = {sg['closest_canonical_size']} "
                 f"(radius {sg['closest_canonical_radius']}){'  [exact match]' if sg['exact_match'] else '  [non-canonical radius]'}")
    if r["stepBlocks"] > 16:
        lines.append(f"  NOTE: step={r['stepBlocks']} is coarser than step16 -- component counts are "
                      f"provisional per this analyzer's proof rules; re-run at step16 or better before "
                      f"treating them as final evidence.")
    lines.append("")
    lines.append("--- shares (raw vs cosine-latitude-area-weighted) ---")
    for key in ("land", "water", "ocean", "river", "coast"):
        raw = report["shares"]["raw"][key]
        w = report["shares"]["area_weighted"][key]
        lines.append(f"  {key:6s}  raw={raw*100:6.2f}%   area-weighted={w*100:6.2f}%")
    lines.append("")
    lines.append("--- components (4-connected) ---")
    for key in ("land", "ocean_basin", "river", "coast"):
        c = report["components"][key]
        lines.append(f"  {key:12s} count={c['count']:6d}  largest raw share={c['largest_share']['raw']*100:6.2f}%  "
                      f"largest area-weighted share={c['largest_share']['weighted']*100:6.2f}%")
    lines.append("")
    lines.append("--- bridge sensitivity (4-conn vs 8-conn) ---")
    for key in ("land", "ocean_basin"):
        b = report["bridge_sensitivity"][key]
        lines.append(f"  {key:12s} count 4conn={b['count_4conn']:6d}  8conn={b['count_8conn']:6d}  "
                      f"largest share 4conn={b['largest_share_4conn']*100:6.2f}%  8conn={b['largest_share_8conn']*100:6.2f}%")
    lines.append("")
    lines.append("--- projection edge composition (west / east) by band ---")
    edge = report["projection_edge"]
    lines.append(f"  edge width = {edge['edge_cols']} columns")
    for name, _lo, _hi in BANDS:
        w = edge["west"][name]
        e = edge["east"][name]
        lines.append(f"  {name:12s}  west: land={w['land']*100:5.1f}% ocean={w['ocean']*100:5.1f}% river={w['river']*100:5.1f}%"
                      f"   |   east: land={e['land']*100:5.1f}% ocean={e['ocean']*100:5.1f}% river={e['river']*100:5.1f}%")
    lines.append("")
    lines.append("NOTE: 'water' here is biome water (ocean/river tags from the exact-ID export), not "
                 "terrain water. Terrain-water truth requires an --emitHeight run, out of scope for this report.")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("run_dir")
    parser.add_argument("--prefix", default=None)
    parser.add_argument("--edge-frac", type=float, default=0.02)
    parser.add_argument("--json-out", default=None)
    parser.add_argument("--text-out", default=None)
    args = parser.parse_args(argv)

    run = load_run(args.run_dir, args.prefix)
    report = analyze(run, edge_frac=args.edge_frac)
    text = format_text_report(report)
    print(text)

    if args.json_out:
        with open(args.json_out, "w") as f:
            json.dump(report, f, indent=2)
    if args.text_out:
        with open(args.text_out, "w") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
