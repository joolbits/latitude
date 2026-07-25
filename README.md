# Latitude

A globe-style world + latitude-based biome bands with a customizable compass HUD and warnings.

## Latitude 2.0 overhaul front door

The current planning front door is `docs/LATITUDE_2_0_OVERHAUL.md`.

That document supersedes older "Mercator", E/W wrap, and ocean-seam design records for the Latitude 2.0 overhaul direction. The 2.0 plan keeps the 2:1 projected-planet foundation, pivots the planned canonical implementation to Minecraft `26.2`, and starts with portability plus Atlas geography measurement before any visible continent/climate behavior changes.

## Current 1.4 candidate status

This checkout is the canonical Minecraft `26.1.2` Latitude 1.4 candidate root. Current release-readiness truth lives in `docs/release/checklist.md`; do not treat older published `1.4.0+26.1.2` or `1.21.11` records as the active candidate gate. The permanent live rerun checklist lives in `docs/release/scenic-drive-green-checklist.md`.

_(origin/main also tracked a parallel 1.4 candidate worktree at `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`, with this checkout serving as the main docs/history root; preserved here for reference.)_

## Features

- **Latitude biome bands** via biome tags under `globe:lat_*`.
- **Warning overlays** (e.g. pole / edge warnings).
- **Compass HUD**
  - Toggle keybind: `K`
  - Open settings: `F9`
  - Preview mode toggle: `P`
  - Alt+Left-Click a compass icon in inventory to toggle (shows a red X when disabled)
  - Fully configurable: anchors, offsets, scale, background alpha, colors, show modes, direction modes

## Design Spec / Release Gate
See:

- `docs/LATITUDE_2_0_OVERHAUL.md` for the Latitude 2.0 overhaul plan.
- `docs/design-spec.md` for the existing design spec.
- `docs/release/checklist.md` for release gates.

## Guardrails / Workflows
- Savepoint Autopilot: `.windsurf/workflows/latitude-savepoint-autopilot.md`

## Dependencies

- Fabric Loader (current local source line: Minecraft 26.1.2; planned 2.0 canonical pivot: Minecraft 26.2)
- _(origin/main also referenced a separate Latitude 1.4 candidate worktree using Fabric Loader for Minecraft `26.1.2` at `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`; this checkout's `gradle.properties` has since moved onto the 26.2 pivot line above.)_
- Fabric API

## Biome tag integration

This mod selects biomes for latitude bands via biome tags:

- `globe:lat_equator`
- `globe:lat_tropical`
- `globe:lat_temperate`
- `globe:lat_subpolar`
- `globe:lat_polar`

Biome mods can integrate by adding their biomes into these tags.

## Config

Compass HUD configuration is stored in:

- `config/globe_compass_hud.json`

## Building

```powershell
.\gradlew.bat clean build
```

The release jar to upload is in:

- `build/libs/` (the remapped main jar, not `-dev` / `-sources`)

For Latitude 1.4 readiness, build from the canonical `26.1.2` worktree named above, not from this historical `1.21.11` checkout.

## Support

ko-fi.com/peetsa

## License

Latitude — a Minecraft mod.
Copyright (C) 2026 Peetsa

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this
program. If not, see <https://www.gnu.org/licenses/>. The full text is in
[`LICENSE`](LICENSE).

*(Previously MIT through early 2026; relicensed to GPL-3.0-or-later going forward.
Versions already published under MIT remain available under MIT.)*
