#!/usr/bin/env python3
"""Over/under-representation report for a Latitude atlas run.

Peer of band_balance_analyze.py / embedded_speck.py. REUSES their loaders + passes verbatim
(import, not copy): band geometry (`parse_geom`/`load`/`lat_of_row` via `analyze`), the 4-connected
component pass (`band_balance_analyze.analyze` → tiny_share/largest_share), and the single-enclosure
speck pass (ported from `embedded_speck.main` → per-biome marooned_pct). Geometry stays identical
(lat = |(-radius + iz*step)| / radius * 90; same BANDS edge list — Art VI).

Two independent axes per biome/family: SHARE delta (actual vs expected range) and COHERENCE
(largest_share / tiny_share / marooned_pct). A rare biome can be under its share band yet coherent
(GREEN), and a biome can be in-band yet confetti (RED-CONFETTI).

Expectation tables:
  * EXPECTED_FAMILY[band][family] = (lo, hi) — share of band LAND pixels (static, below).
  * Per-band BIOME POOL — frozen tools/atlas/expected_pools.json (snapshot of the worldgen
    lat_<province>_<tier>.json tags). Build once with `--rebuild-pools`; the viewer never reaches
    into the worldgen tag tree at runtime.

Usage:
  representation_report.py --rebuild-pools                 # regenerate expected_pools.json from tags
  representation_report.py <run_dir> [--layer step16] [--json]   # emit the report
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import tempfile
from collections import Counter, defaultdict
from pathlib import Path

# Reuse the canonical analyzers (single source of truth for geometry + passes).
import band_balance_analyze as bba

ROOT = Path(__file__).resolve().parents[2]
POOLS_PATH = Path(__file__).resolve().parent / "expected_pools.json"
TAGS_DIR = ROOT / "src" / "main" / "resources" / "data" / "globe" / "tags" / "worldgen" / "biome"

# Family share expectations per geometric band (share of band LAND pixels). §4.4 table A.
EXPECTED_FAMILY = {
    "tropical":    {"jungle": (0.45, 0.75), "savanna": (0.05, 0.20), "desert": (0.00, 0.08),
                    "badlands": (0.00, 0.05), "taiga": (0.00, 0.00), "other": (0.10, 0.35)},
    "subtropical": {"jungle": (0.00, 0.10), "savanna": (0.10, 0.30), "desert": (0.15, 0.40),
                    "badlands": (0.05, 0.20), "taiga": (0.00, 0.00), "other": (0.10, 0.40)},
    "temperate":   {"jungle": (0.00, 0.00), "savanna": (0.00, 0.08), "desert": (0.00, 0.05),
                    "badlands": (0.00, 0.05), "taiga": (0.05, 0.20), "other": (0.45, 0.85)},
    "subpolar":    {"jungle": (0.00, 0.00), "savanna": (0.00, 0.00), "desert": (0.00, 0.00),
                    "badlands": (0.00, 0.00), "taiga": (0.40, 0.75), "other": (0.20, 0.50)},
    "polar":       {"jungle": (0.00, 0.00), "savanna": (0.00, 0.00), "desert": (0.00, 0.00),
                    "badlands": (0.00, 0.00), "taiga": (0.05, 0.25), "other": (0.70, 0.95)},
}
# Inner-equator (0-12°) gate (v1.4): jungle >= 0.55, arid <= 0.07.
INNER_EQUATOR_JUNGLE_MIN = 0.55
INNER_EQUATOR_ARID_MAX = 0.07

# Geometric band -> biome-pool province key in expected_pools.json (for per-biome expectations).
BAND_TO_PROVINCE = {
    "tropical": "equator", "subtropical": "arid",
    "temperate": "temperate", "subpolar": "subpolar", "polar": "polar",
}
ACCENT_CAP = 0.06          # accent per-biome share cap (~6%)
CONFETTI_MAROONED_MIN = 0.15
TIER_FLOOR = {"primary": 0.08, "secondary": 0.03, "accent": 0.0}


def rebuild_pools() -> dict:
    """Snapshot lat_<province>_<tier>.json tags → {province: {primary,secondary,accent}: [ids]}."""
    pools: dict[str, dict[str, list]] = defaultdict(lambda: {"primary": [], "secondary": [], "accent": []})
    for tier in ("primary", "secondary", "accent"):
        for path in sorted(TAGS_DIR.glob(f"lat_*_{tier}.json")):
            province = path.stem[len("lat_"):-(len(tier) + 1)]
            try:
                values = json.loads(path.read_text()).get("values", [])
            except Exception:
                continue
            ids = []
            for v in values:
                bid = v["id"] if isinstance(v, dict) else v
                if isinstance(bid, str) and ":" in bid:
                    ids.append(bid)
            pools[province][tier] = sorted(set(ids))
    out = {k: dict(v) for k, v in sorted(pools.items())}
    POOLS_PATH.write_text(json.dumps(out, indent=2, sort_keys=True) + "\n")
    return out


def load_pools() -> dict:
    if POOLS_PATH.exists():
        try:
            return json.loads(POOLS_PATH.read_text())
        except Exception:
            pass
    return {}


def marooned_by_biome(path: Path, kmax: int = 10) -> dict:
    """Per-biome single-enclosure speck pixels (ported from embedded_speck.main).
    Returns {biome_id: marooned_px}. marooned_pct = marooned_px / total biome land pixels."""
    radius, step = bba.parse_geom(path)
    idx_to_id, px, w, h = bba.load(path)

    def land(b):
        return "ocean" not in b and b not in ("minecraft:river", "minecraft:frozen_river")

    vis = bytearray(w * h)
    comps = []
    for s in range(w * h):
        if vis[s]:
            continue
        idx = px[s]
        vis[s] = 1
        q = [s]
        c = 0
        while c < len(q):
            p = q[c]
            c += 1
            x = p % w
            y = p // w
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h:
                    n = ny * w + nx
                    if not vis[n] and px[n] == idx:
                        vis[n] = 1
                        q.append(n)
        comps.append((idx, q))

    marooned = Counter()
    for idx, cells in comps:
        bid = idx_to_id.get(idx, "?")
        if not land(bid) or len(cells) > kmax:
            continue
        cs = set(cells)
        border = Counter()
        for p in cells:
            x = p % w
            y = p // w
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h:
                    n = ny * w + nx
                    if n not in cs:
                        nb = idx_to_id.get(px[n], "?")
                        if land(nb):
                            border[nb] += 1
        if not border or len(border) != 1:
            continue  # boundary fragmentation, not a marooned speck — excluded
        marooned[bid] += len(cells)
    return dict(marooned)


def load_inventory_shares(run_dir: Path, layer: str | None) -> dict:
    """Whole-world per-biome share from world_biome_inventory.json (discovery_hits)."""
    name = "world_biome_inventory.json"
    candidates = []
    if layer:
        candidates.append(run_dir / f"{layer}_{name}")
    candidates += [run_dir / name] + sorted(run_dir.glob(f"*_{name}"))
    inv_path = next((p for p in candidates if p.exists()), None)
    if not inv_path:
        return {}
    rows = json.loads(inv_path.read_text()).get("biomes", [])
    hits = {r["biome_id"]: float(r.get("discovery_hits", 0) or 0) for r in rows if r.get("biome_id")}
    total = sum(hits.values()) or 1.0
    return {bid: h / total for bid, h in hits.items()}


def _staged_dir(run_dir: Path, layer: str | None):
    """Return (analysis_dir, is_temp). Raw exporter dirs have non-prefixed files (used directly).
    Viewer run dirs use step-prefixed bundles (e.g. step16_biome_ids.png) — symlink the files
    analyze()/load() expect into a temp dir so the canonical passes are reused verbatim. Geometry
    then comes from the symlinked biomes.txt (radiusBlocks/stepBlocks) since the temp path has no R*/step*."""
    if (run_dir / "biome_palette.json").exists() and (run_dir / "biome_ids.png").exists():
        return run_dir, False
    lyr = layer
    if not lyr:
        matches = sorted(run_dir.glob("*_biome_palette.json"))
        if matches:
            lyr = matches[0].name[:-len("_biome_palette.json")]
    if not lyr:
        return run_dir, False
    tmp = Path(tempfile.mkdtemp(prefix="reprep_"))
    for base in ("biome_palette.json", "biome_ids.png", "biomes.txt", "world_biome_inventory.json"):
        src = run_dir / f"{lyr}_{base}"
        if src.exists():
            os.symlink(src.resolve(), tmp / base)
    return tmp, True


def tier_of(biome_id: str, province_pool: dict) -> str | None:
    # Rarest designation wins (accent < secondary < primary): a biome listed as an accent IS a rare
    # accent even if it also appears in secondary — the rarest role sets the most lenient expectation,
    # avoiding false under-rep flags (e.g. equator bamboo_jungle is in both secondary and accent).
    for tier in ("accent", "secondary", "primary"):
        if biome_id in province_pool.get(tier, []):
            return tier
    return None


def build_report(run_dir: Path, layer: str | None = None) -> dict:
    adir, is_temp = _staged_dir(run_dir, layer)
    try:
        a = bba.analyze(adir)
        marooned = marooned_by_biome(adir)
    finally:
        if is_temp:
            shutil.rmtree(adir, ignore_errors=True)
    pools = load_pools()
    shares = load_inventory_shares(run_dir, layer)
    confetti = a.get("confetti", {})

    # per-biome total land pixels (from confetti pass) for marooned_pct
    biome_total = {bid: m.get("total", 0) for bid, m in confetti.items()}

    sections: dict = {}

    # --- per_band_composition (families + inner-equator gauge) ---
    per_band = []
    for band, lo, hi in bba.BANDS:
        tot = a["band_land_total"].get(band, 0)
        if not tot:
            continue
        fams = a["band_counts"].get(band, {})
        exp = EXPECTED_FAMILY.get(band, {})
        rows = []
        for fam in ("jungle", "savanna", "desert", "badlands", "taiga", "other"):
            share = fams.get(fam, 0) / tot
            elo, ehi = exp.get(fam, (0.0, 1.0))
            if share < elo:
                verdict = "under"
            elif share > ehi:
                verdict = "over"
            else:
                verdict = "ok"
            rows.append({"family": fam, "share": round(share, 4),
                         "expected_lo": elo, "expected_hi": ehi, "verdict": verdict})
        per_band.append({"band": band, "land_pixels": tot, "families": rows})
    # inner equator gauge
    inner = a["trop_sub"].get("inner(0-12)", {})
    inner_tot = a["trop_sub_total"].get("inner(0-12)", 0)
    if inner_tot:
        ij = inner.get("jungle", 0) / inner_tot
        ia = (inner.get("desert", 0) + inner.get("badlands", 0)) / inner_tot
        inner_gauge = {
            "land_pixels": inner_tot,
            "jungle_share": round(ij, 4), "jungle_min": INNER_EQUATOR_JUNGLE_MIN,
            "arid_share": round(ia, 4), "arid_max": INNER_EQUATOR_ARID_MAX,
            "verdict": "green" if (ij >= INNER_EQUATOR_JUNGLE_MIN and ia <= INNER_EQUATOR_ARID_MAX) else "red",
        }
    else:
        inner_gauge = None
    sections["per_band_composition"] = {"bands": per_band, "inner_equator": inner_gauge}

    # --- per-biome expectations across the pools (over / under / accent_over_rep / confetti) ---
    over, under, accent_over, confetti_off = [], [], [], []
    # union of all biomes seen in shares / pools
    all_pool_ids = set()
    for prov in pools.values():
        for tier in ("primary", "secondary", "accent"):
            all_pool_ids.update(prov.get(tier, []))
    seen = set(shares) | all_pool_ids
    for bid in sorted(seen):
        share = shares.get(bid, 0.0)
        # find the biome's tier+province (first province pool that lists it)
        tier = None
        province = None
        n_tier = 0
        for prov_name, prov in pools.items():
            t = tier_of(bid, prov)
            if t:
                tier = t
                province = prov_name
                n_tier = len(prov.get(t, []))
                break
        if tier is None:
            continue  # not an admitted/expected biome → skip (custom-admission handled elsewhere)
        # Per-biome expectation = 1/n of the tier weight (§4.4: NEVER a flat global threshold — a 2%
        # member of a 12-pool is NORMAL). Accents are allowed arbitrarily rare (lo=0); only their over-rep
        # cap matters. The flat tier floor is deliberately NOT applied to the lo (it would mis-flag
        # large-pool members; see binder note).
        tier_weight = {"primary": 0.50, "secondary": 0.30, "accent": 0.20}[tier]
        ref = tier_weight / max(n_tier, 1)
        if tier == "accent":
            exp_lo = 0.0
            exp_hi = ACCENT_CAP
        else:
            exp_lo = ref * 0.4
            exp_hi = ref * 2.0
        largest = confetti.get(bid, {}).get("largest_share", 0.0)
        tiny = confetti.get(bid, {}).get("tiny_share", 0.0)
        mar_px = marooned.get(bid, 0)
        tot_px = biome_total.get(bid, 0)
        marooned_pct = (mar_px / tot_px) if tot_px else 0.0
        entry = {
            "biome_id": bid, "tier": tier, "province": province, "pool_size": n_tier,
            "actual_share": round(share, 4), "expected_lo": round(exp_lo, 4), "expected_hi": round(exp_hi, 4),
            "largest_share": round(largest, 4), "tiny_share": round(tiny, 4),
            "marooned_pct": round(marooned_pct, 4),
        }
        # share-delta verdict (independent of the coherence axis below)
        is_missing = bid not in shares
        if largest >= 0.5:
            entry["coherent"] = True  # single dominant component → coherent region
        if is_missing:
            entry["delta"] = -1.0
            entry["verdict"] = "amber" if tier == "accent" else "missing"  # accent-missing = AMBER
            under.append(entry)
        elif share < exp_lo:  # only primary/secondary reach here (accent exp_lo = 0)
            entry["delta"] = round(-(exp_lo - share) / max(exp_hi, 1e-9), 4)
            entry["verdict"] = "amber" if share >= exp_lo * 0.5 else "red"
            under.append(entry)
        elif share > exp_hi:
            entry["delta"] = round((share - exp_hi) / max(exp_hi, 1e-9), 4)
            if tier == "accent":
                entry["verdict"] = "red"  # accent over-rep (e.g. ice_spikes)
                entry["note"] = "accent over-rep (cosmetic)"
                accent_over.append(entry)
            else:
                entry["verdict"] = "amber" if share <= exp_hi * 1.5 else "red"
                over.append(entry)
        else:
            entry["verdict"] = "green"  # in band — incl. coherent rare accents
        # Orthogonal confetti axis. The verdict (red-confetti) fires on either the marooned-speck
        # signal OR severe boundary fragmentation, but the confetti_offenders SECTION lists ONLY
        # TRUE-confetti (marooned_pct >= 0.15) — boundary fragmentation is excluded from the section.
        if marooned_pct >= CONFETTI_MAROONED_MIN:
            confetti_off.append({**entry, "verdict": "red-confetti"})
        elif tiny >= 0.4 and largest < 0.25:
            entry["coherence_flag"] = "boundary-fragmentation"

    sections["over_represented"] = sorted(over, key=lambda e: -e.get("delta", 0))
    sections["under_represented"] = sorted(under, key=lambda e: e.get("delta", 0))
    sections["accent_over_rep"] = sorted(accent_over, key=lambda e: -e.get("delta", 0))
    sections["confetti_offenders"] = sorted(confetti_off, key=lambda e: -e.get("marooned_pct", 0))

    return {
        "run": run_dir.name,
        "radius": a.get("radius"),
        "step": a.get("step"),
        "pools_loaded": bool(pools),
        "sections": sections,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run", type=Path, nargs="?")
    ap.add_argument("--layer", default=None)
    ap.add_argument("--rebuild-pools", action="store_true")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    if args.rebuild_pools:
        pools = rebuild_pools()
        print(f"wrote {POOLS_PATH} ({sum(len(v[t]) for v in pools.values() for t in v)} ids across {len(pools)} provinces)")
        return 0
    if not args.run:
        ap.error("run dir required (or --rebuild-pools)")
    report = build_report(args.run, args.layer)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
