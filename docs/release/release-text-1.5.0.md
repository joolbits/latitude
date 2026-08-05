# Latitude 1.5.0 — release text

Drafts for publication. Nothing here is authorized for upload; Maintainer decides channel and timing.

Artifact: `latitude-1.5.0+26.2.jar` · SHA-256 `a097eab49b0df4738ec1242cb60508e6176932816f3b587bce0c0ae3c3c628b5`
Minecraft 26.2 · Fabric · Java 25 · requires Fabric API

---

## Modrinth / CurseForge version description

### Latitude 1.5.0 — for Minecraft 26.2

**The first Latitude release on the mod platforms since 1.3.** The 1.4 line was only ever tagged on
GitHub, so this build carries two full development cycles: the 1.4 worldgen and custom-biome overhaul,
plus the whole 1.5 polish campaign on top of it.

If you played 1.3, this will not feel like a point release.

**Latitude 2.0 is still in production.** The larger overhaul being showcased publicly, with its more
elaborate mechanics, is a separate line and still in development. 1.5 is the final polish release of
the 1.x line.

#### Before you update

**Back up your worlds first.** Coming from 1.3 (MC 1.21.11) or 1.4 (MC 26.1.2), Minecraft converts
your save to the 26.2 format on first open, and that conversion cannot be undone. Keep a copy of the
save folder before you launch.

**Existing worlds keep their original biome selection** — including in chunks you have not visited
yet. The climate-model and custom-biome work below therefore applies to **newly created worlds**. That
is deliberate: it stops terrain you have already explored from changing underneath you.

**The tree line and snow caps are the exception.** Those are applied as terrain generates, so they will
show up in newly generated chunks of an existing world too. Already-generated chunks are untouched, so
an older save can show a seam where forested summits meet the new bare-and-snowcapped ones.

#### Mountains feel like mountains

New in this release and on by default: a real tree line, and snow-capped peaks whose snowline follows
latitude.

Forests now thin out with altitude instead of climbing to the summit — trees start dropping away around
Y140 and stop entirely by Y168, so the tree line arrives as a gradual thinning rather than a hard edge.
Above it, ground gives way to exposed alpine rock, with a fading shelf of meadow just above the line so
the change does not read as a painted stripe.

Higher still, peaks wear snow, and where snow begins depends on your latitude — shaped after Earth's
real climatic snowline, which sits lowest near the poles and rises toward the dry subtropics. Polar
terrain is snow-covered almost throughout; subpolar has a low snowline; temperate peaks are capped;
in the subtropics only the highest summits are; and there are no equatorial glaciers at all.

The snowline is noise-warped rather than a flat contour, so caps sit unevenly along a ridge the way
real snow does instead of cutting every peak at the same height. And the snow zone deliberately carries
no grass, so nothing pokes up through the caps.

#### The climate map reads like a real world

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

---

## GitHub release body

**Latitude 1.5.0 (Minecraft 26.2)**

First platform release since 1.3. Includes the previously GitHub-only 1.4 "Cohesive Horizons" line in
full, plus the 1.5 polish campaign.

### Before you update

- **Back up your worlds first.** Coming from 1.3 (MC 1.21.11) or 1.4 (MC 26.1.2), Minecraft converts
  your save to the 26.2 format on first open, and that conversion cannot be undone.
- **Existing worlds keep their original biome selection**, including in chunks you have not visited
  yet, so the climate-model and custom-biome work below applies to **newly created worlds**. The tree
  line and snow caps are the exception — they apply as terrain generates, so they appear in newly
  generated chunks of existing worlds too, which can leave a visible seam at an old save's frontier.

Highlights:

- **Mountains feel like mountains** — a real tree line (forests thin out with altitude instead of
  reaching the summit), exposed alpine rock above it, and snow-capped peaks whose snowline follows
  latitude the way Earth's does: lowest at the poles, highest in the dry subtropics, absent at the
  equator. On by default.
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
v1.5.0+26.2
```

Matches the existing convention (`v1.4.0+26.1.2`, `v1.3.0+1.21.11`).
