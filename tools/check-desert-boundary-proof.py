#!/usr/bin/env python3
"""Validate the targeted desert locate/source/populate boundary proof."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SAVED_PROBE = ROOT / "tmp/live-coordinate-probe-20260621/coordinate-proof-summary.json"


def parse_key_values(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def saved_probe_biome() -> str:
    data = json.loads(SAVED_PROBE.read_text(encoding="utf-8"))
    for point in data["saved_world_probe"]["points"]:
        if point.get("point") == "desert_locate_target":
            return point["saved_biome_at_y136"]
    raise AssertionError("saved probe does not contain desert_locate_target")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("proof_file", type=Path)
    args = parser.parse_args()

    proof_file = args.proof_file
    require(proof_file.exists(), f"missing proof file: {proof_file}")
    require(SAVED_PROBE.exists(), f"missing saved-coordinate probe: {SAVED_PROBE}")

    proof = parse_key_values(proof_file)
    expected = saved_probe_biome()
    required_keys = [
        "x_y_z",
        "wrapped_source_biome",
        "populate_equivalent_biome",
        "live_biome",
        "saved_profile_biome",
        "verdict",
    ]
    for key in required_keys:
        require(key in proof and proof[key], f"proof missing {key}")

    require(proof["saved_profile_biome"] == expected,
            f"saved_profile_biome {proof['saved_profile_biome']} does not match saved probe {expected}")
    require(proof["wrapped_source_biome"] == expected,
            f"wrapped SOURCE mismatch: {proof['wrapped_source_biome']} != saved {expected}")
    require(proof["populate_equivalent_biome"] == expected,
            f"populate-equivalent mismatch: {proof['populate_equivalent_biome']} != saved {expected}")
    live_mode = proof.get("live_biome_mode", "profile")
    if live_mode not in {"headless_unwrapped", "not_checked"}:
        require(proof["live_biome"] == expected,
                f"live/generated biome mismatch: {proof['live_biome']} != saved {expected}")
    if proof.get("locate_target_biome"):
        target = proof["locate_target_biome"]
        for key in [
            "locate_result_holder",
            "locate_result_populate_equivalent_biome",
            "locate_result_live_biome",
            "locate_result_final_matches_target",
        ]:
            require(key in proof and proof[key], f"locate proof missing {key}")
        require(proof["locate_result_holder"] == target,
                f"locate result holder {proof['locate_result_holder']} does not match target {target}")
        require(proof["locate_result_populate_equivalent_biome"] == target,
                "locate result populate-equivalent "
                f"{proof['locate_result_populate_equivalent_biome']} does not match target {target}")
        require(proof["locate_result_live_biome"] == target,
                f"locate result live biome {proof['locate_result_live_biome']} does not match target {target}")
        require(proof["locate_result_final_matches_target"] == "true",
                "locate result final biome did not match target")
    require(proof["verdict"] == "pass", f"proof verdict should be pass, found {proof['verdict']}")

    print("PASS: desert locate boundary proof agrees with saved-coordinate truth")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
