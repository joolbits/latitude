# Latitude 1.5.1 Beta 2 — Minecraft 1.21.11

Latitude 1.5.1 Beta 2 brings the 1.5 climate, world-generation, compatibility, and presentation work to
Minecraft 1.21.11.

This is a beta release. Back up important worlds before testing. Existing Latitude worlds keep their
established world-generation policy; the updated climate model and biome selection are intended for new
worlds.

## Highlights

- Rebuilt climate and moisture selection for more coherent equatorial, tropical, subtropical,
  temperate, and polar regions.
- Desert is the staple of the subtropical arid belt again: badlands now generates as coherent
  patches inside it instead of crowding it out, savanna forms its own bordered regions plus a dry
  fringe along every arid edge, and ordinary tropical forest is back after effectively disappearing.
- Windswept hills, forest, and gravelly mountains are confined to subpolar mountains instead of
  spreading across the true polar band.
- Improved support for biome providers and datapacks, including Biomes O' Plenty, Terralith, and
  Promenade integration where those mods are installed.
- Refined polar vegetation, treeline, fog, warnings, and climate-zone presentation.
- Fixed a Biomes O' Plenty compatibility issue where TerraBlender silently overrode Latitude's own
  surface painting, so badlands rendered with the wrong, untuned look whenever it was installed.
- Added the square Atlas Create World preview and a short Latitude intro that does not replay when
  controls or tabs rebuild the screen.
- Improved loading presentation across fresh-world and existing-save entry paths, including a
  climate-zone label that now appears when joining a remote or LAN-hosted server, not only when
  hosting your own world.
- `/locate biome` and `/locate structure` now return the nearest match instead of the first one found,
  coverage gaps are closed so reserved biomes stay findable, and the coordinate `/locate` prints is
  clickable to teleport straight there. Agreement between locate results and every
  structure-placement interaction remains a Beta 2 testing focus.
- Corrected climate-title cooldown behavior when the world clock moves backward.

- **Badlands is no longer a seed lottery.** The noise deciding where mesa country sits scaled with
  world size, so one unlucky roll could hand most of the dry belt to badlands. Across twelve seeds it
  covered 24%–71% of the dry belt before; it now covers 11%–30%, in many more, smaller regions.
- **Desert riverbanks can be green.** Where an arid biome meets fresh water the bank sometimes grows
  grass and wildflowers instead of bare sand to the waterline. Not everywhere, and seed-fixed. The
  biome is unchanged — still desert, still desert mobs and structures; only the planting differs.

## Optional retrofit command

Operators may use `/latitude retrofit` on a backed-up older Latitude world. The command refuses
non-Latitude worlds and requires the explicit `enable` then `confirm` sequence. Its worker processes at most
two chunks per tick, caps the pending queue at 2,048 chunks, exposes progress through `status`, and clears
its session when disabled or when the server stops.

## Compatibility

- Minecraft 1.21.11
- Fabric Loader 0.17.3 or newer
- Fabric API
- Java 21 or newer
- Client and server

The playable file is `latitude-1.5.1-beta.3+1.21.11.jar`. Do not install the sources JAR as the mod.

## Help test Beta 2

Read the [beta testing guide](https://github.com/peetsamods/latitude/blob/main/docs/testing-beta.md) and
use the [structured bug-report form](https://github.com/peetsamods/latitude/issues/new?template=bug_report.yml).

The most useful checks for this beta are:

- whether a fresh world starts inside its selected spawn zone;
- whether `/locate biome` and `/locate structure` agree with what is present after chunks settle,
  and whether the nearest-match result and its clickable teleport land you in the right place;
- provider behavior with Biomes O' Plenty, Terralith, and combined stacks, including Redwood Forest
  and whether badlands renders with Latitude's terracotta look rather than an unrelated untuned one;
- desert, badlands, savanna, and forest balance across the subtropical and tropical belts, plus
  wetland and alpine placement; and
- first load, ordinary movement, save/reload, performance, and crashes.

## Known limitations

- With large custom-biome stacks, rare accent biomes may not appear in every world. Latitude improves
  provider representation, but it does not guarantee that every biome from every installed pack
  appears.
- Villages don't spawn in forest (unchanged vanilla behavior), and savanna — where villages are
  common — now covers less of the warm belt than before this release, so warm-belt village density
  has eased somewhat. Villages remain common inside savanna regions.
- A rare seam can appear where a lush temperate biome directly borders an arid subtropical one at
  the exact band boundary. This is uncommon and already understood; a smoother transition is planned
  for a later pass.
- Savanna can still occasionally turn up a short distance outside the region it belongs to, through a
  fallback path that does not re-check the region boundary. This is minor and accepted, and is also
  present on Latitude's Minecraft 26.2 release line.

Planned source tag: `v1.5.1-beta.3+1.21.11`. The tag and public release are created only after final
artifact verification and live acceptance.
