#!/usr/bin/env python3
"""Per-biome over-representation ranker for a Latitude atlas run (the #3 gate).

The runBiomePreview audit emits one biome-audit_*.csv per step dir with a
`selected_land_bands` column formatted like "subtropical=805;tropical=369"
(one band=count pair per latitude band the biome was selected in, ';'-separated;
bands are polar / subpolar / temperate / subtropical / tropical). This script
turns that column into a repeatable green/red over-representation check instead
of the ad-hoc parsing used in the recon.

It reports, for every LAND biome:
  - overall land %   = biome's total land count / all LAND samples
  - within-band %    = biome's count in its DOMINANT band / all LAND in that band
                       (the truest over-rep measure: a biome can own 2/3 of polar
                        land while looking modest overall)
and a verdict against the proposed acceptance gate, plus the tropical humid-family
share that the equator is supposed to keep majority.

Land/water split: any biome id containing "ocean" or "river" is classed as water
and EXCLUDED from the land denominators (ocean/river rows still carry land-band
counts in the CSV, so they must be filtered or the percentages are wrong).

Parsing approach is the one proven in
tmp/1.4.1-prep-20260621/overrep/overrep-ranking.md (sections (a) and (e)).
stdlib only — no numpy/PIL needed (this reads the CSV, not the PNGs).

Usage:  python3 overrep_rank.py <atlas-step-dir>
  where <atlas-step-dir> contains a biome-audit_*.csv
"""
import sys, os, csv, glob

# Acceptance thresholds (proposed for Maintainer sign-off; see overrep-ranking.md (c)).
OVERALL_SUSPECT = 12.0   # land biome over this % of all land is a suspect head
WITHIN_BAND_SUSPECT = 40.0  # ...or over this % of a single band's land
TROPICAL_HUMID_FLOOR = 60.0  # tropical humid family should hold >= this % of tropical land

# Humid-equator primaries: jungle family + BoP tropics (short name match, any namespace).
TROPICAL_HUMID = {"jungle", "bamboo_jungle", "sparse_jungle", "tropics"}


def is_water(biome_id):
    """ocean/river ids are water; exclude from land denominators."""
    s = biome_id.split(":", 1)[-1]
    return "ocean" in s or "river" in s


def parse_bands(cell):
    """'subtropical=805;tropical=369' -> {'subtropical': 805, 'tropical': 369}."""
    out = {}
    for part in (cell or "").strip().split(";"):
        part = part.strip()
        if "=" not in part:
            continue
        band, _, count = part.partition("=")
        band = band.strip()
        count = count.strip()
        if band and count.isdigit():
            out[band] = out.get(band, 0) + int(count)
    return out


def main(d):
    matches = sorted(glob.glob(os.path.join(d, "biome-audit_*.csv")))
    if not matches:
        sys.exit(f"no biome-audit_*.csv found in {d}")
    path = matches[0]

    land = {}   # biome_id -> {band: count}
    water_ids = []
    with open(path, newline="") as fh:
        for row in csv.DictReader(fh):
            bid = (row.get("biome_id") or "").strip()
            if not bid:
                continue
            bands = parse_bands(row.get("selected_land_bands"))
            if not bands:
                continue
            if is_water(bid):
                water_ids.append(bid)
                continue
            dst = land.setdefault(bid, {})
            for b, c in bands.items():
                dst[b] = dst.get(b, 0) + c

    # Denominators: total land, and per-band land (LAND biomes only).
    band_totals = {}
    for bands in land.values():
        for b, c in bands.items():
            band_totals[b] = band_totals.get(b, 0) + c
    total_land = sum(band_totals.values())
    if total_land == 0:
        sys.exit(f"no land samples parsed from {path}")

    # Rank rows: overall%, dominant band, within-band%.
    # "Dominant band" = the band of PEAK within-band share (not raw count): a
    # biome's over-representation lives in whichever band it owns the largest
    # slice of, which can differ from where it has the most raw samples (e.g.
    # savanna has more raw count in tropical but owns far more of the smaller
    # subtropical band). This is how the recon's offender table reads it.
    rows = []
    for bid, bands in land.items():
        biome_total = sum(bands.values())
        overall = 100.0 * biome_total / total_land
        dom_band, within = max(
            ((b, 100.0 * c / band_totals[b]) for b, c in bands.items() if band_totals[b]),
            key=lambda bw: bw[1],
        )
        suspect = overall > OVERALL_SUSPECT or within > WITHIN_BAND_SUSPECT
        rows.append((bid, overall, dom_band, within, suspect))
    rows.sort(key=lambda r: r[1], reverse=True)

    print(f"atlas audit: {path}")
    print(f"land samples: {total_land}   water biomes excluded: {len(set(water_ids))}")
    bt = "  ".join(f"{b}={band_totals[b]}" for b in sorted(band_totals))
    print(f"per-band land totals: {bt}")
    print(f"thresholds: overall>{OVERALL_SUSPECT:.0f}%  OR  within-band>{WITHIN_BAND_SUSPECT:.0f}%  -> SUSPECT")
    print("-" * 78)
    print(f"{'#':>3}  {'biome':<40} {'overall%':>8}  {'dominant band (within%)':<28} verdict")
    print("-" * 78)
    n_suspect = 0
    for i, (bid, overall, dom, within, suspect) in enumerate(rows, 1):
        verdict = "SUSPECT" if suspect else "ok"
        if suspect:
            n_suspect += 1
            reasons = []
            if overall > OVERALL_SUSPECT:
                reasons.append("overall")
            if within > WITHIN_BAND_SUSPECT:
                reasons.append("within-band")
            verdict += " (" + "+".join(reasons) + ")"
        band_cell = f"{dom} {within:.1f}%"
        print(f"{i:>3}  {bid:<40} {overall:>7.2f}%  {band_cell:<28} {verdict}")

    # Tropical humid-family floor: jungle/bamboo_jungle/sparse_jungle/biomesoplenty:tropics
    # as a share of TROPICAL land.
    trop_total = band_totals.get("tropical", 0)
    humid = 0
    for bid, bands in land.items():
        if bid.split(":", 1)[-1] in TROPICAL_HUMID:
            humid += bands.get("tropical", 0)
    print("-" * 78)
    if trop_total:
        share = 100.0 * humid / trop_total
        flag = "FLAG (<%.0f%%)" % TROPICAL_HUMID_FLOOR if share < TROPICAL_HUMID_FLOOR else "ok"
        print(f"tropical humid family (jungle/bamboo_jungle/sparse_jungle/tropics): "
              f"{share:.1f}% of tropical land  -> {flag}")
    else:
        print("tropical humid family: no tropical land sampled")

    print(f"\nGATE: {n_suspect} suspect biome(s) "
          f"-> {'RED' if n_suspect else 'GREEN'}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1].rstrip("/"))
