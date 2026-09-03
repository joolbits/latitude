#!/usr/bin/env python3
"""Prepare a private Recorder Lite route without storing runtime identity in Git."""

from __future__ import annotations

import argparse
from pathlib import Path
import re


TOKEN = re.compile(r"[a-z0-9][a-z0-9-]{0,63}")
WORLD_CLASSES = (
    "fresh-control",
    "legacy-existing-chunks",
    "legacy-new-chunks",
    "ordinary-control",
    "unknown",
)


def token(raw: str, label: str) -> str:
    value = raw.strip().lower()
    if not TOKEN.fullmatch(value):
        raise ValueError(f"{label} must use lowercase letters, numbers, and dashes")
    return value


def pair(raw: str, label: str) -> tuple[str, str]:
    if "=" not in raw:
        raise ValueError(f"{label} must use name=value")
    key, value = raw.split("=", 1)
    return token(key, f"{label} name"), token(value, f"{label} value")


def atlas_pair(raw: str) -> tuple[str, str]:
    if "=" not in raw:
        raise ValueError("atlas setting must use name=value")
    key, value = raw.split("=", 1)
    key = token(key, "atlas setting name")
    value = value.strip()
    if not value or len(value) > 256 or "\n" in value or "\r" in value:
        raise ValueError("atlas setting value must be one non-empty line up to 256 characters")
    return key, value


def escape_property(value: str) -> str:
    return value.replace("\\", "\\\\").replace("=", "\\=").replace(":", "\\:")


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare one private Latitude Recorder Lite route")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--case", required=True)
    parser.add_argument("--world-class", choices=WORLD_CLASSES, required=True)
    parser.add_argument("--checkpoint", action="append", default=[], metavar="NAME=EXPECTED")
    parser.add_argument("--atlas-setting", action="append", default=[], metavar="NAME=VALUE")
    args = parser.parse_args()

    case_id = token(args.case, "case")
    checkpoints = dict(pair(raw, "checkpoint") for raw in args.checkpoint)
    if not checkpoints:
        parser.error("at least one --checkpoint is required")
    if len(checkpoints) != len(args.checkpoint):
        parser.error("checkpoint names must be unique")
    atlas_settings = dict(atlas_pair(raw) for raw in args.atlas_setting)
    if len(atlas_settings) != len(args.atlas_setting):
        parser.error("atlas setting names must be unique")

    destination = args.out.expanduser().resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "schema=latitude-recorder-plan-v1",
        f"case_id={case_id}",
        f"world_class={args.world_class}",
    ]
    lines.extend(
        f"checkpoint.{key}={escape_property(value)}"
        for key, value in sorted(checkpoints.items())
    )
    lines.extend(
        f"atlas.{key}={escape_property(value)}"
        for key, value in sorted(atlas_settings.items())
    )
    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("LATITUDE_RECORDER_PLAN_READY")
    print(f"jvm_property=-Dlatitude.recorder.plan={destination}")
    print(f"start_command=/latdev case start {case_id}")
    for checkpoint in sorted(checkpoints):
        print(f"mark_command=/latdev case mark {checkpoint}=<observed>")
    print("finish_command=/latdev case finish <pass|fail|hold>")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
