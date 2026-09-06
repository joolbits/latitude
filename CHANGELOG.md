# Changelog

## Latitude 1.5.1-beta.5 (Minecraft 1.21.11)

A small follow-up to Beta 4 that brings the create-world screen work from the Minecraft 26.3 port
to this line. **Beta:** shipped for testing before the 1.5.1 stable tag.

### New

- **A still background for the create-world screen.** The **Still** toggle at the left of the
  bottom button row on Latitude's create-world screen swaps the moving panorama for a flat dark
  backdrop, and the choice is remembered between sessions.

### Improved

- **Create-world screen layout.** Panels sit tighter with less empty space, the World and Settings
  tab headers are now real buttons you can reach and activate from the keyboard (and Minecraft's
  narrator announces them), and panel backgrounds are slightly see-through so the panorama behind
  shows through.

### Fixed

- **The sources jar no longer carries a development-only helper.** Beta 4's `-sources.jar` on this
  line included one clipboard helper that the main jar correctly left out; the packaging rule now
  covers both jars.

Nothing in this release changes world generation.

## Latitude 1.5.1-beta.4 (Minecraft 1.21.11)

- **Smoother transitions into the polar region.** Taiga now thins out in patches
  across the subpolar edge instead of disappearing along a straight latitude line.
  The polar tree line is preserved.
- This changes newly generated terrain. Existing chunks keep their current biomes.
- Edge storm warnings now say whether the storm is to the east or west.
- **Less repeat work while exploring new terrain.** Latitude no longer repeats a
  custom-biome scan that was only needed for disabled diagnostics during chunk decoration.

## Latitude 1.5.1 Beta 3 (Minecraft 1.21.11)

A focused follow-up to Beta 1, bringing this line level with the 26.2 line's Beta 2 and then
past it. Most of that release was adopted *from* this line and was already here; these are the
parts that were not, plus the fixes that came out of live testing afterwards.

*There is no Beta 2 on this Minecraft version. This work was prepared under that number but
never released; it ships as Beta 3 so that a given Latitude version number means the same
thing on every Minecraft version it is published for.*

### World generation

- **Trees no longer half-appear at the pole.** The far polar cap turns away biomes that grow trees,
  but it recognised them by *name* — an explicit list, plus anything called "forest", "taiga" or
  "grove". A fir biome whose name says "clearing" walked straight through, generated in the cap,
  and then had its trees stripped by a later check: grass, ferns and mushrooms standing in a
  snowfield with no trees at all. The cap now also works out whether a biome grows trees by looking
  at what it actually plants, rather than what it is called. Biomes that belong at the pole and
  happen to place a token spruce — Snowy Plains and Ice Spikes among them — keep their place, as do
  the tundra-family biomes ruled in earlier, and frozen oceans and rivers. Found in live testing at
  79° north.
- **Nothing is left floating in mid-air any more.** Where the pole removed a fallen log, whatever
  that log was carrying stayed exactly where it was, hanging unsupported — most visibly mushrooms.
  Anything that needs a floor beneath it is now refused when its floor has been taken away. Plants
  that legitimately attach to walls and ceilings, like glow lichen, are untouched, because open air
  below those is normal. Found in the same live test.
- **Fresh worlds nearly always get their reserved swamp now.** Latitude sets aside a patch of the
  map for each of a handful of vanilla biomes, so a new world is meant to contain one of each. The
  swamp reservation asked for a patch 224 blocks across and required swamp conditions to hold at
  its centre *and* at four points spread right across that width at once — which swamp's
  quick-changing, patchy ground rarely manages. It now asks for a patch sized to fit what swamp
  actually looks like. Measured over twelve worlds it went from failing on 3 to failing on none;
  measured again over forty fresh worlds it failed on 1 of 40. A large improvement rather than a
  cure. Note that **swamps were never missing from worlds** — ordinary swamp generation was
  untouched throughout. What was at stake is the guaranteed reserved province, plus two error lines
  in the log at world creation.

*The percentages below, like Beta 1's, were measured with **no biome packs installed**. With Biomes
O' Plenty, Terralith or similar, those packs supply many of the biomes and the shares will differ.*

- **Badlands is no longer a seed lottery.** The noise that decides where mesa country sits scaled with
  world size, so the whole map got about seven coin-flips no matter how big it was — one unlucky roll
  could hand most of the dry belt to badlands and squeeze desert almost out of existence. Measured on
  this line across twelve seeds, badlands covered anywhere from 24% to 71% of the dry belt. It now
  covers 11% to 30%, in many more, smaller regions. Badlands reads as rare country inside a
  desert-dominant belt, which is how it looks on Earth.
- **Autumn forest no longer wanders into the desert belt.** The soft, wobbled band boundaries could
  promote a spot deep inside the 23.5–35° dry belt (observed at 30–33°N) to the temperate biome
  pool — which is full of maple and autumn forests — planting lush forest islands in open desert.
  A third true-latitude clamp now mirrors the two that already guard the other directions: full
  temperate eligibility from 35.5° poleward, a soft thinning ramp across 34.5–35.5°, and never
  equatorward of 34.5°. The dry belt keeps its own biomes to its true edge; autumn country starts
  where it should.
- **Desert riverbank greenery hugs the water now.** The green banks from earlier in Beta 2 could sit
  up to four blocks above the waterline and their lush/bare rhythm was fine-grained enough to read
  as speckle, with every desert pond seeding its own patch. Banks now stay within two blocks of
  water level, and the verdant stretches are longer, rarer, and more deliberate — lush reaches
  along a river with honest bare desert between them.
- **A green reach is a bank, not a row of dots.** Deciding whether a spot was near water meant
  feeling around at sixteen fixed points, a stencil with holes wide enough that ground seven blocks
  from a river could read as dry — so even a stretch marked verdant came out as scattered blobs.
  The check now sweeps outward from the spot and stops at the water it finds, so it knows the real
  distance to the bank, and greenery thins out smoothly as it climbs away from the water instead of
  ending at a hard rim. Same reaches, same rhythm of lush and bare; each reach now reads as a
  continuous, soft-edged ribbon along the water.
- **With biome packs installed, the shoreline stays a shoreline.** When Biomes O' Plenty, Terralith
  or similar are installed, a safety net reroutes unrecognized pack biomes into the climate band's
  ordinary land pool. That reroute could land on biomes that carry their own strict placement rules —
  it painted landlocked Mangrove Swamp onto cliffed beaches at 27°N — outside mangrove's real
  placement window (within 25 degrees of the equator, near open ocean, at sea level). Rerouted beach columns now stay beaches, and the
  reroute can never hand out mangrove or swamp at all; those only appear through their own validated
  placement. Found in live testing with the full pack set.
- **Deep ocean no longer starts at the beach.** Outside the tropics, the ocean's identity (ocean vs
  deep ocean, and their pack variants) was rolled by map noise with no regard for how deep the water
  actually is, so the first water block off a coast could be labeled Deep Ocean. The tropics already
  respected true depth; every other band now does too: shallow shelf water gets shallow identities,
  true deep water gets the deep ones.
- **Loading the Nether can no longer briefly disarm world generation.** An ordering quirk at world
  load let the Nether's own load step clear the Globe overworld's generation authority for a moment;
  chunks generated in exactly that window would silently fall back to vanilla painting. The window is
  closed.
- **Desert riverbanks can be green now.** Where an arid biome meets fresh water, the bank sometimes
  grows a strip of grass and wildflowers instead of running bare sand to the waterline. Deliberately
  not everywhere: long stretches stay bare, and which stretches green up is fixed by the world seed.
  The biome itself is unchanged — still desert, still desert mobs, still desert structures; only the
  planting is different. New terrain only.

### World creation

- **Creating a world no longer crashes.** Latitude picks your starting point by testing candidate
  spots inside the climate zone you chose. When none of them passed, it gave up by crashing at the
  Create World screen. Measured across 240 fresh worlds — 40 seeds in each of the six climate zones
  — that happened on 41 of them, about one world in six. It was worst in Polar (12 of 40) and
  mildest in Tropical (2 of 40), and 25 of the 40 seeds hit it in at least one zone. Removing the
  biome packs did not change the rate, so this was Latitude's own, not a pack interaction. When the
  search comes up empty it now hands the job back to Minecraft's ordinary safe-spawn picker and
  notes it in the log, instead of failing. Re-measured on the same 240 worlds: all of them create,
  and the hand-off fired on exactly the 41 worlds that used to crash and on none of the other 199 —
  so the new path is demonstrably doing the work, rather than the crash merely not reproducing. A
  further 40 fresh worlds in the Polar zone on the final build: no crashes. The trade is that on
  those seeds you may start a little off the exact middle of your band, which is recoverable in a
  way a crash is not.

- **You can reach Minecraft's own world-creation screen again.** The Rules panel has a new
  "Other World Types & Datapacks..." option that opens Minecraft's full setup, so modded world
  types, Superflat customization, and datapack selection are available from inside Latitude. The
  world name and seed you have already typed carry across, so nothing is retyped. That screen runs
  as plain Minecraft — the Globe world type is deliberately not offered there, because a Latitude
  world made on that screen would never have been asked for Latitude's settings. Creating an
  ordinary Latitude world is unchanged. Ported from the 2.0 line (PR #21, addressing issue #19).
- **Ctrl+Tab moves between panels** on the narrow (tabbed) create screen, and switching panels no
  longer leaves keyboard focus on a control the old panel owned.

- **Each climate zone spawns you at the middle of its own band.** This line already had the fix; Beta 2
  adds the regression test that pins it, with every zone's target latitude recomputed from this line's
  own band boundaries.

### Known limitations

- **The reserved-biome system can still miss.** Across forty fresh worlds on the final build, 3 came
  out with at least one biome failing to claim its reserved patch: one lost swamp, and two lost
  members of the mountain/windswept family. An earlier twelve-world run instead showed Windswept
  Savanna and Savanna Plateau. So this is not specific to swamp or to one family — it is a general
  weakness in how a reserved patch is tested, and sizing the patch better only reduces how often it
  bites. As with swamp, **the biomes themselves generate normally**; it is the reservation that
  fails, and the visible symptom is error lines in the log rather than anything missing in play. The
  proper repair is to test the ground proportionally over many points, the way the sibling
  water-coverage code already does, instead of demanding four spread-out points all pass at once.
  That is planned rather than done.

- The badlands rebalance moves a lot on seeds that previously rolled badlands-heavy. On the reference
  seed, desert went from about 12.5% to about 27.5% of subtropical land and badlands from about 23% to
  about 8%. The point of the change is that the seed-to-seed swing collapses, not the share on any one
  seed — but if badlands reads too rare for your taste in play, say so and it is a one-value tune.
- Green banks are new and their density is a first-pass value. They have not yet been tuned against
  live play on this line.

## Latitude 1.5.1 Beta 1 (Minecraft 1.21.11)

This is a beta release. Back up important worlds before testing, and use the optional retrofit command
only on a disposable copy or a world with a current backup.

**The first Latitude release on this Minecraft version since 1.3.** Latitude 1.3.0 was published on
1.21.11; the 1.4 "Cohesive Horizons" worldgen and custom-biome overhaul that followed it was only
ever published on Minecraft 26.1.2, never on this line. If you're coming from 1.3.0 here, this single
release carries *two* full
development cycles at once — 1.4's worldgen rebuild and the entire 1.5 polish campaign on top of it.

Latitude 1.5 targets **Minecraft 1.21.11**, the same Minecraft version as 1.3.0.

### Before you update

- **This is a same-Minecraft-version upgrade.** 1.3.0 and 1.5.1 Beta 1 both target 1.21.11, so there is no
  save-format conversion to cross going from one to the other — unlike jumping here from an older
  Minecraft version.
- **Terrain you have already explored will not change.** Already-generated chunks are never rewritten,
  and an existing world keeps the custom-biome roster and world-generation policy it was created with.
- **But newly generated chunks in an existing world DO get this release's placement fixes.** That is
  deliberate (maintainer ruling, 2026-08-18): a legality fix applies to all new terrain rather than
  being frozen out of older saves. The practical effect is a seam at the frontier of where you have
  explored — beyond it, windswept, desert and savanna follow the new rules. To see this release as
  intended end to end, make a new world.

### The climate map reads like a real world now

This is the headline of the 1.3 → 1.5 span, and it is not a tuning pass — it is a rebuilt model of
how climate is decided.

*About the percentages below:* they were measured on a Regular world with **no biome packs
installed**, sampling roughly 98,000 columns on one seed. They describe how Latitude's own model
behaves in isolation. With Biomes O' Plenty, Terralith or similar installed, those packs supply many
of the biomes and the shares will differ — often substantially. The direction of every change holds
either way; the exact numbers are vanilla-only.

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
  deep equator. The subtropical arid belt's own desert/badlands/savanna mix changed too — covered
  just below.
- **Desert is the staple of the arid belt again.** Badlands had been crowding it out — this line
  measured almost nine badlands patches for every desert patch in the subtropics. The pick order now
  favors desert first, with badlands generating as coherent regional patches inside a desert-dominant
  belt rather than spreading everywhere: subtropical badlands-to-desert eased from about 8.8:1 to
  under 1.9:1, and subtropical desert coverage more than tripled, from about 3.5% to about 12.5% of
  subtropical land.
- **Savanna is a place again, not the default fallback.** It now forms its own bordered regions across
  the warm belt, plus a dry fringe hugging every arid province — the transition between desert and
  forest, the way it works on Earth — instead of standing in wherever dry-warm terrain used to fall
  back to it. Its share eased from about 48% to about 38% of subtropical land, and from about 54% to
  about 30% of tropical land, while ordinary tropical forest — previously almost absent — now covers
  close to a quarter of tropical land. Jungle itself is unchanged.
- **Eroded badlands can actually generate now.** It was structurally unplaceable before this release —
  the game would select it, fail to place it, and log a warning on every world load. That's fixed.
- **Cross-province bleed reduced** — jungles marooned in desert, desert specks inside jungle.
- **Temperate boundaries and coastal beaches** tightened, and mountain-class beach ridges rejected.
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
  then an exact biome within it, using the live world seed. This reduces crowding from large packs, but
  does not guarantee that every installed provider or biome appears in every world.
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
- **TerraBlender compatibility.** TerraBlender — installed automatically as a dependency of Biomes O'
  Plenty — was silently overriding Latitude's own surface painting, so badlands rendered with
  TerraBlender's untuned look instead of Latitude's terracotta whenever it was present. Latitude's
  surface rules now take precedence again.

### The poles feel like the poles

- Polar blindness was replaced with a **whiteout fog** treatment — readable as weather rather than as a
  broken screen.
- Final-zone frost damage was steadied, and polar foliage is suppressed beyond 80°.
- Polar warnings and zone titles were reworked, including a sand-haze tint correction.
- **Windswept hills no longer cover the pole.** Windswept hills, forest, and gravelly mountains are
  confined to subpolar mountains where they belong, instead of spreading across the true polar band as
  flat, snowless grey-green terrain — measured at roughly 15% of polar land before this fix, and
  effectively none after. Trees and other woody plant growth are also now blocked at or above the tree
  line, closing a rare gap that could place them somewhere they shouldn't grow.

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

- A short Latitude title now fades in, holds, and fades out when Create World first opens. Vanilla
  preparation text stays hidden, and changing climate, world size, tabs, seed, or layout does not
  replay the intro.
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

For backed-up older Latitude worlds, operators can use `/latitude retrofit enable` followed by
`/latitude retrofit confirm` to apply Latitude decoration to newly loaded eligible chunks. The worker
is deliberately bounded to two chunks per tick and a 2,048-chunk pending queue; `/latitude retrofit
status` reports progress and `/latitude retrofit disable` clears the session. It refuses non-Latitude
worlds rather than converting them.

These are inspection and navigation tools only. Latitude's development tooling — session recording,
screenshot capture, world export, seam auditing, chunk pregeneration, and every automatic harness — is
excluded from public builds by policy and cannot be reached from a release jar.

### Villages and structures

- Villages are rejected from climate-mismatched starts, and polar village "ghosts" are prevented.
- Villages are allowed through 80° where appropriate rather than blanket-banned.
- Structure siting and `/locate structure` now use Latitude's final biome rather than the raw biome
  underneath it, and the port's search is bounded by the world border. These changes reduce false
  results, but not every structure-placement interaction is represented by the search; verify the
  destination and report a locate result whose structure does not appear.
- `/locate` now returns the **nearest** match instead of the first one it finds along the search path
  — biome, structure, wetland, and cave searches all share this. A biome the world's final picker moved
  away from a search's exact centre is still findable, and a cave-biome search that used to come up
  empty now falls back to the nearest match too instead of reporting nothing.
- The coordinate `/locate` prints is now clickable — click it to teleport straight there.
- Woodland mansions no longer site inside biomes added by other packs.

### Performance

- Biome work is cached per column; constant IDs and launch flags are cached rather than re-resolved.
- Analog compass disc spans are batched.
- `/locate` is bounded and responsive, including searches that find nothing — `/locate structure` no
  longer searches past the world border or stalls force-generating chunks it can never reach.
- Sodium fog-culling reachability restored for this target's Sodium line
  (0.8.13+mc1.21.11): fog tightening happens before Sodium's own culling snapshot, so distant terrain
  Latitude fogs out is still culled rather than rendered and hidden behind fog.
- Log output is quieter: a duplicate per-player join message no longer prints by default, and a
  render-overlay fault can no longer flood the log every frame.

### World entry

- The bespoke loading screen holds until the world around you has actually finished rendering, so you
  no longer drop into a half-loaded frame.
- New worlds use latitude-aware spawn targeting before pregeneration and aim to keep spawn out of the
  east/west edge warning band.
- Zone spawn targets now use checked latitude-band midpoints. Fallback placement remains a Beta 1 test
  target: report any new world that lands outside the selected zone.
- Zone and hemisphere titles are measured from the world's equator rather than a fixed line, fixing an
  inverted or offset hemisphere readout, and no longer fire spuriously after a long teleport or a
  backward world-clock change.
- The loading screen's climate-zone label now appears when you join a remote or LAN-hosted server, not
  only when you're hosting your own world.

### Existing worlds

Existing Latitude worlds load and continue generating without rewriting already-generated chunks.
Worlds created before 1.3 keep their legacy worldgen policy when opened, and the setting persists
correctly through a world reload on this Minecraft version.

### Known limitations

- **With large custom biome packs installed, not every biome will appear.** Each latitude band draws
  from a finite weighted pool, so the more biomes you add, the smaller each one's share — rarer accent
  biomes from big stacks can fall below visible frequency. 1.5 improves *which* biomes appear and
  reduces provider crowding, but it does not guarantee every provider or biome in every world. One rare
  biome missing from one world is expected; repeated loss of a whole installed provider is reportable.
  Per-pack representation weighting is on the roadmap.
- **Warm-belt village density has eased somewhat.** Forest doesn't spawn villages — vanilla behavior,
  unchanged here — and savanna, where villages are common, now covers less of the warm belt than before
  this release in favor of a more varied mix. Villages remain common inside savanna regions; there is
  simply less savanna overall for them to appear in.
- **A rare seam where a lush temperate biome directly borders an arid subtropical one at the exact band
  boundary.** Uncommon, already understood, and a smoother transition is designed for a later pass.
- **Savanna can still occasionally turn up a short distance outside the region it belongs to,** through
  a fallback substitution path that does not re-check the region boundary. This is minor, accepted
  dilution rather than a bug, and is also present on Latitude's Minecraft 26.2 release line.

### Worldgen parity with the other 1.5 lines

Latitude's climate model and biome rules are the same design across every Minecraft version this mod
targets, and worldgen fixes are ported between this line and Minecraft 26.2 in both directions so
neither line is left behind — several of the fixes above came from 26.2 this round. The two lines are
not expected to be byte-for-byte identical, since 1.21.11 and 26.2 compute terrain differently
underneath and the same rules can land a biome one block to either side of a boundary, but they are
intended to agree on the shape of the climate model: which biomes belong in which latitude band, and
roughly how much of each.


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
