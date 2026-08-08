#!/usr/bin/env python3
"""BEACH raw-observation controller and independent policy oracle.

Java is an append-only sensor.  It never emits queues, selections, counts,
aggregates, or verdicts.  This module validates the raw chain, recomputes every
predicate and timing budget, drives discovery requests, freezes only point
references, creates three fresh final worlds, and owns PASS/HOLD.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import re
import signal
import stat
import subprocess
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Optional, Protocol, Sequence


TOOL_VERSION = 5
SCHEMA = "latitude-beach-raw-v5"
RADIUS = 3750
STEP = 32
WIDTH = 235
SEEDS = tuple(range(1, 33))
RAW_Y = 96
RAW_QY = 24
FAST_Y = 64
PREFIX = 64
CELL_CAP = 256
CALLBACK_NS_CAP = 40_000_000
CALLBACK_HOLD_NS = 1_000_000_000
RING = 24
PRESET = "globe:globe_xsmall"
SETTINGS = "globe:overworld_regular"
DIMENSION = "minecraft:overworld"
MINECRAFT = "26.1.2"
LOADER = "0.18.4"
FABRIC_API = "0.145.4+26.1.2"
BEACH_IDS = frozenset(("minecraft:beach", "minecraft:stony_shore"))
KINDS = ("target", "low_beach", "rolling_foredune", "non_temperate_beach")
BIOME_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
REPO_ROOT = Path(__file__).resolve().parents[1]
TASK_ROOT = REPO_ROOT / "tmp/latitude-1.5-beach-terralith-acceptance-20260718/beach"
R2 = TASK_ROOT / "runtime-20260718-r2"
R3 = TASK_ROOT / "runtime-20260718-r3"
TOOLING = TASK_ROOT / "tooling"
RUNNER_REL = Path("src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java")
OVERLAY_REL = Path("src/main/java/com/example/globe/dev/BeachShortcutBatchProbe.java")
RUNNER_SHA = "afe339ef8903a444bf5f704b144319ba6acc5d33cecdd98317cfd5ee54013b7a"
RUNNER_ANCHOR = "        LocateBoundaryConfig locateBoundaryConfig = parseLocateBoundaryConfig();\n"
RUNNER_INSERT = (
    "        if (BeachShortcutBatchProbe.installIfEnabled(server)) {\n"
    "            return;\n"
    "        }\n\n"
)


class BeachError(RuntimeError):
    pass


class EvidenceError(BeachError):
    pass


class IdentityError(BeachError):
    pass


class SafetyError(BeachError):
    pass


class ProcessError(BeachError):
    pass


@dataclass(frozen=True)
class FrozenIdentity:
    key: str
    commit: str


IDENTITIES = (
    FrozenIdentity("before_fix", "0aacc55aebc1c74d5e71e73652052486ea5be024"),
    FrozenIdentity("exact_fix", "e6327c5715adc3acb295223d24963e74d682044c"),
    FrozenIdentity("current", "31d5d23a96407c871c4a91ac781854af79aec161"),
)
IDENTITY_BY_KEY = {item.key: item for item in IDENTITIES}


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tree_identity(root: Path) -> dict[str, Any]:
    root = root.absolute()
    if not root.is_dir() or root.is_symlink():
        raise SafetyError(f"tree root must be a real directory: {root}")
    digest = hashlib.sha256()
    files = directories = symlinks = 0
    for current, dirnames, filenames in os.walk(root, followlinks=False):
        dirnames.sort()
        filenames.sort()
        base = Path(current)
        for name in tuple(dirnames) + tuple(filenames):
            path = base / name
            rel = path.relative_to(root).as_posix()
            info = os.lstat(path)
            if stat.S_ISLNK(info.st_mode):
                kind, payload = "l", os.readlink(path).encode()
                symlinks += 1
            elif stat.S_ISDIR(info.st_mode):
                kind, payload = "d", b""
                directories += 1
            elif stat.S_ISREG(info.st_mode):
                kind, payload = "f", path.read_bytes()
                files += 1
            else:
                raise SafetyError(f"unsupported tree node: {path}")
            digest.update(f"{kind}\0{rel}\0{stat.S_IMODE(info.st_mode):o}\0".encode())
            digest.update(hashlib.sha256(payload).digest())
    return {
        "algorithm": "latitude-tree-v2",
        "sha256": digest.hexdigest(),
        "files": files,
        "directories": directories,
        "symlinks": symlinks,
    }


def task_path(path: Path, label: str) -> Path:
    root = TASK_ROOT.resolve(strict=True)
    absolute = path.absolute()
    try:
        relative = absolute.relative_to(root)
    except ValueError as exc:
        raise SafetyError(f"{label} escapes task root") from exc
    cursor = root
    for part in relative.parts:
        cursor /= part
        if cursor.exists() or cursor.is_symlink():
            if stat.S_ISLNK(os.lstat(cursor).st_mode):
                raise SafetyError(f"{label} contains symlink component: {cursor}")
    return absolute


def exclusive_json(path: Path, value: Any) -> None:
    task_path(path, "exclusive output")
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(canonical_json(value))
    except BaseException:
        path.unlink(missing_ok=True)
        raise


class RuntimeGuard:
    """Own canonical r3 and prove r2 unchanged in the outermost finally."""

    def __init__(
        self,
        runtime: Path = R3,
        *,
        snapshot: Callable[[Path], Mapping[str, Any]] = tree_identity,
    ) -> None:
        self.runtime = runtime.absolute()
        self.snapshot = snapshot
        self.before: Optional[Mapping[str, Any]] = None
        self.after: Optional[Mapping[str, Any]] = None

    def __enter__(self) -> "RuntimeGuard":
        canonical = R3.absolute()
        if self.runtime != canonical:
            raise SafetyError("runtime must equal canonical r3 exactly")
        task_path(self.runtime, "runtime-r3")
        if self.runtime.exists() or self.runtime.is_symlink():
            raise SafetyError("canonical r3 must be absent/new-only")
        if self.runtime.resolve(strict=False).is_relative_to(R2.resolve(strict=True)):
            raise SafetyError("r3 aliases or descends from r2")
        self.before = dict(self.snapshot(R2))
        self.runtime.mkdir(mode=0o755)
        return self

    def __exit__(self, exc_type: Any, exc: Optional[BaseException], traceback: Any) -> bool:
        r2_error: Optional[BaseException] = None
        try:
            self.after = dict(self.snapshot(R2))
            if self.before != self.after:
                r2_error = SafetyError("runtime-r2 changed during guarded operation")
        except BaseException as error:
            r2_error = SafetyError(f"runtime-r2 final audit failed: {error}")
        if r2_error is not None:
            if exc is not None:
                raise r2_error from exc
            raise r2_error
        return False

    @staticmethod
    def audit_action(
        action: Callable[[], Any],
        snapshot: Callable[[], Mapping[str, Any]],
    ) -> Any:
        """Pure harness for proving exceptional exits still run the final r2 audit."""
        before = dict(snapshot())
        caught: Optional[BaseException] = None
        result: Any = None
        try:
            result = action()
        except BaseException as error:
            caught = error
        finally:
            after = dict(snapshot())
            if before != after:
                raise SafetyError("r2 drift detected in outermost finally") from caught
        if caught is not None:
            raise caught
        return result


def coordinate(index: int) -> int:
    if not 0 <= index < WIDTH:
        raise ValueError(index)
    return -RADIUS + STEP * index


def coordinates() -> tuple[int, ...]:
    return tuple(coordinate(index) for index in range(WIDTH))


def possible_crisp_rows() -> tuple[int, ...]:
    return tuple(
        index
        for index, z in enumerate(coordinates())
        if 1339 <= abs(z) <= 2203
    )


def beach_id(value: Any) -> bool:
    if not isinstance(value, str) or not BIOME_RE.fullmatch(value):
        raise EvidenceError(f"invalid biome id: {value!r}")
    return value in BEACH_IDS


def mountain(continentalness: Any, erosion: Any, weirdness: Any) -> bool:
    try:
        values = tuple(float(value) for value in (continentalness, erosion, weirdness))
    except (TypeError, ValueError) as exc:
        raise EvidenceError("malformed climate primitives") from exc
    if not all(math.isfinite(value) for value in values):
        raise EvidenceError("non-finite climate primitive")
    return values[0] > 0.10 and values[1] < -0.25 and abs(values[2]) > 0.25


def relief(surface_y: int, heights: Sequence[int]) -> int:
    if len(heights) != 9 or heights[0] != surface_y:
        raise EvidenceError("nine center-first heights required")
    if not all(type(value) is int for value in heights):
        raise EvidenceError("height vector must contain integers")
    return sorted((abs(value - surface_y) for value in heights[1:]), reverse=True)[1]


def upland_t(surface_y: int) -> float:
    if surface_y <= 112:
        return 0.0
    if surface_y >= 176:
        return 1.0
    return (surface_y - 112) / 64.0


def _record_hash(record: Mapping[str, Any]) -> str:
    body = {key: value for key, value in record.items() if key != "record_sha256"}
    return sha256_bytes(canonical_json(body))


def validate_raw_records(records: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    if not records:
        raise EvidenceError("raw JSONL is empty")
    expected_previous = "0" * 64
    validated: list[dict[str, Any]] = []
    for index, raw in enumerate(records):
        row = dict(raw)
        if row.get("schema") != SCHEMA or row.get("seq") != index:
            raise EvidenceError(f"raw record sequence/schema mismatch at {index}")
        if row.get("previous_sha256") != expected_previous:
            raise EvidenceError(f"raw chain predecessor mismatch at {index}")
        actual = _record_hash(row)
        if row.get("record_sha256") != actual:
            raise EvidenceError(f"raw record hash mismatch at {index}")
        if not isinstance(row.get("event"), str):
            raise EvidenceError(f"raw event type missing at {index}")
        expected_previous = actual
        validated.append(row)
    return validated


def read_raw_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.endswith("\n"):
                raise EvidenceError(f"truncated JSONL line {line_number}")
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise EvidenceError(f"malformed JSONL line {line_number}") from exc
            if not isinstance(value, dict):
                raise EvidenceError(f"non-object JSONL line {line_number}")
            rows.append(value)
    return validate_raw_records(rows)


def by_event(records: Sequence[Mapping[str, Any]], event: str) -> list[dict[str, Any]]:
    return [dict(row) for row in records if row["event"] == event]


def derive_callback_timing(records: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    starts = {row["callback_id"]: row for row in by_event(records, "callback_start")}
    finishes = {row["callback_id"]: row for row in by_event(records, "callback_finish")}
    publications = {
        row["callback_id"]: row for row in by_event(records, "callback_publication")
    }
    if not starts or set(starts) != set(finishes) or set(starts) != set(publications):
        raise EvidenceError("missing callback start/finish/publication raw rows")
    final_callbacks = [
        callback_id
        for callback_id, finish in finishes.items()
        if finish.get("final_state") is True
    ]
    if len(final_callbacks) != 1:
        raise EvidenceError("exactly one finalized callback raw row required")
    lifecycle = [
        (row["seq"], row.get("state"))
        for row in by_event(records, "lifecycle")
        if row.get("state") in {"restored", "finalized"}
    ]
    if [state for _, state in lifecycle] != ["restored", "finalized"]:
        raise EvidenceError("restoration/finalization lifecycle raw rows incomplete")
    final_finish = finishes[final_callbacks[0]]
    final_publication = publications[final_callbacks[0]]
    if not (
        lifecycle[0][0]
        < lifecycle[1][0]
        < final_finish["seq"]
        < final_publication["seq"]
    ):
        raise EvidenceError("restoration/finalization was not published before final callback finish")
    rows: list[dict[str, Any]] = []
    for callback_id in sorted(starts):
        start, finish = starts[callback_id], finishes[callback_id]
        publication = publications[callback_id]
        required_order = (
            start["started_ns"]
            <= finish["evidence_written_ns"]
            <= finish["restored_ns"]
            <= finish["finalized_ns"]
            <= publication["published_ns"]
        )
        if not required_order:
            raise EvidenceError(f"callback timing order invalid: {callback_id}")
        if finish["seq"] >= publication["seq"]:
            raise EvidenceError(f"callback publication sequence invalid: {callback_id}")
        duration = publication["published_ns"] - start["started_ns"]
        if duration > CALLBACK_HOLD_NS:
            raise EvidenceError(f"callback over one second: {callback_id}")
        cells = finish.get("cells")
        if not isinstance(cells, int) or cells < 0 or cells > CELL_CAP:
            raise EvidenceError(f"callback cell cap invalid: {callback_id}")
        rows.append({"callback_id": callback_id, "duration_ns": duration, "cells": cells})
    reported = by_event(records, "callback_aggregate")
    if reported:
        raise EvidenceError("Java callback aggregates/verdicts are forbidden")
    return {
        "callbacks": len(rows),
        "max_duration_ns": max(row["duration_ns"] for row in rows),
        "max_cells": max(row["cells"] for row in rows),
        "rows": rows,
    }


@dataclass(frozen=True)
class RuntimeSnapshot:
    identity: str
    root: str
    observed: Mapping[str, Any]
    sha256: str


def java_hash_path(path: Path) -> str:
    digest = hashlib.sha256()
    if path.is_file():
        digest.update(path.read_bytes())
    elif path.is_dir():
        for file in sorted(item for item in path.rglob("*") if item.is_file()):
            digest.update(file.relative_to(path).as_posix().encode())
            digest.update(b"\0")
            digest.update(file.read_bytes())
    else:
        raise IdentityError(f"unmappable path: {path}")
    return digest.hexdigest()


def _declared_path(root: Path, raw: Mapping[str, Any], label: str) -> Path:
    declared = raw.get("path")
    if not isinstance(declared, str):
        raise IdentityError(f"{label}: unmappable path")
    candidate = Path(declared)
    path = candidate.absolute() if candidate.is_absolute() else (root / candidate).absolute()
    resolved = path.resolve(strict=True)
    allowed = (root.parent.resolve(strict=True), Path.home().joinpath(".gradle").resolve(strict=False))
    if not any(resolved.is_relative_to(base) for base in allowed):
        raise IdentityError(f"{label}: path escapes runtime root")
    return resolved


def _hash_declared_file(root: Path, raw: Mapping[str, Any], label: str) -> str:
    path = _declared_path(root, raw, label)
    entry = raw.get("entry")
    if entry is not None:
        if not isinstance(entry, str) or entry.startswith("/") or ".." in Path(entry).parts:
            raise IdentityError(f"{label}: invalid class entry")
        if path.is_dir():
            actual = sha256_file(path / entry)
        elif path.is_file() and zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as archive:
                actual = sha256_bytes(archive.read(entry))
        else:
            raise IdentityError(f"{label}: class container unmappable")
    else:
        actual = java_hash_path(path)
    if raw.get("sha256") != actual:
        raise IdentityError(f"{label}: content hash mismatch")
    return actual


def verify_runtime_identity(raw: Mapping[str, Any], root: Path, identity: str) -> RuntimeSnapshot:
    if raw.get("supported") is not True:
        raise IdentityError(f"{identity}: runtime identity unmappable")
    world = raw.get("world")
    if not isinstance(world, Mapping):
        raise IdentityError(f"{identity}: world identity missing")
    expected = {
        "preset": PRESET,
        "settings": SETTINGS,
        "dimension": DIMENSION,
        "generator_class": "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator",
        "radius": RADIUS,
    }
    for key, value in expected.items():
        if world.get(key) != value:
            raise IdentityError(f"{identity}: observed {key} drift")
    if world.get("identity") != identity:
        raise IdentityError(f"{identity}: observed identity label drift")
    if Path(str(world.get("root"))).resolve(strict=True) != root.resolve(strict=True):
        raise IdentityError(f"{identity}: observed world root drift")
    versions = raw.get("versions")
    if versions != {"minecraft": MINECRAFT, "loader": LOADER, "fabric_api": FABRIC_API}:
        raise IdentityError(f"{identity}: observed version drift")
    surfaces = {
        "loaded_mod_origins": raw.get("loaded_mod_origins"),
        "executed_classes": raw.get("executed_classes"),
        "classpath": raw.get("classpath"),
        "active_pack_contents": raw.get("active_pack_contents"),
    }
    recomputed: dict[str, list[str]] = {}
    for surface, values in surfaces.items():
        if not isinstance(values, list) or not values:
            raise IdentityError(f"{identity}: {surface} unmappable")
        declared = [
            (
                str(_declared_path(root, value, f"{identity}/{surface}")),
                value.get("entry"),
            )
            for value in values
        ]
        if len(declared) != len(set(declared)):
            raise IdentityError(f"{identity}: duplicate {surface} observation")
        recomputed[surface] = [
            _hash_declared_file(root, value, f"{identity}/{surface}") for value in values
        ]
    jar = raw.get("release_jar")
    if not isinstance(jar, Mapping):
        raise IdentityError(f"{identity}: release jar unmappable")
    jar_hash = _hash_declared_file(root, jar, f"{identity}/release_jar")
    jar_path = _declared_path(root, jar, f"{identity}/release_jar")
    with zipfile.ZipFile(jar_path) as archive:
        if any(name.startswith("com/example/globe/dev/") for name in archive.namelist()):
            raise IdentityError(f"{identity}: release jar contains dev classes")
    observed = {
        "world": dict(world),
        "versions": dict(versions),
        **recomputed,
        "release_jar": jar_hash,
    }
    return RuntimeSnapshot(identity, str(root.resolve()), observed, sha256_bytes(canonical_json(observed)))


@dataclass(frozen=True)
class FreshWorld:
    path: Path
    seed: int
    preset: str
    created_from_absent: bool


def create_fresh_final_worlds(runtime: Path, selected_seed: int) -> dict[str, FreshWorld]:
    if runtime.absolute() != R3.absolute() or not runtime.is_dir() or runtime.is_symlink():
        raise SafetyError("fresh worlds require owned canonical r3")
    result: dict[str, FreshWorld] = {}
    for identity in ("before_fix", "exact_fix", "current"):
        path = runtime / identity / "minecraft-final"
        if path.exists() or path.is_symlink():
            raise SafetyError(f"final world root must be absent: {identity}")
        path.mkdir(parents=True)
        properties = (
            f"level-seed={selected_seed}\n"
            f"level-type={PRESET}\n"
            "pause-when-empty-seconds=-1\n"
        )
        descriptor = os.open(path / "server.properties", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(properties)
        result[identity] = FreshWorld(path, selected_seed, PRESET, True)
    return result


def _cell_key(row: Mapping[str, Any]) -> tuple[int, int, int]:
    return int(row["seed"]), int(row["z"]), int(row["x"])


def _unique_rows(
    records: Sequence[Mapping[str, Any]], event: str
) -> dict[tuple[int, int, int], dict[str, Any]]:
    result: dict[tuple[int, int, int], dict[str, Any]] = {}
    for row in by_event(records, event):
        key = _cell_key(row)
        if key in result:
            raise EvidenceError(f"duplicate {event} row: {key}")
        result[key] = row
    return result


def derive_fast(row: Mapping[str, Any]) -> dict[str, Any]:
    raw_beach = beach_id(row.get("raw_biome"))
    fast_beach = beach_id(row.get("fast_pick_biome"))
    crisp = row.get("crisp_band") == "TEMPERATE"
    mountain_like = mountain(row.get("continentalness"), row.get("erosion"), row.get("weirdness"))
    mechanism = raw_beach and crisp and mountain_like
    return {
        "raw_beach": raw_beach,
        "crisp_temperate": crisp,
        "mountain": mountain_like,
        "mechanism": mechanism,
        "exact_fast": mechanism and not fast_beach,
    }


def derive_terrain(fast: Mapping[str, Any], terrain: Mapping[str, Any]) -> dict[str, Any]:
    heights = terrain.get("nine_heights")
    if not isinstance(heights, list):
        raise EvidenceError("terrain heights missing")
    surface_y = terrain.get("surface_y")
    sea_level = terrain.get("sea_level")
    if type(surface_y) is not int or type(sea_level) is not int:
        raise EvidenceError("surface/sea primitives missing")
    if heights[0] != surface_y:
        raise EvidenceError("surface Y differs from direct center height")
    return {
        **derive_fast(fast),
        "surface_y": surface_y,
        "sea_level": sea_level,
        "surface_delta": surface_y - sea_level,
        "relief": relief(surface_y, heights),
        "upland_t": upland_t(surface_y),
        "production_pick_biome": terrain.get("production_pick_biome"),
        "nine_heights": list(heights),
    }


def accepts(kind: str, value: Mapping[str, Any]) -> bool:
    pick_beach = beach_id(value["production_pick_biome"])
    eligible = value["surface_delta"] <= 16 and value["upland_t"] == 0.0
    if kind == "target":
        return value["exact_fast"] and eligible and not pick_beach
    if kind == "low_beach":
        return (
            value["raw_beach"]
            and value["crisp_temperate"]
            and not value["mountain"]
            and 0 <= value["surface_delta"] <= 2
            and 0 <= value["relief"] <= 2
            and value["upland_t"] == 0.0
            and pick_beach
        )
    if kind == "rolling_foredune":
        return (
            value["raw_beach"]
            and value["crisp_temperate"]
            and not value["mountain"]
            and 3 <= value["surface_delta"] <= 16
            and 3 <= value["relief"] <= 16
            and value["upland_t"] == 0.0
            and pick_beach
        )
    if kind == "non_temperate_beach":
        return (
            value["raw_beach"]
            and not value["crisp_temperate"]
            and value["mountain"]
            and eligible
            and pick_beach
        )
    raise EvidenceError(f"unknown point kind: {kind}")


def validate_cache_traces(records: Sequence[Mapping[str, Any]]) -> None:
    rows = by_event(records, "cache_trace")
    if len(rows) != 4:
        raise EvidenceError("four cache trace rows required")
    expected_sequence = [
        "install_seed_context",
        "different_chunk_sentinel",
        "production_surfaceDecisionY",
        "direct_base_height",
    ]
    if [row.get("seed") for row in rows] != [1, 1, 2, 2]:
        raise EvidenceError("cache trace seed order mismatch")
    for row in rows:
        if row.get("sequence") != expected_sequence:
            raise EvidenceError("cache trace sequence reversed or incomplete")
        if row.get("sentinel_different_chunk") is not True:
            raise EvidenceError("cache trace sentinel is not in a different chunk")
        if row.get("surface_decision_y") != row.get("direct_base_height"):
            raise EvidenceError("production cache differs from direct ground truth")


@dataclass(frozen=True)
class DiscoveryResult:
    raw_sha256: str
    selected_seed: int
    points: Mapping[str, Mapping[str, Any]]
    derived_counts: Mapping[str, int]
    callback_timing: Mapping[str, Any]
    visual_claim: bool = False

    def freeze(self) -> dict[str, Any]:
        return {
            "schema": SCHEMA,
            "discovery_raw_sha256": self.raw_sha256,
            "selected_seed": self.selected_seed,
            "points": {
                kind: {
                    "seed": row["seed"],
                    "kind": kind,
                    "x": row["x"],
                    "z": row["z"],
                }
                for kind, row in self.points.items()
            },
        }


def production_scan_selected_seed(keys: set[tuple[int, int, int]]) -> int:
    cross_z = {coordinate(index) for index in possible_crisp_rows()}
    all_coordinates = set(coordinates())
    cross = {(z, x) for z in cross_z for x in all_coordinates}
    full = {(z, x) for z in all_coordinates for x in all_coordinates}
    by_seed: dict[int, set[tuple[int, int]]] = {seed: set() for seed in SEEDS}
    for seed, z, x in keys:
        if seed not in by_seed:
            raise EvidenceError(f"unexpected discovery seed: {seed}")
        by_seed[seed].add((z, x))
    full_seeds: list[int] = []
    for seed in SEEDS:
        seed_rows = by_seed[seed]
        if seed_rows == full:
            full_seeds.append(seed)
        elif seed_rows != cross:
            raise EvidenceError(f"seed {seed}: discovery grid shape drift")
    if len(full_seeds) != 1 or len(keys) != 448_615:
        raise EvidenceError("discovery must contain one exact full seed plus 32-seed cross")
    return full_seeds[0]


def derive_discovery(
    records: Sequence[Mapping[str, Any]],
    *,
    require_production_grid: bool = True,
) -> DiscoveryResult:
    validated = validate_raw_records(records)
    if by_event(validated, "verdict") or by_event(validated, "selection") or by_event(validated, "queue"):
        raise EvidenceError("Java emitted forbidden derived evidence")
    fast = _unique_rows(validated, "fast_observation")
    terrain = _unique_rows(validated, "terrain_observation")
    contract_rows = by_event(validated, "scan_contract")
    if len(contract_rows) != 1:
        raise EvidenceError("one raw scan contract required")
    contract = contract_rows[0]
    expected_keys = {
        (int(seed), int(z), int(x))
        for seed, z, x in contract.get("requested_cells", [])
    }
    contracted_full_seed = (
        production_scan_selected_seed(expected_keys)
        if require_production_grid
        else None
    )
    if set(fast) != expected_keys:
        raise EvidenceError("missing or extra raw fast rows")
    ordered = sorted(fast)
    fast_derived = {key: derive_fast(fast[key]) for key in ordered}
    exact_queue = [key for key in ordered if fast_derived[key]["exact_fast"]]
    target_prefix = exact_queue[: min(PREFIX, len(exact_queue))]
    if not target_prefix or any(key not in terrain for key in target_prefix):
        raise EvidenceError("target prefix terrain rows incomplete")
    selected_target: Optional[tuple[int, int, int]] = None
    derived_by_key: dict[tuple[int, int, int], dict[str, Any]] = {}
    for key in target_prefix:
        value = derive_terrain(fast[key], terrain[key])
        derived_by_key[key] = value
        if selected_target is None and accepts("target", value):
            selected_target = key
    if selected_target is None:
        raise EvidenceError("target prefix has no qualifying point")
    selected_seed = selected_target[0]
    if contracted_full_seed is not None and selected_seed != contracted_full_seed:
        raise EvidenceError("full-grid seed differs from Python-selected target seed")
    selected_rows = [key for key in ordered if key[0] == selected_seed]
    temp_queue = [
        key
        for key in selected_rows
        if derive_fast(fast[key])["raw_beach"]
        and derive_fast(fast[key])["crisp_temperate"]
        and not derive_fast(fast[key])["mountain"]
    ]
    non_temp_queue = [
        key
        for key in selected_rows
        if derive_fast(fast[key])["raw_beach"]
        and not derive_fast(fast[key])["crisp_temperate"]
        and derive_fast(fast[key])["mountain"]
    ]
    prefixes = {
        "low_beach": temp_queue[: min(PREFIX, len(temp_queue))],
        "rolling_foredune": temp_queue[: min(PREFIX, len(temp_queue))],
        "non_temperate_beach": non_temp_queue[: min(PREFIX, len(non_temp_queue))],
    }
    selected: dict[str, tuple[int, int, int]] = {"target": selected_target}
    for kind, prefix in prefixes.items():
        if not prefix or any(key not in terrain for key in prefix):
            raise EvidenceError(f"{kind} prefix terrain rows incomplete")
        for key in prefix:
            value = derive_terrain(fast[key], terrain[key])
            derived_by_key[key] = value
            if kind not in selected and accepts(kind, value):
                selected[kind] = key
        if kind not in selected:
            raise EvidenceError(f"{kind} prefix has no qualifying point")
    if len(set(selected.values())) != len(KINDS):
        raise EvidenceError("selected coordinates are not unique")
    validate_cache_traces(validated)
    callbacks = derive_callback_timing(validated)
    points = {
        kind: {
            "seed": key[0],
            "z": key[1],
            "x": key[2],
            "raw": dict(fast[key]),
            "terrain": dict(terrain[key]),
            "derived": derived_by_key[key],
        }
        for kind, key in selected.items()
    }
    counts = {
        "fast_rows": len(fast),
        "terrain_rows": len(terrain),
        "mechanism_rows": sum(value["mechanism"] for value in fast_derived.values()),
        "exact_fast_rows": len(exact_queue),
        "target_prefix": len(target_prefix),
        "low_prefix": len(prefixes["low_beach"]),
        "rolling_prefix": len(prefixes["rolling_foredune"]),
        "non_temperate_prefix": len(prefixes["non_temperate_beach"]),
    }
    return DiscoveryResult(
        sha256_bytes(canonical_json(validated)),
        selected_seed,
        points,
        counts,
        callbacks,
    )


def validate_freeze(freeze: Mapping[str, Any], discovery: DiscoveryResult) -> None:
    if freeze != discovery.freeze():
        raise EvidenceError("frozen handoff contains derived data or drift")
    allowed = {"seed", "kind", "x", "z"}
    for row in freeze["points"].values():
        if set(row) != allowed:
            raise EvidenceError("freeze must contain references only")


def derive_final_observation(row: Mapping[str, Any]) -> dict[str, Any]:
    required = (
        "seed",
        "kind",
        "x",
        "z",
        "raw_biome",
        "crisp_band",
        "continentalness",
        "erosion",
        "weirdness",
        "nine_heights",
        "surface_y",
        "sea_level",
        "production_pick_biome",
        "full_stored_biome",
        "world_biome",
        "raw_quart",
        "absolute_quart",
        "populate_block_y",
    )
    missing = set(required) - set(row)
    if missing:
        raise EvidenceError(f"final raw observation missing fields: {sorted(missing)}")
    fast = {
        "raw_biome": row["raw_biome"],
        "fast_pick_biome": row["production_pick_biome"],
        "crisp_band": row["crisp_band"],
        "continentalness": row["continentalness"],
        "erosion": row["erosion"],
        "weirdness": row["weirdness"],
    }
    terrain = {
        "nine_heights": row["nine_heights"],
        "surface_y": row["surface_y"],
        "sea_level": row["sea_level"],
        "production_pick_biome": row["production_pick_biome"],
    }
    derived = derive_terrain(fast, terrain)
    x, z, surface_y = row["x"], row["z"], row["surface_y"]
    if row["raw_quart"] != [x // 4, RAW_QY, z // 4]:
        raise EvidenceError("final raw quart drift")
    quart = [x // 4, surface_y // 4, z // 4]
    if row["absolute_quart"] != quart or row["populate_block_y"] != (quart[1] << 2) + 2:
        raise EvidenceError("final stored quart/populate Y drift")
    if row["production_pick_biome"] != row["full_stored_biome"]:
        raise EvidenceError("final production/FULL biome drift")
    if row["world_biome"] != row["full_stored_biome"]:
        raise EvidenceError("final world/FULL biome drift")
    return derived


def validate_final_runs(
    discovery: DiscoveryResult,
    freeze: Mapping[str, Any],
    final_records: Mapping[str, Sequence[Mapping[str, Any]]],
    final_roots: Mapping[str, FreshWorld],
) -> dict[str, Any]:
    validate_freeze(freeze, discovery)
    if set(final_records) != set(IDENTITY_BY_KEY) or set(final_roots) != set(IDENTITY_BY_KEY):
        raise EvidenceError("exactly three final identities required")
    derived: dict[str, dict[str, dict[str, Any]]] = {}
    observed_rows: dict[str, dict[str, dict[str, Any]]] = {}
    runtime_snapshots: dict[str, RuntimeSnapshot] = {}
    root_values: set[str] = set()
    for identity, raw_records in final_records.items():
        records = validate_raw_records(raw_records)
        if by_event(records, "verdict") or by_event(records, "selection") or by_event(records, "queue"):
            raise EvidenceError(f"{identity}: Java emitted forbidden derivation")
        identity_rows = by_event(records, "runtime_identity")
        world_rows = by_event(records, "world_identity")
        if len(identity_rows) != 1 or len(world_rows) != 1:
            raise IdentityError(f"{identity}: identity rows incomplete")
        combined_identity = dict(identity_rows[0])
        combined_identity["world"] = dict(world_rows[0])
        fresh = final_roots[identity]
        if not fresh.created_from_absent or fresh.seed != discovery.selected_seed or fresh.preset != PRESET:
            raise IdentityError(f"{identity}: Python fresh-world ledger invalid")
        runtime_snapshots[identity] = verify_runtime_identity(
            combined_identity, fresh.path, identity
        )
        world = world_rows[0]
        if world.get("seed") != discovery.selected_seed:
            raise IdentityError(f"{identity}: final world seed drift")
        root_values.add(str(Path(world["root"]).resolve(strict=True)))
        rows = by_event(records, "final_observation")
        if len(rows) != len(KINDS):
            raise EvidenceError(f"{identity}: final raw rows incomplete")
        keyed = {row["kind"]: row for row in rows}
        if set(keyed) != set(KINDS):
            raise EvidenceError(f"{identity}: final kinds incomplete/duplicated")
        observed_rows[identity] = keyed
        derived[identity] = {}
        for kind, row in keyed.items():
            reference = freeze["points"][kind]
            if {key: row[key] for key in ("seed", "kind", "x", "z")} != reference:
                raise EvidenceError(f"{identity}/{kind}: frozen reference drift")
            derived[identity][kind] = derive_final_observation(row)
        derive_callback_timing(records)
    if len(root_values) != 3:
        raise IdentityError("final worlds did not use three distinct fresh roots")
    pack_hashes = {
        tuple(snapshot.observed["active_pack_contents"])
        for snapshot in runtime_snapshots.values()
    }
    if len(pack_hashes) != 1:
        raise IdentityError("active pack contents drift across final identities")
    for kind in KINDS:
        discovery_row = discovery.points[kind]
        primitive_fields = (
            "raw_biome",
            "crisp_band",
            "continentalness",
            "erosion",
            "weirdness",
        )
        for identity in IDENTITY_BY_KEY:
            final = observed_rows[identity][kind]
            for field in primitive_fields:
                if final[field] != discovery_row["raw"][field]:
                    raise EvidenceError(f"{identity}/{kind}: {field} drift from discovery")
            if final["nine_heights"] != discovery_row["terrain"]["nine_heights"]:
                raise EvidenceError(f"{identity}/{kind}: heights drift from discovery")
            if final["surface_y"] != discovery_row["terrain"]["surface_y"]:
                raise EvidenceError(f"{identity}/{kind}: surface Y drift from discovery")
            if final["sea_level"] != discovery_row["terrain"]["sea_level"]:
                raise EvidenceError(f"{identity}/{kind}: sea level drift from discovery")
    target = {
        identity: observed_rows[identity]["target"]["production_pick_biome"]
        for identity in IDENTITY_BY_KEY
    }
    if not beach_id(target["before_fix"]):
        raise EvidenceError("before target is not beach/shore")
    if beach_id(target["exact_fix"]) or target["exact_fix"] != target["current"]:
        raise EvidenceError("exact/current target outcome parity failed")
    for kind in KINDS:
        if kind != "target":
            values = {
                observed_rows[identity][kind]["production_pick_biome"]
                for identity in IDENTITY_BY_KEY
            }
            if len(values) != 1 or not all(beach_id(value) for value in values):
                raise EvidenceError(f"{kind}: control outcome drift")
    return {"verdict": "PASS", "visual_claim": False, "launches": 4}


def freeze_to_three_finals(
    freeze: Mapping[str, Any], final_paths: Mapping[str, Path]
) -> dict[str, str]:
    if set(final_paths) != set(IDENTITY_BY_KEY):
        raise SafetyError("three final handoff paths required")
    hashes: dict[str, str] = {}
    for identity in ("before_fix", "exact_fix", "current"):
        exclusive_json(final_paths[identity], freeze)
        hashes[identity] = sha256_file(final_paths[identity])
    if len(set(hashes.values())) != 1:
        raise SafetyError("frozen final handoffs are not byte-identical")
    return hashes


class DiscoveryTransport(Protocol):
    def transact(self, request: Mapping[str, Any]) -> Sequence[Mapping[str, Any]]:
        ...


class DiscoveryController:
    """Python-owned interactive discovery; Java only observes requested cells."""

    def __init__(self, transport: DiscoveryTransport) -> None:
        self.transport = transport
        self.raw: list[Mapping[str, Any]] = []

    def request(self, request: Mapping[str, Any]) -> list[dict[str, Any]]:
        rows = [dict(row) for row in self.transport.transact(request)]
        self.raw.extend(rows)
        return rows

    @staticmethod
    def cells(keys: Iterable[tuple[int, int, int]], kind: str = "") -> list[dict[str, Any]]:
        return [
            {"seed": seed, "kind": kind, "x": x, "z": z}
            for seed, z, x in keys
        ]

    def indexed(self, event: str) -> dict[tuple[int, int, int], dict[str, Any]]:
        return {_cell_key(row): dict(row) for row in self.raw if row.get("event") == event}

    def run(self) -> Sequence[Mapping[str, Any]]:
        cross = [
            (seed, coordinate(z_index), coordinate(x_index))
            for seed in SEEDS
            for z_index in possible_crisp_rows()
            for x_index in range(WIDTH)
        ]
        self.request({"command": "scan_fast", "cells": self.cells(cross)})
        fast = self.indexed("fast_observation")
        if set(fast) != set(cross):
            raise EvidenceError("cross scan did not return every requested raw row")
        exact_queue = [
            key for key in sorted(fast) if derive_fast(fast[key])["exact_fast"]
        ]
        target_prefix = exact_queue[: min(PREFIX, len(exact_queue))]
        if not target_prefix:
            raise EvidenceError("Python-derived target prefix is empty")
        self.request(
            {
                "command": "observe_terrain",
                "cells": self.cells(target_prefix, "target"),
            }
        )
        terrain = self.indexed("terrain_observation")
        selected_target = next(
            (
                key
                for key in target_prefix
                if key in terrain and accepts("target", derive_terrain(fast[key], terrain[key]))
            ),
            None,
        )
        if selected_target is None:
            raise EvidenceError("Python-derived target prefix has no qualifying point")
        selected_seed = selected_target[0]
        existing_selected = {key for key in fast if key[0] == selected_seed}
        selected_remainder = [
            (selected_seed, coordinate(z_index), coordinate(x_index))
            for z_index in range(WIDTH)
            for x_index in range(WIDTH)
            if (selected_seed, coordinate(z_index), coordinate(x_index))
            not in existing_selected
        ]
        self.request({"command": "scan_fast", "cells": self.cells(selected_remainder)})
        fast = self.indexed("fast_observation")
        selected_rows = sorted(key for key in fast if key[0] == selected_seed)
        temp_queue = [
            key
            for key in selected_rows
            if derive_fast(fast[key])["raw_beach"]
            and derive_fast(fast[key])["crisp_temperate"]
            and not derive_fast(fast[key])["mountain"]
        ]
        non_temp_queue = [
            key
            for key in selected_rows
            if derive_fast(fast[key])["raw_beach"]
            and not derive_fast(fast[key])["crisp_temperate"]
            and derive_fast(fast[key])["mountain"]
        ]
        control_requests = {
            "low_beach": temp_queue[: min(PREFIX, len(temp_queue))],
            "rolling_foredune": temp_queue[: min(PREFIX, len(temp_queue))],
            "non_temperate_beach": non_temp_queue[: min(PREFIX, len(non_temp_queue))],
        }
        if any(not prefix for prefix in control_requests.values()):
            raise EvidenceError("Python-derived control prefix is empty")
        for kind, prefix in control_requests.items():
            missing = [key for key in prefix if key not in terrain]
            if missing:
                self.request(
                    {
                        "command": "observe_terrain",
                        "cells": self.cells(missing, kind),
                    }
                )
                terrain = self.indexed("terrain_observation")
        requested_fast = sorted(set(cross) | set(selected_remainder))
        self.request(
            {
                "command": "scan_contract",
                "requested_cells": [list(key) for key in requested_fast],
            }
        )
        cache_cells = [
            (1, coordinate(0), coordinate(0)),
            (1, coordinate(1), coordinate(1)),
            (2, coordinate(2), coordinate(2)),
            (2, coordinate(3), coordinate(3)),
        ]
        self.request({"command": "cache_trace", "cells": self.cells(cache_cells)})
        self.request({"command": "finalize_raw"})
        return tuple(self.raw)


@dataclass(frozen=True)
class ProcessIdentity:
    pid: int
    pgid: int
    cwd: str


def safe_group_members(pgid: int) -> list[dict[str, Any]]:
    result = subprocess.run(
        ["ps", "-axo", "pid=,pgid=,ppid=,ucomm="],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    members: list[dict[str, Any]] = []
    for line in result.stdout.splitlines():
        parts = line.strip().split(maxsplit=3)
        if len(parts) == 4 and int(parts[1]) == pgid:
            members.append(
                {"pid": int(parts[0]), "pgid": int(parts[1]), "ppid": int(parts[2]), "ucomm": parts[3]}
            )
    return members


def supervise_owned_process(
    process: subprocess.Popen[bytes],
    identity: ProcessIdentity,
    *,
    timeout_seconds: float,
    members: Callable[[int], Sequence[Mapping[str, Any]]] = safe_group_members,
) -> int:
    if process.pid != identity.pid or os.getpgid(identity.pid) != identity.pgid:
        raise ProcessError("owned process identity mismatch")
    try:
        return_code = process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        os.killpg(identity.pgid, signal.SIGTERM)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(identity.pgid, signal.SIGKILL)
            process.wait(timeout=10)
        return_code = process.returncode
    survivors = list(members(identity.pgid))
    if survivors:
        safe = [
            {
                "pid": row.get("pid"),
                "pgid": row.get("pgid"),
                "ppid": row.get("ppid"),
                "ucomm": row.get("ucomm"),
            }
            for row in survivors
        ]
        raise ProcessError(f"owned process group survived termination: {safe}")
    return return_code


@dataclass(frozen=True)
class OwnedLaunch:
    process: subprocess.Popen
    identity: ProcessIdentity
    raw_path: Path


class ReplacementLauncher(Protocol):
    """Runtime-specific launch adapter; the policy and lifecycle stay in Python."""

    def start_discovery(
        self, runtime: Path
    ) -> tuple[DiscoveryTransport, OwnedLaunch]:
        ...

    def start_final(
        self,
        runtime: Path,
        identity: str,
        world: FreshWorld,
        frozen_handoff: Path,
    ) -> OwnedLaunch:
        ...


def validate_owned_launch(launch: OwnedLaunch, runtime: Path, label: str) -> Path:
    raw_path = task_path(launch.raw_path, f"{label} raw")
    cwd = Path(launch.identity.cwd).resolve(strict=True)
    if not cwd.is_relative_to(runtime.resolve(strict=True)):
        raise ProcessError(f"{label}: owned process cwd escapes runtime")
    if launch.identity.pid <= 1 or launch.identity.pgid <= 1:
        raise ProcessError(f"{label}: unsafe PID/PGID")
    return raw_path


def execute_replacement(
    launcher: ReplacementLauncher,
    *,
    timeout_seconds: float,
) -> dict[str, Any]:
    """One guarded discovery plus three fresh final launches.

    No manual handoff is accepted: the validated discovery result is frozen
    byte-identically into the three fresh roots and each launch must become
    extinct before its raw file is trusted.
    """
    with RuntimeGuard() as guard:
        transport, discovery_launch = launcher.start_discovery(guard.runtime)
        discovery_path = validate_owned_launch(
            discovery_launch, guard.runtime, "discovery"
        )
        discovery_stream = list(DiscoveryController(transport).run())
        supervise_owned_process(
            discovery_launch.process,
            discovery_launch.identity,
            timeout_seconds=timeout_seconds,
        )
        discovery_records = read_raw_jsonl(discovery_path)
        if discovery_stream != discovery_records:
            raise EvidenceError("interactive discovery stream differs from final raw JSONL")
        discovery = derive_discovery(discovery_records)
        freeze = discovery.freeze()
        worlds = create_fresh_final_worlds(guard.runtime, discovery.selected_seed)
        handoffs = {
            identity: worlds[identity].path / "frozen-points.json"
            for identity in IDENTITY_BY_KEY
        }
        handoff_hashes = freeze_to_three_finals(freeze, handoffs)
        final_records: dict[str, Sequence[Mapping[str, Any]]] = {}
        for identity in ("before_fix", "exact_fix", "current"):
            launch = launcher.start_final(
                guard.runtime,
                identity,
                worlds[identity],
                handoffs[identity],
            )
            raw_path = validate_owned_launch(launch, guard.runtime, f"{identity} final")
            supervise_owned_process(
                launch.process,
                launch.identity,
                timeout_seconds=timeout_seconds,
            )
            final_records[identity] = read_raw_jsonl(raw_path)
        verdict = validate_final_runs(discovery, freeze, final_records, worlds)
        return {
            **verdict,
            "discovery_raw_sha256": discovery.raw_sha256,
            "handoff_sha256": next(iter(handoff_hashes.values())),
            "identity_count": len(final_records),
        }


def runner_patch(original: bytes) -> tuple[bytes, str]:
    if sha256_bytes(original) != RUNNER_SHA:
        raise IdentityError("runner source hash drift")
    text = original.decode()
    if text.count(RUNNER_ANCHOR) != 1:
        raise IdentityError("runner anchor is not unique")
    patched = text.replace(RUNNER_ANCHOR, RUNNER_INSERT + RUNNER_ANCHOR)
    import difflib

    patch = "".join(
        difflib.unified_diff(
            text.splitlines(keepends=True),
            patched.splitlines(keepends=True),
            fromfile=f"a/{RUNNER_REL}",
            tofile=f"b/{RUNNER_REL}",
            n=3,
        )
    )
    if patch.count("@@") != 2:
        raise IdentityError("runner patch is not exactly one hunk")
    return patched.encode(), patch


def contract() -> dict[str, Any]:
    return {
        "schema": SCHEMA,
        "tool_version": TOOL_VERSION,
        "java_role": "exclusive append-only raw JSONL sensor",
        "python_role": "all predicates queues prefixes selections counts timing and verdicts",
        "grid": {
            "formula": "c(i)=-3750+32*i",
            "width": WIDTH,
            "possible_crisp_rows": len(possible_crisp_rows()),
        },
        "freeze_fields": ["discovery_raw_sha256", "selected_seed", "kind", "seed", "x", "z"],
        "final_world": {
            "count": 3,
            "fresh_absent_roots": True,
            "seed": "selected discovery seed",
            "preset": PRESET,
            "settings": SETTINGS,
            "dimension": DIMENSION,
        },
        "visual_claim": False,
    }


if __name__ == "__main__":
    print(json.dumps(contract(), indent=2, sort_keys=True))
