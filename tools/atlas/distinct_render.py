#!/usr/bin/env python3
"""Render biome_ids.png with a DISTINCT color per biome id, upscaled.

The shipped biomes.png uses natural biome colors, so jungle/bamboo/sparse (and many
forests) look identical and confetti between same-color biomes is invisible. This
renders the exact per-pixel biome identity (biome_ids.png red channel + biome_palette.json)
with maximally separated colors so every biome boundary is visible.

Color scheme (IDENTICAL to the live viewer's buildDistinctPalette in
tools/atlas/viewer/index.html — this file is the offline QA single source of truth):
  * Keyed on a STABLE biome-id rank (sorted unique lowercased ids), NOT the volatile
    palette index — that index keying was the defect this rewrite fixes.
  * Per-namespace hue arcs (minecraft/biomesoplenty/terralith/promenade) + golden-ratio
    spacing within each namespace's local rank.
  * Reserved muted colors for oceans/rivers/beaches (excluded from arc local-rank count).
  * OKLab ΔE collision sweep: deterministic L nudge until min pairwise ΔE(OKLab) >= 12.

Math is written to match the JS bit-for-bit: Math.round -> floor(x+0.5); Math.cbrt ->
math.cbrt; identical formula order. Run `--selftest <ids.json>` to dump the palette JSON
(used by the JS<->Python parity check).
"""
from __future__ import annotations
import json, math, re, sys
from collections import defaultdict
from pathlib import Path

NS_HUE_ARCS = {
    "minecraft": (20, 70),
    "biomesoplenty": (90, 160),
    "terralith": (200, 260),
    "promenade": (280, 340),
}
DISTINCT_DELTA_E_MIN = 12.0

try:
    _cbrt = math.cbrt  # Python 3.11+ (matches JS Math.cbrt via libm)
except AttributeError:  # pragma: no cover
    def _cbrt(x):
        return math.copysign(abs(x) ** (1.0 / 3.0), x)


def jround(x):
    # JS Math.round: round half toward +Infinity (matches our non-negative 0..255 range).
    return int(math.floor(x + 0.5))


def namespace_of(bid):
    s = str(bid or "")
    i = s.find(":")
    return s[:i] if i > 0 else "unknown"


def ns_hue_arc(ns):
    if ns in NS_HUE_ARCS:
        return NS_HUE_ARCS[ns]
    h = 0
    for ch in ns:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF  # mirrors JS Math.imul(h,31)+c >>> 0
    start = h % 330
    return (start, start + 30)


def reserved_muted_color(bid):
    s = str(bid).lower()
    local = s.split(":", 1)[1] if ":" in s else s
    if "ocean" in s:
        return (0x1C, 0x2A, 0x4A)
    if local in ("river", "frozen_river") or "river" in s:
        return (0x28, 0x46, 0x6E)
    if re.search(r"beach|shore|coast|dune", s):
        return (0xC9, 0xB9, 0x8A)
    return None


def distinct_rank_ids(ids):
    # Stable, deterministic, code-point id rank (== JS Array.sort() on lowercased ids).
    return sorted(set(str(x or "").strip().lower() for x in ids if str(x or "").strip()))


def hsv_to_rgb(h, s, v):
    i = math.floor(h * 6)
    f = h * 6 - i
    p = v * (1 - s)
    q = v * (1 - f * s)
    t = v * (1 - (1 - f) * s)
    m = int(i) % 6
    if m == 0:
        r, g, b = v, t, p
    elif m == 1:
        r, g, b = q, v, p
    elif m == 2:
        r, g, b = p, v, t
    elif m == 3:
        r, g, b = p, q, v
    elif m == 4:
        r, g, b = t, p, v
    else:
        r, g, b = v, p, q
    return [jround(r * 255), jround(g * 255), jround(b * 255)]


def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c):
    v = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return max(0, min(255, jround(v * 255)))


def rgb_to_oklab(rgb):
    r = srgb_to_linear(rgb[0])
    g = srgb_to_linear(rgb[1])
    b = srgb_to_linear(rgb[2])
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_ = _cbrt(l)
    m_ = _cbrt(m)
    s_ = _cbrt(s)
    return (
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )


def oklab_to_rgb(lab):
    L, a, b = lab
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l = l_ * l_ * l_
    m = m_ * m_ * m_
    s = s_ * s_ * s_
    return [
        linear_to_srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        linear_to_srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        linear_to_srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    ]


def delta_e_oklab(a, b):
    dL = a[0] - b[0]
    da = a[1] - b[1]
    db = a[2] - b[2]
    return 100 * math.sqrt(dL * dL + da * da + db * db)


def distinct_candidates():
    # In-gamut HSV candidate grid; identical order to the viewer's distinctCandidates().
    cand = []
    for hi in range(180):                # hue 0..358 step 2deg
        h = (hi * 2) / 360.0
        for s in (0.45, 0.60, 0.75, 0.90, 1.00):
            for v in (0.35, 0.50, 0.65, 0.80, 0.92, 1.00):
                rgb = hsv_to_rgb(h, s, v)
                cand.append((rgb, rgb_to_oklab(rgb)))
    return cand  # 180*5*6 = 5400


def distinct_thin(cand, threshold):
    kept = []
    for rgb, lab in cand:
        ok = True
        for _, klab in kept:
            if delta_e_oklab(lab, klab) < threshold:
                ok = False
                break
        if ok:
            kept.append((rgb, lab))
    return kept


def distinct_lattice(count):
    # Anchor set sized to fit `count` biomes at the largest threshold <= target the gamut allows.
    # sRGB only holds ~43 deltaE>=12 colours, so larger stacks relax to a maximally-separated palette.
    cand = distinct_candidates()
    anchors = distinct_thin(cand, DISTINCT_DELTA_E_MIN)
    t2 = 2 * int(DISTINCT_DELTA_E_MIN) - 1
    while len(anchors) < count and t2 >= 8:
        anchors = distinct_thin(cand, t2 / 2)
        t2 -= 1
    return anchors


def enforce_distinct_delta_e(color_by_id, ranked_ids):
    ids = [i for i in ranked_ids if i in color_by_id]
    lattice = distinct_lattice(len(ids))
    taken = [False] * len(lattice)
    for i in range(len(ids)):
        base_lab = rgb_to_oklab(color_by_id[ids[i]])
        best_idx = -1
        best_d = float("inf")
        for j in range(len(lattice)):
            if taken[j]:
                continue
            d = delta_e_oklab(lattice[j][1], base_lab)
            if d < best_d:  # strict < → lowest-index anchor wins ties
                best_d = d
                best_idx = j
        if best_idx >= 0:
            taken[best_idx] = True
            color_by_id[ids[i]] = lattice[best_idx][0]


def build_distinct_palette(ids):
    """id -> [r,g,b]; identical scheme to the viewer's buildDistinctPalette()."""
    ranked = distinct_rank_ids(ids)
    by_ns = defaultdict(list)
    reserved = {}
    for bid in ranked:
        muted = reserved_muted_color(bid)
        if muted:
            reserved[bid] = list(muted)
            continue
        by_ns[namespace_of(bid)].append(bid)
    color_by_id = {}
    for ns, group in by_ns.items():
        arc_start, arc_end = ns_hue_arc(ns)
        for k, bid in enumerate(group):
            h = (arc_start + (arc_end - arc_start) * ((k * 0.618033) % 1)) / 360.0
            s = 0.62 + 0.30 * (((k * 2) % 3) / 2.0)
            v = 0.70 + 0.25 * (((k * 5) % 4) / 3.0)
            color_by_id[bid] = hsv_to_rgb(h, s, v)
    for bid, c in reserved.items():
        color_by_id[bid] = list(c)
    # ΔE sweep separates only ARC (non-reserved) biomes; reserved muted colors are
    # intentionally shared (oceans/rivers/beaches de-emphasised so land pops).
    arc_ranked = [bid for bid in ranked if bid not in reserved]
    enforce_distinct_delta_e(color_by_id, arc_ranked)
    return color_by_id


def _selftest(ids_path):
    ids = json.loads(Path(ids_path).read_text())
    pal = build_distinct_palette(ids)
    print(json.dumps(pal, sort_keys=True))
    return 0


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--selftest":
        return _selftest(sys.argv[2])

    from PIL import Image
    from collections import defaultdict as _dd

    run = Path(sys.argv[1])
    scale = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    palette = json.loads((run / "biome_palette.json").read_text())["biomes"]
    pal = {int(p["index"]): p["biome_id"] for p in palette}
    img = Image.open(run / "biome_ids.png").convert("RGB")
    w, h = img.size
    px = [rgb[0] for rgb in img.getdata()]

    color_by_id = build_distinct_palette([bid for bid in pal.values()])
    colors = {}
    for idx, bid in pal.items():
        colors[idx] = tuple(color_by_id.get(str(bid).strip().lower(), (0, 0, 0)))

    out = Image.new("RGB", (w, h))
    out.putdata([colors.get(v, (0, 0, 0)) for v in px])
    if scale > 1:
        out = out.resize((w * scale, h * scale), Image.NEAREST)
    dst = run / "biome_ids_distinct.png"
    out.save(dst)
    print(f"wrote {dst}  ({w}x{h} x{scale})")

    # per-biome 4-connected components (so numbers match the picture)
    visited = bytearray(w * h)
    comps = _dd(list)
    for start in range(w * h):
        if visited[start]:
            continue
        idx = px[start]; visited[start] = 1; q = [start]; cur = 0
        while cur < len(q):
            p = q[cur]; cur += 1; x = p % w; y = p // w
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h:
                    npos = ny * w + nx
                    if not visited[npos] and px[npos] == idx:
                        visited[npos] = 1; q.append(npos)
        comps[pal.get(idx, "?")].append(len(q))
    print("biome | total px | comps | singletons(1px) | <5px | <50px | largest")
    rows = sorted(comps.items(), key=lambda kv: -sum(kv[1]))
    for bid, sizes in rows:
        if "ocean" in bid:
            continue
        tot = sum(sizes)
        if tot < 30:
            continue
        ones = sum(1 for s in sizes if s == 1)
        u5 = sum(1 for s in sizes if s < 5)
        u50 = sum(1 for s in sizes if s < 50)
        print(f"{bid:38s} {tot:6d} {len(sizes):5d} {ones:6d} {u5:6d} {u50:6d} {max(sizes):6d}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
