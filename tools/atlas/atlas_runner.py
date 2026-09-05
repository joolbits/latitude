#!/usr/bin/env python3
"""Expose this worktree's existing headless exporter to Latitude Atlas Viewer."""
from __future__ import annotations

import argparse
import hashlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
RUNS_ROOT = ROOT / "run-headless" / "latdev" / "atlas-runs"
SIZES = {
    "itty": (3750, "globe_xsmall"), "ittybitty": (3750, "globe_xsmall"),
    "itty_bitty": (3750, "globe_xsmall"), "xsmall": (3750, "globe_xsmall"),
    "tiny": (5000, "globe_small"), "small": (7500, "globe_regular"),
    "medium": (7500, "globe_regular"), "regular": (10000, "globe_large"),
    "large": (15000, "globe"), "ginormous": (20000, "globe_massive"),
    "massive": (20000, "globe_massive"),
}
REQUIRED = ("biomes.png", "biome_ids.png", "biome_palette.json", "legend.json",
            "world_biome_inventory.json", "land_bands.png", "palette_authority.json")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def command_for(job: Path, *, seed: int, size: str, step: int,
                sysprops: list[str]) -> list[str]:
    radius, preset = SIZES[size]
    recipe = (f"--seed={seed} --radius={radius} --step={step} --y=64 --bundle=true "
              f"--emitbiomeindex=true --layers=biomes,bands,temperature,humidity,continentalness,stats "
              f'--out="{job / "export"}"')
    command = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")),
               "--no-daemon", "--project-cache-dir", str(ROOT / ".gradle" / "lav"),
               "-I", str(job / "build.init.gradle"), "runBiomePreview",
               f"-Platdev.preview.runDir={job / 'runtime'}",
               "-Platdev.preview.levelName=atlas-world", f"-Platdev.preview.levelSeed={seed}",
               f"-Platdev.preview.levelType=globe:{preset}", f"--args={recipe}"]
    for prop in sysprops:
        key, sep, value = prop.partition("=")
        if not sep or key not in {"latitude.emitHeight", "latitude.atlasTerrainAware"} or value not in {"true", "false"}:
            raise ValueError("Unsupported preview property")
        command.append(f"-D{key}={value}")
    return command


def validate_export(path: Path, *, seed: int, radius: int, step: int) -> dict:
    for name in REQUIRED:
        if not (path / name).is_file():
            raise ValueError(f"Missing export file: {name}")
    legend = json.loads((path / "legend.json").read_text())
    if (legend.get("seed"), legend.get("radiusBlocks"), legend.get("stepBlocks")) != (seed, radius, step):
        raise ValueError("Exporter recipe does not match the requested run")
    expected = (2 * radius // step + 1,) * 2
    for name in ("biomes.png", "biome_ids.png"):
        header = (path / name).read_bytes()[:24]
        if header[:8] != b"\x89PNG\r\n\x1a\n" or len(header) != 24 or struct.unpack(">II", header[16:24]) != expected:
            raise ValueError(f"Invalid export image dimensions: {name}")
    return legend


def generate(*, seed: int, size: str, step: int, sysprops: list[str]) -> Path:
    if size not in SIZES or not 1 <= step <= 4096 or not -(2**63) <= seed < 2**63:
        raise ValueError("Invalid size, step, or seed")
    jobs = ROOT / "run-headless" / "lav-jobs"
    jobs.mkdir(parents=True, exist_ok=True)
    lock = jobs / "generation.lock"
    descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(descriptor)
    try:
        head, branch = git("rev-parse", "HEAD"), git("branch", "--show-current")
        source_diff = git("diff", "HEAD")
        runner_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        job = Path(tempfile.mkdtemp(prefix="preview-", dir=jobs))
        runtime = job / "runtime"
        runtime.mkdir()
        # Keep empty preview servers ticking; leave the watchdog at Minecraft's default.
        (runtime / "server.properties").write_text("online-mode=false\nmax-players=1\npause-when-empty-seconds=0\n")
        (job / "build.init.gradle").write_text(
            "gradle.beforeProject { p -> p.layout.buildDirectory.set(new File(p.rootDir, 'build/lav')) }\n")
        command = command_for(job, seed=seed, size=size, step=step, sysprops=sysprops)
        env = os.environ.copy()
        if os.name != "nt" and not env.get("JAVA_HOME") and Path("/usr/libexec/java_home").exists():
            env["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "25"], text=True).strip()
        with (job / "generation.log").open("w") as log:
            result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError(f"Preview failed; inspect {job / 'generation.log'}")
        candidates = list((job / "export").glob(f"seed_{seed}/Run_*/R{SIZES[size][0]}/step{step}"))
        if len(candidates) != 1:
            raise ValueError("Expected exactly one export for this job")
        source = candidates[0]
        validate_export(source, seed=seed, radius=SIZES[size][0], step=step)
        if (git("rev-parse", "HEAD") != head or git("branch", "--show-current") != branch
                or git("diff", "HEAD") != source_diff
                or hashlib.sha256(Path(__file__).read_bytes()).hexdigest() != runner_hash):
            raise ValueError("Source identity changed during generation")
        run_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
        RUNS_ROOT.mkdir(parents=True, exist_ok=True)
        target = job / "viewer-run"
        target.mkdir()
        for path in source.iterdir():
            if path.is_file() and not path.is_symlink():
                shutil.copy2(path, target / f"step{step}_{path.name}")
        manifest = {"ts": run_id, "branch": branch, "commit": head, "seed": str(seed),
                    "size": size, "aspect": 1.0, "radiusBlocks": SIZES[size][0],
                    "radius": SIZES[size][0], "diameter": 2 * SIZES[size][0], "step": step,
                    "emitBiomeIndex": True, "emitHeight": "latitude.emitHeight=true" in sysprops,
                    "sysprops": dict(item.split("=", 1) for item in sysprops)}
        (target / "run_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        published = RUNS_ROOT / run_id
        target.rename(published)
        print(f"ATLAS_RUN_COMPLETE {run_id}", flush=True)
        return published
    finally:
        lock.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    preview = commands.add_parser("generate", help="Generate a saved Viewer run from this worktree")
    preview.add_argument("--step", type=int, required=True)
    preview.add_argument("--seed", type=int, default=1)
    preview.add_argument("--size", choices=sorted(SIZES), default="regular")
    preview.add_argument("--no-viewer-open", action="store_true")
    preview.add_argument("--sysprop", action="append", default=[])
    args = parser.parse_args()
    generate(seed=args.seed, size=args.size, step=args.step, sysprops=args.sysprop)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
