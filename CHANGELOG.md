# Changelog

## Latitude 1.4 status (MC 26.1.2 canonical)
- Current 1.4 candidate truth lives in `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md`.
- The permanent live rerun checklist lives in `docs/release/scenic-drive-green-checklist.md`.
- Public version naming, savepoint, and publication remain undecided until Maintainer approves the final candidate.

## Historical released entries
Entries below are retained for already-published or older-version lines. They are not the active Latitude 1.4 candidate source of truth.

## Latitude 1.3.0 (MC 1.21.11)
- Release hygiene pass for the v1.3 gate: removed dev-only mixins/probes and stray System.out logging from the shipping jar.
- Updated version metadata to `1.3.0+1.21.11` and added release notes aligned to validated scope.
- Worldgen/band logic unchanged; deferred polish remains out of scope (Phase B/E/F).

## Latitude 1.2.5 (MC 1.21.x)
- Broadened declared compatibility to cover MC 1.21.0–1.21.11.
- Two jars provided: one for 1.21.0–1.21.3, one for 1.21.10–1.21.11.
- Hardened fog mixins with require=0 for cross-version stability on the compat jar.
- No gameplay or worldgen changes from 1.2.4.

## Latitude 1.2.4 (MC 1.21.11)
- EW storm intensity ramp tightened (shader-friendly haze works with Sodium + Iris) for a stronger wall near EW borders.
- Warm-band cold-biome clamp prevents snow/ice leakage in Equator/Tropics/Arid bands.
- HUD/overlay ordering preserved so warnings stay readable under the haze.
