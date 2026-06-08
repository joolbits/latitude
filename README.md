# Latitude

A globe-style world + latitude-based biome bands with a customizable compass HUD and warnings.

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
See: docs/design-spec.md

## Guardrails / Workflows
- Savepoint Autopilot: `.windsurf/workflows/latitude-savepoint-autopilot.md`

## Dependencies

- Fabric Loader (Minecraft 1.21.11)
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
