# Latitude

A globe-style world + latitude-based biome bands with a customizable compass HUD and warnings.

## Status

This checkout tracks the Latitude `1.5.1-beta.1+26.2` line (Minecraft 26.2), branch
`codex/port/1.5.1-beta.1-26.2-forward`. See `CHANGELOG.md` for what's new in this release.

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

- Fabric Loader (Minecraft 26.2)
- Fabric API

## Biome tag integration

This mod selects biomes for latitude bands via a family of `globe:lat_*` biome tags — separate
primary/secondary/accent tiers per latitude band, plus dedicated tags for rivers, beaches, oceans,
and wetlands. See `src/main/resources/data/globe/tags/worldgen/biome/` for the current full set.

Biome mods can integrate by adding their biomes into the relevant tags.

## Config

Compass HUD configuration is stored in:

- `config/globe_compass_hud.json`

## Building

```bash
./gradlew clean build
```

(Windows: `gradlew.bat clean build`.)

The release jar to upload is in:

- `build/libs/` (the remapped main jar, not `-dev` / `-sources`)

## Support

ko-fi.com/peetsa
