# Latitude 1.5.1 Beta 1 — Minecraft 1.21.11

Latitude 1.5.1 Beta 1 brings the 1.5 climate, world-generation, compatibility, and presentation work to
Minecraft 1.21.11.

This is a beta release. Back up important worlds before testing. Existing Latitude worlds keep their
established world-generation policy; the updated climate model and biome selection are intended for new
worlds.

## Highlights

- Rebuilt climate and moisture selection for more coherent equatorial, tropical, subtropical,
  temperate, and polar regions.
- Improved support for biome providers and datapacks, including Biomes O' Plenty, Terralith, and
  Promenade integration where those mods are installed.
- Refined polar vegetation, treeline, fog, warnings, and climate-zone presentation.
- Added the square Atlas Create World preview and a short Latitude intro that does not replay when
  controls or tabs rebuild the screen.
- Improved loading presentation across fresh-world and existing-save entry paths.
- Hardened structure and wetland location behavior, including bounded searches and bossbar cleanup.
- Corrected climate-title cooldown behavior when the world clock moves backward.

## Optional retrofit command

Operators may use `/latitude retrofit` on a backed-up older non-Latitude world. The command refuses
Latitude worlds and requires the explicit `enable` then `confirm` sequence. Its worker processes at most
two chunks per tick, caps the pending queue at 2,048 chunks, exposes progress through `status`, and clears
its session when disabled or when the server stops.

## Compatibility

- Minecraft 1.21.11
- Fabric Loader 0.17.3 or newer
- Fabric API
- Java 21 or newer
- Client and server

The playable file is `latitude-1.5.1-beta.1+1.21.11.jar`. Do not install the sources JAR as the mod.

## Known limitation

With large custom-biome stacks, rare accent biomes may not appear in every world. Latitude improves
provider representation, but it does not guarantee that every biome from every installed pack appears.

Planned source tag: `v1.5.1-beta.1+1.21.11`. The tag and public release are created only after final
artifact verification and live acceptance.
