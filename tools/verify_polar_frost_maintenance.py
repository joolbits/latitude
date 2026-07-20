#!/usr/bin/env python3
"""Guard the polar frost visual against the vanilla thaw/refresh sawtooth."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/example/globe/GlobeMod.java"


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"missing owner method: {signature}")
    opening = source.find("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated owner method: {signature}")


def simulated_frost_span(refresh_period: int, target: int, ticks: int = 30) -> int:
    """Model vanilla's two-ticks-per-tick thaw outside powder snow."""
    frozen = 0
    samples = []
    for tick in range(ticks):
        frozen = max(0, frozen - 2)
        if tick % refresh_period == 0:
            frozen = max(frozen, target)
        samples.append(frozen)
    return max(samples) - min(samples)


def main() -> int:
    body = method_body(SOURCE.read_text(), "private static void borderUxTick")

    old_gate = re.search(
        r"if\s*\(\s*\(worldTime\s*%\s*10L\)\s*!=\s*0L\s*\)\s*\{\s*return;\s*\}",
        body,
        re.DOTALL,
    )
    split_gate = "boolean effectsTick = (worldTime % 10L) == 0L;" in body
    if old_gate:
        span = simulated_frost_span(10, 119)
        raise AssertionError(
            "polar frost RED: the 10-tick owner refresh fights vanilla's "
            f"per-tick thaw and produces a {span}-tick visual sawtooth"
        )
    if not split_gate:
        raise AssertionError("missing split per-tick frost / 10-tick effects cadence")

    freeze_calls = [match.start() for match in re.finditer(r"player\.setTicksFrozen\(", body)]
    if len(freeze_calls) != 1:
        raise AssertionError(f"expected one frost maintenance write, found {len(freeze_calls)}")

    effects_gate = body.find("if (!effectsTick)")
    if effects_gate < 0:
        raise AssertionError("missing 10-tick MobEffect gate")
    if freeze_calls[0] > effects_gate:
        raise AssertionError("frost write is still throttled behind the 10-tick effects gate")

    effect_calls = [match.start() for match in re.finditer(r"player\.addEffect\(", body)]
    if not effect_calls or min(effect_calls) < effects_gate:
        raise AssertionError("MobEffects must remain behind their 10-tick cadence gate")

    if "Math.floor(max * 0.85)" in body:
        raise AssertionError(
            "polar damage RED: final-zone frost target is 119/140, below "
            "vanilla's fully-frozen damage threshold"
        )

    required_hazard_fragments = (
        "int max = 140;",
        "int target = max + 3;",
        "Math.max(player.getTicksFrozen(), target)",
        "!(player.isCreative() || player.isSpectator())",
    )
    for fragment in required_hazard_fragments:
        if fragment not in body:
            raise AssertionError(f"missing steady damaging frost policy: {fragment}")

    forbidden_damage_calls = ("hurtServer(", "damageSources().freeze")
    for fragment in forbidden_damage_calls:
        if fragment in body:
            raise AssertionError(f"unexpected polar damage path added: {fragment}")

    span = simulated_frost_span(1, 143)
    if span != 0:
        raise AssertionError(f"per-tick frost maintenance still sawtooths by {span} ticks")

    print(
        "PASS: polar frost is steady above the vanilla damage threshold, "
        "with creative/spectator exemption and no duplicate damage call"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
