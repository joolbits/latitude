# Latitude

A globe-style world + latitude-based biome bands with a customizable compass HUD and warnings.

## Current release line

Latitude `1.5.1-beta.1+1.21.11` targets Minecraft `1.21.11` on Fabric and requires Java 21. It is a beta: back up important worlds before testing, especially before using the optional retrofit command on an older world.

## Features

- **Latitude biome bands** via biome tags under `globe:lat_*`.
- **Warning overlays** (e.g. pole / edge warnings).
- **Compass HUD**
  - Toggle keybind: `K`
  - Open settings: `F9`
  - Preview mode toggle: `P`
  - Alt+Left-Click a compass icon in inventory to toggle (shows a red X when disabled)
  - Fully configurable: anchors, offsets, scale, background alpha, colors, show modes, direction modes

## Design specification

See [`docs/design-spec.md`](docs/design-spec.md).

## Dependencies

- Minecraft 1.21.11
- Fabric Loader 0.17.3 or newer
- Fabric API
- Java 21 or newer

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

```sh
./gradlew clean build
```

On Windows, use `.\gradlew.bat clean build`.

The remapped runtime JAR is:

- `build/libs/latitude-1.5.1-beta.1+1.21.11.jar`

Do not upload development or sources JARs as the playable release file.

## License

Latitude is available under the [GNU General Public License v3.0 or later](LICENSE).

## Support

[Support Latitude on Ko-fi](https://ko-fi.com/peetsa)
