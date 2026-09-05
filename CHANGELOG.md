# Changelog

## Latitude 1.5.1-beta.4 (Minecraft 26.2)

- **Smoother transitions into the polar region.** Taiga now thins out in patches
  across the subpolar edge instead of disappearing along a straight latitude line.
  The polar tree line is preserved.
- This changes newly generated terrain. Existing chunks keep their current biomes.
- Edge storm warnings now say whether the storm is to the east or west.

## Latitude 1.5.1-beta.3 (Minecraft 26.2)

Bug fixes to biome placement and `/locate`, plus a way out of Latitude's world-creation screen for
players who want a Superflat, datapack, or other-mod world. **Beta:** shipped for testing before
the 1.5.1 stable tag.

### World creation

- **You can reach Minecraft's own world-creation screen again.** Latitude replaces the create-world
  screen, which left no route to Superflat, datapack-only worlds, or another mod's world type once
  Latitude was installed. A new **Other World Types & Datapacks…** entry in the Rules panel hands
  off to Minecraft's normal screen, carrying the world name and seed you already typed. Worlds made
  there are ordinary Minecraft worlds — Latitude does not offer its own preset on that screen, so
  there is no way to end up half in and half out.
- `Ctrl+Tab` now cycles the World/Settings tabs, and switching tabs no longer leaves a text field on
  the previous tab quietly holding your keystrokes.

### World generation

- **Oceans are the right depth again.** Outside the tropics, the first water off a beach could be
  labelled deep ocean, so shallow coastal water got deep-ocean fish, colour, and mob spawns. Every
  climate band now matches the water's real depth. This is the largest change in the release by
  area — tens of thousands of map cells per world — but it is a correction to labelling, not a
  reshaping of terrain. New terrain only.
- **Lush forests no longer grow inside the desert belt.** Randomness at the band boundary could push
  temperate forest as far equatorward as 31°, dropping green woodland islands into arid country.
  Temperate growth now stops at the true 35° line with a soft, natural-looking edge. New terrain only.
- **Desert riverbanks follow the water instead of printing blobs.** The test for "is there water
  nearby" sampled sixteen fixed points, which narrow streams slipped straight through — the bank
  right at the water's edge was the part most often missed. Banks now sweep outward properly and
  fade with distance instead of ending on a hard rim.
- **Rare biome slots can no longer come up empty without mods.** Two of the "accent" slots that add
  variety to the dry and tropical belts contained only modded biomes, so on a vanilla world that
  slot silently fell through to whatever the raw terrain suggested — which occasionally meant ice
  spikes or snowy plains at the equator. Both slots now have vanilla members.
- **Modded beaches stay beaches.** With biome packs installed, an unrecognised beach could be
  replaced by an inland biome — and in rare cases by a swamp or mangrove that bypassed every
  coastal check. Unrecognised beaches now fall back to the ordinary beach for that latitude.
  Affects modded worlds only.

### `/locate`

- **`/locate biome` puts you inside the biome, not on its edge.** The command accepted the nearest
  single matching cell, which at a thin boundary — swamp against mangrove, for instance — could
  leave you standing in the neighbouring biome. It now prefers a spot whose surroundings match, and
  will look moderately further to find one.

### Known issues

- **CliffTree and TerraBlender cannot currently be used together on Minecraft 26.2**, with or
  without Latitude. CliffTree adds a terrain rule when TerraBlender is present that Minecraft 26.2
  refuses to save, so the world crashes the moment it is first saved. This is not a Latitude bug —
  it reproduces on a plain Minecraft world type with Latitude generating nothing — but Latitude
  players hitting it will see it during world creation. Use one or the other until CliffTree or
  TerraBlender ships a fix.

## Latitude 1.5.1-beta.2 (Minecraft 26.2)

A focused follow-up to beta.1, adopting a set of fixes proven on the parallel 1.21.11 line and
verified against this line's own measurements. **Beta:** shipped for testing before the 1.5.1
stable tag.

### Structures and `/locate`

- **Structures are judged by their whole footprint, not a single center point.** A desert pyramid
  or outpost whose bounding box reached into badlands could previously generate half-in anyway;
  the entire footprint must now sit on legal ground. As deliberate design, no structure generates
  within 100 blocks of the world's east/west border danger zone.
- **`/locate structure` results now land on the actual structure.** The reported position is the
  generated structure's center rather than the pre-generation placement point, and teleporting to
  a buried structure (e.g. a desert pyramid) now surfaces you safely above it instead of inside it.

### World generation

- **Swamps now appear only in genuinely wet regions.** Previously "not explicitly dry" was enough,
  which let swamps form on medium-moisture land. Expect noticeably fewer, better-placed swamps
  (roughly a third fewer overall); mangroves are unaffected (they follow a separate coastal rule).
  Applies to newly generated terrain only.
- **Desert oases actually exist now.** The rare "desert surviving inside a wet region" feature was
  silently dead on most worlds — its coherence noise scaled with world size, so the whole map got
  about three coin-flips regardless of size. On some seeds this revives oases that never appeared;
  on others nothing changes. Applies everywhere, new terrain only.
- **Desert riverbanks can be green now.** Where an arid biome meets fresh water, the bank
  sometimes grows a strip of grass and wildflowers instead of running bare sand to the waterline.
  Deliberately not everywhere: long stretches stay bare, and which stretches green up is fixed by
  the world seed. The biome itself is unchanged — it is still desert, still spawns desert mobs,
  still admits desert structures; only the planting is different. New terrain only.
- **Badlands is now an earthlike-rare accent of the desert belt instead of a seed lottery.** The
  badlands-region noise had the same world-size disease as the oases above: a handful of giant
  cells per map, so some seeds handed badlands most of the arid belt (measured up to two-thirds,
  with desert squeezed to a sliver) while others got almost none. The region size is now capped
  and the coverage calibrated: on every measured seed badlands holds roughly 15% of the dry belt
  and desert is the staple, in coherent kilometer-scale mesas rather than one continent-sized
  sheet. Applies everywhere, new terrain only.

### World creation

- **Each climate zone now spawns you at the middle of its own band.** Most visibly: requesting a
  Subtropical start previously spawned you just across the line in Temperate, every time. All five
  zone starting latitudes shift slightly as a result (e.g. Tropical 18°→11.75°, Subpolar
  65.25°→58.25°) — each now the honest center of its zone.

## Latitude 1.5.1-beta.1 (Minecraft 26.2)

A fix-and-polish pass on top of 1.5.0, carrying forward fixes discovered while porting Latitude to
other Minecraft versions, a full worldgen variety pass, a compatibility fix affecting anyone
running TerraBlender, and a round of live-tested UI cleanup. **Beta:** shipped for testing before
the 1.5.1 stable tag. This update rounds out recent custom-biome integration work, but the sharpest
fix is for the vanilla fallback. Without custom biome packs, biome admission was badly skewed and savanna
was crowding the tropics, for one example.

### World generation

- **Windswept hills no longer dominate the polar band.** They were showing up snowless across the
  pole with green grass and foliage; now correctly restricted to genuine cold mountain
  terrain, and the pole reads more varied as a result.
- **Desert is the staple of the arid belt again.** Badlands had been crowding it out almost
  entirely. Badlands now generates as coherent regional patches inside a desert-dominant belt
  instead of everywhere.
- **Fixed bug where Savanna dominated the tropics.** It's now a coherent region with real borders, set
  inside a proper mix of forest and jungle, and still forms the natural transition band hugging
  every arid area.
- **Custom biomes: Muskeg, ice marsh, and glacier cliff were unplaceable in every world.** Fixed — a disagreement
  between two internal systems meant they were admitted but never actually reachable.
- **Wetlands could appear in bone-dry regions, and deserts in explicitly wet ones.** Both directions
  fixed.
- **Rivers and beaches can now come from installed biome packs**, not only vanilla.
- **`eroded_badlands` was structurally unplaceable in every world**, logging a coverage warning on
  every load. Fixed.
- **Redwood forest (a custom biome) now routes through the correct pool**, correcting a stale route
  from an earlier port.
- **Alpine terrain respects real snow footing**, and badlands structures keep their intended
  desolate look instead of getting dressed up by nearby biomes.
- **Custom-pack biomes decorate properly.** Biomes admitted only through a pack's provider ticket
  (not through Latitude's own band tags) could generate with no flowers, grass, or other decoration
  at all. Confirmed live with real Biomes O' Plenty/TerraBlender jars and fixed.
- **Dedicated/server-created worlds now place custom-pack biomes at all.** That capture previously
  only happened for worlds created through the in-game screen.
- **`savanna_plateau` no longer overrides a correct low-elevation biome choice.**
- **The bonus chest no longer spawns floating over water.**
- *Known trade-off:* since forest doesn't spawn villages (vanilla behavior), warm-belt village
  density drops somewhat now that savanna covers less ground. Villages remain common inside savanna
  regions themselves.

### Snow

- **Windswept hills/forest at cold-but-not-freezing latitudes were showing bare grey grass with no
  snow.** Three separate causes, all fixed, and windswept terrain now has its own lower snow line so
  it reads as snow-capped.
- **A leftover "orphaned" white-rimmed grass glitch** from snow removal is fixed.
- **The tree line and foliage limit are now correctly separate rules** — trees stop at 72° latitude,
  ground vegetation continues to the pole.
- *(Snow fixes only affect newly-generated terrain — already-explored chunks are unchanged.)*

### Sulfur caves

**Sulfur caves generated completely empty** — no ore, no decoration. Root-caused and fixed at every
layer, including a compatibility conflict with TerraBlender (pulled in by Biomes O' Plenty) that was
silently overriding Latitude's terrain rules. Fixing that also restored **badlands' proper
terracotta look** for anyone with TerraBlender installed — it had been silently using an old,
untuned vanilla appearance instead.

### World Creation screen

- **Cancel could accidentally select a climate zone instead of closing the screen**, at high GUI
  scale. Fixed.
- **Tighter margins and spacing**, and a visual divider between climate zone entries.
- **The second tab now correctly reads "Settings"** (was mislabeled "Rules").
- **A new title-intro animation** plays once per screen-open in the compact layout.
- **The loading screen no longer flashes vanilla's own screen first.**
- **The compass HUD no longer shifts position** when the location label changes length, and no
  longer renders outside a Latitude world.

### Compatibility

- **Latitude reliably recognizes its own worlds even with other world-gen mods installed**
  (confirmed with CliffTree). It previously could silently fall back to vanilla terrain while still
  showing Latitude's loading screen and HUD — now it either works correctly or fails loudly with a
  clear error.
- **Steep temperate terrain no longer gets flattened onto a plains-family biome** — the
  land-cohesion check that prevents this is now active rather than silently skipped.

### `/locate` and structures fixes

- **`/locate biome` can now find custom-pack biomes**, not just vanilla.
- **`/locate` now returns the actual nearest match**, not just the first one found along the search
  path.
- **Structures now generate using Latitude's own biome map.** This ensures that structures are placed
  in the correct biomes, even when custom biome packs are installed. This also fixes an issue where
  structures were not generating in some biomes.
- **New worlds' spawn point reliably stays inside your selected latitude.**

### Other

- Corrected license inconsistencies and confirmed alignment to GPL-3.0-or-later.
- Reduced unnecessary log-file bloat from the World Creation screen.

### Known issues

- A rare seam where a lush temperate biome can directly border an arid subtropical one (e.g. flower
  forest against badlands) at the exact latitude-band boundary. Deferred — a fix is designed for a
  future pass.
- Windswept hills are correctly restricted to cold mountains now, but are still fairly rare there
  (~3–6% of that terrain) — an open art-direction question, not a bug.
- Climate-zone rows on the World Creation screen have extra spacing at high GUI scale. Known,
  intentionally left for now.

## Latitude 1.5.0 (Minecraft 26.2)

**This is the first Latitude release published to the mod platforms since 1.3.** The 1.4 line was
tagged on GitHub but never published to Modrinth or CurseForge, so if you are coming from 1.3 this
release contains *two* full development cycles: the 1.4 "Cohesive Horizons" worldgen and
custom-biome overhaul, and the entire 1.5 polish campaign on top of it. The 1.4 notes are retained
below for reference; everything in them is included here.

Latitude 1.5 targets **Minecraft 26.2**.

### Before you update

- **Back up your worlds first.** If you are coming from 1.3 (Minecraft 1.21.11) or 1.4
  (Minecraft 26.1.2), Minecraft itself converts your save to the 26.2 format the first time you open
  it, and that conversion cannot be undone. Keep a copy of the save folder before you launch.
- **Existing worlds keep their original biome selection.** A world made before 1.5 stays pinned to the
  biome policy it was created under, including in chunks you have not visited yet. The rebuilt climate
  model, provider selection, and biome-diversity work below therefore apply to **newly created
  worlds**. This is deliberate: it stops terrain you have already explored from changing underneath you.
- **Surface-level features are not pinned that way.** The tree line and alpine snow caps below are
  applied as terrain is generated, so they *will* appear in newly generated chunks of an existing
  world. Already-generated chunks are untouched, which means an older save can show a visible seam
  where forested summits meet the new bare-and-snowcapped ones.

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

### Mountains feel like mountains

New in this release and on by default: a real tree line, and snow-capped peaks whose snowline follows
latitude.

- **Forests now thin out with altitude instead of climbing to the summit.** Trees start dropping away
  around Y140 and stop entirely by Y168, so the tree line arrives as a gradual thinning rather than a
  hard edge.
- **Above the line, ground gives way to exposed alpine rock**, with a fading shelf of meadow just above
  it so the change does not read as a painted stripe.
- **Higher still, peaks wear snow — and where snow begins depends on your latitude**, shaped after
  Earth's real climatic snowline, which sits lowest near the poles and rises toward the dry subtropics:

  | Band | Snow begins |
  | --- | --- |
  | Polar | essentially all high terrain |
  | Subpolar | low snowline, near-full alpine cover |
  | Temperate | on the peaks |
  | Subtropical | only the highest summits |
  | Tropical | none — no equatorial glaciers |

- **The snowline is noise-warped rather than a flat contour**, so caps sit unevenly along a ridge the
  way real snow does instead of cutting every peak at the same height.
- **Nothing pokes through the caps.** The snow zone deliberately carries no grass, so you will not get
  flowers and tufts standing up through a snowfield.

### Custom biome support (Biomes O' Plenty, Terralith, and friends)

- Custom biomes from other mods and datapacks can be slotted into the latitude bands through the
  `globe:lat_*` biome tags, with an admission safety rail so climate-incompatible biomes cannot leak
  into the wrong band — anything not admitted falls back to a sensible vanilla biome.
- **Provider selection was rebuilt in 1.5.** Selection had effectively been driven by a fixed seed and
  a single tag-size-weighted roll, which let large packs crowd out whole provider families and made
  different worlds repeat the same choices. Latitude now picks a coherent provider namespace first,
  then an exact biome within it, using the live world seed — so provider choice no longer depends on
  how many biomes a provider happens to contribute.
- In sampled worlds with both packs installed, land share came out roughly a fifth Biomes O' Plenty,
  a quarter to a third Terralith, and about half vanilla, with dozens of distinct custom biomes
  present. Terralith's orchid swamp, amethyst canyon, amethyst rainforest and tropical jungle, and
  BoP's marsh, wetland and tropics all appear in their intended latitude families, and temperate
  forest went from a near-monoculture to a genuine mix. Exact shares vary by seed, world size and
  which packs you have installed.
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
