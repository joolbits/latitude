# Latitude 1.5.0+26.1.2 — release text

Drafts for publication. Nothing here is authorized for upload; Maintainer decides channel and timing.
Adapted from `release-text-1.5.0.md` (the 26.2 release text) and the actual published `v1.5.0+26.2`
GitHub release body, per the campaign method's Phase-8 step: re-frame "before you update" for this
target rather than reuse the 26.2 wording verbatim.

Artifact: `latitude-1.5.0+26.1.2.jar` · SHA-256
`c7ec74d5b8fb29a0b23eeecebacf2093fc6a51b0f588d63425fc92539327cdd4`
(freshly built off `port/1.5.0-26.1.2`, full gate green, not the earlier `TEST 6` jar)
Minecraft 26.1.2 · Fabric · Java 25 · requires Fabric API

---

## Modrinth / CurseForge version description

### Latitude 1.5.0 — for Minecraft 26.1.2

**The first Latitude release on the mod platforms for the 26.1.2 line.** Latitude 1.3 was published on
Minecraft 1.21.11; Latitude 1.4 shipped on 26.1.2 but was only ever tagged on GitHub, never published
to Modrinth or CurseForge. This build brings the 1.4 "Cohesive Horizons" worldgen and custom-biome
overhaul to the platforms for the first time, together with the entire 1.5 polish campaign on top of it.

If you played 1.4 from GitHub, you already have most of this — 1.5 is the polish pass, not a rebuild.
If you're coming from 1.3, this will not feel like a point release.

**Latitude 2.0 is still in production.** The larger overhaul being showcased publicly, with its more
elaborate mechanics, is a separate line and still in development. 1.5 is the final polish release of
the 1.x line.

#### Before you update

- **If you're already on Latitude 1.4 (26.1.2), this is a same-Minecraft-version upgrade** — 1.4 and
  1.5 both target 26.1.2, so there is no save-format conversion to cross going from one to the other.
- **If you're coming from an older line** (1.3 on 1.21.11, or earlier), you're changing Minecraft
  versions, not just Latitude versions — back up your world before updating, the way you would for any
  Minecraft version jump.
- **Existing worlds keep their original biome selection**, including in chunks you have not visited
  yet, so the climate-model and custom-biome work below applies to **newly created worlds**. This is
  deliberate: it stops terrain you have already explored from changing underneath you.

#### Structures now match the biome you're standing in

Two real bugs, one root cause: vanilla's own structure-placement check and its `/locate structure`
prediction were reading two different biome sources — one already-repainted by Latitude, one still raw.
That split explained both a desert temple generating in what turned out to be snow, and `/locate
structure` promising a pyramid or village that was never actually there. Structure siting now judges
the same final biome the game shows you, so what's reported and what generates always agree.

`/locate structure` is also now bounded to the world border and answers immediately, instead of
occasionally searching far past the playable world and stalling while it force-generates chunks to
check candidates that could never be reached.

#### The climate map reads like a real world now

The warm-side moisture model used to ignore latitude, so deserts scattered evenly across every warm
band — including the equator. That model has been rebuilt around an Earth-analog wet-bias: the
equatorial belt stays humid, arid country sits out in the subtropics where it belongs, and you travel
through a believable jungle → savanna → desert gradient instead of a shuffled bag of biomes.

The equator stayed *varied* rather than becoming a wall of jungle, badlands stopped appearing at the
equator (it's a subtropical landform on Earth), and biome "confetti" is largely gone — accent biomes
now form real patches instead of single-cell speckles. On validation seeds, jungle small-fragment share
fell from roughly a quarter to under a tenth.

Also fixed along the way: Pale Garden forms one coherent inland region, Mushroom Fields is actual land
instead of a water-coloured label, caves are reliably represented, temperate coasts and beaches were
tightened, and windswept mountains came back.

#### Custom biome packs actually get represented

Latitude admits biomes from other mods through its `globe:lat_*` tags, with a safety rail so nothing
lands in a climate it doesn't belong in.

In 1.5 the selection was rebuilt. It used to be driven by a fixed seed and a single tag-size-weighted
roll, which let big packs crowd out entire providers and made different worlds repeat the same choices.
Latitude now picks a provider first, then a biome inside it, using your world seed — so a pack's share
no longer depends on how many biomes it happens to ship.

With Biomes O' Plenty and Terralith both installed, sampled worlds came out around a fifth BoP, a
quarter to a third Terralith, and about half vanilla, with dozens of distinct custom biomes present.
Terralith's orchid swamp and amethyst regions and BoP's marsh, wetland and tropics all show up in the
right latitudes, and temperate forest went from a near-monoculture to a genuine mix. Exact shares vary
by seed, world size, and which packs you have installed.

#### The poles feel like the poles

Polar blindness is now a whiteout fog you can read as weather. Frost damage in the final zone was
steadied, foliage is suppressed beyond 80°, and the polar warnings and zone titles were reworked.

#### A new create-world screen

A square Atlas preview replaces the old disc. Smaller worlds show a faint Regular-world reference
underneath and larger worlds a darkened one inset, so you can actually compare sizes. The Atlas stays
put while you cycle sizes, and the compact-world note no longer shoves it around.

#### A customizable compass HUD

HUD Studio brings HUD customization into one tabbed screen with a live preview. Drag the title,
compass, and a new biome/zone readout wherever you want, snap-to-grid or free, with independent text
scaling and six analog compass themes.

#### Operator commands

Public builds now ship a few operator-gated tools under `/latitude`: `here` and `explainHere` to see
your latitude, band and why a biome was chosen; `probe` to sample the surrounding distribution; and
`tpLat` / `tpBand` to jump to a latitude or band. Inspection and navigation only — Latitude's
development tooling is excluded from release builds by policy.

#### Compatibility

Existing Latitude worlds load and keep generating without rewriting chunks you've already explored.

#### Known limitation

With several large biome packs installed, you will not see every biome. Each latitude band draws from a
finite weighted pool, so the more you add, the thinner each one's share. 1.5 substantially improves
which biomes appear and stops whole packs being crowded out, but it does not make coverage complete.
Per-pack representation weighting is on the roadmap.

#### Help us catch biome anomalies

If you notice something that looks wrong for its surroundings — a flat biome like plains generating on
unusually steep or rocky terrain, an expected feature like a mushroom island not appearing where the
biome says it should, a biome that seems out of place for its latitude, or anything else that looks
like a placement bug rather than natural variety — please report it with your seed, coordinates, and
which packs you have installed. That kind of report is exactly what has caught real regressions before.

---

## GitHub release body

**Latitude 1.5.0 (Minecraft 26.1.2)**

First platform release on the 26.1.2 line. Brings the previously GitHub-only 1.4 "Cohesive Horizons"
line to Modrinth/CurseForge for the first time, plus the full 1.5 polish campaign.

**Latitude 2.0 is still in production.** The larger overhaul being showcased publicly, with its more
elaborate mechanics, is a separate line and still in development. 1.5 is the final polish release of
the 1.x line.

### Before you update

- **Back up your worlds first if you're coming from 1.3 (MC 1.21.11) or earlier** — you'll be crossing
  a Minecraft version change. Coming from 1.4 (already on 26.1.2), there's no version change to cross.
- **Existing worlds keep their original biome selection**, including in chunks you have not visited
  yet, so the climate-model and custom-biome work below applies to newly created worlds.

Highlights:

- **Structure siting now matches the biome you're standing in** — fixes both structures generating in
  the wrong biome and `/locate structure` reporting ones that were never actually there; `/locate
  structure` is also now bounded to the world border and responsive
- **Zone spawn targets now land inside the zone you picked** — every zone's spawn target is the true
  midpoint of its latitude band
- Rebuilt latitude-aware climate model — humid equator, subtropical arid belt, believable
  jungle → savanna → desert gradient
- Biome coherence pass: accent biomes form patches, not confetti; no equatorial badlands; reduced
  cross-province bleed
- Custom-biome provider selection rebuilt — packs are chosen by provider first, so large packs no
  longer crowd each other out
- Pale Garden coherence, Mushroom Fields as real land, guaranteed cave representation, temperate coast
  and beach fixes
- Polar whiteout fog replacing blindness; steadied frost damage; polar foliage suppression beyond 80°
- Reworked east/west edge advisory and escalation
- HUD Studio: one tabbed customization screen, draggable title/compass/location readout, independent
  text scaling, six analog themes
- New square-Atlas create-world screen with world-size reference underlays
- New operator commands under `/latitude` (`here`, `explainHere`, `probe`, `tpLat`, `tpBand`,
  `flyspeed`)
- Performance: per-column biome caching, batched compass rendering, bounded and responsive `/locate`
- Existing worlds load and expand without rewriting explored chunks

Full notes: see `CHANGELOG.md`.

Known limitation: with large custom biome packs installed, not every biome will appear — each latitude
band draws from a finite weighted pool. Per-pack representation weighting is on the roadmap.

---

## Suggested tag

```
v1.5.0+26.1.2
```

Matches the existing convention (`v1.4.0+26.1.2`, `v1.3.0+1.21.11`, `v1.5.0+26.2`).

---

## GitHub issue #8 follow-up (held per the maintainer's instruction, 2026-08-06)

Post-release, once this artifact is live: post the dedicated-server smoke result to issue #8 with a
link to this release. Draft already prepared and shown to Maintainer this change; not posted yet.
