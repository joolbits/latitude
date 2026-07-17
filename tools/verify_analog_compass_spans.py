#!/usr/bin/env python3
"""Dependency-free red/green and raster-equivalence proof for Performance Slice C."""

from __future__ import annotations

import argparse
import math
import re
import sys
from pathlib import Path


SOURCE_REL = Path("src/main/java/com/example/globe/client/CompassHud.java")
DRAW_SIGNATURE = (
    "private static void drawAnalogCompass("
    "GuiGraphicsExtractor ctx, CompassHudConfig cfg, int cx, int cy, int radius, double angle)"
)


def extract_method(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise ValueError(f"missing method signature: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise ValueError(f"missing method body: {signature}")
    depth = 0
    quote: str | None = None
    escaped = False
    line_comment = False
    block_comment = False
    index = brace
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
        elif block_comment:
            if char == "*" and following == "/":
                block_comment = False
                index += 1
        elif quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char == "/" and following == "/":
            line_comment = True
            index += 1
        elif char == "/" and following == "*":
            block_comment = True
            index += 1
        elif char in {'"', "'"}:
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1
    raise ValueError(f"unterminated method body: {signature}")


def span_half(radius: int, dy: int) -> int:
    remaining = radius * radius - dy * dy
    return -1 if remaining < 0 else math.isqrt(remaining)


def baseline_disc_commands(
    cx: int, cy: int, radius: int, ring: int, face: int
) -> list[tuple[int, int, int, int, int]]:
    commands: list[tuple[int, int, int, int, int]] = []
    inner_squared = (radius - 2) * (radius - 2)
    outer_squared = radius * radius
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            distance_squared = dx * dx + dy * dy
            if distance_squared > outer_squared:
                continue
            color = ring if distance_squared > inner_squared else face
            commands.append((cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color))
    return commands


def candidate_span_commands(
    cx: int, cy: int, radius: int, ring: int, face: int
) -> list[tuple[int, int, int, int, int]]:
    commands: list[tuple[int, int, int, int, int]] = []
    inner_radius = radius - 2
    for dy in range(-radius, radius + 1):
        outer_half = span_half(radius, dy)
        if outer_half < 0:
            continue
        y = cy + dy
        inner_half = (
            span_half(inner_radius, dy) if abs(dy) <= inner_radius else -1
        )
        if inner_half < 0:
            commands.append(
                (cx - outer_half, y, cx + outer_half + 1, y + 1, ring)
            )
        else:
            commands.append(
                (cx - outer_half, y, cx - inner_half, y + 1, ring)
            )
            commands.append(
                (cx + inner_half + 1, y, cx + outer_half + 1, y + 1, ring)
            )
            commands.append(
                (cx - inner_half, y, cx + inner_half + 1, y + 1, face)
            )
    return commands


def rasterize(
    commands: list[tuple[int, int, int, int, int]],
) -> dict[tuple[int, int], int]:
    pixels: dict[tuple[int, int], int] = {}
    for x0, y0, x1, y1, color in commands:
        if x0 >= x1 or y0 >= y1:
            raise AssertionError(f"empty/inverted fill: {(x0, y0, x1, y1, color)}")
        for py in range(y0, y1):
            for px in range(x0, x1):
                if (px, py) in pixels:
                    raise AssertionError(f"overlapping base spans at {(px, py)}")
                pixels[(px, py)] = color
    return pixels


def model_equivalence() -> tuple[bool, str]:
    ring = 0xFF72A5D4
    face = 0x6B18324A
    centers = ((0, 0), (37, -19))
    tested_diameters = 0
    tested_cases = 0
    baseline_calls = 0
    span_calls = 0
    max_baseline_calls = 0
    max_span_calls = 0
    even_diameter_oddity_cases = 0
    checkpoints: dict[int, tuple[int, int]] = {}

    for diameter in range(24, 129):
        radius = diameter // 2
        tested_diameters += 1
        if diameter % 2 == 0:
            if 2 * radius + 1 != diameter + 1:
                return False, f"even-diameter geometry changed at diameter={diameter}"
            even_diameter_oddity_cases += 1
        for cx, cy in centers:
            tested_cases += 1
            baseline = baseline_disc_commands(cx, cy, radius, ring, face)
            spans = candidate_span_commands(cx, cy, radius, ring, face)
            try:
                baseline_pixels = rasterize(baseline)
                span_pixels = rasterize(spans)
            except AssertionError as error:
                return False, f"diameter={diameter} center={(cx, cy)} {error}"
            if span_pixels != baseline_pixels:
                missing = set(baseline_pixels) - set(span_pixels)
                extra = set(span_pixels) - set(baseline_pixels)
                recolored = {
                    pixel
                    for pixel in set(baseline_pixels) & set(span_pixels)
                    if baseline_pixels[pixel] != span_pixels[pixel]
                }
                return (
                    False,
                    f"diameter={diameter} center={(cx, cy)} "
                    f"missing={len(missing)} extra={len(extra)} "
                    f"recolored={len(recolored)}",
                )
            for x0, y0, x1, y1, _color in spans:
                if not (
                    cx - radius <= x0 < x1 <= cx + radius + 1
                    and cy - radius <= y0 < y1 <= cy + radius + 1
                    and y1 == y0 + 1
                ):
                    return False, f"out-of-bounds span: {(x0, y0, x1, y1)}"
            if len(spans) >= len(baseline):
                return (
                    False,
                    f"draw-call reduction missing at diameter={diameter}: "
                    f"{len(baseline)} -> {len(spans)}",
                )
            baseline_calls += len(baseline)
            span_calls += len(spans)
            max_baseline_calls = max(max_baseline_calls, len(baseline))
            max_span_calls = max(max_span_calls, len(spans))
            if (cx, cy) == centers[0] and diameter in {24, 48, 128}:
                checkpoints[diameter] = (len(baseline), len(spans))

    return (
        True,
        f"diameters={tested_diameters} centers={len(centers)} cases={tested_cases} "
        f"even_oddity_cases={even_diameter_oddity_cases} "
        f"checkpoints={checkpoints} "
        f"calls={baseline_calls}->{span_calls} "
        f"max_case={max_baseline_calls}->{max_span_calls}",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, default=Path("."))
    parser.add_argument("--baseline-root", type=Path)
    args = parser.parse_args()

    source = (args.source_root / SOURCE_REL).read_text()
    baseline = (
        (args.baseline_root / SOURCE_REL).read_text()
        if args.baseline_root is not None
        else source
    )
    checks: list[tuple[str, bool, str]] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append((name, condition, detail))

    try:
        draw_method = extract_method(source, DRAW_SIGNATURE)
        baseline_draw = extract_method(baseline, DRAW_SIGNATURE)
    except ValueError as error:
        print(f"VERDICT RED failures=1 assertions=1")
        print(f"FAIL owner_method: {error}")
        return 1

    nested_pixel_loop = (
        "for (int dx = -radius; dx <= radius; dx++)" in draw_method
        and "ctx.fill(px, py, px + 1, py + 1" in draw_method
    )
    check(
        "per_pixel_disc_removed",
        not nested_pixel_loop,
        f"nested_pixel_loop={nested_pixel_loop}",
    )
    check(
        "single_row_loop",
        draw_method.count("for (int dy = -radius; dy <= radius; dy++)") == 1
        and "for (int dx = -radius; dx <= radius; dx++)" not in draw_method,
        "one dy loop and no dx loop",
    )
    check(
        "span_geometry_used",
        "int half = analogSpanHalf(radius, dy);" in draw_method
        and "int halfIn = Math.abs(dy) <= innerRadius"
        in draw_method
        and "analogSpanHalf(innerRadius, dy)" in draw_method,
        "outer and inner horizontal half-spans",
    )
    check(
        "disc_colors_computed_once",
        draw_method.count("analogInnerColor(cfg, colors.face())") == 1
        and "int faceColor = analogInnerColor(cfg, colors.face());" in draw_method,
        "unchanged inner-color result reused by all face spans",
    )
    check(
        "draw_prefix_preserved",
        re.search(
            re.escape(DRAW_SIGNATURE)
            + r"\s*\{\s*var colors = analogColors\(cfg\);\s*"
            r"int innerRadius = radius - 2;",
            draw_method,
        )
        is not None,
        "signature and theme-color fetch remain first; span state follows",
    )
    expected_fills = (
        "ctx.fill(cx - half, py, cx + half + 1, py + 1, colors.ring());",
        "ctx.fill(cx - half, py, cx - halfIn, py + 1, colors.ring());",
        "ctx.fill(cx + halfIn + 1, py, cx + half + 1, py + 1, colors.ring());",
        "ctx.fill(cx - halfIn, py, cx + halfIn + 1, py + 1, faceColor);",
    )
    check(
        "exact_span_fill_partition",
        all(fill in draw_method for fill in expected_fills),
        "full ring row or disjoint left-ring/right-ring/face spans",
    )

    try:
        span_method = extract_method(
            source, "private static int analogSpanHalf(int radius, int dy)"
        )
        check(
            "integer_exact_span_helper",
            "int remaining = radius * radius - dy * dy;" in span_method
            and "return remaining < 0 ? -1 : (int) Math.sqrt(remaining);"
            in span_method,
            "largest integer |dx| satisfying dx^2+dy^2 <= radius^2",
        )
    except ValueError as error:
        check("integer_exact_span_helper", False, str(error))

    tail_marker = "int tickLen = Math.max(2, radius / 6);"
    source_tail_start = draw_method.find(tail_marker)
    baseline_tail_start = baseline_draw.find(tail_marker)
    check(
        "overlay_tail_byte_identical",
        source_tail_start >= 0
        and baseline_tail_start >= 0
        and draw_method[source_tail_start:] == baseline_draw[baseline_tail_start:],
        "ticks, N label, both needles, and center dot retain source and order",
    )
    check(
        "disc_before_overlays",
        draw_method.find("for (int dy = -radius; dy <= radius; dy++)")
        < source_tail_start,
        "base disc commands remain before tick/label/needle/center overlays",
    )

    baseline_match_methods = (
        "public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter)",
        "public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH)",
        "public static void renderAdjustPreview(GuiGraphicsExtractor ctx, int screenW, int screenH)",
        "private static void renderInternal(GuiGraphicsExtractor ctx, int screenW, int screenH, boolean forceVisible)",
        "public static void renderPreview(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, int x, int y)",
        "private static void renderAnalogAt(",
        "private static int analogInnerColor(CompassHudConfig cfg, int faceRgb)",
        "private static void drawLine(",
        "private static int analogDiameter(CompassHudConfig cfg)",
        "private static AnalogColors analogColors(CompassHudConfig cfg)",
    )
    for signature in baseline_match_methods:
        try:
            check(
                f"baseline_match_{signature.split('(')[0].split()[-1]}",
                extract_method(source, signature)
                == extract_method(baseline, signature),
                signature,
            )
        except ValueError as error:
            check(f"baseline_match_{len(checks)}", False, str(error))

    try:
        theme_method = extract_method(
            source, "private static AnalogColors analogColors(CompassHudConfig cfg)"
        )
        theme_cases = re.findall(r"\bcase\s+([A-Z_]+)\s*->", theme_method)
        check(
            "all_11_theme_mappings_preserved",
            len(theme_cases) == 11 and len(set(theme_cases)) == 11,
            f"theme_cases={theme_cases}",
        )
    except ValueError as error:
        check("all_11_theme_mappings_preserved", False, str(error))

    donor_only_tokens = (
        "CompassDialRenderer",
        "CompassLook",
        "analogLook",
        "drawDiscBase",
        "drawRose",
        "drawTape",
        "drawMinimal",
        "TAPE_WINDOW_DEG",
        "TEXTURE_PRESENT",
        "livePosQ",
        "liveBiomeQuad",
        "liveDirStr",
        "invalidateTextures",
    )
    check(
        "no_mixed_2_0_hud_import",
        not any(token in source for token in donor_only_tokens),
        f"forbidden_tokens={[token for token in donor_only_tokens if token in source]}",
    )

    model_ok, model_detail = model_equivalence()
    check(
        "all_effective_sizes_raster_equivalent",
        model_ok,
        model_detail,
    )

    failed = [(name, detail) for name, ok, detail in checks if not ok]
    baseline_radius = 64
    baseline_max = len(
        baseline_disc_commands(0, 0, baseline_radius, 0xFF000001, 0x7F000002)
    )
    candidate_max = len(
        candidate_span_commands(0, 0, baseline_radius, 0xFF000001, 0x7F000002)
    )
    print(
        "DRAW_CALLS "
        f"radius={baseline_radius} disc_commands={baseline_max}->{candidate_max} "
        f"pixel_loop={str(nested_pixel_loop).lower()}"
    )
    print(f"MODEL {model_detail}")
    for name, detail in failed:
        print(f"FAIL {name}: {detail}")
    if failed:
        print(f"VERDICT RED failures={len(failed)} assertions={len(checks)}")
        return 1
    print(f"VERDICT GREEN assertions={len(checks)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
