#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ATLAS_ROOT = ROOT / "run" / "latdev" / "atlas"
RUNS_ROOT = ROOT / "run-headless" / "latdev" / "atlas-runs"
DEFAULT_SEED = 2591890304012655616
DEFAULT_SIZE = "small"
DEFAULT_Y = 64
SIZE_TO_RADIUS = {
    "itty": 3750,
    "ittybitty": 3750,
    "itty_bitty": 3750,
    "xsmall": 3750,
    "tiny": 5000,
    "small": 7500,
    "medium": 7500,
    "regular": 10000,
    "large": 15000,
    "ginormous": 20000,
    "massive": 20000,
}
REQUIRED_BUNDLE_FILES = (
    "biomes.png",
    "legend.json",
    "world_biome_inventory.json",
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Cross-platform Latitude atlas runner.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate = subparsers.add_parser("generate", help="Generate a viewer run.")
    generate.add_argument("--step", type=int, required=True)
    generate.add_argument("--seed", type=int, default=DEFAULT_SEED)
    generate.add_argument("--size", default=DEFAULT_SIZE)
    generate.add_argument("--no-viewer-open", action="store_true")

    ruggedness = subparsers.add_parser("ruggedness", help="Generate ruggedness for an existing viewer run.")
    ruggedness.add_argument("--run", required=True)
    ruggedness.add_argument("--step", type=int, required=True)
    ruggedness.add_argument("--no-viewer-open", action="store_true")

    args = parser.parse_args()
    if args.command == "generate":
        generate_run(step=args.step, seed=args.seed, size=args.size)
        return 0
    if args.command == "ruggedness":
        generate_ruggedness(run_id=args.run, step=args.step)
        return 0
    parser.error(f"Unknown command: {args.command}")
    return 2


def generate_run(*, step: int, seed: int, size: str) -> None:
    validate_step(step)
    size_key = normalize_size(size)
    run_id = allocate_run_id()
    target_run_dir = RUNS_ROOT / run_id

    ensure_clean_target(target_run_dir)
    started_at = time.time()
    run_gradle_preview(
        seed=seed,
        size=size_key,
        step=step,
        layers=None,
    )
    source_step_dir = find_fresh_step_dir(seed=seed, step=step, started_at=started_at)
    if not source_step_dir.exists():
        raise FileNotFoundError(f"Atlas step output not found: {source_step_dir}")
    # The exporter's size->radius mapping is authoritative, so read the radius from
    # the emitted R<radius> directory instead of guessing from UI size labels.
    radius_match = re.search(r"[/\\]R(\d+)[/\\]", str(source_step_dir) + os.sep)
    radius = int(radius_match.group(1)) if radius_match else radius_for_size(size_key)

    RUNS_ROOT.mkdir(parents=True, exist_ok=True)
    target_run_dir.mkdir(parents=True, exist_ok=False)
    copy_step_bundle(source_step_dir, target_run_dir, step)
    write_manifest(
        target_run_dir / "run_manifest.json",
        run_id=run_id,
        seed=seed,
        size=size_key,
        radius=radius,
        step=step,
    )
    validate_bundle(target_run_dir, step, radius)


def generate_ruggedness(*, run_id: str, step: int) -> None:
    validate_step(step)
    run_dir = RUNS_ROOT / run_id
    if not run_dir.exists():
        raise FileNotFoundError(f"Run folder not found: {run_dir}")

    manifest = read_json(run_dir / "run_manifest.json")
    seed = int(manifest.get("seed", DEFAULT_SEED))
    size = normalize_size(str(manifest.get("size", DEFAULT_SIZE)))

    started_at = time.time()
    run_gradle_preview(
        seed=seed,
        size=size,
        step=step,
        layers="ruggedness",
    )
    source_step_dir = find_fresh_step_dir(seed=seed, step=step, started_at=started_at)
    ruggedness_path = source_step_dir / "ruggedness.png"
    if not ruggedness_path.exists():
        raise FileNotFoundError(f"Ruggedness output not found: {ruggedness_path}")
    shutil.copy2(ruggedness_path, run_dir / f"step{step}_ruggedness.png")


def run_gradle_preview(*, seed: int, size: str, step: int, layers: str | None) -> None:
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    args = [
        f"--seed {seed}",
        f"--size {size}",
        f"--step {step}",
        f"--y {DEFAULT_Y}",
        "--bundle true",
        "--emitBiomeIndex true",
    ]
    if layers:
        args.append(f"--layers {layers}")

    command = [gradlew, "--no-daemon", "runBiomePreview", f"--args={' '.join(args)}"]
    subprocess.run(command, cwd=ROOT, env=gradle_env(), check=True)


def copy_step_bundle(source_step_dir: Path, target_run_dir: Path, step: int) -> None:
    for child in sorted(source_step_dir.iterdir()):
        if child.is_file():
            shutil.copy2(child, target_run_dir / f"step{step}_{child.name}")


def validate_bundle(target_run_dir: Path, step: int, expected_radius: int) -> None:
    for name in REQUIRED_BUNDLE_FILES:
        path = target_run_dir / f"step{step}_{name}"
        if not path.exists():
            raise FileNotFoundError(f"Missing required atlas bundle file: {path}")

    legend = read_json(target_run_dir / f"step{step}_legend.json")
    radius = int(legend.get("radiusBlocks", -1))
    if radius != expected_radius:
        raise RuntimeError(
            f"Radius mismatch for run {target_run_dir.name}: expected {expected_radius}, legend reported {radius}"
        )


def ensure_clean_target(target_run_dir: Path) -> None:
    if target_run_dir.exists():
        raise FileExistsError(f"Run folder already exists: {target_run_dir}")


def find_fresh_step_dir(*, seed: int, step: int, started_at: float) -> Path:
    seed_root = ATLAS_ROOT / f"seed_{seed}"
    if not seed_root.exists():
        raise FileNotFoundError(f"Atlas seed output root not found: {seed_root}")

    # Match any recent R*/step<step> folder because the exporter decides the final
    # radius for a size label, and that mapping can drift from hardcoded guesses.
    candidates: list[Path] = []
    for step_dir in seed_root.glob(f"Run_*/R*/step{step}"):
        if not step_dir.is_dir():
            continue
        try:
            mtime = step_dir.stat().st_mtime
        except FileNotFoundError:
            continue
        if mtime >= started_at - 1.0:
            candidates.append(step_dir)

    if not candidates:
        raise FileNotFoundError(f"No fresh atlas step directory found under {seed_root} for step {step}")

    candidates.sort(key=lambda path: path.stat().st_mtime)
    return candidates[-1]


def write_manifest(path: Path, *, run_id: str, seed: int, size: str, radius: int, step: int) -> None:
    payload = {
        "ts": run_id,
        "branch": git_text("branch", "--show-current"),
        "commit": git_text("rev-parse", "--short", "HEAD"),
        "seed": str(seed),
        "size": size,
        "radiusBlocks": radius,
        "step": step,
        "emitBiomeIndex": True,
        "emitHeight": False,
        "legacyRun": f"Run_{run_id}",
        "migratedFrom": "run/latdev/atlas",
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def git_text(*args: str) -> str:
    try:
        proc = subprocess.run(
            ["git", *args],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        return proc.stdout.strip()
    except Exception:
        return ""


def allocate_run_id() -> str:
    base = datetime.now().strftime("%Y%m%d-%H%M%S")
    candidate = base
    suffix = 2
    while (RUNS_ROOT / candidate).exists():
        candidate = f"{base}.{suffix}"
        suffix += 1
    return candidate


def radius_for_size(size: str) -> int:
    try:
        return SIZE_TO_RADIUS[size]
    except KeyError as exc:
        raise ValueError(f"Unknown atlas size '{size}'") from exc


def normalize_size(raw: str) -> str:
    value = (raw or "").strip().lower()
    if not value:
        return DEFAULT_SIZE
    return value


def validate_step(step: int) -> None:
    if step <= 0 or step > 4096:
        raise ValueError("step must be in range 1..4096")


def gradle_env() -> dict[str, str]:
    env = os.environ.copy()
    if os.name != "nt" and not env.get("JAVA_HOME"):
        java_home = detect_java_home_25()
        if java_home:
            env["JAVA_HOME"] = java_home
            env["PATH"] = f"{Path(java_home) / 'bin'}:{env.get('PATH', '')}"
    return env


def detect_java_home_25() -> str | None:
    if sys.platform != "darwin":
        return None
    try:
        proc = subprocess.run(
            ["/usr/libexec/java_home", "-v", "25"],
            check=True,
            capture_output=True,
            text=True,
        )
    except Exception:
        return None
    value = proc.stdout.strip()
    return value or None


def read_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"Expected JSON object in {path}")
    return data


if __name__ == "__main__":
    raise SystemExit(main())
