#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path
from typing import Optional


REQUIRED_STATE_KEYS = {
    "job_id",
    "seed",
    "size",
    "radius_blocks",
    "tile_size_blocks",
    "phase",
    "chunks_done",
    "chunks_total",
    "tiles_done",
    "tiles_total",
    "percent",
    "started_at",
    "updated_at",
    "error",
}

REQUIRED_MANIFEST_KEYS = {
    "schema_version",
    "kind",
    "seed",
    "size",
    "radius_blocks",
    "tile_size_blocks",
    "branch",
    "commit",
    "color_model",
    "origin_min_x",
    "origin_min_z",
    "tiles_root",
    "tiles",
    "status",
}

REQUIRED_TILE_KEYS = {
    "z",
    "tile_x",
    "tile_z",
    "path",
    "min_x",
    "max_x",
    "min_z",
    "max_z",
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a Latitude P0 world-map run folder.")
    parser.add_argument("job_dir", type=Path)
    parser.add_argument("--expected-tiles", type=int, required=True)
    parser.add_argument(
        "--expected-phase",
        choices=("complete", "running", "paused", "canceled", "failed"),
        default="complete",
    )
    parser.add_argument("--expected-status")
    parser.add_argument("--expected-total-tiles", type=int)
    parser.add_argument("--expect-error-contains")
    parser.add_argument("--write-tile-snapshot", type=Path)
    parser.add_argument("--expect-preserved-from", type=Path)
    args = parser.parse_args()

    try:
        expected_status = args.expected_status if args.expected_status is not None else args.expected_phase
        manifest = validate(
            args.job_dir,
            args.expected_tiles,
            expected_phase=args.expected_phase,
            expected_status=expected_status,
            expected_total_tiles=args.expected_total_tiles,
            expect_error_contains=args.expect_error_contains,
        )
        if args.expect_preserved_from is not None:
            verify_preserved_tiles(args.job_dir, args.expect_preserved_from)
        if args.write_tile_snapshot is not None:
            if manifest is None:
                raise ValueError("--write-tile-snapshot requires a manifest")
            write_tile_snapshot(args.job_dir, manifest, args.write_tile_snapshot)
    except Exception as exc:
        print(f"world-map run invalid: {exc}", file=sys.stderr)
        return 1

    print(f"world-map run valid: {args.job_dir}")
    return 0


def validate(
    job_dir: Path,
    expected_tiles: int,
    *,
    expected_phase: str,
    expected_status: str,
    expected_total_tiles: Optional[int],
    expect_error_contains: Optional[str],
) -> Optional[dict]:
    if expected_tiles < 0:
        raise ValueError("--expected-tiles must be non-negative")
    if expected_total_tiles is None:
        expected_total_tiles = expected_tiles
    if expected_total_tiles < expected_tiles:
        raise ValueError("--expected-total-tiles must be >= --expected-tiles")

    state_path = job_dir / "job_state.json"
    manifest_path = job_dir / "world_map_manifest.json"
    if not state_path.exists():
        raise FileNotFoundError(state_path)

    state = read_json_object(state_path)
    missing_state = REQUIRED_STATE_KEYS.difference(state)
    if missing_state:
        raise ValueError(f"job_state.json missing keys: {sorted(missing_state)}")

    if state["phase"] != expected_phase:
        raise ValueError(f"unexpected job phase: {state['phase']!r} != {expected_phase!r}")
    if int(state["tiles_done"]) != expected_tiles:
        raise ValueError(f"state tiles_done mismatch: {state['tiles_done']} != {expected_tiles}")
    if int(state["tiles_total"]) != expected_total_tiles:
        raise ValueError(f"state tiles_total mismatch: {state['tiles_total']} != {expected_total_tiles}")
    if expect_error_contains is not None and expect_error_contains not in str(state.get("error", "")):
        raise ValueError(f"state error does not contain {expect_error_contains!r}: {state.get('error')!r}")

    if not manifest_path.exists():
        if expected_phase == "failed":
            return None
        raise FileNotFoundError(manifest_path)

    manifest = read_json_object(manifest_path)
    missing_manifest = REQUIRED_MANIFEST_KEYS.difference(manifest)
    if missing_manifest:
        raise ValueError(f"world_map_manifest.json missing keys: {sorted(missing_manifest)}")
    if manifest["kind"] != "world-map":
        raise ValueError(f"unexpected manifest kind: {manifest['kind']!r}")
    if manifest["tiles_root"] != "tiles":
        raise ValueError(f"unexpected tiles_root: {manifest['tiles_root']!r}")
    if manifest["status"] != expected_status:
        raise ValueError(f"unexpected manifest status: {manifest['status']!r} != {expected_status!r}")
    if int(state["tiles_done"]) > int(state["tiles_total"]):
        raise ValueError(f"state tiles_done exceeds tiles_total: {state['tiles_done']} > {state['tiles_total']}")

    tiles = manifest["tiles"]
    if not isinstance(tiles, list):
        raise ValueError("manifest tiles must be a list")
    if len(tiles) != expected_tiles:
        raise ValueError(f"expected {expected_tiles} tiles, found {len(tiles)}")

    radius = int(manifest["radius_blocks"])
    tile_size = int(manifest["tile_size_blocks"])
    origin_min_x = int(manifest["origin_min_x"])
    origin_min_z = int(manifest["origin_min_z"])
    world_width = (radius * 2) + 1
    tiles_per_axis = max(1, math.ceil(world_width / tile_size))
    for index, tile in enumerate(tiles):
        if not isinstance(tile, dict):
            raise ValueError(f"tile {index} is not an object")
        missing_tile = REQUIRED_TILE_KEYS.difference(tile)
        if missing_tile:
            raise ValueError(f"tile {index} missing keys: {sorted(missing_tile)}")

        tile_x = int(tile["tile_x"])
        tile_z = int(tile["tile_z"])
        z = int(tile["z"])
        if z != 0:
            raise ValueError(f"P0 tile {index} has nonzero z level: {z}")
        expected_tile_x = index % tiles_per_axis
        expected_tile_z = index // tiles_per_axis
        if (tile_x, tile_z) != (expected_tile_x, expected_tile_z):
            raise ValueError(
                f"tile {index} order mismatch: {(tile_x, tile_z)} != {(expected_tile_x, expected_tile_z)}"
            )
        expected_path = f"tiles/z{z}/x_{tile_x}_z_{tile_z}.png"
        if tile["path"] != expected_path:
            raise ValueError(f"tile {index} path {tile['path']!r} != {expected_path!r}")
        image_path = job_dir / expected_path
        if not image_path.exists():
            raise FileNotFoundError(image_path)
        expected_min_x = origin_min_x + (tile_x * tile_size)
        expected_min_z = origin_min_z + (tile_z * tile_size)
        expected_max_x = min(radius, expected_min_x + tile_size - 1)
        expected_max_z = min(radius, expected_min_z + tile_size - 1)
        actual_bounds = (
            int(tile["min_x"]),
            int(tile["max_x"]),
            int(tile["min_z"]),
            int(tile["max_z"]),
        )
        expected_bounds = (expected_min_x, expected_max_x, expected_min_z, expected_max_z)
        if actual_bounds != expected_bounds:
            raise ValueError(f"tile {index} bounds {actual_bounds} != {expected_bounds}")
        if int(tile["min_x"]) > int(tile["max_x"]) or int(tile["min_z"]) > int(tile["max_z"]):
            raise ValueError(f"tile {index} has invalid bounds")

    pngs = sorted((job_dir / "tiles" / "z0").glob("x_*_z_*.png"))
    if len(pngs) != expected_tiles:
        raise ValueError(f"expected {expected_tiles} z0 PNGs, found {len(pngs)}")
    return manifest


def read_json_object(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def write_tile_snapshot(job_dir: Path, manifest: dict, snapshot_path: Path) -> None:
    snapshot = {
        "job_dir": str(job_dir),
        "tiles": [snapshot_tile(job_dir, tile["path"]) for tile in manifest["tiles"]],
    }
    snapshot_path.parent.mkdir(parents=True, exist_ok=True)
    snapshot_path.write_text(json.dumps(snapshot, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def verify_preserved_tiles(job_dir: Path, snapshot_path: Path) -> None:
    snapshot = read_json_object(snapshot_path)
    tiles = snapshot.get("tiles")
    if not isinstance(tiles, list):
        raise ValueError(f"{snapshot_path} missing tiles list")
    for index, tile in enumerate(tiles):
        if not isinstance(tile, dict):
            raise ValueError(f"snapshot tile {index} is not an object")
        path = tile.get("path")
        if not isinstance(path, str) or not path:
            raise ValueError(f"snapshot tile {index} missing path")
        current = snapshot_tile(job_dir, path)
        for key in ("size", "mtime_ns", "sha256"):
            if current[key] != tile.get(key):
                raise ValueError(
                    f"tile {path} was not preserved: {key} {current[key]!r} != {tile.get(key)!r}"
                )


def snapshot_tile(job_dir: Path, relative_path: str) -> dict:
    path = job_dir / relative_path
    if not path.exists():
        raise FileNotFoundError(path)
    stat = path.stat()
    return {
        "path": relative_path,
        "size": stat.st_size,
        "mtime_ns": stat.st_mtime_ns,
        "sha256": sha256(path),
    }


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
