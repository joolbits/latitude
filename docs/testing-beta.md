# Testing a Latitude beta

Beta reports are most useful when they identify the exact game setup, world history, and player-visible
result. You do not need to understand Latitude's internal code to help.

## Before you start

- Back up every important world before testing. Prefer a disposable test world.
- Use the playable Latitude JAR, not the sources JAR.
- Test one mod/provider setup at a time and record exact mod and datapack versions.
- Do not run `/latitude retrofit` first on the only copy of an older world.

## Pick a provider row

Choose one row for a test and name it in the report. You do not need to run every row.

- Latitude with Fabric API and no additional biome provider
- Biomes O' Plenty
- Terralith
- Biomes O' Plenty and Terralith together
- Another biome provider or worldgen datapack stack

If you add, remove, or update a provider or datapack, treat that as a different test row.

## Fresh-world spawn check

1. Create a new Latitude world with the provider row you are testing.
2. Record the world size, climate choice, selected spawn zone, and Generate Structures setting.
3. Let the surrounding terrain finish loading.
4. Check the displayed zone in the HUD or with `/latitude here`, when commands are available.

Report a spawn outside the selected zone, an unsafe starting position, a spawn inside the east/west edge
warning area, or a first load that remains blank or stuck.

## Existing-world check

Use a backup copy and identify the chunks you are looking at:

- **Existing chunks** were generated before the update.
- **Legacy-new chunks** belong to an older Latitude world but were generated after the update.

Check and report those separately. Existing Latitude worlds retain the world-generation policy chosen
when the world was created, so a legacy-new chunk is not the same test as a chunk in a newly created
world.

## Biomes and terrain

Look for player-visible placement problems rather than trying to find every biome in one world:

- With Biomes O' Plenty installed, a Redwood Forest that appears should be in the Temperate zone, and
  badlands should render with Latitude's terracotta surface look rather than an unrelated, untuned
  one — report either kind of mismatch.
- In the subtropics, desert should be the most common arid biome, with badlands forming its own
  coherent patches and savanna forming clear bordered regions plus a dry fringe hugging arid edges.
  Report the reverse: badlands dominating over desert, or savanna spreading everywhere rather than
  staying inside its own regions and fringe.
- Windswept hills, forest, and gravelly mountains should stay on subpolar mountains, not spread
  across the true polar band as flat, snowless terrain.
- Report badlands at the deep equator or desert overwhelming the tropical/equatorial region.
- Temperate wetlands should read as flat lowlands; warm mangroves should read as warm coastal or
  brackish terrain.
- Report implausible alpine vegetation, snow, treeline, or structure placement.
- Report a biome or provider repeatedly appearing in an obviously wrong climate zone.

One rare biome being absent from one seed is not itself a bug. Large provider stacks do not guarantee
that every provider or every biome appears in every world. A whole installed provider repeatedly absent
across comparable disposable worlds is still worth reporting.

## Locate accuracy

When commands are available, run `/locate biome` or `/locate structure`, travel to the result, and wait
for the destination chunks to settle. Report whether the named biome or real structure is actually
present. For a structure report, include whether Generate Structures was enabled. False destinations and
suspicious not-found results are useful beta reports.

`/locate` now reports the nearest match rather than the first one found along the search path, and the
coordinate it prints is clickable — clicking it should teleport you straight to the reported location.
Report a result that is clearly not the nearest match, or a click that does not teleport you to the
printed coordinate.

## Stability and performance

Report crashes, disconnects, long stalls, terrain holes, save/reload failures, or severe stutter during
ordinary movement. Include how long you tested, whether you had just teleported or were moving normally,
and any relevant render distance, simulation distance, shader, or hardware details.

## Make a useful report

[Open the structured beta bug-report form](../../../issues/new?template=bug_report.yml) and include:

- exact Latitude, Minecraft, Fabric Loader, and Fabric API versions;
- singleplayer, LAN, or dedicated-server context;
- installed mods, providers, and datapacks with versions;
- fresh world, existing chunk, or legacy-new chunk;
- short reproduction steps, expected result, actual result, and frequency; and
- one focused screenshot, exact message, command result, crash-report excerpt, or short log excerpt when
  safely available.

## Evidence and privacy

- Share only the relevant part of a log. Do not upload an entire log or arbitrary archive by default.
- Do not attach a whole world save, launcher profile, configuration folder, or credential file unless a
  maintainer specifically requests a safe follow-up after initial triage.
- Remove machine usernames, absolute home/profile paths, server addresses, private world/server names,
  account details, authentication data, and private chat from text and screenshots.
- Seeds and coordinates are optional. Share them only when you are comfortable doing so, preferably from
  a disposable test world.
