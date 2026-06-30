# Changelog

## Latitude 1.4 — Cohesive Horizons (MC 26.1.2)

Latitude 1.4 "Cohesive Horizons" is a worldgen-quality and compatibility release. It makes the climate map read like a coherent, Earth-like world and adds first-class support for custom biome mods.

### Custom biome support
- Custom biomes from other mods and datapacks (Biomes O' Plenty, Terralith, Promenade, and similar) can now be slotted into the latitude bands through the `globe:lat_*` biome tags.
- Added a custom-biome admission safety rail so unknown or climate-incompatible biomes cannot leak into the wrong band; anything not admitted falls back to a sensible vanilla biome for that band.
- Placed Promenade's Glacarian Taiga in the subpolar band and its Blush/Cotton Sakura Groves in the temperate band, as accent biomes that form coherent patches in their climate zone (all marked optional, so they're simply skipped when Promenade isn't installed).
- Made the custom-biome source wrapping more robust on the 26.1.2 stack: corrected the biome-source hook so structure and surface placement follow the latitude biome map, and deferred wrapping safely when a source mod's biome registry isn't ready yet (fixes a class of world-load crashes with source-side biome mods).

### Worldgen rebalance
- **Fixed "overwhelming desert in the tropics."** The warm-side moisture model was latitude-independent, so dry/desert pockets scattered uniformly across every warm latitude — including the equator. A new Earth-analog latitude wet-bias keeps the equatorial belt humid (rainforest/ITCZ) and pushes arid country out to the subtropics where it belongs, grading into a believable jungle→savanna→desert transition toward the poleward edge.
- **Kept the equator varied, not a jungle monoculture.** Equatorial humidity is balanced so the rainforest belt stays *diverse* — a jungle-dominant mix of jungle, bamboo, sparse-jungle clearings, savanna clearings, tropical wetlands, and occasional desert pockets — instead of an endless wall of jungle. Custom tropical biomes from installed mods (e.g. Biomes O' Plenty) are preserved in the humid belt rather than being overwritten with vanilla jungle, so the equator reads richly whether or not you run biome add-ons.
- **Greatly reduced biome "confetti."** Raised the tier-selection coherence wavelength so secondary/accent biomes (old-growth taiga, sparse jungle, savanna, and similar) form coherent patches instead of single-cell speckles sprinkled through the dominant biome. Across validation seeds, jungle small-fragment share dropped from roughly a quarter to under a tenth, and savanna/desert/taiga fragment counts fell by half or more.
- **Reduced cross-province bleed** — jungle blobs marooned in desert and desert specks inside jungle are substantially reduced.
- **Restored a believable arid mix.** Both desert and badlands remain visibly present in the arid belt (with coherent wooded/eroded badlands sub-regions rather than scattered specks), avoiding the earlier over-correction that thinned them out.
- **No badlands or deserts anywhere in the tropics (geography law).** Badlands/mesa and hot desert are subtropical landforms on Earth, never tropical ones. Both are now fully excluded across the entire tropical band (0–23.5°) — replaced there by savanna — and phase back in across the lower subtropics. (Strengthens the earlier deep-equator-only thinning into a clean band-wide rule; the boundary is noise-warped, not a hard line.)

### Worldgen refinements (1.4.1, 2026-06-23)
- **Snow on the mountains, where it belongs.** The alpine snow line was set too high to ever catch the peaks that actually generate, so temperate/subtropical summits stayed bare. The snow-cap onset is lowered and shaped after Earth's real snowline (lowest toward the poles, highest in the dry subtropics), so cold-band peaks cap fully and temperate peaks get snow well down the slope — while the tropics stay snow-free and warm-latitude snow creep remains impossible.
- **No more grass poking through alpine snow.** The snowy alpine zone is now bare snow/stone; the grassy meadow shelf stays strictly below the snow line.
- **Less polar sameness.** The poles were dominated by snowy plains and ice spikes; the polar pool now draws on more cold biomes (snowy taiga, grove, and installed-pack cold variants such as Terralith's snowy shield / siberian taiga / wintry forests), so ice spikes drop to a rare accent and the cap reads with real variety.
- **A varied savanna belt.** The subtropical dry belt was a savanna monoculture; warm picks from installed packs are now preserved instead of being flattened to vanilla savanna, so the belt reads as a mix of savanna, prairie, and other warm grasslands.
- **Mushroom islands only in open ocean.** Mushroom fields can no longer splotch onto inland deep-water pockets on high terrain — they appear only in genuine open ocean.
- **More compass themes.** Six new analog compass colour schemes (Obsidian & Red, Arctic Blue, Emerald, Royal Purple, Sunset, Monochrome) are selectable in the HUD Studio; the default is unchanged.
- **A proper, Earth-like arid belt.** The subtropical desert/badlands belt previously filled in only across a thin 30–35° sliver, with savanna dominating even at its peak. It now phases in from ~26° and reads as a continuous, genuinely arid belt across roughly 26–35° (savanna remains present at the equatorward fringe — this isn't a desert monoculture).
- **No badlands, desert, frozen rivers, or ice spires leaking into the temperate band.** A latitude-blending edge case could place small patches of subtropical-arid terrain, or subpolar frozen rivers (with their associated ice-spire structures), noticeably out of their home band. Both are now clamped to their true latitude.
- **Plains stay flat.** Plains was occasionally generating on dramatic, steep terrain (it should always be a relatively flat biome). Gently rolling plains are unaffected; only genuinely rugged or high columns reroute to a more terrain-appropriate biome (hills, peaks, meadow).

### World shape
- **Mercator world type.** New worlds are wider — roughly twice as wide east-west as they are tall north-south — giving every latitude band more east-west room and more biome variety per band. Existing worlds are completely unaffected.

### World creation
- **Fixed the bonus chest.** The bonus-chest option could silently fail to generate a chest in your new world; it now works correctly.
- **Added a "Generate Structures" toggle** to the create-world screen, alongside the existing options (defaults on, matching vanilla).
- Renamed "Expedition" to "World" throughout the create-world screen for clarity.

### World entry & interface
- **Smoother first entry into a new world.** The bespoke loading screen now stays up until the world around you has actually finished rendering, so you no longer drop into a half-loaded or empty-looking frame for a moment when entering. (Held until the spawn chunks are loaded and the surrounding terrain is compiled and visible, with a safety timeout.)
- **Latitude-aware initial spawn.** New worlds place your starting spawn in a latitude-appropriate zone before terrain pregeneration runs, and keep spawn clear of the east/west world-edge warning band.
- **Fixed zone and hemisphere titles.** The on-screen zone/hemisphere announcement labels are now measured from the world's equator (the world-border center) instead of a fixed line — correcting a case where the hemisphere readout could be inverted or offset — and no longer fire spuriously when you teleport a long distance.

### Validation
- Atlas biome-preview balance audited across multiple seeds and world sizes (small and regular) with a band-aware balance analyzer (`tools/atlas/band_balance_analyze.py`).

### Known limitations
- **With several custom biome packs installed at once, not every biome will appear.** Each latitude band draws from a finite weighted pool, so the more biomes you add, the smaller each one's share — rarer/accent biomes from large stacks can fall below visible frequency. This is expected behavior, not a bug; you'll still get a coherent, climate-appropriate mix, just not 100% coverage of every biome in every installed pack. (Configurable per-pack representation weighting is on the roadmap.)

## Historical released entries
Entries below are retained for already-published or older-version lines. They are not the active `1.4.1-beta.2+26.1.2` candidate gate.

## Latitude 1.3.0+1.20.1-r1 (MC 1.20.1)
- Hotfix release for the 1.20.1 startup/refmap crash tracked in issue #5 and PR #6.
- Corrects the 1.20.1 mixin/refmap surface so `globe.mixins.json` uses `latitude-refmap.json` and Java 17 compatible mixin initialization.
- Removes the release-jar dependency on excluded warm-snow debug stats, and keeps the early initial-spawn radius tied to the new world's generator instead of stale prior-world state.
- Supersedes the deprecated `1.3.0+1.20.1` upload; `1.3.0+1.20.1-r1` is the fixed public 1.20.1 build.

## Latitude 1.3.0 (MC 1.21.11)
Latitude 1.3.0 is a major upgrade from the 1.2.x line. It makes the world read more like a coherent climate system, while also improving stability and the first-run player experience.

### Major worldgen improvements
- Reworked the climate-band model and related biome policy so latitude structure reads more clearly and more naturally.
- Reduced scattered confetti biome placement and improved biome contiguity across the world.
- Strengthened regional biome identity so key biomes feel more intentional and less thin, noisy, or patchy.
- Improved high-value cases like badlands and Pale Garden so they form more convincingly as real places.
- Restored missing or underrepresented biome outcomes in the regions where players would expect to find them.
- Smoothed climate handoff behavior so transitions feel less arbitrary and less visibly synthetic.

### Player-facing improvements
- Refined the create-world and loading flow so starting a world feels more intentional and less abrupt.
- Fixed the excessively long first-time world creation/loading delay from the 1.2.x line, so first entry now feels much closer to normal vanilla startup.
- Improved first-entry behavior after world creation and after upgrading an existing save.
- Improved the first-run experience so the mod feels smoother and more finished from the moment you create or open a world.

### Existing-world compatibility
- Existing 1.2.x worlds keep their legacy worldgen policy when opened in 1.3.0.
- The upgraded-save crash caused by a missing `worldgen_policy` bootstrap path was fixed.

### Validation
- Structural audits passed.
- Invariant scan passed.
- Release integrity checks passed.
- Live upgrade and save smoke checks passed.

## Latitude 1.2.5 (MC 1.21.x)
- Broadened declared compatibility to cover MC 1.21.0–1.21.11.
- Two jars provided: one for 1.21.0–1.21.3, one for 1.21.10–1.21.11.
- Hardened fog mixins with require=0 for cross-version stability on the compat jar.
- No gameplay or worldgen changes from 1.2.4.

## Latitude 1.2.4 (MC 1.21.11)
- EW storm intensity ramp tightened (shader-friendly haze works with Sodium + Iris) for a stronger wall near EW borders.
- Warm-band cold-biome clamp prevents snow/ice leakage in Equator/Tropics/Arid bands.
- HUD/overlay ordering preserved so warnings stay readable under the haze.
