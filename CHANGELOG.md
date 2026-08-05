# Changelog

## Latitude 1.5.0 (Minecraft 26.2)

**This is the first Latitude release published to the mod platforms since 1.3.** The 1.4 line was
tagged on GitHub but never published to Modrinth or CurseForge, so if you are coming from 1.3 this
release contains *two* full development cycles: the 1.4 "Cohesive Horizons" worldgen and
custom-biome overhaul, and the entire 1.5 polish campaign on top of it. The 1.4 notes are retained
below for reference; everything in them is included here.

Latitude 1.5 targets **Minecraft 26.2**.

### The climate map reads like a real world now

This is the headline of the 1.3 → 1.5 span, and it is not a tuning pass — it is a rebuilt model of
how climate is decided.

- **Deserts no longer swallow the tropics.** The warm-side moisture model was latitude-independent, so
  arid pockets scattered evenly across every warm latitude, equator included. An Earth-analog latitude
  wet-bias now keeps the equatorial belt humid and pushes arid country out to the subtropics where it
  belongs, grading through a believable jungle → savanna → desert transition.
- **The equator stayed varied instead of becoming a jungle wall.** Equatorial humidity is balanced so
  the rainforest belt reads as a mix — jungle, bamboo, sparse-jungle clearings, savanna clearings,
  tropical wetlands, occasional desert pockets — not a monoculture.
- **Biome "confetti" is largely gone.** The tier-selection coherence wavelength was raised so accent
  biomes form real patches instead of single-cell speckles. Across validation seeds, jungle
  small-fragment share fell from roughly a quarter to under a tenth, and savanna/desert/taiga fragment
  counts halved or better.
- **Badlands left the equator.** Mesa is a subtropical landform on Earth, never an equatorial one; it
  now fades in toward the subtropics where it belongs, with savanna clearings taking its place at the
  deep equator. The subtropical arid belt is untouched.
- **Cross-province bleed reduced** — jungles marooned in desert, desert specks inside jungle.
- **Temperate boundaries and coastal beaches** tightened, mountain-class beach ridges rejected, and
  windswept temperate mountains restored after a regression.
- **Pale Garden** is consolidated into one coherent, reachable inland region rather than fragments.
- **Mushroom Fields is real land.** A biome label could previously reserve open water without ever
  materializing its planned island terrain; placement now reaches the actual chunk-writing path.
- **Cave representation is guaranteed** rather than incidental, without unsafe surface expression.
- **Meadow** no longer leaks into the lowland fallback, and flat wetlands stay off mountain terrain.

### Custom biome support (Biomes O' Plenty, Terralith, and friends)

- Custom biomes from other mods and datapacks can be slotted into the latitude bands through the
  `globe:lat_*` biome tags, with an admission safety rail so climate-incompatible biomes cannot leak
  into the wrong band — anything not admitted falls back to a sensible vanilla biome.
- **Provider selection was rebuilt in 1.5.** Selection had effectively been driven by a fixed seed and
  a single tag-size-weighted roll, which let large packs crowd out whole provider families and made
  different worlds repeat the same choices. Latitude now picks a coherent provider namespace first,
  then an exact biome within it, using the live world seed — so provider choice no longer depends on
  how many biomes a provider happens to contribute.
- Measured across two seeds at ~392,000 exact biome samples per map: with both packs installed, land
  share came out roughly 22% Biomes O' Plenty / 26–30% Terralith / ~50% vanilla, with 49 distinct
  custom biome IDs present per seed. Terralith's orchid swamp, amethyst canyon, amethyst rainforest
  and tropical jungle, and BoP's marsh, wetland and tropics all appear in their intended latitude
  families. Temperate seasonal/maple forest dropped from a reported ~45% near-monoculture to 20–30%.
- A dedicated **temperate wetland family** was added; BoP marsh/wetland and Terralith orchid swamp are
  admitted only under the flat-wetland terrain law, while warm mangroves keep the coastal/brackish rule.
- Promenade's Glacarian Taiga sits in the subpolar band and its Blush/Cotton Sakura Groves in the
  temperate band, as optional accents simply skipped when Promenade isn't installed.
- Biome-source wrapping was made robust: structure and surface placement follow the latitude biome map,
  and wrapping defers safely when a source mod's biome registry isn't ready yet — fixing a class of
  world-load crashes with source-side biome mods.

### The poles feel like the poles

- Polar blindness was replaced with a **whiteout fog** treatment — readable as weather rather than as a
  broken screen.
- Final-zone frost damage was steadied, and polar foliage is suppressed beyond 80°.
- Polar warnings and zone titles were reworked, including a sand-haze tint correction.

### World edges

- East/west edge presentation was smoothed, with a storm advisory aligned to the actual particle onset
  and a restored escalation curve as you approach.
- The advisory now reads **"Storms ahead. Low visibility; consider turning back."** — it previously
  claimed every polar edge storm was a sandstorm.

### Compass HUD and HUD Studio

- **HUD Studio** consolidates HUD customization into one tabbed screen without restricting the preview
  canvas. The title, unattached compass, and detached biome/zone readout are all draggable.
- Title dragging honours the selected placement mode live: SNAP quantizes to the 8-pixel grid, FREE is
  pixel-by-pixel, and there is no jump on release, reopen, or config reload.
- New **biome and zone location detail** readout, with independent text scaling from 50% to 125% in 5%
  steps, separate from compass size.
- The HUD Studio preview can show which mod a biome came from.
- Six append-only analog compass themes.

### Create-world screen

- Rebuilt around a **square Atlas preview** (the circular disc, the "ATLAS" caption and the degree
  gutter are gone).
- Smaller worlds draw a constant-size Regular-world reference underlay; larger worlds show a darkened
  Regular reference inset, so world sizes are comparable at a glance.
- The Atlas stays centred and does not shift when the compact-world disclaimer appears — the
  disclaimer now sits below the world-size controls.
- Enlarged, letter-spaced Latitude title; gold climate heading with a divider; a version label in the
  lower-right; and a responsive layout that switches to tabs at high GUI scales or narrow panes.
- "Expedition" wording is now "World"; bonus chest and Generate Structures states report truthfully.

### Operator commands (new)

Latitude now ships a small set of operator commands in the public build, rooted at **`/latitude`** and
requiring operator permission:

- `/latitude here` — latitude, band, terrain and biome readout at your position
- `/latitude explainHere` — why this biome was chosen here
- `/latitude probe <radius> <samples>` — sample nearby biome and band distribution
- `/latitude tpLat <deg> [x]` — teleport to a signed latitude
- `/latitude tpBand <band> [edge]` — teleport to a latitude band
- `/latitude flyspeed <1-5>`, `/latitude help`

These are inspection and navigation tools only. Latitude's development tooling — session recording,
screenshot capture, world export, seam auditing, chunk pregeneration, and every automatic harness — is
excluded from public builds by policy and cannot be reached from a release jar.

### Villages and structures

- Villages are rejected from climate-mismatched starts, and polar village "ghosts" are prevented.
- Villages are allowed through 80° where appropriate rather than blanket-banned.

### Performance

- Biome work is cached per column; constant IDs and launch flags are cached rather than re-resolved.
- Analog compass disc spans are batched.
- `/locate` is bounded and responsive, including searches that find nothing.
- Sodium fog-culling reachability restored, and the 26.2 port tolerates Sodium's removed visibility hook.

### World entry

- The bespoke loading screen holds until the world around you has actually finished rendering, so you
  no longer drop into a half-loaded frame.
- New worlds place your spawn in a latitude-appropriate zone before pregeneration, and keep spawn out
  of the east/west edge warning band.
- Zone and hemisphere titles are measured from the world's equator rather than a fixed line, fixing an
  inverted or offset hemisphere readout, and no longer fire spuriously after a long teleport.

### Existing worlds

Existing Latitude worlds load and continue generating without rewriting already-generated chunks.
Worlds created before 1.3 keep their legacy worldgen policy when opened.

### Known limitations

- **With large custom biome packs installed, not every biome will appear.** Each latitude band draws
  from a finite weighted pool, so the more biomes you add, the smaller each one's share — rarer accent
  biomes from big stacks can fall below visible frequency. 1.5 substantially improves *which* biomes
  appear and stops whole providers being crowded out, but it does not make coverage complete. This is
  expected behaviour, not a bug. Per-pack representation weighting is on the roadmap.

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
- **No more badlands at the equator.** Badlands/mesa is a subtropical landform on Earth, never an equatorial one, but on some seeds it was leaking into the deep tropics. It's now gated out of the equatorial belt (smoothly fading in toward the subtropics where it belongs) and replaced there by savanna clearings, while the subtropical arid belt is left exactly as-is.
- **Thinned desert at the deep equator.** True hot desert is essentially absent from Earth's rainforest equator, so the innermost tropics (0–10°) now carry noticeably less desert — partially replaced by savanna clearings — fading back to the full subtropical desert belt by ~12°. Desert is thinned, not removed: it stays present and is untouched everywhere outside the deep equator.

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
